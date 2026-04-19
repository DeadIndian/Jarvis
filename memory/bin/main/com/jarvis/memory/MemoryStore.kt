package com.jarvis.memory

interface MemoryStore {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
    suspend fun search(query: String, limit: Int = 5): List<String>
}
