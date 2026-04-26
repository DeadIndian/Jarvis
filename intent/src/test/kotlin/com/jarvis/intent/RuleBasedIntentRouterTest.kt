package com.jarvis.intent

import kotlin.test.Test
import kotlin.test.assertEquals

class RuleBasedIntentRouterTest {
    private val router = RuleBasedIntentRouter()

    @Test
    fun parseMapsFlashlightCommandToSystemControlIntent() {
        val result = runBlockingIntent { router.parse("turn on flash light") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("flashlight", result.entities["target"])
        assertEquals("ON", result.entities["action"])
    }

    @Test
    fun parseMapsBluetoothTypoToSystemControlIntent() {
        val result = runBlockingIntent { router.parse("enable bloototh") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("bluetooth", result.entities["target"])
        assertEquals("ON", result.entities["action"])
    }

    @Test
    fun parseStillRoutesOpenAppCommands() {
        val result = runBlockingIntent { router.parse("open maps") }

        assertEquals("OPEN_APP", result.intent)
        assertEquals("maps", result.entities["app"])
    }

    @Test
    fun parseMapsVolumeCommandToSystemControlIntent() {
        val result = runBlockingIntent { router.parse("volume up") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("volume", result.entities["target"])
        assertEquals("UP", result.entities["action"])
    }

    @Test
    fun parseMapsBrightnessCommandToSystemControlIntent() {
        val result = runBlockingIntent { router.parse("increase brightness") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("brightness", result.entities["target"])
        assertEquals("UP", result.entities["action"])
    }

    @Test
    fun parseMapsDndCommandToSystemControlIntent() {
        val result = runBlockingIntent { router.parse("turn on do not disturb") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("dnd", result.entities["target"])
        assertEquals("ON", result.entities["action"])
    }

    @Test
    fun parseMapsLocationCommandToSystemControlIntent() {
        val result = runBlockingIntent { router.parse("turn off gps") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("location", result.entities["target"])
        assertEquals("OFF", result.entities["action"])
    }

    @Test
    fun parseMapsAirplaneModeCommandToSystemControlIntent() {
        val result = runBlockingIntent { router.parse("open airplane mode") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("airplane_mode", result.entities["target"])
    }

    @Test
    fun parseMapsOpenSettingsCommandToSystemControlIntent() {
        val result = runBlockingIntent { router.parse("open settings") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("settings", result.entities["target"])
        assertEquals("OPEN_SETTINGS", result.entities["action"])
    }

    @Test
    fun parseMapsSetAlarmWithTime() {
        val result = runBlockingIntent { router.parse("set alarm for 7:30 am") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("alarm", result.entities["target"])
        assertEquals("SET_ALARM", result.entities["action"])
        assertEquals("7", result.entities["hour"])
        assertEquals("30", result.entities["minute"])
    }

    @Test
    fun parseMapsCancelAlarm() {
        val result = runBlockingIntent { router.parse("cancel alarm") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("alarm", result.entities["target"])
        assertEquals("CANCEL_ALARM", result.entities["action"])
    }

    @Test
    fun parseMapsSetTimerWithDuration() {
        val result = runBlockingIntent { router.parse("set timer for 1 hour 30 minutes") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("timer", result.entities["target"])
        assertEquals("START_TIMER", result.entities["action"])
        assertEquals((60 * 60 + 30 * 60).toString(), result.entities["lengthSeconds"])
    }

    private fun runBlockingIntent(block: suspend () -> IntentResult): IntentResult {
        return kotlinx.coroutines.runBlocking { block() }
    }
}