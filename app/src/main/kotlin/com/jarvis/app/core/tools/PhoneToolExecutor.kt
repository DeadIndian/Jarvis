package com.jarvis.app.core.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.jarvis.app.InstalledAppLauncher
import kotlinx.coroutines.runBlocking

/**
 * PhoneToolExecutor is the core-level interface for executing local phone tool actions.
 * It abstracts away SystemControlManager so that core/session/VoiceSessionManager
 * does not depend on app-level code.
 */
interface PhoneToolExecutor {
    fun executeTool(tool: String, arguments: Map<String, Any>): Boolean
}

/**
 * InMemoryPhoneToolExecutor dispatches tool calls to concrete handlers.
 * Uses the deterministic tool set registered at construction time.
 */
class InMemoryPhoneToolExecutor(
    private val context: Context,
    private val installedAppLauncher: InstalledAppLauncher? = null
) : PhoneToolExecutor {

    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun executeTool(tool: String, arguments: Map<String, Any>): Boolean {
        return try {
            when (tool) {
                "set_volume" -> handleSetVolume(arguments)
                "toggle_bluetooth" -> handleBluetooth(arguments)
                "toggle_wifi" -> handleWifi(arguments)
                "media_control" -> handleMediaControl(arguments)
                "open_app" -> handleOpenApp(arguments)
                "make_call" -> handleMakeCall(arguments)
                "send_whatsapp" -> handleSendWhatsApp(arguments)
                "read_notifications" -> handleReadNotifications(arguments)
                "toggle_flashlight" -> handleFlashlight(arguments)
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun handleSetVolume(arguments: Map<String, Any>): Boolean {
        val level = (arguments["level"] as? Number)?.toInt() ?: return false
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = ((maxVolume * level) / 100.0).toInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return true
    }

    private fun handleBluetooth(arguments: Map<String, Any>): Boolean {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun handleWifi(arguments: Map<String, Any>): Boolean {
        // On Android Q+, we can't toggle Wi-Fi programmatically without system permission.
        // Open the Wi-Fi settings panel so the user can toggle.
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun handleMediaControl(arguments: Map<String, Any>): Boolean {
        val action = arguments["action"]?.toString()?.lowercase() ?: return false
        val keyCode = when (action) {
            "play" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return false
        }
        val down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        val up = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(down)
        audioManager.dispatchMediaKeyEvent(up)
        return true
    }

    private fun handleOpenApp(arguments: Map<String, Any>): Boolean {
        val appName = arguments["app_name"]?.toString() ?: arguments["package_name"]?.toString()
        if (appName.isNullOrBlank()) return false
        val launcher = installedAppLauncher ?: return false
        // InstalledAppLauncher.launch is suspend; runBlocking bridges sync interface to suspend.
        // ponytail: single-thread blocking call — acceptable for a tool-dispatch interface,
        //           upgrade path: make executeTool suspend-callback or use a coroutine scope.
        return try {
            runBlocking { launcher.launch(appName) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun handleMakeCall(arguments: Map<String, Any>): Boolean {
        val raw = arguments["phone_number"]?.toString()?.trim() ?: return false
        if (raw.isEmpty()) return false

        // If it already looks like a dialable number, call it directly. Otherwise treat as a
        // contact name and look it up (needs READ_CONTACTS).
        val number = if (raw.any { it.isDigit() } && raw.none { it.isLetter() }) {
            raw
        } else {
            resolveContact(raw) ?: return false
        }

        val hasCallPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        // ACTION_CALL places the call directly (needs CALL_PHONE); ACTION_DIAL just opens the
        // dialer pre-filled (no permission). Fall back to DIAL so the action never silently fails.
        val action = if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun handleSendWhatsApp(arguments: Map<String, Any>): Boolean {
        val recipient = arguments["recipient"]?.toString()?.trim() ?: return false
        val message = arguments["message"]?.toString()?.trim().orEmpty()
        if (recipient.isEmpty()) return false

        // Resolve to a number: literal digits pass through; a name is looked up in contacts.
        val number = if (recipient.any { it.isDigit() } && recipient.none { it.isLetter() }) {
            recipient
        } else {
            resolveContact(recipient) ?: return false
        }
        // wa.me wants digits only, no '+', spaces, or dashes.
        val waNumber = number.filter { it.isDigit() }
        if (waNumber.isEmpty()) return false

        // Deep link opens the chat with the message pre-filled; the user taps send.
        val uri = Uri.parse("https://wa.me/$waNumber?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Force the standard WhatsApp app if present, else business, else let the system resolve
        // wa.me (browser → WhatsApp). Prevents a browser tab when WhatsApp is installed.
        val pm = context.packageManager
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull {
            runCatching { pm.getPackageInfo(it, 0) }.isSuccess
        }
        if (pkg != null) intent.setPackage(pkg)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun resolveContact(name: String): String? {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name)
        )
        val proj = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }

    private fun handleReadNotifications(arguments: Map<String, Any>): Boolean {
        return true
    }

    private fun handleFlashlight(arguments: Map<String, Any>): Boolean {
        val enable = arguments["enable"] as? Boolean ?: false
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return false
        return try {
            cameraManager.setTorchMode(cameraId, enable)
            true
        } catch (_: CameraAccessException) {
            false
        }
    }
}
