package com.jarvis.memory.ingestion

import com.jarvis.memory.*

data class IngestionInput(
    val rawText: String,
    val userExplicitlyRequested: Boolean = false,
    val context: IngestionContext = IngestionContext()
)

data class IngestionContext(
    val activeProject: String? = null,
    val recentTopics: List<String> = emptyList(),
    val currentGoal: String? = null
)

data class IngestionResult(
    val action: WriteAction,
    val targetPath: String? = null,
    val targetHeading: String? = null,
    val content: String = "",
    val memoryType: MemoryType = MemoryType.EPISODIC,
    val importance: Float = 0.5f,
    val confidence: Float = 1.0f,
    val reason: String = "",
    val metadata: NoteMetadata? = null
)

enum class WriteAction {
    CREATE_FILE,
    CREATE_SECTION,
    APPEND_TO_SECTION,
    UPDATE_METADATA,
    CREATE_LINK,
    CREATE_TAG,
    MOVE_NOTE,
    SKIP
}

enum class MemoryType {
    SEMANTIC,
    EPISODIC,
    PROCEDURAL,
    PROJECT,
    PERSON,
    TASK,
    REFERENCE,
    IDEA,
    PREFERENCE
}

data class CandidateDetectionResult(
    val shouldStore: Boolean,
    val confidence: Float,
    val reason: String
)

data class ImportanceResult(
    val importance: Float,
    val factors: Map<String, Float>
)

data class ClassificationResult(
    val memoryType: MemoryType,
    val confidence: Float,
    val reasoning: String
)

data class KnowledgeMatch(
    val nodeId: String,
    val filePath: String,
    val title: String,
    val similarity: Float,
    val headings: List<String>,
    val parentPath: List<String>
)

data class MergeDecision(
    val action: WriteAction,
    val targetFile: String?,
    val targetHeading: String?,
    val confidence: Float,
    val reason: String,
    val existingMatches: List<KnowledgeMatch>
)

class MemoryIngestionPipeline(
    private val brain: Brain,
    private val knowledgeGraph: KnowledgeGraph
) {
    private val candidateDetector = CandidateDetector()
    private val importanceScorer = ImportanceScorer()
    private val memoryClassifier = MemoryClassifier()
    private val knowledgeMatcher = KnowledgeMatcher(brain, knowledgeGraph)
    private val mergeDecisionEngine = MergeDecisionEngine()
    private val pathResolver = PathResolver(brain.brainDir)

    suspend fun process(input: IngestionInput): IngestionResult {
        android.util.Log.d("MemoryIngestion", "Processing ingestion: '${input.rawText.take(100)}'")
        val step1 = candidateDetector.detect(input)
        android.util.Log.d("MemoryIngestion", "Candidate detector: shouldStore=${step1.shouldStore}, reason='${step1.reason}'")
        if (!step1.shouldStore && step1.confidence < 0.5f) {
            return IngestionResult(
                action = WriteAction.SKIP,
                confidence = step1.confidence,
                reason = "Not a memory candidate: ${step1.reason}"
            )
        }

        val importanceResult = importanceScorer.score(input, step1.shouldStore)
        android.util.Log.d("MemoryIngestion", "Importance: ${importanceResult.importance}")

        val classificationResult = memoryClassifier.classify(input)
        android.util.Log.d("MemoryIngestion", "Classification: ${classificationResult.memoryType}, reasoning='${classificationResult.reasoning}'")

        val matches = knowledgeMatcher.findRelated(
            input.rawText,
            classificationResult.memoryType,
            input.context
        )
        android.util.Log.d("MemoryIngestion", "Matches found: ${matches.size}")

        val mergeDecision = mergeDecisionEngine.decide(
            input = input,
            classification = classificationResult,
            importance = importanceResult,
            matches = matches
        )
        android.util.Log.d("MemoryIngestion", "Merge decision: ${mergeDecision.action}, targetFile='${mergeDecision.targetFile}'")

        val targetPath = if (mergeDecision.targetFile != null) {
            mergeDecision.targetFile
        } else {
            val path = pathResolver.resolve(
                memoryType = classificationResult.memoryType,
                title = extractTitle(input.rawText),
                context = input.context,
                matches = matches
            )
            android.util.Log.d("MemoryIngestion", "Resolved path: $path")
            path
        }

        val metadata = NoteMetadata(
            title = extractTitle(input.rawText),
            type = mapMemoryTypeToNoteType(classificationResult.memoryType),
            importance = importanceResult.importance,
            tags = extractTags(input.rawText)
        )

        val finalResult = IngestionResult(
            action = mergeDecision.action,
            targetPath = targetPath,
            targetHeading = mergeDecision.targetHeading,
            content = input.rawText,
            memoryType = classificationResult.memoryType,
            importance = importanceResult.importance,
            confidence = mergeDecision.confidence,
            reason = mergeDecision.reason,
            metadata = metadata
        )
        android.util.Log.d("MemoryIngestion", "Final result: action=${finalResult.action}, path=${finalResult.targetPath}")
        return finalResult
    }

    private fun extractTitle(text: String): String {
        return text.lines().firstOrNull()?.trim()
            ?.replace(Regex("^[#*-]+\\s*"), "")
            ?.take(50)
            ?: "Untitled"
    }

    private fun extractTags(text: String): List<String> {
        return Regex("""#(\w+)""").findAll(text).map { it.groupValues[1] }.toList()
    }

    private fun mapMemoryTypeToNoteType(type: MemoryType): NoteType {
        return when (type) {
            MemoryType.SEMANTIC -> NoteType.CONCEPT
            MemoryType.EPISODIC -> NoteType.MEMORY
            MemoryType.PROCEDURAL -> NoteType.PROCEDURE
            MemoryType.PROJECT -> NoteType.PROJECT
            MemoryType.PERSON -> NoteType.PERSON
            MemoryType.TASK -> NoteType.TASK
            MemoryType.REFERENCE -> NoteType.REFERENCE
            MemoryType.IDEA -> NoteType.IDEA
            MemoryType.PREFERENCE -> NoteType.SYSTEM
        }
    }
}