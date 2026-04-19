package com.jarvis.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class JarvisRuntimeService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                START_NOT_STICKY
            }

            else -> {
                val stateName = intent?.getStringExtra(EXTRA_STATE_NAME).orEmpty().ifBlank { "ACTIVE" }
                val notification = buildNotification(stateName)
                startForeground(NOTIFICATION_ID, notification)
                START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.runtime_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.runtime_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(stateName: String): Notification {
        val title = getString(R.string.runtime_notification_title)
        val text = getString(R.string.runtime_notification_text, stateName)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "jarvis_runtime"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_STATE_NAME = "state_name"
        private const val ACTION_START = "com.jarvis.app.runtime.START"
        private const val ACTION_STOP = "com.jarvis.app.runtime.STOP"

        fun start(context: Context, stateName: String) {
            val intent = Intent(context, JarvisRuntimeService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STATE_NAME, stateName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisRuntimeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}