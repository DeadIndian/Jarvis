package com.jarvis.memory.ingestion

class ImportanceScorer {
    private val highImportanceKeywords = mapOf(
        "critical" to 0.3f,
        "important" to 0.25f,
        "essential" to 0.25f,
        "vital" to 0.25f,
        "must remember" to 0.3f,
        "don't forget" to 0.25f,
        "never forget" to 0.35f,
        "password" to 0.2f,
        "credential" to 0.2f,
        "secret" to 0.25f,
        "api key" to 0.2f,
        "token" to 0.15f,
        "security" to 0.15f,
        "deadline" to 0.2f,
        "due" to 0.15f,
        "exam" to 0.2f,
        "test" to 0.1f,
        "interview" to 0.25f,
        "meeting" to 0.15f,
        "presentation" to 0.2f
    )

    private val mediumImportanceKeywords = mapOf(
        "project" to 0.15f,
        "architecture" to 0.15f,
        "design" to 0.15f,
        "plan" to 0.1f,
        "schedule" to 0.1f,
        "task" to 0.1f,
        "homework" to 0.15f,
        "assignment" to 0.15f,
        "code" to 0.1f,
        "config" to 0.1f,
        "setup" to 0.1f,
        "install" to 0.1f,
        "command" to 0.1f,
        "recipe" to 0.15f,
        "process" to 0.1f,
        "method" to 0.1f,
        "technique" to 0.1f,
        "strategy" to 0.1f
    )

    private val personalInfoKeywords = mapOf(
        "my " to 0.15f,
        "i prefer" to 0.2f,
        "i like" to 0.15f,
        "i hate" to 0.15f,
        "i love" to 0.15f,
        "i'm" to 0.1f,
        "i am" to 0.1f,
        "my name" to 0.25f,
        "my email" to 0.25f,
        "my phone" to 0.25f,
        "my address" to 0.25f,
        "my birthday" to 0.25f,
        "my wife" to 0.2f,
        "my husband" to 0.2f,
        "my kids" to 0.2f,
        "my family" to 0.2f
    )

    fun score(input: IngestionInput, alreadyDeterminedShouldStore: Boolean): ImportanceResult {
        val text = input.rawText.lowercase()
        val factors = mutableMapOf<String, Float>()
        
        var importance = 0.3f

        if (input.userExplicitlyRequested) {
            importance += 0.3f
            factors["explicit_request"] = 0.3f
        }

        for ((keyword, weight) in highImportanceKeywords) {
            if (keyword in text) {
                importance += weight
                factors["keyword:$keyword"] = weight
            }
        }

        for ((keyword, weight) in mediumImportanceKeywords) {
            if (keyword in text) {
                importance += weight
                factors["keyword:$keyword"] = weight
            }
        }

        for ((keyword, weight) in personalInfoKeywords) {
            if (keyword in text) {
                importance += weight
                factors["keyword:$keyword"] = weight
            }
        }

        val contentLength = input.rawText.length
        when {
            contentLength > 500 -> {
                importance += 0.1f
                factors["content_length_long"] = 0.1f
            }
            contentLength > 200 -> {
                importance += 0.05f
                factors["content_length_medium"] = 0.05f
            }
            contentLength < 30 -> {
                importance -= 0.1f
                factors["content_length_short"] = -0.1f
            }
        }

        if (input.context.activeProject != null) {
            val projectContext = input.context.activeProject.lowercase()
            if (projectContext in text) {
                importance += 0.15f
                factors["active_project_context"] = 0.15f
            }
        }

        if (text.contains("how to") || text.contains("how do i") || text.contains("tutorial")) {
            importance += 0.1f
            factors["instructional_content"] = 0.1f
        }

        if (text.contains("?")) {
            importance -= 0.1f
            factors["question_detected"] = -0.1f
        }

        if (text.contains("http://") || text.contains("https://") || text.contains("www.")) {
            importance += 0.1f
            factors["url_present"] = 0.1f
        }

        if (Regex("""\d{4,}""").containsMatchIn(text)) {
            importance += 0.1f
            factors["numbers_present"] = 0.1f
        }

        importance = importance.coerceIn(0.1f, 1.0f)

        return ImportanceResult(importance, factors)
    }
}