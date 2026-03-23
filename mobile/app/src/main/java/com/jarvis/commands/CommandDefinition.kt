package com.jarvis.commands

data class CommandDefinition(
    val id: String,
    val description: String,
    val activatesOn: CommandActivation,
    val executes: CommandExecution,
    val guards: CommandGuards = CommandGuards(),
    val response: CommandResponse = CommandResponse()
)

data class CommandActivation(
    val intents: List<String>,
    val entities: List<String> = emptyList()
)

data class CommandExecution(
    val handler: String,
    val timeoutMs: Long = 5_000L
)

data class CommandGuards(
    val requiresNetwork: Boolean = false,
    val allowedModes: Set<String> = setOf("active")
)

data class CommandResponse(
    val success: String = "Done.",
    val failure: String = "Sorry, I could not complete that."
)
