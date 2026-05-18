package com.jarvis.memory

import java.time.LocalDate

data class ParsedNote(
    val metadata: NoteMetadata,
    val sections: List<KnowledgeNode>,
    val edges: List<GraphEdge>
)

class MarkdownSectionParser {
    private val wikiLinkPattern = Regex("""\[\[([^\]]+)]]""")
    private val tagPattern = Regex("""#(\w+)""")
    private val headingPattern = Regex("""^(#{1,6})\s+(.+)$""")

    fun parse(content: String, filePath: String): ParsedNote {
        val (metadata, body) = FrontmatterParser.parse(content)
        val effectiveMetadata = metadata ?: NoteMetadata(
            title = extractTitleFromContent(body),
            type = inferTypeFromPath(filePath)
        )

        val sections = mutableListOf<KnowledgeNode>()
        val edges = mutableListOf<GraphEdge>()
        
        val lines = body.lines()
        val headingStack = mutableListOf<Pair<Int, String>>()
        var currentSectionContent = StringBuilder()
        var currentFileId = effectiveMetadata.id
        
        for (line in lines) {
            val headingMatch = headingPattern.find(line)
            if (headingMatch != null) {
                if (currentSectionContent.isNotEmpty()) {
                    val parentPath = headingStack.map { it.second }
                    val sectionTitle = headingStack.lastOrNull()?.second ?: effectiveMetadata.title
                    val lastHeading = headingStack.lastOrNull()
                    
                    val node = createNode(
                        id = "node_${currentFileId}_${sectionTitle.lowercase().replace(" ", "-")}",
                        file = filePath,
                        type = effectiveMetadata.type,
                        title = sectionTitle,
                        path = parentPath,
                        content = currentSectionContent.toString().trim(),
                        level = lastHeading?.first ?: 1,
                        metadata = effectiveMetadata
                    )
                    sections.add(node)
                    currentSectionContent = StringBuilder()
                }
                
                val level = headingMatch.groupValues[1].length
                val title = headingMatch.groupValues[2].trim()
                
                while (headingStack.isNotEmpty() && headingStack.last().first >= level) {
                    headingStack.removeLast()
                }
                headingStack.add(level to title)
                
                val parentNodeId = headingStack.dropLast(1).lastOrNull()?.let { 
                    "node_${currentFileId}_${it.second.lowercase().replace(" ", "-")}" 
                }
                if (parentNodeId != null) {
                    val childNodeId = "node_${currentFileId}_${title.lowercase().replace(" ", "-")}"
                    edges.add(GraphEdge(
                        fromNodeId = parentNodeId,
                        toNodeId = childNodeId,
                        type = EdgeType.PARENT
                    ))
                }
            } else {
                currentSectionContent.appendLine(line)
            }
        }
        
        if (currentSectionContent.isNotEmpty()) {
            val sectionTitle = headingStack.lastOrNull()?.second ?: effectiveMetadata.title
            val parentPath = headingStack.dropLast(1).map { it.second }
            val lastHeading = headingStack.lastOrNull()
            
            val node = createNode(
                id = "node_${currentFileId}_${sectionTitle.lowercase().replace(" ", "-")}",
                file = filePath,
                type = effectiveMetadata.type,
                title = sectionTitle,
                path = parentPath,
                content = currentSectionContent.toString().trim(),
                level = lastHeading?.first ?: 1,
                metadata = effectiveMetadata
            )
            sections.add(node)
        }
        
        val allContent = body
        val wikiLinks = wikiLinkPattern.findAll(allContent).map { it.groupValues[1] }.toList()
        if (wikiLinks.isNotEmpty() && sections.isNotEmpty()) {
            val rootNode = sections.first()
            wikiLinks.forEach { link ->
                edges.add(GraphEdge(
                    fromNodeId = rootNode.id,
                    toNodeId = "ref_$link",
                    type = EdgeType.REFERENCE
                ))
            }
        }
        
        return ParsedNote(effectiveMetadata, sections, edges)
    }

    private fun createNode(
        id: String,
        file: String,
        type: NoteType,
        title: String,
        path: List<String>,
        content: String,
        level: Int,
        metadata: NoteMetadata
    ): KnowledgeNode {
        val wikiLinks = wikiLinkPattern.findAll(content).map { it.groupValues[1] }.toList()
        val tags = tagPattern.findAll(content).map { it.groupValues[1] }.toList()
        
        return KnowledgeNode(
            id = id,
            file = file,
            type = type,
            title = title,
            path = path,
            content = content,
            summary = generateSummary(content),
            tags = (metadata.tags + tags).distinct(),
            aliases = metadata.aliases,
            links = wikiLinks,
            importance = metadata.importance,
            confidence = 1.0f,
            level = level,
            createdAt = metadata.created.toEpochDay() * 86400000,
            updatedAt = metadata.updated.toEpochDay() * 86400000,
            lastAccessed = System.currentTimeMillis()
        )
    }

    private fun extractTitleFromContent(content: String): String {
        val firstLine = content.lines().firstOrNull { it.isNotBlank() }?.trim() ?: "Untitled"
        return firstLine.removePrefix("# ").ifBlank { "Untitled" }
    }

    private fun inferTypeFromPath(filePath: String): NoteType {
        val path = filePath.lowercase()
        return when {
            path.contains("/concepts/") -> NoteType.CONCEPT
            path.contains("/projects/") -> NoteType.PROJECT
            path.contains("/procedures/") -> NoteType.PROCEDURE
            path.contains("/memories/") -> NoteType.MEMORY
            path.contains("/journal/") -> NoteType.JOURNAL
            path.contains("/people/") -> NoteType.PERSON
            path.contains("/inbox/") -> NoteType.INBOX
            else -> NoteType.INBOX
        }
    }

    private fun generateSummary(content: String): String {
        val clean = content
            .replace(Regex("""^[\s#*-]+"""), "")
            .replace(Regex("""\n+"""), " ")
            .trim()
        
        return if (clean.length > 200) {
            clean.take(197) + "..."
        } else {
            clean
        }
    }
}