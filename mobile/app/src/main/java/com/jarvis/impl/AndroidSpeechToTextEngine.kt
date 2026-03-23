package com.jarvis.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jarvis.engines.SpeechToTextEngine
import java.util.Locale

class AndroidSpeechToTextEngine(private val context: Context) : SpeechToTextEngine {
    private val recognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private var callbackUsed: Boolean = false

    override fun transcribe(rawInput: String): String = rawInput.trim()

    override fun listenOnce(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition service not available on this device")
            return
        }

        callbackUsed = false

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                if (!callbackUsed) {
                    callbackUsed = true
                    onError("Speech recognition error: $error")
                }
            }

            override fun onResults(results: Bundle?) {
                if (callbackUsed) {
                    return
                }
                callbackUsed = true
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                if (text.isBlank()) {
                    onError("No speech detected")
                } else {
                    onResult(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        recognizer.startListening(intent)
    }

    override fun stopListening() {
        recognizer.cancel()
    }

    fun destroy() {
        recognizer.destroy()
    }
}
