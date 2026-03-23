package com.jarvis.impl

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import com.jarvis.commands.CommandDefinition
import com.jarvis.core.ActionExecutor
import com.jarvis.core.ActionResult
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ActionExecutorImpl(private val context: Context) : ActionExecutor {
    override fun execute(command: CommandDefinition, rawInput: String): ActionResult {
        return try {
            when (command.id) {
                "system.time" -> {
                    val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    ActionResult(true, "It is $now")
                }

                "app.open.settings" -> {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    ActionResult(true, "Opened settings")
                }

                "assistant.mode.toggle" -> ActionResult(true, "Mode toggle requested")

                "reminder.quick" -> {
                    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    ActionResult(true, "Opened alarm app")
                }

                else -> ActionResult(true, command.response.success)
            }
        } catch (ex: Exception) {
            ActionResult(false, command.response.failure)
        }
    }
}
