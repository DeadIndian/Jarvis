package com.jarvis.intent

data class ClassifierTrace(
    val input: String,
    val source: String,
    val intent: String,
    val confidence: Double,
    val entities: Map<String, String>,
    val rawOutput: String? = null
)
