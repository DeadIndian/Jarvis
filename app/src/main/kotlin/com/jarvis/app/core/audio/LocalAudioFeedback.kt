package com.jarvis.app.core.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * LocalAudioFeedback generates immediate audio cues ("ding") locally when a trigger fires.
 */
class LocalAudioFeedback {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator for audio feedback", e)
        }
    }

    /**
     * Plays immediate local activation sound ("ding").
     * Uses ToneGenerator to guarantee < 250ms latency without I/O overhead.
     */
    fun playActivationDing() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing activation ding tone", e)
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing tone generator", e)
        }
    }

    companion object {
        private const val TAG = "LocalAudioFeedback"
    }
}
