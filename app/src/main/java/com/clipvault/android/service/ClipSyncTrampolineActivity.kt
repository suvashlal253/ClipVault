package com.clipvault.android.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.clipvault.android.domain.repository.ClipboardRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ClipSyncTrampolineActivity : ComponentActivity() {

    @Inject
    lateinit var repository: ClipboardRepository

    private var hasExecuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        // Post directly to decorView so clipboard is read the microsecond window attaches
        window?.decorView?.post {
            if (!hasExecuted) {
                hasExecuted = true
                captureClipboardAndFinish()
            }
        }
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

    private fun captureClipboardAndFinish() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clip: ClipData? = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
                    if (!text.isNullOrEmpty()) {
                        CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch(kotlinx.coroutines.NonCancellable) {
                            repository.saveNewClip(content = text, sourceApp = "Instant Auto-Capture")
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Handled safely
        } finally {
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
