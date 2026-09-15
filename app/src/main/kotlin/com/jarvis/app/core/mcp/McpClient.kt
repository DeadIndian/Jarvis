package com.jarvis.app.core.mcp

import android.util.Log
import com.jarvis.app.core.config.McpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/** A tool advertised by an MCP server, with the server it lives on. */
data class McpTool(
    val server: McpServer,
    val name: String,
    val description: String,
    val inputSchema: JSONObject
) {
    /** Namespaced so tools from different servers don't collide in the agent's tool list. */
    val qualifiedName: String get() = "mcp__${server.name}__$name"
}

/**
 * Minimal MCP client speaking Streamable-HTTP (2024-11-05):
 *   initialize -> capture mcp-session-id header -> notifications/initialized -> tools/list / tools/call
 *
 * ponytail: one client per server, session established lazily on first use and cached.
 *           No reconnect/backoff — a dead session just re-initializes on next call.
 */
class McpClient(private val server: McpServer) {

    val serverName: String get() = server.name

    private val tag = "McpClient"
    private val idSeq = AtomicInteger(0)
    @Volatile private var sessionId: String? = null

    /** Establishes a session if not already open. Returns true on success. */
    suspend fun ensureSession(): Boolean {
        if (sessionId != null) return true
        return withContext(Dispatchers.IO) {
            val initParams = JSONObject()
                .put("protocolVersion", "2024-11-05")
                .put("capabilities", JSONObject())
                .put("clientInfo", JSONObject().put("name", "jarvis").put("version", "1.0"))
            val (code, body, newSession) = rpc("initialize", initParams, includeSessionHeader = false)
            if (code != 200 || newSession == null) {
                Log.w(tag, "initialize failed (${server.name}): http $code")
                return@withContext false
            }
            sessionId = newSession
            // Per spec, follow up with the initialized notification (no response expected).
            runCatching { rpc("notifications/initialized", JSONObject(), isNotification = true) }
            true
        }
    }

    suspend fun listTools(): List<McpTool> {
        if (!ensureSession()) return emptyList()
        return withContext(Dispatchers.IO) {
            val (code, body, _) = rpc("tools/list", JSONObject())
            if (code != 200 || body == null) return@withContext emptyList()
            val tools = JSONObject(body).optJSONObject("result")?.optJSONArray("tools") ?: JSONArray()
            (0 until tools.length()).map { i ->
                val t = tools.getJSONObject(i)
                McpTool(
                    server = server,
                    name = t.getString("name"),
                    description = t.optString("description", ""),
                    inputSchema = t.optJSONObject("inputSchema") ?: JSONObject().put("type", "object")
                )
            }
        }
    }

    /** Calls a tool by its raw (un-namespaced) name. Returns the text content of the result. */
    suspend fun callTool(name: String, arguments: JSONObject): String {
        if (!ensureSession()) return "MCP server ${server.name} unavailable."
        return withContext(Dispatchers.IO) {
            val params = JSONObject().put("name", name).put("arguments", arguments)
            val (code, body, _) = rpc("tools/call", params)
            if (code != 200 || body == null) return@withContext "MCP call failed (http $code)."
            extractText(JSONObject(body))
        }
    }

    private fun extractText(response: JSONObject): String {
        val result = response.optJSONObject("result")
            ?: return response.optJSONObject("error")?.optString("message") ?: "No result."
        val content = result.optJSONArray("content") ?: return result.toString()
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") sb.append(block.optString("text"))
        }
        return sb.toString().ifBlank { result.toString() }
    }

    /**
     * Sends one JSON-RPC request. Returns (httpCode, bodyString-or-null, sessionIdHeader-or-null).
     * Handles both `application/json` and `text/event-stream` (SSE) response bodies.
     */
    private fun rpc(
        method: String,
        params: JSONObject,
        includeSessionHeader: Boolean = true,
        isNotification: Boolean = false
    ): Triple<Int, String?, String?> {
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("method", method)
            .apply { if (!isNotification) put("id", idSeq.incrementAndGet()) }
            .put("params", params)

        val conn = (URL(server.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10000
            readTimeout = 30000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json, text/event-stream")
            server.apiKey?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (includeSessionHeader) sessionId?.let { setRequestProperty("mcp-session-id", it) }
        }
        return try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val newSession = conn.getHeaderField("mcp-session-id")
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.readText().orEmpty()
            val body = if (conn.contentType?.contains("event-stream") == true) parseSse(raw) else raw
            Triple(code, body, newSession)
        } catch (e: Exception) {
            Log.w(tag, "rpc $method failed (${server.name}): ${e.message}")
            Triple(-1, null, null)
        } finally {
            conn.disconnect()
        }
    }

    /** Pulls the JSON out of the last `data:` line of an SSE stream. */
    private fun parseSse(raw: String): String =
        raw.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .lastOrNull { it.isNotBlank() }
            ?: raw
}
