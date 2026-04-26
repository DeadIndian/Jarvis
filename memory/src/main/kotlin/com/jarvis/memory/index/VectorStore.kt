package com.jarvis.memory.index

import com.jarvis.memory.models.MemoryChunk

interface VectorStore {
    fun upsertFileChunks(filePath: String, chunks: List<MemoryChunk>)
    fun getAllChunks(): List<MemoryChunk>
    fun getFileChunks(filePath: String): List<MemoryChunk>
}
