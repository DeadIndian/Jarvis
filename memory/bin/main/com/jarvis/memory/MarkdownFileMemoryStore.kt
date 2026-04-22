package com.jarvis.memory

import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

class MarkdownFileMemoryStore(
    private val baseDirectory: Path = Paths.get(System.getProperty("user.home"), ".jarvis", "memory"),
    private val logger: JarvisLogger = NoOpJarvisLogger
) : MemoryStore {
    override suspend fun put(key: String, value: String) {
        val target = pathForKey(key)
        Files.createDirectories(baseDirectory)
        target.toFile().writeText(value, StandardCharsets.UTF_8)
        logger.info("memory", "Memory item persisted", mapOf("key" to normalizeKey(key)))
    }

    override suspend fun get(key: String): String? {
        val target = pathForKey(key)
        if (!target.exists()) {
            return null
        }
        return target.toFile().readText(StandardCharsets.UTF_8)
    }

    override suspend fun search(query: String, limit: Int): List<String> {
        if (limit <= 0 || !baseDirectory.exists()) {
            return emptyList()
        }

        val normalizedQuery = normalizeSearchText(query)
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }

        val tokens = normalizedQuery.split(' ').filter { it.isNotBlank() }.toSet()
        val ranked = Files.list(baseDirectory).use { paths ->
            paths.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(MARKDOWN_SUFFIX) }
                .mapNotNull { file ->
                    val content = file.toFile().readText(StandardCharsets.UTF_8)
                    val combinedText = normalizeSearchText(file.name.removeSuffix(MARKDOWN_SUFFIX) + " " + content)
                    val score = tokens.count { token -> combinedText.contains(token) }
                    if (score <= 0) {
                        null
                    } else {
                        RankedResult(content = content, score = score)
                    }
                }
                .sortedWith(compareByDescending<RankedResult> { it.score }.thenBy { it.content.length })
                .take(limit)
                .toList()
        }

        logger.info("memory", "Memory search executed", mapOf("matches" to ranked.size, "limit" to limit))
        return ranked.map { it.content }
    }

    private fun pathForKey(key: String): Path {
        val normalized = normalizeKey(key)
        return baseDirectory.resolve("$normalized$MARKDOWN_SUFFIX")
    }

    private fun normalizeKey(key: String): String {
        val cleaned = key.trim().lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-')
        return cleaned.ifBlank { "memory-item" }
    }

    private fun normalizeSearchText(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private data class RankedResult(
        val content: String,
        val score: Int
    )

    companion object {
        private const val MARKDOWN_SUFFIX = ".md"
    }
}
