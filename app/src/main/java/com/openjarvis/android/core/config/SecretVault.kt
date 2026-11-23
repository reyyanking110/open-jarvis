package com.openjarvis.android.core.config

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.openjarvis.android.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ApiKeyProvider {
    fun getGeminiApiKey(): String
    fun hasGeminiApiKey(): Boolean
    fun saveGeminiApiKey(apiKey: String)
    fun clearGeminiApiKey()
    val apiKeyConfiguredFlow: StateFlow<Boolean>
}

/**
 * Hardware/TEE-backed Android Keystore AES-256-GCM vault for storing user-provided API keys
 * without relying on deprecated EncryptedSharedPreferences or committing secrets to VCS.
 */
@Singleton
class SecretVault @Inject constructor(
    @ApplicationContext private val context: Context,
) : ApiKeyProvider {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _configured = MutableStateFlow(getGeminiApiKey().isNotBlank())
    override val apiKeyConfiguredFlow: StateFlow<Boolean> = _configured.asStateFlow()

    override fun getGeminiApiKey(): String {
        val encrypted = prefs.getString(KEY_CIPHER, null)
        val iv = prefs.getString(KEY_IV, null)
        if (!encrypted.isNullOrBlank() && !iv.isNullOrBlank()) {
            runCatching { decrypt(encrypted, iv) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return BuildConfig.DEFAULT_GEMINI_API_KEY.trim()
    }

    override fun hasGeminiApiKey(): Boolean = getGeminiApiKey().isNotBlank()

    override fun saveGeminiApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            clearGeminiApiKey()
            return
        }
        val (cipherBase64, ivBase64) = encrypt(trimmed)
        prefs.edit()
            .putString(KEY_CIPHER, cipherBase64)
            .putString(KEY_IV, ivBase64)
            .apply()
        _configured.value = true
    }

    override fun clearGeminiApiKey() {
        prefs.edit()
            .remove(KEY_CIPHER)
            .remove(KEY_IV)
            .apply()
        _configured.value = BuildConfig.DEFAULT_GEMINI_API_KEY.isNotBlank()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(plainText: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipherBytes, Base64.NO_WRAP) to
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(cipherBase64: String, ivBase64: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val ivBytes = Base64.decode(ivBase64, Base64.NO_WRAP)
        val spec = GCMParameterSpec(128, ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        val plainBytes = cipher.doFinal(Base64.decode(cipherBase64, Base64.NO_WRAP))
        return String(plainBytes, Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "open_jarvis_gemini_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS_NAME = "open_jarvis_vault"
        const val KEY_CIPHER = "gemini_cipher"
        const val KEY_IV = "gemini_iv"
    }
}
