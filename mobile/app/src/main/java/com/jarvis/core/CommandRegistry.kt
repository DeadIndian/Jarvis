package com.jarvis.core

import com.jarvis.commands.CommandDefinition

interface CommandRegistry {
    fun register(command: CommandDefinition)
    fun list(): List<CommandDefinition>
    fun resolve(input: String): CommandDefinition?
    fun validateOrThrow()
}
