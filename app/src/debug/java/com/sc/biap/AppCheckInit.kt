package com.sc.biap

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * DEBUG variant App Check.
 *
 * Uses the debug provider, whose token must be registered once in
 * Firebase Console -> App Check -> biap (Android) -> Manage debug tokens. Without a
 * registered token every App Check-gated call is rejected - Phone Auth verification,
 * image uploads, and image downloads alike.
 *
 * WHY THE TOKEN IS PINNED HERE:
 *   Left to itself the provider mints a random secret on first run and keeps it in
 *   SharedPreferences. "Clear storage" and reinstall both wipe that store, so a fresh
 *   unregistered secret is minted and everything App Check protects begins to fail -
 *   with no visible error, because a rejected download just renders as no photo.
 *   That cost real debugging time: a design's photo appeared to vanish after a storage
 *   clear when in truth the bytes were safe in Storage and the app was simply refused.
 *
 *   Seeding the secret from local.properties makes it stable across both, so the token
 *   is registered once and stays valid. local.properties is gitignored, so the secret
 *   never enters version control; when it is absent the original random behaviour
 *   applies and nothing breaks.
 *
 * Read the active token with:  adb logcat -s DebugAppCheckProvider
 *
 * The release source set has a parallel file that installs Play Integrity instead, so
 * none of this reaches production. Variant selection is done by the build system, not by
 * a runtime BuildConfig.DEBUG check - a runtime check silently resolved against the
 * Firebase SDK's own BuildConfig (always false) and inverted the branch.
 */
fun installAppCheck(context: Context) {
    val pinnedSecret = context.getString(R.string.appcheck_debug_secret)
    if (pinnedSecret.isNotBlank()) {
        seedDebugSecret(context, pinnedSecret)
    }

    Firebase.appCheck.installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance()
    )

    Log.i(
        "BiapAppCheck",
        if (pinnedSecret.isNotBlank()) {
            "Debug App Check installed with a pinned token (survives clear-storage and " +
                "reinstall). Register it once: Firebase Console -> App Check -> Manage debug tokens."
        } else {
            "Debug App Check installed with a RANDOM token - it will change on clear-storage " +
                "or reinstall. Set APPCHECK_DEBUG_SECRET in local.properties to pin it. " +
                "Read the current one: adb logcat -s DebugAppCheckProvider"
        }
    )
}

/**
 * Write the secret into the exact store the debug provider reads on startup.
 *
 * The provider has no API for supplying a secret (DebugAppCheckProviderFactory exposes
 * only getInstance()), so the value is placed where its StorageHelper looks: a
 * SharedPreferences file named for the FirebaseApp's persistence key. Both the filename
 * template and the key below mirror that class.
 *
 * Uses commit() rather than apply(): the provider reads this during the very next call,
 * so the write has to have landed before we return.
 */
private fun seedDebugSecret(context: Context, secret: String) {
    try {
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = context.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.$persistenceKey",
            Context.MODE_PRIVATE
        )
        if (prefs.getString(DEBUG_SECRET_KEY, null) != secret) {
            prefs.edit().putString(DEBUG_SECRET_KEY, secret).commit()
            Log.i("BiapAppCheck", "Pinned debug token restored into the App Check store.")
        }
    } catch (e: Exception) {
        // Never block startup over a debugging convenience; the provider will simply
        // fall back to minting its own secret.
        Log.w("BiapAppCheck", "Could not pin the debug token: ${e.message}")
    }
}

private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
