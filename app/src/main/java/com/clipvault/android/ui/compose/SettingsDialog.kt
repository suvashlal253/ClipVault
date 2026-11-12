package com.clipvault.android.ui.compose

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clipvault.android.ClipAccessibilityService
import com.clipvault.android.service.ClipboardForegroundService
import com.clipvault.android.ui.theme.AppThemeMode
import com.clipvault.android.ui.viewmodel.ClipboardIntent
import com.clipvault.android.util.AccessibilityPermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismissRequest: () -> Unit,
    onIntent: (ClipboardIntent) -> Unit,
    currentThemeMode: AppThemeMode = AppThemeMode.DARK,
    onThemeChanged: (AppThemeMode) -> Unit = {},
    onOpenAboutDeveloper: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onExportClips: () -> Unit,
    onBackupToCloud: () -> Unit = {},
    onRestoreFromCloud: () -> Unit = {}
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

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val dialogBg = if (isDark) Color(0xFF0F1626) else Color(0xFFF8F9FA)
    val dialogBorder = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val sectionCardBg = if (isDark) Color(0xFF161F33) else Color(0xFFFFFFFF)
    val sectionCardBorder = if (isDark) Color(0xFF222F49) else Color(0xFFE2E8F0)

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF64748B),
        uncheckedTrackColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFE2E8F0)
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 650.dp),
            shape = RoundedCornerShape(24.dp),
            color = dialogBg,
            border = BorderStroke(1.dp, dialogBorder),
            tonalElevation = if (isDark) 8.dp else 4.dp,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Clean, Modern Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "ClipVault Settings",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Appearance, cloud backup & vault security",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFEFF2F6))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = if (isDark) Color(0xFF1E293B) else Color(0xFFE4E7EC))

                // Scrollable Section Cards
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // SECTION 1: APPEARANCE
                    SettingsSectionCard(title = "APPEARANCE") {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Theme Mode",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Switch seamlessly between dark, light, or system theme",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDark) Color(0xFF0B0F19) else Color(0xFFF1F5F9))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val modes = listOf(
                                    Triple(AppThemeMode.SYSTEM, "System", Icons.Outlined.BrightnessAuto),
                                    Triple(AppThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
                                    Triple(AppThemeMode.DARK, "Dark", Icons.Outlined.DarkMode)
                                )
                                modes.forEach { (mode, label, icon) ->
                                    val isSelected = currentThemeMode == mode
                                    Surface(
                                        onClick = {
                                            sharedPrefs.edit().putString("app_theme_mode", mode.name).apply()
                                            onThemeChanged(mode)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 12.sp
                                                ),
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 2: CLIPBOARD AUTOMATION
                    SettingsSectionCard(title = "CLIPBOARD AUTOMATION") {
                        SettingsToggleItem(
                            icon = Icons.Outlined.Bolt,
                            iconTint = if (isDark) Color(0xFF10B981) else Color(0xFF059669),
                            title = "Instant Background Capture",
                            subtitle = "Zero-delay auto-capture from WhatsApp, Facebook & all apps",
                            checked = autoCaptureEnabled,
                            switchColors = switchColors,
                            onCheckedChange = { isChecked ->
                                autoCaptureEnabled = isChecked
                                sharedPrefs.edit().putBoolean("auto_capture_enabled", isChecked).apply()
                                if (isChecked) {
                                    ClipboardForegroundService.start(context)
                                    Toast.makeText(context, "Instant background capture active", Toast.LENGTH_SHORT).show()
                                } else {
                                    ClipboardForegroundService.stop(context)
                                    Toast.makeText(context, "Background capture paused", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        SectionDivider()
                        val isA11yEnabled = AccessibilityPermissionHelper.isAccessibilityServiceEnabled(
                            context,
                            ClipAccessibilityService::class.java
                        )
                        SettingsActionItem(
                            icon = Icons.Outlined.AccessibilityNew,
                            iconTint = if (isA11yEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                            title = "Accessibility Background Capture",
                            subtitle = if (isA11yEnabled) {
                                "Active · Capturing copied text globally in background"
                            } else {
                                "Tap to enable ClipVault in Android Accessibility Settings"
                            },
                            onClick = {
                                AccessibilityPermissionHelper.openAccessibilitySettings(context)
                            }
                        )
                        SectionDivider()
                        SettingsActionItem(
                            icon = Icons.Outlined.TouchApp,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Quick Access & Floating Tile Guide",
                            subtitle = "Configure Notification Sync & Quick Settings Tile",
                            onClick = { showQuickAccessGuide = true }
                        )
                    }

                    // SECTION 3: SECURITY
                    SettingsSectionCard(title = "SECURITY") {
                        SettingsToggleItem(
                            icon = Icons.Outlined.Fingerprint,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Biometric Vault Lock",
                            subtitle = "Require fingerprint, face, or PIN to open Encrypted Vault",
                            checked = biometricLockEnabled,
                            switchColors = switchColors,
                            onCheckedChange = { isChecked ->
                                biometricLockEnabled = isChecked
                                sharedPrefs.edit().putBoolean("biometric_lock_enabled", isChecked).apply()
                            }
                        )
                    }

                    // SECTION 4: CLOUD BACKUP & DATA MANAGEMENT
                    SettingsSectionCard(title = "CLOUD BACKUP & DATA MANAGEMENT") {
                        SettingsActionItem(
                            icon = Icons.Outlined.CloudUpload,
                            iconTint = if (isDark) Color(0xFF10B981) else Color(0xFF059669),
                            title = "Cloud Backup (Google Drive / JSON)",
                            subtitle = "Backup encrypted & normal clips to Google Drive or local file",
                            onClick = {
                                onBackupToCloud()
                                onDismissRequest()
                            }
                        )
                        SectionDivider()
                        SettingsActionItem(
                            icon = Icons.Outlined.CloudDownload,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Restore Backup (Google Drive / JSON)",
                            subtitle = "Import & merge clips from a ClipVault JSON backup file",
                            onClick = {
                                onRestoreFromCloud()
                                onDismissRequest()
                            }
                        )
                        SectionDivider()
                        SettingsActionItem(
                            icon = Icons.Outlined.IosShare,
                            iconTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            title = "Export / Share Clips as Text",
                            subtitle = "Share all saved clips as plain text via Android Share Sheet",
                            onClick = {
                                onExportClips()
                                onDismissRequest()
                            }
                        )
                        SectionDivider()
                        SettingsActionItem(
                            icon = Icons.Outlined.DeleteForever,
                            iconTint = MaterialTheme.colorScheme.error,
                            title = "Clear All History",
                            titleColor = MaterialTheme.colorScheme.error,
                            subtitle = "Delete unpinned clips and reset active search filters",
                            onClick = { showClearConfirmDialog = true }
                        )
                    }

                    // SECTION 5: ABOUT
                    SettingsSectionCard(title = "ABOUT") {
                        SettingsActionItem(
                            icon = Icons.Outlined.Person,
                            iconTint = if (isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9),
                            title = "About Developer",
                            subtitle = "Suvash Purkaystha · AI Developer & Vibe Coder",
                            onClick = {
                                onOpenAboutDeveloper()
                                onDismissRequest()
                            }
                        )
                        SectionDivider()
                        SettingsActionItem(
                            icon = Icons.Outlined.VerifiedUser,
                            iconTint = if (isDark) Color(0xFF10B981) else Color(0xFF059669),
                            title = "Privacy Policy",
                            subtitle = "100% offline Room SQLite storage · Zero telemetry",
                            onClick = {
                                onOpenPrivacyPolicy()
                                onDismissRequest()
                            }
                        )
                    }
                }

                HorizontalDivider(color = if (isDark) Color(0xFF1E293B) else Color(0xFFE4E7EC))

                // Footer Button Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    // Quick Access Guide Dialog
    if (showQuickAccessGuide) {
        AlertDialog(
            onDismissRequest = { showQuickAccessGuide = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Quick Access Guide",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                        text = "1. Instant Background Capture (WhatsApp / Facebook)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ClipVault's Foreground Service & Accessibility observer automatically capture copied text from WhatsApp, Facebook, Chrome, and any app with zero delay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "2. Notification Bar 1-Tap Sync",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Pull down your notification drawer and tap 'Sync Clip Now' to force an immediate clipboard read at any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "3. Quick Settings Tile",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Add the 'Clip Capture' tile to your Android Quick Settings panel to save clips with a single tap from anywhere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showQuickAccessGuide = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Got It")
                }
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = dialogBg,
            title = {
                Text(
                    "Clear All History?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete all history? This action cannot be undone. Encrypted Vault items remain protected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Purge system clipboard cache first so the last copied clip never lingers or reappears
                        try {
                            val sysClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                sysClipboard?.clearPrimaryClip()
                            } else {
                                sysClipboard?.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                            }
                        } catch (_: Exception) {}

                        onIntent(ClipboardIntent.ClearAllHistory)
                        showClearConfirmDialog = false
                        onDismissRequest()
                        Toast.makeText(context, "All history cleared", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearConfirmDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isDark) Color.Transparent else Color.White,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) Color(0xFF161F33) else Color(0xFFFFFFFF),
            shadowElevation = if (isDark) 0.dp else 1.5.dp,
            border = BorderStroke(1.dp, if (isDark) Color(0xFF222F49) else Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun SectionDivider() {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = if (isDark) Color(0xFF222F49) else Color(0xFFE4E7EC)
    )
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    switchColors: SwitchColors,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = switchColors,
            modifier = Modifier.scale(0.86f)
        )
    }
}

@Composable
private fun SettingsActionItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                ),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
    }
}
