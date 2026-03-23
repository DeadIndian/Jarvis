package com.jarvis.core

interface AssistantOrchestrator {
    fun start()
    fun stop()
    fun handleWakeEvent()
    fun handleCommand(input: String)
}