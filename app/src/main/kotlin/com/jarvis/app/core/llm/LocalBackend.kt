package com.jarvis.app.core.llm

import android.content.Context
import android.util.Log
import com.jarvis.app.core.memory.ChatTurn

sealed interface LLMResult {
    data class Text(val text: String) : LLMResult
    data object Unavailable : LLMResult
}

interface LLMBackend {
    /** @param context recent conversation turns (oldest→newest), for follow-up continuity. */
    suspend fun complete(prompt: String, context: List<ChatTurn>): LLMResult
}

/**
 * LocalBackend: on-device LLM (LiteRT-LM) + BYO-key cloud providers.
 * Degrades gracefully: no model asset → no cloud key → returns Unavailable.
 * Long-term memory lives in basic-memory (MCP) via the cloud agent, not here.
 */
class LocalBackend(
    private val context: Context,
    private val modelPath: String? = null,
    private val geminiKey: String? = null,
    private val openaiKey: String? = null,
    private val anthropicKey: String? = null
) : LLMBackend {

    private val tag = "LocalBackend"

    override suspend fun complete(prompt: String, context: List<ChatTurn>): LLMResult {
        // 1. Try on-device LiteRT model
        val modelResult = modelPath?.let { tryOnDevice(it, prompt, context) }
        if (modelResult != null && modelResult.isNotBlank()) {
            return LLMResult.Text(modelResult)
        }

        // 2. Try BYO cloud keys
        val cloudResult = tryCloud(prompt, context)
        if (cloudResult != null && cloudResult.isNotBlank()) {
            return LLMResult.Text(cloudResult)
        }

        Log.w(tag, "No local model or cloud keys available")
        return LLMResult.Unavailable
    }

    private suspend fun tryOnDevice(modelPath: String, prompt: String, ctx: List<ChatTurn>): String? {
        // Inference is CPU-heavy and blocking; keep it off the main thread (was causing tid==pid).
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val liteRt = com.jarvis.app.LiteRtRuntimeBridge(context)
                liteRt.complete(modelPath, transcriptPrompt(ctx, prompt))
            } catch (e: Exception) {
                Log.w(tag, "On-device model failed: ${e.message}")
                null
            }
        }
    }

    private suspend fun tryCloud(prompt: String, ctx: List<ChatTurn>): String? {
        return when {
            geminiKey != null -> callGemini(geminiKey, prompt, ctx)
            openaiKey != null -> callOpenAI(openaiKey, prompt, ctx)
            anthropicKey != null -> callAnthropic(anthropicKey, prompt, ctx)
            else -> null
        }
    }

    private suspend fun callGemini(key: String, prompt: String, ctx: List<ChatTurn>): String? =
        try {
            val json = org.json.JSONObject().put("contents", org.json.JSONArray().put(
                org.json.JSONObject().put("parts", org.json.JSONArray().put(
                    org.json.JSONObject().put("text", transcriptPrompt(ctx, prompt))
                ))
            ))
            val body = json.toString()
            httpPost("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key", body)
        } catch (e: Exception) { Log.w(tag, "Gemini call failed: ${e.message}"); null }

    private suspend fun callOpenAI(key: String, prompt: String, ctx: List<ChatTurn>): String? =
        try {
            // OpenAI supports real roles; map turns directly and append the new prompt.
            val messages = org.json.JSONArray()
            ctx.forEach { messages.put(org.json.JSONObject().put("role", it.role).put("content", it.content)) }
            messages.put(org.json.JSONObject().put("role", "user").put("content", prompt))
            val json = org.json.JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", messages)
                put("max_tokens", 512)
            }
            httpPost("https://api.openai.com/v1/chat/completions", json.toString(), extraHeaders = mapOf("Authorization" to "Bearer $key"))
        } catch (e: Exception) { Log.w(tag, "OpenAI call failed: ${e.message}"); null }

    private suspend fun callAnthropic(key: String, prompt: String, ctx: List<ChatTurn>): String? =
        try {
            val messages = org.json.JSONArray()
            ctx.forEach { messages.put(org.json.JSONObject().put("role", it.role).put("content", it.content)) }
            messages.put(org.json.JSONObject().put("role", "user").put("content", prompt))
            val json = org.json.JSONObject().apply {
                put("model", "claude-3-5-haiku-20241022")
                put("max_tokens", 512)
                put("messages", messages)
            }
            httpPost("https://api.anthropic.com/v1/messages", json.toString(), extraHeaders = mapOf(
                "x-api-key" to key,
                "anthropic-version" to "2023-06-01"
            ))
        } catch (e: Exception) { Log.w(tag, "Anthropic call failed: ${e.message}"); null }

    /** Flattens recent turns + the new prompt into a single string for text-only backends. */
    private fun transcriptPrompt(ctx: List<ChatTurn>, prompt: String): String {
        if (ctx.isEmpty()) return prompt
        val history = ctx.joinToString("\n") { "${it.role}: ${it.content}" }
        return "$history\nuser: $prompt"
    }

    private suspend fun httpPost(url: String, body: String, extraHeaders: Map<String, String> = emptyMap()): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")
            extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().readText()
                parseText(resp)
            } else {
                // Surface the failure instead of returning "" silently.
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                Log.w(tag, "HTTP ${conn.responseCode} from $url: ${err.take(300)}")
                ""
            }
        }

    private fun parseText(response: String): String {
        val json = org.json.JSONObject(response)
        return json.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
            ?.optString("text")
            ?: json.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content")
            ?: json.optJSONArray("content")?.optJSONObject(0)?.optString("text")  // Anthropic: content is a block array
            ?: json.optString("content", "")
    }
}

object LLMProviderManager {
    private var geminiKey: String? = null
    private var openaiKey: String? = null
    private var anthropicKey: String? = null

    fun configure(gemini: String? = null, openai: String? = null, anthropic: String? = null) {
        geminiKey = gemini
        openaiKey = openai
        anthropicKey = anthropic
    }

    fun hasAny(): Boolean = geminiKey != null || openaiKey != null || anthropicKey != null
}
