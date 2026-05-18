package com.jarvis.memory

data class KnowledgeNode(
    val id: String,
    val file: String,
    val type: NoteType,
    val title: String,
    val path: List<String> = emptyList(),
    val content: String = "",
    val summary: String = "",
    val tags: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val importance: Float = 0.5f,
    val confidence: Float = 1.0f,
    val level: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis()
)

enum class EdgeType {
    REFERENCE,
    PARENT,
    CHILD,
    RELATED,
    CONTRADICTS,
    DEPENDS_ON,
    EXTENDS,
    MENTIONS
}

data class GraphEdge(
    val id: String = "edge_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}",
    val fromNodeId: String,
    val toNodeId: String,
    val type: EdgeType,
    val weight: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
)