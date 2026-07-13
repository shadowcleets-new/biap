package com.example.biap.util

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_LENGTH = 256
    private const val ITERATIONS = 1000

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun encrypt(plainText: String, keySeed: String): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val secretKey = deriveKey(keySeed, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(12)
        random.nextBytes(iv)
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(cipherTextBase64: String, keySeed: String): String {
        val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
        val salt = ByteArray(16)
        System.arraycopy(combined, 0, salt, 0, salt.size)
        val iv = ByteArray(12)
        System.arraycopy(combined, salt.size, iv, 0, iv.size)
        val cipherText = ByteArray(combined.size - salt.size - iv.size)
        System.arraycopy(combined, salt.size + iv.size, cipherText, 0, cipherText.size)
        val secretKey = deriveKey(keySeed, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes, Charsets.UTF_8)
    }

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
     * Encrypts plaintext using a raw Base64-encoded AES-256 key.
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
     * Decrypts ciphertext using a raw Base64-encoded AES-256 key.
     */
    fun decryptWithKey(cipherTextBase64: String, rawKeyBase64: String): String {
        val keyBytes = Base64.decode(rawKeyBase64, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
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

    fun getSecurePrefs(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "biap_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getOrGenerateLocalSyncKey(context: Context): String {
        val prefs = try {
            getSecurePrefs(context)
        } catch (e: Exception) {
            context.getSharedPreferences("biap_prefs_fallback", Context.MODE_PRIVATE)
        }
        var key = prefs.getString("sync_key", null)
        if (key.isNullOrBlank()) {
            key = generateRandomKey()
            prefs.edit().putString("sync_key", key).apply()
        }
        return key
    }

    fun saveLocalSyncKey(context: Context, key: String) {
        val prefs = try {
            getSecurePrefs(context)
        } catch (e: Exception) {
            context.getSharedPreferences("biap_prefs_fallback", Context.MODE_PRIVATE)
        }
        prefs.edit().putString("sync_key", key).apply()
    }
}
