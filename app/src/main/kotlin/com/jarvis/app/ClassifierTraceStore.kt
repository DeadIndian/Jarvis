package com.jarvis.app

import android.content.Context
import com.jarvis.intent.ClassifierTrace
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

class ClassifierTraceStore(context: Context) {
    private val dir = File(context.filesDir, "classifier")
    private val traceFile = File(dir, "classification_traces.jsonl")

    @Synchronized
    fun append(trace: ClassifierTrace) {
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val line = JSONObject()
            .put("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
            .put("input", trace.input)
            .put("source", trace.source)
            .put("intent", trace.intent)
            .put("confidence", trace.confidence)
            .put("entities", JSONObject(trace.entities))
            .put("rawOutput", trace.rawOutput ?: "")
            .toString()

        traceFile.appendText(line + "\n")
    }

    @Synchronized
    fun exportSnapshot(exportDir: File): File? {
        if (!traceFile.exists()) return null
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }

        val outFile = File(exportDir, "jarvis-classifier-traces-${System.currentTimeMillis()}.jsonl")
        traceFile.copyTo(outFile, overwrite = true)
        return outFile
    }
}
