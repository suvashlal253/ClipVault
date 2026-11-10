package com.clipvault.android.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Clean Material 3 Privacy Policy & Prominent Disclosure Dialog
 * Supports both Dark Mode (#0F1626 & #161F33) and Crisp Light Mode (#F8F9FA & #FFFFFF).
 */
@Composable
fun PrivacyDisclosureDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val dialogBg = if (isDark) Color(0xFF0F1626) else Color(0xFFF8F9FA)
    val dialogBorder = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val cardBorder = if (isDark) Color(0xFF222F49) else Color(0xFFE2E8F0)
    val textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1A1C1E)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF44474F)
    val closeBtnBg = if (isDark) Color(0xFF1E293B) else Color(0xFFEFF2F6)
    val closeBtnTint = if (isDark) Color(0xFFCBD5E1) else Color(0xFF1A1C1E)
    val dividerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE4E7EC)
    val primaryAccent = if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5)

    Dialog(
        onDismissRequest = onDecline,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 650.dp),
            shape = RoundedCornerShape(26.dp),
            color = dialogBg,
            tonalElevation = if (isDark) 10.dp else 4.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, dialogBorder)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Bar matching SettingsDialog
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Privacy Policy & Consent",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textPrimary,
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            text = "Google Play Policy & Accessibility Disclosure",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(closeBtnBg)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = closeBtnTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = dividerColor)

                // Scrollable Section Cards
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. OVERVIEW SECTION
                    PrivacySectionCard(title = "CLIPBOARD ACCESS DISCLOSURE", isDark = isDark) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Why ClipVault Reads Your Clipboard",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ClipVault uses Android's ClipboardManager and Foreground Service solely to capture text you copy across apps and organize it into your searchable local history.",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = textSecondary
                            )
                        }
                    }

                    // 2. CORE PRIVACY GUARANTEES SECTION
                    PrivacySectionCard(title = "SECURITY & PRIVACY GUARANTEES", isDark = isDark) {
                        PrivacyGuaranteeRow(
                            icon = Icons.Outlined.CloudOff,
                            iconTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            title = "100% Local On-Device Storage",
                            description = "All clips are stored strictly in an offline Room SQLite database on your phone. Nothing is ever uploaded to any cloud server.",
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = cardBorder
                        )
                        PrivacyGuaranteeRow(
                            icon = Icons.Default.Lock,
                            iconTint = primaryAccent,
                            title = "Hardware AES-256 Encryption",
                            description = "Sensitive clips, passwords, and vaulted items are encrypted using Android Keystore AES-256 GCM and locked with Biometrics.",
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = cardBorder
                        )
                        PrivacyGuaranteeRow(
                            icon = Icons.Outlined.CheckCircle,
                            iconTint = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                            title = "Zero Telemetry & No Ad Tracking",
                            description = "ClipVault contains no analytics SDKs, no trackers, and never shares or monetizes your data with third parties.",
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }

                    // 3. USER CONTROL SECTION
                    PrivacySectionCard(title = "YOUR DATA CONTROL", isDark = isDark) {
                        PrivacyGuaranteeRow(
                            icon = Icons.Outlined.VerifiedUser,
                            iconTint = if (isDark) Color(0xFFA5B4FC) else Color(0xFF6D28D9),
                            title = "Full Deletion & Export Control",
                            description = "You can delete individual clips, wipe unpinned history, or pause background clipboard capture at any time from Settings.",
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }
                }

                HorizontalDivider(color = dividerColor)

                // Bottom Action Buttons matching SettingsDialog footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isDark) Color.Transparent else Color.White,
                            contentColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF1A1C1E)
                        ),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = "Close",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryAccent,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = "Agree & Continue",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacySectionCard(
    title: String,
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
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
private fun PrivacyGuaranteeRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.14f)),
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
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.5.sp,
                color = textSecondary
            )
        }
    }
}
