package com.jarvis.core

import com.jarvis.commands.CommandDefinition

interface ActionExecutor {
    fun execute(command: CommandDefinition, rawInput: String): ActionResult
}

data class ActionResult(
    val success: Boolean,
    val outputText: String
)
