package com.sc.biap.security

import android.content.Context
import android.util.Base64
import com.sc.biap.util.EncryptionUtils
import java.security.SecureRandom

/**
 * @file DatabaseKey.kt
 * @description The passphrase that encrypts the local catalogue database.
 *
 * WHY THIS EXISTS:
 *   The catalogue used to be plain SQLite. Designs, cost prices, margins and karigar names
 *   sat in a file readable by anyone with access to the phone's storage - which during this
 *   project was demonstrated in practice by pulling the database off the device and reading
 *   it outright. The biometric lock did nothing about that: it covers a running app, it does
 *   not encrypt a file.
 *
 * WHAT THIS KEY IS, AND IS NOT:
 *   It is a random 256-bit value held in EncryptedSharedPreferences, which is itself sealed
 *   by a key in the Android Keystore - hardware-backed on a Pixel. That protects the
 *   database against file-level access: a copied .db is useless without the phone.
 *
 *   It is NOT protection against someone using the unlocked phone, and it never can be: the
 *   app must be able to open its own database without asking anybody anything, so any
 *   process running as this app can reach the key. Physical access to an unlocked device
 *   remains the strongest attack, and the biometric lock is the (partial) answer to that,
 *   not this.
 *
 *   The key never leaves the device and is never uploaded. It is not the sync key: a phone
 *   restored from backup generates a new one and re-downloads the catalogue from Drive.
 */
object DatabaseKey {

    init {
        // SQLCipher ships its own native SQLite and will not open anything until this is
        // loaded. Done here because every encrypted open goes through getOrCreate(), so
        // there is no path to a database that skips it.
        System.loadLibrary("sqlcipher")
    }

    private const val PREF_NAME = "local_db_passphrase"
    private const val KEY_BYTES = 32

    /**
     * The passphrase for SQLCipher, created once per install.
     *
     * Returned as a ByteArray because SQLCipher zeroes the array it is given; a String
     * would linger in the JVM's string pool until garbage collection, unwipeable.
     */
    fun getOrCreate(context: Context): ByteArray {
        val prefs = EncryptionUtils.getSecurePrefs(context)
        val existing = prefs.getString(PREF_NAME, null)
        if (!existing.isNullOrBlank()) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }

        val fresh = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        // commit(), not apply(): the database is opened immediately after this returns, and
        // a key that had not reached disk would be regenerated on the next launch - leaving
        // a database nothing can ever open again.
        prefs.edit().putString(PREF_NAME, Base64.encodeToString(fresh, Base64.NO_WRAP)).commit()
        return fresh
    }
}
