package com.jarvis.memory.ingestion

import com.jarvis.memory.Brain
import com.jarvis.memory.KnowledgeGraph
import com.jarvis.memory.KnowledgeNode

class KnowledgeMatcher(
    private val brain: Brain,
    private val knowledgeGraph: KnowledgeGraph
) {
    private val similarityThreshold = 0.5f

    fun findRelated(
        inputText: String,
        memoryType: MemoryType,
        context: IngestionContext
    ): List<KnowledgeMatch> {
        val matches = mutableListOf<KnowledgeMatch>()

        matches.addAll(titleBasedSearch(inputText))
        matches.addAll(tagBasedSearch(inputText))
        matches.addAll(aliasBasedSearch(inputText))
        matches.addAll(graphBasedSearch(inputText))

        if (context.activeProject != null) {
            matches.addAll(projectContextSearch(inputText, context.activeProject))
        }

        return matches
            .distinctBy { it.nodeId }
            .sortedByDescending { it.similarity }
    }

    private fun titleBasedSearch(text: String): List<KnowledgeMatch> {
        val keywords = extractKeywords(text)
        val matches = mutableListOf<KnowledgeMatch>()

        for (keyword in keywords) {
            val nodes = knowledgeGraph.searchByTitle(keyword)
            for (node in nodes) {
                val similarity = calculateTitleSimilarity(text, node.title)
                if (similarity >= similarityThreshold) {
                    matches.add(
                        KnowledgeMatch(
                            nodeId = node.id,
                            filePath = node.file,
                            title = node.title,
                            similarity = similarity,
                            headings = node.path,
                            parentPath = node.path.dropLast(1)
                        )
                    )
                }
            }
        }

        return matches
    }

    private fun tagBasedSearch(text: String): List<KnowledgeMatch> {
        val inputTags = extractTagsFromText(text)
        val matches = mutableListOf<KnowledgeMatch>()

        val allNodes = knowledgeGraph.getAllNodes()
        for (node in allNodes) {
            val commonTags = node.tags.intersect(inputTags.toSet())
            if (commonTags.isNotEmpty()) {
                val similarity = commonTags.size.toFloat() / maxOf(node.tags.size, inputTags.size)
                if (similarity >= 0.3f) {
                    matches.add(
                        KnowledgeMatch(
                            nodeId = node.id,
                            filePath = node.file,
                            title = node.title,
                            similarity = similarity,
                            headings = node.path,
                            parentPath = node.path.dropLast(1)
                        )
                    )
                }
            }
        }

        return matches
    }

    private fun aliasBasedSearch(text: String): List<KnowledgeMatch> {
        val keywords = extractKeywords(text)
        val matches = mutableListOf<KnowledgeMatch>()

        val allNodes = knowledgeGraph.getAllNodes()
        for (node in allNodes) {
            for (alias in node.aliases) {
                for (keyword in keywords) {
                    if (alias.lowercase().contains(keyword.lowercase()) ||
                        keyword.lowercase().contains(alias.lowercase())) {
                        matches.add(
                            KnowledgeMatch(
                                nodeId = node.id,
                                filePath = node.file,
                                title = node.title,
                                similarity = 0.8f,
                                headings = node.path,
                                parentPath = node.path.dropLast(1)
                            )
                        )
                        break
                    }
                }
            }
        }

        return matches
    }

    private fun graphBasedSearch(text: String): List<KnowledgeMatch> {
        val keywords = extractKeywords(text)
        val matches = mutableListOf<KnowledgeMatch>()

        val allNodes = knowledgeGraph.getAllNodes()
        for (node in allNodes) {
            val contentSimilarity = calculateContentSimilarity(text, node.content)
            if (contentSimilarity >= similarityThreshold) {
                matches.add(
                    KnowledgeMatch(
                        nodeId = node.id,
                        filePath = node.file,
                        title = node.title,
                        similarity = contentSimilarity,
                        headings = node.path,
                        parentPath = node.path.dropLast(1)
                    )
                )
            }
        }

        return matches
    }

    private fun projectContextSearch(text: String, projectName: String): List<KnowledgeMatch> {
        val matches = mutableListOf<KnowledgeMatch>()

        val projectNodes = knowledgeGraph.searchByTitle(projectName)
        for (node in projectNodes) {
            if (node.file.contains("projects")) {
                val relatedNodes = knowledgeGraph.getRelatedNodes(node.id, 1)
                for (related in relatedNodes) {
                    matches.add(
                        KnowledgeMatch(
                            nodeId = related.id,
                            filePath = related.file,
                            title = related.title,
                            similarity = 0.7f,
                            headings = related.path,
                            parentPath = related.path.dropLast(1)
                        )
                    )
                }
            }
        }

        return matches
    }

    private fun extractKeywords(text: String): List<String> {
        val stopWords = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "must", "shall", "can", "need", "to", "of",
            "in", "for", "on", "with", "at", "by", "from", "as", "into", "through",
            "during", "before", "after", "above", "below", "between", "under",
            "again", "further", "then", "once", "here", "there", "when", "where",
            "why", "how", "all", "each", "few", "more", "most", "other", "some",
            "such", "no", "nor", "not", "only", "own", "same", "so", "than",
            "too", "very", "just", "i", "you", "he", "she", "it", "we", "they",
            "what", "which", "who", "whom", "this", "that", "these", "those", "my",
            "your", "his", "her", "its", "our", "their"
        )

        return text.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
            .distinct()
    }

    private fun extractTagsFromText(text: String): List<String> {
        return Regex("""#(\w+)""").findAll(text).map { it.groupValues[1] }.toList()
    }

    private fun calculateTitleSimilarity(input: String, title: String): Float {
        val inputKeywords = extractKeywords(input).toSet()
        val titleKeywords = extractKeywords(title).toSet()

        if (titleKeywords.isEmpty()) return 0f

        val intersection = inputKeywords.intersect(titleKeywords)
        return intersection.size.toFloat() / titleKeywords.size
    }

    private fun calculateContentSimilarity(input: String, content: String): Float {
        val inputKeywords = extractKeywords(input)
        val contentKeywords = extractKeywords(content)

        if (contentKeywords.isEmpty()) return 0f

        val intersection = inputKeywords.intersect(contentKeywords.toSet())
        return (intersection.size * 2).toFloat() / (inputKeywords.size + contentKeywords.size)
    }
}