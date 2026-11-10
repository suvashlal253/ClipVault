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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Android 10 (API 29)+ restricts background apps from reading clipboard data.
 * An AccessibilityService runs with elevated system capabilities and can access
 * ClipboardManager whenever copy events or text selections happen in other apps.
 */
@AndroidEntryPoint
class ClipboardAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var clipboardRepository: ClipboardRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var clipboardManager: ClipboardManager
    private var lastCapturedText: String? = null
    private var lastCaptureTime: Long = 0

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        processCurrentClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        Log.d(TAG, "ClipboardAccessibilityService registered clip listener")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Instant capture on text selection, copy button click, or "Copied" toast/snackbar in WhatsApp & Facebook
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val now = System.currentTimeMillis()
                if (now - lastCaptureTime > 60) {
                    processCurrentClipboard(event.packageName?.toString())
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "ClipboardAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
    }

    private fun processCurrentClipboard(sourcePackage: String? = null) {
        try {
            if (!clipboardManager.hasPrimaryClip()) return

            val clipData: ClipData? = clipboardManager.primaryClip
            if (clipData == null || clipData.itemCount == 0) return

            val item = clipData.getItemAt(0)
            val text = item.coerceToText(this)?.toString()?.trim() ?: return

            if (text.isEmpty()) return

            // Fast deduplication guard (800ms) for identical consecutive system events
            val now = System.currentTimeMillis()
            if (text == lastCapturedText && (now - lastCaptureTime) < 800) {
                return
            }

            lastCapturedText = text
            lastCaptureTime = now

            // Default Home Dashboard Behavior:
            // All newly copied items (including Bengali, English, URLs, Emails, etc.) are saved
            // strictly to the main Home Dashboard by default (autoVault = false).
            // Items only move to the Secure Vault when the user explicitly clicks the Lock/Vault icon.
            serviceScope.launch {
                clipboardRepository.saveNewClip(
                    content = text,
                    sourceApp = sourcePackage,
                    autoVault = false
                )
                Log.d(TAG, "Captured clip to Home Dashboard: ${text.take(20)}...")
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
