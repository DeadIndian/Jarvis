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

        if (localProvider == null) {
            if (request.allowCloudFallback) {
                val cloudAttempt = tryCloud(prompt)
                if (cloudAttempt.response != null) {
                    return cloudAttempt.response
                }
                val errorMessage = if (cloudProvider == null) {
                    "No local or cloud LLM provider is configured. Download a local model or add a Gemini API key."
                } else {
                    buildString {
                        append("Cloud LLM provider is configured but failed to respond")
                        cloudAttempt.errorInfo?.let { append(": $it") }
                        append(". Check your Gemini API key or network connectivity.")
                    }
                }
                logger.warn(
                    stage = "llm",
                    message = "No provider could complete request",
                    data = mapOf(
                        "allowCloudFallback" to request.allowCloudFallback,
                        "error" to errorMessage
                    )
                )
                return LLMResponse(text = "Error: $errorMessage", provider = "none")
            }

            val errorMessage = "No local LLM provider initialized (check if model is downloaded and selected)"
            logger.warn(
                stage = "llm",
                message = "No local provider configured",
                data = mapOf("allowCloudFallback" to request.allowCloudFallback, "error" to errorMessage)
            )
            return LLMResponse(text = "Error: $errorMessage", provider = "none")
        }

        val localAttempt = tryLocal(prompt)
        if (localAttempt.response != null) {
            return localAttempt.response
        }

        val cloudAttempt = if (request.allowCloudFallback) tryCloud(prompt) else CloudAttemptResult()
        if (cloudAttempt.response != null) {
            return cloudAttempt.response
        }

        val errorMessage = when {
            request.allowCloudFallback && cloudProvider == null ->
                "Cloud fallback is enabled but no cloud provider is configured. Save a Gemini API key or switch to local mode."
            else -> buildString {
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

    private suspend fun tryCloud(prompt: String): CloudAttemptResult {
        val provider = cloudProvider ?: return CloudAttemptResult()
        val startedAt = System.nanoTime()
        var caughtException: Throwable? = null
        val output = withTimeoutOrNull(cloudTimeoutMs) {
            runCatching {
                provider.complete(prompt).trim().ifBlank { null }
            }.onFailure { caughtException = it }
                .getOrNull()
        }
        val latencyMs = (System.nanoTime() - startedAt) / 1_000_000

        return if (!output.isNullOrBlank()) {
            logger.info("llm", "Cloud fallback request completed", mapOf("provider" to provider.name, "latencyMs" to latencyMs))
            CloudAttemptResult(response = LLMResponse(text = output, provider = provider.name))
        } else {
            val errorInfo = caughtException?.message ?: "Timeout after ${latencyMs}ms"
            logger.warn("llm", "Cloud fallback request failed", mapOf("provider" to provider.name, "latencyMs" to latencyMs, "error" to errorInfo))
            CloudAttemptResult(errorInfo = errorInfo)
        }
    }

    private data class CloudAttemptResult(
        val response: LLMResponse? = null,
        val errorInfo: String? = null
    )

    private data class LocalAttemptResult(
        val response: LLMResponse? = null,
        val errorInfo: String? = null
    )
}
