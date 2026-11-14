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
import com.clipvault.android.ime.ClipVaultInputMethodService
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
    private var openSettingsRequested by mutableStateOf(false)

    private val foregroundClipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (hasWindowFocus() && currentScreen == CurrentScreen.CLIPBOARD) {
            syncForegroundClipboard("Foreground Sync")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
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

        // Handle explicit Quick Settings Tile tap or Notification Import Clip action
        if (intent?.action == ClipCaptureTileService.ACTION_QUICK_TILE_OPEN ||
            intent?.action == ClipMonitorService.ACTION_IMPORT_CLIPBOARD
        ) {
            syncForegroundClipboard("Imported Clip")
        }

        // Handle Keyboard Shortcut long-press or Keyboard Settings toolbar button
        if (intent?.getBooleanExtra(ClipVaultInputMethodService.EXTRA_OPEN_CLIPVAULT_SETTINGS, false) == true) {
            openSettingsRequested = true
        }

        setContent {
            var currentThemeMode by remember {
                val modeName = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE).getString("app_theme_mode", "DARK")
                mutableStateOf(
                    try {
                        com.clipvault.android.ui.theme.AppThemeMode.valueOf(modeName ?: "DARK")
                    } catch (_: Exception) {
                        com.clipvault.android.ui.theme.AppThemeMode.DARK
                    }
                )
            }

            // Cloud Backup (Google Drive / SAF Document Provider) Export Launcher
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
                                    "Backup write failed: " + (e.localizedMessage ?: "error"),
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

            // Cloud Restore (Google Drive / SAF Document Provider) Import Launcher
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
                                    "Restored " + restoredCount + " clips from Cloud Backup!",
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
                            "Restore read failed: " + (e.localizedMessage ?: "error"),
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

                    LaunchedEffect(openSettingsRequested) {
                        if (openSettingsRequested) {
                            showSettingsDialog = true
                            openSettingsRequested = false
                        }
                    }

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
                                backupDocumentLauncher.launch("ClipVault-Backup-" + dateStamp + ".json")
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
        if (intent.action == ClipCaptureTileService.ACTION_QUICK_TILE_OPEN ||
            intent.action == ClipMonitorService.ACTION_IMPORT_CLIPBOARD
        ) {
            syncForegroundClipboard("Imported Clip")
            Toast.makeText(this, "Clipboard synced to ClipVault", Toast.LENGTH_SHORT).show()
        }
        if (intent.getBooleanExtra(ClipVaultInputMethodService.EXTRA_OPEN_CLIPVAULT_SETTINGS, false)) {
            openSettingsRequested = true
        }
    }

    override fun onResume() {
        super.onResume()
        isAccessibilityActive = AccessibilityPermissionHelper.isAccessibilityServiceEnabled(
            this,
            ClipAccessibilityService::class.java
        )
        try {
            clipboardManager.addPrimaryClipChangedListener(foregroundClipListener)
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        try {
            clipboardManager.removePrimaryClipChangedListener(foregroundClipListener)
        } catch (_: Exception) {
        }
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && currentScreen == CurrentScreen.CLIPBOARD) {
            // Legitimate Android 10+ foreground clipboard read when user opens/focuses ClipVault while using Gboard or any keyboard
            syncForegroundClipboard("Foreground Sync")
        }
    }

    private fun syncForegroundClipboard(sourceLabel: String = "Foreground Sync") {
        if (currentScreen == CurrentScreen.VAULT) return
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_capture_enabled", true)) return
        try {
            if (clipboardManager.hasPrimaryClip()) {
                val clip: ClipData? = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    for (i in 0 until clip.itemCount) {
                        val text = clip.getItemAt(i).coerceToText(this)?.toString()?.trim()
                        if (!text.isNullOrEmpty()) {
                            ClipboardServiceHelper.getInstance(this).enqueueClip(text, sourceLabel)
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
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
