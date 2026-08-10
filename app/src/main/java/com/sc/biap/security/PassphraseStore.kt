package com.sc.biap.security

import android.content.Context
import com.sc.biap.util.EncryptionUtils

/**
 * @file PassphraseStore.kt
 * @description Remembers the sync passphrase on THIS device, so it is typed once and not
 *              on every sync.
 *
 * Held in EncryptedSharedPreferences, sealed by the Android Keystore - the same protection
 * as the database key. That is deliberately weaker than "never stored": the passphrase
 * exists to keep the key out of Google Drive, not off the owner's own phone. A phone that
 * is already trusted to hold the decrypted catalogue gains nothing from also forcing the
 * passphrase to be re-typed hourly.
 *
 * The consequence to be clear about: someone who fully compromises an unlocked phone can
 * reach this. The threat this closes is the Drive folder - where the key used to sit in
 * plain text beside the data it protected.
 */
object PassphraseStore {

    private const val PREF_NAME = "sync_passphrase"

    fun get(context: Context): CharArray? =
        EncryptionUtils.getSecurePrefs(context)
            .getString(PREF_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?.toCharArray()

    fun save(context: Context, passphrase: CharArray) {
        // commit(), not apply(): a sync runs immediately after this and must not race a
        // background write that has not landed.
        EncryptionUtils.getSecurePrefs(context)
            .edit()
            .putString(PREF_NAME, String(passphrase))
            .commit()
    }

    fun isSet(context: Context): Boolean = get(context) != null

    fun clear(context: Context) {
        EncryptionUtils.getSecurePrefs(context).edit().remove(PREF_NAME).commit()
    }
}
