package com.jarvis.memory.ingestion

class MemoryClassifier {
    private val semanticPatterns = listOf(
        Regex("""^(the |a |an )?\w+ (is|are|was|were|means|stands for|refers to)""", RegexOption.IGNORE_CASE),
        Regex("""^what is a""", RegexOption.IGNORE_CASE),
        Regex("""^what are""", RegexOption.IGNORE_CASE),
        Regex("""^how (does|do) .* work""", RegexOption.IGNORE_CASE),
        Regex("""^explain""", RegexOption.IGNORE_CASE),
        Regex("""^definition of""", RegexOption.IGNORE_CASE),
        Regex("""runs on port""", RegexOption.IGNORE_CASE),
        Regex("""uses? \w+ algorithm""", RegexOption.IGNORE_CASE),
        Regex("""is (a|an) .* that""", RegexOption.IGNORE_CASE),
        Regex("""consists of""", RegexOption.IGNORE_CASE),
        Regex("""made (up|out) of""", RegexOption.IGNORE_CASE),
        Regex("""is used for""", RegexOption.IGNORE_CASE),
        Regex("""is a type of""", RegexOption.IGNORE_CASE),
        Regex("""belongs to""", RegexOption.IGNORE_CASE),
        Regex("""falls under""", RegexOption.IGNORE_CASE)
    )

    private val episodicPatterns = listOf(
        Regex("""^yesterday""", RegexOption.IGNORE_CASE),
        Regex("""^last (week|month|year|night)""", RegexOption.IGNORE_CASE),
        Regex("""^today""", RegexOption.IGNORE_CASE),
        Regex("""^once""", RegexOption.IGNORE_CASE),
        Regex("""^i remember""", RegexOption.IGNORE_CASE),
        Regex("""^i was""", RegexOption.IGNORE_CASE),
        Regex("""^i went""", RegexOption.IGNORE_CASE),
        Regex("""^i saw""", RegexOption.IGNORE_CASE),
        Regex("""^i met""", RegexOption.IGNORE_CASE),
        Regex("""^i heard""", RegexOption.IGNORE_CASE),
        Regex("""^at (the |)\w+ (i met|i saw|i went)""", RegexOption.IGNORE_CASE),
        Regex("""^when (i was|i went|i met)""", RegexOption.IGNORE_CASE)
    )

    private val proceduralPatterns = listOf(
        Regex("""^how to""", RegexOption.IGNORE_CASE),
        Regex("""^how do i""", RegexOption.IGNORE_CASE),
        Regex("""^how do you""", RegexOption.IGNORE_CASE),
        Regex("""^steps? to""", RegexOption.IGNORE_CASE),
        Regex("""^steps?:?""", RegexOption.IGNORE_CASE),
        Regex("""^first(,|) (you |)""", RegexOption.IGNORE_CASE),
        Regex("""^then(,|)""", RegexOption.IGNORE_CASE),
        Regex("""^after that""", RegexOption.IGNORE_CASE),
        Regex("""^next(,|)""", RegexOption.IGNORE_CASE),
        Regex("""^finally(,|)""", RegexOption.IGNORE_CASE),
        Regex("""^install (the |)""", RegexOption.IGNORE_CASE),
        Regex("""^run (the |)\w+ (command|script)""", RegexOption.IGNORE_CASE),
        Regex("""^use (the |)\w+ to""", RegexOption.IGNORE_CASE),
        Regex("""^start by""", RegexOption.IGNORE_CASE),
        Regex("""recipe:?""", RegexOption.IGNORE_CASE),
        Regex("""instructions?:?""", RegexOption.IGNORE_CASE)
    )

    private val projectPatterns = listOf(
        Regex("""^project:?""", RegexOption.IGNORE_CASE),
        Regex("""^working on""", RegexOption.IGNORE_CASE),
        Regex("""^building""", RegexOption.IGNORE_CASE),
        Regex("""^developing""", RegexOption.IGNORE_CASE),
        Regex("""^implementing""", RegexOption.IGNORE_CASE),
        Regex("""^my project""", RegexOption.IGNORE_CASE),
        Regex("""^for the project""", RegexOption.IGNORE_CASE),
        Regex("""^in the project""", RegexOption.IGNORE_CASE),
        Regex("""^the project (needs|requires|uses)""", RegexOption.IGNORE_CASE),
        Regex("""^sprint""", RegexOption.IGNORE_CASE),
        Regex("""^milestone""", RegexOption.IGNORE_CASE)
    )

    private val personPatterns = listOf(
        Regex("""^person:?""", RegexOption.IGNORE_CASE),
        Regex("""^contact:?""", RegexOption.IGNORE_CASE),
        Regex("""^my (friend|colleague|boss|coworker|teammate)""", RegexOption.IGNORE_CASE),
        Regex("""^meet (my |)\w+""", RegexOption.IGNORE_CASE),
        Regex("""^his name is""", RegexOption.IGNORE_CASE),
        Regex("""^her name is""", RegexOption.IGNORE_CASE),
        Regex("""^their name is""", RegexOption.IGNORE_CASE),
        Regex("""^works? at""", RegexOption.IGNORE_CASE),
        Regex("""^email (is|:)""", RegexOption.IGNORE_CASE),
        Regex("""^phone (is|:)""", RegexOption.IGNORE_CASE)
    )

    private val taskPatterns = listOf(
        Regex("""^task:?""", RegexOption.IGNORE_CASE),
        Regex("""^todo""", RegexOption.IGNORE_CASE),
        Regex("""^remind me""", RegexOption.IGNORE_CASE),
        Regex("""^remember to""", RegexOption.IGNORE_CASE),
        Regex("""^don't forget to""", RegexOption.IGNORE_CASE),
        Regex("""^i need to""", RegexOption.IGNORE_CASE),
        Regex("""^i should""", RegexOption.IGNORE_CASE),
        Regex("""^i have to""", RegexOption.IGNORE_CASE),
        Regex("""^must (do|finish|complete|start)""", RegexOption.IGNORE_CASE),
        Regex("""^deadline""", RegexOption.IGNORE_CASE),
        Regex("""^due""", RegexOption.IGNORE_CASE),
        Regex("""^by (tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)""", RegexOption.IGNORE_CASE)
    )

    private val preferencePatterns = listOf(
        Regex("""^i prefer""", RegexOption.IGNORE_CASE),
        Regex("""^i like""", RegexOption.IGNORE_CASE),
        Regex("""^i hate""", RegexOption.IGNORE_CASE),
        Regex("""^i love""", RegexOption.IGNORE_CASE),
        Regex("""^i usually""", RegexOption.IGNORE_CASE),
        Regex("""^my favorite""", RegexOption.IGNORE_CASE),
        Regex("""^i always""", RegexOption.IGNORE_CASE),
        Regex("""^i never""", RegexOption.IGNORE_CASE),
        Regex("""^i don't like""", RegexOption.IGNORE_CASE),
        Regex("""^settings?:?""", RegexOption.IGNORE_CASE),
        Regex("""^preference:?""", RegexOption.IGNORE_CASE),
        Regex("""^config:?""", RegexOption.IGNORE_CASE)
    )

    private val ideaPatterns = listOf(
        Regex("""^idea:?""", RegexOption.IGNORE_CASE),
        Regex("""^what if""", RegexOption.IGNORE_CASE),
        Regex("""^i thought (of|about)""", RegexOption.IGNORE_CASE),
        Regex("""^i have an idea""", RegexOption.IGNORE_CASE),
        Regex("""^maybe""", RegexOption.IGNORE_CASE),
        Regex("""^perhaps""", RegexOption.IGNORE_CASE),
        Regex("""^could (we|i)""", RegexOption.IGNORE_CASE),
        Regex("""^wouldn't it be cool""", RegexOption.IGNORE_CASE)
    )

    fun classify(input: IngestionInput): ClassificationResult {
        val text = input.rawText.trim()

        for (pattern in semanticPatterns) {
            if (pattern.containsMatchIn(text)) {
                return ClassificationResult(
                    memoryType = MemoryType.SEMANTIC,
                    confidence = 0.85f,
                    reasoning = "semantic_pattern_match"
                )
            }
        }

        for (pattern in proceduralPatterns) {
            if (pattern.containsMatchIn(text)) {
                return ClassificationResult(
                    memoryType = MemoryType.PROCEDURAL,
                    confidence = 0.85f,
                    reasoning = "procedural_pattern_match"
                )
            }
        }

        for (pattern in preferencePatterns) {
            if (pattern.containsMatchIn(text)) {
                return ClassificationResult(
                    memoryType = MemoryType.PREFERENCE,
                    confidence = 0.8f,
                    reasoning = "preference_pattern_match"
                )
            }
        }

        for (pattern in projectPatterns) {
            if (pattern.containsMatchIn(text)) {
                return ClassificationResult(
                    memoryType = MemoryType.PROJECT,
                    confidence = 0.8f,
                    reasoning = "project_pattern_match"
                )
            }
        }

        for (pattern in personPatterns) {
            if (pattern.containsMatchIn(text)) {
                return ClassificationResult(
                    memoryType = MemoryType.PERSON,
                    confidence = 0.8f,
                    reasoning = "person_pattern_match"
                )
            }
        }

        for (pattern in taskPatterns) {
            if (pattern.containsMatchIn(text)) {
                return ClassificationResult(
                    memoryType = MemoryType.TASK,
                    confidence = 0.8f,
                    reasoning = "task_pattern_match"
                )
            }
        }

        for (pattern in episodicPatterns) {
            if (pattern.containsMatchIn(text)) {
                return ClassificationResult(
                    memoryType = MemoryType.EPISODIC,
                    confidence = 0.75f,
                    reasoning = "episodic_pattern_match"
                )
            }
        }

        for (pattern in ideaPatterns) {
            if (pattern.containsMatchIn(text)) {
                return ClassificationResult(
                    memoryType = MemoryType.IDEA,
                    confidence = 0.7f,
                    reasoning = "idea_pattern_match"
                )
            }
        }

        if (input.userExplicitlyRequested) {
            return ClassificationResult(
                memoryType = MemoryType.EPISODIC,
                confidence = 0.95f,
                reasoning = "explicit_user_request"
            )
        }

        val wordCount = text.split(Regex("\\s+")).size
        if (wordCount > 5 && text.contains(" is ") || text.contains(" are ")) {
            return ClassificationResult(
                memoryType = MemoryType.SEMANTIC,
                confidence = 0.6f,
                reasoning = "inferred_semantic_from_structure"
            )
        }

        return ClassificationResult(
            memoryType = MemoryType.EPISODIC,
            confidence = 0.5f,
            reasoning = "default_to_episodic"
        )
    }
}