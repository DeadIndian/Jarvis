package com.jarvis.llm

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParameter>
)

data class ToolParameter(
    val type: String,
    val description: String,
    val required: Boolean = true
)

data class ToolCall(
    val name: String,
    val arguments: Map<String, String>
)

interface ToolCallingProvider : LLMProvider {
    suspend fun completeWithTools(
        prompt: String,
        tools: List<ToolDefinition>
    ): ToolCallingResponse
}

sealed class ToolCallingResponse {
    data class Text(val text: String) : ToolCallingResponse()
    data class ToolCalls(val calls: List<ToolCall>) : ToolCallingResponse()
    data class Error(val message: String) : ToolCallingResponse()
}
