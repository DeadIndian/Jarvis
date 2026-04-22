package com.jarvis.skills

import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class CurrentTimeSkill(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val locale: Locale = Locale.getDefault()
) : Skill {
    override val name: String = "CurrentTime"
    override val description: String = "Tells the current local time."

    override suspend fun execute(input: Map<String, String>): String {
        val now = ZonedDateTime.now(clock)
        val time = now.format(DateTimeFormatter.ofPattern("h:mm a", locale))
        val zone = now.zone.getDisplayName(TextStyle.SHORT, locale)
        return "It is $time $zone."
    }
}
