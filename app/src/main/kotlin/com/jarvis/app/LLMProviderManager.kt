package com.jarvis.app

import com.jarvis.llm.LLMException
import com.jarvis.llm.LLMProvider
import com.jarvis.llm.LLMRequest
import com.jarvis.llm.LLMResponse
import com.jarvis.llm.LLMRouter
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class LLMProviderManager(
    private val logger: JarvisLogger = NoOpJarvisLogger
) : LLMRouter {
    private val providerStates = ConcurrentHashMap<String, ProviderState>()
    private val mutex = Mutex()
    private var providers: List<LLMProvider> = emptyList()

    data class ProviderState(
        val name: String,
        var cooldownUntil: Instant? = null,
        var lastError: String? = null,
        var errorCount: Int = 0
    )

    // For backward compatibility with code expecting LLMRouter
    override suspend fun complete(request: LLMRequest): LLMResponse = routeRequest(request)

    fun registerProviders(vararg providers: LLMProvider) {
        this.providers = providers.toList()
        providers.forEach { provider ->
            providerStates.getOrPut(provider.name) {
                ProviderState(name = provider.name)
            }
        }
        logger.info("llm", "Registered providers", mapOf("providers" to providers.map { it.name }))
    }

    fun setProviderPriority(priority: List<String>) {
        val current = providers.toMutableList()
        current.sortBy { provider ->
            val idx = priority.indexOf(provider.name)
            if (idx >= 0) idx else Int.MAX_VALUE
        }
        providers = current
        logger.info("llm", "Provider priority set", mapOf("order" to providers.map { it.name }))
    }

    suspend fun routeRequest(request: LLMRequest): LLMResponse {
        require(providers.isNotEmpty()) { "No providers registered" }

        val availableProviders = providers.filter { !isOnCooldown(it.name) }
        if (availableProviders.isEmpty()) {
            val nextCooldown = providerStates.values
                .filter { it.cooldownUntil != null }
                .minByOrNull { it.cooldownUntil!! }
            val waitTime = nextCooldown?.let { Duration.between(Instant.now(), it.cooldownUntil!!).toMinutes() } ?: 0
            val msg = "All LLM providers on cooldown. Try again in ${waitTime} minutes."
            logger.warn("llm", msg, emptyMap())
            return LLMResponse(text = "Error: $msg", provider = "none")
        }

        val triedProviders = mutableListOf<String>()
        var lastError: Throwable? = null

        for (provider in availableProviders) {
            triedProviders.add(provider.name)

            try {
                logger.info("llm", "Attempting provider", mapOf("provider" to provider.name))
                val result = provider.complete(request.prompt)
                clearError(provider.name)
                logger.info("llm", "Provider succeeded", mapOf("provider" to provider.name))
                return LLMResponse(text = result, provider = provider.name)
            } catch (e: Exception) {
                lastError = e
                val errorType = detectErrorType(e)
                logger.warn("llm", "Provider failed", mapOf(
                    "provider" to provider.name,
                    "errorType" to errorType.name,
                    "message" to e.message
                ))
                recordFailure(provider.name, errorType, e)
            }
        }

        val errorMsg = buildString {
            append("All providers failed. Tried: ")
            append(triedProviders.joinToString(", "))
            lastError?.message?.let { append(". Last error: $it") }
        }
        logger.error("llm", errorMsg, lastError)
        return LLMResponse(text = "Error: $errorMsg", provider = "none")
    }

    fun getProviderCooldown(providerName: String): Duration? {
        val state = providerStates[providerName] ?: return null
        return state.cooldownUntil?.let { Duration.between(Instant.now(), it) }
    }

    fun isOnCooldown(providerName: String): Boolean {
        val state = providerStates[providerName] ?: return false
        return state.cooldownUntil?.let { it.isAfter(Instant.now()) } ?: false
    }

    fun getProviderStatus(): Map<String, ProviderStatus> {
        return providerStates.mapValues { (name, state) ->
            val cooldownRemaining = state.cooldownUntil?.let { Duration.between(Instant.now(), it) }
            ProviderStatus(
                name = name,
                onCooldown = state.cooldownUntil?.isAfter(Instant.now()) ?: false,
                cooldownRemainingMinutes = cooldownRemaining?.toMinutes(),
                lastError = state.lastError,
                errorCount = state.errorCount
            )
        }
    }

    private suspend fun clearError(providerName: String) {
        mutex.withLock {
            providerStates[providerName]?.apply {
                cooldownUntil = null
                lastError = null
                errorCount = 0
            }
        }
    }

    private suspend fun recordFailure(providerName: String, errorType: ErrorType, e: Exception) {
        mutex.withLock {
            val state = providerStates.getOrPut(providerName) { ProviderState(name = providerName) }
            state.lastError = e.message
            state.errorCount++

            val customRetryAfter = if (e is LLMException.RateLimitReached) e.retryAfter else null
            
            state.cooldownUntil = when {
                customRetryAfter != null -> Instant.now().plus(customRetryAfter)
                errorType == ErrorType.RATE_LIMIT -> Instant.now().plus(Duration.ofMinutes(15))
                errorType == ErrorType.CREDIT_EXHAUSTED -> Instant.now().plus(Duration.ofDays(1))
                errorType == ErrorType.QUOTA_EXCEEDED -> Instant.now().plus(Duration.ofHours(5))
                errorType == ErrorType.AUTH_INVALID -> Instant.now().plus(Duration.ofDays(7))
                errorType == ErrorType.SERVER_ERROR -> Instant.now().plus(Duration.ofMinutes(5))
                errorType == ErrorType.NETWORK_ERROR -> Instant.now().plus(Duration.ofMinutes(1))
                else -> Instant.now().plus(Duration.ofMinutes(10))
            }

            logger.info("llm", "Provider cooldown set", mapOf(
                "provider" to providerName,
                "errorType" to errorType.name,
                "cooldownMinutes" to Duration.between(Instant.now(), state.cooldownUntil).toMinutes(),
                "message" to (e.message ?: "")
            ))
        }
    }

    private fun detectErrorType(e: Exception): ErrorType {
        if (e is LLMException) {
            return when (e) {
                is LLMException.RateLimitReached -> ErrorType.RATE_LIMIT
                is LLMException.AuthenticationFailed -> ErrorType.AUTH_INVALID
                is LLMException.QuotaExceeded -> ErrorType.QUOTA_EXCEEDED
                is LLMException.InsufficientCredits -> ErrorType.CREDIT_EXHAUSTED
                is LLMException.InvalidRequest -> ErrorType.UNKNOWN
                is LLMException.ServerError -> ErrorType.SERVER_ERROR
                is LLMException.NetworkError -> ErrorType.NETWORK_ERROR
                is LLMException.ContentBlocked -> ErrorType.UNKNOWN
                is LLMException.UnknownError -> ErrorType.UNKNOWN
            }
        }

        val message = (e.message ?: "").lowercase()
        return when {
            message.contains("insufficient_quota") ||
            message.contains("billing") ||
            message.contains("credit") ||
            message.contains("exceeded") -> ErrorType.CREDIT_EXHAUSTED

            message.contains("rate_limit") ||
            message.contains("rate limit") ||
            message.contains("too many requests") ||
            message.contains("429") -> ErrorType.RATE_LIMIT

            message.contains("quota") ||
            message.contains("monthly limit") ||
            message.contains("daily limit") -> ErrorType.QUOTA_EXCEEDED

            message.contains("invalid api key") ||
            message.contains("authentication") ||
            message.contains("unauthorized") ||
            message.contains("401") ||
            message.contains("403") -> ErrorType.AUTH_INVALID

            message.contains("500") ||
            message.contains("502") ||
            message.contains("503") ||
            message.contains("server error") ||
            message.contains("internal error") -> ErrorType.SERVER_ERROR

            message.contains("connection") ||
            message.contains("timeout") ||
            message.contains("network") ||
            message.contains("no internet") -> ErrorType.NETWORK_ERROR

            else -> ErrorType.UNKNOWN
        }
    }

    data class ProviderStatus(
        val name: String,
        val onCooldown: Boolean,
        val cooldownRemainingMinutes: Long?,
        val lastError: String?,
        val errorCount: Int
    )

    enum class ErrorType {
        RATE_LIMIT,
        CREDIT_EXHAUSTED,
        QUOTA_EXCEEDED,
        AUTH_INVALID,
        SERVER_ERROR,
        NETWORK_ERROR,
        UNKNOWN
    }
}
