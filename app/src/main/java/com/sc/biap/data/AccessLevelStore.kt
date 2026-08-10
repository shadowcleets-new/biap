package com.sc.biap.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * @file AccessLevelStore.kt
 * @description Reads what the signed-in person is allowed to do. Read-only, by design.
 *
 * WHY THIS IS THE ONE THING NOT KEPT IN DRIVE:
 *   Everything else about a user rides with the catalogue in their own Drive appDataFolder.
 *   A role cannot. That folder belongs to the user, so a role stored there is a role the
 *   user can edit, and "assigned by an admin" would be a label on a field anyone could
 *   change. Enforcement has to sit somewhere the user does not control.
 *
 *   Firestore document users/{uid} holds it, with rules that let a person read their own
 *   role and only an admin write anyone's. See firestore.rules.
 *
 *   There is NO write path in this class, and that is not an oversight. The app has no
 *   legitimate reason to set a role, so it has no code that can - an admin assigns roles
 *   from the Firebase Console. Code that cannot do a thing cannot be tricked into it.
 *
 * FAILS CLOSED:
 *   Any problem - offline, missing document, unreadable value - yields
 *   [AccessLevel.DEFAULT], which is VIEWER. An outage must not hand out privileges. The
 *   caller can tell a real answer from a fallback via [Result.isAuthoritative], so the UI
 *   can say "could not confirm" instead of asserting someone is a Viewer.
 */
object AccessLevelStore {

    private const val TAG = "AccessLevelStore"
    private const val COLLECTION = "users"
    private const val FIELD_ROLE = "role"

    data class Result(
        val level: AccessLevel,
        /** False when [level] is the fail-closed fallback rather than a value we actually read. */
        val isAuthoritative: Boolean,
    )

    suspend fun current(): Result = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext Result(AccessLevel.DEFAULT, isAuthoritative = false)

        return@withContext try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection(COLLECTION)
                .document(uid)
                .get()
                .await()

            if (!snapshot.exists()) {
                // Nobody has been assigned a role yet. Genuinely unassigned, so the answer
                // is authoritative even though it is the default.
                Log.i(TAG, "no role document for uid=$uid; treating as ${AccessLevel.DEFAULT}")
                return@withContext Result(AccessLevel.DEFAULT, isAuthoritative = true)
            }

            val level = AccessLevel.fromName(snapshot.getString(FIELD_ROLE))
            Log.i(TAG, "role for uid=$uid is $level")
            Result(level, isAuthoritative = true)
        } catch (e: Exception) {
            Log.w(TAG, "could not read role: ${e.message}")
            Result(AccessLevel.DEFAULT, isAuthoritative = false)
        }
    }
}
