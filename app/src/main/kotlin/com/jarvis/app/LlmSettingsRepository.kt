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

    fun getOpenAIApiKey(): String?
    fun saveOpenAIApiKey(apiKey: String)
    fun clearOpenAIApiKey()

    fun getAnthropicApiKey(): String?
    fun saveAnthropicApiKey(apiKey: String)
    fun clearAnthropicApiKey()
}

class EncryptedLlmSettingsRepository(
    context: Context,
    private val logger: JarvisLogger = NoOpJarvisLogger
) : LlmSettingsRepository {
    companion object {
        private const val PREFS_NAME = "jarvis_llm_settings"
        private const val KEY_BACKEND_MODE = "backendMode"
        private const val KEY_GEMINI_API_KEY = "geminiApiKey"
        private const val KEY_OPENAI_API_KEY = "openaiApiKey"
        private const val KEY_ANTHROPIC_API_KEY = "anthropicApiKey"
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

    override fun getOpenAIApiKey(): String? {
        return sharedPreferences.getString(KEY_OPENAI_API_KEY, null)
            ?.takeUnless { it.isBlank() }
            ?: BuildConfig.JARVIS_OPENAI_API_KEY.takeIf { it.isNotBlank() }
    }

    override fun saveOpenAIApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_OPENAI_API_KEY, apiKey.trim()).apply()
        logger.info("llm_settings", "OpenAI API key saved", emptyMap())
    }

    override fun clearOpenAIApiKey() {
        sharedPreferences.edit().remove(KEY_OPENAI_API_KEY).apply()
        logger.info("llm_settings", "OpenAI API key cleared", emptyMap())
    }

    override fun getAnthropicApiKey(): String? {
        return sharedPreferences.getString(KEY_ANTHROPIC_API_KEY, null)
            ?.takeUnless { it.isBlank() }
            ?: BuildConfig.JARVIS_ANTHROPIC_API_KEY.takeIf { it.isNotBlank() }
    }

    override fun saveAnthropicApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_ANTHROPIC_API_KEY, apiKey.trim()).apply()
        logger.info("llm_settings", "Anthropic API key saved", emptyMap())
    }

    override fun clearAnthropicApiKey() {
        sharedPreferences.edit().remove(KEY_ANTHROPIC_API_KEY).apply()
        logger.info("llm_settings", "Anthropic API key cleared", emptyMap())
    }
}
