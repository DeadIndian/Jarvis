package com.jarvis.llm.providers

import com.jarvis.llm.LLMProvider

class EchoLocalLLMProvider : LLMProvider {
    override val name: String = "local-echo"

    override suspend fun complete(prompt: String): String {
        val normalized = prompt.trim().ifBlank { "(empty prompt)" }
        return "Local stub response: $normalized"
    }
}