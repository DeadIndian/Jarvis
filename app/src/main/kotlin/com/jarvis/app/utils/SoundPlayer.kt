package com.jarvis.app.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.jarvis.app.R

class SoundPlayer(context: Context) {
    private val soundPool: SoundPool
    
    private var launchSoundId: Int = 0
    private var actionSoundId: Int = 0

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load sounds from raw resources
        launchSoundId = soundPool.load(context, R.raw.launch_sound, 1)
        actionSoundId = soundPool.load(context, R.raw.action_sound, 1)
    }

    fun playLaunchSound() {
        if (launchSoundId != 0) {
            soundPool.play(launchSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun playActionSound() {
        if (actionSoundId != 0) {
            soundPool.play(actionSoundId, 1f, 1f, 1, 0, 1f)
        }
    }
    
    fun release() {
        soundPool.release()
    }
}
