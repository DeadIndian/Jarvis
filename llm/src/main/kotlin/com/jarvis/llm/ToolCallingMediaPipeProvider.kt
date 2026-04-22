package com.jarvis.llm

import android.content.Context
import com.jarvis.llm.providers.MediaPipeLLMProvider
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import org.json.JSONArray
import org.json.JSONObject

class ToolCallingMediaPipeProvider(
    context: Context,
    modelPath: String,
    private val logger: JarvisLogger = NoOpJarvisLogger
) : ToolCallingProvider {
    private val baseProvider = MediaPipeLLMProvider(context, modelPath, logger)
    override val name: String = "mediapipe-tool-calling"

    override suspend fun complete(prompt: String): String {
        return baseProvider.complete(prompt)
    }

    suspend fun warmup(): Boolean {
        return baseProvider.warmup()
    }

    override suspend fun completeWithTools(
        prompt: String,
        tools: List<ToolDefinition>
    ): ToolCallingResponse {
        val systemPrompt = buildSystemPrompt(tools)
        val fullPrompt = "$systemPrompt\n\nUser: $prompt\n\nAssistant:"
        
        val response = baseProvider.complete(fullPrompt).trim()
        
        return try {
            if (response.startsWith("{") || response.contains("\"tool_calls\"")) {
                // Try to parse as JSON tool call
                val json = try {
                    JSONObject(response)
                } catch (e: Exception) {
                    // Fallback: search for JSON block
                    val start = response.indexOf("{")
                    val end = response.lastIndexOf("}")
                    if (start != -1 && end != -1) {
                        JSONObject(response.substring(start, end + 1))
                    } else {
                        null
                    }
                }

                if (json != null && json.has("tool_calls")) {
                    val callsArray = json.getJSONArray("tool_calls")
                    val toolCalls = mutableListOf<ToolCall>()
                    for (i in 0 until callsArray.length()) {
                        val obj = callsArray.getJSONObject(i)
                        val name = obj.getString("name")
                        val argsObj = obj.optJSONObject("arguments") ?: JSONObject()
                        val args = mutableMapOf<String, String>()
                        argsObj.keys().forEach { key ->
                            args[key] = argsObj.get(key).toString()
                        }
                        toolCalls.add(ToolCall(name, args))
                    }
                    ToolCallingResponse.ToolCalls(toolCalls)
                } else if (json != null && json.has("thought") && json.has("response")) {
                    ToolCallingResponse.Text(json.getString("response"))
                } else {
                    ToolCallingResponse.Text(response)
                }
            } else {
                ToolCallingResponse.Text(response)
            }
        } catch (e: Exception) {
            logger.error("llm", "Failed to parse tool calling response", e)
            ToolCallingResponse.Text(response)
        }
    }

    private fun buildSystemPrompt(tools: List<ToolDefinition>): String {
        val toolsJson = JSONArray()
        tools.forEach { tool ->
            val toolObj = JSONObject()
            toolObj.put("name", tool.name)
            toolObj.put("description", tool.description)
            val paramsObj = JSONObject()
            tool.parameters.forEach { (name, param) ->
                val p = JSONObject()
                p.put("type", param.type)
                p.put("description", param.description)
                p.put("required", param.required)
                paramsObj.put(name, p)
            }
            toolObj.put("parameters", paramsObj)
            toolsJson.put(toolObj)
        }

        return """
            You are Jarvis, a helpful assistant.
            You have access to the following tools:
            ${toolsJson.toString(2)}
            
            When you need to use a tool, respond with a JSON object containing a "tool_calls" array.
            Each tool call must have "name" and "arguments".
            Example: {"tool_calls": [{"name": "AppLauncher", "arguments": {"app": "WhatsApp"}}]}
            
            If you can answer without tools, just respond with the text.
            Always prefer using tools if the user request matches a tool description.
        """.trimIndent()
    }
    
    fun close() {
        baseProvider.close()
    }
}
