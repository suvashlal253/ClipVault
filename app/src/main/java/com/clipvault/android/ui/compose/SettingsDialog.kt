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
import com.clipvault.android.keyboard.transliteration.BengaliUserDictionary
import com.clipvault.android.keyboard.transliteration.KeyboardLanguageMode
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
    val userDictionary = remember { BengaliUserDictionary(context) }

    var autoCaptureEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("auto_capture_enabled", true))
    }
    var biometricLockEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("biometric_lock_enabled", true))
    }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showQuickAccessGuide by remember { mutableStateOf(false) }
    var showAboutAutoCaptureDialog by remember { mutableStateOf(false) }
    var showPersonalDictionaryDialog by remember { mutableStateOf(false) }
    var showCustomShortcutsDialog by remember { mutableStateOf(false) }

    // Language & Typing Preferences (Part 11A)
    var defaultKeyboardLanguage by remember {
        mutableStateOf(sharedPrefs.getString("keyboard_default_language", KeyboardLanguageMode.ENGLISH.prefCode) ?: "en")
    }
    var rememberLastLanguage by remember {
        mutableStateOf(sharedPrefs.getBoolean("keyboard_remember_last_language", true))
    }
    var banglaPhoneticEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("keyboard_bangla_phonetic_enabled", true))
    }
    var banglaNumeralsEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("keyboard_bangla_numerals", false))
    }
    var suggestionsEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("keyboard_suggestions_enabled", true))
    }
    var autocorrectEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("keyboard_autocorrect_enabled", true))
    }
    var nextWordPredictionEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("keyboard_next_word_prediction", true))
    }
    var showNumberRow by remember {
        mutableStateOf(sharedPrefs.getBoolean("keyboard_show_number_row", false))
    }

    var personalWordsCount by remember { mutableStateOf(userDictionary.getAllPersonalWords().size) }
    var shortcutsCount by remember { mutableStateOf(userDictionary.getAllShortcuts().size) }

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val dialogBg = if (isDark) Color(0xFF0F1626) else Color(0xFFF8F9FA)
    val dialogBorder = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)

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
                .heightIn(max = 660.dp),
            shape = RoundedCornerShape(24.dp),
            color = dialogBg,
            border = BorderStroke(1.dp, dialogBorder),
            tonalElevation = if (isDark) 8.dp else 4.dp,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Bar
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
                                text = "ClipVault v1.6.0 Settings",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "English + বাংলা Phonetic Keyboard & Clipboard",
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
                    var isA11yEnabled by remember {
                        mutableStateOf(
                            AccessibilityPermissionHelper.isAccessibilityServiceEnabled(
                                context,
                                ClipAccessibilityService::class.java
                            )
                        )
                    }
                    var isImeEnabled by remember {
                        mutableStateOf(AccessibilityPermissionHelper.isImeEnabled(context))
                    }
                    var isImeDefault by remember {
                        mutableStateOf(AccessibilityPermissionHelper.isImeSelectedAsDefault(context))
                    }
                    var isBatteryExempt by remember {
                        mutableStateOf(AccessibilityPermissionHelper.isIgnoringBatteryOptimizations(context))
                    }
                    var keyboardHeightMode by remember {
                        mutableStateOf(sharedPrefs.getString("keyboard_height_mode", "NORMAL") ?: "NORMAL")
                    }
                    var keyboardThemeMode by remember {
                        mutableStateOf(sharedPrefs.getString("keyboard_theme_mode", "DARK_CHARCOAL") ?: "DARK_CHARCOAL")
                    }
                    var vibrationIntensity by remember {
                        mutableStateOf(sharedPrefs.getString("keyboard_vibration_intensity", "MEDIUM") ?: "MEDIUM")
                    }
                    var vibrateOnSpacebar by remember {
                        mutableStateOf(sharedPrefs.getBoolean("keyboard_vibrate_spacebar", true))
                    }
                    var keyboardSoundEnabled by remember {
                        mutableStateOf(sharedPrefs.getBoolean("keyboard_sound_enabled", false))
                    }

                    LaunchedEffect(autoCaptureEnabled) {
                        isA11yEnabled = AccessibilityPermissionHelper.isAccessibilityServiceEnabled(
                            context,
                            ClipAccessibilityService::class.java
                        )
                        isImeEnabled = AccessibilityPermissionHelper.isImeEnabled(context)
                        isImeDefault = AccessibilityPermissionHelper.isImeSelectedAsDefault(context)
                        isBatteryExempt = AccessibilityPermissionHelper.isIgnoringBatteryOptimizations(context)
                    }

                    val captureStateBadge = AccessibilityPermissionHelper.resolveAccurateCaptureState(
                        autoCaptureEnabled = autoCaptureEnabled,
                        isAccessibilityEnabled = isA11yEnabled,
                        isImeEnabled = isImeEnabled,
                        isImeDefault = isImeDefault
                    )

                    // SECTION A: LANGUAGE & TYPING (Part 11A)
                    SettingsSectionCard(title = "LANGUAGE & TYPING") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Default Keyboard Language",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Swipe Spacebar or tap 🌐 Globe anytime to switch between English & বাংলা",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDark) Color(0xFF0B0F19) else Color(0xFFF1F5F9))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val langOptions = listOf(
                                    KeyboardLanguageMode.ENGLISH.prefCode to "English (QWERTY)",
                                    KeyboardLanguageMode.BANGLA_PHONETIC.prefCode to "বাংলা (Phonetic)"
                                )
                                langOptions.forEach { (langKey, langLabel) ->
                                    val isSelected = defaultKeyboardLanguage.equals(langKey, ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            defaultKeyboardLanguage = langKey
                                            sharedPrefs.edit()
                                                .putString("keyboard_default_language", langKey)
                                                .putString("keyboard_last_language", langKey)
                                                .apply()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = langLabel,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 12.sp
                                                ),
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SectionDivider()
                        SettingsToggleItem(
                            icon = Icons.Outlined.History,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Remember Last Used Language",
                            subtitle = "Automatically resume in your most recently active language (English or বাংলা)",
                            checked = rememberLastLanguage,
                            switchColors = switchColors,
                            onCheckedChange = { isChecked ->
                                rememberLastLanguage = isChecked
                                sharedPrefs.edit().putBoolean("keyboard_remember_last_language", isChecked).apply()
                            }
                        )
                        SectionDivider()
                        SettingsToggleItem(
                            icon = Icons.Outlined.Translate,
                            iconTint = Color(0xFF10B981),
                            title = "Bengali Phonetic Typing",
                            subtitle = "Type Banglish (ami -> আমি, suvash -> সুভাষ) with real-time Bengali conversion",
                            checked = banglaPhoneticEnabled,
                            switchColors = switchColors,
                            onCheckedChange = { isChecked ->
                                banglaPhoneticEnabled = isChecked
                                sharedPrefs.edit().putBoolean("keyboard_bangla_phonetic_enabled", isChecked).apply()
                            }
                        )
                        SectionDivider()
                        SettingsToggleItem(
                            icon = Icons.Outlined.Pin,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Bengali Numerals (০-৯)",
                            subtitle = "Convert digits 0-9 into Bengali numerals ০-৯ while in বাংলা mode",
                            checked = banglaNumeralsEnabled,
                            switchColors = switchColors,
                            onCheckedChange = { isChecked ->
                                banglaNumeralsEnabled = isChecked
                                sharedPrefs.edit().putBoolean("keyboard_bangla_numerals", isChecked).apply()
                            }
                        )
                        SectionDivider()
                        SettingsToggleItem(
                            icon = Icons.Outlined.AutoAwesome,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Smart Word Suggestions",
                            subtitle = "Show 3-5 ranked Bengali & English candidates above the keyboard",
                            checked = suggestionsEnabled,
                            switchColors = switchColors,
                            onCheckedChange = { isChecked ->
                                suggestionsEnabled = isChecked
                                sharedPrefs.edit().putBoolean("keyboard_suggestions_enabled", isChecked).apply()
                            }
                        )
                        SectionDivider()
                        SettingsToggleItem(
                            icon = Icons.Outlined.Spellcheck,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Autocorrection",
                            subtitle = "Automatically apply top phonetic or typo correction on Spacebar (Backspace undoes)",
                            checked = autocorrectEnabled,
                            switchColors = switchColors,
                            onCheckedChange = { isChecked ->
                                autocorrectEnabled = isChecked
                                sharedPrefs.edit().putBoolean("keyboard_autocorrect_enabled", isChecked).apply()
                            }
                        )
                        SectionDivider()
                        SettingsToggleItem(
                            icon = Icons.Outlined.FastForward,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Next-Word Prediction",
                            subtitle = "Suggest contextual follow-up words (e.g. আমার -> নাম, কেমন -> আছো)",
                            checked = nextWordPredictionEnabled,
                            switchColors = switchColors,
                            onCheckedChange = { isChecked ->
                                nextWordPredictionEnabled = isChecked
                                sharedPrefs.edit().putBoolean("keyboard_next_word_prediction", isChecked).apply()
                            }
                        )
                        SectionDivider()
                        SettingsActionItem(
                            icon = Icons.Outlined.MenuBook,
                            iconTint = Color(0xFF10B981),
                            title = "Personal Dictionary (" + personalWordsCount + " saved)",
                            subtitle = "Add, edit, delete, or reset preferred spellings (e.g. suvash → সুভাষ)",
                            onClick = { showPersonalDictionaryDialog = true }
                        )
                        SectionDivider()
                        SettingsActionItem(
                            icon = Icons.Outlined.ShortText,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Custom Text Shortcuts (" + shortcutsCount + " active)",
                            subtitle = "Manage quick expansions (brb → be right back, amar nam → আমার নাম সুভাষ)",
                            onClick = { showCustomShortcutsDialog = true }
                        )
                    }

                    // SECTION B: KEYBOARD APPEARANCE (Part 11B)
                    SettingsSectionCard(title = "KEYBOARD APPEARANCE") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "ClipVault Keyboard (IME)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isImeDefault) {
                                        Color(0xFF10B981).copy(alpha = 0.16f)
                                    } else if (isImeEnabled) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            isImeDefault -> "Default Keyboard"
                                            isImeEnabled -> "Enabled (Not Default)"
                                            else -> "Disabled"
                                        },
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isImeDefault -> Color(0xFF10B981)
                                            isImeEnabled -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        AccessibilityPermissionHelper.openInputMethodSettings(context)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Enable Keyboard",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Button(
                                    onClick = {
                                        AccessibilityPermissionHelper.showInputMethodPicker(context)
                                        isImeEnabled = AccessibilityPermissionHelper.isImeEnabled(context)
                                        isImeDefault = AccessibilityPermissionHelper.isImeSelectedAsDefault(context)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Select Keyboard",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        SectionDivider()

                        // Keyboard Theme Selector (Dark Charcoal / Pure Black / Light / Follow System)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Keyboard Theme",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Samsung/Gboard-style dark charcoal, pure black AMOLED, or clean light",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDark) Color(0xFF0B0F19) else Color(0xFFF1F5F9))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val themeOptions = listOf(
                                    "DARK_CHARCOAL" to "Charcoal",
                                    "PURE_BLACK" to "Black",
                                    "LIGHT" to "Light",
                                    "FOLLOW_SYSTEM" to "System"
                                )
                                themeOptions.forEach { (themeKey, themeLabel) ->
                                    val isSelected = keyboardThemeMode.equals(themeKey, ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            keyboardThemeMode = themeKey
                                            sharedPrefs.edit().putString("keyboard_theme_mode", themeKey).apply()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = themeLabel,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 11.sp
                                                ),
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SectionDivider()

                        // Keyboard Height Selector (Compact / Normal / Tall)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Keyboard Height",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Large 24-26sp primary letters + secondary top-right numbers & symbols",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDark) Color(0xFF0B0F19) else Color(0xFFF1F5F9))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val heightOptions = listOf(
                                    "COMPACT" to "Compact",
                                    "NORMAL" to "Normal",
                                    "TALL" to "Tall"
                                )
                                heightOptions.forEach { (modeKey, modeLabel) ->
                                    val isSelected = keyboardHeightMode.equals(modeKey, ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            keyboardHeightMode = modeKey
                                            sharedPrefs.edit().putString("keyboard_height_mode", modeKey).apply()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = modeLabel,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 12.sp
                                                ),
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SectionDivider()
                        SettingsToggleItem(
                            icon = Icons.Outlined.Dialpad,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Dedicated Number Row (1-0)",
                            subtitle = "Show an extra number row above the Q-P row in both English & বাংলা modes",
                            checked = showNumberRow,
                            switchColors = switchColors,
                            onCheckedChange = { isChecked ->
                                showNumberRow = isChecked
                                sharedPrefs.edit().putBoolean("keyboard_show_number_row", isChecked).apply()
                            }
                        )
                    }

                    // SECTION C: KEYPRESS PREFERENCES (Part 11C)
                    SettingsSectionCard(title = "KEYPRESS PREFERENCES") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Keypress Vibration Intensity",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tactile haptic feedback in both English & বাংলা modes",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDark) Color(0xFF0B0F19) else Color(0xFFF1F5F9))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val intensityOptions = listOf(
                                    "OFF" to "Off",
                                    "LIGHT" to "Light",
                                    "MEDIUM" to "Medium",
                                    "STRONG" to "Strong"
                                )
                                intensityOptions.forEach { (levelKey, levelLabel) ->
                                    val isSelected = vibrationIntensity.equals(levelKey, ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            vibrationIntensity = levelKey
                                            val isEnabled = !levelKey.equals("OFF", ignoreCase = true)
                                            sharedPrefs.edit()
                                                .putString("keyboard_vibration_intensity", levelKey)
                                                .putBoolean("keyboard_vibrate_enabled", isEnabled)
                                                .apply()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = levelLabel,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 11.5.sp
                                                ),
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SectionDivider()
                        SettingsToggleItem(
                            icon = Icons.Outlined.Vibration,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Vibrate on Spacebar",
                            subtitle = "Enable haptic feedback when tapping or swiping the spacebar",
                            checked = vibrateOnSpacebar,
                            switchColors = switchColors,
                            onCheckedChange = { isChecked ->
                                vibrateOnSpacebar = isChecked
                                sharedPrefs.edit().putBoolean("keyboard_vibrate_spacebar", isChecked).apply()
                            }
                        )
                        SectionDivider()
                        SettingsToggleItem(
                            icon = Icons.Outlined.VolumeUp,
                            iconTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            title = "Keypress Sound",
                            subtitle = "Play system click sound on key press in ClipVault Keyboard",
                            checked = keyboardSoundEnabled,
                            switchColors = switchColors,
                            onCheckedChange = { isChecked ->
                                keyboardSoundEnabled = isChecked
                                sharedPrefs.edit().putBoolean("keyboard_sound_enabled", isChecked).apply()
                            }
                        )
                    }

                    // SECTION D: CLIPBOARD AUTOMATION (Part 11D)
                    SettingsSectionCard(title = "CLIPBOARD AUTOMATION") {
                        SettingsToggleItem(
                            icon = Icons.Outlined.Bolt,
                            iconTint = if (isDark) Color(0xFF10B981) else Color(0xFF059669),
                            title = "Background Auto-Capture",
                            subtitle = if (autoCaptureEnabled) {
                                "Enabled · Mode: " + captureStateBadge
                            } else {
                                "Disabled · Capture Permission Required"
                            },
                            checked = autoCaptureEnabled,
                            switchColors = switchColors,
                            onCheckedChange = { isChecked ->
                                autoCaptureEnabled = isChecked
                                sharedPrefs.edit().putBoolean("auto_capture_enabled", isChecked).apply()
                                if (isChecked) {
                                    ClipboardForegroundService.start(context)
                                    Toast.makeText(context, "Background auto-capture enabled", Toast.LENGTH_SHORT).show()
                                } else {
                                    ClipboardForegroundService.stop(context)
                                    Toast.makeText(context, "Background auto-capture paused", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        SectionDivider()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Current Capture Mode: " + captureStateBadge,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = if (autoCaptureEnabled && (isA11yEnabled || isImeDefault)) {
                                    Color(0xFF10B981)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = "Accessibility Service: " + (if (isA11yEnabled) "Running" else "Not Enabled") +
                                    " · Foreground Monitor: " + (if (autoCaptureEnabled) "Active" else "Paused") +
                                    " · Battery Unrestricted: " + (if (isBatteryExempt) "Yes" else "Standard"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = AccessibilityPermissionHelper.resolveCaptureStatus(
                                    autoCaptureEnabled = autoCaptureEnabled,
                                    isAccessibilityEnabled = isA11yEnabled,
                                    isImeEnabled = isImeEnabled,
                                    isImeDefault = isImeDefault
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isA11yEnabled = AccessibilityPermissionHelper.isAccessibilityServiceEnabled(
                                            context,
                                            ClipAccessibilityService::class.java
                                        )
                                        isImeEnabled = AccessibilityPermissionHelper.isImeEnabled(context)
                                        isImeDefault = AccessibilityPermissionHelper.isImeSelectedAsDefault(context)
                                        isBatteryExempt = AccessibilityPermissionHelper.isIgnoringBatteryOptimizations(context)
                                        Toast.makeText(context, "Capture status refreshed", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Retry Status Check",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }

                                Button(
                                    onClick = {
                                        AccessibilityPermissionHelper.openAccessibilitySettings(context)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Accessibility Settings",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        SectionDivider()
                        SettingsActionItem(
                            icon = Icons.Outlined.BatteryChargingFull,
                            iconTint = if (isBatteryExempt) Color(0xFF10B981) else Color(0xFFF59E0B),
                            title = if (isBatteryExempt) {
                                "Battery Optimization (Unrestricted)"
                            } else {
                                "Battery Optimization Guidance"
                            },
                            subtitle = if (isBatteryExempt) {
                                "ClipVault is exempt from aggressive background battery restrictions"
                            } else {
                                "Allow unrestricted background usage so Android does not kill clipboard monitoring"
                            },
                            onClick = {
                                AccessibilityPermissionHelper.requestIgnoreBatteryOptimizations(context)
                            }
                        )
                        SectionDivider()
                        SettingsActionItem(
                            icon = Icons.Outlined.Info,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "About Background Auto-Capture",
                            subtitle = "Re-open the setup & explanation dialog for background clipboard capture",
                            onClick = {
                                showAboutAutoCaptureDialog = true
                            }
                        )
                    }

                    // SECTION E: APP THEME & PRIVACY
                    SettingsSectionCard(title = "APP APPEARANCE & PRIVACY") {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "ClipVault App Theme",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
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
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
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
                        SectionDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "100% Offline Bilingual Typing & Android 10+ Privacy",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "• All English & বাংলা Phonetic transliteration, personal dictionary words, and clipboard history run 100% offline on your device.\n" +
                                    "• Zero keystroke logging, zero external servers, and password fields are automatically excluded.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 15.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SectionDivider()
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

                    // SECTION F: CLOUD BACKUP & DATA MANAGEMENT
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

                    // SECTION G: ABOUT
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

    // Personal Dictionary Manager Dialog (Part 3D: Add, Edit, Delete, Reset)
    if (showPersonalDictionaryDialog) {
        var entries by remember { mutableStateOf(userDictionary.getAllPersonalWords()) }
        var romanInput by remember { mutableStateOf("") }
        var targetInput by remember { mutableStateOf("") }
        var editingOriginalKey by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                personalWordsCount = userDictionary.getAllPersonalWords().size
                showPersonalDictionaryDialog = false
            },
            containerColor = dialogBg,
            title = {
                Text(
                    "Personal Dictionary (বাংলা & English)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Save your preferred Bengali or English spellings (e.g. suvash → সুভাষ):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = romanInput,
                        onValueChange = { romanInput = it },
                        label = { Text("Banglish / Trigger (e.g. suvash)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it },
                        label = { Text("Preferred Output (e.g. সুভাষ)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (romanInput.isNotBlank() && targetInput.isNotBlank()) {
                                    val orig = editingOriginalKey
                                    if (orig != null) {
                                        userDictionary.editWord(orig, romanInput, targetInput)
                                        editingOriginalKey = null
                                    } else {
                                        userDictionary.addOrUpdateWord(romanInput, targetInput)
                                    }
                                    romanInput = ""
                                    targetInput = ""
                                    entries = userDictionary.getAllPersonalWords()
                                    personalWordsCount = entries.size
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (editingOriginalKey != null) "Update Word" else "Add to Dictionary")
                        }
                        OutlinedButton(
                            onClick = {
                                userDictionary.resetPersonalDictionary(keepDefaults = true)
                                entries = userDictionary.getAllPersonalWords()
                                personalWordsCount = entries.size
                                Toast.makeText(context, "Personal dictionary reset", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Reset")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    entries.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.romanKey + "  →  " + item.preferredWord,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        editingOriginalKey = item.romanKey
                                        romanInput = item.romanKey
                                        targetInput = item.preferredWord
                                    }
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        userDictionary.deleteWord(item.romanKey)
                                        entries = userDictionary.getAllPersonalWords()
                                        personalWordsCount = entries.size
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        personalWordsCount = userDictionary.getAllPersonalWords().size
                        showPersonalDictionaryDialog = false
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }

    // Custom Text Shortcuts & Personal Phrases Dialog (Part 8 #1 & #2)
    if (showCustomShortcutsDialog) {
        var shortcuts by remember { mutableStateOf(userDictionary.getAllShortcuts()) }
        var triggerInput by remember { mutableStateOf("") }
        var expansionInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = {
                shortcutsCount = userDictionary.getAllShortcuts().size
                showCustomShortcutsDialog = false
            },
            containerColor = dialogBg,
            title = {
                Text(
                    "Custom Shortcuts & Bengali Phrases",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Examples: brb → be right back, amar nam → আমার নাম সুভাষ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = triggerInput,
                        onValueChange = { triggerInput = it },
                        label = { Text("Shortcut Trigger (e.g. brb)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = expansionInput,
                        onValueChange = { expansionInput = it },
                        label = { Text("Expanded Text (e.g. be right back)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (triggerInput.isNotBlank() && expansionInput.isNotBlank()) {
                                userDictionary.addOrUpdateShortcut(triggerInput, expansionInput)
                                triggerInput = ""
                                expansionInput = ""
                                shortcuts = userDictionary.getAllShortcuts()
                                shortcutsCount = shortcuts.size
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Shortcut")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    shortcuts.forEach { sc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = sc.trigger + "  →  " + sc.expansion,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    userDictionary.deleteShortcut(sc.trigger)
                                    shortcuts = userDictionary.getAllShortcuts()
                                    shortcutsCount = shortcuts.size
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        shortcutsCount = userDictionary.getAllShortcuts().size
                        showCustomShortcutsDialog = false
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }

    // Re-open Background Auto-Capture Explanation / Setup Dialog on user request
    if (showAboutAutoCaptureDialog) {
        BackgroundCaptureOnboardingDialog(
            onEnableNow = {
                showAboutAutoCaptureDialog = false
                AccessibilityPermissionHelper.openAccessibilitySettings(context)
            },
            onMaybeLater = {
                showAboutAutoCaptureDialog = false
            },
            onClose = {
                showAboutAutoCaptureDialog = false
            }
        )
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
                        text = "ClipVault's Foreground Service & Accessibility observer automatically capture copied text from WhatsApp, Facebook, Chrome, and any app when permitted.",
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
