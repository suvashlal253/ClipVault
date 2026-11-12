package com.clipvault.android.service

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.clipvault.android.R
import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.domain.classifier.ClipClassifier
import com.clipvault.android.receiver.BootCompletedReceiver
import com.clipvault.android.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Persistent 24/7 Foreground Service (ClipMonitorService pattern) that keeps running
 * with an ongoing low-importance notification even when ClipVault is swiped away or closed.
 */
@AndroidEntryPoint
class ClipboardForegroundService : Service() {

    @Inject
    lateinit var clipDao: ClipDao

    @Inject
    lateinit var clipboardServiceHelper: ClipboardServiceHelper

    private var clipboardManager: ClipboardManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    @Volatile
    private var lastCopiedText: String? = null

    @Volatile
    private var isExplicitlyStopped: Boolean = false

    private var lastSnippet: String = "Listening for copied clips in the background"

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureClipboardContent(allowTrampolineFallback = true)
    }

    override fun onCreate() {
        super.onCreate()
        isExplicitlyStopped = false

        // 1. Immediately promote to Foreground Service so Android OS never kills it
        startForegroundServiceWithNotification(lastSnippet)

        // 2. Register PrimaryClipChangedListener on system ClipboardManager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                isExplicitlyStopped = true
                getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("auto_capture_enabled", false)
                    .apply()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CAPTURE_NOW -> {
                startForegroundServiceWithNotification(lastSnippet)
                captureClipboardContent(allowTrampolineFallback = true)
            }
            else -> {
                // Ensure foreground notification is active on every START_STICKY restart
                startForegroundServiceWithNotification(lastSnippet)
            }
        }
        // START_STICKY ensures the OS recreates the service automatically if memory is low
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification(statusText: String = lastSnippet) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ClipVault Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps ClipVault active in the background to capture copied clips 24/7"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = buildNotification(statusText)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // Fallback notification display if OS restricts FGS type in rare OEM states
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }
    }

    /**
     * Captures primaryClip items immediately on callback thread and queues them sequentially
     * via [saveClipToDatabase] under a [Mutex] lock so rapid copies never race or get lost.
     */
    private fun captureClipboardContent(allowTrampolineFallback: Boolean = false) {
        var capturedDirectly = false
        try {
            val cm = clipboardManager ?: (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            if (cm != null && cm.hasPrimaryClip()) {
                val clip: ClipData? = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    for (i in 0 until clip.itemCount) {
                        val text = clip.getItemAt(i).coerceToText(this)?.toString()?.trim()
                        if (!text.isNullOrEmpty()) {
                            capturedDirectly = true
                            if (text != lastCopiedText) {
                                lastCopiedText = text
                                saveClipToDatabase(text)
                            }
                        }
                    }
                    if (capturedDirectly) return
                }
            }
        } catch (_: Exception) {
            // Android 10+ background restriction may require translucent window focus
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

    private fun saveClipToDatabase(text: String) {
        // Queue using Mutex inside SupervisorJob IO scope to prevent race conditions during rapid copying
        serviceScope.launch {
            mutex.withLock {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ClipVault::ClipSaveWakeLock"
                )
                try {
                    wakeLock?.acquire(3_000L)
                    val trimmed = text.trim()
                    if (trimmed.isEmpty()) return@withLock

                    // Check against latest clip in Room DB to avoid duplicate consecutive entries
                    val latestInDb = clipDao.getLatestClipContent()?.trim()
                    if (latestInDb == trimmed) return@withLock

                    val classification = ClipClassifier.classify(trimmed)
                    val entity = ClipEntity(
                        content = trimmed,
                        encryptedIv = null,
                        category = classification.category,
                        createdAt = System.currentTimeMillis(),
                        tags = classification.tags,
                        sourceApp = "Background Service",
                        isPinned = false,
                        isVaulted = false,
                        charCount = trimmed.length
                    )
                    clipDao.insertClip(entity)

                    val preview = trimmed.take(30) + if (trimmed.length > 30) "..." else ""
                    updateNotificationSnippet("Saved: $preview")
                } catch (_: Exception) {
                    // Fallback to helper if needed
                    clipboardServiceHelper.onTextCopied(text, sourceApp = "Background Service")
                } finally {
                    if (wakeLock?.isHeld == true) {
                        runCatching { wakeLock.release() }
                    }
                }
            }
        }
    }

    /**
     * Called when the user swipes the app away from the Recent Apps list.
     * Schedules an immediate restart so the background clipboard listener never dies.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!isExplicitlyStopped) {
            scheduleImmediateRestart()
        }
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        serviceScope.cancel()
        if (!isExplicitlyStopped) {
            scheduleImmediateRestart()
        }
        super.onDestroy()
    }

    private fun scheduleImmediateRestart() {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val autoCaptureEnabled = prefs.getBoolean("auto_capture_enabled", true)
        if (!autoCaptureEnabled) return

        runCatching {
            val restartServiceIntent = Intent(applicationContext, ClipboardForegroundService::class.java)
            val pendingIntent = PendingIntent.getService(
                applicationContext,
                1009,
                restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 600L,
                pendingIntent
            )
        }

        runCatching {
            val broadcastIntent = Intent(applicationContext, BootCompletedReceiver::class.java).apply {
                action = BootCompletedReceiver.ACTION_RESTART_SERVICE
            }
            sendBroadcast(broadcastIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
            .setContentTitle("ClipVault is active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_content_copy)
            .setContentIntent(openAppIntent)
            .addAction(R.drawable.ic_content_copy, "Sync Clip Now", captureIntent)
            .addAction(R.drawable.ic_content_copy, "Pause", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "clipvault_monitor_channel"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_CAPTURE_NOW = "ACTION_CAPTURE_NOW"

        fun start(context: Context) {
            try {
                context.getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("auto_capture_enabled", true)
                    .apply()
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
