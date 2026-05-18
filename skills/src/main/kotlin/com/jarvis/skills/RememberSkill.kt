package com.jarvis.skills

import com.jarvis.memory.BrainMemoryStore
import com.jarvis.memory.MemoryStore
import com.jarvis.memory.NoteType
import com.jarvis.memory.ingestion.*

class RememberSkill(private val memoryStore: MemoryStore?) : Skill {
    override val name: String = "RememberInfo"
    override val description: String = "Saves specific information or facts to memory for later retrieval."

    private var pipeline: MemoryIngestionPipeline? = null

    override suspend fun execute(input: Map<String, String>): String {
        val info = input["info"] ?: return "I don't have anything specific to remember."
        
        return if (memoryStore != null) {
            try {
                if (memoryStore is BrainMemoryStore) {
                    processWithPipeline(memoryStore, info, input["folder"])
                } else {
                    processLegacy(memoryStore, info, input["folder"])
                }
            } catch (e: Exception) {
                "I had trouble saving that to my memory: ${e.message}"
            }
        } else {
            "My memory storage is not currently available."
        }
    }

    private suspend fun processWithPipeline(memoryStore: BrainMemoryStore, info: String, folder: String?): String {
        android.util.Log.d("RememberSkill", "Processing with pipeline: info='$info', folder='$folder'")
        if (pipeline == null) {
            val brain = memoryStore.brain
            pipeline = MemoryIngestionPipeline(brain, brain.graph)
        }
        
        val ingestionInput = IngestionInput(
            rawText = info,
            userExplicitlyRequested = true,
            context = IngestionContext()
        )
        
        val result = pipeline!!.process(ingestionInput)
        android.util.Log.d("RememberSkill", "Pipeline result action: ${result.action}, targetPath: ${result.targetPath}")
        
        if (result.action == WriteAction.SKIP) {
            return "This doesn't seem like something worth remembering."
        }
        
        val writer = MemoryWriter(memoryStore.brain, PathResolver(memoryStore.brain.brainDir))
        val writeResult = writer.execute(result)
        android.util.Log.d("RememberSkill", "Write result: success=${writeResult.success}, message='${writeResult.message}', path='${writeResult.path}'")
        
        return if (writeResult.success) {
            buildResponseMessage(result, writeResult)
        } else {
            "I had trouble saving that: ${writeResult.message}"
        }
    }

    private fun buildResponseMessage(result: IngestionResult, writeResult: WriteResult): String {
        return when (result.action) {
            WriteAction.CREATE_FILE -> "Created new note: ${result.metadata?.title ?: "Untitled"}"
            WriteAction.CREATE_SECTION -> "Added to existing note as new section"
            WriteAction.APPEND_TO_SECTION -> "Appended to existing section"
            WriteAction.SKIP -> "Skipped"
            else -> "Stored successfully"
        }
    }

    private suspend fun processLegacy(memoryStore: MemoryStore, info: String, folder: String?): String {
        val (title, content) = parseRememberInput(info)
        
        val type = if (folder != null) inferNoteType(folder) else NoteType.INBOX
        memoryStore.addNote(title, content, type)
        
        return if (folder != null) {
            "I've stored '${title}' to memory in ${type.name.lowercase()}."
        } else {
            "I've stored '${title}' to memory."
        }
    }

    private fun parseRememberInput(info: String): Pair<String, String> {
        val title = extractTitle(info)
        val content = extractContent(info)
        return Pair(title, content)
    }

    private fun inferNoteType(folder: String): NoteType {
        val lower = folder.lowercase()
        return when {
            lower.contains("concept") -> NoteType.CONCEPT
            lower.contains("project") -> NoteType.PROJECT
            lower.contains("procedure") -> NoteType.PROCEDURE
            lower.contains("memory") -> NoteType.MEMORY
            lower.contains("journal") -> NoteType.JOURNAL
            lower.contains("person") -> NoteType.PERSON
            lower.contains("task") -> NoteType.TASK
            lower.contains("research") -> NoteType.RESEARCH
            lower.contains("idea") -> NoteType.IDEA
            lower.contains("reference") -> NoteType.REFERENCE
            else -> NoteType.INBOX
        }
    }

    private fun extractTitle(info: String): String {
        val firstLine = info.lines().firstOrNull()?.trim() ?: "Note"
        return firstLine
            .replace(Regex("^[#*-]+\\s*"), "")
            .take(50)
            .replace(Regex("[^a-zA-Z0-9\\s-]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Note" }
    }

    private fun extractContent(info: String): String {
        val lines = info.lines().drop(1).map { it.trim() }.filter { it.isNotBlank() }
        return if (lines.isEmpty()) {
            info.lines().firstOrNull()?.trim() ?: ""
        } else {
            lines.joinToString("\n")
        }
    }
}
