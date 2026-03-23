package com.jarvis.engines

interface WakeWordEngine {
    fun startListening(onWake: () -> Unit)
    fun stopListening()
    fun isListening(): Boolean
}
