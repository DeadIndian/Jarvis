package com.jarvis.app.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * LocalTtsEngine provides a local neural/system text-to-speech backend
 * with streaming chunk support and immediate barge-in interruption capability.
 */
class LocalTtsEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false
    private var onDoneCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Default language US is not supported for TTS")
            } else {
                isInitialized = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        onDoneCallback?.invoke()
                        onDoneCallback = null
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        onDoneCallback?.invoke()
                        onDoneCallback = null
                    }
                })
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status $status")
        }
    }

    /**
     * Speaks the given text locally.
     * @param text The text to speak.
     * @param queueMode QUEUE_FLUSH (default, stops ongoing speech) or QUEUE_ADD.
     * @param onDone Called when speech finishes (async).
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH, onDone: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not yet initialized, speech delayed")
            return
        }
        onDoneCallback = onDone
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, queueMode, null, utteranceId)
    }

    /**
     * Immediately interrupts ongoing speech (barge-in support).
     */
    fun stopSpeaking() {
        if (isSpeaking || isInitialized) {
            tts?.stop()
            isSpeaking = false
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
        isSpeaking = false
        onDoneCallback = null
    }

    companion object {
        private const val TAG = "LocalTtsEngine"
    }
}
