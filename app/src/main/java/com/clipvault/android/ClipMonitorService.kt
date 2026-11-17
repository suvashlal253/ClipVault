package com.clipvault.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.clipvault.android.service.ClipboardServiceHelper
import com.clipvault.android.ui.MainActivity
import com.clipvault.android.util.AccessibilityPermissionHelper

class ClipMonitorService : Service() {

    private var clipboardManager: ClipboardManager? = null

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (AccessibilityPermissionHelper.isAccessibilityServiceEnabled(this, ClipAccessibilityService::class.java)) {
            return@OnPrimaryClipChangedListener
        }

        try {
            val clipData: ClipData? = clipboardManager?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                for (i in 0 until clipData.itemCount) {
                    val item = clipData.getItemAt(i)
                    val text = item?.text?.toString() ?: item?.coerceToText(this)?.toString()

                    if (!text.isNullOrBlank()) {
                        ClipboardServiceHelper.getInstance(this).enqueueClip(text, "ClipMonitorService")
                    }
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "clipvault_persistent_channel"
        val channelName = "ClipVault Background Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ClipVault status and quick clipboard import actions"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val isA11yOn = AccessibilityPermissionHelper.isAccessibilityServiceEnabled(this, ClipAccessibilityService::class.java)
        val isImeDefault = AccessibilityPermissionHelper.isImeSelectedAsDefault(this)

        val statusTitle = when {
            isImeDefault -> "ClipVault · ClipVault Keyboard Active"
            isA11yOn -> "ClipVault · Clipboard Capture Active"
            else -> "ClipVault · Tap Import to Sync Latest Clip"
        }

        val statusBody = when {
            isImeDefault || isA11yOn -> "Automatic clipboard capture is active"
            else -> "Using another keyboard (e.g. Gboard). Tap Import Clip to save copied text."
        }

        val importIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_IMPORT_CLIPBOARD
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val importPendingIntent = PendingIntent.getActivity(
            this,
            101,
            importIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(statusTitle)
            .setContentText(statusBody)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(importPendingIntent)
            .addAction(android.R.drawable.ic_input_add, "Import Clip", importPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    1002,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(1002, notification)
            }
        } catch (_: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceWithNotification()
        val extraText = intent?.getStringExtra(EXTRA_ENQUEUE_TEXT)
        if (!extraText.isNullOrBlank()) {
            ClipboardServiceHelper.getInstance(this).enqueueClip(extraText, "ClipMonitorService")
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        runCatching {
            val restartIntent = Intent(applicationContext, ClipMonitorService::class.java)
            val pendingIntent = PendingIntent.getService(
                applicationContext,
                1010,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 500L,
                pendingIntent
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val EXTRA_ENQUEUE_TEXT = "com.clipvault.android.EXTRA_ENQUEUE_TEXT"
        const val ACTION_IMPORT_CLIPBOARD = "com.clipvault.android.ACTION_IMPORT_CLIPBOARD"

        fun enqueueFromFocusReader(context: Context, text: String) {
            ClipboardServiceHelper.getInstance(context).enqueueClip(text, "Foreground Sync")
        }

        fun start(context: Context) {
            try {
                val serviceIntent = Intent(context, ClipMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (_: Exception) {
            }
        }
    }
}
