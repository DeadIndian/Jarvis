package com.jarvis.app

import com.jarvis.llm.LLMProvider
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger

class MlKitNativeLLMProvider(
    private val modelManager: OnDeviceModelManager,
    private val logger: JarvisLogger = NoOpJarvisLogger
) : LLMProvider {
    override val name: String = "mlkit-native"

    override suspend fun complete(prompt: String): String {
        require(prompt.isNotBlank()) { "Prompt must not be blank" }

        val activeModel = modelManager.getActiveModelId()
        logger.info("llm", "Using MLKit native runtime", mapOf("modelId" to activeModel))
        return modelManager.complete(prompt).trim()
    }
}
