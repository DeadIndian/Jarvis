package com.jarvis.app

import com.jarvis.llm.LLMProvider
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger

class LiteRtNativeLLMProvider(
    private val modelManager: OnDeviceModelManager,
    private val logger: JarvisLogger = NoOpJarvisLogger
) : LLMProvider {
    override val name: String = "litert-native"

    override suspend fun complete(prompt: String): String {
        require(prompt.isNotBlank()) { "Prompt must not be blank" }

        val activeModel = modelManager.getActiveModelId()
        logger.info("llm", "Using LiteRT local runtime", mapOf("modelId" to activeModel))
        return modelManager.complete(prompt).trim()
    }
}
