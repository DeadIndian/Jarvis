package com.jarvis.engines

interface WakeWordEngine {
    fun startListening(onWake: () -> Unit)
    fun stopListening()
    fun isActive(): Boolean
}