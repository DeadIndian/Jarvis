package com.jarvis.memory.embedding

import kotlin.math.sqrt

class HashingEmbeddingModel(
    private val dimensions: Int = 384
) : EmbeddingModel {
    override fun embed(text: String): FloatArray {
        val vector = FloatArray(dimensions)
        val tokens = normalize(text)
        if (tokens.isEmpty()) {
            return vector
        }

        for (token in tokens) {
            val hash = token.hashCode()
            val index = hash.mod(dimensions)
            val sign = if ((hash and 1) == 0) 1f else -1f
            vector[index] += sign
        }

        normalizeL2(vector)
        return vector
    }

    private fun normalize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    private fun normalizeL2(vector: FloatArray) {
        var sum = 0.0
        for (value in vector) {
            sum += value * value
        }
        if (sum <= 0.0) {
            return
        }
        val norm = sqrt(sum).toFloat()
        for (i in vector.indices) {
            vector[i] /= norm
        }
    }
}
