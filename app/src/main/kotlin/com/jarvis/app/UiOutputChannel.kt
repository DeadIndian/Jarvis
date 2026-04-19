package com.jarvis.app

import com.jarvis.output.OutputChannel

class UiOutputChannel(
    private val onState: (String) -> Unit,
    private val onSpeak: (String) -> Unit
) : OutputChannel {
    override fun showListening() {
        onState("Listening")
    }

    override fun showThinking() {
        onState("Thinking")
    }

    override fun showSpeaking() {
        onState("Speaking")
    }

    override fun speak(text: String) {
        onSpeak(text)
    }
}
