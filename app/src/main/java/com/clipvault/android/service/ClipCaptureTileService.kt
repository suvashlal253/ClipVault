package com.clipvault.android.service

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.clipvault.android.domain.repository.ClipboardRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class ClipCaptureTileService : TileService() {

    @Inject
    lateinit var repository: ClipboardRepository

    override fun onClick() {
        super.onClick()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip

        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this).toString()
            if (text.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    repository.saveNewClip(text, sourceApp = "Quick Settings Tile")
                }
                Toast.makeText(this, "ClipVault: Clip Captured!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
