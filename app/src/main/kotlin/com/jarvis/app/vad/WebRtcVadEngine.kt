package com.jarvis.app.vad

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.jarvis.logging.JarvisLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * JNI-backed WebRTC VAD engine scaffold.
 *
 * Note: native implementation is a stub. To enable real WebRTC VAD, add the
 * WebRTC VAD C sources and implement `webrtc_vad_jni.cpp` accordingly.
 */
class WebRtcVadEngine(
    private val context: Context,
    private val logger: JarvisLogger,
    private val onVoiceDetected: () -> Unit,
    private val aggressiveness: Int = 2
) : VadEngine {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        WebRtcVadNative.load()
        WebRtcVadNative.init(aggressiveness)
    }

    override fun start() {
        if (job != null) return
        job = scope.launch {
            try {
                val sampleRate = 16000
                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)

                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                val audioBuffer = ShortArray(bufferSize / 2)
                recorder.startRecording()
                logger.info("vad", "WebRtc VAD started (native stub)")

                while (job?.isActive == true) {
                    val read = recorder.read(audioBuffer, 0, audioBuffer.size)
                    if (read > 0) {
                        val speech = WebRtcVadNative.isSpeech(audioBuffer, read)
                        if (speech) {
                            logger.info("vad", "WebRtc VAD detected voice (native)")
                            onVoiceDetected()
                            kotlinx.coroutines.delay(800)
                        }
                    }
                }

                recorder.stop()
                recorder.release()
            } catch (t: Throwable) {
                logger.warn("vad", "WebRtc VAD failed", mapOf("error" to (t.message ?: "unknown")))
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        WebRtcVadNative.release()
        logger.info("vad", "WebRtc VAD stopped")
    }
}

object WebRtcVadNative {
    private var loaded = false

    fun load() {
        if (loaded) return
        try {
            System.loadLibrary("webrtc_vad_jni")
            loaded = true
        } catch (t: Throwable) {
            // library may not exist yet; fall back to stub behavior
            loaded = false
        }
    }

    external fun init(aggressiveness: Int)
    external fun isSpeech(pcm: ShortArray, length: Int): Boolean
    external fun release()
}
