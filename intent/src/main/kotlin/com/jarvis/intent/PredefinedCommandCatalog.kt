package com.jarvis.intent

data class CommandExample(
    val phrase: String,
    val note: String
)

data class CommandSection(
    val title: String,
    val description: String,
    val examples: List<CommandExample>
)

object PredefinedCommandCatalog {
    val helpExamples = listOf(
        CommandExample("help", "Open the in-app help center."),
        CommandExample("tell me what you can do", "Show the full command catalog."),
        CommandExample("show help", "Jump straight to the Help tab."),
        CommandExample("what can you do", "See the built-in actions Jarvis supports.")
    )

    val appLaunchExamples = listOf(
        CommandExample("open WhatsApp", "Launch an installed app by name."),
        CommandExample("open Maps", "Open navigation or utility apps."),
        CommandExample("open Camera", "Start a common system app quickly.")
    )

    val timeExamples = listOf(
        CommandExample("what is the time", "Hear the current local time."),
        CommandExample("time now", "Quick time check without extra wording."),
        CommandExample("tell me the time", "Natural phrasing also works.")
    )

    val systemControlSections = listOf(
        CommandSection(
            title = "System Settings",
            description = "Open settings panels or trigger direct system actions with richer arguments.",
            examples = listOf(
                CommandExample("turn on flashlight", "Toggle the torch when supported."),
                CommandExample("enable bluetooth", "Open Bluetooth controls."),
                CommandExample("turn off hotspot", "Open tethering or hotspot settings."),
                CommandExample("open wifi settings", "Open internet or Wi-Fi controls."),
                CommandExample("set volume to 3 out of 10", "Set an exact media volume target."),
                CommandExample("turn the volume down to 10%", "Use percentages for quieter playback."),
                CommandExample("set brightness to 65%", "Set an exact screen brightness level."),
                CommandExample("set brightness to max", "Use semantic levels like max, min, or half."),
                CommandExample("turn on do not disturb", "Open focus or DND settings."),
                CommandExample("turn off gps", "Open location settings."),
                CommandExample("open airplane mode", "Open airplane mode settings."),
                CommandExample("open settings", "Open the main Android settings app.")
            )
        ),
        CommandSection(
            title = "Alarms And Timers",
            description = "Create or manage clock actions with natural phrases, labels, and repeat patterns.",
            examples = listOf(
                CommandExample("set alarm for 7:30 am", "Create an alarm directly."),
                CommandExample("set alarm every weekday for 6:45 am called gym", "Create a recurring labeled alarm."),
                CommandExample("cancel alarm", "Dismiss the next matching alarm."),
                CommandExample("set timer for 10 minutes", "Create a countdown timer."),
                CommandExample("set timer for 1h 20m 5s named pasta", "Use shorthand durations and a custom label."),
                CommandExample("cancel timer", "Open timers so you can stop the current one.")
            )
        )
    )

    val allSections: List<CommandSection> = listOf(
        CommandSection(
            title = "Help",
            description = "Open this help center and review the built-in rules.",
            examples = helpExamples
        ),
        CommandSection(
            title = "Open Apps",
            description = "Launch installed apps with short commands.",
            examples = appLaunchExamples
        ),
        CommandSection(
            title = "Current Time",
            description = "Ask for the local time with direct or natural phrasing.",
            examples = timeExamples
        )
    ) + systemControlSections

    val helpIntentPhrases: List<String> = helpExamples.map { it.phrase.lowercase() }
}
