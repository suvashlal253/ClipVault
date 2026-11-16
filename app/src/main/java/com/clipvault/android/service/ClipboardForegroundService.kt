package com.clipvault.android.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.clipvault.android.ClipMonitorService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ClipboardForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("auto_capture_enabled", false)
                    .apply()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                ClipMonitorService.start(this)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "clipvault_persistent_channel"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_CAPTURE_NOW = "ACTION_CAPTURE_NOW"

        fun start(context: Context) {
            context.getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("auto_capture_enabled", true)
                .apply()
            ClipMonitorService.start(context)
        }

        fun stop(context: Context) {
            try {
                context.getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("auto_capture_enabled", false)
                    .apply()
                context.stopService(Intent(context, ClipMonitorService::class.java))
            } catch (_: Exception) {}
        }
    }
}
