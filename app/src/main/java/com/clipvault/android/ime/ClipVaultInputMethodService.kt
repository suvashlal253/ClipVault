package com.clipvault.android.ime

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.clipvault.android.data.local.AppDatabase
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.service.ClipboardServiceHelper
import com.clipvault.android.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ClipVault Keyboard (IME) v1.5.0 — Samsung/Gboard-inspired dark charcoal & pure black keyboard
 * with top toolbar (ClipVault App Shortcut first), secondary numbers/symbols (Q=1..P=0),
 * long-press symbol input, 4-level haptic feedback, Emoji panel, Clipboard quick panel,
 * and Text Editing panel.
 */
class ClipVaultInputMethodService : InputMethodService() {

    enum class ShiftState {
        LOWERCASE,
        SHIFTED_SINGLE,
        CAPS_LOCK
    }

    enum class SymbolPage {
        LETTERS,
        NUMBERS_SYMBOLS_1,
        SYMBOLS_2
    }

    enum class ActivePanel {
        NONE,
        EMOJI,
        CLIPBOARD,
        TEXT_EDITING,
        MORE_OPTIONS
    }

    enum class KeyboardHeightPreset(
        val prefKey: String,
        val keyHeightDp: Int,
        val letterSp: Float,
        val secondarySp: Float,
        val numberSp: Float,
        val functionSp: Float
    ) {
        COMPACT("COMPACT", keyHeightDp = 45, letterSp = 24f, secondarySp = 11f, numberSp = 21.5f, functionSp = 15.5f),
        NORMAL("NORMAL", keyHeightDp = 51, letterSp = 25f, secondarySp = 12f, numberSp = 23f, functionSp = 17f),
        TALL("TALL", keyHeightDp = 57, letterSp = 26f, secondarySp = 13f, numberSp = 24.5f, functionSp = 18.5f);

        companion object {
            fun fromKey(raw: String?): KeyboardHeightPreset =
                values().firstOrNull { it.prefKey.equals(raw, ignoreCase = true) } ?: NORMAL
        }
    }

    enum class KeyboardThemeMode(
        val prefKey: String,
        val displayName: String,
        val boardBgHex: String,
        val keyBgHex: String,
        val keyPressedHex: String,
        val modifierBgHex: String,
        val borderHex: String,
        val primaryTextHex: String,
        val secondaryTextHex: String,
        val enterAccentHex: String
    ) {
        DARK_CHARCOAL(
            prefKey = "DARK_CHARCOAL",
            displayName = "Dark Charcoal",
            boardBgHex = "#121316",
            keyBgHex = "#282A2F",
            keyPressedHex = "#3A3D45",
            modifierBgHex = "#1C1E22",
            borderHex = "#34373E",
            primaryTextHex = "#FFFFFF",
            secondaryTextHex = "#9CA3AF",
            enterAccentHex = "#4F46E5"
        ),
        PURE_BLACK(
            prefKey = "PURE_BLACK",
            displayName = "Pure Black",
            boardBgHex = "#000000",
            keyBgHex = "#1C1D21",
            keyPressedHex = "#2E3036",
            modifierBgHex = "#121316",
            borderHex = "#27292E",
            primaryTextHex = "#FFFFFF",
            secondaryTextHex = "#9CA3AF",
            enterAccentHex = "#6366F1"
        ),
        LIGHT(
            prefKey = "LIGHT",
            displayName = "Light",
            boardBgHex = "#E5E7EB",
            keyBgHex = "#FFFFFF",
            keyPressedHex = "#D1D5DB",
            modifierBgHex = "#CFD4DC",
            borderHex = "#D1D5DB",
            primaryTextHex = "#111827",
            secondaryTextHex = "#6B7280",
            enterAccentHex = "#4F46E5"
        ),
        FOLLOW_SYSTEM(
            prefKey = "FOLLOW_SYSTEM",
            displayName = "Follow System",
            boardBgHex = "#121316",
            keyBgHex = "#282A2F",
            keyPressedHex = "#3A3D45",
            modifierBgHex = "#1C1E22",
            borderHex = "#34373E",
            primaryTextHex = "#FFFFFF",
            secondaryTextHex = "#9CA3AF",
            enterAccentHex = "#4F46E5"
        );

        companion object {
            fun fromKey(raw: String?): KeyboardThemeMode =
                values().firstOrNull { it.prefKey.equals(raw, ignoreCase = true) } ?: DARK_CHARCOAL
        }
    }

    enum class VibrationIntensity(
        val prefKey: String,
        val displayName: String,
        val durationMs: Long,
        val amplitude: Int
    ) {
        OFF("OFF", "Off", 0L, 0),
        LIGHT("LIGHT", "Light", 12L, 70),
        MEDIUM("MEDIUM", "Medium", 22L, 145),
        STRONG("STRONG", "Strong", 34L, 255);

        companion object {
            fun fromKey(raw: String?): VibrationIntensity =
                values().firstOrNull { it.prefKey.equals(raw, ignoreCase = true) } ?: LIGHT
        }
    }

    private data class KeyCellRefs(
        val container: FrameLayout,
        val primaryLabel: TextView,
        val secondaryLabel: TextView
    )

    private var clipboardManager: ClipboardManager? = null
    private var audioManager: AudioManager? = null
    private var vibrator: Vibrator? = null
    private var isListenerRegistered = false

    private val imeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recentClipsCache: List<ClipEntity> = emptyList()

    private var shiftState: ShiftState = ShiftState.SHIFTED_SINGLE
    private var symbolPage: SymbolPage = SymbolPage.LETTERS
    private var activePanel: ActivePanel = ActivePanel.NONE
    private var selectedEmojiCategoryIndex: Int = 1 // Default to Smileys
    private var lastShiftTapUptimeMs: Long = 0L

    private val keyCellViews = mutableListOf<KeyCellRefs>()
    private var shiftKeyView: TextView? = null
    private var modeKeyView: TextView? = null
    private var longPressHintBanner: TextView? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isBackspacePressed = false
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            if (!isBackspacePressed) return
            handleBackspace()
            mainHandler.postDelayed(this, 50L)
        }
    }

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        capturePermittedClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        isActiveInstance = true
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        vibrator = resolveSystemVibrator()
        registerClipboardListenerIfNeeded()
        refreshRecentClipsAsync()
    }

    private fun resolveSystemVibrator(): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onCreateInputView(): View {
        registerClipboardListenerIfNeeded()
        return buildKeyboardLayout()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        registerClipboardListenerIfNeeded()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isInputViewVisibleState = true
        registerClipboardListenerIfNeeded()
        refreshRecentClipsAsync()
        setInputView(buildKeyboardLayout())
        capturePermittedClipboard()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopBackspaceRepeat()
        activePanel = ActivePanel.NONE
        isInputViewVisibleState = false
    }

    override fun onDestroy() {
        stopBackspaceRepeat()
        unregisterClipboardListener()
        isInputViewVisibleState = false
        isActiveInstance = false
        keyCellViews.clear()
        imeScope.cancel()
        super.onDestroy()
    }

    private fun refreshRecentClipsAsync() {
        imeScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    AppDatabase.getDatabase(applicationContext)
                        .clipDao()
                        .getAllClipsIncludingVaultSync()
                        .filter { !it.isVaulted }
                        .take(15)
                }.getOrDefault(emptyList())
            }
            recentClipsCache = loaded
        }
    }

    private fun registerClipboardListenerIfNeeded() {
        val manager = clipboardManager ?: return
        if (ListenerRegistrationController.markRegistered(isListenerRegistered)) {
            try {
                manager.addPrimaryClipChangedListener(clipChangedListener)
                isListenerRegistered = true
            } catch (_: Exception) {
                isListenerRegistered = false
            }
        }
    }

    private fun unregisterClipboardListener() {
        val manager = clipboardManager ?: return
        if (ListenerRegistrationController.markUnregistered(isListenerRegistered)) {
            try {
                manager.removePrimaryClipChangedListener(clipChangedListener)
            } catch (_: Exception) {
            }
            isListenerRegistered = false
        }
    }

    /**
     * Reads clipboard items only when Android permits access and the current editor is not a password field.
     */
    private fun capturePermittedClipboard() {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_capture_enabled", true)) return

        val editorInfo = currentInputEditorInfo
        if (editorInfo != null && isInputFieldPassword(editorInfo.inputType)) {
            return
        }

        try {
            val manager = clipboardManager ?: return
            if (!manager.hasPrimaryClip()) return
            val clipData: ClipData = manager.primaryClip ?: return

            if (isClipDescriptionSensitive(clipData.description)) {
                return
            }

            val count = clipData.itemCount
            if (count <= 0) return

            val helper = ClipboardServiceHelper.getInstance(applicationContext)
            for (i in 0 until count) {
                val item = clipData.getItemAt(i) ?: continue
                val rawText = item.text?.toString() ?: item.coerceToText(this)?.toString()
                val trimmed = rawText?.trim()
                if (!trimmed.isNullOrEmpty()) {
                    helper.enqueueClip(trimmed, sourceApp = "ClipVault IME")
                }
            }
            refreshRecentClipsAsync()
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun isClipDescriptionSensitive(description: ClipDescription?): Boolean {
        if (description == null) return false
        val extras: PersistableBundle = description.extras ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
        } else {
            extras.getBoolean("android.content.extra.IS_SENSITIVE", false)
        }
    }

    private fun resolveEffectiveTheme(): KeyboardThemeMode {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val rawTheme = prefs.getString("keyboard_theme_mode", KeyboardThemeMode.DARK_CHARCOAL.prefKey)
        val selected = KeyboardThemeMode.fromKey(rawTheme)
        if (selected != KeyboardThemeMode.FOLLOW_SYSTEM) return selected
        val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMask == Configuration.UI_MODE_NIGHT_YES) {
            KeyboardThemeMode.DARK_CHARCOAL
        } else {
            KeyboardThemeMode.LIGHT
        }
    }

    private fun getKeyboardHeightPreset(): KeyboardHeightPreset {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        return KeyboardHeightPreset.fromKey(prefs.getString("keyboard_height_mode", "NORMAL"))
    }

    private fun getVibrationIntensity(): VibrationIntensity {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("keyboard_vibrate_enabled", true)) {
            return VibrationIntensity.OFF
        }
        return VibrationIntensity.fromKey(prefs.getString("keyboard_vibration_intensity", "LIGHT"))
    }

    /**
     * Triggers hardware/view haptic feedback according to user's selected intensity (Off, Light, Medium, Strong)
     * and respects the separate "Vibrate on Spacebar" preference.
     */
    private fun performKeyFeedback(view: View, isSpacebar: Boolean = false) {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val intensity = getVibrationIntensity()
        val spacebarVibrateAllowed = !isSpacebar || prefs.getBoolean("keyboard_vibrate_spacebar", true)

        if (intensity != VibrationIntensity.OFF && spacebarVibrateAllowed) {
            try {
                val vib = vibrator
                if (vib != null && vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(
                            VibrationEffect.createOneShot(
                                intensity.durationMs,
                                intensity.amplitude.coerceIn(1, 255)
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(intensity.durationMs)
                    }
                } else {
                    view.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                    )
                }
            } catch (_: Exception) {
                try {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } catch (_: Exception) {
                }
            }
        }

        if (prefs.getBoolean("keyboard_sound_enabled", false)) {
            try {
                audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Launches ClipVault MainActivity directly from the top keyboard toolbar without duplicate instances.
     * Supports optional [openSettings] flag on long-press.
     */
    fun launchClipVaultMainActivity(openSettings: Boolean = false) {
        try {
            val baseIntent = packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, MainActivity::class.java)
            baseIntent.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (openSettings) {
                    putExtra(EXTRA_OPEN_CLIPVAULT_SETTINGS, true)
                }
            }
            startActivity(baseIntent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (openSettings) {
                        putExtra(EXTRA_OPEN_CLIPVAULT_SETTINGS, true)
                    }
                }
                startActivity(fallback)
            } catch (_: Exception) {
            }
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun createRoundedBackground(fillColor: Int, strokeColor: Int, radiusDp: Int = 8): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
        }
    }

    private fun rebuildActiveInputView() {
        setInputView(buildKeyboardLayout())
    }

    private fun buildKeyboardLayout(): View {
        keyCellViews.clear()
        val theme = resolveEffectiveTheme()
        val preset = getKeyboardHeightPreset()

        val boardBg = Color.parseColor(theme.boardBgHex)
        val keyBg = Color.parseColor(theme.keyBgHex)
        val keyPressedBg = Color.parseColor(theme.keyPressedHex)
        val actionKeyBg = Color.parseColor(theme.modifierBgHex)
        val keyBorder = Color.parseColor(theme.borderHex)
        val textPrimary = Color.parseColor(theme.primaryTextHex)
        val textSecondary = Color.parseColor(theme.secondaryTextHex)
        val primaryAccent = Color.parseColor(theme.enterAccentHex)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(boardBg)
            setPadding(dp(4), dp(4), dp(4), dp(10))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. TOP ICON TOOLBAR (Matching reference screenshot: ClipVault Shortcut first + 7 tool icons)
        root.addView(
            buildTopToolbar(
                theme = theme,
                actionKeyBg = actionKeyBg,
                keyBorder = keyBorder,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                primaryAccent = primaryAccent
            )
        )

        // Subtle long-press / status feedback bar
        val hintBanner = TextView(this).apply {
            text = ""
            visibility = View.GONE
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = createRoundedBackground(primaryAccent, primaryAccent, radiusDp = 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, dp(3))
            }
        }
        longPressHintBanner = hintBanner
        root.addView(hintBanner)

        // 2. ACTIVE BODY: Either one of the interactive tool panels (Emoji / Clipboard / Text Editing / More) or QWERTY rows
        when (activePanel) {
            ActivePanel.EMOJI -> {
                root.addView(buildEmojiPanel(preset, keyBg, keyPressedBg, actionKeyBg, keyBorder, textPrimary, textSecondary, primaryAccent))
            }
            ActivePanel.CLIPBOARD -> {
                root.addView(buildClipboardQuickPanel(preset, keyBg, actionKeyBg, keyBorder, textPrimary, textSecondary, primaryAccent))
            }
            ActivePanel.TEXT_EDITING -> {
                root.addView(buildTextEditingPanel(preset, keyBg, keyPressedBg, actionKeyBg, keyBorder, textPrimary, textSecondary, primaryAccent))
            }
            ActivePanel.MORE_OPTIONS -> {
                root.addView(buildMoreOptionsPanel(preset, keyBg, actionKeyBg, keyBorder, textPrimary, textSecondary, primaryAccent))
            }
            ActivePanel.NONE -> {
                // Row 1: Q W E R T Y U I O P (with secondary numbers 1..9, 0)
                val row1Keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
                root.addView(
                    buildQwertyCharacterRow(
                        keys = row1Keys,
                        preset = preset,
                        keyBg = keyBg,
                        keyPressedBg = keyPressedBg,
                        actionKeyBg = actionKeyBg,
                        keyBorder = keyBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        primaryAccent = primaryAccent,
                        horizontalInsetDp = 0
                    )
                )

                // Row 2: A S D F G H J K L (with secondary symbols @ # $ _ & - + ( ))
                val row2Keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
                root.addView(
                    buildQwertyCharacterRow(
                        keys = row2Keys,
                        preset = preset,
                        keyBg = keyBg,
                        keyPressedBg = keyPressedBg,
                        actionKeyBg = actionKeyBg,
                        keyBorder = keyBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        primaryAccent = primaryAccent,
                        horizontalInsetDp = 16
                    )
                )

                // Row 3: Shift + Z X C V B N M + Backspace
                val row3 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, dp(3), 0, dp(3))
                }

                val isShiftActive = symbolPage == SymbolPage.LETTERS && shiftState != ShiftState.LOWERCASE
                val shiftBtn = createFunctionKeyView(
                    label = resolveLeftModifierLabel(),
                    weight = 1.48f,
                    heightDp = preset.keyHeightDp,
                    textSizeSp = preset.functionSp,
                    bgColor = if (isShiftActive) primaryAccent else actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = if (isShiftActive) Color.WHITE else textPrimary
                ) {
                    onLeftModifierTapped(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset)
                }
                shiftKeyView = shiftBtn
                row3.addView(shiftBtn)

                val row3Keys = listOf("z", "x", "c", "v", "b", "n", "m")
                row3Keys.forEachIndexed { idx, ch ->
                    val globalIdx = 19 + idx
                    val cell = createCharacterKeyCell(
                        baseLetter = ch,
                        globalIndex = globalIdx,
                        preset = preset,
                        keyBg = keyBg,
                        keyPressedBg = keyPressedBg,
                        actionKeyBg = actionKeyBg,
                        keyBorder = keyBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        primaryAccent = primaryAccent
                    )
                    keyCellViews.add(cell)
                    row3.addView(cell.container)
                }

                val backspaceBtn = createRepeatingBackspaceKey(
                    weight = 1.48f,
                    heightDp = preset.keyHeightDp,
                    textSizeSp = preset.functionSp + 1f,
                    bgColor = actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary
                )
                row3.addView(backspaceBtn)
                root.addView(row3)

                // Row 4: ?123 + Emoji + Comma + Space + Period + Enter
                val row4 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, dp(3), 0, dp(2))
                }

                val symBtn = createFunctionKeyView(
                    label = if (symbolPage == SymbolPage.LETTERS) "?123" else "ABC",
                    weight = 1.42f,
                    heightDp = preset.keyHeightDp,
                    textSizeSp = preset.functionSp - 1f,
                    bgColor = actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary
                ) {
                    symbolPage = toggleSymbolMode(symbolPage)
                    refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset)
                }
                modeKeyView = symBtn
                row4.addView(symBtn)

                val emojiKeyBtn = createFunctionKeyView(
                    label = "☺",
                    weight = 1.0f,
                    heightDp = preset.keyHeightDp,
                    textSizeSp = preset.functionSp + 1f,
                    bgColor = actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary
                ) {
                    activePanel = if (activePanel == ActivePanel.EMOJI) ActivePanel.NONE else ActivePanel.EMOJI
                    rebuildActiveInputView()
                }
                row4.addView(emojiKeyBtn)

                val commaBtn = createFunctionKeyView(
                    label = ",",
                    weight = 0.95f,
                    heightDp = preset.keyHeightDp,
                    textSizeSp = preset.letterSp - 2f,
                    bgColor = keyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary
                ) {
                    currentInputConnection?.commitText(",", 1)
                }
                row4.addView(commaBtn)

                val spaceBtn = createFunctionKeyView(
                    label = "English",
                    weight = 4.1f,
                    heightDp = preset.keyHeightDp,
                    textSizeSp = 14.5f,
                    bgColor = keyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textSecondary,
                    isSpacebar = true
                ) {
                    currentInputConnection?.commitText(" ", 1)
                }
                row4.addView(spaceBtn)

                val periodBtn = createFunctionKeyView(
                    label = ".",
                    weight = 0.95f,
                    heightDp = preset.keyHeightDp,
                    textSizeSp = preset.letterSp - 2f,
                    bgColor = keyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary
                ) {
                    currentInputConnection?.commitText(".", 1)
                }
                row4.addView(periodBtn)

                val enterBtn = createFunctionKeyView(
                    label = "↵",
                    weight = 1.55f,
                    heightDp = preset.keyHeightDp,
                    textSizeSp = preset.functionSp + 2f,
                    bgColor = primaryAccent,
                    pressedBgColor = primaryAccent,
                    borderColor = primaryAccent,
                    textColor = Color.WHITE
                ) {
                    handleEnterAction()
                }
                row4.addView(enterBtn)
                root.addView(row4)

                refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset)
            }
        }

        return root
    }

    /**
     * Builds the Top Icon Toolbar inspired by the reference screenshot:
     * 1. ClipVault App Shortcut (Branded badge, tap -> MainActivity, long-press -> Settings)
     * 2. Emoji Panel
     * 3. Keyboard Settings
     * 4. Clipboard History Quick Panel
     * 5. Text Editing Tools (Select All, Copy, Cut, Paste, Cursor Left/Right)
     * 6. Number/Symbol Layout (?123)
     * 7. Theme Cycler
     * 8. More Options
     */
    private fun buildTopToolbar(
        theme: KeyboardThemeMode,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        primaryAccent: Int
    ): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
            )
        }

        // 1. FIRST ACTION: Recognizable ClipVault App Shortcut Icon
        val clipVaultBrandBtn = TextView(this).apply {
            text = "CV"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            background = createRoundedBackground(primaryAccent, Color.parseColor("#38BDF8"), radiusDp = 10)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(32)).apply {
                setMargins(dp(2), 0, dp(6), 0)
            }
            contentDescription = "Open ClipVault App"
            setOnClickListener {
                performKeyFeedback(this)
                launchClipVaultMainActivity(openSettings = false)
            }
            setOnLongClickListener {
                performKeyFeedback(this)
                Toast.makeText(context, "Opening ClipVault Settings", Toast.LENGTH_SHORT).show()
                launchClipVaultMainActivity(openSettings = true)
                true
            }
        }
        bar.addView(clipVaultBrandBtn)

        // Helper for remaining toolbar action buttons
        fun addToolbarAction(
            iconLabel: String,
            desc: String,
            isSelected: Boolean = false,
            onClick: () -> Unit
        ) {
            val btn = TextView(this).apply {
                text = iconLabel
                gravity = Gravity.CENTER
                setTextColor(if (isSelected) Color.WHITE else textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                contentDescription = desc
                background = if (isSelected) {
                    createRoundedBackground(primaryAccent, primaryAccent, radiusDp = 8)
                } else {
                    createRoundedBackground(actionKeyBg, keyBorder, radiusDp = 8)
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    performKeyFeedback(this)
                    onClick()
                }
            }
            bar.addView(btn)
        }

        // 2. Emoji Panel
        addToolbarAction("☺", "Emoji Panel", activePanel == ActivePanel.EMOJI) {
            activePanel = if (activePanel == ActivePanel.EMOJI) ActivePanel.NONE else ActivePanel.EMOJI
            rebuildActiveInputView()
        }

        // 3. Keyboard Settings
        addToolbarAction("⚙", "Keyboard Settings", false) {
            launchClipVaultMainActivity(openSettings = true)
        }

        // 4. Clipboard History Quick Access
        addToolbarAction("📋", "Clipboard History", activePanel == ActivePanel.CLIPBOARD) {
            refreshRecentClipsAsync()
            activePanel = if (activePanel == ActivePanel.CLIPBOARD) ActivePanel.NONE else ActivePanel.CLIPBOARD
            rebuildActiveInputView()
        }

        // 5. Text Editing Tools
        addToolbarAction("✥", "Text Editing Tools", activePanel == ActivePanel.TEXT_EDITING) {
            activePanel = if (activePanel == ActivePanel.TEXT_EDITING) ActivePanel.NONE else ActivePanel.TEXT_EDITING
            rebuildActiveInputView()
        }

        // 6. Number/Symbol Layout
        addToolbarAction("123", "Number and Symbol Layout", symbolPage != SymbolPage.LETTERS && activePanel == ActivePanel.NONE) {
            activePanel = ActivePanel.NONE
            symbolPage = toggleSymbolMode(symbolPage)
            rebuildActiveInputView()
        }

        // 7. Theme Switcher
        addToolbarAction("◐", "Switch Keyboard Theme", false) {
            val nextTheme = cycleNextKeyboardTheme(theme)
            getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
                .edit()
                .putString("keyboard_theme_mode", nextTheme.prefKey)
                .apply()
            rebuildActiveInputView()
        }

        // 8. More Options
        addToolbarAction("⋯", "More Keyboard Options", activePanel == ActivePanel.MORE_OPTIONS) {
            activePanel = if (activePanel == ActivePanel.MORE_OPTIONS) ActivePanel.NONE else ActivePanel.MORE_OPTIONS
            rebuildActiveInputView()
        }

        return bar
    }

    private fun buildQwertyCharacterRow(
        keys: List<String>,
        preset: KeyboardHeightPreset,
        keyBg: Int,
        keyPressedBg: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        primaryAccent: Int,
        horizontalInsetDp: Int
    ): LinearLayout {
        val baseOffset = keyCellViews.size
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(horizontalInsetDp), dp(3), dp(horizontalInsetDp), dp(3))
        }
        keys.forEachIndexed { index, rawKey ->
            val cell = createCharacterKeyCell(
                baseLetter = rawKey,
                globalIndex = baseOffset + index,
                preset = preset,
                keyBg = keyBg,
                keyPressedBg = keyPressedBg,
                actionKeyBg = actionKeyBg,
                keyBorder = keyBorder,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                primaryAccent = primaryAccent
            )
            keyCellViews.add(cell)
            row.addView(cell.container)
        }
        return row
    }

    /**
     * Creates a Samsung/Gboard-style keycap with:
     * - Small secondary number/symbol in the top-right corner (11-13sp in gray)
     * - Large uppercase/lowercase primary character in the center (25-29sp)
     * - Long-press handler to input the secondary symbol with popup indicator
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createCharacterKeyCell(
        baseLetter: String,
        globalIndex: Int,
        preset: KeyboardHeightPreset,
        keyBg: Int,
        keyPressedBg: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        primaryAccent: Int
    ): KeyCellRefs {
        val normalDrawable = createRoundedBackground(keyBg, keyBorder, radiusDp = 8)
        val pressedDrawable = createRoundedBackground(keyPressedBg, primaryAccent, radiusDp = 8)

        val secondaryText = TextView(this).apply {
            text = resolveSecondarySymbol(globalIndex)
            gravity = Gravity.TOP or Gravity.END
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, preset.secondarySp)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setPadding(0, dp(3), dp(5), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val primaryText = TextView(this).apply {
            text = resolveKeyOutput(baseLetter, globalIndex, shiftState, symbolPage)
            gravity = Gravity.CENTER
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, preset.letterSp)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            setPadding(0, dp(4), 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val cellContainer = FrameLayout(this).apply {
            background = normalDrawable
            layoutParams = LinearLayout.LayoutParams(0, dp(preset.keyHeightDp), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            addView(secondaryText)
            addView(primaryText)
        }

        var longPressConsumed = false
        val longPressRunnable = Runnable {
            if (symbolPage == SymbolPage.LETTERS) {
                val sec = resolveSecondarySymbol(globalIndex)
                if (sec.isNotEmpty()) {
                    longPressConsumed = true
                    performKeyFeedback(cellContainer)
                    currentInputConnection?.commitText(sec, 1)
                    showTemporaryKeyPopup("Inserted '" + sec + "'")
                }
            }
        }

        cellContainer.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longPressConsumed = false
                    v.scaleX = 0.96f
                    v.scaleY = 0.96f
                    v.background = pressedDrawable
                    performKeyFeedback(v)
                    mainHandler.postDelayed(longPressRunnable, 270L)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    v.scaleX = 1f
                    v.scaleY = 1f
                    v.background = normalDrawable
                    if (!longPressConsumed) {
                        commitKeyCharacter(
                            baseLetter,
                            globalIndex,
                            preset,
                            primaryAccent,
                            actionKeyBg,
                            keyBorder,
                            textPrimary,
                            textSecondary
                        )
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    v.scaleX = 1f
                    v.scaleY = 1f
                    v.background = normalDrawable
                    true
                }
                else -> false
            }
        }

        return KeyCellRefs(
            container = cellContainer,
            primaryLabel = primaryText,
            secondaryLabel = secondaryText
        )
    }

    private fun showTemporaryKeyPopup(message: String) {
        val banner = longPressHintBanner ?: return
        banner.text = message
        banner.visibility = View.VISIBLE
        mainHandler.removeCallbacksAndMessages(banner)
        mainHandler.postDelayed({ banner.visibility = View.GONE }, 700L)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFunctionKeyView(
        label: String,
        weight: Float,
        heightDp: Int,
        textSizeSp: Float,
        bgColor: Int,
        pressedBgColor: Int,
        borderColor: Int,
        textColor: Int,
        isSpacebar: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        val normalDrawable = createRoundedBackground(bgColor, borderColor, radiusDp = 8)
        val pressedDrawable = createRoundedBackground(pressedBgColor, borderColor, radiusDp = 8)

        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            background = normalDrawable
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.scaleX = 0.96f
                        v.scaleY = 0.96f
                        v.background = pressedDrawable
                        performKeyFeedback(v, isSpacebar = isSpacebar)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.scaleX = 1f
                        v.scaleY = 1f
                        v.background = normalDrawable
                        onClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.scaleX = 1f
                        v.scaleY = 1f
                        v.background = normalDrawable
                        true
                    }
                    else -> false
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createRepeatingBackspaceKey(
        weight: Float,
        heightDp: Int,
        textSizeSp: Float,
        bgColor: Int,
        pressedBgColor: Int,
        borderColor: Int,
        textColor: Int
    ): TextView {
        val normalDrawable = createRoundedBackground(bgColor, borderColor, radiusDp = 8)
        val pressedDrawable = createRoundedBackground(pressedBgColor, borderColor, radiusDp = 8)

        return TextView(this).apply {
            text = "⌫"
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            background = normalDrawable
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.scaleX = 0.95f
                        v.scaleY = 0.95f
                        v.background = pressedDrawable
                        performKeyFeedback(v)
                        handleBackspace()
                        isBackspacePressed = true
                        mainHandler.removeCallbacks(backspaceRepeatRunnable)
                        mainHandler.postDelayed(backspaceRepeatRunnable, 340L)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopBackspaceRepeat()
                        v.scaleX = 1f
                        v.scaleY = 1f
                        v.background = normalDrawable
                        true
                    }
                    else -> false
                }
            }
        }
    }

    /**
     * Interactive Emoji Selection Panel with 8 categories (Recent, Smileys, People, Animals, Food, Travel, Objects, Symbols).
     */
    private fun buildEmojiPanel(
        preset: KeyboardHeightPreset,
        keyBg: Int,
        keyPressedBg: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        primaryAccent: Int
    ): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(preset.keyHeightDp * 4 + 18)
            )
        }

        // Category tab row + Back to ABC button
        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val categoryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(6))
        }

        val backBtn = TextView(this).apply {
            text = "ABC"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = createRoundedBackground(primaryAccent, primaryAccent, radiusDp = 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dp(6), 0) }
            setOnClickListener {
                performKeyFeedback(this)
                activePanel = ActivePanel.NONE
                rebuildActiveInputView()
            }
        }
        categoryRow.addView(backBtn)

        EMOJI_CATEGORIES.forEachIndexed { idx, (catName, _) ->
            val isSel = selectedEmojiCategoryIndex == idx
            val tab = TextView(this).apply {
                text = catName
                setTextColor(if (isSel) Color.WHITE else textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = createRoundedBackground(
                    if (isSel) primaryAccent else actionKeyBg,
                    keyBorder,
                    radiusDp = 8
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(2), 0, dp(2), 0) }
                setOnClickListener {
                    performKeyFeedback(this)
                    selectedEmojiCategoryIndex = idx
                    rebuildActiveInputView()
                }
            }
            categoryRow.addView(tab)
        }
        categoryScroll.addView(categoryRow)
        container.addView(categoryScroll)

        // Emoji grid (8 columns per row)
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val gridColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val emojis = getEmojisForCategory(selectedEmojiCategoryIndex)
        emojis.chunked(7).forEach { rowEmojis ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(2), 0, dp(2))
            }
            rowEmojis.forEach { emoji ->
                val cell = TextView(this).apply {
                    text = emoji
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    background = createRoundedBackground(keyBg, keyBorder, radiusDp = 8)
                    layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                        setMargins(dp(2), 0, dp(2), 0)
                    }
                    setOnClickListener {
                        performKeyFeedback(this)
                        currentInputConnection?.commitText(emoji, 1)
                        recordRecentEmoji(emoji)
                    }
                }
                r.addView(cell)
            }
            gridColumn.addView(r)
        }
        scroll.addView(gridColumn)
        container.addView(scroll)

        return container
    }

    private fun recordRecentEmoji(emoji: String) {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val current = (prefs.getString("keyboard_recent_emojis", "😂,❤️,🔥,👍,🎉,✨,🙏") ?: "")
            .split(",")
            .filter { it.isNotBlank() && it != emoji }
        val updated = (listOf(emoji) + current).take(14)
        prefs.edit().putString("keyboard_recent_emojis", updated.joinToString(",")).apply()
    }

    private fun getEmojisForCategory(categoryIndex: Int): List<String> {
        if (categoryIndex == 0) {
            val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
            val saved = (prefs.getString("keyboard_recent_emojis", "😂,❤️,🔥,👍,🎉,✨,🙏,😊,🚀,💯,✅,⭐,💡,🎯") ?: "")
                .split(",")
                .filter { it.isNotBlank() }
            if (saved.isNotEmpty()) return saved
        }
        return EMOJI_CATEGORIES.getOrElse(categoryIndex) { EMOJI_CATEGORIES[1] }.second
    }

    /**
     * Compact ClipVault Clipboard History Quick Panel inside the keyboard.
     * Displays recent saved clips from Room and inserts into the active editor ONLY on explicit user tap.
     */
    private fun buildClipboardQuickPanel(
        preset: KeyboardHeightPreset,
        keyBg: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        primaryAccent: Int
    ): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(preset.keyHeightDp * 4 + 18)
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(6))
        }

        val title = TextView(this).apply {
            text = "ClipVault Quick Paste (" + recentClipsCache.size + ")"
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val openAppBtn = TextView(this).apply {
            text = "Open App"
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = createRoundedBackground(actionKeyBg, keyBorder, radiusDp = 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dp(6), 0) }
            setOnClickListener {
                performKeyFeedback(this)
                launchClipVaultMainActivity(openSettings = false)
            }
        }

        val backToKeysBtn = TextView(this).apply {
            text = "ABC ⌨"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = createRoundedBackground(primaryAccent, primaryAccent, radiusDp = 8)
            setOnClickListener {
                performKeyFeedback(this)
                activePanel = ActivePanel.NONE
                rebuildActiveInputView()
            }
        }

        header.addView(title)
        header.addView(openAppBtn)
        header.addView(backToKeysBtn)
        container.addView(header)

        val isPasswordEditor = currentInputEditorInfo?.let { isInputFieldPassword(it.inputType) } ?: false
        if (isPasswordEditor) {
            val warning = TextView(this).apply {
                text = "Clipboard quick-paste is disabled in password fields for security."
                setTextColor(textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(24), dp(12), dp(24))
            }
            container.addView(warning)
            return container
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val listCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (recentClipsCache.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No recent clips yet. Copy text anywhere to save to ClipVault."
                setTextColor(textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(24), dp(12), dp(24))
            }
            listCol.addView(emptyView)
        } else {
            recentClipsCache.forEach { clip ->
                val itemRow = TextView(this).apply {
                    text = clip.content.replace("\n", " ")
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(textPrimary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                    background = createRoundedBackground(keyBg, keyBorder, radiusDp = 8)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, dp(2), 0, dp(2)) }
                    setOnClickListener {
                        performKeyFeedback(this)
                        // Insert ONLY after explicit user tap
                        currentInputConnection?.commitText(clip.content, 1)
                    }
                }
                listCol.addView(itemRow)
            }
        }

        scroll.addView(listCol)
        container.addView(scroll)
        return container
    }

    /**
     * Useful Text Editing Panel: Select All, Copy, Cut, Paste, Move Cursor Left, Move Cursor Right.
     */
    private fun buildTextEditingPanel(
        preset: KeyboardHeightPreset,
        keyBg: Int,
        keyPressedBg: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        primaryAccent: Int
    ): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(preset.keyHeightDp * 4 + 18)
            )
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(8))
        }
        val title = TextView(this).apply {
            text = "Text Editing & Cursor Controls"
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "ABC ⌨"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = createRoundedBackground(primaryAccent, primaryAccent, radiusDp = 8)
            setOnClickListener {
                performKeyFeedback(this)
                activePanel = ActivePanel.NONE
                rebuildActiveInputView()
            }
        }
        topRow.addView(title)
        topRow.addView(closeBtn)
        container.addView(topRow)

        val actionsRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3), 0, dp(3))
        }
        actionsRow1.addView(
            createFunctionKeyView("Select All", 1f, preset.keyHeightDp, 14f, keyBg, keyPressedBg, keyBorder, textPrimary) {
                currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
            }
        )
        actionsRow1.addView(
            createFunctionKeyView("Copy", 1f, preset.keyHeightDp, 14f, keyBg, keyPressedBg, keyBorder, textPrimary) {
                currentInputConnection?.performContextMenuAction(android.R.id.copy)
                capturePermittedClipboard()
            }
        )
        actionsRow1.addView(
            createFunctionKeyView("Cut", 1f, preset.keyHeightDp, 14f, keyBg, keyPressedBg, keyBorder, textPrimary) {
                currentInputConnection?.performContextMenuAction(android.R.id.cut)
                capturePermittedClipboard()
            }
        )
        actionsRow1.addView(
            createFunctionKeyView("Paste", 1f, preset.keyHeightDp, 14f, primaryAccent, primaryAccent, primaryAccent, Color.WHITE) {
                currentInputConnection?.performContextMenuAction(android.R.id.paste)
            }
        )
        container.addView(actionsRow1)

        val actionsRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(3))
        }
        actionsRow2.addView(
            createFunctionKeyView("◀ Cursor Left", 1f, preset.keyHeightDp, 14f, actionKeyBg, keyPressedBg, keyBorder, textPrimary) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            }
        )
        actionsRow2.addView(
            createFunctionKeyView("Cursor Right ▶", 1f, preset.keyHeightDp, 14f, actionKeyBg, keyPressedBg, keyBorder, textPrimary) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
        )
        actionsRow2.addView(
            createRepeatingBackspaceKey(1f, preset.keyHeightDp, 16f, actionKeyBg, keyPressedBg, keyBorder, textPrimary)
        )
        container.addView(actionsRow2)

        return container
    }

    /**
     * More Options Panel inside keyboard: Quick toggles for Height, Haptic Intensity, Theme, and Switch IME.
     */
    private fun buildMoreOptionsPanel(
        preset: KeyboardHeightPreset,
        keyBg: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        primaryAccent: Int
    ): View {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val currentIntensity = getVibrationIntensity()
        val currentTheme = KeyboardThemeMode.fromKey(prefs.getString("keyboard_theme_mode", "DARK_CHARCOAL"))

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(preset.keyHeightDp * 4 + 18)
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(6))
        }
        val title = TextView(this).apply {
            text = "ClipVault Keyboard Quick Settings"
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "Done"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = createRoundedBackground(primaryAccent, primaryAccent, radiusDp = 8)
            setOnClickListener {
                performKeyFeedback(this)
                activePanel = ActivePanel.NONE
                rebuildActiveInputView()
            }
        }
        header.addView(title)
        header.addView(closeBtn)
        container.addView(header)

        // Row 1: Vibration Intensity Cycle + Height Cycle
        val r1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3), 0, dp(3))
        }
        r1.addView(
            createFunctionKeyView(
                label = "Haptics: " + currentIntensity.displayName,
                weight = 1f,
                heightDp = 44,
                textSizeSp = 13f,
                bgColor = keyBg,
                pressedBgColor = actionKeyBg,
                borderColor = keyBorder,
                textColor = textPrimary
            ) {
                val next = cycleNextVibrationIntensity(currentIntensity)
                prefs.edit()
                    .putBoolean("keyboard_vibrate_enabled", next != VibrationIntensity.OFF)
                    .putString("keyboard_vibration_intensity", next.prefKey)
                    .apply()
                rebuildActiveInputView()
            }
        )
        r1.addView(
            createFunctionKeyView(
                label = "Height: " + preset.prefKey,
                weight = 1f,
                heightDp = 44,
                textSizeSp = 13f,
                bgColor = keyBg,
                pressedBgColor = actionKeyBg,
                borderColor = keyBorder,
                textColor = textPrimary
            ) {
                val nextH = when (preset) {
                    KeyboardHeightPreset.COMPACT -> KeyboardHeightPreset.NORMAL
                    KeyboardHeightPreset.NORMAL -> KeyboardHeightPreset.TALL
                    KeyboardHeightPreset.TALL -> KeyboardHeightPreset.COMPACT
                }
                prefs.edit().putString("keyboard_height_mode", nextH.prefKey).apply()
                rebuildActiveInputView()
            }
        )
        container.addView(r1)

        // Row 2: Theme Cycle + Switch Keyboard Picker
        val r2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3), 0, dp(3))
        }
        r2.addView(
            createFunctionKeyView(
                label = "Theme: " + currentTheme.displayName,
                weight = 1f,
                heightDp = 44,
                textSizeSp = 13f,
                bgColor = keyBg,
                pressedBgColor = actionKeyBg,
                borderColor = keyBorder,
                textColor = textPrimary
            ) {
                val nextT = cycleNextKeyboardTheme(currentTheme)
                prefs.edit().putString("keyboard_theme_mode", nextT.prefKey).apply()
                rebuildActiveInputView()
            }
        )
        r2.addView(
            createFunctionKeyView(
                label = "Switch Keyboard ⌨",
                weight = 1f,
                heightDp = 44,
                textSizeSp = 13f,
                bgColor = actionKeyBg,
                pressedBgColor = keyBg,
                borderColor = keyBorder,
                textColor = textPrimary
            ) {
                switchInputMethodSafely()
            }
        )
        container.addView(r2)

        return container
    }

    private fun stopBackspaceRepeat() {
        isBackspacePressed = false
        mainHandler.removeCallbacks(backspaceRepeatRunnable)
    }

    private fun resolveLeftModifierLabel(): String {
        return when (symbolPage) {
            SymbolPage.LETTERS -> when (shiftState) {
                ShiftState.CAPS_LOCK -> "⇪"
                ShiftState.SHIFTED_SINGLE -> "⬆"
                ShiftState.LOWERCASE -> "⇧"
            }
            SymbolPage.NUMBERS_SYMBOLS_1 -> "=/<"
            SymbolPage.SYMBOLS_2 -> "?123"
        }
    }

    private fun onLeftModifierTapped(
        primaryAccent: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        preset: KeyboardHeightPreset
    ) {
        if (symbolPage == SymbolPage.LETTERS) {
            val now = SystemClock.uptimeMillis()
            shiftState = computeNextShiftState(shiftState, now - lastShiftTapUptimeMs)
            lastShiftTapUptimeMs = now
        } else {
            symbolPage = if (symbolPage == SymbolPage.NUMBERS_SYMBOLS_1) {
                SymbolPage.SYMBOLS_2
            } else {
                SymbolPage.NUMBERS_SYMBOLS_1
            }
        }
        refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset)
    }

    private fun commitKeyCharacter(
        baseLetter: String,
        index: Int,
        preset: KeyboardHeightPreset,
        primaryAccent: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int
    ) {
        val ic = currentInputConnection ?: return
        val output = resolveKeyOutput(baseLetter, index, shiftState, symbolPage)
        ic.commitText(output, 1)

        if (symbolPage == SymbolPage.LETTERS && shiftState == ShiftState.SHIFTED_SINGLE) {
            shiftState = ShiftState.LOWERCASE
            refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset)
        }
    }

    private fun refreshKeyLabels(
        primaryAccent: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        preset: KeyboardHeightPreset
    ) {
        keyCellViews.forEachIndexed { idx, cell ->
            val baseChar = BASE_QWERTY_KEYS.getOrElse(idx) { "" }
            cell.primaryLabel.text = resolveKeyOutput(baseChar, idx, shiftState, symbolPage)
            val targetSp = when {
                symbolPage != SymbolPage.LETTERS && idx < 10 -> preset.numberSp
                symbolPage != SymbolPage.LETTERS -> preset.numberSp - 0.5f
                else -> preset.letterSp
            }
            cell.primaryLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, targetSp)
            cell.secondaryLabel.visibility = if (symbolPage == SymbolPage.LETTERS) View.VISIBLE else View.INVISIBLE
            cell.secondaryLabel.setTextColor(textSecondary)
        }

        val isShiftHighlighted = symbolPage == SymbolPage.LETTERS && shiftState != ShiftState.LOWERCASE
        shiftKeyView?.apply {
            text = resolveLeftModifierLabel()
            background = createRoundedBackground(
                if (isShiftHighlighted) primaryAccent else actionKeyBg,
                keyBorder,
                radiusDp = 8
            )
            setTextColor(if (isShiftHighlighted) Color.WHITE else textPrimary)
        }
        modeKeyView?.text = if (symbolPage == SymbolPage.LETTERS) "?123" else "ABC"
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun handleEnterAction() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val actionId = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(actionId)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun switchInputMethodSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val switched = switchToNextInputMethod(false)
                if (switched) return
            }
        } catch (_: Exception) {
        }
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        } catch (_: Exception) {
        }
    }

    object ListenerRegistrationController {
        fun markRegistered(currentlyRegistered: Boolean): Boolean = !currentlyRegistered
        fun markUnregistered(currentlyRegistered: Boolean): Boolean = currentlyRegistered
    }

    companion object {
        const val EXTRA_OPEN_CLIPVAULT_SETTINGS = "com.clipvault.android.EXTRA_OPEN_CLIPVAULT_SETTINGS"

        @Volatile
        var isActiveInstance: Boolean = false
            private set

        @Volatile
        var isInputViewVisibleState: Boolean = false
            private set

        private const val DOUBLE_TAP_CAPS_THRESHOLD_MS = 360L

        private val BASE_QWERTY_KEYS = listOf(
            "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
            "a", "s", "d", "f", "g", "h", "j", "k", "l",
            "z", "x", "c", "v", "b", "n", "m"
        )

        // Secondary numbers (Q=1..P=0) and symbols displayed above primary letters and committed on long-press
        private val SECONDARY_QWERTY_SYMBOLS = listOf(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
            "@", "#", "$", "_", "&", "-", "+", "(", ")",
            "*", "\"", "'", ":", ";", "!", "?"
        )

        private val SYMBOL_KEYS_PAGE_1 = listOf(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
            "@", "#", "$", "_", "&", "-", "+", "(", ")",
            "*", "\"", "'", ":", ";", "!", "?"
        )

        private val SYMBOL_KEYS_PAGE_2 = listOf(
            "~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆",
            "£", "€", "¥", "^", "°", "=", "{", "}", "\\",
            "%", "©", "®", "™", "✓", "[", "]"
        )

        private val EMOJI_CATEGORIES: List<Pair<String, List<String>>> = listOf(
            "Recent" to listOf("😂", "❤️", "🔥", "👍", "🎉", "✨", "🙏", "😊", "🚀", "💯", "✅", "⭐", "💡", "🎯"),
            "Smileys" to listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "😉", "😍", "🥰", "😘", "😎", "🤩", "🤔", "😴", "🥳", "🤯"),
            "People" to listOf("👍", "👎", "👏", "🙌", "🤝", "🙏", "💪", "🫶", "👋", "✌️", "🤞", "👌", "🫡", "🫂"),
            "Animals" to listOf("🐶", "🐱", "🦁", "🐼", "🦊", "🐨", "🐯", "🦄", "🐝", "🦋", "🐢", "🐬", "🦉", "🦖"),
            "Food" to listOf("🍕", "🍔", "🍟", "🌮", "🍣", "🍜", "🍩", "🍪", "🎂", "☕", "🍵", "🥤", "🍎", "🍉"),
            "Travel" to listOf("🚗", "✈️", "🚀", "🛸", "🚆", "⛵", "🏖️", "🏔️", "🗽", "🗼", "🏰", "🌍", "🧭", "⛺"),
            "Objects" to listOf("💻", "📱", "⌚", "📷", "🎧", "🔑", "🔒", "💡", "📌", "📎", "📝", "📚", "🎁", "💎"),
            "Symbols" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "✨", "🔥", "💯", "✅", "❌", "⚡")
        )

        /**
         * Returns the secondary number or symbol mapped to a QWERTY key index (e.g., Q=0 -> "1", P=9 -> "0").
         */
        fun resolveSecondarySymbol(index: Int): String =
            SECONDARY_QWERTY_SYMBOLS.getOrElse(index) { "" }

        /**
         * Cycles through the 4 Keyboard Themes in order.
         */
        fun cycleNextKeyboardTheme(current: KeyboardThemeMode): KeyboardThemeMode {
            return when (current) {
                KeyboardThemeMode.DARK_CHARCOAL -> KeyboardThemeMode.PURE_BLACK
                KeyboardThemeMode.PURE_BLACK -> KeyboardThemeMode.LIGHT
                KeyboardThemeMode.LIGHT -> KeyboardThemeMode.FOLLOW_SYSTEM
                KeyboardThemeMode.FOLLOW_SYSTEM -> KeyboardThemeMode.DARK_CHARCOAL
            }
        }

        /**
         * Cycles through the 4 Haptic Feedback Vibration Intensities in order.
         */
        fun cycleNextVibrationIntensity(current: VibrationIntensity): VibrationIntensity {
            return when (current) {
                VibrationIntensity.OFF -> VibrationIntensity.LIGHT
                VibrationIntensity.LIGHT -> VibrationIntensity.MEDIUM
                VibrationIntensity.MEDIUM -> VibrationIntensity.STRONG
                VibrationIntensity.STRONG -> VibrationIntensity.OFF
            }
        }

        /**
         * Pure state-machine transition for Shift / Caps Lock (tested in unit tests).
         */
        fun computeNextShiftState(current: ShiftState, elapsedSinceLastTapMs: Long): ShiftState {
            return when (current) {
                ShiftState.LOWERCASE -> ShiftState.SHIFTED_SINGLE
                ShiftState.SHIFTED_SINGLE -> {
                    if (elapsedSinceLastTapMs in 0..DOUBLE_TAP_CAPS_THRESHOLD_MS) {
                        ShiftState.CAPS_LOCK
                    } else {
                        ShiftState.LOWERCASE
                    }
                }
                ShiftState.CAPS_LOCK -> ShiftState.LOWERCASE
            }
        }

        /**
         * Pure helper toggling between ABC letters and 123 numbers/symbols.
         */
        fun toggleSymbolMode(current: SymbolPage): SymbolPage {
            return if (current == SymbolPage.LETTERS) {
                SymbolPage.NUMBERS_SYMBOLS_1
            } else {
                SymbolPage.LETTERS
            }
        }

        /**
         * Resolves the output character for a given key index, ShiftState, and SymbolPage.
         */
        fun resolveKeyOutput(
            baseLetter: String,
            index: Int,
            shiftState: ShiftState,
            symbolPage: SymbolPage
        ): String {
            return when (symbolPage) {
                SymbolPage.NUMBERS_SYMBOLS_1 -> SYMBOL_KEYS_PAGE_1.getOrElse(index) { baseLetter }
                SymbolPage.SYMBOLS_2 -> SYMBOL_KEYS_PAGE_2.getOrElse(index) { baseLetter }
                SymbolPage.LETTERS -> {
                    if (shiftState == ShiftState.SHIFTED_SINGLE || shiftState == ShiftState.CAPS_LOCK) {
                        baseLetter.uppercase()
                    } else {
                        baseLetter.lowercase()
                    }
                }
            }
        }

        /**
         * Determines whether an input field's [inputType] represents a password or sensitive credential field.
         */
        fun isInputFieldPassword(inputType: Int): Boolean {
            if (inputType == 0) return false
            val klass = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            if (klass == InputType.TYPE_CLASS_TEXT) {
                return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            }
            if (klass == InputType.TYPE_CLASS_NUMBER) {
                return variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            return false
        }
    }
}
