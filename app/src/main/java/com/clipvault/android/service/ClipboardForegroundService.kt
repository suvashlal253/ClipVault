package com.clipvault.android.service

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.clipvault.android.R
import com.clipvault.android.domain.repository.ClipboardRepository
import com.clipvault.android.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardForegroundService : Service() {

    @Inject
    lateinit var repository: ClipboardRepository

    private lateinit var clipboardManager: ClipboardManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureClipboardContent()
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_CAPTURE_NOW -> {
                captureClipboardContent()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
    }

    private fun captureClipboardContent() {
        try {
            if (clipboardManager.hasPrimaryClip()) {
                val clip: ClipData? = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
                    if (!text.isNullOrEmpty()) {
                        serviceScope.launch {
                            repository.saveNewClip(content = text, sourceApp = "Background Service")
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Handled safely
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val captureIntent = Intent(this, ClipboardForegroundService::class.java).apply {
            action = ACTION_CAPTURE_NOW
        }.let {
            PendingIntent.getService(this, 1, it, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ClipVault Active")
            .setContentText("Monitoring clipboard without accessibility restrictions")
            .setSmallIcon(R.drawable.ic_content_copy)
            .setContentIntent(openAppIntent)
            .addAction(R.drawable.ic_content_copy, "Sync Clip", captureIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clipboard Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status while ClipVault is actively auto-capturing clips"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "clipboard_monitor_channel"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_CAPTURE_NOW = "ACTION_CAPTURE_NOW"
        const val ACTION_PAUSE = "ACTION_PAUSE"

        fun start(context: Context) {
            val intent = Intent(context, ClipboardForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ClipboardForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
