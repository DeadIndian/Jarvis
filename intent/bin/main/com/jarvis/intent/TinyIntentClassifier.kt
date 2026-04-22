package com.jarvis.intent

class TinyIntentClassifier {
    fun classify(input: String): IntentResult? {
        val normalized = input.lowercase().trim()
        if (normalized.isBlank()) return null

        val openAppResult = classifyOpenApp(input, normalized)
        if (openAppResult != null) {
            return openAppResult
        }

        if (isTimeQuery(normalized)) {
            return IntentResult(
                intent = "CurrentTime",
                confidence = 0.92,
                entities = emptyMap()
            )
        }

        val speakText = classifySimpleSpeak(normalized)
        if (!speakText.isNullOrBlank()) {
            return IntentResult(
                intent = "SPEAK",
                confidence = 0.85,
                entities = mapOf("text" to speakText)
            )
        }

        return null
    }

    private fun classifyOpenApp(originalInput: String, normalized: String): IntentResult? {
        val trigger = OPEN_APP_VERBS.firstOrNull { verb ->
            normalized.startsWith("$verb ") || normalized.contains(" $verb ")
        } ?: return null

        val app = originalInput.substringAfter(trigger, "").trim()
        if (app.isBlank()) return null

        return IntentResult(
            intent = "OPEN_APP",
            confidence = 0.93,
            entities = mapOf("app" to app)
        )
    }

    private fun classifySimpleSpeak(normalized: String): String? {
        if (GREETING_WORDS.any { token -> normalized == token || normalized.startsWith("$token ") }) {
            return "Hi! How can I help?"
        }

        if (normalized.contains("thank") || normalized == "ty") {
            return "You are welcome."
        }

        if (normalized == "who are you" || normalized == "what are you") {
            return "I am Jarvis, your on-device assistant."
        }

        return null
    }

    private fun isTimeQuery(normalized: String): Boolean {
        if (normalized == "time" || normalized == "time?" || normalized == "time now") {
            return true
        }

        return TIME_QUERY_PATTERNS.any { pattern -> normalized.contains(pattern) }
    }

    companion object {
        private val OPEN_APP_VERBS = listOf("open", "launch", "start")
        private val GREETING_WORDS = listOf("hi", "hii", "hello", "hey")
        private val TIME_QUERY_PATTERNS = listOf(
            "what time",
            "what's the time",
            "whats the time",
            "tell me the time",
            "current time",
            "time is it"
        )
    }
}
