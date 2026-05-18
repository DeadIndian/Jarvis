package com.jarvis.app.providers

import com.jarvis.llm.LLMException
import com.jarvis.llm.LLMProvider
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.util.concurrent.TimeUnit

class OpenAILLMProvider(
    apiKey: String,
    private val modelName: String = "gpt-4o-mini",
    private val logger: JarvisLogger = NoOpJarvisLogger
) : LLMProvider {
    override val name: String = "OpenAI:$modelName"

    private val apiKey: String = apiKey.trim().takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("OpenAI API key is required")

    private val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
            }
        }
    }

    override suspend fun complete(prompt: String): String {
        val url = "https://api.openai.com/v1/chat/completions"

        val requestBody = JSONObject().apply {
            put("model", modelName)
            put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", prompt)
            ))
            put("temperature", 0.7)
            put("max_tokens", 1024)
        }

        val response = try {
            client.post(url) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, "application/json")
                setBody(TextContent(requestBody.toString(), ContentType.Application.Json))
            }
        } catch (exception: Exception) {
            logger.warn(
                stage = "llm",
                message = "OpenAI request failed",
                data = mapOf("error" to exception.message)
            )
            throw LLMException.NetworkError("OpenAI request failed: ${exception.message}", exception)
        }

        val responseText = response.bodyAsText()
        if (response.status.value !in 200..299) {
            handleErrorResponse(response, responseText)
        }

        val output = extractOpenAIText(responseText)
        if (output.isBlank()) {
            logger.warn("llm", "OpenAI returned an empty response", mapOf("response" to responseText))
            throw LLMException.UnknownError("OpenAI returned an empty response")
        }
        logger.info("llm", "OpenAI response received", mapOf("outputLength" to output.length))
        return output
    }

    private fun handleErrorResponse(response: HttpResponse, body: String) {
        val errorJson = try {
            JSONObject(body).optJSONObject("error")
        } catch (_: Exception) {
            null
        }

        val message = errorJson?.optString("message") ?: "HTTP ${response.status.value}: $body"
        val code = errorJson?.optString("code") ?: ""
        
        val retryAfter = response.headers["retry-after"]?.toLongOrNull()?.let { Duration.ofSeconds(it) }

        when (response.status.value) {
            400 -> throw LLMException.InvalidRequest(message)
            401 -> throw LLMException.AuthenticationFailed(message)
            403 -> throw LLMException.AuthenticationFailed(message)
            429 -> {
                if (code == "insufficient_quota") {
                    throw LLMException.QuotaExceeded(message)
                }
                throw LLMException.RateLimitReached(retryAfter, message)
            }
            in 500..599 -> throw LLMException.ServerError(retryAfter, message)
            else -> throw LLMException.UnknownError(message)
        }
    }

    private fun extractOpenAIText(responseText: String): String {
        return try {
            val root = JSONObject(responseText)
            val choices = root.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                message.getString("content").trim()
            } else {
                ""
            }
        } catch (e: Exception) {
            logger.warn("llm", "Failed to parse OpenAI response", mapOf("error" to e.message))
            ""
        }
    }
}