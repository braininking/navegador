package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object VaultCryptoManager {
    private const val TAG = "VaultCryptoManager"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "AegisX_Vault_AES256_Key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            "AXGCM1:$ivB64:$dataB64"
        } catch (e: Exception) {
            Log.w(TAG, "Keystore encryption fallback: ${e.message}")
            "AXRAW:" + Base64.encodeToString(plainText.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            when {
                cipherText.startsWith("AXGCM1:") -> {
                    val parts = cipherText.split(":")
                    if (parts.size != 3) return ""
                    val iv = Base64.decode(parts[1], Base64.NO_WRAP)
                    val data = Base64.decode(parts[2], Base64.NO_WRAP)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                    String(cipher.doFinal(data), StandardCharsets.UTF_8)
                }
                cipherText.startsWith("AXRAW:") -> {
                    val raw = Base64.decode(cipherText.removePrefix("AXRAW:"), Base64.NO_WRAP)
                    String(raw, StandardCharsets.UTF_8)
                }
                else -> cipherText
            }
        } catch (e: Exception) {
            Log.w(TAG, "Decryption failed: ${e.message}")
            ""
        }
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
