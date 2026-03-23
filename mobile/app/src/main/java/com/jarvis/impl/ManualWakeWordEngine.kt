package com.jarvis.impl

import com.jarvis.engines.WakeWordEngine

class ManualWakeWordEngine : WakeWordEngine {
    private var active: Boolean = false
    private var callback: (() -> Unit)? = null

    override fun startListening(onWake: () -> Unit) {
        active = true
        callback = onWake
    }

    override fun stopListening() {
        active = false
        callback = null
    }

    override fun isListening(): Boolean = active

    fun triggerWake() {
        if (active) {
            callback?.invoke()
        }
    }
}
