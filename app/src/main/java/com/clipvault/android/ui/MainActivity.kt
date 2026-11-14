package com.clipvault.android.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.clipvault.android.ClipAccessibilityService
import com.clipvault.android.ClipMonitorService
import com.clipvault.android.security.BiometricAuthManager
import com.clipvault.android.service.ClipCaptureTileService
import com.clipvault.android.service.ClipboardServiceHelper
import com.clipvault.android.ui.compose.*
import com.clipvault.android.ui.theme.ClipVaultTheme
import com.clipvault.android.ui.viewmodel.ClipboardIntent
import com.clipvault.android.ui.viewmodel.ClipboardViewModel
import com.clipvault.android.util.AccessibilityPermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CurrentScreen {
    CLIPBOARD,
    VAULT
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: ClipboardViewModel by viewModels()
    private lateinit var biometricAuthManager: BiometricAuthManager
    private lateinit var clipboardManager: ClipboardManager
    private var currentScreen by mutableStateOf(CurrentScreen.CLIPBOARD)
    private var isAccessibilityActive by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Start or refresh the persistent START_STICKY ClipMonitorService once user responds
        startClipMonitorService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biometricAuthManager = BiometricAuthManager(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        isAccessibilityActive = AccessibilityPermissionHelper.isAccessibilityServiceEnabled(
            this,
            ClipAccessibilityService::class.java
        )

        // 1. Runtime check & request for POST_NOTIFICATIONS on Android 13+ (API 33+) and start ClipMonitorService
        requestNotificationPermissionAndStartService()

        // 2. Prompt once for battery optimization exemption so START_STICKY service is never killed
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("battery_opt_prompted", false) &&
            !AccessibilityPermissionHelper.isIgnoringBatteryOptimizations(this)
        ) {
            prefs.edit().putBoolean("battery_opt_prompted", true).apply()
            AccessibilityPermissionHelper.requestIgnoreBatteryOptimizations(this)
        }

        // Handle explicit Quick Settings Tile tap if triggered by user from status bar
        if (intent?.action == ClipCaptureTileService.ACTION_QUICK_TILE_OPEN) {
            enqueueFromQuickTile()
        }

        setContent {
            var currentThemeMode by remember {
                val modeName = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE).getString("app_theme_mode", "DARK")
                mutableStateOf(try { com.clipvault.android.ui.theme.AppThemeMode.valueOf(modeName ?: "DARK") } catch (_: Exception) { com.clipvault.android.ui.theme.AppThemeMode.DARK })
            }

            // Feature 1: Cloud Backup (Google Drive / SAF Document Provider) Export Launcher
            val backupDocumentLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json")
            ) { uri: Uri? ->
                if (uri != null) {
                    viewModel.exportBackupJson(
                        onSuccess = { jsonPayload ->
                            try {
                                contentResolver.openOutputStream(uri)?.use { outputStream ->
                                    outputStream.write(jsonPayload.toByteArray(Charsets.UTF_8))
                                }
                                Toast.makeText(
                                    this@MainActivity,
                                    "Cloud Backup saved successfully!",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Backup write failed: ${e.localizedMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onError = { errMsg ->
                            Toast.makeText(this@MainActivity, errMsg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }

            // Feature 1: Cloud Restore (Google Drive / SAF Document Provider) Import Launcher
            val restoreDocumentLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    try {
                        val jsonBuilder = StringBuilder()
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    jsonBuilder.append(line).append("\n")
                                }
                            }
                        }
                        viewModel.importBackupJson(
                            jsonPayload = jsonBuilder.toString(),
                            onSuccess = { restoredCount ->
                                Toast.makeText(
                                    this@MainActivity,
                                    "Restored $restoredCount clips from Cloud Backup!",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onError = { errMsg ->
                                Toast.makeText(this@MainActivity, errMsg, Toast.LENGTH_LONG).show()
                            }
                        )
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "Restore read failed: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            ClipVaultTheme(themeMode = currentThemeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.uiState.collectAsState()

                    var showSettingsDialog by remember { mutableStateOf(false) }
                    var showAboutDialog by remember { mutableStateOf(false) }
                    var showPrivacyDialog by remember { mutableStateOf(false) }

                    fun requestVaultAccess() {
                        if (state.isVaultUnlocked) {
                            currentScreen = CurrentScreen.VAULT
                        } else {
                            biometricAuthManager.promptBiometric(
                                activity = this@MainActivity,
                                title = "Unlock Secure Vault",
                                subtitle = "Authenticate using fingerprint, face, or device PIN",
                                onSuccess = {
                                    viewModel.onIntent(ClipboardIntent.UnlockVault)
                                    currentScreen = CurrentScreen.VAULT
                                },
                                onError = { errMsg ->
                                    Toast.makeText(this@MainActivity, errMsg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    when (currentScreen) {
                        CurrentScreen.CLIPBOARD -> {
                            ClipboardScreen(
                                uiState = state,
                                isAccessibilityActive = isAccessibilityActive,
                                onIntent = viewModel::onIntent,
                                onRequestEnableAccessibility = {
                                    AccessibilityPermissionHelper.openAccessibilitySettings(this@MainActivity)
                                },
                                onOpenVault = { requestVaultAccess() },
                                onOpenSettings = { showSettingsDialog = true },
                                onOpenAboutDeveloper = { showAboutDialog = true },
                                onOpenPrivacyPolicy = { showPrivacyDialog = true }
                            )
                        }
                        CurrentScreen.VAULT -> {
                            VaultScreen(
                                uiState = state,
                                onIntent = viewModel::onIntent,
                                onRequestUnlock = { requestVaultAccess() },
                                onNavigateBack = { currentScreen = CurrentScreen.CLIPBOARD }
                            )
                        }
                    }

                    if (showSettingsDialog) {
                        SettingsDialog(
                            onDismissRequest = { showSettingsDialog = false },
                            onIntent = viewModel::onIntent,
                            currentThemeMode = currentThemeMode,
                            onThemeChanged = { newMode -> currentThemeMode = newMode },
                            onOpenAboutDeveloper = { showAboutDialog = true },
                            onOpenPrivacyPolicy = { showPrivacyDialog = true },
                            onExportClips = {
                                exportAllClips(state.clips.map { it.content })
                            },
                            onBackupToCloud = {
                                val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                backupDocumentLauncher.launch("ClipVault-Backup-$dateStamp.json")
                            },
                            onRestoreFromCloud = {
                                restoreDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            }
                        )
                    }

                    if (showAboutDialog) {
                        AboutDeveloperDialog(onDismissRequest = { showAboutDialog = false })
                    }

                    if (showPrivacyDialog) {
                        PrivacyDisclosureDialog(
                            onAccept = { showPrivacyDialog = false },
                            onDecline = { showPrivacyDialog = false }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ClipCaptureTileService.ACTION_QUICK_TILE_OPEN) {
            enqueueFromQuickTile()
        }
    }

    override fun onResume() {
        super.onResume()
        isAccessibilityActive = AccessibilityPermissionHelper.isAccessibilityServiceEnabled(
            this,
            ClipAccessibilityService::class.java
        )
    }

    private fun enqueueFromQuickTile() {
        if (currentScreen == CurrentScreen.VAULT) return
        try {
            if (clipboardManager.hasPrimaryClip()) {
                val clip: ClipData? = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    for (i in 0 until clip.itemCount) {
                        val text = clip.getItemAt(i).coerceToText(this)?.toString()?.trim()
                        if (!text.isNullOrEmpty()) {
                            ClipboardServiceHelper.getInstance(this).enqueueClip(text)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Handled gracefully
        }
    }

    private fun exportAllClips(clipContents: List<String>) {
        if (clipContents.isEmpty()) {
            Toast.makeText(this, "No clips to export", Toast.LENGTH_SHORT).show()
            return
        }
        val exportText = clipContents.joinToString(separator = "\n\n---\n\n")
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, exportText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export ClipVault Clips")
        startActivity(shareIntent)
    }

    /**
     * Smoothly checks and requests POST_NOTIFICATIONS on Android 13+ (TIRAMISU)
     * and starts the persistent START_STICKY ClipMonitorService.
     */
    private fun requestNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        startClipMonitorService()
    }

    private fun startClipMonitorService() {
        val serviceIntent = Intent(this, ClipMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
