package com.jarvis.memory

import android.content.Context
import com.jarvis.memory.embedding.EmbeddingModel
import com.jarvis.memory.index.VectorStore
import java.nio.file.Path
import java.nio.file.Paths

class BrainMemoryStore(
    private val context: Context,
    private val brainDir: BrainDirectory = BrainDirectory.default
) : MemoryStore {
    val brain: Brain by lazy { Brain(context, brainDir) }

    override suspend fun put(key: String, value: String) {
        brain.put(key, value)
    }

    override suspend fun get(key: String): String? {
        return brain.get(key)
    }

    override suspend fun search(query: String, limit: Int): List<String> {
        return brain.search(query, limit)
    }

    override suspend fun addNote(title: String, content: String, type: NoteType, tags: List<String>) {
        brain.addNote(title, content, type, tags)
    }

    suspend fun addNoteToFolder(title: String, content: String, folderPath: String, tags: List<String> = emptyList()) {
        brain.addNoteToFolder(title, content, folderPath, tags)
    }

    override suspend fun retrieve(query: String, limit: Int): List<RetrievalResult> {
        return brain.retrieve(query, limit)
    }

    override suspend fun getGraph(nodeId: String, depth: Int): List<KnowledgeNode> {
        return brain.getGraph(nodeId, depth)
    }

    companion object {
        fun withCustomPath(context: Context, basePath: String): BrainMemoryStore {
            val path = Paths.get(basePath)
            // Always keep databases in internal storage for reliability and performance
            val internalPath = Paths.get(context.filesDir.absolutePath, "internal_brain")
            return BrainMemoryStore(context, BrainDirectory(path, internalPath))
        }
    }
}