package com.clipvault.android

import android.app.Application
import android.content.Context
import com.clipvault.android.service.ClipboardForegroundService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ClipVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("auto_capture_enabled", true)) {
            ClipboardForegroundService.start(this)
        }
    }
}
