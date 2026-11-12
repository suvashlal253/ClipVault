package com.clipvault.android.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Android 10 (API 29)+ restricts background apps from reading clipboard data.
 * An AccessibilityService runs with elevated system capabilities and delegates
 * all clipboard reads to [ClipboardServiceHelper] using a Mutex-locked background scope
 * so rapid consecutive copies are never dropped or overwritten.
 */
@AndroidEntryPoint
class ClipboardAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var clipboardServiceHelper: ClipboardServiceHelper

    private lateinit var clipboardManager: ClipboardManager

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        processCurrentClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        Log.d(TAG, "ClipboardAccessibilityService registered clip listener with Mutex helper")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

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
    }

    /**
     * Reads the current ClipData snapshot synchronously on the event thread and passes
     * it to [ClipboardServiceHelper.onTextCopied] so rapid consecutive copies are queued
     * and inserted sequentially under a Mutex lock.
     */
    private fun processCurrentClipboard(sourcePackage: String? = null) {
        try {
            if (!clipboardManager.hasPrimaryClip()) return

            val clipData: ClipData? = clipboardManager.primaryClip
            if (clipData == null || clipData.itemCount == 0) return

            for (i in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(i)
                val rawText = item.coerceToText(this)?.toString() ?: continue
                clipboardServiceHelper.onTextCopied(
                    rawText = rawText,
                    sourceApp = sourcePackage ?: "Accessibility Auto-Capture"
                )
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
