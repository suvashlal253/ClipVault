package com.clipvault.android.ui.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Clean Material 3 About Developer Dialog matching the ClipVault Settings Dialog
 * Supports both Dark Mode (#0F1626 & #161F33) and Crisp Light Mode (#F8F9FA & #FFFFFF).
 */
@Composable
fun AboutDeveloperDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val dialogBg = if (isDark) Color(0xFF0F1626) else Color(0xFFF8F9FA)
    val dialogBorder = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val cardBg = if (isDark) Color(0xFF161F33) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF222F49) else Color(0xFFE2E8F0)
    val textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1A1C1E)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF44474F)
    val closeBtnBg = if (isDark) Color(0xFF1E293B) else Color(0xFFEFF2F6)
    val closeBtnTint = if (isDark) Color(0xFFCBD5E1) else Color(0xFF1A1C1E)
    val dividerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE4E7EC)
    val primaryAccent = if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(26.dp),
            color = dialogBg,
            tonalElevation = if (isDark) 10.dp else 4.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, dialogBorder)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Dialog Header matching SettingsDialog
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "About Developer",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textPrimary,
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            text = "Creator profile, open-source links & build info",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                    IconButton(
                        onClick = onDismissRequest,
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
                    // 1. DEVELOPER PROFILE SECTION
                    AboutSectionCard(title = "DEVELOPER PROFILE", isDark = isDark) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Glowing Avatar Badge
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    primaryAccent.copy(alpha = if (isDark) 0.28f else 0.16f),
                                                    Color(0xFF38BDF8).copy(alpha = 0.18f)
                                                )
                                            )
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            brush = Brush.linearGradient(
                                                colors = listOf(Color(0xFF38BDF8), primaryAccent)
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "SP",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isDark) Color(0xFFF8FAFC) else primaryAccent
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Suvash Purkaystha",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = primaryAccent.copy(alpha = if (isDark) 0.18f else 0.12f),
                                        border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "AI Developer & Vibe Coder",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color(0xFFA5B4FC) else primaryAccent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = cardBorder)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Passionate creator building smart, privacy-first, user-centric Android applications with modern Jetpack Compose architecture.",
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                color = textSecondary
                            )
                        }
                    }

                    // 2. CONNECT & SOCIAL LINKS SECTION
                    AboutSectionCard(title = "CONNECT & CHANNELS", isDark = isDark) {
                        AboutContactRow(
                            icon = Icons.Outlined.Email,
                            iconTint = primaryAccent,
                            title = "Email Developer",
                            subtitle = "suvashlal253@gmail.com",
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:suvashlal253@gmail.com"))
                                context.startActivity(intent)
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = cardBorder
                        )
                        AboutContactRow(
                            icon = Icons.Outlined.Code,
                            iconTint = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                            title = "GitHub Repository",
                            subtitle = "github.com/suvashlal253/ClipVault",
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/suvashlal253/ClipVault"))
                                context.startActivity(intent)
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = cardBorder
                        )
                        AboutContactRow(
                            icon = Icons.Outlined.Language,
                            iconTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            title = "Developer Portfolio",
                            subtitle = "https://suvash.dev",
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://suvash.dev"))
                                context.startActivity(intent)
                            }
                        )
                    }

                    // 3. APPLICATION & ARCHITECTURE INFO SECTION
                    AboutSectionCard(title = "APPLICATION BUILD", isDark = isDark) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ClipVault Android",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isDark) Color(0xFF0F172A) else Color(0xFFEEF2FF),
                                    border = BorderStroke(1.dp, if (isDark) Color(0xFF222F49) else Color(0xFFC7D2FE))
                                ) {
                                    Text(
                                        text = "v1.1.0 (Build 11)",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) Color(0xFF38BDF8) else primaryAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Kotlin • Jetpack Compose • Room SQLite • Hardware AES-256 GCM",
                                fontSize = 11.5.sp,
                                color = textSecondary
                            )
                        }
                    }
                }

                HorizontalDivider(color = dividerColor)

                // Bottom Footer Action Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryAccent,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Done",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSectionCard(
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
private fun AboutContactRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
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
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
