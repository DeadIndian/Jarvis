package com.jarvis.app

enum class LlmBackendMode(
    val id: String,
    val label: String
) {
    LITERT(id = "litert", label = "LiteRT (MediaPipe)");

    companion object {
        fun fromId(id: String?): LlmBackendMode = LITERT
        fun fromConfig(raw: String?): LlmBackendMode = LITERT
    }
}
