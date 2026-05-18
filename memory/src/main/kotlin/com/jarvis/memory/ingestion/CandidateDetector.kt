package com.jarvis.memory.ingestion

class CandidateDetector {
    private val ignorePatterns = listOf(
        Regex("""^what('s| is| was| will)*\s+(the )?time""", RegexOption.IGNORE_CASE),
        Regex("""^what('s| is)*\s+the weather""", RegexOption.IGNORE_CASE),
        Regex("""^how('s| is| are)*\s+(the )?weather""", RegexOption.IGNORE_CASE),
        Regex("""^play\s+""", RegexOption.IGNORE_CASE),
        Regex("""^open\s+(spotify|youtube|netflix)""", RegexOption.IGNORE_CASE),
        Regex("""^set\s+(a )?timer""", RegexOption.IGNORE_CASE),
        Regex("""^remind\s+me\s+in""", RegexOption.IGNORE_CASE),
        Regex("""^wake\s+me\s+up""", RegexOption.IGNORE_CASE),
        Regex("""^turn\s+(on|off)\s+""", RegexOption.IGNORE_CASE),
        Regex("""^turn\s+(on|off)\s+the\s+""", RegexOption.IGNORE_CASE),
        Regex("""^show\s+me\s+(the )?notifications""", RegexOption.IGNORE_CASE),
        Regex("""^hello""", RegexOption.IGNORE_CASE),
        Regex("""^hi( jarvis)?"""", RegexOption.IGNORE_CASE),
        Regex("""^hey\s+jarvis""", RegexOption.IGNORE_CASE),
        Regex("""^good\s+(morning|afternoon|evening|night)""", RegexOption.IGNORE_CASE),
        Regex("""^thank(s| you)""", RegexOption.IGNORE_CASE),
        Regex("""^please\s+""", RegexOption.IGNORE_CASE),
        Regex("""^sorry""", RegexOption.IGNORE_CASE),
        Regex("""^ok(ay)?(\s+jarvis)?"""", RegexOption.IGNORE_CASE),
        Regex("""^stop""", RegexOption.IGNORE_CASE),
        Regex("""^never mind""", RegexOption.IGNORE_CASE),
        Regex("""^nvm""", RegexOption.IGNORE_CASE)
    )

    private val memoryIndicatorPatterns = listOf(
        Regex("""remember\s+""", RegexOption.IGNORE_CASE),
        Regex("""don't\s+forget""", RegexOption.IGNORE_CASE),
        Regex("""save\s+this""", RegexOption.IGNORE_CASE),
        Regex("""keep\s+in\s+mind""", RegexOption.IGNORE_CASE),
        Regex("""store\s+""", RegexOption.IGNORE_CASE),
        Regex("""note\s+that""", RegexOption.IGNORE_CASE),
        Regex("""important:?""", RegexOption.IGNORE_CASE),
        Regex("""^my\s+\w+\s+is\s+""", RegexOption.IGNORE_CASE),
        Regex("""^my\s+\w+\s+are\s+""", RegexOption.IGNORE_CASE),
        Regex("""^i\s+(prefer|hate|love|like)""", RegexOption.IGNORE_CASE),
        Regex("""^i'm\s+working\s+on""", RegexOption.IGNORE_CASE),
        Regex("""^i'm\s+building""", RegexOption.IGNORE_CASE),
        Regex("""^i\s+(learned|found out|discovered)""", RegexOption.IGNORE_CASE),
        Regex("""^the\s+\w+\s+(is|was|runs|uses|needs|requires)""", RegexOption.IGNORE_CASE),
        Regex("""^how\s+to\s+""", RegexOption.IGNORE_CASE),
        Regex("""^how\s+do\s+i\s+""", RegexOption.IGNORE_CASE),
        Regex("""^how\s+does\s+""", RegexOption.IGNORE_CASE)
    )

    private val persistentInfoPatterns = listOf(
        Regex("""password[s]?"""", RegexOption.IGNORE_CASE),
        Regex("""credential[s]?"""", RegexOption.IGNORE_CASE),
        Regex("""login""", RegexOption.IGNORE_CASE),
        Regex("""api\s*key""", RegexOption.IGNORE_CASE),
        Regex("""token""", RegexOption.IGNORE_CASE),
        Regex("""address""", RegexOption.IGNORE_CASE),
        Regex("""phone\s*number""", RegexOption.IGNORE_CASE),
        Regex("""email""", RegexOption.IGNORE_CASE),
        Regex("""meeting""", RegexOption.IGNORE_CASE),
        Regex("""appointment""", RegexOption.IGNORE_CASE),
        Regex("""deadline""", RegexOption.IGNORE_CASE),
        Regex("""due\s+date""", RegexOption.IGNORE_CASE),
        Regex("""exam""", RegexOption.IGNORE_CASE),
        Regex("""test""", RegexOption.IGNORE_CASE),
        Regex("""project""", RegexOption.IGNORE_CASE),
        Regex("""code""", RegexOption.IGNORE_CASE),
        Regex("""config""", RegexOption.IGNORE_CASE),
        Regex("""setting[s]?"""", RegexOption.IGNORE_CASE),
        Regex("""install""", RegexOption.IGNORE_CASE),
        Regex("""setup""", RegexOption.IGNORE_CASE),
        Regex("""deploy""", RegexOption.IGNORE_CASE),
        Regex("""server""", RegexOption.IGNORE_CASE),
        Regex("""port""", RegexOption.IGNORE_CASE),
        Regex("""database""", RegexOption.IGNORE_CASE),
        Regex("""ip\s*address""", RegexOption.IGNORE_CASE),
        Regex("""url""", RegexOption.IGNORE_CASE),
        Regex("""website""", RegexOption.IGNORE_CASE),
        Regex("""documentation""", RegexOption.IGNORE_CASE),
        Regex("""tutorial""", RegexOption.IGNORE_CASE),
        Regex("""guide""", RegexOption.IGNORE_CASE),
        Regex("""command""", RegexOption.IGNORE_CASE),
        Regex("""syntax""", RegexOption.IGNORE_CASE),
        Regex("""algorithm""", RegexOption.IGNORE_CASE),
        Regex("""architecture""", RegexOption.IGNORE_CASE),
        Regex("""framework""", RegexOption.IGNORE_CASE),
        Regex("""library""", RegexOption.IGNORE_CASE),
        Regex("""package""", RegexOption.IGNORE_CASE),
        Regex("""dependency""", RegexOption.IGNORE_CASE)
    )

    fun detect(input: IngestionInput): CandidateDetectionResult {
        if (input.userExplicitlyRequested) {
            return CandidateDetectionResult(
                shouldStore = true,
                confidence = 1.0f,
                reason = "explicit_user_request"
            )
        }

        val text = input.rawText.trim()

        for (pattern in ignorePatterns) {
            if (pattern.matches(text)) {
                return CandidateDetectionResult(
                    shouldStore = false,
                    confidence = 0.95f,
                    reason = "transient_query"
                )
            }
        }

        var shouldStore = false
        var confidence = 0.5f
        val reasons = mutableListOf<String>()

        for (pattern in memoryIndicatorPatterns) {
            if (pattern.containsMatchIn(text)) {
                shouldStore = true
                confidence = 0.9f
                reasons.add("memory_indicator_detected")
                break
            }
        }

        if (!shouldStore) {
            var matchCount = 0
            for (pattern in persistentInfoPatterns) {
                if (pattern.containsMatchIn(text)) {
                    matchCount++
                }
            }
            if (matchCount > 0) {
                shouldStore = true
                confidence = (0.6f + (matchCount * 0.1f)).coerceAtMost(0.9f)
                reasons.add("persistent_info_patterns:$matchCount")
            }
        }

        if (!shouldStore) {
            if (text.length > 20 && text.split(" ").size >= 3) {
                shouldStore = true
                confidence = 0.6f
                reasons.add("substantive_content")
            }
        }

        return CandidateDetectionResult(
            shouldStore = shouldStore,
            confidence = confidence,
            reason = reasons.joinToString("; ")
        )
    }
}