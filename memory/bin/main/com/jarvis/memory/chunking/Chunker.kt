package com.jarvis.memory.chunking

interface Chunker {
    fun chunk(filePath: String, markdown: String, timestamp: Long): List<MemoryChunkSeed>
}

data class MemoryChunkSeed(
    val text: String,
    val filePath: String,
    val section: String,
    val timestamp: Long,
    val tokenCount: Int
)
