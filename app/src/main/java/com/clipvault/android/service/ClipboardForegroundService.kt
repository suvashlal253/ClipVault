package com.clipvault.android.service

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.clipvault.android.R
import com.clipvault.android.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardForegroundService : Service() {

    @Inject
    lateinit var clipboardServiceHelper: ClipboardServiceHelper

    private lateinit var clipboardManager: ClipboardManager
    private var lastSnippet: String = "Instant auto-capture active"

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureClipboardContent(allowTrampolineFallback = true)
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(lastSnippet),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(lastSnippet))
            }
        } catch (_: Exception) {
            // Safe fallback if background foreground-service restrictions apply
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_CAPTURE_NOW -> {
                captureClipboardContent(allowTrampolineFallback = true)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.removePrimaryClipChangedListener(clipListener)
    }

    /**
     * Snapshots primaryClip immediately on the listener callback thread and forwards each item
     * to [ClipboardServiceHelper.onTextCopied], which locks with a Mutex on Dispatchers.IO
     * to guarantee sequential Room insertion without dropping rapid consecutive copies.
     */
    private fun captureClipboardContent(allowTrampolineFallback: Boolean = false) {
        var capturedDirectly = false
        try {
            if (clipboardManager.hasPrimaryClip()) {
                val clip: ClipData? = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    for (i in 0 until clip.itemCount) {
                        val rawText = clip.getItemAt(i).coerceToText(this)?.toString()
                        if (!rawText.isNullOrBlank()) {
                            capturedDirectly = true
                            clipboardServiceHelper.onTextCopied(
                                rawText = rawText,
                                sourceApp = "Instant Auto-Capture",
                                onSaved = { savedEntity ->
                                    val preview = savedEntity.content.take(30) +
                                        if (savedEntity.content.length > 30) "..." else ""
                                    updateNotificationSnippet("Saved: $preview")
                                }
                            )
                        }
                    }
                    if (capturedDirectly) return
                }
            }
        } catch (_: Exception) {
            // Android 10+ background restriction may require window focus
        }

        if (!allowTrampolineFallback) return

        // Instant zero-animation Trampoline Activity focus read when Android 10+ blocks background read
        try {
            val syncIntent = Intent(this, ClipSyncTrampolineActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
                )
            }
            startActivity(syncIntent)
        } catch (_: Exception) {
            // Handled safely
        }
    }

    private fun updateNotificationSnippet(snippet: String) {
        lastSnippet = snippet
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(snippet))
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val captureIntent = Intent(this, ClipSyncTrampolineActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }.let {
            PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val stopIntent = Intent(this, ClipboardForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }.let {
            PendingIntent.getService(this, 2, it, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ClipVault Background Auto-Capture")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_content_copy)
            .setContentIntent(openAppIntent)
            .addAction(R.drawable.ic_content_copy, "Sync Clip Now", captureIntent)
            .addAction(R.drawable.ic_content_copy, "Pause", stopIntent)
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

        fun start(context: Context) {
            try {
                val intent = Intent(context, ClipboardForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Prevent ForegroundServiceStartNotAllowedException on restricted background states
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, ClipboardForegroundService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
