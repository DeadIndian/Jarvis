package com.jarvis.app.providers

import com.jarvis.llm.LLMProvider
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AnthropicLLMProvider(
    apiKey: String,
    private val modelName: String = "claude-3-haiku-20240307",
    private val logger: JarvisLogger = NoOpJarvisLogger
) : LLMProvider {
    override val name: String = "Anthropic:$modelName"

    private val apiKey: String = apiKey.trim().takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Anthropic API key is required")

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
        val url = "https://api.anthropic.com/v1/messages"

        val requestBody = JSONObject().apply {
            put("model", modelName)
            put("messages", org.json.JSONArray().put(
                JSONObject().put("role", "user").put("content", prompt)
            ))
            put("max_tokens", 1024)
            put("temperature", 0.7)
        }

        val responseText = try {
            client.post(url) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                header(HttpHeaders.ContentType, "application/json")
                setBody(TextContent(requestBody.toString(), ContentType.Application.Json))
            }.bodyAsText()
        } catch (exception: Exception) {
            logger.warn(
                stage = "llm",
                message = "Anthropic request failed",
                data = mapOf("error" to exception.message)
            )
            throw ProviderException("Anthropic request failed: ${exception.message}", exception)
        }

        val output = extractAnthropicText(responseText)
        if (output.isBlank()) {
            val errorMsg = parseErrorMessage(responseText) ?: "Anthropic returned an empty response"
            logger.warn("llm", "Anthropic response error", mapOf("error" to errorMsg, "response" to responseText))
            throw ProviderException(errorMsg)
        }
        logger.info("llm", "Anthropic response received", mapOf("outputLength" to output.length))
        return output
    }

    private fun extractAnthropicText(responseText: String): String {
        return try {
            val root = JSONObject(responseText)
            val content = root.getJSONArray("content")
            if (content.length() > 0) {
                val firstBlock = content.getJSONObject(0)
                if (firstBlock.has("text")) {
                    firstBlock.getString("text").trim()
                } else {
                    ""
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            logger.warn("llm", "Failed to parse Anthropic response", mapOf("error" to e.message))
            ""
        }
    }

    private fun parseErrorMessage(responseText: String): String? {
        return try {
            val root = JSONObject(responseText)
            val error = root.optJSONObject("error")
            error?.optString("message")
        } catch (e: Exception) {
            null
        }
    }

    class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
}