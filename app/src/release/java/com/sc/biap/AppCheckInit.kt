package com.sc.biap

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * RELEASE variant App Check.
 *
 * Uses Play Integrity, which attests the app against its package name and signing certificate.
 * Requires the release signing SHA-256 to be registered in Firebase, and the app to be
 * distributed through Play (or an integrity-eligible channel).
 *
 * The debug source set has a parallel file that installs the debug provider instead.
 */
fun installAppCheck(context: Context) {
    Firebase.appCheck.installAppCheckProviderFactory(
        PlayIntegrityAppCheckProviderFactory.getInstance()
    )
}
