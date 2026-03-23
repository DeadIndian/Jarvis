package com.jarvis.engines

interface SpeechToTextEngine {
    fun transcribe(audioData: ByteArray, onResult: (String) -> Unit, onError: (Throwable) -> Unit)
}