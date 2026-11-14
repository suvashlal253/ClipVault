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
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.clipvault.android.data.local.AppDatabase
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.keyboard.transliteration.BengaliCandidate
import com.clipvault.android.keyboard.transliteration.BengaliPhoneticEngine
import com.clipvault.android.keyboard.transliteration.BengaliSuggestionEngine
import com.clipvault.android.keyboard.transliteration.BengaliTransliterationRules
import com.clipvault.android.keyboard.transliteration.BengaliUserDictionary
import com.clipvault.android.keyboard.transliteration.KeyboardLanguageMode
import com.clipvault.android.service.ClipboardServiceHelper
import com.clipvault.android.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * ClipVault Keyboard (IME) v1.6.0 — Bilingual English + বাংলা Phonetic Keyboard
 * with real-time incremental Bengali transliteration, smart suggestion bar, personal dictionary,
 * Spacebar horizontal swipe & Globe language switching, top 8-icon toolbar (ClipVault App Shortcut first),
 * secondary numbers/symbols (Q=1..P=0), 4-level haptic feedback, Emoji panel, Clipboard quick panel,
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
        MORE_OPTIONS,
        LANGUAGE_MENU
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

    // Bilingual Engine & Composition State (v1.6.0)
    private lateinit var userDictionary: BengaliUserDictionary
    private lateinit var phoneticEngine: BengaliPhoneticEngine
    private lateinit var suggestionEngine: BengaliSuggestionEngine

    private var languageMode: KeyboardLanguageMode = KeyboardLanguageMode.ENGLISH
    private val composingRomanBuffer = StringBuilder()
    private var activeComposingPreview: String = ""
    private var previousCommittedWord: String = ""
    private var currentCandidates: List<BengaliCandidate> = emptyList()
    private var isInternalComposingUpdate: Boolean = false

    private var shiftState: ShiftState = ShiftState.SHIFTED_SINGLE
    private var symbolPage: SymbolPage = SymbolPage.LETTERS
    private var activePanel: ActivePanel = ActivePanel.NONE
    private var selectedEmojiCategoryIndex: Int = 1 // Default to Smileys
    private var lastShiftTapUptimeMs: Long = 0L

    private val keyCellViews = mutableListOf<KeyCellRefs>()
    private var shiftKeyView: TextView? = null
    private var modeKeyView: TextView? = null
    private var spacebarView: TextView? = null
    private var globeKeyView: TextView? = null
    private var longPressHintBanner: TextView? = null
    private var suggestionChipsRow: LinearLayout? = null
    private var suggestionLangBadge: TextView? = null

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

        userDictionary = BengaliUserDictionary(applicationContext)
        phoneticEngine = BengaliPhoneticEngine(userDictionary)
        suggestionEngine = BengaliSuggestionEngine(phoneticEngine)
        languageMode = resolveStartingLanguageMode()

        registerClipboardListenerIfNeeded()
        refreshRecentClipsAsync()
    }

    private fun resolveStartingLanguageMode(): KeyboardLanguageMode {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val banglaEnabled = prefs.getBoolean("keyboard_bangla_phonetic_enabled", true)
        if (!banglaEnabled) return KeyboardLanguageMode.ENGLISH

        val rememberLast = prefs.getBoolean("keyboard_remember_last_language", true)
        val rawCode = if (rememberLast) {
            prefs.getString("keyboard_last_language", null)
                ?: prefs.getString("keyboard_default_language", KeyboardLanguageMode.ENGLISH.prefCode)
        } else {
            prefs.getString("keyboard_default_language", KeyboardLanguageMode.ENGLISH.prefCode)
        }
        return KeyboardLanguageMode.fromPrefCode(rawCode)
    }

    private fun persistLanguageMode(mode: KeyboardLanguageMode) {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("keyboard_last_language", mode.prefCode).apply()
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
        resetComposingState(finishEditorComposing = false)
        registerClipboardListenerIfNeeded()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isInputViewVisibleState = true
        languageMode = resolveStartingLanguageMode()
        resetComposingState(finishEditorComposing = false)
        registerClipboardListenerIfNeeded()
        refreshRecentClipsAsync()
        setInputView(buildKeyboardLayout())
        capturePermittedClipboard()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (isInternalComposingUpdate) return

        // Part 2E: If user moves the cursor outside the active composing span into previously committed text,
        // safely finish the composition so existing text is never overwritten or corrupted.
        if (composingRomanBuffer.isNotEmpty()) {
            val movedOutsideComposition = candidatesStart >= 0 &&
                (newSelStart < candidatesStart || newSelEnd > candidatesEnd || newSelStart != newSelEnd)
            val cursorJumpedNonSequentially = candidatesStart < 0 && abs(newSelStart - oldSelStart) > 1
            if (movedOutsideComposition || cursorJumpedNonSequentially) {
                finishCurrentCompositionSafely()
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        finishCurrentCompositionSafely()
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

    private fun resetComposingState(finishEditorComposing: Boolean) {
        composingRomanBuffer.clear()
        activeComposingPreview = ""
        if (finishEditorComposing) {
            try {
                currentInputConnection?.finishComposingText()
            } catch (_: Exception) {
            }
        }
        updateSuggestionsAndRefreshBar()
    }

    private fun finishCurrentCompositionSafely() {
        if (composingRomanBuffer.isNotEmpty()) {
            val committed = activeComposingPreview.ifEmpty { composingRomanBuffer.toString() }
            previousCommittedWord = committed
            composingRomanBuffer.clear()
            activeComposingPreview = ""
            try {
                currentInputConnection?.finishComposingText()
            } catch (_: Exception) {
            }
            updateSuggestionsAndRefreshBar()
        }
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
     * and respects the separate "Vibrate on Spacebar" preference. Works identically in English and Bangla modes.
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
     * Switches between English (QWERTY) and বাংলা (Bangla Phonetic) cleanly without inserting spaces.
     */
    private fun switchKeyboardLanguage(targetMode: KeyboardLanguageMode? = null, viaSwipe: Boolean = false) {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val banglaEnabled = prefs.getBoolean("keyboard_bangla_phonetic_enabled", true)
        if (!banglaEnabled && targetMode != KeyboardLanguageMode.ENGLISH) {
            showTemporaryKeyPopup("Enable Bangla Phonetic in Settings")
            return
        }

        // Safely commit any active composition before switching language
        finishCurrentCompositionSafely()
        suggestionEngine.clearLastCorrectionState()

        languageMode = targetMode ?: languageMode.toggle()
        persistLanguageMode(languageMode)

        spacebarView?.text = resolveSpacebarLabel(languageMode)
        globeKeyView?.text = "🌐 " + languageMode.badgeText
        suggestionLangBadge?.text = languageMode.badgeText

        updateSuggestionsAndRefreshBar()
        val methodDesc = if (viaSwipe) "Swiped to " else "Switched to "
        showTemporaryKeyPopup(methodDesc + languageMode.displayName)
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
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val theme = resolveEffectiveTheme()
        val preset = getKeyboardHeightPreset()
        val showNumberRow = prefs.getBoolean("keyboard_show_number_row", false)

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
            setPadding(dp(4), dp(3), dp(4), dp(8))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. TOP ICON TOOLBAR (ClipVault Shortcut first + 7 tool icons)
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

        // 2. BILINGUAL SMART SUGGESTION BAR (3-5 Bengali or English candidates + Personal Dictionary save)
        if (activePanel == ActivePanel.NONE) {
            root.addView(
                buildSuggestionBar(
                    keyBg = keyBg,
                    actionKeyBg = actionKeyBg,
                    keyBorder = keyBorder,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    primaryAccent = primaryAccent
                )
            )
        }

        // Subtle long-press / language transition feedback banner
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

        // 3. ACTIVE BODY: Either one of the interactive tool panels or QWERTY rows
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
            ActivePanel.LANGUAGE_MENU -> {
                root.addView(buildLanguageMenuPanel(preset, keyBg, actionKeyBg, keyBorder, textPrimary, textSecondary, primaryAccent))
            }
            ActivePanel.NONE -> {
                // Optional Dedicated Number Row (1..0 or ১..০)
                if (showNumberRow && symbolPage == SymbolPage.LETTERS) {
                    root.addView(
                        buildDedicatedNumberRow(
                            preset = preset,
                            keyBg = keyBg,
                            keyPressedBg = keyPressedBg,
                            keyBorder = keyBorder,
                            textPrimary = textPrimary
                        )
                    )
                }

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

                // Row 4: ?123 + Globe (Language Switch) + Comma + Spacebar (with Swipe) + Period + Enter
                val row4 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, dp(3), 0, dp(2))
                }

                val symBtn = createFunctionKeyView(
                    label = if (symbolPage == SymbolPage.LETTERS) "?123" else "ABC",
                    weight = 1.35f,
                    heightDp = preset.keyHeightDp,
                    textSizeSp = preset.functionSp - 1.5f,
                    bgColor = actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary
                ) {
                    finishCurrentCompositionSafely()
                    symbolPage = toggleSymbolMode(symbolPage)
                    refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset)
                }
                modeKeyView = symBtn
                row4.addView(symBtn)

                // Dedicated Globe / Language Switch Key (Tap: English <-> বাংলা, Long-press: Language Selection Menu)
                val globeBtn = createGlobeLanguageKey(
                    weight = 1.15f,
                    heightDp = preset.keyHeightDp,
                    bgColor = actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary,
                    primaryAccent = primaryAccent
                )
                globeKeyView = globeBtn
                row4.addView(globeBtn)

                val commaBtn = createFunctionKeyView(
                    label = ",",
                    weight = 0.88f,
                    heightDp = preset.keyHeightDp,
                    textSizeSp = preset.letterSp - 2f,
                    bgColor = keyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary
                ) {
                    commitPunctuationOrSeparator(",")
                }
                row4.addView(commaBtn)

                // Spacebar with horizontal swipe gesture detection (English <-> বাংলা)
                val spaceBtn = createSwipeableSpacebarView(
                    weight = 3.9f,
                    heightDp = preset.keyHeightDp,
                    bgColor = keyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textSecondary,
                    primaryAccent = primaryAccent
                )
                spacebarView = spaceBtn
                row4.addView(spaceBtn)

                val periodBtn = createFunctionKeyView(
                    label = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) "।" else ".",
                    weight = 0.88f,
                    heightDp = preset.keyHeightDp,
                    textSizeSp = preset.letterSp - 2f,
                    bgColor = keyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary
                ) {
                    val mark = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) "।" else "."
                    commitPunctuationOrSeparator(mark)
                }
                row4.addView(periodBtn)

                val enterBtn = createFunctionKeyView(
                    label = "↵",
                    weight = 1.48f,
                    heightDp = preset.keyHeightDp,
                    textSizeSp = preset.functionSp + 2f,
                    bgColor = primaryAccent,
                    pressedBgColor = primaryAccent,
                    borderColor = primaryAccent,
                    textColor = Color.WHITE
                ) {
                    finishCurrentCompositionSafely()
                    handleEnterAction()
                }
                row4.addView(enterBtn)
                root.addView(row4)

                refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset)
                updateSuggestionsAndRefreshBar()
            }
        }

        return root
    }

    /**
     * Builds the fixed-height Bilingual Suggestion Bar directly above the keyboard.
     * Never causes keyboard layout jumping.
     */
    private fun buildSuggestionBar(
        keyBg: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        primaryAccent: Int
    ): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(38)
            )
        }

        val langBadge = TextView(this).apply {
            text = languageMode.badgeText
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = createRoundedBackground(
                if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) Color.parseColor("#0D9488") else actionKeyBg,
                keyBorder,
                radiusDp = 6
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(30)
            ).apply {
                setMargins(0, 0, dp(6), 0)
            }
            setOnClickListener {
                performKeyFeedback(this)
                switchKeyboardLanguage(viaSwipe = false)
            }
        }
        suggestionLangBadge = langBadge
        container.addView(langBadge)

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        val chipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        suggestionChipsRow = chipsRow
        scroll.addView(chipsRow)
        container.addView(scroll)

        return container
    }

    /**
     * Recomputes bilingual suggestions for the current composing word or next-word context
     * and updates the suggestion bar in-place with zero layout jumping.
     */
    private fun updateSuggestionsAndRefreshBar() {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val suggestionsEnabled = prefs.getBoolean("keyboard_suggestions_enabled", true)
        val autocorrectEnabled = prefs.getBoolean("keyboard_autocorrect_enabled", true)
        val nextWordEnabled = prefs.getBoolean("keyboard_next_word_prediction", true)
        val banglaNumerals = prefs.getBoolean("keyboard_bangla_numerals", false)

        currentCandidates = suggestionEngine.getSuggestions(
            composingText = composingRomanBuffer.toString(),
            previousWord = previousCommittedWord,
            languageMode = languageMode,
            suggestionsEnabled = suggestionsEnabled,
            autocorrectEnabled = autocorrectEnabled,
            nextWordPredictionEnabled = nextWordEnabled,
            useBengaliNumerals = banglaNumerals,
            maxSuggestions = 5
        )

        val row = suggestionChipsRow ?: return
        row.removeAllViews()

        val theme = resolveEffectiveTheme()
        val keyBg = Color.parseColor(theme.keyBgHex)
        val actionKeyBg = Color.parseColor(theme.modifierBgHex)
        val keyBorder = Color.parseColor(theme.borderHex)
        val textPrimary = Color.parseColor(theme.primaryTextHex)
        val textSecondary = Color.parseColor(theme.secondaryTextHex)
        val primaryAccent = Color.parseColor(theme.enterAccentHex)

        suggestionLangBadge?.apply {
            text = languageMode.badgeText
            background = createRoundedBackground(
                if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) Color.parseColor("#0D9488") else actionKeyBg,
                keyBorder,
                radiusDp = 6
            )
        }

        if (!suggestionsEnabled) {
            val disabledLabel = TextView(this).apply {
                text = "Suggestions Off"
                setTextColor(textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }
            row.addView(disabledLabel)
            return
        }

        if (currentCandidates.isEmpty()) {
            val hint = TextView(this).apply {
                text = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) {
                    "Type Banglish (e.g. ami -> আমি, suvash -> সুভাষ)"
                } else {
                    "ClipVault Bilingual Keyboard (Swipe Space for বাংলা)"
                }
                setTextColor(textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(6), dp(4), dp(6), dp(4))
            }
            row.addView(hint)
            return
        }

        currentCandidates.forEachIndexed { index, candidate ->
            val isHighlighted = candidate.isPrimary && composingRomanBuffer.isNotEmpty()
            val chip = TextView(this).apply {
                text = candidate.text
                gravity = Gravity.CENTER
                setTextColor(if (isHighlighted) Color.WHITE else textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                typeface = if (isHighlighted) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setPadding(dp(12), dp(4), dp(12), dp(4))
                background = createRoundedBackground(
                    if (isHighlighted) primaryAccent else keyBg,
                    if (isHighlighted) primaryAccent else keyBorder,
                    radiusDp = 8
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(30)
                ).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener {
                    performKeyFeedback(this)
                    commitSelectedCandidate(candidate)
                }
            }
            row.addView(chip)

            // Subtle divider between top candidates
            if (index < currentCandidates.size - 1) {
                val div = View(this).apply {
                    setBackgroundColor(keyBorder)
                    layoutParams = LinearLayout.LayoutParams(dp(1), dp(16)).apply {
                        setMargins(dp(1), 0, dp(1), 0)
                    }
                }
                row.addView(div)
            }
        }

        // Personal Dictionary quick "+ Save" chip when user is composing a word
        val rawRoman = composingRomanBuffer.toString().trim()
        val topCandidate = currentCandidates.firstOrNull()?.text.orEmpty()
        if (rawRoman.length >= 2 && topCandidate.isNotEmpty()) {
            val saveChip = TextView(this).apply {
                text = "+ Save"
                gravity = Gravity.CENTER
                setTextColor(textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(9), dp(4), dp(9), dp(4))
                background = createRoundedBackground(actionKeyBg, keyBorder, radiusDp = 8)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(28)
                ).apply {
                    setMargins(dp(6), 0, dp(2), 0)
                }
                setOnClickListener {
                    performKeyFeedback(this)
                    userDictionary.addOrUpdateWord(rawRoman, topCandidate, languageMode)
                    showTemporaryKeyPopup("Saved '" + rawRoman + " → " + topCandidate + "'")
                }
            }
            row.addView(saveChip)
        }
    }

    /**
     * Commits a user-tapped suggestion candidate into the active InputConnection,
     * records personal dictionary preference, and advances to next-word prediction.
     */
    private fun commitSelectedCandidate(candidate: BengaliCandidate) {
        val ic = currentInputConnection ?: return
        val rawRoman = composingRomanBuffer.toString()

        isInternalComposingUpdate = true
        try {
            ic.beginBatchEdit()
            ic.commitText(candidate.text + " ", 1)
            ic.finishComposingText()
            ic.endBatchEdit()
        } finally {
            isInternalComposingUpdate = false
        }

        suggestionEngine.onCandidateSelectedByUser(
            rawRoman = rawRoman,
            selectedWord = candidate.text,
            languageMode = languageMode
        )

        previousCommittedWord = candidate.text
        composingRomanBuffer.clear()
        activeComposingPreview = ""
        updateSuggestionsAndRefreshBar()
    }

    /**
     * Builds optional dedicated top number row (1..0 or ১..০).
     */
    private fun buildDedicatedNumberRow(
        preset: KeyboardHeightPreset,
        keyBg: Int,
        keyPressedBg: Int,
        keyBorder: Int,
        textPrimary: Int
    ): View {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val useBanglaDigits = languageMode == KeyboardLanguageMode.BANGLA_PHONETIC &&
            prefs.getBoolean("keyboard_bangla_numerals", false)
        val digits = if (useBanglaDigits) {
            listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০")
        } else {
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(2))
        }
        digits.forEach { d ->
            row.addView(
                createFunctionKeyView(
                    label = d,
                    weight = 1f,
                    heightDp = (preset.keyHeightDp * 0.78f).toInt(),
                    textSizeSp = 16.5f,
                    bgColor = keyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary
                ) {
                    finishCurrentCompositionSafely()
                    currentInputConnection?.commitText(d, 1)
                }
            )
        }
        return row
    }

    /**
     * Builds the Top Icon Toolbar inspired by the reference screenshot:
     * 1. ClipVault App Shortcut (Branded badge, tap -> MainActivity, long-press -> Settings)
     * 2. Emoji Panel
     * 3. Keyboard Settings
     * 4. Clipboard History Quick Panel
     * 5. Text Editing Tools (Select All, Copy, Cut, Paste, Cursor Left/Right, Undo/Redo)
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
            setPadding(dp(4), dp(2), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
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
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(30)).apply {
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
                contentDescription = desc
                background = if (isSelected) {
                    createRoundedBackground(primaryAccent, primaryAccent, radiusDp = 8)
                } else {
                    createRoundedBackground(actionKeyBg, keyBorder, radiusDp = 8)
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply {
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
            finishCurrentCompositionSafely()
            activePanel = if (activePanel == ActivePanel.EMOJI) ActivePanel.NONE else ActivePanel.EMOJI
            rebuildActiveInputView()
        }

        // 3. Keyboard Settings
        addToolbarAction("⚙", "Keyboard Settings", false) {
            finishCurrentCompositionSafely()
            launchClipVaultMainActivity(openSettings = true)
        }

        // 4. Clipboard History Quick Access
        addToolbarAction("📋", "Clipboard History", activePanel == ActivePanel.CLIPBOARD) {
            finishCurrentCompositionSafely()
            refreshRecentClipsAsync()
            activePanel = if (activePanel == ActivePanel.CLIPBOARD) ActivePanel.NONE else ActivePanel.CLIPBOARD
            rebuildActiveInputView()
        }

        // 5. Text Editing Tools
        addToolbarAction("✥", "Text Editing Tools", activePanel == ActivePanel.TEXT_EDITING) {
            finishCurrentCompositionSafely()
            activePanel = if (activePanel == ActivePanel.TEXT_EDITING) ActivePanel.NONE else ActivePanel.TEXT_EDITING
            rebuildActiveInputView()
        }

        // 6. Number/Symbol Layout
        addToolbarAction("123", "Number and Symbol Layout", symbolPage != SymbolPage.LETTERS && activePanel == ActivePanel.NONE) {
            finishCurrentCompositionSafely()
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
            finishCurrentCompositionSafely()
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
     * - Large uppercase/lowercase primary character in the center (24-26sp)
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
                    finishCurrentCompositionSafely()
                    val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
                    val outSec = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC &&
                        prefs.getBoolean("keyboard_bangla_numerals", false)
                    ) {
                        BengaliTransliterationRules.convertDigitsToBengali(sec)
                    } else {
                        sec
                    }
                    currentInputConnection?.commitText(outSec, 1)
                    showTemporaryKeyPopup("Inserted '" + outSec + "'")
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

    /**
     * Creates the dedicated Globe / Language button:
     * - Tap: Switches English <-> বাংলা
     * - Long-press: Opens the Language Selection Menu panel
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createGlobeLanguageKey(
        weight: Float,
        heightDp: Int,
        bgColor: Int,
        pressedBgColor: Int,
        borderColor: Int,
        textColor: Int,
        primaryAccent: Int
    ): TextView {
        val normalDrawable = createRoundedBackground(bgColor, borderColor, radiusDp = 8)
        val pressedDrawable = createRoundedBackground(pressedBgColor, primaryAccent, radiusDp = 8)

        return TextView(this).apply {
            text = "🌐 " + languageMode.badgeText
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            background = normalDrawable
            contentDescription = "Switch Language (Tap: English/বাংলা, Long-Press: Menu)"
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            setOnClickListener {
                performKeyFeedback(this)
                switchKeyboardLanguage(viaSwipe = false)
            }
            setOnLongClickListener {
                performKeyFeedback(this)
                finishCurrentCompositionSafely()
                activePanel = ActivePanel.LANGUAGE_MENU
                rebuildActiveInputView()
                true
            }
        }
    }

    /**
     * Creates the Spacebar supporting both normal space tap AND horizontal swipe (left/right)
     * to toggle between English and বাংলা without inserting unwanted spaces.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createSwipeableSpacebarView(
        weight: Float,
        heightDp: Int,
        bgColor: Int,
        pressedBgColor: Int,
        borderColor: Int,
        textColor: Int,
        primaryAccent: Int
    ): TextView {
        val normalDrawable = createRoundedBackground(bgColor, borderColor, radiusDp = 8)
        val pressedDrawable = createRoundedBackground(pressedBgColor, primaryAccent, radiusDp = 8)

        var downX = 0f
        var downY = 0f
        var swipeTriggered = false

        return TextView(this).apply {
            text = resolveSpacebarLabel(languageMode)
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            background = normalDrawable
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        swipeTriggered = false
                        v.scaleX = 0.97f
                        v.scaleY = 0.97f
                        v.background = pressedDrawable
                        performKeyFeedback(v, isSpacebar = true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.x - downX
                        val deltaY = event.y - downY
                        if (!swipeTriggered && isHorizontalSpacebarSwipe(deltaX, deltaY, dp(SPACEBAR_SWIPE_THRESHOLD_DP).toFloat())) {
                            swipeTriggered = true
                            performKeyFeedback(v, isSpacebar = true)
                            switchKeyboardLanguage(viaSwipe = true)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.scaleX = 1f
                        v.scaleY = 1f
                        v.background = normalDrawable
                        val deltaX = event.x - downX
                        val deltaY = event.y - downY
                        if (!swipeTriggered && isHorizontalSpacebarSwipe(deltaX, deltaY, dp(SPACEBAR_SWIPE_THRESHOLD_DP).toFloat())) {
                            swipeTriggered = true
                            switchKeyboardLanguage(viaSwipe = true)
                        } else if (!swipeTriggered) {
                            handleSpacebarTap()
                        }
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

    private fun showTemporaryKeyPopup(message: String) {
        val banner = longPressHintBanner ?: return
        banner.text = message
        banner.visibility = View.VISIBLE
        mainHandler.removeCallbacksAndMessages(banner)
        mainHandler.postDelayed({ banner.visibility = View.GONE }, 800L)
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
     * Language Selection Menu Panel opened on Globe button long-press.
     */
    private fun buildLanguageMenuPanel(
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
            setPadding(dp(8), dp(6), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(preset.keyHeightDp * 4 + 18)
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(8))
        }
        val title = TextView(this).apply {
            text = "Select Keyboard Language"
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "Close"
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

        KeyboardLanguageMode.entries.forEach { mode ->
            val isSelected = languageMode == mode
            val item = TextView(this).apply {
                text = (if (isSelected) "✓  " else "    ") + mode.displayName + " (" + mode.spacebarLabel + ")"
                setTextColor(if (isSelected) Color.WHITE else textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = createRoundedBackground(
                    if (isSelected) primaryAccent else keyBg,
                    keyBorder,
                    radiusDp = 8
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(4), 0, dp(4)) }
                setOnClickListener {
                    performKeyFeedback(this)
                    activePanel = ActivePanel.NONE
                    switchKeyboardLanguage(targetMode = mode, viaSwipe = false)
                    rebuildActiveInputView()
                }
            }
            container.addView(item)
        }

        val sysPickerBtn = TextView(this).apply {
            text = "⌨  Switch System Input Method (Gboard / Samsung)"
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = createRoundedBackground(actionKeyBg, keyBorder, radiusDp = 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, 0) }
            setOnClickListener {
                performKeyFeedback(this)
                switchInputMethodSafely()
            }
        }
        container.addView(sysPickerBtn)

        return container
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
     * Works identically in English and বাংলা modes; inserts into editor ONLY on explicit user tap.
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
                        finishCurrentCompositionSafely()
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
     * Useful Text Editing Panel: Select All, Copy, Cut, Paste, Move Cursor Left, Move Cursor Right, Undo, Redo.
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
            createFunctionKeyView("Select All", 1f, preset.keyHeightDp, 13.5f, keyBg, keyPressedBg, keyBorder, textPrimary) {
                currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
            }
        )
        actionsRow1.addView(
            createFunctionKeyView("Copy", 1f, preset.keyHeightDp, 13.5f, keyBg, keyPressedBg, keyBorder, textPrimary) {
                currentInputConnection?.performContextMenuAction(android.R.id.copy)
                capturePermittedClipboard()
            }
        )
        actionsRow1.addView(
            createFunctionKeyView("Cut", 1f, preset.keyHeightDp, 13.5f, keyBg, keyPressedBg, keyBorder, textPrimary) {
                currentInputConnection?.performContextMenuAction(android.R.id.cut)
                capturePermittedClipboard()
            }
        )
        actionsRow1.addView(
            createFunctionKeyView("Paste", 1f, preset.keyHeightDp, 13.5f, primaryAccent, primaryAccent, primaryAccent, Color.WHITE) {
                currentInputConnection?.performContextMenuAction(android.R.id.paste)
            }
        )
        container.addView(actionsRow1)

        val actionsRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(3))
        }
        actionsRow2.addView(
            createFunctionKeyView("◀ Left", 1f, preset.keyHeightDp, 13.5f, actionKeyBg, keyPressedBg, keyBorder, textPrimary) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            }
        )
        actionsRow2.addView(
            createFunctionKeyView("Right ▶", 1f, preset.keyHeightDp, 13.5f, actionKeyBg, keyPressedBg, keyBorder, textPrimary) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
        )
        actionsRow2.addView(
            createFunctionKeyView("↶ Undo", 1f, preset.keyHeightDp, 13.5f, actionKeyBg, keyPressedBg, keyBorder, textPrimary) {
                currentInputConnection?.performContextMenuAction(android.R.id.undo)
            }
        )
        actionsRow2.addView(
            createFunctionKeyView("↷ Redo", 1f, preset.keyHeightDp, 13.5f, actionKeyBg, keyPressedBg, keyBorder, textPrimary) {
                currentInputConnection?.performContextMenuAction(android.R.id.redo)
            }
        )
        container.addView(actionsRow2)

        return container
    }

    /**
     * More Options Panel inside keyboard: Quick toggles for Language, Number Row, Height, Haptic Intensity, Theme, and Switch IME.
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
        val showNumberRow = prefs.getBoolean("keyboard_show_number_row", false)

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
            text = "ClipVault v1.6.0 Quick Controls"
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

        // Row 1: Language Mode + Number Row Toggle
        val r1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        r1.addView(
            createFunctionKeyView(
                label = "Lang: " + languageMode.spacebarLabel,
                weight = 1f,
                heightDp = 42,
                textSizeSp = 12.5f,
                bgColor = keyBg,
                pressedBgColor = actionKeyBg,
                borderColor = keyBorder,
                textColor = textPrimary
            ) {
                switchKeyboardLanguage(viaSwipe = false)
                rebuildActiveInputView()
            }
        )
        r1.addView(
            createFunctionKeyView(
                label = "Num Row: " + (if (showNumberRow) "ON" else "OFF"),
                weight = 1f,
                heightDp = 42,
                textSizeSp = 12.5f,
                bgColor = keyBg,
                pressedBgColor = actionKeyBg,
                borderColor = keyBorder,
                textColor = textPrimary
            ) {
                prefs.edit().putBoolean("keyboard_show_number_row", !showNumberRow).apply()
                rebuildActiveInputView()
            }
        )
        container.addView(r1)

        // Row 2: Vibration Intensity Cycle + Height Cycle
        val r2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        r2.addView(
            createFunctionKeyView(
                label = "Haptics: " + currentIntensity.displayName,
                weight = 1f,
                heightDp = 42,
                textSizeSp = 12.5f,
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
        r2.addView(
            createFunctionKeyView(
                label = "Height: " + preset.prefKey,
                weight = 1f,
                heightDp = 42,
                textSizeSp = 12.5f,
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
        container.addView(r2)

        // Row 3: Theme Cycle + Switch Keyboard Picker
        val r3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        r3.addView(
            createFunctionKeyView(
                label = "Theme: " + currentTheme.displayName,
                weight = 1f,
                heightDp = 42,
                textSizeSp = 12.5f,
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
        r3.addView(
            createFunctionKeyView(
                label = "Switch Keyboard ⌨",
                weight = 1f,
                heightDp = 42,
                textSizeSp = 12.5f,
                bgColor = actionKeyBg,
                pressedBgColor = keyBg,
                borderColor = keyBorder,
                textColor = textPrimary
            ) {
                switchInputMethodSafely()
            }
        )
        container.addView(r3)

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

    /**
     * Handles character key taps for both Mode A (English) and Mode B (Bangla Phonetic)
     * using incremental InputConnection composition.
     */
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
        suggestionEngine.clearLastCorrectionState()

        val editorInfo = currentInputEditorInfo
        val isPassword = editorInfo != null && isInputFieldPassword(editorInfo.inputType)
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val suggestionsEnabled = prefs.getBoolean("keyboard_suggestions_enabled", true)
        val banglaNumerals = prefs.getBoolean("keyboard_bangla_numerals", false)

        if (symbolPage != SymbolPage.LETTERS || isPassword) {
            finishCurrentCompositionSafely()
            val outSym = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC && banglaNumerals) {
                BengaliTransliterationRules.convertDigitsToBengali(output)
            } else {
                output
            }
            ic.commitText(outSym, 1)
            return
        }

        if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) {
            // Incremental Bangla Phonetic composition
            composingRomanBuffer.append(output)
            updateSuggestionsAndRefreshBar()
            val primaryBangla = currentCandidates.firstOrNull()?.text
                ?: phoneticEngine.transliterateWord(composingRomanBuffer.toString(), banglaNumerals)
            activeComposingPreview = primaryBangla

            isInternalComposingUpdate = true
            try {
                ic.setComposingText(primaryBangla, 1)
            } finally {
                isInternalComposingUpdate = false
            }
        } else {
            // Mode A: English QWERTY with word suggestions
            if (suggestionsEnabled) {
                composingRomanBuffer.append(output)
                activeComposingPreview = composingRomanBuffer.toString()
                isInternalComposingUpdate = true
                try {
                    ic.setComposingText(activeComposingPreview, 1)
                } finally {
                    isInternalComposingUpdate = false
                }
                updateSuggestionsAndRefreshBar()
            } else {
                ic.commitText(output, 1)
            }
        }

        if (symbolPage == SymbolPage.LETTERS && shiftState == ShiftState.SHIFTED_SINGLE) {
            shiftState = ShiftState.LOWERCASE
            refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset)
        }
    }

    /**
     * Handles Spacebar taps: commits the active composing word (in Bengali or English)
     * followed by a single space, and records Undo state.
     */
    private fun handleSpacebarTap() {
        val ic = currentInputConnection ?: return
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val autocorrectEnabled = prefs.getBoolean("keyboard_autocorrect_enabled", true)
        val banglaNumerals = prefs.getBoolean("keyboard_bangla_numerals", false)

        if (composingRomanBuffer.isNotEmpty()) {
            val rawInput = composingRomanBuffer.toString()
            val committedWord = when (languageMode) {
                KeyboardLanguageMode.BANGLA_PHONETIC -> {
                    currentCandidates.firstOrNull()?.text
                        ?: phoneticEngine.transliterateWord(rawInput, banglaNumerals)
                }
                KeyboardLanguageMode.ENGLISH -> {
                    val shortcut = userDictionary.lookupShortcut(rawInput)?.expansion
                    if (shortcut != null) {
                        shortcut
                    } else if (autocorrectEnabled) {
                        currentCandidates.firstOrNull()?.text ?: rawInput
                    } else {
                        rawInput
                    }
                }
            }

            isInternalComposingUpdate = true
            try {
                ic.beginBatchEdit()
                ic.commitText(committedWord + " ", 1)
                ic.finishComposingText()
                ic.endBatchEdit()
            } finally {
                isInternalComposingUpdate = false
            }

            suggestionEngine.recordAutoCommit(rawInput, committedWord, " ")
            userDictionary.recordRecentWord(committedWord)
            previousCommittedWord = committedWord
            composingRomanBuffer.clear()
            activeComposingPreview = ""
            updateSuggestionsAndRefreshBar()
        } else {
            suggestionEngine.clearLastCorrectionState()
            ic.commitText(" ", 1)
        }
    }

    private fun commitPunctuationOrSeparator(punctuation: String) {
        val ic = currentInputConnection ?: return
        if (composingRomanBuffer.isNotEmpty()) {
            val rawInput = composingRomanBuffer.toString()
            val committedWord = activeComposingPreview.ifEmpty { rawInput }
            isInternalComposingUpdate = true
            try {
                ic.beginBatchEdit()
                ic.commitText(committedWord + punctuation, 1)
                ic.finishComposingText()
                ic.endBatchEdit()
            } finally {
                isInternalComposingUpdate = false
            }
            previousCommittedWord = committedWord
            composingRomanBuffer.clear()
            activeComposingPreview = ""
            updateSuggestionsAndRefreshBar()
        } else {
            ic.commitText(punctuation, 1)
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
        spacebarView?.text = resolveSpacebarLabel(languageMode)
        globeKeyView?.text = "🌐 " + languageMode.badgeText
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return

        // 1. If user is currently composing a word incrementally, remove last Roman character and re-transliterate
        if (composingRomanBuffer.isNotEmpty()) {
            composingRomanBuffer.deleteCharAt(composingRomanBuffer.length - 1)
            if (composingRomanBuffer.isEmpty()) {
                activeComposingPreview = ""
                isInternalComposingUpdate = true
                try {
                    ic.commitText("", 1)
                    ic.finishComposingText()
                } finally {
                    isInternalComposingUpdate = false
                }
            } else {
                updateSuggestionsAndRefreshBar()
                val updatedPreview = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) {
                    currentCandidates.firstOrNull()?.text
                        ?: phoneticEngine.transliterateWord(composingRomanBuffer.toString())
                } else {
                    composingRomanBuffer.toString()
                }
                activeComposingPreview = updatedPreview
                isInternalComposingUpdate = true
                try {
                    ic.setComposingText(updatedPreview, 1)
                } finally {
                    isInternalComposingUpdate = false
                }
                return
            }
            updateSuggestionsAndRefreshBar()
            return
        }

        // 2. Part 8 #7: Undo last autocorrection / transliteration when pressing Backspace right after space commit
        val lastCorrection = suggestionEngine.lastCorrectionState
        if (lastCorrection != null) {
            val totalReplacedLen = lastCorrection.committedReplacement.length + lastCorrection.trailingSpace.length
            isInternalComposingUpdate = true
            try {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(totalReplacedLen, 0)
                ic.commitText(lastCorrection.rawRomanOrTypo, 1)
                ic.endBatchEdit()
            } finally {
                isInternalComposingUpdate = false
            }
            showTemporaryKeyPopup("Reverted to '" + lastCorrection.rawRomanOrTypo + "'")
            suggestionEngine.clearLastCorrectionState()
            return
        }

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
        const val SPACEBAR_SWIPE_THRESHOLD_DP = 42

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
         * Resolves the Spacebar label for the active language mode ("English" or "বাংলা").
         */
        fun resolveSpacebarLabel(mode: KeyboardLanguageMode): String = mode.spacebarLabel

        /**
         * Determines whether a horizontal gesture on the Spacebar qualifies as a language switch swipe
         * (preventing accidental switches or unwanted space insertions).
         */
        fun isHorizontalSpacebarSwipe(deltaX: Float, deltaY: Float, thresholdPx: Float = 42f): Boolean {
            return abs(deltaX) >= thresholdPx && abs(deltaX) > abs(deltaY) * 1.5f
        }

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
