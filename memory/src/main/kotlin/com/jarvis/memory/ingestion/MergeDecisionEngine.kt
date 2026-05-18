package com.jarvis.memory.ingestion

class MergeDecisionEngine {
    private val mergeSimilarityThreshold = 0.7f
    private val appendSimilarityThreshold = 0.5f
    private val createNewThreshold = 0.3f

    fun decide(
        input: IngestionInput,
        classification: ClassificationResult,
        importance: ImportanceResult,
        matches: List<KnowledgeMatch>
    ): MergeDecision {
        if (matches.isEmpty()) {
            return MergeDecision(
                action = WriteAction.CREATE_FILE,
                targetFile = null,
                targetHeading = null,
                confidence = 0.9f,
                reason = "no_existing_knowledge_found",
                existingMatches = emptyList()
            )
        }

        val bestMatch = matches.maxByOrNull { it.similarity }!!

        if (bestMatch.similarity >= mergeSimilarityThreshold) {
            if (bestMatch.headings.isNotEmpty()) {
                return MergeDecision(
                    action = WriteAction.APPEND_TO_SECTION,
                    targetFile = bestMatch.filePath,
                    targetHeading = bestMatch.headings.last(),
                    confidence = bestMatch.similarity,
                    reason = "high_similarity_merge_into_section",
                    existingMatches = matches
                )
            } else {
                return MergeDecision(
                    action = WriteAction.CREATE_SECTION,
                    targetFile = bestMatch.filePath,
                    targetHeading = extractTitleFromInput(input.rawText),
                    confidence = bestMatch.similarity,
                    reason = "high_similarity_create_section",
                    existingMatches = matches
                )
            }
        }

        if (bestMatch.similarity >= appendSimilarityThreshold) {
            return MergeDecision(
                action = WriteAction.CREATE_SECTION,
                targetFile = bestMatch.filePath,
                targetHeading = extractTitleFromInput(input.rawText),
                confidence = bestMatch.similarity,
                reason = "medium_similarity_create_section",
                existingMatches = matches
            )
        }

        if (belongsToSameDomain(bestMatch, classification)) {
            return MergeDecision(
                action = WriteAction.CREATE_SECTION,
                targetFile = bestMatch.filePath,
                targetHeading = extractTitleFromInput(input.rawText),
                confidence = 0.6f,
                reason = "same_domain_create_section",
                existingMatches = matches
            )
        }

        if (bestMatch.similarity < createNewThreshold) {
            val sameParent = matches.filter { it.parentPath == bestMatch.parentPath }
            if (sameParent.size >= 2) {
                return MergeDecision(
                    action = WriteAction.CREATE_SECTION,
                    targetFile = bestMatch.filePath,
                    targetHeading = extractTitleFromInput(input.rawText),
                    confidence = 0.5f,
                    reason = "multiple_same_parent_sections",
                    existingMatches = matches
                )
            }
        }

        return MergeDecision(
            action = WriteAction.CREATE_FILE,
            targetFile = null,
            targetHeading = null,
            confidence = bestMatch.similarity,
            reason = "new_domain_or_low_similarity",
            existingMatches = matches
        )
    }

    private fun belongsToSameDomain(match: KnowledgeMatch, classification: ClassificationResult): Boolean {
        val filePath = match.filePath.lowercase()
        
        return when (classification.memoryType) {
            MemoryType.SEMANTIC -> filePath.contains("concept")
            MemoryType.PROCEDURAL -> filePath.contains("procedure")
            MemoryType.PROJECT -> filePath.contains("project")
            MemoryType.PERSON -> filePath.contains("person")
            MemoryType.TASK -> filePath.contains("task") || filePath.contains("inbox")
            MemoryType.EPISODIC, MemoryType.PREFERENCE -> filePath.contains("memory") || filePath.contains("journal")
            MemoryType.IDEA -> filePath.contains("inbox") || filePath.contains("ideas")
            MemoryType.REFERENCE -> filePath.contains("reference")
        }
    }

    private fun extractTitleFromInput(text: String): String {
        return text.lines().firstOrNull()?.trim()
            ?.replace(Regex("^[#*-]+\\s*"), "")
            ?.take(50)
            ?: "New Section"
    }
}