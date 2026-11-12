package com.clipvault.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.domain.classifier.ClipClassifier
import com.clipvault.android.service.ClipSyncTrampolineActivity
import com.clipvault.android.service.ClipboardServiceHelper
import com.clipvault.android.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
class ClipMonitorService : Service() {

    @Inject
    lateinit var clipDao: ClipDao

    @Inject
    lateinit var clipboardServiceHelper: ClipboardServiceHelper

    private var clipboardManager: ClipboardManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    @Volatile
    private var lastCopiedText: String? = null

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        var capturedDirectly = false
        try {
            val clipData = clipboardManager?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                for (i in 0 until clipData.itemCount) {
                    val text = clipData.getItemAt(i)?.coerceToText(this)?.toString()?.trim()
                    if (!text.isNullOrBlank()) {
                        capturedDirectly = true
                        if (text != lastCopiedText) {
                            lastCopiedText = text
                            saveClipSequentially(text)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Translucent zero-animation focus fallback if Android 10+ blocks background primaryClip read
        if (!capturedDirectly) {
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
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "clipvault_monitor_channel"
        val channelName = "ClipVault Background Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running in background to capture clips safely"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ClipVault Active")
            .setContentText("Running in background to capture clips safely")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    1001,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveClipSequentially(text: String) {
        serviceScope.launch {
            mutex.withLock {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ClipVault::ClipMonitorWakeLock"
                )
                try {
                    wakeLock?.acquire(3_000L)
                    val trimmed = text.trim()
                    if (trimmed.isEmpty()) return@withLock

                    // Prevent duplicate consecutive insertion if already at top of Room DB
                    val latestInDb = clipDao.getLatestClipContent()?.trim()
                    if (latestInDb == trimmed) return@withLock

                    // Insert securely into Room Database DAO sequentially
                    val classification = ClipClassifier.classify(trimmed)
                    val entity = ClipEntity(
                        content = trimmed,
                        encryptedIv = null,
                        category = classification.category,
                        createdAt = System.currentTimeMillis(),
                        tags = classification.tags,
                        sourceApp = "ClipMonitorService",
                        isPinned = false,
                        isVaulted = false,
                        charCount = trimmed.length
                    )
                    clipDao.insertClip(entity)
                } catch (e: Exception) {
                    e.printStackTrace()
                    clipboardServiceHelper.onTextCopied(text, sourceApp = "ClipMonitorService")
                } finally {
                    if (wakeLock?.isHeld == true) {
                        runCatching { wakeLock.release() }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceWithNotification()
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serviceScope.cancel()
    }

    companion object {
        fun start(context: Context) {
            try {
                val serviceIntent = Intent(context, ClipMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
