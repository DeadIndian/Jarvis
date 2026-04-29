package com.jarvis.app

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

class GeminiCloudLLMProvider(
    apiKey: String,
    private val modelName: String = "models/text-bison-001",
    private val logger: JarvisLogger = NoOpJarvisLogger
) : LLMProvider {
    override val name: String = "GeminiCloud"
    private val apiKey: String = apiKey.trim().takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Gemini API key is required")
    private val client = HttpClient(OkHttp)

    override suspend fun complete(prompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta2/$modelName:generateText?key=$apiKey"
        val requestBody = JSONObject().apply {
            put("prompt", JSONObject().put("text", prompt))
            put("temperature", 0.7)
            put("maxOutputTokens", 512)
            put("topP", 0.9)
        }.toString()

        val responseText = try {
            client.post(url) {
                setBody(TextContent(requestBody, ContentType.Application.Json))
            }.bodyAsText()
        } catch (exception: Exception) {
            logger.warn(
                stage = "llm",
                message = "Gemini cloud request failed",
                data = mapOf("error" to exception.message)
            )
            throw IllegalStateException("Gemini cloud request failed: ${exception.message}", exception)
        }

        val output = extractGeminiText(responseText)
        if (output.isBlank()) {
            logger.warn("llm", "Gemini cloud response contained no text", mapOf("response" to responseText))
            throw IllegalStateException("Gemini cloud returned an empty response")
        }
        logger.info("llm", "Gemini cloud response received", mapOf("outputLength" to output.length))
        return output
    }

    private fun extractGeminiText(responseText: String): String {
        return try {
            val root = JSONObject(responseText)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val output = firstCandidate?.optString("output")
            if (!output.isNullOrBlank()) {
                output.trim()
            } else {
                firstCandidate?.optString("content")?.trim().orEmpty()
            }
        } catch (exception: Exception) {
            logger.warn("llm", "Failed to parse Gemini cloud response", mapOf("error" to exception.message))
            ""
        }
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
