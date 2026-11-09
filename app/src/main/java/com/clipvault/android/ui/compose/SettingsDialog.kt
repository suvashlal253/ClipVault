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
                Text(
                    text = "Clipboard Automation",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 1. Auto-Capture Toggle
                ListItem(
                    headlineContent = { Text("Background Monitoring") },
                    supportingContent = { Text("Runs persistent service to auto-capture clips without accessibility restrictions.") },
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Security & Privacy",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )

                // 2. Biometric Protection Toggle
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

                // 3. Clear History
                ListItem(
                    modifier = Modifier.clickable { showClearConfirmDialog = true },
                    headlineContent = { Text("Clear Clipboard History", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Delete unpinned clips from local database.") },
                    leadingContent = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )

                // 4. Export History
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

                // 5. About Developer
                ListItem(
                    modifier = Modifier.clickable {
                        onOpenAboutDeveloper()
                        onDismissRequest()
                    },
                    headlineContent = { Text("About Developer") },
                    supportingContent = { Text("Suvash Purkaystha (AI Developer & Vibe Coder)") },
                    leadingContent = { Icon(Icons.Outlined.Person, contentDescription = null) }
                )

                // 6. Privacy Policy
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

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear History?") },
            text = { Text("This will permanently delete all unpinned clips. Vaulted and pinned clips will remain safe.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIntent(ClipboardIntent.ClearUnpinned)
                        showClearConfirmDialog = false
                        Toast.makeText(context, "Unpinned clips cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
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
