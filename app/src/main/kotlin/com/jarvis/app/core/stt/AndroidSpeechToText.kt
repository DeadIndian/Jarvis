package com.jarvis.app.core.stt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArraySet

interface SpeechListener {
    fun onResults(text: String)
    fun onError(message: String)
    fun onEndOfSpeech()
}

class AndroidSpeechToText(
    private val context: Context
) : RecognitionListener {

    private val appContext = context.applicationContext
    private var _recognizer: SpeechRecognizer? = null
    private val listeners = CopyOnWriteArraySet<SpeechListener>()

    // ponytail: guards double-start. Rapid taps call startListening() while the
    // recognizer is still active, which fires ERROR_CLIENT ("Client side error").
    @Volatile private var isListening = false

    private val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    fun startListening() {
        if (!hasPermission) {
            listeners.forEach { it.onError("Microphone permission not granted") }
            return
        }

        if (isListening) return  // already active; ignore rapid re-taps

        ensureRecognizer()
        val intent = RecognizerIntent.ACTION_RECOGNIZE_SPEECH.let {
            Intent(it).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        }
        isListening = true
        _recognizer?.startListening(intent)
    }

    fun stopListening() {
        _recognizer?.stopListening()
    }

    fun destroy() {
        isListening = false
        _recognizer?.destroy()
        _recognizer = null
    }

    fun addListener(listener: SpeechListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: SpeechListener) {
        listeners.remove(listener)
    }

    private fun ensureRecognizer() {
        if (_recognizer == null) {
            _recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            _recognizer?.setRecognitionListener(this)
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()
        if (!text.isNullOrBlank()) {
            listeners.forEach {
                it.onResults(text)
                it.onEndOfSpeech()
            }
        }
    }

    override fun onError(errorCode: Int) {
        isListening = false
        val message = when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            3 -> "Insufficient audio"       // ERROR_INSUFFICIENT_AUDIO
            4 -> "Missing parts"            // ERROR_MISSING_PARTS
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            6 -> "Network unavailable"      // ERROR_NETWORK_SERVER_UNAVAILABLE
            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Recognition error ($errorCode)"
        }
        listeners.forEach { it.onError(message) }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
