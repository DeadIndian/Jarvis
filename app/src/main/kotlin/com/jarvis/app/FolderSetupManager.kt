package com.jarvis.app

import android.content.Context
import com.jarvis.llm.LLMProvider
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class FolderSetupResult(
    val needsSetup: Boolean,
    val hasMalformedFiles: Boolean,
    val malformedFiles: List<File> = emptyList()
)

data class FileClassificationResult(
    val file: File,
    val suggestedType: String,
    val suggestedTitle: String,
    val suggestedTags: List<String>,
    val suggestedFolder: String
)

class FolderSetupManager(
    private val context: Context,
    private val logger: JarvisLogger = NoOpJarvisLogger
) {
    private val requiredDirs = listOf(
        "notes/concepts",
        "notes/projects",
        "notes/procedures",
        "notes/memories",
        "notes/journal",
        "notes/people",
        "notes/inbox",
        "notes/references",
        "notes/system",
        "metadata",
        "embeddings",
        "graph",
        "cache",
        "derived/summaries",
        "derived/clusters",
        "derived/reflections",
        "logs"
    )

    suspend fun checkFolderSetup(basePath: String): FolderSetupResult = withContext(Dispatchers.IO) {
        val baseDir = File(basePath)
        
        if (!baseDir.exists()) {
            logger.info("FolderSetup", "Base folder does not exist: $basePath")
            return@withContext FolderSetupResult(needsSetup = true, hasMalformedFiles = false)
        }

        val missingDirs = requiredDirs.filter { !File(baseDir, it).exists() }
        if (missingDirs.isNotEmpty()) {
            logger.info("FolderSetup", "Missing directories: ${missingDirs.joinToString()}")
            return@withContext FolderSetupResult(needsSetup = true, hasMalformedFiles = false)
        }

        val malformedFiles = findMalformedFiles(baseDir)
        val hasMalformed = malformedFiles.isNotEmpty()
        
        if (hasMalformed) {
            logger.info("FolderSetup", "Found ${malformedFiles.size} malformed files")
        }

        FolderSetupResult(
            needsSetup = missingDirs.isNotEmpty() || !baseDir.exists(),
            hasMalformedFiles = hasMalformed,
            malformedFiles = malformedFiles
        )
    }

    suspend fun setupFolder(basePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseDir = File(basePath)
            if (!baseDir.exists()) {
                val createdBase = baseDir.mkdirs()
                if (!createdBase && !baseDir.exists()) {
                    logger.error("FolderSetup", "Failed to create base directory: $basePath. Check permissions or path validity.")
                    return@withContext false
                }
            }

            var allCreated = true
            for (dirPath in requiredDirs) {
                val dir = File(baseDir, dirPath)
                if (!dir.exists()) {
                    val created = dir.mkdirs()
                    if (!created && !dir.exists()) {
                        logger.warn("FolderSetup", "Failed to create directory: $dirPath in $basePath")
                        allCreated = false
                    } else {
                        logger.info("FolderSetup", "Created directory: $dirPath")
                    }
                }
            }

            allCreated
        } catch (e: Exception) {
            logger.error("FolderSetup", "Failed to setup folder: ${e.message}", e)
            false
        }
    }

    private fun findMalformedFiles(baseDir: File): List<File> {
        val malformedFiles = mutableListOf<File>()
        
        // Scan the root directory and the 'notes' subdirectory
        val dirsToScan = listOf(baseDir, File(baseDir, "notes"))
        
        dirsToScan.filter { it.exists() }.forEach { dir ->
            dir.walkTopDown()
                .maxDepth(2) // Only scan shallow to find files that need moving/formatting
                .filter { it.isFile && it.extension == "md" }
                .forEach { file ->
                    if (!isValidNoteFile(file)) {
                        malformedFiles.add(file)
                    }
                }
        }

        return malformedFiles.distinctBy { it.absolutePath }
    }

    private fun isValidNoteFile(file: File): Boolean {
        val content = file.readText()
        
        if (content.isBlank()) return false
        
        if (!content.trimStart().startsWith("---")) return false
        
        val frontmatterEnd = content.indexOf("\n---", 4)
        if (frontmatterEnd == -1) return false
        
        val frontmatter = content.substring(4, frontmatterEnd).trim()
        return try {
            frontmatter.contains("id:") ||
            frontmatter.contains("title:") ||
            frontmatter.contains("type:")
        } catch (_: Exception) {
            false
        }
    }

    suspend fun classifyFilesWithAI(
        files: List<File>,
        llmProvider: LLMProvider
    ): List<FileClassificationResult> = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<FileClassificationResult>()

        for (file in files) {
            try {
                val content = file.readText().take(2000)
                val classification = classifySingleFile(file, content, llmProvider)
                if (classification != null) {
                    results.add(classification)
                    logger.info("FolderSetup", "Classified ${file.name}: ${classification.suggestedType}/${classification.suggestedFolder}")
                }
            } catch (e: Exception) {
                logger.warn("FolderSetup", "Failed to classify ${file.name}: ${e.message}")
                results.add(FileClassificationResult(
                    file = file,
                    suggestedType = "inbox",
                    suggestedTitle = file.nameWithoutExtension,
                    suggestedTags = emptyList(),
                    suggestedFolder = "inbox"
                ))
            }
        }

        results
    }

    private suspend fun classifySingleFile(
        file: File,
        content: String,
        llmProvider: LLMProvider
    ): FileClassificationResult? {
        val fileName = file.name
        val prompt = """
You are a note classification assistant. Analyze the following note content and classify it.

File name: $fileName
Content:
$content

Respond with a JSON object in this exact format:
{
  "type": "one of: concept, project, procedure, memory, journal, person, task, research, system, idea, reference, inbox",
  "title": "A concise title for this note",
  "tags": ["tag1", "tag2"],
  "folder": "one of: concepts, projects, procedures, memories, journal, people, inbox, references, system"
}

Only respond with the JSON object, no other text.
""".trimIndent()

        return try {
            val response = llmProvider.complete(prompt)
            parseClassificationResponse(file, response.trim())
        } catch (e: Exception) {
            logger.warn("FolderSetup", "AI classification failed: ${e.message}")
            null
        }
    }

    private fun parseClassificationResponse(file: File, response: String): FileClassificationResult? {
        return try {
            val jsonStr = response
                .replace(Regex("```json\\s*"), "")
                .replace(Regex("```\\s*"), "")
                .trim()

            val json = if (jsonStr.startsWith("{")) {
                JSONObject(jsonStr)
            } else {
                val startIndex = jsonStr.indexOf("{")
                val endIndex = jsonStr.lastIndexOf("}") + 1
                if (startIndex >= 0 && endIndex > startIndex) {
                    JSONObject(jsonStr.substring(startIndex, endIndex))
                } else {
                    return null
                }
            }

            val type = json.optString("type", "inbox")
            val title = json.optString("title", file.nameWithoutExtension)
            val tagsArray = json.optJSONArray("tags")
            val tags = if (tagsArray != null) {
                (0 until tagsArray.length()).mapNotNull { tagsArray.optString(it) }
            } else emptyList()
            val folder = json.optString("folder", "inbox")

            FileClassificationResult(
                file = file,
                suggestedType = type,
                suggestedTitle = title,
                suggestedTags = tags,
                suggestedFolder = folder
            )
        } catch (e: Exception) {
            logger.warn("FolderSetup", "Failed to parse classification response: ${e.message}")
            null
        }
    }

    suspend fun reorganizeFiles(
        basePath: String,
        classifications: List<FileClassificationResult>
    ): Int = withContext(Dispatchers.IO) {
        val baseDir = File(basePath)
        var movedCount = 0

        for (classification in classifications) {
            try {
                val sourceFile = classification.file
                if (!sourceFile.exists()) continue

                val targetDir = File(File(baseDir, "notes"), classification.suggestedFolder)
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                val safeTitle = classification.suggestedTitle
                    .lowercase()
                    .replace(Regex("[^a-z0-9\\s-]"), "")
                    .replace(Regex("\\s+"), "-")
                    .take(50)
                    .ifBlank { "note" }

                val targetFile = File(targetDir, "$safeTitle.md")
                
                val newContent = addFrontmatter(sourceFile, classification)
                targetFile.writeText(newContent)
                
                if (targetFile.exists() && targetFile.length() > 0) {
                    sourceFile.delete()
                    movedCount++
                    logger.info("FolderSetup", "Moved ${sourceFile.name} to ${classification.suggestedFolder}/$safeTitle.md")
                }
            } catch (e: Exception) {
                logger.error("FolderSetup", "Failed to reorganize ${classification.file.name}: ${e.message}", e)
            }
        }

        movedCount
    }

    private fun addFrontmatter(sourceFile: File, classification: FileClassificationResult): String {
        val originalContent = sourceFile.readText()
        
        if (originalContent.trimStart().startsWith("---")) {
            return originalContent
        }

        val now = java.time.LocalDate.now().toString()
        val id = "note_${System.currentTimeMillis()}"

        val frontmatter = buildString {
            appendLine("---")
            appendLine("id: $id")
            appendLine("type: ${classification.suggestedType}")
            appendLine("title: ${classification.suggestedTitle}")
            if (classification.suggestedTags.isNotEmpty()) {
                appendLine("tags:")
                classification.suggestedTags.forEach { tag ->
                    appendLine("  - $tag")
                }
            }
            appendLine("status: active")
            appendLine("created: $now")
            appendLine("updated: $now")
            appendLine("---")
            appendLine()
        }

        return frontmatter + originalContent
    }
}
