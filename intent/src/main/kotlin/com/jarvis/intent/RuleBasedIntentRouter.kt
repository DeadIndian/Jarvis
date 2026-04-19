package com.jarvis.intent

class RuleBasedIntentRouter : IntentRouter {
    override suspend fun parse(input: String): IntentResult {
        val normalized = input.lowercase()
        return when {
            normalized.contains("open ") -> {
                val app = input.substringAfter("open ", missingDelimiterValue = "").trim()
                IntentResult(
                    intent = "OPEN_APP",
                    confidence = 0.95,
                    entities = mapOf("app" to app.ifBlank { "unknown" })
                )
            }

            else -> IntentResult(
                intent = "UNKNOWN",
                confidence = 0.3,
                entities = emptyMap()
            )
        }
    }
}
