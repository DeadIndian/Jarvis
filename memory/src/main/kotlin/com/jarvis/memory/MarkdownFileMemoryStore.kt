package com.jarvis.memory

import com.jarvis.memory.chunking.Chunker
import com.jarvis.memory.chunking.MarkdownSectionChunker
import com.jarvis.memory.index.AndroidSqliteVectorStore
import com.jarvis.memory.embedding.EmbeddingModel
import com.jarvis.memory.embedding.HashingEmbeddingModel
import com.jarvis.memory.index.InMemoryVectorStore
import com.jarvis.memory.index.JdbcSqliteVectorStore
import com.jarvis.memory.index.VectorStore
import com.jarvis.memory.models.MemoryChunk
import com.jarvis.memory.retrieval.MemoryRetriever
import com.jarvis.memory.writer.MemoryWriter
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.math.max
import kotlin.streams.asSequence

class MarkdownFileMemoryStore(
    private val baseDirectory: Path = Paths.get(System.getProperty("user.home"), ".jarvis", "memory"),
    private val chunker: Chunker = MarkdownSectionChunker(),
    private val embeddingModel: EmbeddingModel = HashingEmbeddingModel(),
    private val vectorStore: VectorStore = defaultVectorStore(baseDirectory),
    private val logger: JarvisLogger = NoOpJarvisLogger,
    private val writesEnabled: Boolean = true
) : MemoryStore, MemoryWriter, MemoryRetriever {
    private val fileRevision = mutableMapOf<String, Long>()

    init {
        ensureLayout()
    }

    override suspend fun put(key: String, value: String) {
        if (!writesEnabled) {
            logger.info("memory", "Ignoring put in read-only mode", mapOf("key" to key))
            return
        }
        ensureLayout()
        val target = pathForKey(key)
        Files.createDirectories(target.parent)
        val markdown = ensureMarkdownFormat(value, title = normalizeKey(key).replace('-', ' '))
        target.toFile().writeText(markdown, StandardCharsets.UTF_8)
        reindexFile(target)
        logger.info(
            "memory",
            "Memory item persisted",
            mapOf("key" to normalizeKey(key), "path" to relativePath(target))
        )
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

        ensureIndexed()

        val ranked = rankChunks(normalizedQuery, limit.coerceAtLeast(1))

        logger.info("memory", "Memory search executed", mapOf("matches" to ranked.size, "limit" to limit))
        return ranked.map { chunk ->
            val file = fileNameFromPath(chunk.filePath)
            "(${file} - ${chunk.section})\n${chunk.text}"
        }
    }

    override suspend fun append(file: String, content: String) {
        if (!writesEnabled) {
            logger.info("memory", "Ignoring append in read-only mode", mapOf("file" to file))
            return
        }
        ensureLayout()
        val target = pathForFile(file)
        Files.createDirectories(target.parent)

        val appendText = content.trim()
        if (appendText.isBlank()) {
            return
        }

        val existing = if (target.exists()) {
            target.toFile().readText(StandardCharsets.UTF_8)
        } else {
            "# ${titleForPath(target)}\n\n## Notes\n"
        }

        validateMarkdown(existing)
        val updated = buildString {
            append(existing.trimEnd())
            append("\n")
            append(appendText)
            append("\n")
        }
        target.toFile().writeText(updated, StandardCharsets.UTF_8)
        reindexFile(target)
    }

    override suspend fun updateSection(file: String, section: String, content: String) {
        if (!writesEnabled) {
            logger.info(
                "memory",
                "Ignoring updateSection in read-only mode",
                mapOf("file" to file, "section" to section)
            )
            return
        }
        ensureLayout()
        val target = pathForFile(file)
        require(target.exists()) { "Memory file not found: $file" }

        val markdown = target.toFile().readText(StandardCharsets.UTF_8)
        validateMarkdown(markdown)

        val lines = markdown.lines()
        val sectionHeader = "## ${section.trim()}"
        val replacement = content.trim().ifBlank { "- (empty)" }

        val start = lines.indexOfFirst { it.trim() == sectionHeader }
        require(start >= 0) { "Section not found: $section" }

        var end = start + 1
        while (end < lines.size && !lines[end].startsWith("## ")) {
            end++
        }

        val rebuilt = buildList {
            addAll(lines.subList(0, start + 1))
            add("")
            addAll(replacement.lines())
            if (end < lines.size) {
                add("")
                addAll(lines.subList(end, lines.size))
            }
        }.joinToString("\n")

        target.toFile().writeText(rebuilt.trimEnd() + "\n", StandardCharsets.UTF_8)
        reindexFile(target)
    }

    override fun retrieve(query: String, k: Int): List<MemoryChunk> {
        if (query.isBlank()) {
            return emptyList()
        }
        ensureIndexed()
        return rankChunks(query, k.coerceAtLeast(1))
    }

    private fun pathForKey(key: String): Path {
        if ("/" !in key) {
            return when {
                key.startsWith("interaction-") -> baseDirectory.resolve(DAILY_LOGS_DIR)
                    .resolve("${normalizeKey(key)}$MARKDOWN_SUFFIX")
                else -> baseDirectory.resolve(NOTES_DIR).resolve("${normalizeKey(key)}$MARKDOWN_SUFFIX")
            }
        }

        val rawSegments = key.split('/').filter { it.isNotBlank() }
        val namespace = rawSegments.firstOrNull().orEmpty()
        val fileName = rawSegments.drop(1).joinToString("-")
        val safeFile = normalizeKey(fileName.ifBlank { "memory-item" }) + MARKDOWN_SUFFIX

        val dir = when (namespace.lowercase()) {
            DAILY_LOGS_DIR -> DAILY_LOGS_DIR
            NOTES_DIR -> NOTES_DIR
            PREFERENCES_DIR -> PREFERENCES_DIR
            SYSTEM_DIR -> SYSTEM_DIR
            else -> NOTES_DIR
        }
        return baseDirectory.resolve(dir).resolve(safeFile)
    }

    private fun pathForFile(file: String): Path {
        val normalized = file.trim().replace('\\', '/')
        if (normalized.isBlank()) {
            throw IllegalArgumentException("File path cannot be blank")
        }
        val asPath = baseDirectory.resolve(normalized).normalize()
        require(asPath.startsWith(baseDirectory.normalize())) { "Writes are restricted to memory directory" }
        return if (asPath.name.endsWith(MARKDOWN_SUFFIX)) asPath else asPath.resolveSibling(asPath.name + MARKDOWN_SUFFIX)
    }

    private fun ensureLayout() {
        Files.createDirectories(baseDirectory.resolve(DAILY_LOGS_DIR))
        Files.createDirectories(baseDirectory.resolve(NOTES_DIR))
        Files.createDirectories(baseDirectory.resolve(PREFERENCES_DIR))
        Files.createDirectories(baseDirectory.resolve(SYSTEM_DIR))
    }

    private fun ensureIndexed() {
        if (!baseDirectory.exists()) {
            return
        }

        Files.walk(baseDirectory).use { paths ->
            paths.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(MARKDOWN_SUFFIX) }
                .forEach { file ->
                    val filePath = file.absolutePathString()
                    val revision = file.toFile().lastModified()
                    if (fileRevision[filePath] != revision) {
                        reindexFile(file)
                    }
                }
        }
    }

    private fun reindexFile(file: Path) {
        if (!file.exists()) {
            return
        }

        val markdown = file.toFile().readText(StandardCharsets.UTF_8)
        val normalized = ensureMarkdownFormat(markdown, title = titleForPath(file))
        if (normalized != markdown) {
            file.toFile().writeText(normalized, StandardCharsets.UTF_8)
        }

        val timestamp = file.toFile().lastModified()
        val seeds = chunker.chunk(relativePath(file), normalized, timestamp)
        val chunks = seeds.map { seed ->
            MemoryChunk(
                id = UUID.randomUUID().toString(),
                text = seed.text,
                filePath = seed.filePath,
                section = seed.section,
                timestamp = seed.timestamp,
                tokenCount = seed.tokenCount,
                embedding = embeddingModel.embed(seed.text)
            )
        }
        vectorStore.upsertFileChunks(relativePath(file), chunks)
        fileRevision[file.absolutePathString()] = timestamp
    }

    private fun rankChunks(query: String, limit: Int): List<MemoryChunk> {
        val queryVector = embeddingModel.embed(query)
        val now = Instant.now().epochSecond

        return vectorStore.getAllChunks()
            .map { chunk ->
                val similarity = cosineSimilarity(queryVector, chunk.embedding).coerceIn(0f, 1f)
                val recency = recencyWeight(now, chunk.timestamp)
                val source = sourceWeight(chunk.filePath)
                val score = (similarity * 0.7f) + (recency * 0.2f) + (source * 0.1f)
                ScoredChunk(chunk, score)
            }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.chunk }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) {
            return 0f
        }
        var dot = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
        }
        return max(dot, 0f)
    }

    private fun recencyWeight(nowEpochSec: Long, timestampMs: Long): Float {
        if (timestampMs <= 0L) {
            return 0f
        }
        val ageSeconds = max(nowEpochSec - (timestampMs / 1000L), 0L)
        val ageDays = ageSeconds / 86_400.0
        return (1.0 / (1.0 + (ageDays / 30.0))).toFloat()
    }

    private fun sourceWeight(filePath: String): Float {
        val normalized = filePath.replace('\\', '/').lowercase()
        return when {
            "/$PREFERENCES_DIR/" in normalized -> 1.0f
            "/$NOTES_DIR/" in normalized -> 0.8f
            "/$DAILY_LOGS_DIR/" in normalized -> 0.5f
            else -> 0.6f
        }
    }

    private fun ensureMarkdownFormat(content: String, title: String): String {
        val trimmed = content.trim()
        if (trimmed.isBlank()) {
            return "# ${title.sanitizeTitle()}\n\n## Notes\n\n- (empty)\n"
        }

        return if (isValidMarkdown(trimmed)) {
            trimmed + "\n"
        } else {
            val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
            val bullets = lines.joinToString("\n") { "- $it" }
            "# ${title.sanitizeTitle()}\n\n## Notes\n\n$bullets\n"
        }
    }

    private fun validateMarkdown(markdown: String) {
        require(isValidMarkdown(markdown.trim())) {
            "Malformed markdown. Only # and ## headings are allowed."
        }
    }

    private fun isValidMarkdown(markdown: String): Boolean {
        val lines = markdown.lines()
        val firstContentLine = lines.firstOrNull { it.trim().isNotEmpty() } ?: return false
        if (!firstContentLine.startsWith("# ")) {
            return false
        }
        return lines.none { line ->
            val trimmed = line.trim()
            trimmed.startsWith("###") || trimmed.startsWith("####")
        }
    }

    private fun relativePath(path: Path): String {
        return baseDirectory.normalize().relativize(path.normalize()).toString().replace('\\', '/')
    }

    private fun titleForPath(path: Path): String {
        return path.name.removeSuffix(MARKDOWN_SUFFIX).replace('-', ' ').replace('_', ' ')
    }

    private fun normalizeKey(key: String): String {
        val cleaned = key.trim().lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-')
        return cleaned.ifBlank { "memory-item" }
    }

    private fun normalizeSearchText(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private fun fileNameFromPath(value: String): String {
        val normalized = value.replace('\\', '/')
        return normalized.substringAfterLast('/', normalized).ifBlank { normalized }
    }

    private fun String.sanitizeTitle(): String {
        return trim().ifBlank { "Memory" }.split(Regex("\\s+"))
            .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }
    }

    private data class ScoredChunk(
        val chunk: MemoryChunk,
        val score: Float
    )

    companion object {
        private const val MARKDOWN_SUFFIX = ".md"
        private const val DAILY_LOGS_DIR = "daily_logs"
        private const val NOTES_DIR = "notes"
        private const val PREFERENCES_DIR = "preferences"
        private const val SYSTEM_DIR = "system"

        private fun defaultVectorStore(baseDirectory: Path): VectorStore {
            val dbPath = baseDirectory.resolve(SYSTEM_DIR).resolve("memory_index.db")
            return runCatching {
                JdbcSqliteVectorStore(dbPath)
            }.recoverCatching {
                AndroidSqliteVectorStore(dbPath)
            }.getOrElse {
                InMemoryVectorStore()
            }
        }
    }
}
