package com.jarvis.memory.chunking

class MarkdownSectionChunker(
    private val maxSectionTokens: Int = 500
) : Chunker {
    override fun chunk(filePath: String, markdown: String, timestamp: Long): List<MemoryChunkSeed> {
        val lines = markdown.lines()
        val chunks = mutableListOf<MemoryChunkSeed>()

        var currentSection = "General"
        val buffer = mutableListOf<String>()

        fun flush() {
            val raw = buffer.joinToString("\n").trim()
            buffer.clear()
            if (raw.isBlank()) return

            val normalized = normalizeWhitespace(raw)
            val tokens = tokenCount(normalized)
            if (tokens <= maxSectionTokens) {
                chunks += MemoryChunkSeed(
                    text = normalized,
                    filePath = filePath,
                    section = currentSection,
                    timestamp = timestamp,
                    tokenCount = tokens
                )
                return
            }

            chunks += splitLargeChunk(
                text = normalized,
                filePath = filePath,
                section = currentSection,
                timestamp = timestamp
            )
        }

        for (line in lines) {
            when {
                line.startsWith("## ") -> {
                    flush()
                    currentSection = line.removePrefix("## ").trim().ifBlank { "General" }
                }
                !line.startsWith("# ") -> {
                    buffer += line
                }
            }
        }
        flush()

        return chunks
    }

    private fun splitLargeChunk(
        text: String,
        filePath: String,
        section: String,
        timestamp: Long
    ): List<MemoryChunkSeed> {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val result = mutableListOf<MemoryChunkSeed>()
        val chunkBuilder = StringBuilder()
        var currentTokens = 0

        fun emit() {
            val value = chunkBuilder.toString().trim()
            if (value.isBlank()) return
            result += MemoryChunkSeed(
                text = value,
                filePath = filePath,
                section = section,
                timestamp = timestamp,
                tokenCount = tokenCount(value)
            )
            chunkBuilder.clear()
            currentTokens = 0
        }

        for (sentence in sentences) {
            val cleanSentence = sentence.trim()
            if (cleanSentence.isBlank()) continue
            val sentenceTokens = tokenCount(cleanSentence)
            if (currentTokens > 0 && currentTokens + sentenceTokens > maxSectionTokens) {
                emit()
            }
            if (chunkBuilder.isNotEmpty()) {
                chunkBuilder.append(' ')
            }
            chunkBuilder.append(cleanSentence)
            currentTokens += sentenceTokens
        }

        emit()
        return result
    }

    private fun tokenCount(text: String): Int {
        return text.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    private fun normalizeWhitespace(value: String): String {
        return value.replace(Regex("[\\t ]+"), " ").trim()
    }
}
