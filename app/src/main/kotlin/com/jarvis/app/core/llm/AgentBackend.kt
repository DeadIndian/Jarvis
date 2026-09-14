package com.jarvis.app.core.llm

import android.util.Log
import com.jarvis.app.core.config.AgentConfigRepository
import com.jarvis.app.core.contracts.ActionEvent
import com.jarvis.app.core.mcp.McpClient
import com.jarvis.app.core.mcp.McpTool
import com.jarvis.app.core.memory.ChatTurn
import com.jarvis.app.core.security.ActionValidator
import com.jarvis.app.core.security.ValidationResult
import com.jarvis.app.core.tools.PhoneToolExecutor
import com.jarvis.app.core.tools.ToolCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AgentBackend is the "intelligent" cloud agent: an OpenAI-compatible chat-completions
 * tool loop with phone tools + MCP tools + a Claude-style memory system prompt.
 *
 * Loop: send messages+tools -> if model returns tool_calls, execute each (phone action via
 * validator+executor, or MCP tool) and feed results back -> repeat until the model returns text.
 */
class AgentBackend(
    private val config: AgentConfigRepository,
    private val phoneToolExecutor: PhoneToolExecutor,
    private val mcpClients: List<McpClient>
) {
    private val tag = "AgentBackend"

    companion object {
        private const val MAX_TOOL_ROUNDS = 5

        private val SYSTEM_PROMPT = """
            You are JARVIS, a voice assistant running on the user's phone. You speak your replies aloud, so keep them short, natural, and free of markdown or lists.

            The recent conversation is provided as prior messages — use it for immediate follow-ups ("call him back", "make it louder"). That short-term context is NOT saved; your long-term memory is the note tools below.

            LONG-TERM MEMORY — you have persistent notes via these MCP tools:
            - search_notes(query): find what you know about the user before answering.
            - read_note(title): read a specific note in full.
            - write_note(title, content): save a durable fact.

            How to use them, like a person with a good memory:
            - BEFORE answering anything that depends on who the user is — their preferences, people in their life, habits, past decisions, ongoing projects — call search_notes first. Do not guess or claim ignorance until you have searched.
            - AFTER the user reveals anything durable — a preference, a fact about them, a person (name + relationship + number), a recurring task, a decision — call write_note to save it. Do this proactively, without asking permission, and without announcing it out loud.
            - Keep each note short and give it a clear, findable title (e.g. "Contact: Mom", "Preference: morning alarm 6:30").
            - Update an existing note rather than creating near-duplicates when you can find one.

            PHONE ACTIONS: when the user wants to do something on the phone (call, volume, bluetooth, wifi, media, open app, flashlight, notifications), call the matching tool. For anything you can just answer, reply with text.
        """.trimIndent()
    }

    /**
     * Runs the agent loop over the given conversation window.
     * @return spoken text for TTS, plus phone actions executed (for the session listener).
     */
    suspend fun run(window: List<ChatTurn>): AgentResult {
        val key = config.apiKey ?: return AgentResult("No agent API key configured. Add one in Settings.", emptyList())

        // Gather MCP tools once per turn (namespaced), plus phone tools.
        val mcpTools: List<McpTool> = mcpClients.flatMap { runCatching { it.listTools() }.getOrDefault(emptyList()) }
        val tools = buildToolsArray(mcpTools)

        val messages = JSONArray().put(msg("system", SYSTEM_PROMPT))
        window.forEach { messages.put(msg(it.role, it.content)) }

        val executed = mutableListOf<ActionEvent.PhoneToolAction>()

        repeat(MAX_TOOL_ROUNDS) {
            val response = chatCompletion(key, messages, tools) ?: return AgentResult("I couldn't reach the agent.", executed)
            val choice = response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?: return AgentResult("The agent returned no message.", executed)

            val toolCalls = choice.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                // Final answer.
                return AgentResult(choice.optString("content").ifBlank { "Done." }, executed)
            }

            // Echo the assistant tool-call message back, then append each tool result.
            messages.put(choice)
            for (i in 0 until toolCalls.length()) {
                val call = toolCalls.getJSONObject(i)
                val fn = call.optJSONObject("function") ?: continue
                val name = fn.optString("name")
                val args = parseArgs(fn.optString("arguments"))
                val result = executeTool(name, args, mcpTools, executed)
                messages.put(toolResult(call.optString("id"), name, result))
            }
        }
        return AgentResult("I got stuck running tools for that. Try rephrasing.", executed)
    }

    private fun buildToolsArray(mcpTools: List<McpTool>): JSONArray {
        val arr = ToolCatalog.phoneTools()
        mcpTools.forEach { t ->
            arr.put(ToolCatalog.toOpenAiTool(t.qualifiedName, t.description, t.inputSchema))
        }
        return arr
    }

    /** Executes one tool call and returns a text result to feed back to the model. */
    private suspend fun executeTool(
        name: String,
        args: JSONObject,
        mcpTools: List<McpTool>,
        executed: MutableList<ActionEvent.PhoneToolAction>
    ): String {
        // MCP tool (namespaced mcp__server__tool)?
        val mcpTool = mcpTools.firstOrNull { it.qualifiedName == name }
        if (mcpTool != null) {
            val client = mcpClients.firstOrNull { it.serverName == mcpTool.server.name }
                ?: return "MCP server '${mcpTool.server.name}' not found."
            return client.callTool(mcpTool.name, args)
        }

        // Phone tool.
        if (name in ToolCatalog.phoneToolNames) {
            val action = ActionEvent.PhoneToolAction(tool = name, arguments = args.toArgMap())
            return when (val v = ActionValidator.validate(action)) {
                is ValidationResult.Invalid -> "Rejected: ${v.reason}"
                is ValidationResult.ValidWithConfirmation ->
                    // ponytail: agent path auto-declines confirm-required tools; UI confirmation TODO.
                    "This action needs user confirmation and was not executed: ${v.reason}"
                is ValidationResult.Valid -> {
                    val ok = phoneToolExecutor.executeTool(name, action.arguments)
                    if (ok) { executed.add(action); "Executed $name." } else "Failed to execute $name."
                }
            }
        }
        return "Unknown tool: $name"
    }

    private suspend fun chatCompletion(key: String, messages: JSONArray, tools: JSONArray): JSONObject? =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", config.model)
                .put("messages", messages)
                .put("tools", tools)
                .put("tool_choice", "auto")
            val url = config.baseUrl.trimEnd('/') + "/chat/completions"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 60000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode == 200) {
                    JSONObject(conn.inputStream.bufferedReader().readText())
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                    Log.w(tag, "chat/completions http ${conn.responseCode}: ${err.take(300)}")
                    null
                }
            } catch (e: Exception) {
                Log.w(tag, "chat/completions failed: ${e.message}")
                null
            } finally {
                conn.disconnect()
            }
        }

    private fun msg(role: String, content: String) = JSONObject().put("role", role).put("content", content)
    private fun toolResult(id: String, name: String, content: String) =
        JSONObject().put("role", "tool").put("tool_call_id", id).put("name", name).put("content", content)

    private fun parseArgs(raw: String): JSONObject =
        runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrElse { JSONObject() }
}

data class AgentResult(val text: String, val actions: List<ActionEvent.PhoneToolAction>)

/** Converts a flat JSONObject of arguments into the Map<String, Any> the executor expects. */
private fun JSONObject.toArgMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    keys().forEach { map[it] = get(it) }
    return map
}
