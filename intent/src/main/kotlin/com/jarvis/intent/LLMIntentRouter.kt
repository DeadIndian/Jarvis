package com.jarvis.intent

import com.jarvis.llm.ToolCallingProvider
import com.jarvis.llm.ToolDefinition
import com.jarvis.llm.ToolParameter
import com.jarvis.llm.ToolCallingResponse
import com.jarvis.skills.SkillRegistry
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger

class LLMIntentRouter(
    private val toolCallingProvider: ToolCallingProvider,
    private val skillRegistry: SkillRegistry,
    private val fallbackRouter: IntentRouter? = null,
    private val logger: JarvisLogger = NoOpJarvisLogger
) : IntentRouter {

    override suspend fun parse(input: String): IntentResult {
        val skills = skillRegistry.all()
        val tools = skills.map { skill ->
            ToolDefinition(
                name = skill.name,
                description = skill.description,
                parameters = skill.parameters.associate { it.name to ToolParameter(it.type, it.description, it.required) }
            )
        }

        val response = toolCallingProvider.completeWithTools(input, tools)
        
        return when (response) {
            is ToolCallingResponse.ToolCalls -> {
                val firstCall = response.calls.first()
                IntentResult(
                    intent = firstCall.name,
                    confidence = 0.9,
                    entities = firstCall.arguments
                )
            }
            is ToolCallingResponse.Text -> {
                // If it didn't call a tool, maybe the fallback router can handle it?
                fallbackRouter?.parse(input) ?: IntentResult(
                    intent = "CONVERSATIONAL_RESPONSE",
                    confidence = 0.8,
                    entities = mapOf("text" to response.text)
                )
            }
            is ToolCallingResponse.Error -> {
                logger.error("intent", "LLM Intent parsing failed: ${response.message}")
                fallbackRouter?.parse(input) ?: IntentResult("UNKNOWN", 0.0, emptyMap())
            }
        }
    }
}
