package com.jarvis.memory.ingestion

import com.jarvis.memory.BrainDirectory
import java.nio.file.Path
import java.nio.file.Paths

class PathResolver(private val brainDir: BrainDirectory) {
    private val memoryTypeToFolder = mapOf(
        MemoryType.SEMANTIC to "concepts",
        MemoryType.EPISODIC to "memories",
        MemoryType.PROCEDURAL to "procedures",
        MemoryType.PROJECT to "projects",
        MemoryType.PERSON to "people",
        MemoryType.TASK to "inbox",
        MemoryType.REFERENCE to "references",
        MemoryType.IDEA to "inbox",
        MemoryType.PREFERENCE to "system"
    )

    fun resolve(
        memoryType: MemoryType,
        title: String,
        context: IngestionContext,
        matches: List<KnowledgeMatch>
    ): String {
        if (matches.isNotEmpty()) {
            val bestMatch = matches.maxByOrNull { it.similarity }!!
            return bestMatch.filePath
        }

        if (context.activeProject != null && memoryType == MemoryType.SEMANTIC) {
            val projectFolder = sanitizeFolderName(context.activeProject)
            return "notes/projects/$projectFolder/${sanitizeFileName(title)}.md"
        }

        val folder = memoryTypeToFolder[memoryType] ?: "inbox"
        
        val subfolder = extractSubfolder(title, memoryType)
        val fullFolder = if (subfolder.isNotEmpty()) {
            "$folder/$subfolder"
        } else {
            folder
        }

        return "notes/$fullFolder/${sanitizeFileName(title)}.md"
    }

    fun getFullPath(relativePath: String): Path {
        return brainDir.base.resolve(relativePath)
    }

    private fun extractSubfolder(title: String, memoryType: MemoryType): String {
        val words = title.lowercase().split(Regex("\\s+"))
        
        return when (memoryType) {
            MemoryType.SEMANTIC -> {
                val techKeywords = listOf("ai", "ml", "data", "web", "mobile", "cloud", "security", "network")
                words.find { it in techKeywords } ?: ""
            }
            MemoryType.PROCEDURAL -> {
                val toolKeywords = listOf("docker", "kubernetes", "git", "python", "java", "javascript", "bash", "shell")
                words.find { it in toolKeywords } ?: ""
            }
            MemoryType.PROJECT -> {
                ""
            }
            else -> ""
        }
    }

    private fun sanitizeFolderName(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .take(30)
            .trim('-')
    }

    private fun sanitizeFileName(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .take(50)
            .trim('-')
            .ifBlank { "untitled" }
    }

    fun ensureDirectoryExists(relativePath: String) {
        val fullPath = getFullPath(relativePath)
        fullPath.parent.toFile().mkdirs()
    }
}