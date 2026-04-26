package com.jarvis.memory.embedding

interface EmbeddingModel {
    fun embed(text: String): FloatArray
}
