package com.jarvis.impl

import android.content.Context
import android.speech.tts.TextToSpeech
import com.jarvis.engines.TextToSpeechEngine
import java.util.Locale

class AndroidTextToSpeechEngine(context: Context) : TextToSpeechEngine {
    private var ready: Boolean = false
    private var languageApplied: Boolean = false
    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    override fun speak(text: String) {
        if (ready) {
            if (!languageApplied) {
                tts.language = Locale.getDefault()
                languageApplied = true
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-utterance")
        }
    }

    override fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
