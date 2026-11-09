package com.chatverse.app

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

internal data class StoredSession(
    val accessToken: String,
    val refreshToken: String
)

/**
 * Encrypts Supabase session tokens with an AES-GCM key held by Android Keystore.
 * Existing plaintext preferences are migrated once and cleared after encryption.
 */
internal class SecureSessionStore(context: Context) {
    private val securePrefs = context.getSharedPreferences("chatverse_secure_session", Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences("chatverse_session", Context.MODE_PRIVATE)

    fun load(): StoredSession? {
        val encrypted = securePrefs.getString("payload", null)
        val ivText = securePrefs.getString("iv", null)

        if (!encrypted.isNullOrBlank() && !ivText.isNullOrBlank()) {
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = Base64.decode(ivText, Base64.NO_WRAP)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                val plaintext = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
                val json = JSONObject(String(plaintext, Charsets.UTF_8))
                val access = json.optString("access_token")
                val refresh = json.optString("refresh_token")
                if (access.isBlank() || refresh.isBlank()) {
                    clear()
                    null
                } else {
                    StoredSession(access, refresh)
                }
            } catch (_: Exception) {
                // If the Keystore key was invalidated, discard the unusable session.
                clear()
                null
            }
        }

        // One-time migration from the previous unencrypted SharedPreferences store.
        val legacyAccess = legacyPrefs.getString("access_token", null)
        val legacyRefresh = legacyPrefs.getString("refresh_token", null)
        if (!legacyAccess.isNullOrBlank() && !legacyRefresh.isNullOrBlank()) {
            return try {
                save(legacyAccess, legacyRefresh)
                StoredSession(legacyAccess, legacyRefresh)
            } catch (_: Exception) {
                clear()
                null
            }
        }
        return null
    }

    fun save(accessToken: String, refreshToken: String) {
        require(accessToken.isNotBlank() && refreshToken.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = JSONObject()
            .put("access_token", accessToken)
            .put("refresh_token", refreshToken)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(payload)

        securePrefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
        legacyPrefs.edit().clear().apply()
    }

    fun clear() {
        securePrefs.edit().clear().apply()
        legacyPrefs.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "chatverse.session.aesgcm.v1"
    }
}
