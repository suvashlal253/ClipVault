package com.clipvault.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.domain.classifier.ClipClassifier
import com.clipvault.android.service.ClipSyncTrampolineActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
class ClipAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var clipDao: ClipDao

    private var clipboardManager: ClipboardManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class QueuedClip(
        val text: String,
        val sourcePackage: String
    )

    // Unbounded Coroutine Channel + Mutex Queue so rapid background copies are never lost
    private val clipQueue = Channel<QueuedClip>(Channel.UNLIMITED)
    private val mutex = Mutex()

    @Volatile
    private var lastSavedText: String? = null

    // Tracks the most recently highlighted/selected text across any app on the phone
    @Volatile
    private var latestSelectedText: String? = null

    @Volatile
    private var latestSelectedPackage: String? = null

    @Volatile
    private var latestSelectionTimestamp: Long = 0L

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val pkg = latestSelectedPackage ?: "com.android.systemui"
        val capturedDirectly = readClipboardAndEnqueue(pkg)
        if (!capturedDirectly) {
            // If Android 10-15 masks primaryClip in background, immediately save cached selection
            val now = SystemClock.elapsedRealtime()
            val selected = latestSelectedText?.trim()
            if (!selected.isNullOrEmpty() && (now - latestSelectionTimestamp) <= 15_000L) {
                enqueueClip(selected, pkg)
            }
            // Also trigger ephemeral focus trampoline for 1-tap "Copy Link" / "Copy Code" buttons
            launchTrampolineActivity()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startClipProcessor()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
                eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                notificationTimeout = 80L
                flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update AccessibilityServiceInfo dynamically: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkgName = event.packageName?.toString() ?: "android"

        // Ignore events originating from ClipVault itself to prevent feedback loops
        if (pkgName == packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                val selected = extractSelectedText(event)
                if (!selected.isNullOrBlank()) {
                    latestSelectedText = selected
                    latestSelectedPackage = pkgName
                    latestSelectionTimestamp = SystemClock.elapsedRealtime()
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (isCopyActionEvent(event)) {
                    val now = SystemClock.elapsedRealtime()
                    val selected = latestSelectedText?.trim()
                    if (!selected.isNullOrEmpty() && (now - latestSelectionTimestamp) <= 20_000L) {
                        enqueueClip(selected, pkgName)
                    }
                    // Also read primaryClip after a short 120ms settle delay once OS updates clipboard
                    serviceScope.launch {
                        delay(120)
                        if (!readClipboardAndEnqueue(pkgName)) {
                            launchTrampolineActivity()
                        }
                    }
                }
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (isClipboardToastOrOverlay(event, pkgName)) {
                    val now = SystemClock.elapsedRealtime()
                    val selected = latestSelectedText?.trim()
                    if (!selected.isNullOrEmpty() && (now - latestSelectionTimestamp) <= 15_000L) {
                        enqueueClip(selected, latestSelectedPackage ?: pkgName)
                    }
                    if (!readClipboardAndEnqueue(pkgName)) {
                        launchTrampolineActivity()
                    }
                }
            }
        }
    }

    private fun launchTrampolineActivity() {
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
        }
    }

    /**
     * Extracts the currently highlighted/selected text from the AccessibilityEvent or source node.
     */
    private fun extractSelectedText(event: AccessibilityEvent): String? {
        var sourceNode: AccessibilityNodeInfo? = null
        try {
            sourceNode = event.source
            if (sourceNode != null) {
                val nodeText = sourceNode.text?.toString()
                val selStart = sourceNode.textSelectionStart
                val selEnd = sourceNode.textSelectionEnd
                if (!nodeText.isNullOrEmpty() && selStart >= 0 && selEnd >= 0 && selStart != selEnd) {
                    val start = minOf(selStart, selEnd).coerceAtLeast(0)
                    val end = maxOf(selStart, selEnd).coerceAtMost(nodeText.length)
                    if (end > start) {
                        return nodeText.substring(start, end).trim()
                    }
                }
            }

            val eventText = event.text.joinToString(" ").trim()
            val fromIdx = event.fromIndex
            val toIdx = event.toIndex
            if (eventText.isNotEmpty() && fromIdx >= 0 && toIdx > fromIdx && toIdx <= eventText.length) {
                return eventText.substring(fromIdx, toIdx).trim()
            }
        } catch (_: Exception) {
        } finally {
            @Suppress("DEPRECATION")
            try {
                sourceNode?.recycle()
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Checks if a clicked view corresponds to a "Copy" button or floating toolbar action.
     */
    private fun isCopyActionEvent(event: AccessibilityEvent): Boolean {
        val combinedText = buildString {
            event.text.forEach { append(it).append(' ') }
            event.contentDescription?.let { append(it).append(' ') }
        }.lowercase()

        val viewId = try {
            event.source?.viewIdResourceName?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }

        return combinedText.contains("copy") ||
            combinedText.contains("কপি") ||
            combinedText.contains("copiar") ||
            combinedText.contains("copier") ||
            combinedText.contains("kopieren") ||
            viewId.contains("copy") ||
            viewId.endsWith(":id/floating_toolbar_menu_item_text")
    }

    /**
     * Detects Android 13+ SystemUI clipboard overlay or "Copied to clipboard" toast notifications.
     */
    private fun isClipboardToastOrOverlay(event: AccessibilityEvent, pkgName: String): Boolean {
        val text = buildString {
            event.text.forEach { append(it).append(' ') }
            event.contentDescription?.let { append(it).append(' ') }
        }.lowercase()

        if (text.contains("copied") || text.contains("clipboard") || text.contains("কপি করা")) {
            return true
        }
        if (pkgName == "com.android.systemui" &&
            (text.contains("share") && text.contains("edit") || text.contains("clipboard"))
        ) {
            return true
        }
        return false
    }

    /**
     * Reads ClipboardManager.primaryClip directly if permitted by OS and enqueues all items.
     */
    private fun readClipboardAndEnqueue(sourcePackage: String): Boolean {
        return try {
            val manager = clipboardManager ?: return false
            if (!manager.hasPrimaryClip()) return false
            val clipData: ClipData = manager.primaryClip ?: return false
            if (clipData.itemCount <= 0) return false

            var foundAny = false
            for (i in 0 until clipData.itemCount) {
                val rawText = clipData.getItemAt(i).coerceToText(this)?.toString()?.trim()
                if (!rawText.isNullOrEmpty()) {
                    enqueueClip(rawText, sourcePackage)
                    foundAny = true
                }
            }
            foundAny
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun enqueueClip(rawText: String, sourcePackage: String) {
        val trimmed = rawText.trim()
        if (trimmed.isNotEmpty()) {
            clipQueue.trySend(QueuedClip(trimmed, sourcePackage))
        }
    }

    /**
     * Sequential background consumer using Kotlin Coroutine Channel + Mutex lock
     * to insert every captured clip into Room without race conditions or dropped items.
     */
    private fun startClipProcessor() {
        serviceScope.launch {
            for (item in clipQueue) {
                mutex.withLock {
                    val trimmed = item.text.trim()
                    if (trimmed.isNotEmpty() && trimmed != lastSavedText) {
                        val latestInDb = try {
                            clipDao.getLatestClipContent()?.trim()
                        } catch (_: Exception) {
                            null
                        }

                        if (latestInDb == trimmed) {
                            lastSavedText = trimmed
                            return@withLock
                        }

                        lastSavedText = trimmed
                        val classification = ClipClassifier.classify(trimmed)
                        val newClip = ClipEntity(
                            content = trimmed,
                            encryptedIv = null,
                            category = classification.category,
                            createdAt = System.currentTimeMillis(),
                            tags = classification.tags,
                            sourceApp = item.sourcePackage,
                            isPinned = false,
                            isVaulted = false,
                            charCount = trimmed.length
                        )
                        clipDao.insertClip(newClip)
                        delay(100)
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "ClipAccessibilityService interrupted")
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        clipQueue.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ClipAccessibilitySvc"
    }
}
