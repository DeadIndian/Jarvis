package com.jarvis.engines

interface SpeechToTextEngine {
    fun transcribe(rawInput: String): String
    fun listenOnce(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun stopListening()
}
