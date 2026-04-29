package com.jarvis.app

import android.content.Context
import com.jarvis.memory.embedding.EmbeddingModel
import com.jarvis.memory.embedding.HashingEmbeddingModel
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.TextEmbedder
import com.google.mediapipe.tasks.text.TextEmbedderOptions
import kotlin.math.sqrt

/**
 * MediaPipe-based text embedding model for semantic memory retrieval.
 *
 * Uses MediaPipe's TextEmbedder task with a TFLite model (e.g., MobileBERT, MiniLM).
 * The model file must be placed in app/src/main/assets/embedding_model.tflite.
 *
 * If the model fails to load (missing file, unsupported device, etc.), falls back
 * to HashingEmbeddingModel (keyword-based) to ensure system stability.
 */
class MediaPipeTextEmbeddingModel(
    context: Context,
    private val modelAssetPath: String = "embedding_model.tflite"
) : EmbeddingModel {

    private val embedder: TextEmbedder? = initEmbedder(context)
    private val fallback = HashingEmbeddingModel()

    private fun initEmbedder(context: Context): TextEmbedder? {
        return try {
            val baseOptions = BaseOptions.Builder()
                .setModelAssetPath(modelAssetPath)
                .build()
            val options = TextEmbedderOptions.Builder()
                .setBaseOptions(baseOptions)
                .build()
            TextEmbedder.createFromOptions(context, options)
        } catch (e: Exception) {
            // Model not available or incompatible; fallback will be used
            return null
        }
    }

    override fun embed(text: String): FloatArray {
        // Try MediaPipe embedder first; on any failure, use fallback
        return try {
            embedder?.embed(text)?.embeddings?.firstOrNull()?.floatArray?.let { raw ->
                // Normalize to unit length for cosine similarity consistency
                normalizeL2(raw)
            } ?: fallback.embed(text)
        } catch (e: Exception) {
            // MediaPipe failed; use fallback
            fallback.embed(text)
        }
    }

    private fun normalizeL2(vector: FloatArray): FloatArray {
        var sumSq = 0.0
        for (v in vector) {
            sumSq += v * v
        }
        val norm = sqrt(sumSq).toFloat()
        return if (norm > 0f) {
            val normalized = FloatArray(vector.size) { i -> vector[i] / norm }
            normalized
        } else {
            vector
        }
    }
}
