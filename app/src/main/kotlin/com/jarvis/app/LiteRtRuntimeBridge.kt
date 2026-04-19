package com.jarvis.app

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.util.concurrent.ConcurrentHashMap

internal class LiteRtRuntimeBridge(
    private val context: Context
) {
    private val clients = ConcurrentHashMap<String, LlmInference>()
    private val tag = "LiteRtBridge"

    fun complete(modelPath: String, prompt: String): String {
        require(prompt.isNotBlank()) { "Prompt must not be blank" }
        val client = clients[modelPath] ?: createAndCacheClient(modelPath)
        return try {
            val response = client.generateResponse(prompt)
            val result = response.orEmpty()
            if (result.isBlank()) {
                Log.w(tag, "Empty response from generateResponse()")
            }
            result
        } catch (e: Exception) {
            Log.e(tag, "Inference error", e)
            ""
        }
    }

    fun clear(modelPath: String) {
        val client = clients.remove(modelPath)
        runCatching { client?.close() }
    }

    private fun createAndCacheClient(modelPath: String): LlmInference {
        synchronized(this) {
            val existing = clients[modelPath]
            if (existing != null) {
                return existing
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(DEFAULT_MAX_TOKENS)
                .setMaxTopK(DEFAULT_TOP_K)
                .build()
            val created = LlmInference.createFromOptions(context, options)
            clients[modelPath] = created
            return created
        }
    }

    private companion object {
        // Reduced for Gemma 3 1B:  typical output 100-256 tokens
        private const val DEFAULT_MAX_TOKENS = 256
        // Top-K should be <= vocabulary; safer values for small models
        private const val DEFAULT_TOP_K = 40
        // Temperature for deterministic output
        private const val DEFAULT_TEMPERATURE = 0.7f
    }
}
