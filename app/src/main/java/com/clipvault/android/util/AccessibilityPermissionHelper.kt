package com.clipvault.android.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat

object AccessibilityPermissionHelper {

    /**
     * Checks whether POST_NOTIFICATIONS runtime permission is granted on Android 13+ (API 33+).
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks whether ClipVault is whitelisted from aggressive OS / OEM battery optimizations
     * so the START_STICKY Foreground Service is not killed in the background.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    /**
     * Launches the system prompt to exempt ClipVault from battery optimization restrictions.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }

    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedComponentName = context.packageName + "/" + serviceClass.name
        val shortComponentName = context.packageName + "/." + serviceClass.simpleName
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true) ||
                componentName.equals(shortComponentName, ignoreCase = true) ||
                componentName.contains(serviceClass.simpleName)
            ) {
                return true
            }
        }
        return false
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                // Silently ignore if device restrictions prevent launching settings
            }
        }
    }

    /**
     * Checks whether the optional ClipVault Keyboard (IME) is enabled in Android system settings.
     */
    fun isImeEnabled(context: Context): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            val enabledList = imm?.enabledInputMethodList ?: emptyList()
            if (enabledList.any { it.packageName == context.packageName }) {
                return true
            }
            val enabledSecure = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS
            ) ?: return false
            enabledSecure.contains(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether ClipVault Keyboard (IME) is currently selected as the user's default keyboard.
     */
    fun isImeSelectedAsDefault(context: Context): Boolean {
        return try {
            val defaultIme = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            ) ?: return false
            defaultIme.startsWith(context.packageName + "/") || defaultIme.contains("ClipVaultInputMethodService")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens Android's system Input Method (On-screen Keyboard) Settings screen so the user
     * can optionally enable or disable ClipVault Keyboard.
     */
    fun openInputMethodSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }

    /**
     * Opens the system InputMethodManager keyboard picker dialog so the user can switch
     * between their preferred keyboard (e.g., Gboard) and ClipVault Keyboard at any time.
     */
    fun showInputMethodPicker(context: Context) {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showInputMethodPicker()
        } catch (_: Exception) {}
    }

    /**
     * Returns the exact v1.4.0 user-facing capture state badge label:
     * - "Capture Permission Required"
     * - "ClipVault Keyboard Active"
     * - "Clipboard Capture Active"
     * - "Background Capture Restricted"
     * - "Waiting for Clipboard Access"
     */
    fun resolveAccurateCaptureState(
        autoCaptureEnabled: Boolean,
        isAccessibilityEnabled: Boolean,
        isImeEnabled: Boolean,
        isImeDefault: Boolean,
        hasForegroundFocus: Boolean = false
    ): String {
        if (!autoCaptureEnabled) {
            return "Capture Permission Required"
        }
        return when {
            isImeDefault -> "ClipVault Keyboard Active"
            isAccessibilityEnabled || hasForegroundFocus -> "Clipboard Capture Active"
            isImeEnabled -> "Waiting for Clipboard Access"
            else -> "Background Capture Restricted"
        }
    }

    /**
     * Pure status helper summarizing current capture mode for Settings UI and unit tests.
     */
    fun resolveCaptureStatus(
        autoCaptureEnabled: Boolean,
        isAccessibilityEnabled: Boolean,
        isImeEnabled: Boolean,
        isImeDefault: Boolean
    ): String {
        if (!autoCaptureEnabled) {
            return "Capture Permission Required · Automatic clipboard capture is turned off"
        }
        return when {
            isAccessibilityEnabled && isImeDefault ->
                "ClipVault Keyboard Active (Accessibility + ClipVault Keyboard Default)"
            isAccessibilityEnabled && isImeEnabled ->
                "Clipboard Capture Active (Accessibility Primary · ClipVault Keyboard Ready)"
            isAccessibilityEnabled ->
                "Clipboard Capture Active (Accessibility Permitted Capture)"
            isImeDefault ->
                "ClipVault Keyboard Active (Default IME Permitted Capture)"
            isImeEnabled ->
                "Waiting for Clipboard Access (Other Keyboard Active · Switch to ClipVault or Open App)"
            else ->
                "Background Capture Restricted (Other Keyboard Active · Foreground Import or Enable Accessibility)"
        }
    }
}
