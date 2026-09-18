package com.jarvis.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver starts any boot-time initialization.
 * TODO: Re-wire to runtime service once Phase 1 services are re-implemented.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Boot completed — runtime service not yet wired (Phase 1).")
        }
    }
}
