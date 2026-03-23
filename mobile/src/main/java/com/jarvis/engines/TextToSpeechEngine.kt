package com.jarvis.engines

interface TextToSpeechEngine {
    fun speak(text: String, onDone: (() -> Unit)? = null)
    fun stop()
}