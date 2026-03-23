package com.jarvis.engines

interface TextToSpeechEngine {
    fun speak(text: String)
    fun stop()
}
