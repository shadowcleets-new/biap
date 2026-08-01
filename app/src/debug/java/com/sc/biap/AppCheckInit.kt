package com.sc.biap

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * DEBUG variant App Check.
 *
 * Uses the debug provider, which mints a random debug token on first run and prints it to
 * logcat under the tag "DebugAppCheckProvider". That token must be registered once in
 * Firebase Console -> App Check -> biap (Android) -> Manage debug tokens, otherwise every
 * App Check-gated call (including Phone Auth verification) is rejected.
 *
 * Find it with:  adb logcat -s DebugAppCheckProvider
 *
 * The release source set has a parallel file that installs Play Integrity instead. Variant
 * selection is done by the build system, not by a runtime BuildConfig.DEBUG check - a runtime
 * check silently resolved against the Firebase SDK's own BuildConfig (always false) and
 * inverted the branch.
 */
fun installAppCheck(context: Context) {
    Firebase.appCheck.installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance()
    )
    Log.i(
        "BiapAppCheck",
        "Debug App Check installed. Run: adb logcat -s DebugAppCheckProvider " +
            "to read the debug token, then register it in Firebase Console."
    )
}
