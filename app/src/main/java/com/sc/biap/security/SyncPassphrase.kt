package com.sc.biap.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * @file SyncPassphrase.kt
 * @description Wraps the sync key with a passphrase, so the key stops travelling in the
 *              clear next to the data it protects.
 *
 * THE PROBLEM THIS FIXES:
 *   The catalogue is encrypted with AES-256-GCM and stored in Google Drive as
 *   bi_data.json.enc. The key was uploaded to the SAME folder as sync_key.txt, in plain
 *   text. Ciphertext and key, side by side.
 *
 *   That made the encryption close to decorative. It defended against nobody who mattered:
 *   not against Google, and not against anyone who obtained access to the Drive account,
 *   because both of them get the key along with the data. Transit was already covered by
 *   TLS. The only attacker it stopped was one who could read one file but not its
 *   neighbour - which is not a real attacker.
 *
 * WHAT REPLACES IT:
 *   The sync key is encrypted with a second key derived from a passphrase the owner knows
 *   and which is never uploaded. Drive now holds sync_key.enc: salt, iteration count and
 *   the wrapped key. Someone with full access to the Drive folder holds two ciphertexts
 *   and no key, and has to brute-force the passphrase to get anywhere.
 *
 *   PBKDF2-HMAC-SHA256 at 210,000 iterations - the OWASP 2023 floor for this algorithm -
 *   so each guess costs real time and a weak passphrase is not instantly reversible.
 *
 * THE TRADE, STATED HONESTLY:
 *   A forgotten passphrase means the catalogue in Drive can never be decrypted again. That
 *   is not a bug to be worked around; it is what makes the protection real. Any "recovery"
 *   that does not involve the passphrase would be a second way in, and would put us back
 *   where we started.
 */
object SyncPassphrase {

    private const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    /** Minimum length accepted. Short enough to be usable, long enough to be worth PBKDF2. */
    const val MIN_LENGTH = 8

    /**
     * Encrypt [syncKeyBase64] under [passphrase]. Returns the envelope stored in Drive.
     *
     * A fresh salt every time, so wrapping the same key twice produces different bytes and
     * reveals nothing by comparison.
     */
    fun wrap(syncKeyBase64: String, passphrase: CharArray): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val kek = deriveKek(passphrase, salt, ITERATIONS)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val wrapped = cipher.doFinal(syncKeyBase64.toByteArray(Charsets.UTF_8))
        kek.fill(0)

        // Deliberately hand-rolled rather than a JSON library: this envelope must stay
        // readable by any future version of the app, including one that has changed how it
        // parses everything else. Four fields, one line, no dependencies.
        return listOf(
            VERSION.toString(),
            ITERATIONS.toString(),
            hex(salt),
            hex(iv),
            hex(wrapped),
        ).joinToString(":")
    }

    /**
     * Recover the sync key, or null if the passphrase is wrong or the envelope is damaged.
     *
     * Null rather than an exception for a wrong passphrase specifically, because that is an
     * ordinary thing for a person to do and the caller should be able to say "try again"
     * without distinguishing it from corruption.
     */
    fun unwrap(envelope: String, passphrase: CharArray): String? {
        return try {
            val parts = envelope.trim().split(":")
            if (parts.size != 5) return null
            if (parts[0].toIntOrNull() != VERSION) return null

            val iterations = parts[1].toIntOrNull() ?: return null
            val salt = unhex(parts[2]) ?: return null
            val iv = unhex(parts[3]) ?: return null
            val wrapped = unhex(parts[4]) ?: return null

            val kek = deriveKek(passphrase, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            // GCM authenticates, so a wrong passphrase fails here rather than returning
            // plausible garbage that would later be used as an encryption key.
            val plain = cipher.doFinal(wrapped)
            kek.fill(0)
            String(plain, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /** True if [envelope] looks like something [unwrap] could handle. */
    fun isEnvelope(envelope: String?): Boolean {
        val parts = envelope?.trim()?.split(":") ?: return false
        return parts.size == 5 && parts[0].toIntOrNull() == VERSION
    }

    private fun deriveKek(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    // Hex rather than Base64 on purpose: android.util.Base64 cannot run in a JVM unit
    // test and java.util.Base64 needs API 26 while this app supports 24. Encoding that
    // guards a key is worth being able to test directly, and these values are tiny.
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(s: String): ByteArray? {
        if (s.length % 2 != 0) return null
        return try {
            ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        } catch (e: Exception) {
            null
        }
    }
}
