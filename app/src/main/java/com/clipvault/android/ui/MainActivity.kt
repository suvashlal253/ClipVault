package com.clipvault.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.clipvault.android.security.BiometricAuthManager
import com.clipvault.android.service.ClipboardAccessibilityService
import com.clipvault.android.service.ClipboardForegroundService
import com.clipvault.android.ui.compose.AboutDeveloperDialog
import com.clipvault.android.ui.compose.ClipboardScreen
import com.clipvault.android.ui.compose.PrivacyDisclosureDialog
import com.clipvault.android.ui.viewmodel.ClipboardIntent
import com.clipvault.android.ui.viewmodel.ClipboardViewModel
import com.clipvault.android.ui.theme.ClipVaultTheme
import com.clipvault.android.util.AccessibilityPermissionHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: ClipboardViewModel by viewModels()
    private lateinit var biometricAuthManager: BiometricAuthManager
    private lateinit var clipboardManager: ClipboardManager

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureForegroundClipboard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biometricAuthManager = BiometricAuthManager(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // Start background service
        ClipboardForegroundService.start(this)

        setContent {
            ClipVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.uiState.collectAsState()

                    var showAboutDialog by remember { mutableStateOf(false) }
                    var showPrivacyDialog by remember { mutableStateOf(false) }
                    var isAccessibilityActive by remember {
                        mutableStateOf(AccessibilityPermissionHelper.isAccessibilityServiceEnabled(this, ClipboardAccessibilityService::class.java))
                    }

                    ClipboardScreen(
                        uiState = state,
                        isAccessibilityActive = isAccessibilityActive,
                        onIntent = viewModel::onIntent,
                        onRequestEnableAccessibility = {
                            AccessibilityPermissionHelper.openAccessibilitySettings(this)
                        },
                        onOpenVault = {
                            if (state.isVaultUnlocked) {
                                viewModel.onIntent(ClipboardIntent.LockVault)
                            } else {
                                biometricAuthManager.promptBiometric(
                                    activity = this,
                                    onSuccess = { viewModel.onIntent(ClipboardIntent.UnlockVault) },
                                    onError = { /* Biometric cancelled */ }
                                )
                            }
                        },
                        onOpenSettings = {
                            AccessibilityPermissionHelper.openAccessibilitySettings(this)
                        },
                        onOpenAboutDeveloper = {
                            showAboutDialog = true
                        },
                        onOpenPrivacyPolicy = {
                            showPrivacyDialog = true
                        }
                    )

                    if (showAboutDialog) {
                        AboutDeveloperDialog(onDismissRequest = { showAboutDialog = false })
                    }

                    if (showPrivacyDialog) {
                        PrivacyDisclosureDialog(
                            onAccept = {
                                showPrivacyDialog = false
                                AccessibilityPermissionHelper.openAccessibilitySettings(this)
                            },
                            onDecline = { showPrivacyDialog = false }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        captureForegroundClipboard()
    }

    override fun onPause() {
        super.onPause()
        clipboardManager.removePrimaryClipChangedListener(clipListener)
    }

    private fun captureForegroundClipboard() {
        try {
            if (clipboardManager.hasPrimaryClip()) {
                val clip: ClipData? = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
                    if (!text.isNullOrEmpty()) {
                        viewModel.onIntent(ClipboardIntent.SaveNewClip(text, "Foreground / Direct"))
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore background reading restrictions
        }
    }
}
