package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jarvis.input.WakeWordEngine
import com.jarvis.logging.JarvisLogger
import java.util.Locale

/**
 * Open-source wake-word implementation using on-device SpeechRecognizer keyword spotting.
 * It continuously listens and triggers when the configured keyword is detected.
 */
class OpenWakeWordEngine(
    context: Context,
    private val logger: JarvisLogger,
    private val onWakeWordDetected: (String) -> Unit,
    private val onError: (String) -> Unit
) : WakeWordEngine {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer: SpeechRecognizer? = if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
        SpeechRecognizer.createSpeechRecognizer(appContext)
    } else {
        null
    }

    private var activeKeyword: String = "jarvis"
    private var running = false
    private var listening = false

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                logger.info("wakeword", "Wake-word recognizer ready")
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                listening = false
                scheduleRestart()
            }

            override fun onError(error: Int) {
                listening = false
                val message = speechError(error)
                logger.warn("wakeword", "Wake-word recognition error", mapOf("code" to error, "error" to message))
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    onError(message)
                }
                scheduleRestart()
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = extractText(results)
                handleTranscript(text)
                if (running) {
                    scheduleRestart()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = extractText(partialResults)
                handleTranscript(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    override fun start(keyword: String) {
        activeKeyword = keyword.trim().ifBlank { "jarvis" }
        if (running) {
            return
        }
        if (recognizer == null) {
            onError("Wake-word recognition is not available on this device")
            return
        }
        running = true
        logger.info("wakeword", "Wake-word engine started", mapOf("keyword" to activeKeyword))
        scheduleRestart(immediate = true)
    }

    override fun stop() {
        if (!running) {
            return
        }
        running = false
        listening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        logger.info("wakeword", "Wake-word engine stopped")
    }

    fun release() {
        stop()
        recognizer?.destroy()
    }

    private fun scheduleRestart(immediate: Boolean = false) {
        if (!running || recognizer == null) {
            return
        }
        handler.removeCallbacksAndMessages(null)
        val runnable = Runnable {
            if (!running || listening) {
                return@Runnable
            }
            try {
                listening = true
                recognizer.startListening(recognizerIntent())
            } catch (t: Throwable) {
                listening = false
                logger.warn("wakeword", "Failed to restart wake-word listener", mapOf("error" to (t.message ?: "unknown")))
                onError("Wake-word listener restart failed")
            }
        }
        if (immediate) {
            handler.post(runnable)
        } else {
            handler.postDelayed(runnable, 250L)
        }
    }

    private fun handleTranscript(text: String) {
        if (!running || text.isBlank()) {
            return
        }
        val normalizedText = text.lowercase(Locale.US)
        val normalizedKeyword = activeKeyword.lowercase(Locale.US)
        if (normalizedText.contains(normalizedKeyword)) {
            logger.info("wakeword", "Wake-word detected", mapOf("keyword" to activeKeyword))
            running = false
            listening = false
            recognizer?.cancel()
            onWakeWordDetected(activeKeyword)
        }
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