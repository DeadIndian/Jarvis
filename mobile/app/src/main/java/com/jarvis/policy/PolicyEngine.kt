package com.jarvis.policy

interface PolicyEngine {
    fun canActivateNow(): Boolean
    fun setMode(mode: AssistantMode)
    fun getMode(): AssistantMode
}

enum class AssistantMode {
    ACTIVE,
    WORK,
    SLEEP,
    MANUAL_OFF
}
