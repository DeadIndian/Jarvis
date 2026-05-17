package com.jarvis.app

import com.jarvis.llm.LLMProvider
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.http.content.TextContent
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiCloudLLMProvider(
    apiKey: String,
    private val modelName: String = "gemini-2.5-flash",
    private val logger: JarvisLogger = NoOpJarvisLogger
) : LLMProvider {
    override val name: String = "GeminiCloud"
    private val apiKey: String = apiKey.trim().takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Gemini API key is required")
    private val client = HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
            }
        }
    }

    override suspend fun complete(prompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 512)
                put("topP", 0.9)
            })
        }.toString()

        logger.info("llm", "Gemini cloud request", mapOf(
            "url" to url.take(50) + "...",
            "modelName" to modelName,
            "body" to requestBody.take(100)
        ))

        val response = try {
            client.post(url) {
                header(HttpHeaders.UserAgent, "Jarvis/1.0 (Android)")
                setBody(TextContent(requestBody, ContentType.Application.Json))
            }
        } catch (exception: Exception) {
            logger.warn(
                stage = "llm",
                message = "Gemini cloud network error",
                data = mapOf("error" to (exception.message ?: "unknown"))
            )
            throw IllegalStateException("Gemini cloud request failed: ${exception.message}", exception)
        }

        val responseStatus = response.status
        val responseText = response.bodyAsText()

        logger.info("llm", "Gemini cloud response", mapOf(
            "status" to responseStatus.value.toString(),
            "response" to responseText.take(500)
        ))

        if (responseStatus.value !in 200..299) {
            val errorMsg = try {
                val errorJson = JSONObject(responseText)
                errorJson.optJSONObject("error")?.optString("message") ?: "HTTP ${responseStatus.value}"
            } catch (_: Exception) {
                "HTTP ${responseStatus.value}: $responseText"
            }
            throw IllegalStateException("Gemini API error: $errorMsg")
        }

        return extractGeminiText(responseText)
    }

    private fun extractGeminiText(responseText: String): String {
        val root = try {
            JSONObject(responseText)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse Gemini response as JSON: ${e.message}. Raw: ${responseText.take(200)}")
        }

        // Check for error in response
        if (root.has("error")) {
            val error = root.getJSONObject("error")
            val message = error.optString("message", "Unknown error")
            throw IllegalStateException("Gemini API error: $message")
        }

        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val promptFeedback = root.optJSONObject("promptFeedback")
            val blockReason = promptFeedback?.optString("blockReason")
            if (!blockReason.isNullOrBlank()) {
                throw IllegalStateException("Gemini blocked the request: $blockReason")
            }
            throw IllegalStateException("Gemini returned no candidates. Check your prompt or safety settings. Raw: ${responseText.take(200)}")
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        if (parts == null || parts.length() == 0) {
            val finishReason = firstCandidate.optString("finishReason")
            throw IllegalStateException("Gemini candidate has no content. Finish reason: $finishReason")
        }

        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i)
            val text = part?.optString("text")
            if (!text.isNullOrBlank()) {
                sb.append(text)
            }
        }

        val output = sb.toString().trim()
        if (output.isBlank()) {
            val finishReason = firstCandidate.optString("finishReason")
            throw IllegalStateException("Gemini returned an empty text part. Finish reason: $finishReason")
        }

        return output
    }
}
