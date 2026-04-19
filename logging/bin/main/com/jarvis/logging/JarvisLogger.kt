package com.jarvis.logging

interface JarvisLogger {
    fun info(stage: String, message: String, data: Map<String, Any?> = emptyMap())
    fun warn(stage: String, message: String, data: Map<String, Any?> = emptyMap())
    fun error(stage: String, message: String, throwable: Throwable? = null, data: Map<String, Any?> = emptyMap())
}
