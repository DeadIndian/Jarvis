package com.jarvis.memory.ingestion

import com.jarvis.memory.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class MemoryWriter(
    private val brain: Brain,
    private val pathResolver: PathResolver
) {
    suspend fun execute(result: IngestionResult): WriteResult {
        return when (result.action) {
            WriteAction.SKIP -> WriteResult(success = true, message = "Skipped: ${result.reason}")
            
            WriteAction.CREATE_FILE -> createNewFile(result)
            
            WriteAction.CREATE_SECTION -> createSection(result)
            
            WriteAction.APPEND_TO_SECTION -> appendToSection(result)
            
            WriteAction.UPDATE_METADATA -> updateMetadata(result)
            
            WriteAction.CREATE_LINK -> createLink(result)
            
            WriteAction.CREATE_TAG -> createTag(result)
            
            WriteAction.MOVE_NOTE -> moveNote(result)
        }
    }

    private suspend fun createNewFile(result: IngestionResult): WriteResult {
        val relativePath = result.targetPath 
            ?: pathResolver.resolve(result.memoryType, result.content.lines().firstOrNull() ?: "untitled", 
                IngestionContext(), emptyList())
        
        val fullPath = pathResolver.getFullPath(relativePath)
        android.util.Log.d("MemoryWriter", "Creating new file: relative='$relativePath', full='$fullPath'")
        pathResolver.ensureDirectoryExists(relativePath)
        
        val content = buildMarkdownContent(result)
        
        return try {
            fullPath.toFile().writeText(content)
            android.util.Log.d("MemoryWriter", "File written successfully: $fullPath")
            brain.reindexFile(fullPath.toString())
            WriteResult(
                success = true,
                message = "Created new note: ${fullPath.fileName}",
                path = fullPath.toString()
            )
        } catch (e: Exception) {
            android.util.Log.e("MemoryWriter", "Failed to write file: $fullPath", e)
            WriteResult(success = false, message = "Failed to create file: ${e.message}")
        }
    }

    private suspend fun createSection(result: IngestionResult): WriteResult {
        val targetPath = result.targetPath ?: return WriteResult(false, "No target file specified")
        val fullPath = pathResolver.getFullPath(targetPath)
        
        if (!fullPath.toFile().exists()) {
            return createNewFile(result)
        }
        
        return try {
            val existing = fullPath.toFile().readText()
            val heading = result.targetHeading ?: extractTitle(result.content)
            val newSection = "\n\n## $heading\n\n${result.content}\n"
            
            val updated = if (existing.endsWith("\n")) {
                existing + newSection
            } else {
                existing + "\n" + newSection
            }
            
            fullPath.toFile().writeText(updated)
            brain.reindexFile(fullPath.toString())
            
            WriteResult(
                success = true,
                message = "Added section '$heading' to ${fullPath.fileName}",
                path = fullPath.toString()
            )
        } catch (e: Exception) {
            WriteResult(success = false, message = "Failed to create section: ${e.message}")
        }
    }

    private suspend fun appendToSection(result: IngestionResult): WriteResult {
        val targetPath = result.targetPath ?: return WriteResult(false, "No target file specified")
        val fullPath = pathResolver.getFullPath(targetPath)
        
        if (!fullPath.toFile().exists()) {
            return createNewFile(result)
        }
        
        return try {
            val existing = fullPath.toFile().readText()
            val sectionHeading = "## ${result.targetHeading}"
            val sectionStart = existing.indexOf(sectionHeading)
            
            val updated = if (sectionStart >= 0) {
                val afterHeading = existing.indexOf("\n", sectionStart)
                if (afterHeading >= 0) {
                    val sectionEnd = existing.indexOf("\n## ", afterHeading + 1)
                    val before = existing.substring(0, afterHeading + 1)
                    val sectionContent = if (sectionEnd >= 0) existing.substring(afterHeading + 1, sectionEnd) else existing.substring(afterHeading + 1)
                    val after = if (sectionEnd >= 0) existing.substring(sectionEnd) else ""
                    
                    val appendedContent = sectionContent.trimEnd() + "\n- " + result.content.lines().firstOrNull()
                    
                    before + appendedContent + after
                } else {
                    existing + "\n- " + result.content.lines().firstOrNull()
                }
            } else {
                existing + "\n\n## ${result.targetHeading}\n\n- ${result.content}\n"
            }
            
            fullPath.toFile().writeText(updated)
            brain.reindexFile(fullPath.toString())
            
            WriteResult(
                success = true,
                message = "Appended to section '${result.targetHeading}' in ${fullPath.fileName}",
                path = fullPath.toString()
            )
        } catch (e: Exception) {
            WriteResult(success = false, message = "Failed to append to section: ${e.message}")
        }
    }

    private suspend fun updateMetadata(result: IngestionResult): WriteResult {
        return WriteResult(true, "Metadata update not yet implemented")
    }

    private suspend fun createLink(result: IngestionResult): WriteResult {
        return WriteResult(true, "Link creation not yet implemented")
    }

    private suspend fun createTag(result: IngestionResult): WriteResult {
        return WriteResult(true, "Tag creation not yet implemented")
    }

    private suspend fun moveNote(result: IngestionResult): WriteResult {
        return WriteResult(true, "Note moving not yet implemented")
    }

    private fun buildMarkdownContent(result: IngestionResult): String {
        val metadata = result.metadata
        val title = metadata?.title ?: extractTitle(result.content)
        
        return buildString {
            appendLine("---")
            appendLine("id: note_${System.currentTimeMillis()}")
            if (metadata != null) {
                appendLine("type: ${metadata.type.name.lowercase()}")
                appendLine("title: ${metadata.title}")
                if (metadata.tags.isNotEmpty()) {
                    append("tags:")
                    metadata.tags.forEach { append(" $it") }
                    appendLine()
                }
                appendLine("importance: ${metadata.importance}")
            }
            appendLine("created: ${java.time.LocalDate.now()}")
            appendLine("---")
            appendLine()
            appendLine("# $title")
            appendLine()
            appendLine(result.content)
        }
    }

    private fun extractTitle(text: String): String {
        return text.lines().firstOrNull()?.trim()
            ?.replace(Regex("^[#*-]+\\s*"), "")
            ?.take(50)
            ?: "Untitled"
    }
}

data class WriteResult(
    val success: Boolean,
    val message: String,
    val path: String? = null
)