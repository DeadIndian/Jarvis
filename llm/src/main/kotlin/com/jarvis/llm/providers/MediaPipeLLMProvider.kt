package com.jarvis.llm.providers

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.jarvis.llm.LLMProvider
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MediaPipeLLMProvider(
    private val context: Context,
    private val modelPath: String,
    private val logger: JarvisLogger = NoOpJarvisLogger,
    private val maxTokens: Int = 512,
    private val topK: Int = 40
) : LLMProvider {
    override val name: String = "mediapipe-litert"

    // MediaPipe LlmInference is not thread-safe and MUST be called from a single thread.
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    
    @Volatile
    private var inference: LlmInference? = null
    private val isProcessing = AtomicBoolean(false)

    private fun getOrInitInference(): LlmInference {
        return inference ?: synchronized(this) {
            inference ?: run {
                logger.info("llm", "Initializing MediaPipe LlmInference", mapOf("model" to modelPath))
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(maxTokens)
                    .setMaxTopK(topK)
                    .setPreferredBackend(LlmInference.Backend.GPU)
                    .build()
                LlmInference.createFromOptions(context, options).also {
                    inference = it 
                    logger.info("llm", "MediaPipe LlmInference initialized", mapOf("backend" to "GPU"))
                }
            }
        }
    }

    suspend fun warmup(): Boolean = withContext(dispatcher) {
        try {
            getOrInitInference()
            logger.info("llm", "MediaPipe model warmed up successfully")
            true
        } catch (e: Exception) {
            closeInference()
            logger.error("llm", "Failed to warmup MediaPipe on GPU", e)
            false
        }
    }

    override suspend fun complete(prompt: String): String = withContext(dispatcher) {
        if (isProcessing.getAndSet(true)) {
            logger.warn("llm", "MediaPipe is already processing a request. Skipping.")
            return@withContext ""
        }
        try {
            val engine = getOrInitInference()
            logger.info("llm", "Starting MediaPipe inference", mapOf("promptLength" to prompt.length))
            
            val response = engine.generateResponse(prompt) ?: ""
            logger.info("llm", "MediaPipe inference completed", mapOf("responseLength" to response.length))
            response
        } catch (e: Exception) {
            logger.error("llm", "MediaPipe inference failed on GPU", e)
            closeInference()
            ""
        } finally {
            isProcessing.set(false)
        }
    }

    private fun closeInference() {
        runCatching { inference?.close() }
        inference = null
    }

    fun close() {
        executor.execute {
            try {
                inference?.close()
                logger.info("llm", "MediaPipe LlmInference closed")
            } catch (e: Exception) {
                logger.error("llm", "Error during inference close", e)
            } finally {
                inference = null
            }
        }
        executor.shutdown()
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }
}
