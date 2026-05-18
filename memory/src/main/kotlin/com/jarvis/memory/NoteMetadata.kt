package com.jarvis.memory

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class NoteMetadata(
    val id: String = "note_${System.currentTimeMillis()}",
    val type: NoteType = NoteType.INBOX,
    val title: String = "",
    val tags: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val importance: Float = 0.5f,
    val status: String = "active",
    val created: LocalDate = LocalDate.now(),
    val updated: LocalDate = LocalDate.now()
)

object FrontmatterParser {
    private val FRONTMATTER_REGEX = Regex("""^---\n([\s\S]*?)\n---""", RegexOption.MULTILINE)
    private val KEY_VALUE_REGEX = Regex("""^(\w+):\s*(.*)$""")
    private val LIST_REGEX = Regex("""^\s*-\s*(.+)$""")

    fun parse(content: String): Pair<NoteMetadata?, String> {
        val match = FRONTMATTER_REGEX.find(content) ?: return Pair(null, content)
        
        val frontmatter = match.groupValues[1]
        val body = content.substring(match.range.last + 1).trim()
        
        val metadata = parseFrontmatter(frontmatter)
        return Pair(metadata, body)
    }

    private fun parseFrontmatter(frontmatter: String): NoteMetadata {
        var id: String? = null
        var type: NoteType = NoteType.INBOX
        var title: String = ""
        val tags = mutableListOf<String>()
        val aliases = mutableListOf<String>()
        var importance: Float = 0.5f
        var status: String = "active"
        var created: LocalDate = LocalDate.now()
        var updated: LocalDate = LocalDate.now()

        val lines = frontmatter.lines()
        var currentList: MutableList<String>? = null

        for (line in lines) {
            val keyMatch = KEY_VALUE_REGEX.find(line)
            if (keyMatch != null) {
                currentList = null
                val key = keyMatch.groupValues[1]
                val value = keyMatch.groupValues[2].trim()

                when (key) {
                    "id" -> id = value
                    "type" -> type = runCatching { NoteType.valueOf(value.uppercase()) }.getOrElse { NoteType.INBOX }
                    "title" -> title = value
                    "importance" -> importance = value.toFloatOrNull() ?: 0.5f
                    "status" -> status = value
                    "created" -> created = LocalDate.parse(value, DateTimeFormatter.ISO_DATE)
                    "updated" -> updated = LocalDate.parse(value, DateTimeFormatter.ISO_DATE)
                    "tags" -> {
                        currentList = tags
                        if (value.isNotBlank()) tags.add(value)
                    }
                    "aliases" -> {
                        currentList = aliases
                        if (value.isNotBlank()) aliases.add(value)
                    }
                }
            } else {
                val listMatch = LIST_REGEX.find(line)
                if (listMatch != null && currentList != null) {
                    currentList.add(listMatch.groupValues[1].trim())
                }
            }
        }

        return NoteMetadata(
            id = id ?: "note_${System.currentTimeMillis()}",
            type = type,
            title = title,
            tags = tags,
            aliases = aliases,
            importance = importance,
            status = status,
            created = created,
            updated = updated
        )
    }

    fun serialize(metadata: NoteMetadata): String {
        return buildString {
            appendLine("---")
            appendLine("id: ${metadata.id}")
            appendLine("type: ${metadata.type.name.lowercase()}")
            appendLine("title: ${metadata.title}")
            append("tags:\n")
            metadata.tags.forEach { appendLine("  - $it") }
            append("aliases:\n")
            metadata.aliases.forEach { appendLine("  - $it") }
            appendLine("importance: ${metadata.importance}")
            appendLine("status: ${metadata.status}")
            appendLine("created: ${metadata.created.format(DateTimeFormatter.ISO_DATE)}")
            appendLine("updated: ${metadata.updated.format(DateTimeFormatter.ISO_DATE)}")
            appendLine("---")
        }
    }
}