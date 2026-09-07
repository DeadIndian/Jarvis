package com.jarvis.app.core.security

import com.jarvis.app.core.contracts.ActionEvent

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class ValidWithConfirmation(val reason: String) : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

/**
 * ActionValidator verifies tool execution requests before running them on the phone.
 * Validates tool names, argument types, value ranges, and safety/permission criteria.
 */
object ActionValidator {

    private val ALLOWED_TOOLS = setOf(
        "set_volume",
        "toggle_bluetooth",
        "toggle_wifi",
        "media_control",
        "open_app",
        "make_call",
        "send_whatsapp",
        "read_notifications",
        "toggle_flashlight"
    )

    private val SENSITIVE_TOOLS = setOf(
        "make_call",
        "send_whatsapp",
        "send_sms",
        "delete_data"
    )

    fun validate(action: ActionEvent.PhoneToolAction): ValidationResult {
        val tool = action.tool
        if (tool !in ALLOWED_TOOLS) {
            return ValidationResult.Invalid("Tool '$tool' is not in the allowlist of valid phone tools.")
        }

        // Validate tool-specific arguments and ranges
        when (tool) {
            "set_volume" -> {
                val levelRaw = action.arguments["level"]
                val level = when (levelRaw) {
                    is Number -> levelRaw.toInt()
                    is String -> levelRaw.toIntOrNull()
                    else -> null
                }
                if (level == null || level !in 0..100) {
                    return ValidationResult.Invalid("Tool 'set_volume' requires argument 'level' between 0 and 100.")
                }
            }

            "toggle_bluetooth", "toggle_wifi" -> {
                val enable = action.arguments["enable"]
                if (enable !is Boolean && enable != "true" && enable != "false") {
                    return ValidationResult.Invalid("Tool '$tool' requires boolean argument 'enable'.")
                }
            }

            "media_control" -> {
                val command = action.arguments["action"]?.toString()?.lowercase()
                val validCommands = setOf("play", "pause", "next", "previous", "stop")
                if (command == null || command !in validCommands) {
                    return ValidationResult.Invalid("Tool 'media_control' action must be one of $validCommands.")
                }
            }

            "open_app" -> {
                val app = action.arguments["app_name"]?.toString() ?: action.arguments["package_name"]?.toString()
                if (app.isNullOrBlank()) {
                    return ValidationResult.Invalid("Tool 'open_app' requires 'app_name' or 'package_name'.")
                }
            }

            "make_call" -> {
                val phone = action.arguments["phone_number"]?.toString()
                if (phone.isNullOrBlank()) {
                    return ValidationResult.Invalid("Tool 'make_call' requires 'phone_number'.")
                }
                return ValidationResult.ValidWithConfirmation("Initiating phone call to $phone requires confirmation.")
            }

            "send_whatsapp" -> {
                val recipient = action.arguments["recipient"]?.toString()
                if (recipient.isNullOrBlank()) {
                    return ValidationResult.Invalid("Tool 'send_whatsapp' requires 'recipient'.")
                }
            }

            "read_notifications" -> {
                val limitRaw = action.arguments["limit"]
                val limit = (limitRaw as? Number)?.toInt() ?: 10
                if (limit !in 1..50) {
                    return ValidationResult.Invalid("Tool 'read_notifications' limit must be between 1 and 50.")
                }
            }
        }

        if (action.tool in SENSITIVE_TOOLS || action.confirmationRequired) {
            return ValidationResult.ValidWithConfirmation("Sensitive tool execution requested.")
        }

        return ValidationResult.Valid
    }
}
