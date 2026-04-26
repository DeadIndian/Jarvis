package com.jarvis.intent

import java.util.Calendar
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
    fun parseMapsWhatIsTheTimeToCurrentTimeIntent() {
        val result = runBlockingIntent { router.parse("what is the time") }

        assertEquals("CurrentTime", result.intent)
    }

    @Test
    fun parseMapsHelpPhraseToHelpIntent() {
        val result = runBlockingIntent { router.parse("tell me what you can do") }

        assertEquals("SHOW_HELP", result.intent)
    }

    @Test
    fun parseMapsVolumeCommandToSystemControlIntent() {
        val result = runBlockingIntent { router.parse("volume up") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("volume", result.entities["target"])
        assertEquals("UP", result.entities["action"])
    }

    @Test
    fun parseMapsExactVolumePercentCommand() {
        val result = runBlockingIntent { router.parse("turn the volume down to 10%") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("volume", result.entities["target"])
        assertEquals("SET_LEVEL", result.entities["action"])
        assertEquals("10", result.entities["levelPercent"])
    }

    @Test
    fun parseMapsVolumeOutOfScaleCommand() {
        val result = runBlockingIntent { router.parse("set volume to 3 out of 10") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("volume", result.entities["target"])
        assertEquals("SET_LEVEL", result.entities["action"])
        assertEquals("30", result.entities["levelPercent"])
    }

    @Test
    fun parseMapsBrightnessCommandToSystemControlIntent() {
        val result = runBlockingIntent { router.parse("increase brightness") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("brightness", result.entities["target"])
        assertEquals("UP", result.entities["action"])
    }

    @Test
    fun parseMapsExactBrightnessLevelCommand() {
        val result = runBlockingIntent { router.parse("set the brightness to 65") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("brightness", result.entities["target"])
        assertEquals("SET_LEVEL", result.entities["action"])
        assertEquals("65", result.entities["levelPercent"])
    }

    @Test
    fun parseMapsSemanticBrightnessLevelCommand() {
        val result = runBlockingIntent { router.parse("set brightness to max") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("brightness", result.entities["target"])
        assertEquals("SET_LEVEL", result.entities["action"])
        assertEquals("100", result.entities["levelPercent"])
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
    fun parseMapsRecurringAlarmWithLabel() {
        val result = runBlockingIntent { router.parse("set alarm every weekday for 6:45 am called gym") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("alarm", result.entities["target"])
        assertEquals("SET_ALARM", result.entities["action"])
        assertEquals("6", result.entities["hour"])
        assertEquals("45", result.entities["minute"])
        assertEquals(
            listOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY
            ).joinToString(","),
            result.entities["days"]
        )
        assertEquals("gym", result.entities["label"])
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

    @Test
    fun parseMapsTimerShorthandAndLabel() {
        val result = runBlockingIntent { router.parse("set timer for 1h 20m 5s named pasta") }

        assertEquals("SYSTEM_CONTROL", result.intent)
        assertEquals("timer", result.entities["target"])
        assertEquals("START_TIMER", result.entities["action"])
        assertEquals((60 * 60 + 20 * 60 + 5).toString(), result.entities["lengthSeconds"])
        assertEquals("pasta", result.entities["label"])
    }

    private fun runBlockingIntent(block: suspend () -> IntentResult): IntentResult {
        return kotlinx.coroutines.runBlocking { block() }
    }
}
