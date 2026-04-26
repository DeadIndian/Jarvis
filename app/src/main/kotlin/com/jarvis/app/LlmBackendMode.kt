package com.jarvis.app

enum class LlmBackendMode(
    val id: String,
    val label: String
) {
    JARVIS_LITERT(id = "litert", label = "Jarvis LiteRT (MediaPipe)"),
    GOOGLE_AICORE(id = "google-aicore", label = "Google Gemini Nano via AICore"),
    GEMINI_CLOUD(id = "gemini-cloud", label = "Gemini cloud only");

    companion object {
        fun fromLegacyConfig(raw: String?): LlmBackendMode {
            return when (raw?.trim()?.lowercase()) {
                "local-only", "local", "litert" -> JARVIS_LITERT
                "gemini", "cloud-only", "cloud" -> GEMINI_CLOUD
                "google-aicore", "aicore" -> GOOGLE_AICORE
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
