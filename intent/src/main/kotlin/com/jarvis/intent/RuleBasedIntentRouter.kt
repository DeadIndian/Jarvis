package com.jarvis.intent

import java.util.Calendar

class RuleBasedIntentRouter : IntentRouter {
    override suspend fun parse(input: String): IntentResult {
        val normalized = input.lowercase().trim()
        val helpIntent = parseHelpIntent(normalized)
        if (helpIntent != null) {
            return helpIntent
        }

        val currentTime = parseCurrentTime(normalized)
        if (currentTime != null) {
            return currentTime
        }

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

    private fun parseHelpIntent(normalized: String): IntentResult? {
        val isDirectQuery = normalized == "help" || normalized == "jarvis help"
        val hasHelpPattern = PredefinedCommandCatalog.helpIntentPhrases.any { normalized.contains(it) }
        if (!isDirectQuery && !hasHelpPattern) {
            return null
        }

        return IntentResult(
            intent = "SHOW_HELP",
            confidence = 0.98,
            entities = emptyMap()
        )
    }

    private fun parseCurrentTime(normalized: String): IntentResult? {
        val isDirectQuery = normalized == "time" || normalized == "time?" || normalized == "time now"
        val hasTimePattern = TIME_QUERY_PATTERNS.any { normalized.contains(it) }
        if (!isDirectQuery && !hasTimePattern) {
            return null
        }

        return IntentResult(
            intent = "CurrentTime",
            confidence = 0.94,
            entities = emptyMap()
        )
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

            parseAlarmDays(normalized)?.let { days ->
                entities["days"] = days.joinToString(",")
            }
            entities["label"] = parseLabel(normalized, target = "alarm") ?: "Jarvis Alarm"
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

            entities["label"] = parseLabel(normalized, target = "timer") ?: "Jarvis Timer"
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
        DURATION_WORD_REGEX.findAll(normalized).forEach { match ->
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
        DURATION_SHORTHAND_REGEX.findAll(normalized).forEach { match ->
            val amount = match.groupValues[1].toIntOrNull() ?: return@forEach
            val unit = match.groupValues[2]
            val seconds = when (unit) {
                "h" -> amount * 3600
                "m" -> amount * 60
                "s" -> amount
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

        val levelPercent = if (target == "volume" || target == "brightness") {
            parseLevelPercent(normalized, target)
        } else {
            null
        }

        val action = when {
            levelPercent != null -> "SET_LEVEL"
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

        val entities = linkedMapOf(
            "target" to target,
            "action" to action
        )
        if (levelPercent != null) {
            entities["levelPercent"] = levelPercent.toString()
        }

        return IntentResult(
            intent = "SYSTEM_CONTROL",
            confidence = 0.95,
            entities = entities
        )
    }

    private fun parseLevelPercent(normalized: String, target: String): Int? {
        val semanticValue = when {
            MAX_LEVEL_TOKENS.any { normalized.contains(it) } -> 100
            MIN_LEVEL_TOKENS.any { normalized.contains(it) } -> 0
            HALF_LEVEL_TOKENS.any { normalized.contains(it) } -> 50
            target == "brightness" && normalized.contains("brightest") -> 100
            target == "brightness" && normalized.contains("darkest") -> 0
            else -> null
        }
        if (semanticValue != null) {
            return semanticValue
        }

        val outOfMatch = OUT_OF_LEVEL_REGEX.find(normalized)
        if (outOfMatch != null) {
            val current = outOfMatch.groupValues[1].toIntOrNull()
            val total = outOfMatch.groupValues[2].toIntOrNull()
            if (current != null && total != null && total > 0) {
                return ((current.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            }
        }

        val contextual = LEVEL_CONTEXT_REGEX.find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val percentMarked = LEVEL_PERCENT_REGEX.find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val rawValue = contextual ?: percentMarked ?: return null
        return rawValue.coerceIn(0, 100)
    }

    private fun parseLabel(normalized: String, target: String): String? {
        val candidate = LABEL_REGEX.find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('.', ',', '!', '?')
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val cleaned = candidate
            .removePrefix("the ")
            .removePrefix("a ")
            .removePrefix("an ")
            .trim()
        if (cleaned.isBlank() || cleaned == target) {
            return null
        }
        return cleaned
    }

    private fun parseAlarmDays(normalized: String): List<Int>? {
        if (normalized.contains("every day") || normalized.contains("daily")) {
            return FULL_WEEK
        }
        if (normalized.contains("weekday")) {
            return WEEKDAYS
        }
        if (normalized.contains("weekend")) {
            return WEEKENDS
        }
        if (!normalized.contains("every ")) {
            return null
        }

        val matches = DAY_NAME_REGEX.findAll(normalized)
            .mapNotNull { match -> DAY_NAME_TO_CALENDAR[match.value] }
            .distinct()
            .toList()
        return matches.ifEmpty { null }
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
        private val MAX_LEVEL_TOKENS = listOf("maximum", "max", "full", "highest")
        private val MIN_LEVEL_TOKENS = listOf("minimum", "min", "lowest", "zero")
        private val HALF_LEVEL_TOKENS = listOf("half", "50 50")
        private val TIME_QUERY_PATTERNS = listOf(
            "what time",
            "what's the time",
            "whats the time",
            "what is the time",
            "tell me the time",
            "current time",
            "time is it"
        )

        private val CLOCK_TIME_REGEX = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b")
        private val DURATION_WORD_REGEX = Regex("\\b(\\d+)\\s*(hours?|hr|hrs|minutes?|mins?|seconds?|secs?)\\b")
        private val DURATION_SHORTHAND_REGEX = Regex("\\b(\\d+)\\s*([hms])\\b")
        private val LEVEL_CONTEXT_REGEX = Regex("\\b(?:to|at)\\s*(\\d{1,3})(?:\\s*(?:%|percent))?\\b")
        private val LEVEL_PERCENT_REGEX = Regex("\\b(\\d{1,3})\\s*(?:%|percent)\\b")
        private val OUT_OF_LEVEL_REGEX = Regex("\\b(\\d{1,3})\\s*(?:out of|/)\\s*(\\d{1,3})\\b")
        private val LABEL_REGEX = Regex("\\b(?:called|named|label(?:ed)?(?: as)?)\\s+(.+)$")
        private val DAY_NAME_REGEX = Regex("\\b(monday|mon|tuesday|tue|tues|wednesday|wed|thursday|thu|thurs|friday|fri|saturday|sat|sunday|sun)\\b")
        private val DAY_NAME_TO_CALENDAR = mapOf(
            "monday" to Calendar.MONDAY,
            "mon" to Calendar.MONDAY,
            "tuesday" to Calendar.TUESDAY,
            "tue" to Calendar.TUESDAY,
            "tues" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY,
            "wed" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY,
            "thu" to Calendar.THURSDAY,
            "thurs" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY,
            "fri" to Calendar.FRIDAY,
            "saturday" to Calendar.SATURDAY,
            "sat" to Calendar.SATURDAY,
            "sunday" to Calendar.SUNDAY,
            "sun" to Calendar.SUNDAY
        )
        private val WEEKDAYS = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY
        )
        private val WEEKENDS = listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        private val FULL_WEEK = listOf(
            Calendar.SUNDAY,
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY
        )
    }
}
