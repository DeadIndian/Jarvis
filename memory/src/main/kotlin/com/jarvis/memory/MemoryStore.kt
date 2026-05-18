package com.jarvis.memory

interface MemoryStore {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
    suspend fun search(query: String, limit: Int = 5): List<String>
    suspend fun addNote(title: String, content: String, type: NoteType = NoteType.INBOX, tags: List<String> = emptyList())
    suspend fun retrieve(query: String, limit: Int = 5): List<RetrievalResult>
    suspend fun getGraph(nodeId: String, depth: Int = 2): List<KnowledgeNode>
}
