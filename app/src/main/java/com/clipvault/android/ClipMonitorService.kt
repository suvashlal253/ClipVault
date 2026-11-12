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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@AndroidEntryPoint
class ClipMonitorService : Service() {

    @Inject
    lateinit var clipDao: ClipDao

    @Inject
    lateinit var clipboardServiceHelper: ClipboardServiceHelper

    private var clipboardManager: ClipboardManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // A thread-safe channel to queue clipboard texts sequentially
    private val clipQueue = Channel<String>(Channel.UNLIMITED)
    private val mutex = Mutex()
    private val lastTimestamp = AtomicLong(0L)
    private var lastProcessedText: String? = null

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        var capturedDirectly = false
        try {
            val clipData: ClipData? = clipboardManager?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                for (i in 0 until clipData.itemCount) {
                    val item = clipData.getItemAt(i)
                    val text = item?.text?.toString() ?: item?.coerceToText(this)?.toString()

                    if (!text.isNullOrBlank()) {
                        capturedDirectly = true
                        // Send to channel immediately so no rapid copy is lost
                        serviceScope.launch {
                            clipQueue.send(text)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // On Android 10+, background services need brief window focus to read primaryClip.
        // Launch ClipSyncTrampolineActivity which waits for onWindowFocusChanged(true)
        // and feeds the captured text directly into this service's Channel queue.
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
        activeInstance = this
        startForegroundServiceWithNotification()

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)

        // Start consumer worker to process the queue sequentially
        startClipProcessor()
    }

    private fun startClipProcessor() {
        serviceScope.launch {
            for (text in clipQueue) {
                mutex.withLock {
                    val trimmed = text.trim()
                    if (trimmed.isNotEmpty() && trimmed != lastProcessedText) {
                        lastProcessedText = trimmed
                        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                        val wakeLock = powerManager?.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "ClipVault::ChannelQueueWakeLock"
                        )
                        try {
                            wakeLock?.acquire(3_000L)
                            // Check against latest clip in Room DB to avoid duplicate consecutive entries
                            val latestInDb = clipDao.getLatestClipContent()?.trim()
                            if (latestInDb != trimmed) {
                                val classification = ClipClassifier.classify(trimmed)
                                val now = System.currentTimeMillis()
                                val monotonicTime = maxOf(now, lastTimestamp.get() + 1L)
                                lastTimestamp.set(monotonicTime)

                                val entity = ClipEntity(
                                    content = trimmed,
                                    encryptedIv = null,
                                    category = classification.category,
                                    createdAt = monotonicTime,
                                    tags = classification.tags,
                                    sourceApp = "ClipMonitorService",
                                    isPinned = false,
                                    isVaulted = false,
                                    charCount = trimmed.length
                                )
                                clipDao.insertClip(entity)
                                clipboardServiceHelper.resetLastCopiedText(trimmed)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            if (wakeLock?.isHeld == true) {
                                runCatching { wakeLock.release() }
                            }
                        }
                    }
                }
            }
        }
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
                description = "Actively monitoring clipboard in background"
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
            .setContentTitle("ClipVault is running")
            .setContentText("Actively monitoring clipboard in background")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openAppIntent)
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceWithNotification()
        val extraText = intent?.getStringExtra(EXTRA_ENQUEUE_TEXT)
        if (!extraText.isNullOrBlank()) {
            serviceScope.launch {
                clipQueue.send(extraText)
            }
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
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onDestroy()
        try {
            clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serviceScope.cancel()
    }

    companion object {
        const val EXTRA_ENQUEUE_TEXT = "com.clipvault.android.EXTRA_ENQUEUE_TEXT"

        @Volatile
        private var activeInstance: ClipMonitorService? = null

        fun enqueueFromFocusReader(context: Context, text: String) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            val instance = activeInstance
            if (instance != null) {
                instance.serviceScope.launch {
                    instance.clipQueue.send(trimmed)
                }
            } else {
                try {
                    val intent = Intent(context, ClipMonitorService::class.java).apply {
                        putExtra(EXTRA_ENQUEUE_TEXT, trimmed)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (_: Exception) {}
            }
        }

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
