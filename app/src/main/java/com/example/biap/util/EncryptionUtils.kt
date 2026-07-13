package com.example.biap.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val SECURE_PREFS_FILE = "biap_secure_prefs"
    private const val LEGACY_FALLBACK_PREFS_FILE = "biap_prefs_fallback"
    private const val KEY_PREF_NAME = "sync_key"

    /**
     * Generate a cryptographically secure 256-bit AES key, Base64-encoded.
     */
    fun generateRandomKey(): String {
        val random = SecureRandom()
        val keyBytes = ByteArray(32) // 256 bits
        random.nextBytes(keyBytes)
        return Base64.encodeToString(keyBytes, Base64.NO_WRAP)
    }

    /**
     * True only if the string Base64-decodes to exactly 32 bytes (a valid AES-256 key).
     */
    fun isValidKey(rawKeyBase64: String): Boolean {
        return try {
            Base64.decode(rawKeyBase64, Base64.NO_WRAP).size == 32
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encrypts plaintext using a raw Base64-encoded AES-256 key. Output: Base64(iv || ciphertext).
     */
    fun encryptWithKey(plainText: String, rawKeyBase64: String): String {
        val keyBytes = Base64.decode(rawKeyBase64, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        val random = SecureRandom()
        val iv = ByteArray(12)
        random.nextBytes(iv)
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts ciphertext using a raw Base64-encoded AES-256 key. Throws on tampered/foreign-key input.
     */
    fun decryptWithKey(cipherTextBase64: String, rawKeyBase64: String): String {
        val keyBytes = Base64.decode(rawKeyBase64, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
        require(combined.size > 12) { "Ciphertext too short" }
        val iv = ByteArray(12)
        System.arraycopy(combined, 0, iv, 0, iv.size)
        val cipherText = ByteArray(combined.size - iv.size)
        System.arraycopy(combined, iv.size, cipherText, 0, cipherText.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun buildSecurePrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Opens the encrypted prefs. If unreadable (e.g. restored to a new device where the
     * Keystore master key does not exist), deletes the pref file once and recreates it.
     * There is NO plaintext fallback; if secure storage still fails, this throws and the
     * caller surfaces a sync error instead of silently degrading security.
     */
    fun getSecurePrefs(context: Context): SharedPreferences {
        return try {
            buildSecurePrefs(context)
        } catch (e: Exception) {
            context.deleteSharedPreferences(SECURE_PREFS_FILE)
            buildSecurePrefs(context)
        }
    }

    fun getOrGenerateLocalSyncKey(context: Context): String {
        val prefs = getSecurePrefs(context)
        migrateLegacyFallbackKey(context, prefs)
        var key = prefs.getString(KEY_PREF_NAME, null)
        if (key.isNullOrBlank() || !isValidKey(key)) {
            key = generateRandomKey()
            prefs.edit().putString(KEY_PREF_NAME, key).apply()
        }
        return key
    }

    fun saveLocalSyncKey(context: Context, key: String) {
        getSecurePrefs(context).edit().putString(KEY_PREF_NAME, key).apply()
    }

    /**
     * One-time migration: if the abolished plaintext fallback file holds a key and secure
     * storage does not, move it into secure storage; then delete the plaintext file.
     */
    private fun migrateLegacyFallbackKey(context: Context, securePrefs: SharedPreferences) {
        val fallback = context.getSharedPreferences(LEGACY_FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
        val legacyKey = fallback.getString(KEY_PREF_NAME, null)
        if (!legacyKey.isNullOrBlank() && isValidKey(legacyKey) &&
            securePrefs.getString(KEY_PREF_NAME, null).isNullOrBlank()
        ) {
            securePrefs.edit().putString(KEY_PREF_NAME, legacyKey).apply()
        }
        context.deleteSharedPreferences(LEGACY_FALLBACK_PREFS_FILE)
    }
}
