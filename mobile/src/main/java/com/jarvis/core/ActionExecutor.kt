package com.jarvis.core

interface ActionExecutor {
    fun execute(commandId: String, params: Map<String, Any>? = null): Boolean
}