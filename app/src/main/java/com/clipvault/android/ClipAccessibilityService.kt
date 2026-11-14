package com.clipvault.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.clipvault.android.service.ClipboardServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClipAccessibilityService : AccessibilityService() {

    private var clipboardManager: ClipboardManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Tracks the most recently highlighted/selected text across any app on the phone
    @Volatile
    private var latestSelectedText: String? = null

    @Volatile
    private var latestSelectionTimestamp: Long = 0L

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val capturedDirectly = readClipboardAndEnqueue()
        if (!capturedDirectly) {
            val now = SystemClock.elapsedRealtime()
            val selected = latestSelectedText?.trim()
            if (!selected.isNullOrEmpty() && (now - latestSelectionTimestamp) <= 15_000L) {
                ClipboardServiceHelper.getInstance(this).enqueueClip(selected)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
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
                    latestSelectionTimestamp = SystemClock.elapsedRealtime()
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (isCopyActionEvent(event)) {
                    val now = SystemClock.elapsedRealtime()
                    val selected = latestSelectedText?.trim()
                    if (!selected.isNullOrEmpty() && (now - latestSelectionTimestamp) <= 20_000L) {
                        ClipboardServiceHelper.getInstance(this).enqueueClip(selected)
                    }
                    // Also read primaryClip after a short 120ms settle delay once OS updates clipboard
                    serviceScope.launch {
                        delay(120)
                        readClipboardAndEnqueue()
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
                        ClipboardServiceHelper.getInstance(this).enqueueClip(selected)
                    }
                    readClipboardAndEnqueue()
                }
            }
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
     * Reads ClipboardManager.primaryClip directly if permitted by OS and enqueues into ClipboardServiceHelper.
     */
    private fun readClipboardAndEnqueue(): Boolean {
        return try {
            val manager = clipboardManager ?: return false
            if (!manager.hasPrimaryClip()) return false
            val clipData: ClipData = manager.primaryClip ?: return false
            if (clipData.itemCount <= 0) return false

            var foundAny = false
            for (i in 0 until clipData.itemCount) {
                val rawText = clipData.getItemAt(i).coerceToText(this)?.toString()?.trim()
                if (!rawText.isNullOrEmpty()) {
                    ClipboardServiceHelper.getInstance(this).enqueueClip(rawText)
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

    override fun onInterrupt() {
        Log.w(TAG, "ClipAccessibilityService interrupted")
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ClipAccessibilitySvc"
    }
}
