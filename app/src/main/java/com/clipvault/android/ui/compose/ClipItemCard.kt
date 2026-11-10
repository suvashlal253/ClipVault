package com.clipvault.android.ui.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Smart Category Color Coding palette for distinct visual badges
 */
private fun getCategoryAccentColor(category: ClipCategory): Color = when (category) {
    ClipCategory.OTP -> Color(0xFFF59E0B)      // Warm Orange / Amber for OTP
    ClipCategory.CODE -> Color(0xFF10B981)     // Vibrant Emerald Green for Code
    ClipCategory.URL -> Color(0xFF38BDF8)      // Electric Cyan / Blue for URLs
    ClipCategory.COLOR -> Color(0xFFEC4899)    // Vivid Pink / Magenta for Hex Colors
    ClipCategory.EMAIL -> Color(0xFF818CF8)    // Soft Indigo / Violet for Emails
    ClipCategory.PHONE -> Color(0xFF14B8A6)    // Teal Accent for Phone Numbers
    else -> Color(0xFFA78BFA)                  // Rich Purple Accent for Text & Notes
}

@Composable
fun ClipItemCard(
    clip: ClipEntity,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleVault: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // 12-hour format with AM/PM (e.g., Nov 9, 6:04 PM)
    val timeFormatted = remember(clip.createdAt) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(clip.createdAt))
    }
    val isUrl = clip.category == ClipCategory.URL
    val categoryAccent = getCategoryAccentColor(clip.category)

    // 2. Haptic Feedback & Copy Bounce Scale Animation State
    var isCopiedFeedback by remember { mutableStateOf(false) }
    var isPressedBounce by remember { mutableStateOf(false) }

    LaunchedEffect(isPressedBounce) {
        if (isPressedBounce) {
            delay(140)
            isPressedBounce = false
        }
    }

    LaunchedEffect(isCopiedFeedback) {
        if (isCopiedFeedback) {
            delay(1400)
            isCopiedFeedback = false
        }
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isPressedBounce) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "CardCopyScale"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isCopiedFeedback -> Color(0xFF10B981)
            clip.isPinned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            isUrl -> categoryAccent.copy(alpha = 0.65f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        },
        label = "CardBorderColor"
    )

    val triggerCopyWithHaptics = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        isPressedBounce = true
        isCopiedFeedback = true
        onCopy()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clickable { triggerCopyWithHaptics() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                clip.isPinned -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isCopiedFeedback || clip.isPinned || isUrl) 1.3.dp else 1.dp,
            color = animatedBorderColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Smart Category Color Badge, 12-Hour Timestamp, Char Count, Pin Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Smart Category Color Badge
                    Surface(
                        color = categoryAccent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, categoryAccent.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (isUrl) "URL LINK" else clip.category.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.4.sp
                            ),
                            color = categoryAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = clip.charCount.toString() + " chars",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (clip.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body Content with Special Category Rendering
            if (clip.category == ClipCategory.COLOR && clip.content.startsWith("#")) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val parsedColor = try {
                        Color(android.graphics.Color.parseColor(clip.content.trim()))
                    } catch (e: Exception) {
                        Color.Gray
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(parsedColor, CircleShape)
                            .border(1.5.dp, categoryAccent.copy(alpha = 0.8f), CircleShape)
                    )
                    Text(
                        text = clip.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (isUrl) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = categoryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = clip.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = categoryAccent,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (clip.category == ClipCategory.OTP) {
                Text(
                    text = clip.content,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = categoryAccent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = clip.content,
                    style = if (clip.category == ClipCategory.CODE) {
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                    } else {
                        MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 20.sp
                        )
                    },
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Contextual Primary Action Button
                when (clip.category) {
                    ClipCategory.URL -> {
                        Button(
                            onClick = {
                                val url = if (clip.content.startsWith("http")) clip.content else ("https://" + clip.content)
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = categoryAccent.copy(alpha = 0.2f),
                                contentColor = categoryAccent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open Link", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    ClipCategory.PHONE -> {
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + clip.content)))
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = categoryAccent.copy(alpha = 0.2f),
                                contentColor = categoryAccent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Dial", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    ClipCategory.EMAIL -> {
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + clip.content)))
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = categoryAccent.copy(alpha = 0.2f),
                                contentColor = categoryAccent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Email", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    else -> Spacer(modifier = Modifier.width(1.dp))
                }

                // Standard Action Icons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { triggerCopyWithHaptics() }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isCopiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = if (isCopiedFeedback) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit Clip",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (clip.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (clip.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onToggleVault, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (clip.isVaulted) Icons.Filled.Lock else Icons.Outlined.Lock,
                            contentDescription = "Vault",
                            tint = if (clip.isVaulted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
