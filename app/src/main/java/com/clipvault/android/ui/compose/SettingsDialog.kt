package com.clipvault.android.ui.compose

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.clipvault.android.service.ClipboardForegroundService
import com.clipvault.android.ui.viewmodel.ClipboardIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismissRequest: () -> Unit,
    onIntent: (ClipboardIntent) -> Unit,
    currentThemeMode: com.clipvault.android.ui.theme.AppThemeMode = com.clipvault.android.ui.theme.AppThemeMode.SYSTEM,
    onThemeChanged: (com.clipvault.android.ui.theme.AppThemeMode) -> Unit = {},
    onOpenAboutDeveloper: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onExportClips: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE) }
    
    var autoCaptureEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("auto_capture_enabled", true))
    }
    var biometricLockEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("biometric_lock_enabled", true))
    }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showQuickAccessGuide by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text("ClipVault Settings")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Appearance & Theme Switch
                Text(
                    text = "Appearance & Theme",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        Triple(com.clipvault.android.ui.theme.AppThemeMode.SYSTEM, "System", Icons.Outlined.BrightnessAuto),
                        Triple(com.clipvault.android.ui.theme.AppThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
                        Triple(com.clipvault.android.ui.theme.AppThemeMode.DARK, "Dark", Icons.Outlined.DarkMode)
                    )
                    modes.forEach { (mode, label, icon) ->
                        val isSelected = currentThemeMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                sharedPrefs.edit().putString("app_theme_mode", mode.name).apply()
                                onThemeChanged(mode)
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Clipboard Automation",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 2. Auto-Capture Toggle
                ListItem(
                    headlineContent = { Text("Background Monitoring") },
                    supportingContent = { Text("Runs persistent foreground service to capture clips without accessibility blocks.") },
                    trailingContent = {
                        Switch(
                            checked = autoCaptureEnabled,
                            onCheckedChange = { isChecked ->
                                autoCaptureEnabled = isChecked
                                sharedPrefs.edit().putBoolean("auto_capture_enabled", isChecked).apply()
                                if (isChecked) {
                                    ClipboardForegroundService.start(context)
                                    Toast.makeText(context, "Monitoring service started", Toast.LENGTH_SHORT).show()
                                } else {
                                    ClipboardForegroundService.stop(context)
                                    Toast.makeText(context, "Monitoring service stopped", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                )

                // Quick Access Overlay Guide Item
                ListItem(
                    modifier = Modifier.clickable { showQuickAccessGuide = true },
                    headlineContent = { Text("Quick Access & Floating Guide") },
                    supportingContent = { Text("How to use the Quick Settings Tile and Notification bar.") },
                    leadingContent = { Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Security & Privacy",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )

                // 3. Biometric Protection Toggle
                ListItem(
                    headlineContent = { Text("Biometric Lock for Vault") },
                    supportingContent = { Text("Require fingerprint or device PIN to access Vault clips.") },
                    trailingContent = {
                        Switch(
                            checked = biometricLockEnabled,
                            onCheckedChange = { isChecked ->
                                biometricLockEnabled = isChecked
                                sharedPrefs.edit().putBoolean("biometric_lock_enabled", isChecked).apply()
                            }
                        )
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Data Management",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )

                // 4. Clear History
                ListItem(
                    modifier = Modifier.clickable { showClearConfirmDialog = true },
                    headlineContent = { Text("Clear All History", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Permanently delete clips and reset all active search filters.") },
                    leadingContent = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )

                // 5. Export History
                ListItem(
                    modifier = Modifier.clickable {
                        onExportClips()
                        onDismissRequest()
                    },
                    headlineContent = { Text("Export / Share Clips") },
                    supportingContent = { Text("Export all clips as text to share or back up.") },
                    leadingContent = { Icon(Icons.Outlined.Share, contentDescription = null) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "About & Info",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )

                // 6. About Developer
                ListItem(
                    modifier = Modifier.clickable {
                        onOpenAboutDeveloper()
                        onDismissRequest()
                    },
                    headlineContent = { Text("About Developer") },
                    supportingContent = { Text("Suvash Purkaystha (AI Developer & Vibe Coder)") },
                    leadingContent = { Icon(Icons.Outlined.Person, contentDescription = null) }
                )

                // 7. Privacy Policy
                ListItem(
                    modifier = Modifier.clickable {
                        onOpenPrivacyPolicy()
                        onDismissRequest()
                    },
                    headlineContent = { Text("Privacy Policy") },
                    supportingContent = { Text("100% on-device SQLite database with zero telemetry.") },
                    leadingContent = { Icon(Icons.Outlined.PrivacyTip, contentDescription = null) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )

    // Quick Access Guide Dialog
    if (showQuickAccessGuide) {
        AlertDialog(
            onDismissRequest = { showQuickAccessGuide = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Quick Access Guide")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. Notification Bar Quick Action",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "While ClipVault is running, pull down your notification drawer and tap 'Sync Clip Now' to instantly capture the current clipboard content without leaving your current app.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "2. Quick Settings Tile",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Swipe down twice from the top of your screen to open Quick Settings, tap the pencil/edit icon, and drag the 'Clip Capture' tile into your active tiles. Tapping it anytime immediately records your clipboard.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "3. Keyboard Clipboard Sync (Android 12+)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ClipVault works alongside Gboard and Samsung Keyboard's clipboard toolbar. Clips saved in ClipVault are immediately accessible across all input fields.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showQuickAccessGuide = false }) {
                    Text("Got It")
                }
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All History?") },
            text = { Text("This will permanently delete all unpinned clips and reset active search filters. Vaulted clips remain securely encrypted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIntent(ClipboardIntent.ClearAllHistory)
                        showClearConfirmDialog = false
                        Toast.makeText(context, "All history cleared & filters reset", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
