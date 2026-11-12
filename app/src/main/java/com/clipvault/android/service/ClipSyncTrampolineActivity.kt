package com.clipvault.android.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import com.clipvault.android.ClipMonitorService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ClipSyncTrampolineActivity : ComponentActivity() {

    @Inject
    lateinit var clipboardServiceHelper: ClipboardServiceHelper

    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasCaptured = false

    private val safetyFinishRunnable = Runnable {
        if (!hasCaptured) {
            tryReadClipboard()
            finishQuietly()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        // Safety fallback in case screen is off and window focus is delayed
        mainHandler.postDelayed(safetyFinishRunnable, 450L)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        hasCaptured = false
        if (hasWindowFocus()) {
            captureAndFinishWhenFocused()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasCaptured) {
            // Post 1 frame after focus is granted so Android 10+ ClipboardService registers focus
            mainHandler.post {
                captureAndFinishWhenFocused()
            }
        }
    }

    private fun captureAndFinishWhenFocused() {
        if (hasCaptured) return
        val captured = tryReadClipboard()
        if (captured) {
            hasCaptured = true
            mainHandler.removeCallbacks(safetyFinishRunnable)
            finishQuietly()
        } else {
            // Retry once 50ms later while still holding window focus
            mainHandler.postDelayed({
                if (!hasCaptured) {
                    hasCaptured = true
                    tryReadClipboard()
                    mainHandler.removeCallbacks(safetyFinishRunnable)
                    finishQuietly()
                }
            }, 50L)
        }
    }

    private fun tryReadClipboard(): Boolean {
        var foundText = false
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clip: ClipData? = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    for (i in 0 until clip.itemCount) {
                        val item = clip.getItemAt(i)
                        val rawText = item?.text?.toString() ?: item?.coerceToText(this)?.toString()
                        if (!rawText.isNullOrBlank()) {
                            foundText = true
                            ClipMonitorService.enqueueFromFocusReader(applicationContext, rawText)
                            clipboardServiceHelper.onTextCopied(
                                rawText = rawText,
                                sourceApp = "Instant Auto-Capture"
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Handled safely
        }
        return foundText
    }

    private fun finishQuietly() {
        if (!isFinishing) {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
