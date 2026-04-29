package com.jarvis.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger

interface LlmSettingsRepository {
    fun getBackendMode(): LlmBackendMode
    fun setBackendMode(mode: LlmBackendMode)
    fun getGeminiApiKey(): String?
    fun saveGeminiApiKey(apiKey: String)
    fun clearGeminiApiKey()
}

class EncryptedLlmSettingsRepository(
    context: Context,
    private val logger: JarvisLogger = NoOpJarvisLogger
) : LlmSettingsRepository {
    companion object {
        private const val PREFS_NAME = "jarvis_llm_settings"
        private const val KEY_BACKEND_MODE = "backendMode"
        private const val KEY_GEMINI_API_KEY = "geminiApiKey"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getBackendMode(): LlmBackendMode {
        val stored = sharedPreferences.getString(KEY_BACKEND_MODE, null)
        return if (stored.isNullOrBlank()) {
            LlmBackendMode.fromConfig(BuildConfig.JARVIS_LLM_BACKEND)
        } else {
            LlmBackendMode.fromId(stored)
        }
    }

    override fun setBackendMode(mode: LlmBackendMode) {
        sharedPreferences.edit().putString(KEY_BACKEND_MODE, mode.id).apply()
        logger.info("llm_settings", "LLM backend mode persisted", mapOf("backendMode" to mode.id))
    }

    override fun getGeminiApiKey(): String? {
        return sharedPreferences.getString(KEY_GEMINI_API_KEY, null)
            ?.takeUnless { it.isBlank() }
            ?: BuildConfig.JARVIS_GEMINI_API_KEY.takeIf { it.isNotBlank() }
    }

    override fun saveGeminiApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
        logger.info("llm_settings", "Gemini API key saved", emptyMap())
    }

    override fun clearGeminiApiKey() {
        sharedPreferences.edit().remove(KEY_GEMINI_API_KEY).apply()
        logger.info("llm_settings", "Gemini API key cleared", emptyMap())
    }
}
