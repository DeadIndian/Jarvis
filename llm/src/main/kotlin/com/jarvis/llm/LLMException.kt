package com.jarvis.llm

import java.time.Duration

/**
 * Base class for all LLM-related exceptions.
 */
sealed class LLMException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * Thrown when the rate limit for the API is reached (e.g., HTTP 429).
     */
    class RateLimitReached(
        val retryAfter: Duration? = null,
        message: String,
        cause: Throwable? = null
    ) : LLMException(message, cause)

    /**
     * Thrown when authentication fails (e.g., invalid API key, HTTP 401).
     */
    class AuthenticationFailed(message: String, cause: Throwable? = null) : LLMException(message, cause)

    /**
     * Thrown when the user has exceeded their quota or billing limit (e.g., HTTP 403 or 429 with specific message).
     */
    class QuotaExceeded(message: String, cause: Throwable? = null) : LLMException(message, cause)

    /**
     * Thrown when there are insufficient credits to complete the request.
     */
    class InsufficientCredits(message: String, cause: Throwable? = null) : LLMException(message, cause)

    /**
     * Thrown when the request is invalid (e.g., malformed JSON, invalid parameters, HTTP 400).
     */
    class InvalidRequest(message: String, cause: Throwable? = null) : LLMException(message, cause)

    /**
     * Thrown when the server is overloaded or experiencing an internal error (e.g., HTTP 500, 503, 529).
     */
    class ServerError(
        val retryAfter: Duration? = null,
        message: String,
        cause: Throwable? = null
    ) : LLMException(message, cause)

    /**
     * Thrown when a network-level error occurs (e.g., timeout, no connection).
     */
    class NetworkError(message: String, cause: Throwable) : LLMException(message, cause)

    /**
     * Thrown when the content was blocked by safety filters.
     */
    class ContentBlocked(val reason: String, message: String) : LLMException(message)

    /**
     * Thrown for any other error that doesn't fit the above categories.
     */
    class UnknownError(message: String, cause: Throwable? = null) : LLMException(message, cause)
}
