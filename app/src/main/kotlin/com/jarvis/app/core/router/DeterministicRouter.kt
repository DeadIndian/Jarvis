package com.jarvis.app.core.router

import com.jarvis.app.core.contracts.ActionEvent

sealed class RoutingResult {
    data class LocalTool(val action: ActionEvent.PhoneToolAction) : RoutingResult()
    object Ambiguous : RoutingResult()
    object ServerHandoff : RoutingResult()
}

/**
 * DeterministicRouter matches user transcripts against pre-defined rules, regex patterns,
 * and slot extraction logic for zero-latency local tool dispatch.
 */
object DeterministicRouter {

    fun route(transcript: String): RoutingResult {
        val text = transcript.trim().lowercase()
        if (text.isEmpty()) return RoutingResult.Ambiguous

        // 1. Volume Control
        val volumeMatch = Regex("""(?:set|change|turn)?\s*volume\s*(?:to)?\s*(\d+)%?""").find(text)
            ?: Regex("""(?:set|change)?\s*volume\s*(\d+)""").find(text)
        if (volumeMatch != null) {
            val level = volumeMatch.groupValues[1].toIntOrNull() ?: 50
            return RoutingResult.LocalTool(
                ActionEvent.PhoneToolAction(
                    tool = "set_volume",
                    arguments = mapOf("level" to level.coerceIn(0, 100))
                )
            )
        }

        if (text.contains("mute volume") || text.contains("mute audio") || text == "mute") {
            return RoutingResult.LocalTool(
                ActionEvent.PhoneToolAction(
                    tool = "set_volume",
                    arguments = mapOf("level" to 0)
                )
            )
        }

        // 2. Bluetooth Toggle
        if (text.contains("turn on bluetooth") || text.contains("enable bluetooth")) {
            return RoutingResult.LocalTool(
                ActionEvent.PhoneToolAction(tool = "toggle_bluetooth", arguments = mapOf("enable" to true))
            )
        }
        if (text.contains("turn off bluetooth") || text.contains("disable bluetooth")) {
            return RoutingResult.LocalTool(
                ActionEvent.PhoneToolAction(tool = "toggle_bluetooth", arguments = mapOf("enable" to false))
            )
        }

        // 3. Wi-Fi Toggle
        if (text.contains("turn on wifi") || text.contains("turn on wi-fi") || text.contains("enable wifi")) {
            return RoutingResult.LocalTool(
                ActionEvent.PhoneToolAction(tool = "toggle_wifi", arguments = mapOf("enable" to true))
            )
        }
        if (text.contains("turn off wifi") || text.contains("turn off wi-fi") || text.contains("disable wifi")) {
            return RoutingResult.LocalTool(
                ActionEvent.PhoneToolAction(tool = "toggle_wifi", arguments = mapOf("enable" to false))
            )
        }

        // 4. Media Control
        if (text.contains("pause the music") || text.contains("pause music") || text == "pause") {
            return RoutingResult.LocalTool(
                ActionEvent.PhoneToolAction(tool = "media_control", arguments = mapOf("action" to "pause"))
            )
        }
        if (text.contains("play music") || text.contains("resume music") || text == "play" || text == "resume") {
            return RoutingResult.LocalTool(
                ActionEvent.PhoneToolAction(tool = "media_control", arguments = mapOf("action" to "play"))
            )
        }
        if (text.contains("next song") || text.contains("next track") || text == "next") {
            return RoutingResult.LocalTool(
                ActionEvent.PhoneToolAction(tool = "media_control", arguments = mapOf("action" to "next"))
            )
        }
        if (text.contains("previous song") || text.contains("previous track") || text == "previous") {
            return RoutingResult.LocalTool(
                ActionEvent.PhoneToolAction(tool = "media_control", arguments = mapOf("action" to "previous"))
            )
        }

        // 5. Open App
        val openAppMatch = Regex("""(?:open|launch|start)\s+([a-zA-Z0-9\s]+)""").find(text)
        if (openAppMatch != null) {
            val appName = openAppMatch.groupValues[1].trim()
            if (appName.isNotEmpty() && !appName.contains("my notes") && !appName.contains("browser")) {
                return RoutingResult.LocalTool(
                    ActionEvent.PhoneToolAction(tool = "open_app", arguments = mapOf("app_name" to appName))
                )
            }
        }

        // 6. Contact Calls
        val callMatch = Regex("""(?:call|dial|phone)\s+([a-zA-Z0-9\s]+)""").find(text)
        if (callMatch != null) {
            val contact = callMatch.groupValues[1].trim()
            if (contact.isNotEmpty()) {
                return RoutingResult.LocalTool(
                    ActionEvent.PhoneToolAction(
                        tool = "make_call",
                        arguments = mapOf("phone_number" to contact),
                        confirmationRequired = true
                    )
                )
            }
        }

        // 6.5 WhatsApp message: "whatsapp <recipient> [saying/that <message>]"
        //     e.g. "whatsapp mom saying running late", "message John on whatsapp hello"
        val waMatch = Regex(
            """(?:whatsapp|message|text)\s+(.+?)\s+(?:on\s+whatsapp\s+)?(?:saying|that|:)\s+(.+)"""
        ).find(text)
        if (waMatch != null && text.contains("whatsapp")) {
            val recipient = waMatch.groupValues[1].replace("on whatsapp", "").trim()
            val message = waMatch.groupValues[2].trim()
            if (recipient.isNotEmpty()) {
                return RoutingResult.LocalTool(
                    ActionEvent.PhoneToolAction(
                        tool = "send_whatsapp",
                        arguments = mapOf("recipient" to recipient, "message" to message),
                        confirmationRequired = true
                    )
                )
            }
        }

        // 7. Read Notifications
        if (text.contains("read my notifications") || text.contains("read notifications") || text == "notifications") {
            return RoutingResult.LocalTool(
                ActionEvent.PhoneToolAction(tool = "read_notifications", arguments = mapOf("limit" to 10))
            )
        }

        // 7.5 Flashlight Toggle
        if (text.contains("turn on flashlight") || text.contains("turn on torch") ||
            text.contains("enable flashlight") || text == "flashlight on") {
            return RoutingResult.LocalTool(
                ActionEvent.PhoneToolAction(tool = "toggle_flashlight", arguments = mapOf("enable" to true))
            )
        }
        if (text.contains("turn off flashlight") || text.contains("turn off torch") ||
            text.contains("disable flashlight") || text == "flashlight off") {
            return RoutingResult.LocalTool(
                ActionEvent.PhoneToolAction(tool = "toggle_flashlight", arguments = mapOf("enable" to false))
            )
        }

        // 8. Explicit Complex Server Query Patterns
        val serverKeywords = setOf(
            "summarize", "explain", "research", "compare", "plan my", "portfolio",
            "why did", "how do i", "write", "code", "repo", "repository", "project"
        )
        if (serverKeywords.any { text.contains(it) }) {
            return RoutingResult.ServerHandoff
        }

        // Fallback for unrecognized phrases
        return RoutingResult.Ambiguous
    }
}
