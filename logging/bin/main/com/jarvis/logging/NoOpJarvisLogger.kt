package com.jarvis.logging

object NoOpJarvisLogger : JarvisLogger {
    override fun info(stage: String, message: String, data: Map<String, Any?>) = Unit

    override fun warn(stage: String, message: String, data: Map<String, Any?>) = Unit

    override fun error(stage: String, message: String, throwable: Throwable?, data: Map<String, Any?>) = Unit
}
