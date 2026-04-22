package com.jarvis.skills

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class CurrentTimeSkillTest {
    @Test
    fun executeReturnsFormattedTimeWithZone() {
        val fixedClock = Clock.fixed(Instant.parse("2026-04-22T13:05:00Z"), ZoneId.of("UTC"))
        val skill = CurrentTimeSkill(clock = fixedClock, locale = Locale.US)

        runTest {
            assertEquals("It is 1:05 PM UTC.", skill.execute(emptyMap()))
        }
    }
}
