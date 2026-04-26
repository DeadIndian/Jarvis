package com.jarvis.memory.writer

interface MemoryWriter {
    suspend fun append(file: String, content: String)
    suspend fun updateSection(file: String, section: String, content: String)
}
