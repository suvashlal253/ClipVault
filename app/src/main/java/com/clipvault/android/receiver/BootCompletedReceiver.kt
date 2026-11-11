package com.clipvault.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clipvault.android.service.ClipboardForegroundService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
            val autoCaptureEnabled = prefs.getBoolean("auto_capture_enabled", true)
            if (autoCaptureEnabled) {
                ClipboardForegroundService.start(context)
            }
        }
    }
}
