package com.jarvis.memory.models

data class MemoryChunk(
    val id: String,
    val text: String,
    val filePath: String,
    val section: String,
    val timestamp: Long,
    val tokenCount: Int,
    val embedding: FloatArray
)
