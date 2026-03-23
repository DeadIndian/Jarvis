package com.jarvis.impl

import com.jarvis.policy.AssistantMode
import com.jarvis.policy.PolicyEngine
import java.time.LocalTime

class DefaultPolicyEngine(
    private val sleepStartHour: Int = 23,
    private val sleepEndHour: Int = 7,
    private val workStartHour: Int = 9,
    private val workEndHour: Int = 18
) : PolicyEngine {
    private var mode: AssistantMode = AssistantMode.ACTIVE

    override fun canActivateNow(): Boolean {
        if (mode == AssistantMode.MANUAL_OFF) {
            return false
        }

        val hour = LocalTime.now().hour
        val inSleepWindow = hour >= sleepStartHour || hour < sleepEndHour
        val inWorkWindow = hour in workStartHour until workEndHour

        return when (mode) {
            AssistantMode.ACTIVE -> !inSleepWindow
            AssistantMode.SLEEP -> false
            AssistantMode.WORK -> !inWorkWindow
            AssistantMode.MANUAL_OFF -> false
        }
    }

    override fun setMode(mode: AssistantMode) {
        this.mode = mode
    }

    override fun getMode(): AssistantMode = mode
}
