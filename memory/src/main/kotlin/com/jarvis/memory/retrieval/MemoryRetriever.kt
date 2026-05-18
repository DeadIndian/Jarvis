package com.jarvis.memory.retrieval

import com.jarvis.memory.models.MemoryChunk

interface MemoryRetriever {
    fun retrieveChunks(query: String, k: Int = 5): List<MemoryChunk>
}
