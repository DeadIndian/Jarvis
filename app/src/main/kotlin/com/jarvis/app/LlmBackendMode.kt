package com.jarvis.app

enum class LlmBackendMode(
    val id: String,
    val label: String
) {
    JARVIS_LITERT(id = "litert", label = "Jarvis LiteRT (MediaPipe)"),
    HYBRID(id = "hybrid", label = "Jarvis LiteRT + Cloud fallback"),
    GOOGLE_AICORE(id = "google-aicore", label = "Google Gemini Nano via AICore"),
    GEMINI_CLOUD(id = "gemini-cloud", label = "Gemini cloud only"),
    OPENAI_CLOUD(id = "openai-cloud", label = "OpenAI GPT only"),
    ANTHROPIC_CLOUD(id = "anthropic-cloud", label = "Anthropic Claude only");

    companion object {
        fun fromLegacyConfig(raw: String?): LlmBackendMode {
            return when (raw?.trim()?.lowercase()) {
                "local-only", "local", "litert" -> JARVIS_LITERT
                "hybrid" -> HYBRID
                "gemini", "cloud-only", "cloud" -> GEMINI_CLOUD
                "google-aicore", "aicore" -> GOOGLE_AICORE
                "openai", "openai-cloud" -> OPENAI_CLOUD
                "anthropic", "anthropic-cloud" -> ANTHROPIC_CLOUD
                else -> JARVIS_LITERT
            }
        }

        fun fromId(id: String?): LlmBackendMode {
            return entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }
                ?: fromLegacyConfig(id)
        }

        fun fromConfig(raw: String?): LlmBackendMode {
            return fromId(raw)
        }
    }
}
