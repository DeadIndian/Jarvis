package com.jarvis.impl

import com.jarvis.engines.SpeechToTextEngine

class PassThroughSpeechToTextEngine : SpeechToTextEngine {
    override fun transcribe(rawInput: String): String = rawInput.trim()

    override fun listenOnce(onResult: (String) -> Unit, onError: (String) -> Unit) {
        onResult("")
    }

    override fun stopListening() {
        // No-op for pass-through engine.
    }
}
