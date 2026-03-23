package com.jarvis.core

interface AssistantOrchestrator {
    fun start()
    fun stop()
    fun processText(input: String): String
    fun processVoice(onResponse: (String) -> Unit, onError: (String) -> Unit)
    fun onWakeEvent(): String
}
