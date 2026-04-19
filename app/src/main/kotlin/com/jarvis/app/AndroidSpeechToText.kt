package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jarvis.input.STT
import com.jarvis.logging.JarvisLogger

class AndroidSpeechToText(
    context: Context,
    private val logger: JarvisLogger,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit
) : STT {
    private val appContext = context.applicationContext
    private val recognizer: SpeechRecognizer? = if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
        SpeechRecognizer.createSpeechRecognizer(appContext)
    } else {
        null
    }
    private var listening: Boolean = false

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                logger.info("stt", "Speech recognizer ready")
            }

            override fun onBeginningOfSpeech() {
                logger.info("stt", "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                listening = false
                logger.info("stt", "Speech ended")
            }

            override fun onError(error: Int) {
                listening = false
                val message = speechError(error)
                logger.warn("stt", "Speech recognition error", mapOf("code" to error, "error" to message))
                onError(message)
            }

            override fun onResults(results: Bundle?) {
                val text = extractText(results)
                listening = false
                if (text.isNotBlank()) {
                    onFinalResult(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = extractText(partialResults)
                if (text.isNotBlank()) {
                    onPartialResult(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    override fun startListening() {
        if (listening) {
            return
        }
        val localRecognizer = recognizer
        if (localRecognizer == null) {
            onError("Speech recognition is not available on this device")
            return
        }

        listening = true
        localRecognizer.startListening(recognizerIntent())
    }

    override fun stopListening() {
        if (!listening) {
            return
        }
        listening = false
        recognizer?.stopListening()
    }

    fun release() {
        recognizer?.cancel()
        recognizer?.destroy()
    }

    private fun recognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
    }

    private fun extractText(results: Bundle?): String {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return matches?.firstOrNull().orEmpty().trim()
    }

    private fun speechError(code: Int): String {
        return when (code) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side recognition error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
            SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout during recognition"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
            SpeechRecognizer.ERROR_SERVER -> "Recognition service error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected before timeout"
            else -> "Unknown speech recognition error"
        }
    }
}