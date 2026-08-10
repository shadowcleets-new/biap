package com.sc.biap.security

import android.util.Log
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException

/**
 * @file AccountLinker.kt
 * @description Attaches a sign-in method to the account you are already using, instead of
 *              swapping you into a different one.
 *
 * THE BUG THIS EXISTS TO FIX:
 *   Every flow used to call signInWithCredential. That does not "add" a sign-in method - it
 *   signs you out and signs you in as whoever owns that credential. So signing in with Google
 *   and then entering a phone number produced TWO separate Firebase accounts with two
 *   different uids.
 *
 *   That was not merely an annoyance at the login screen. The catalogue database is named
 *   bi_database_<hash of uid>.db, so the two identities were reading two different databases:
 *   designs added while signed in by phone were invisible when signed in by Google, and a
 *   reinstall followed by a Google sign-in landed on an account that had never heard of the
 *   phone number.
 *
 *   linkWithCredential attaches the credential to the CURRENT user, so one person keeps one
 *   uid, one database, and one set of sign-in methods. Afterwards any linked method - Google,
 *   phone or email - signs in to that same account, which is what makes a reinstall restore
 *   everything regardless of which method is used.
 *
 * WHY IT NEVER SILENTLY SWITCHES ACCOUNTS:
 *   If the credential already belongs to somebody else, linking fails with a collision. The
 *   safe response is to say so and change nothing. Quietly signing into the other account
 *   would swap the user's entire catalogue underneath them - exactly the failure this class
 *   was written to end.
 */
object AccountLinker {

    private const val TAG = "AccountLinker"

    /**
     * Link [credential] to the signed-in user, or sign in with it when nobody is signed in.
     *
     * [onResult] receives null on success, or a human-readable reason on failure.
     */
    fun signInOrLink(
        auth: FirebaseAuth,
        credential: AuthCredential,
        onResult: (error: String?) -> Unit,
    ) {
        val current = auth.currentUser

        if (current == null) {
            auth.signInWithCredential(credential)
                .addOnSuccessListener { onResult(null) }
                .addOnFailureListener { e ->
                    Log.w(TAG, "sign-in failed: ${e.message}")
                    onResult(e.localizedMessage ?: "Sign-in failed")
                }
            return
        }

        // Already linked to THIS account: re-verifying an existing method is a normal thing
        // to do and must read as success, not as an error.
        if (current.providerData.any { it.providerId == credential.provider }) {
            Log.i(TAG, "${credential.provider} already linked to this account")
            onResult(null)
            return
        }

        current.linkWithCredential(credential)
            .addOnSuccessListener {
                Log.i(TAG, "linked ${credential.provider} to uid=${current.uid}")
                onResult(null)
            }
            .addOnFailureListener { e ->
                val message = when (e) {
                    is FirebaseAuthUserCollisionException ->
                        "This ${describe(credential.provider)} is already registered to a " +
                            "different BIAP account. Sign out first to use it."
                    else -> e.localizedMessage ?: "Could not add this sign-in method"
                }
                Log.w(TAG, "link failed for ${credential.provider}: ${e.message}")
                onResult(message)
            }
    }

    /** Sign-in methods currently attached to the signed-in account. */
    fun linkedProviders(auth: FirebaseAuth): Set<String> =
        auth.currentUser?.providerData
            ?.map { it.providerId }
            ?.filter { it != "firebase" }   // the umbrella entry, not a sign-in method
            ?.toSet()
            .orEmpty()

    private fun describe(providerId: String): String = when (providerId) {
        "phone" -> "phone number"
        "password" -> "email address"
        "google.com" -> "Google account"
        else -> "sign-in method"
    }
}
