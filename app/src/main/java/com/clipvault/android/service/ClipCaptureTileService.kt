package com.clipvault.android.service

import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.clipvault.android.domain.repository.ClipboardRepository
import com.clipvault.android.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class ClipCaptureTileService : TileService() {

    @Inject
    lateinit var repository: ClipboardRepository

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "ClipVault"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "Sync & Open"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // 1. Attempt immediate clipboard capture if OS grants focus to the active Tile
        runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    for (i in 0 until clip.itemCount) {
                        val text = clip.getItemAt(i)?.coerceToText(this)?.toString()?.trim()
                        if (!text.isNullOrBlank()) {
                            tileScope.launch {
                                repository.saveNewClip(text, sourceApp = "Quick Settings Tile")
                            }
                        }
                    }
                    Toast.makeText(this, "ClipVault: Clipboard Synced!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 2. Collapse notification shade and launch ClipVault MainActivity with quick-sync flag
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_QUICK_TILE_OPEN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    204,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(launchIntent)
            }
        }
    }

    companion object {
        const val ACTION_QUICK_TILE_OPEN = "com.clipvault.android.action.QUICK_TILE_OPEN"
    }
}
