package com.jarvis.intent

class RuleBasedIntentRouter : IntentRouter {
    override suspend fun parse(input: String): IntentResult {
        val normalized = input.lowercase().trim()
        val alarmTimer = parseAlarmTimer(normalized)
        if (alarmTimer != null) {
            return alarmTimer
        }

        val systemControl = parseSystemControl(normalized)
        if (systemControl != null) {
            return systemControl
        }

        return when {
            normalized.contains("open ") -> {
                val app = input.substringAfter("open ", missingDelimiterValue = "").trim()
                IntentResult(
                    intent = "OPEN_APP",
                    confidence = 0.95,
                    entities = mapOf("app" to app.ifBlank { "unknown" })
                )
            }

            else -> IntentResult(
                intent = "UNKNOWN",
                confidence = 0.3,
                entities = emptyMap()
            )
        }
    }

    private fun parseAlarmTimer(normalized: String): IntentResult? {
        if (normalized.contains("alarm")) {
            val entities = linkedMapOf<String, String>("target" to "alarm")
            val action = when {
                CANCEL_TOKENS.any { normalized.contains(it) } -> "CANCEL_ALARM"
                normalized.contains("open") && normalized.contains("alarm") -> "OPEN_ALARM"
                else -> "SET_ALARM"
            }
            entities["action"] = action

            val time = parseClockTime(normalized)
            if (time != null) {
                entities["hour"] = time.first.toString()
                entities["minute"] = time.second.toString()
            }

            entities["label"] = "Jarvis Alarm"
            return IntentResult(
                intent = "SYSTEM_CONTROL",
                confidence = 0.96,
                entities = entities
            )
        }

        if (normalized.contains("timer")) {
            val entities = linkedMapOf<String, String>("target" to "timer")
            val action = when {
                CANCEL_TOKENS.any { normalized.contains(it) } -> "CANCEL_TIMER"
                normalized.contains("open") && normalized.contains("timer") -> "OPEN_TIMER"
                else -> "START_TIMER"
            }
            entities["action"] = action

            val seconds = parseDurationSeconds(normalized)
            if (seconds > 0) {
                entities["lengthSeconds"] = seconds.toString()
            }

            entities["label"] = "Jarvis Timer"
            return IntentResult(
                intent = "SYSTEM_CONTROL",
                confidence = 0.96,
                entities = entities
            )
        }

        return null
    }

    private fun parseClockTime(normalized: String): Pair<Int, Int>? {
        val match = CLOCK_TIME_REGEX.find(normalized) ?: return null
        val rawHour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val amPm = match.groupValues[3]

        if (minute !in 0..59) {
            return null
        }

        val hour24 = when (amPm) {
            "am" -> if (rawHour == 12) 0 else rawHour
            "pm" -> if (rawHour == 12) 12 else rawHour + 12
            else -> rawHour
        }

        if (hour24 !in 0..23) {
            return null
        }

        return hour24 to minute
    }

    private fun parseDurationSeconds(normalized: String): Int {
        var totalSeconds = 0
        DURATION_REGEX.findAll(normalized).forEach { match ->
            val amount = match.groupValues[1].toIntOrNull() ?: return@forEach
            val unit = match.groupValues[2]
            val seconds = when {
                unit.startsWith("hour") || unit == "hr" || unit == "hrs" -> amount * 3600
                unit.startsWith("min") -> amount * 60
                unit.startsWith("sec") -> amount
                else -> 0
            }
            totalSeconds += seconds
        }
        return totalSeconds
    }

    private fun parseSystemControl(normalized: String): IntentResult? {
        val target = when {
            FLASHLIGHT_TOKENS.any { normalized.contains(it) } -> "flashlight"
            HOTSPOT_TOKENS.any { normalized.contains(it) } -> "hotspot"
            BLUETOOTH_TOKENS.any { normalized.contains(it) } -> "bluetooth"
            WIFI_TOKENS.any { normalized.contains(it) } -> "wifi"
            VOLUME_TOKENS.any { normalized.contains(it) } -> "volume"
            BRIGHTNESS_TOKENS.any { normalized.contains(it) } -> "brightness"
            DND_TOKENS.any { normalized.contains(it) } -> "dnd"
            MOBILE_DATA_TOKENS.any { normalized.contains(it) } -> "mobile_data"
            LOCATION_TOKENS.any { normalized.contains(it) } -> "location"
            AIRPLANE_TOKENS.any { normalized.contains(it) } -> "airplane_mode"
            SETTINGS_TOKENS.any { normalized.contains(it) } -> "settings"
            else -> null
        } ?: return null

        val action = when {
            UP_TOKENS.any { normalized.contains(it) } -> "UP"
            DOWN_TOKENS.any { normalized.contains(it) } -> "DOWN"
            MUTE_TOKENS.any { normalized.contains(it) } -> "MUTE"
            UNMUTE_TOKENS.any { normalized.contains(it) } -> "UNMUTE"
            ON_TOKENS.any { normalized.contains(it) } -> "ON"
            OFF_TOKENS.any { normalized.contains(it) } -> "OFF"
            TOGGLE_TOKENS.any { normalized.contains(it) } -> "TOGGLE"
            normalized.contains("setting") || normalized.startsWith("open ") -> "OPEN_SETTINGS"
            target == "flashlight" -> "TOGGLE"
            else -> "OPEN_SETTINGS"
        }

        return IntentResult(
            intent = "SYSTEM_CONTROL",
            confidence = 0.95,
            entities = mapOf(
                "target" to target,
                "action" to action
            )
        )
    }

    companion object {
        private val FLASHLIGHT_TOKENS = listOf("flashlight", "flash light", "torch")
        private val HOTSPOT_TOKENS = listOf("hotspot", "hot spot", "tether", "tethering")
        private val BLUETOOTH_TOKENS = listOf("bluetooth", "blue tooth", "bloototh")
        private val WIFI_TOKENS = listOf("wi-fi", "wifi", "internet")
        private val VOLUME_TOKENS = listOf("volume", "sound", "ringer", "silent mode")
        private val BRIGHTNESS_TOKENS = listOf("brightness", "screen light", "screen brightness")
        private val DND_TOKENS = listOf("do not disturb", "dnd", "focus mode")
        private val MOBILE_DATA_TOKENS = listOf("mobile data", "cellular data", "data roaming")
        private val LOCATION_TOKENS = listOf("location", "gps")
        private val AIRPLANE_TOKENS = listOf("airplane mode", "flight mode")
        private val SETTINGS_TOKENS = listOf("settings", "setting")

        private val ON_TOKENS = listOf("turn on", "enable", "start", "on")
        private val OFF_TOKENS = listOf("turn off", "disable", "stop", "off")
        private val TOGGLE_TOKENS = listOf("toggle", "switch")
        private val UP_TOKENS = listOf("volume up", "increase volume", "raise volume", "brighter", "increase brightness")
        private val DOWN_TOKENS = listOf("volume down", "decrease volume", "lower volume", "dim", "decrease brightness")
        private val MUTE_TOKENS = listOf("mute", "silent")
        private val UNMUTE_TOKENS = listOf("unmute")
        private val CANCEL_TOKENS = listOf("cancel", "remove", "delete", "dismiss", "stop")

        private val CLOCK_TIME_REGEX = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b")
        private val DURATION_REGEX = Regex("\\b(\\d+)\\s*(hours?|hr|hrs|minutes?|mins?|seconds?|secs?)\\b")
    }
}
