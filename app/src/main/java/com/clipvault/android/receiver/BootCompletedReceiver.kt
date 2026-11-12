package com.clipvault.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clipvault.android.service.ClipboardForegroundService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == ACTION_RESTART_SERVICE
        ) {
            val prefs = context.getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
            val autoCaptureEnabled = prefs.getBoolean("auto_capture_enabled", true)
            if (autoCaptureEnabled) {
                ClipboardForegroundService.start(context)
            }
        }
    }

    companion object {
        const val ACTION_RESTART_SERVICE = "com.clipvault.android.action.RESTART_CLIP_SERVICE"
    }
}
