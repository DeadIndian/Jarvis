package com.jarvis.app

import android.util.Log
import com.jarvis.logging.JarvisLogger

class AndroidLogJarvisLogger(
    private val onLog: ((String) -> Unit)? = null
) : JarvisLogger {
    override fun info(stage: String, message: String, data: Map<String, Any?>) {
        val line = formatLine(level = "INFO", stage = stage, message = message, data = data)
        Log.i("Jarvis/$stage", line)
        onLog?.invoke(line)
    }

    override fun warn(stage: String, message: String, data: Map<String, Any?>) {
        val line = formatLine(level = "WARN", stage = stage, message = message, data = data)
        Log.w("Jarvis/$stage", line)
        onLog?.invoke(line)
    }

    override fun error(stage: String, message: String, throwable: Throwable?, data: Map<String, Any?>) {
        val throwableSummary = throwable?.let {
            "${it::class.simpleName ?: "Throwable"}: ${it.message ?: "no message"}"
        }
        val line = formatLine(
            level = "ERROR",
            stage = stage,
            message = message,
            data = data,
            throwableSummary = throwableSummary
        )
        Log.e("Jarvis/$stage", line, throwable)
        onLog?.invoke(line)
    }

    private fun formatLine(
        level: String,
        stage: String,
        message: String,
        data: Map<String, Any?>,
        throwableSummary: String? = null
    ): String {
        val dataSuffix = if (data.isEmpty()) "" else " | data=$data"
        val throwableSuffix = throwableSummary?.let { " | cause=$it" }.orEmpty()
        return "[$level][$stage] $message$dataSuffix$throwableSuffix"
    }
}
