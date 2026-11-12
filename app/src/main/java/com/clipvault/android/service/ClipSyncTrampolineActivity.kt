package com.clipvault.android.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ClipSyncTrampolineActivity : ComponentActivity() {

    @Inject
    lateinit var clipboardServiceHelper: ClipboardServiceHelper

    private var hasExecuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        window?.decorView?.post {
            if (!hasExecuted) {
                hasExecuted = true
                captureClipboardAndFinish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureClipboardSnapshotOnly()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasExecuted) {
            hasExecuted = true
            captureClipboardAndFinish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasExecuted) {
            hasExecuted = true
            captureClipboardAndFinish()
        }
    }

    private fun captureClipboardSnapshotOnly() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clip: ClipData? = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    for (i in 0 until clip.itemCount) {
                        val rawText = clip.getItemAt(i).coerceToText(this)?.toString()
                        if (!rawText.isNullOrBlank()) {
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
    }

    private fun captureClipboardAndFinish() {
        try {
            captureClipboardSnapshotOnly()
        } finally {
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
