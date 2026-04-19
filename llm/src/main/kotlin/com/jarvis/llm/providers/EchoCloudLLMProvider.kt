package com.jarvis.llm.providers

import com.jarvis.llm.LLMProvider

class EchoCloudLLMProvider : LLMProvider {
    override val name: String = "cloud-echo"

    override suspend fun complete(prompt: String): String {
        val normalized = prompt.trim().ifBlank { "(empty prompt)" }
        return "Cloud stub response: $normalized"
    }
}