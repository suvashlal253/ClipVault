package com.clipvault.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Unchanged Signature Dark Mode Palette
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1),              // Vibrant Indigo/Blue-Purple Accent
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4F46E5),     // Rich Active Tab / Accent Container
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF10B981),             // High-contrast Emerald Green Accent
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFD1FAE5),
    background = Color(0xFF0B0F19),           // Deep Midnight Slate Background
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF131B2E),              // Clean Elevated Dark Card Surface
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),       // Subtle Muted Card / Chip Surface
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

// Overhauled Crisp, High-Contrast Light Mode Palette (#F8F9FA Surface, #FFFFFF Cards, #1A1C1E Deep Charcoal Text)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),              // Crisp Vibrant Indigo-Purple Brand Accent
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4F46E5),     // Solid High-Contrast Active Filter Chip & Pill Fill
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF6366F1),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEF2FF),   // Soft Indigo Tint for Light Badges
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFF059669),             // Rich Emerald Green for Light Mode
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    background = Color(0xFFF8F9FA),           // Crisp Premium Alabaster Light Background (#F8F9FA)
    onBackground = Color(0xFF1A1C1E),         // High-Contrast Deep Charcoal Text (#1A1C1E)
    surface = Color(0xFFFFFFFF),              // Pure White Card & TopBar Containers (#FFFFFF)
    onSurface = Color(0xFF1A1C1E),            // Deep Charcoal Primary Typography (#1A1C1E)
    surfaceVariant = Color(0xFFF1F5F9),       // Subtle Secondary Container / Unselected Chip Fill
    onSurfaceVariant = Color(0xFF44474F),     // Crisp Dark Slate Secondary Text (No Washed-Out Grey)
    outline = Color(0xFFD0D5DD),              // Clean Subtle Card & Input Border
    outlineVariant = Color(0xFFE4E7EC),       // Hairline Section Divider
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

/**
 * Unified Dialog & Card Design Tokens so Settings, About Developer, Privacy Policy,
 * and Edit Clip dialogs share a crisp #F8F9FA / #FFFFFF light layout while preserving
 * the exact #0F1626 / #161F33 dark mode surfaces.
 */
@Immutable
data class ClipVaultExtendedColors(
    val isDark: Boolean,
    val dialogBackground: Color,
    val dialogBorder: Color,
    val sectionCardBackground: Color,
    val sectionCardBorder: Color,
    val sectionHeaderTitle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val closeButtonBg: Color,
    val closeButtonIcon: Color,
    val dividerColor: Color,
    val secondaryButtonBg: Color,
    val secondaryButtonBorder: Color,
    val secondaryButtonText: Color,
    val primaryButtonBg: Color
)

val LocalClipVaultExtendedColors = staticCompositionLocalOf {
    ClipVaultExtendedColors(
        isDark = true,
        dialogBackground = Color(0xFF0F1626),
        dialogBorder = Color(0xFF1E293B),
        sectionCardBackground = Color(0xFF161F33),
        sectionCardBorder = Color(0xFF222F49),
        sectionHeaderTitle = Color(0xFF818CF8),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8),
        closeButtonBg = Color(0xFF1E293B),
        closeButtonIcon = Color(0xFFCBD5E1),
        dividerColor = Color(0xFF1E293B),
        secondaryButtonBg = Color.Transparent,
        secondaryButtonBorder = Color(0xFF334155),
        secondaryButtonText = Color(0xFFCBD5E1),
        primaryButtonBg = Color(0xFF6366F1)
    )
}

@Composable
fun rememberClipVaultExtendedColors(
    isDark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
): ClipVaultExtendedColors {
    return if (isDark) {
        ClipVaultExtendedColors(
            isDark = true,
            dialogBackground = Color(0xFF0F1626),
            dialogBorder = Color(0xFF1E293B),
            sectionCardBackground = Color(0xFF161F33),
            sectionCardBorder = Color(0xFF222F49),
            sectionHeaderTitle = Color(0xFF818CF8),
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFF94A3B8),
            closeButtonBg = Color(0xFF1E293B),
            closeButtonIcon = Color(0xFFCBD5E1),
            dividerColor = Color(0xFF1E293B),
            secondaryButtonBg = Color.Transparent,
            secondaryButtonBorder = Color(0xFF334155),
            secondaryButtonText = Color(0xFFCBD5E1),
            primaryButtonBg = Color(0xFF6366F1)
        )
    } else {
        ClipVaultExtendedColors(
            isDark = false,
            dialogBackground = Color(0xFFF8F9FA),       // Crisp #F8F9FA Dialog Surface
            dialogBorder = Color(0xFFE2E8F0),           // Clean Light Dialog Border
            sectionCardBackground = Color(0xFFFFFFFF),  // Pure White #FFFFFF Section Cards
            sectionCardBorder = Color(0xFFE2E8F0),      // Crisp Hairline Card Border
            sectionHeaderTitle = Color(0xFF4F46E5),     // Vibrant Indigo Section Header
            textPrimary = Color(0xFF1A1C1E),            // Deep Charcoal #1A1C1E Primary Text
            textSecondary = Color(0xFF44474F),          // High-Contrast Slate Secondary Text
            closeButtonBg = Color(0xFFEFF2F6),          // Subtle Light Circle Button
            closeButtonIcon = Color(0xFF1A1C1E),        // Crisp Charcoal Icon
            dividerColor = Color(0xFFE4E7EC),           // Clean Divider
            secondaryButtonBg = Color(0xFFFFFFFF),      // Solid Pure White Secondary Button Fill
            secondaryButtonBorder = Color(0xFFCBD5E1),  // High-Contrast Button Outline
            secondaryButtonText = Color(0xFF1A1C1E),    // Deep Charcoal Button Label
            primaryButtonBg = Color(0xFF4F46E5)         // Solid Vibrant Brand Indigo Fill
        )
    }
}

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun ClipVaultTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    darkTheme: Boolean = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    },
    dynamicColor: Boolean = false, // Explicit premium dark/light palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val extendedColors = rememberClipVaultExtendedColors(isDark = darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(LocalClipVaultExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
