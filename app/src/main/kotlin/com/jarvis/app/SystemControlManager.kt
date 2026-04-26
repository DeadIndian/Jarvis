package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings

class SystemControlManager(context: Context) {
    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var torchEnabled = false

    suspend fun execute(input: Map<String, String>): String {
        val target = input["target"].orEmpty()
        val action = input["action"].orEmpty()
        return when (target.lowercase()) {
            "flashlight" -> handleFlashlight(action)
            "bluetooth" -> openBluetoothControls(action)
            "hotspot" -> openHotspotControls(action)
            "wifi" -> openWifiControls(action)
            "volume" -> handleVolume(action)
            "brightness" -> openBrightnessControls(action)
            "dnd" -> openDndControls(action)
            "mobile_data" -> openMobileDataControls(action)
            "location" -> openLocationControls(action)
            "airplane_mode" -> openAirplaneModeControls(action)
            "settings" -> openGeneralSettings()
            "alarm" -> handleAlarm(action, input)
            "timer" -> handleTimer(action, input)
            else -> "I can't control $target yet"
        }
    }

    private fun handleFlashlight(action: String): String {
        val cameraId = findFlashCameraId() ?: return "This device does not have a flashlight"

        val desiredState = when (action.uppercase()) {
            "ON" -> true
            "OFF" -> false
            else -> !torchEnabled
        }

        return runCatching {
            cameraManager.setTorchMode(cameraId, desiredState)
            torchEnabled = desiredState
            if (desiredState) {
                "Flashlight turned on"
            } else {
                "Flashlight turned off"
            }
        }.getOrElse {
            "I couldn't change flashlight state. Please allow camera permission."
        }
    }

    private fun openBluetoothControls(action: String): String {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        launchSettings(intent)
        return when (action.uppercase()) {
            "ON" -> "Opened Bluetooth controls to turn it on"
            "OFF" -> "Opened Bluetooth controls to turn it off"
            else -> "Opened Bluetooth controls"
        }
    }

    private fun openHotspotControls(action: String): String {
        val tetherIntent = Intent("android.settings.TETHER_SETTINGS")
        val fallbackIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
        val chosen = if (canResolve(tetherIntent)) tetherIntent else fallbackIntent
        launchSettings(chosen)
        return when (action.uppercase()) {
            "ON" -> "Opened hotspot settings to turn it on"
            "OFF" -> "Opened hotspot settings to turn it off"
            else -> "Opened hotspot settings"
        }
    }

    private fun openWifiControls(action: String): String {
        val intent = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            else -> Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        launchSettings(intent)
        return when (action.uppercase()) {
            "ON" -> "Opened Wi-Fi controls to turn it on"
            "OFF" -> "Opened Wi-Fi controls to turn it off"
            else -> "Opened Wi-Fi controls"
        }
    }

    private fun handleVolume(action: String): String {
        return when (action.uppercase()) {
            "UP", "ON" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "Volume increased"
            }
            "DOWN", "OFF" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "Volume decreased"
            }
            "MUTE" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                } else {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                }
                "Volume muted"
            }
            "UNMUTE" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                } else {
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 0.4f).toInt().coerceAtLeast(1), AudioManager.FLAG_SHOW_UI)
                }
                "Volume unmuted"
            }
            else -> {
                launchSettings(Intent(Settings.ACTION_SOUND_SETTINGS))
                "Opened sound settings"
            }
        }
    }

    private fun openBrightnessControls(action: String): String {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
        launchSettings(intent)
        return when (action.uppercase()) {
            "UP", "ON" -> "Opened brightness controls to increase brightness"
            "DOWN", "OFF" -> "Opened brightness controls to decrease brightness"
            else -> "Opened brightness controls"
        }
    }

    private fun openDndControls(action: String): String {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        launchSettings(intent)
        return when (action.uppercase()) {
            "ON" -> "Opened Do Not Disturb controls to turn it on"
            "OFF" -> "Opened Do Not Disturb controls to turn it off"
            else -> "Opened Do Not Disturb controls"
        }
    }

    private fun openMobileDataControls(action: String): String {
        val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        }
        launchSettings(panelIntent)
        return when (action.uppercase()) {
            "ON" -> "Opened mobile data controls to turn it on"
            "OFF" -> "Opened mobile data controls to turn it off"
            else -> "Opened mobile data controls"
        }
    }

    private fun openLocationControls(action: String): String {
        launchSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        return when (action.uppercase()) {
            "ON" -> "Opened location settings to turn it on"
            "OFF" -> "Opened location settings to turn it off"
            else -> "Opened location settings"
        }
    }

    private fun openAirplaneModeControls(action: String): String {
        launchSettings(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
        return when (action.uppercase()) {
            "ON" -> "Opened airplane mode settings to turn it on"
            "OFF" -> "Opened airplane mode settings to turn it off"
            else -> "Opened airplane mode settings"
        }
    }

    private fun openGeneralSettings(): String {
        launchSettings(Intent(Settings.ACTION_SETTINGS))
        return "Opened settings"
    }

    private fun handleAlarm(action: String, input: Map<String, String>): String {
        return when (action.uppercase()) {
            "SET_ALARM" -> {
                val hour = input["hour"]?.toIntOrNull()
                val minute = input["minute"]?.toIntOrNull()
                if (hour == null || minute == null) {
                    launchSettings(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                    return "Opened alarms. Say: set alarm for 7:30 am"
                }

                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, input["label"] ?: "Jarvis Alarm")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
                launchSettings(intent)
                "Alarm set for ${formatTime(hour, minute)}"
            }

            "CANCEL_ALARM" -> {
                val hour = input["hour"]?.toIntOrNull()
                val minute = input["minute"]?.toIntOrNull()
                val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                    if (hour != null && minute != null) {
                        putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    } else {
                        putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_NEXT)
                    }
                }
                launchSettings(intent)
                if (hour != null && minute != null) {
                    "Requested cancel for alarm at ${formatTime(hour, minute)}"
                } else {
                    "Requested cancel for the next alarm"
                }
            }

            else -> {
                launchSettings(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                "Opened alarms"
            }
        }
    }

    private fun handleTimer(action: String, input: Map<String, String>): String {
        return when (action.uppercase()) {
            "START_TIMER" -> {
                val lengthSeconds = input["lengthSeconds"]?.toIntOrNull()
                if (lengthSeconds == null || lengthSeconds <= 0) {
                    launchSettings(Intent(AlarmClock.ACTION_SHOW_TIMERS))
                    return "Opened timers. Say: set timer for 10 minutes"
                }

                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, lengthSeconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, input["label"] ?: "Jarvis Timer")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
                launchSettings(intent)
                "Timer set for ${formatDuration(lengthSeconds)}"
            }

            "CANCEL_TIMER" -> {
                launchSettings(Intent(AlarmClock.ACTION_SHOW_TIMERS))
                "Opened timers to cancel running timer"
            }

            else -> {
                launchSettings(Intent(AlarmClock.ACTION_SHOW_TIMERS))
                "Opened timers"
            }
        }
    }

    private fun findFlashCameraId(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    private fun launchSettings(intent: Intent) {
        val launchIntent = if (canResolve(intent)) intent else Intent(Settings.ACTION_SETTINGS)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(launchIntent)
    }

    private fun canResolve(intent: Intent): Boolean {
        return intent.resolveActivity(appContext.packageManager) != null
    }

    private fun formatTime(hour24: Int, minute: Int): String {
        val suffix = if (hour24 >= 12) "PM" else "AM"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return "%d:%02d %s".format(hour12, minute, suffix)
    }

    private fun formatDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return buildString {
            if (hours > 0) {
                append("$hours hour")
                if (hours != 1) append("s")
            }
            if (minutes > 0) {
                if (isNotEmpty()) append(" ")
                append("$minutes minute")
                if (minutes != 1) append("s")
            }
            if (seconds > 0 || isEmpty()) {
                if (isNotEmpty()) append(" ")
                append("$seconds second")
                if (seconds != 1) append("s")
            }
        }
    }
}