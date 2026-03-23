package com.jarvis.impl

import com.jarvis.commands.CommandDefinition
import com.jarvis.core.CommandRegistry

class CommandRegistryImpl : CommandRegistry {
    private val commands = linkedMapOf<String, CommandDefinition>()

    override fun register(command: CommandDefinition) {
        commands[command.id] = command
    }

    override fun list(): List<CommandDefinition> = commands.values.toList()

    override fun resolve(input: String): CommandDefinition? {
        val normalized = input.lowercase()
        return commands.values.firstOrNull { cmd ->
            cmd.activatesOn.intents.any { normalized.contains(it.lowercase()) }
        }
    }

    override fun validateOrThrow() {
        commands.values.forEach { cmd ->
            require(cmd.id.isNotBlank()) { "Command id cannot be blank" }
            require(cmd.activatesOn.intents.isNotEmpty()) { "Command ${cmd.id} has no activation intents" }
            require(cmd.executes.handler.isNotBlank()) { "Command ${cmd.id} has no handler" }
        }
    }
}
