package com.jarvis.app.core.trigger

enum class TriggerType {
    PUSH_TO_TALK,
    SIDE_BUTTON,
    CUSTOM_WAKE_WORD,
    APP_ACTION,
    TEST_INJECTED
}

/**
 * TriggerSource abstracts any wake or activation input into the generic VoiceSession.
 */
interface TriggerSource {
    val type: TriggerType
    fun startListening(onTriggered: (TriggerType) -> Unit)
    fun stopListening()
}

/**
 * PushToTalkTrigger fires when the user taps or holds the PTT button in UI.
 */
class PushToTalkTrigger : TriggerSource {
    override val type: TriggerType = TriggerType.PUSH_TO_TALK
    private var listener: ((TriggerType) -> Unit)? = null

    override fun startListening(onTriggered: (TriggerType) -> Unit) {
        this.listener = onTriggered
    }

    override fun stopListening() {
        this.listener = null
    }

    fun triggerManually() {
        listener?.invoke(type)
    }
}

/**
 * SideButtonTrigger fires on hardware key events (e.g. side key double-click or long press).
 */
class SideButtonTrigger : TriggerSource {
    override val type: TriggerType = TriggerType.SIDE_BUTTON
    private var listener: ((TriggerType) -> Unit)? = null

    override fun startListening(onTriggered: (TriggerType) -> Unit) {
        this.listener = onTriggered
    }

    override fun stopListening() {
        this.listener = null
    }

    fun onHardwareKeyEvent() {
        listener?.invoke(type)
    }
}

/**
 * TestInjectedTrigger allows unit tests and test suites to trigger voice sessions programmatically.
 */
class TestInjectedTrigger : TriggerSource {
    override val type: TriggerType = TriggerType.TEST_INJECTED
    private var listener: ((TriggerType) -> Unit)? = null

    override fun startListening(onTriggered: (TriggerType) -> Unit) {
        this.listener = onTriggered
    }

    override fun stopListening() {
        this.listener = null
    }

    fun injectTrigger() {
        listener?.invoke(type)
    }
}

/**
 * DevTrigger is a development convenience trigger that injects a PerceptionEvent
 * for local dev/testing without requiring a wake word or hardware button.
 * It fires when triggerManually() is called, e.g. from a debug button in the UI.
 */
class DevTrigger : TriggerSource {
    override val type: TriggerType = TriggerType.TEST_INJECTED
    private var listener: ((TriggerType) -> Unit)? = null

    override fun startListening(onTriggered: (TriggerType) -> Unit) {
        this.listener = onTriggered
    }

    override fun stopListening() {
        this.listener = null
    }

    fun triggerManually() {
        listener?.invoke(type)
    }
}
