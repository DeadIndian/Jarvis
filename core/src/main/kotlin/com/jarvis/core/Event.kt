package com.jarvis.core

sealed class Event {
    data class VoiceInput(val text: String) : Event()
    data class TextInput(val text: String) : Event()
    data class WakeWordDetected(val keyword: String) : Event()
    data object PowerButtonHeld : Event()
    data class HousePartyToggle(val enabled: Boolean) : Event()
    data object TimeoutElapsed : Event()
    data class SkillResult(val skill: String, val result: String) : Event()
    data class Error(val message: String, val cause: Throwable? = null) : Event()
}
