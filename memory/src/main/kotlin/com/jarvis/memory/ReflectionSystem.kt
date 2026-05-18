package com.jarvis.memory

import android.content.Context
import java.time.LocalDate

class ReflectionSystem(
    private val context: Context,
    private val brainDir: BrainDirectory
) {
    private val graph: KnowledgeGraph by lazy { 
        KnowledgeGraph.getInstance(context, brainDir.graphEdges) 
    }

    suspend fun generateSummary(nodeId: String): String {
        val node = graph.getNode(nodeId) ?: return ""
        
        return buildString {
            appendLine("# Summary: ${node.title}")
            appendLine()
            appendLine("## Overview")
            appendLine(node.summary)
            appendLine()
            appendLine("## Key Points")
            
            val sentences = node.content.split(Regex("[.!?]")).filter { it.isNotBlank() }
            sentences.take(3).forEach { sentence ->
                appendLine("- ${sentence.trim()}")
            }
            appendLine()
            appendLine("## Connected Topics")
            
            val related = graph.getRelatedNodes(nodeId, 1)
            related.forEach { relatedNode ->
                appendLine("- [[${relatedNode.title}]]")
            }
        }
    }

    suspend fun inferRelationships(): List<GraphEdge> {
        val nodes = graph.getAllNodes()
        val newEdges = mutableListOf<GraphEdge>()
        
        for (node in nodes) {
            for (other in nodes) {
                if (node.id != other.id && !hasEdge(node.id, other.id)) {
                    val similarity = calculateSimilarity(node, other)
                    if (similarity > 0.6f) {
                        newEdges.add(GraphEdge(
                            fromNodeId = node.id,
                            toNodeId = other.id,
                            type = EdgeType.RELATED,
                            weight = similarity
                        ))
                    }
                }
            }
        }
        
        return newEdges
    }

    private fun hasEdge(fromId: String, toId: String): Boolean {
        return graph.getEdgesFrom(fromId).any { it.toNodeId == toId } ||
               graph.getEdgesTo(fromId).any { it.fromNodeId == toId }
    }

    private fun calculateSimilarity(a: KnowledgeNode, b: KnowledgeNode): Float {
        var score = 0f
        var factors = 0
        
        if (a.type == b.type) {
            score += 0.3f
        }
        factors++
        
        val commonTags = a.tags.intersect(b.tags.toSet())
        if (commonTags.isNotEmpty()) {
            score += 0.4f * (commonTags.size.toFloat() / maxOf(a.tags.size, b.tags.size))
        }
        factors++
        
        val commonLinks = a.links.intersect(b.links.toSet())
        if (commonLinks.isNotEmpty()) {
            score += 0.3f * (commonLinks.size.toFloat() / maxOf(a.links.size, b.links.size))
        }
        factors++
        
        return if (factors > 0) score else 0f
    }

    suspend fun clusterRelatedNodes(): Map<String, List<KnowledgeNode>> {
        val nodes = graph.getAllNodes()
        val clusters = mutableMapOf<String, MutableList<KnowledgeNode>>()
        
        for (node in nodes) {
            val clusterKey = node.tags.firstOrNull() ?: node.type.name.lowercase()
            clusters.getOrPut(clusterKey) { mutableListOf() }.add(node)
        }
        
        return clusters
    }

    suspend fun saveReflection(nodeId: String, content: String) {
        val node = graph.getNode(nodeId) ?: return
        val reflectionDir = brainDir.derivedReflections
        reflectionDir.toFile().mkdirs()
        
        val fileName = "reflection_${nodeId}_${System.currentTimeMillis()}.md"
        val file = reflectionDir.resolve(fileName)
        
        val reflectionContent = buildString {
            appendLine("# Reflection")
            appendLine()
            appendLine("## Source")
            appendLine("[[${node.title}]]")
            appendLine()
            appendLine("## Generated Insight")
            appendLine(content)
            appendLine()
            appendLine("---")
            appendLine("*Generated: ${LocalDate.now()}*")
        }
        
        file.toFile().writeText(reflectionContent)
    }

    suspend fun createSummary(nodeId: String): String {
        val node = graph.getNode(nodeId) ?: return ""
        
        val summaryDir = brainDir.derivedSummaries
        summaryDir.toFile().mkdirs()
        
        val content = buildString {
            appendLine("# ${node.title}")
            appendLine()
            appendLine("**Type:** ${node.type.name}")
            appendLine("**Created:** ${LocalDate.ofEpochDay(node.createdAt / 86400000)}")
            appendLine("**Updated:** ${LocalDate.ofEpochDay(node.updatedAt / 86400000)}")
            appendLine()
            appendLine("## Summary")
            appendLine(node.summary)
            appendLine()
            if (node.tags.isNotEmpty()) {
                appendLine("## Tags")
                node.tags.forEach { appendLine("- $it") }
                appendLine()
            }
            if (node.links.isNotEmpty()) {
                appendLine("## Related")
                node.links.forEach { appendLine("- [[$it]]") }
            }
        }
        
        val fileName = "summary_${node.title.lowercase().replace(" ", "-")}.md"
        val file = summaryDir.resolve(fileName)
        file.toFile().writeText(content)
        
        return content
    }
}