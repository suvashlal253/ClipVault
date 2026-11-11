package com.clipvault.android.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.clipvault.android.domain.repository.ClipboardRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Android 10 (API 29)+ restricts background apps from reading clipboard data.
 * An AccessibilityService runs with elevated system capabilities and can access
 * ClipboardManager whenever copy events or text selections happen in other apps.
 * Uses an unbounded FIFO Channel + Single-Thread Dispatcher + Mutex so rapid consecutive
 * copies are captured synchronously and inserted sequentially with zero intermediate loss.
 */
@AndroidEntryPoint
class ClipboardAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var clipboardRepository: ClipboardRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sequentialClipDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val serviceScope = CoroutineScope(sequentialClipDispatcher + SupervisorJob())
    private val captureMutex = Mutex()
    private val clipEventQueue = Channel<Pair<String, String?>>(Channel.UNLIMITED)

    private lateinit var clipboardManager: ClipboardManager
    @Volatile
    private var lastQueuedText: String? = null
    @Volatile
    private var lastQueuedTime: Long = 0L

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        processCurrentClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        startSequentialQueueWorker()
        Log.d(TAG, "ClipboardAccessibilityService registered clip listener & FIFO queue")
    }

    private fun startSequentialQueueWorker() {
        serviceScope.launch {
            for ((text, sourcePkg) in clipEventQueue) {
                captureMutex.withLock {
                    withContext(NonCancellable) {
                        clipboardRepository.saveNewClip(
                            content = text,
                            sourceApp = sourcePkg,
                            autoVault = false
                        )
                        Log.d(TAG, "Queued clip saved to Home Dashboard: ${text.take(20)}...")
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Instant capture on text selection, copy button click, or "Copied" toast/snackbar in WhatsApp & Facebook
        // No artificial throttling so rapid back-to-back copies are never skipped
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                processCurrentClipboard(event.packageName?.toString())
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "ClipboardAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        clipEventQueue.close()
        serviceScope.cancel()
    }

    /**
     * Reads the current ClipData snapshot synchronously on the event thread and immediately
     * enqueues it into the unbounded FIFO Channel so rapid consecutive copies never overwrite each other.
     */
    private fun processCurrentClipboard(sourcePackage: String? = null) {
        try {
            if (!clipboardManager.hasPrimaryClip()) return

            val clipData: ClipData? = clipboardManager.primaryClip
            if (clipData == null || clipData.itemCount == 0) return

            for (i in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(i)
                val text = item.coerceToText(this)?.toString()?.trim() ?: continue
                if (text.isEmpty()) continue

                val now = System.currentTimeMillis()
                // Only skip if the exact same text was just queued within 120ms by duplicate OS accessibility events;
                // any distinct rapid copy is queued immediately with zero delay.
                if (text == lastQueuedText && (now - lastQueuedTime) < 120L) {
                    continue
                }

                lastQueuedText = text
                lastQueuedTime = now
                clipEventQueue.trySend(text to sourcePackage)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException reading clipboard: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing clipboard: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ClipAccessService"
    }
}
