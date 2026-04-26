@file:Suppress("DEPRECATION")

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
            response.orEmpty()
        } catch (e: Exception) {
            Log.e(tag, "Inference error on GPU", e)
            // Clear the failed client
            clients.remove(modelPath)
            runCatching { client.close() }
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

            Log.i(tag, "Creating client for $modelPath with backend: GPU")
            
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(DEFAULT_MAX_TOKENS)
                .setMaxTopK(DEFAULT_TOP_K)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()
                
            val created = LlmInference.createFromOptions(context, options)
            clients[modelPath] = created
            return created
        }
    }

    private companion object {
        private const val DEFAULT_MAX_TOKENS = 256
        private const val DEFAULT_TOP_K = 40
    }
}
