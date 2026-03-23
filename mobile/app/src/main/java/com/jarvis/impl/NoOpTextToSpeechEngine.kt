package com.jarvis.impl

import com.jarvis.engines.TextToSpeechEngine

class NoOpTextToSpeechEngine : TextToSpeechEngine {
    private var lastUtterance: String = ""

    override fun speak(text: String) {
        lastUtterance = text
    }

    override fun stop() {
        lastUtterance = ""
    }
}
