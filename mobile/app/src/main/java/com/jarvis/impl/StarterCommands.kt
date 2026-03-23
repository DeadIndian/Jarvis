package com.jarvis.impl

import com.jarvis.commands.CommandActivation
import com.jarvis.commands.CommandDefinition
import com.jarvis.commands.CommandExecution
import com.jarvis.commands.CommandResponse

object StarterCommands {
    fun all(): List<CommandDefinition> {
        return listOf(
            CommandDefinition(
                id = "system.time",
                description = "Tell current time",
                activatesOn = CommandActivation(intents = listOf("time", "what time")),
                executes = CommandExecution(handler = "system/time"),
                response = CommandResponse(success = "Here is the current time")
            ),
            CommandDefinition(
                id = "app.open.settings",
                description = "Open device settings",
                activatesOn = CommandActivation(intents = listOf("open settings", "settings")),
                executes = CommandExecution(handler = "device/open_settings"),
                response = CommandResponse(success = "Opening settings")
            ),
            CommandDefinition(
                id = "reminder.quick",
                description = "Create a reminder",
                activatesOn = CommandActivation(intents = listOf("remind me", "set reminder")),
                executes = CommandExecution(handler = "assistant/reminder"),
                response = CommandResponse(success = "Opening reminder flow")
            ),
            CommandDefinition(
                id = "assistant.mode.toggle",
                description = "Toggle assistant mode",
                activatesOn = CommandActivation(intents = listOf("mode", "assistant mode")),
                executes = CommandExecution(handler = "assistant/toggle_mode"),
                response = CommandResponse(success = "Mode command accepted")
            ),
            CommandDefinition(
                id = "assistant.help",
                description = "List available commands",
                activatesOn = CommandActivation(intents = listOf("help", "what can you do")),
                executes = CommandExecution(handler = "assistant/help"),
                response = CommandResponse(success = "You can ask time, open settings, set reminder, or change mode")
            )
        )
    }
}
