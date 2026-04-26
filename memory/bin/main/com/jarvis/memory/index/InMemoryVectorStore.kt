package com.jarvis.memory.index

import com.jarvis.memory.models.MemoryChunk

class InMemoryVectorStore : VectorStore {
    private val chunksByFile = LinkedHashMap<String, MutableList<MemoryChunk>>()

    override fun upsertFileChunks(filePath: String, chunks: List<MemoryChunk>) {
        chunksByFile[filePath] = chunks.toMutableList()
    }

    override fun getAllChunks(): List<MemoryChunk> {
        return chunksByFile.values.flatten()
    }

    override fun getFileChunks(filePath: String): List<MemoryChunk> {
        return chunksByFile[filePath]?.toList().orEmpty()
    }
}
