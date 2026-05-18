package com.jarvis.memory

import android.content.Context
import com.jarvis.memory.embedding.EmbeddingModel
import com.jarvis.memory.index.VectorStore

data class RetrievalResult(
    val node: KnowledgeNode,
    val score: Float,
    val reason: String
)

class Brain(
    private val context: Context,
    val brainDir: BrainDirectory = BrainDirectory.default,
    private val embeddingModel: EmbeddingModel? = null,
    private val vectorStore: VectorStore? = null
) {
    private val parser = MarkdownSectionParser()
    val graph: KnowledgeGraph by lazy { 
        KnowledgeGraph.getInstance(context, brainDir.graphEdges) 
    }
    
    init {
        brainDir.ensureLayout()
    }

    suspend fun put(key: String, content: String, type: NoteType = NoteType.INBOX) {
        val filePath = brainDir.notePath(type, key)
        brainDir.notePath(type, key).parent.toFile().mkdirs()
        
        val existingContent = if (filePath.toFile().exists()) {
            filePath.toFile().readText()
        } else ""
        
        val merged = if (existingContent.isNotBlank()) {
            mergeContent(existingContent, content)
        } else {
            createNewNote(key, content, type)
        }
        
        filePath.toFile().writeText(merged)
        reindexFile(filePath.toString())
    }

    suspend fun get(key: String): String? {
        val files = findNoteFiles(key)
        return files.firstOrNull()?.readText()
    }

    suspend fun search(query: String, limit: Int = 5): List<String> {
        val results = retrieve(query, limit)
        return results.map { "${it.node.title}\n${it.node.content}" }
    }

    suspend fun retrieve(query: String, limit: Int = 5): List<RetrievalResult> {
        val exactResults = exactRetrieval(query)
        val semanticResults = if (embeddingModel != null && vectorStore != null) {
            semanticRetrieval(query, limit)
        } else emptyList()
        val graphResults = graphRetrieval(query, limit)
        val temporalResults = temporalRetrieval(query, limit)
        
        return mergeResults(exactResults, semanticResults, graphResults, temporalResults, limit)
    }

    suspend fun addNote(title: String, content: String, type: NoteType, tags: List<String> = emptyList()) {
        val metadata = NoteMetadata(
            title = title,
            type = type,
            tags = tags
        )
        val frontmatter = FrontmatterParser.serialize(metadata)
        val body = "\n# $title\n\n$content\n"
        val fullContent = frontmatter + body
        
        val filePath = brainDir.notePath(type, title)
        brainDir.notePath(type, title).parent.toFile().mkdirs()
        filePath.toFile().writeText(fullContent)
        
        reindexFile(filePath.toString())
    }

    suspend fun addNoteToFolder(title: String, content: String, folderPath: String, tags: List<String> = emptyList()) {
        val metadata = NoteMetadata(
            title = title,
            type = NoteType.INBOX,
            tags = tags
        )
        val frontmatter = FrontmatterParser.serialize(metadata)
        val body = "\n# $title\n\n$content\n"
        val fullContent = frontmatter + body
        
        val filePath = brainDir.notePathFromFolder(folderPath, title)
        filePath.parent.toFile().mkdirs()
        filePath.toFile().writeText(fullContent)
        
        reindexFile(filePath.toString())
    }

    suspend fun getGraph(nodeId: String, depth: Int = 2): List<KnowledgeNode> {
        return graph.getRelatedNodes(nodeId, depth)
    }

    suspend fun getAllNotes(): List<KnowledgeNode> {
        return graph.getAllNodes()
    }

    suspend fun deleteNote(nodeId: String) {
        graph.deleteNode(nodeId)
    }

    suspend fun reindexFile(filePath: String) {
        val content = java.io.File(filePath).readText()
        val parsed = parser.parse(content, filePath)
        
        parsed.sections.forEach { node ->
            graph.upsertNode(node)
        }
        
        parsed.edges.forEach { edge ->
            graph.addEdge(edge)
        }
    }

    private fun exactRetrieval(query: String): List<RetrievalResult> {
        val queryLower = query.lowercase()
        val allNodes = graph.getAllNodes()
        
        return allNodes.map { node ->
            var score = 0f
            if (node.title.lowercase().contains(queryLower)) score += 0.8f
            if (node.content.lowercase().contains(queryLower)) score += 0.4f
            if (node.tags.any { it.lowercase().contains(queryLower) }) score += 0.5f
            if (node.aliases.any { it.lowercase().contains(queryLower) }) score += 0.6f
            
            if (score > 0) {
                RetrievalResult(node, score, "exact")
            } else null
        }.filterNotNull().sortedByDescending { it.score }
    }

    private suspend fun semanticRetrieval(query: String, limit: Int): List<RetrievalResult> {
        return emptyList()
    }

    private fun graphRetrieval(query: String, limit: Int): List<RetrievalResult> {
        val queryLower = query.lowercase()
        val matchingNodes = graph.searchByTitle(queryLower)
        
        return matchingNodes.flatMap { node ->
            graph.getRelatedNodes(node.id, 2).map { related ->
                RetrievalResult(related, 0.3f, "graph")
            }
        }.sortedByDescending { it.score }.take(limit)
    }

    private fun temporalRetrieval(query: String, limit: Int): List<RetrievalResult> {
        val allNodes = graph.getAllNodes()
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)
        
        return allNodes
            .filter { it.updatedAt > thirtyDaysAgo }
            .sortedByDescending { it.updatedAt }
            .take(limit)
            .map { RetrievalResult(it, 0.2f, "temporal") }
    }

    private fun mergeResults(
        exact: List<RetrievalResult>,
        semantic: List<RetrievalResult>,
        graph: List<RetrievalResult>,
        temporal: List<RetrievalResult>,
        limit: Int
    ): List<RetrievalResult> {
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<RetrievalResult>()
        
        val all = exact + semantic + graph + temporal
        
        for (result in all.sortedByDescending { it.score }) {
            if (!seen.contains(result.node.id)) {
                seen.add(result.node.id)
                merged.add(result)
                if (merged.size >= limit) break
            }
        }
        
        return merged
    }

    private fun findNoteFiles(key: String): List<java.io.File> {
        return brainDir.notes.toFile().walkTopDown()
            .filter { it.isFile && it.name.endsWith(".md") }
            .filter { it.nameWithoutExtension.contains(key.lowercase()) }
            .toList()
    }

    private fun createNewNote(title: String, content: String, type: NoteType): String {
        val metadata = NoteMetadata(
            title = title,
            type = type
        )
        return FrontmatterParser.serialize(metadata) + "\n# $title\n\n$content\n"
    }

    private fun mergeContent(existing: String, newContent: String): String {
        val (metadata, body) = FrontmatterParser.parse(existing)
        val updated = metadata?.copy(updated = java.time.LocalDate.now())
        
        val frontmatter = updated?.let { FrontmatterParser.serialize(it) } ?: ""
        return frontmatter + "\n" + body + "\n---\n\n" + newContent + "\n"
    }

    companion object {
    }
}