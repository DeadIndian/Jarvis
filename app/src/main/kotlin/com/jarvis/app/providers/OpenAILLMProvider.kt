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
import org.json.JSONArray
import org.json.JSONObject
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

        val responseText = try {
            client.post(url) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, "application/json")
                setBody(TextContent(requestBody.toString(), ContentType.Application.Json))
            }.bodyAsText()
        } catch (exception: Exception) {
            logger.warn(
                stage = "llm",
                message = "OpenAI request failed",
                data = mapOf("error" to exception.message)
            )
            throw ProviderException("OpenAI request failed: ${exception.message}", exception)
        }

        val output = extractOpenAIText(responseText)
        if (output.isBlank()) {
            val errorMsg = parseErrorMessage(responseText) ?: "OpenAI returned an empty response"
            logger.warn("llm", "OpenAI response error", mapOf("error" to errorMsg, "response" to responseText))
            throw ProviderException(errorMsg)
        }
        logger.info("llm", "OpenAI response received", mapOf("outputLength" to output.length))
        return output
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