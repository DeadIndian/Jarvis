package com.jarvis.app

import com.jarvis.intent.CommandExample
import com.jarvis.intent.CommandSection
import com.jarvis.intent.PredefinedCommandCatalog

data class HelpBlock(
    val title: String,
    val body: String,
    val examples: List<CommandExample> = emptyList()
)

object JarvisHelpCatalog {
    val overviewBlocks: List<HelpBlock> = listOf(
        HelpBlock(
            title = "How To Use Jarvis",
            body = "Use your wake word or assistant overlay to speak a command. Jarvis handles fast built-in actions locally, and this Help tab stays inside the app so new users can see exactly what is supported right now."
        ),
        HelpBlock(
            title = "Voice Tips",
            body = "Say \"Jarvis\" to wake the assistant when wake-word listening is active. If continuous voice mode is running, say \"thank you\" to end the live conversation and return to idle."
        ),
        HelpBlock(
            title = "Inside The App",
            body = "Home shows runtime status, Help explains the built-in rules, Logs exposes recent assistant activity, and Settings lets you refresh, download, select, or delete on-device models."
        )
    )

    val commandSections: List<CommandSection> = PredefinedCommandCatalog.allSections
}
