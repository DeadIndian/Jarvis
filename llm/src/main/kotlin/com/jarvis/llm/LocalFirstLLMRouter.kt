package com.jarvis.llm

import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import kotlinx.coroutines.withTimeoutOrNull

interface LLMProvider {
    val name: String
    suspend fun complete(prompt: String): String
}

class LocalFirstLLMRouter(
    private val localProvider: LLMProvider? = null,
    private val cloudProvider: LLMProvider? = null,
    private val logger: JarvisLogger = NoOpJarvisLogger,
    private val localTimeoutMs: Long = 30000L, // Increased timeout for local inference
    private val cloudTimeoutMs: Long = 2500L
) : LLMRouter {
    override suspend fun complete(request: LLMRequest): LLMResponse {
        val prompt = request.prompt.trim()
        require(prompt.isNotBlank()) { "Prompt must not be blank" }

        val localAttempt = tryLocal(prompt)
        if (localAttempt.response != null) {
            return localAttempt.response
        }

        if (request.allowCloudFallback) {
            val cloud = tryCloud(prompt)
            if (cloud != null) {
                return cloud
            }
        }

        val errorMessage = if (localProvider == null) {
            "No local LLM provider initialized (check if model is downloaded and selected)"
        } else {
            buildString {
                append("Local LLM failed to respond")
                localAttempt.errorInfo?.let {
                    append(": ")
                    append(it)
                }
            }
        }

        logger.warn(
            stage = "llm",
            message = "No provider could complete request",
            data = mapOf("allowCloudFallback" to request.allowCloudFallback, "error" to errorMessage)
        )
        return LLMResponse(
            text = "Error: $errorMessage",
            provider = "none"
        )
    }

    private suspend fun tryLocal(prompt: String): LocalAttemptResult {
        val provider = localProvider ?: return LocalAttemptResult()
        val startedAt = System.nanoTime()

        var caughtException: Throwable? = null
        val output = withTimeoutOrNull(localTimeoutMs) {
            try {
                provider.complete(prompt).trim().ifBlank { null }
            } catch (e: Exception) {
                caughtException = e
                null
            }
        }
        val latencyMs = (System.nanoTime() - startedAt) / 1_000_000

        return if (!output.isNullOrBlank()) {
            logger.info("llm", "Local LLM request completed", mapOf("provider" to provider.name, "latencyMs" to latencyMs))
            LocalAttemptResult(
                response = LLMResponse(text = output, provider = provider.name)
            )
        } else {
            val errorInfo = caughtException?.message ?: "Timeout after ${latencyMs}ms"
            logger.warn("llm", "Local LLM request failed", mapOf("provider" to provider.name, "latencyMs" to latencyMs, "error" to errorInfo))
            LocalAttemptResult(errorInfo = errorInfo)
        }
    }

    private suspend fun tryCloud(prompt: String): LLMResponse? {
        val provider = cloudProvider ?: return null
        val startedAt = System.nanoTime()
        val output = withTimeoutOrNull(cloudTimeoutMs) {
            runCatching { provider.complete(prompt) }.getOrNull()?.trim().orEmpty()
        }
        val latencyMs = (System.nanoTime() - startedAt) / 1_000_000

        return if (!output.isNullOrBlank()) {
            logger.info("llm", "Cloud fallback request completed", mapOf("provider" to provider.name, "latencyMs" to latencyMs))
            LLMResponse(text = output, provider = provider.name)
        } else {
            logger.warn("llm", "Cloud fallback request failed", mapOf("provider" to provider.name, "latencyMs" to latencyMs))
            null
        }
    }

    private data class LocalAttemptResult(
        val response: LLMResponse? = null,
        val errorInfo: String? = null
    )
}
