package com.clipvault.android.ui.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
 * Dynamically highlights all case-insensitive occurrences of [searchQuery] inside [fullText]
 * using a high-contrast background and bold text color.
 */
fun buildHighlightedClipText(
    fullText: String,
    searchQuery: String,
    highlightBackground: Color = Color(0xFFFACC15),
    highlightTextColor: Color = Color(0xFF0F172A)
): AnnotatedString {
    val cleanQuery = searchQuery.trim()
    if (cleanQuery.isEmpty()) return AnnotatedString(fullText)

    return buildAnnotatedString {
        append(fullText)
        var startIndex = fullText.indexOf(cleanQuery, startIndex = 0, ignoreCase = true)
        while (startIndex >= 0) {
            val endIndex = (startIndex + cleanQuery.length).coerceAtMost(fullText.length)
            addStyle(
                style = SpanStyle(
                    background = highlightBackground,
                    color = highlightTextColor,
                    fontWeight = FontWeight.ExtraBold
                ),
                start = startIndex,
                end = endIndex
            )
            startIndex = fullText.indexOf(cleanQuery, startIndex = endIndex, ignoreCase = true)
        }
    }
}

/**
 * Smart Category Color Coding palette with high-contrast adjustments for Light Mode
 * while preserving the exact vibrant neon palette in Dark Mode.
 */
private fun getCategoryAccentColor(category: ClipCategory, isDark: Boolean): Color = if (isDark) {
    when (category) {
        ClipCategory.OTP -> Color(0xFFF59E0B)      // Warm Orange / Amber for OTP
        ClipCategory.CODE -> Color(0xFF10B981)     // Vibrant Emerald Green for Code
        ClipCategory.URL -> Color(0xFF38BDF8)      // Electric Cyan / Blue for URLs
        ClipCategory.EMAIL -> Color(0xFFF43F5E)    // Vibrant Coral-Rose Accent for EMAIL
        ClipCategory.COLOR -> Color(0xFFEC4899)    // Vivid Pink / Magenta for Hex Colors
        ClipCategory.PHONE -> Color(0xFF14B8A6)    // Teal Accent for Phone Numbers
        else -> Color(0xFFA78BFA)                  // Rich Purple Accent for Text & Notes
    }
} else {
    when (category) {
        ClipCategory.OTP -> Color(0xFFD97706)      // High-Contrast Deep Amber for Light Mode
        ClipCategory.CODE -> Color(0xFF059669)     // High-Contrast Rich Emerald for Light Mode
        ClipCategory.URL -> Color(0xFF0284C7)      // High-Contrast Ocean Blue for Light Mode
        ClipCategory.EMAIL -> Color(0xFFE11D48)    // High-Contrast Crimson-Rose for EMAIL in Light Mode
        ClipCategory.COLOR -> Color(0xFFDB2777)    // High-Contrast Berry Pink for Light Mode
        ClipCategory.PHONE -> Color(0xFF0D9488)    // Rich Deep Teal for Light Mode
        else -> Color(0xFF6D28D9)                  // Deep Royal Purple for Light Mode
    }
}

private val SystemAutoTags = setOf(
    "url", "code", "email", "phone", "otp", "sensitive", "color", "markdown", "text", "bengali", "multiline"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipItemCard(
    clip: ClipEntity,
    searchQuery: String = "",
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onCopy: () -> Unit,
    onToggleSelect: () -> Unit = {},
    onLongPressSelect: () -> Unit = {},
    onManageTags: () -> Unit = {},
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleVault: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    // 12-hour format with AM/PM (e.g., Nov 9, 6:04 PM)
    val timeFormatted = remember(clip.createdAt) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(clip.createdAt))
    }
    val isUrl = clip.category == ClipCategory.URL
    val isEmail = clip.category == ClipCategory.EMAIL
    val categoryAccent = getCategoryAccentColor(clip.category, isDark)

    // Filter custom user-defined tags (e.g., Office, Personal, Study)
    val customUserTags = remember(clip.tags) {
        clip.tags.filter { tag ->
            !tag.startsWith("len:") && tag.lowercase() !in SystemAutoTags
        }
    }

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

    // 1. Clean Unified Card Borders (plus Multi-Selection highlight border)
    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isCopiedFeedback -> Color(0xFF10B981)
            clip.isPinned -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.7f else 0.6f)
            else -> if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.35f) else Color(0xFFE2E8F0)
        },
        label = "CardBorderColor"
    )

    val triggerCopyWithHaptics = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        isPressedBounce = true
        isCopiedFeedback = true
        onCopy()
    }

    val highlightBg = if (isDark) Color(0xFFFACC15) else Color(0xFFFDE047)
    val highlightFg = if (isDark) Color(0xFF0F172A) else Color(0xFF1A1C1E)
    val highlightedContent = remember(clip.content, searchQuery, isDark) {
        buildHighlightedClipText(
            fullText = clip.content,
            searchQuery = searchQuery,
            highlightBackground = highlightBg,
            highlightTextColor = highlightFg
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(cardScale)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelect()
                    } else {
                        triggerCopyWithHaptics()
                    }
                },
                onLongClick = {
                    onLongPressSelect()
                }
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDark) 0.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected && isDark -> Color(0xFF1E293B)
                isSelected && !isDark -> Color(0xFFEEF2FF)
                clip.isPinned && isDark -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                clip.isPinned && !isDark -> Color(0xFFF5F7FF) // Crisp Soft Indigo-White for Pinned Card in Light Mode
                else -> MaterialTheme.colorScheme.surface     // Pure #FFFFFF in Light Mode, #131B2E in Dark Mode
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 1.8.dp else if (isCopiedFeedback || clip.isPinned) 1.3.dp else 1.dp,
            color = animatedBorderColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Inline Header Row: [Type Label] -> [Custom Tag Chip (if assigned)] -> [• Date & Time • Character Count]
            val clipTypeLabel = when (clip.category) {
                ClipCategory.URL -> "URL LINK"
                ClipCategory.EMAIL -> "EMAIL"
                else -> clip.category.name
            }
            val inlineCustomTag = customUserTags.firstOrNull()?.removePrefix("#")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Unselected",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    ClipCardHeader(
                        clipType = clipTypeLabel,
                        customTag = inlineCustomTag,
                        extraCustomTags = customUserTags.drop(1).map { it.removePrefix("#") },
                        timestamp = timeFormatted,
                        charCount = clip.charCount,
                        categoryAccent = categoryAccent,
                        modifier = Modifier.weight(1f, fill = false)
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

            // Body Content with Dynamic Search Keyword Highlighting & Category Rendering
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
                        text = highlightedContent,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (clip.category == ClipCategory.OTP) {
                Text(
                    text = highlightedContent,
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
                // 2. Normal Text Color for EMAIL, URL, and Standard Text + Search Highlighting
                Text(
                    text = highlightedContent,
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
                    maxLines = if (isEmail) 2 else if (isUrl) 3 else 4,
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
                // 3. Uniform Action Buttons for URL, EMAIL, and PHONE:
                val actionChipBackground = if (isDark) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                } else {
                    Color(0xFFF1F5F9)
                }
                val actionChipContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                val actionChipBorderColor = if (isDark) {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                } else {
                    Color(0xFFE2E8F0)
                }

                when (clip.category) {
                    ClipCategory.URL -> {
                        OutlinedButton(
                            onClick = {
                                val url = if (clip.content.startsWith("http")) clip.content else ("https://" + clip.content)
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = actionChipBackground,
                                contentColor = actionChipContentColor
                            ),
                            border = BorderStroke(1.dp, actionChipBorderColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Open Link", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    ClipCategory.PHONE -> {
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + clip.content)))
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = actionChipBackground,
                                contentColor = actionChipContentColor
                            ),
                            border = BorderStroke(1.dp, actionChipBorderColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Dial", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    ClipCategory.EMAIL -> {
                        OutlinedButton(
                            onClick = {
                                val emailTarget = clip.content.removePrefix("mailto:").trim()
                                try {
                                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + emailTarget)))
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = actionChipBackground,
                                contentColor = actionChipContentColor
                            ),
                            border = BorderStroke(1.dp, actionChipBorderColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Email", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    else -> Spacer(modifier = Modifier.width(1.dp))
                }

                // Standard Action Icons + Custom Tag Button
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = { triggerCopyWithHaptics() }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = if (isCopiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = if (isCopiedFeedback) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = onManageTags, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Label,
                            contentDescription = "Manage Custom Tags",
                            tint = if (customUserTags.isNotEmpty()) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit Clip",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = if (clip.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (clip.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = onToggleVault, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = if (clip.isVaulted) Icons.Filled.Lock else Icons.Outlined.Lock,
                            contentDescription = "Vault",
                            tint = if (clip.isVaulted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Inline Card Header Composable displaying sequentially:
 * [1. Main Type Label] -> [2. Custom Tag Chip (if assigned)] -> [3. Date, Time & Character Count metadata]
 */
@Composable
fun ClipCardHeader(
    clipType: String,
    customTag: String?,
    timestamp: String,
    charCount: Int,
    modifier: Modifier = Modifier,
    extraCustomTags: List<String> = emptyList(),
    categoryAccent: Color? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Main Type Label (e.g., TEXT, URL LINK, EMAIL)
        Surface(
            color = categoryAccent?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            border = if (categoryAccent != null) BorderStroke(1.dp, categoryAccent.copy(alpha = 0.45f)) else null
        ) {
            Text(
                text = clipType,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                color = categoryAccent ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 2. Custom Tag Chip (Inline next to Type Label if exists)
        if (!customTag.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = customTag.removePrefix("#"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        extraCustomTags.take(2).forEach { extraTag ->
            if (extraTag.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = extraTag.removePrefix("#"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 3. Date, Time & Character Count metadata
        Text(
            text = "•  $timestamp  •  $charCount chars",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
