package com.clipvault.android.service

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Cleaned up to prevent unnecessary background activity launches.
 * All clipboard capture is handled directly by ClipAccessibilityService
 * and routed through ClipboardServiceHelper.getInstance(context).
 */
class ClipSyncTrampolineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        finish()
        overridePendingTransition(0, 0)
    }
}
