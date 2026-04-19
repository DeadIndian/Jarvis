package com.jarvis.intent

data class IntentResult(
    val intent: String,
    val confidence: Double,
    val entities: Map<String, String>
)

interface IntentRouter {
    suspend fun parse(input: String): IntentResult
}
