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
    private val localTimeoutMs: Long = 1200L,
    private val cloudTimeoutMs: Long = 2500L
) : LLMRouter {
    override suspend fun complete(request: LLMRequest): LLMResponse {
        val prompt = request.prompt.trim()
        require(prompt.isNotBlank()) { "Prompt must not be blank" }

        val local = tryLocal(prompt)
        if (local != null) {
            return local
        }

        if (request.allowCloudFallback) {
            val cloud = tryCloud(prompt)
            if (cloud != null) {
                return cloud
            }
        }

        logger.warn(
            stage = "llm",
            message = "No provider could complete request",
            data = mapOf("allowCloudFallback" to request.allowCloudFallback)
        )
        return LLMResponse(
            text = "I could not complete that request right now",
            provider = "none"
        )
    }

    private suspend fun tryLocal(prompt: String): LLMResponse? {
        val provider = localProvider ?: return null
        val startedAt = System.nanoTime()
        val output = withTimeoutOrNull(localTimeoutMs) {
            runCatching { provider.complete(prompt) }.getOrNull()?.trim().orEmpty()
        }
        val latencyMs = (System.nanoTime() - startedAt) / 1_000_000

        return if (!output.isNullOrBlank()) {
            logger.info("llm", "Local LLM request completed", mapOf("provider" to provider.name, "latencyMs" to latencyMs))
            LLMResponse(text = output, provider = provider.name)
        } else {
            logger.warn("llm", "Local LLM request failed", mapOf("provider" to provider.name, "latencyMs" to latencyMs))
            null
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
}