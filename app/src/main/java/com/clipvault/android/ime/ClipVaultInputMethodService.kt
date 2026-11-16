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
import android.util.Log
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
import com.clipvault.android.keyboard.transliteration.BengaliCandidate
import com.clipvault.android.keyboard.transliteration.BengaliFontStyle
import com.clipvault.android.keyboard.transliteration.BengaliPhoneticEngine
import com.clipvault.android.keyboard.transliteration.BengaliSuggestionEngine
import com.clipvault.android.keyboard.transliteration.BengaliTransliterationRules
import com.clipvault.android.keyboard.transliteration.BengaliUserDictionary
import com.clipvault.android.keyboard.transliteration.KeySizePreset
import com.clipvault.android.keyboard.transliteration.KeyboardLanguageMode
import com.clipvault.android.keyboard.transliteration.KeyboardUndoRedoManager
import com.clipvault.android.keyboard.transliteration.OneHandedMode
import com.clipvault.android.service.ClipboardServiceHelper
import com.clipvault.android.ui.MainActivity
import kotlin.math.abs

/**
 * ClipVault Keyboard (IME) v1.7.0 — Production Bilingual English + বাংলা Phonetic Keyboard.
 *
 * Key v1.7.0 Architectural Upgrades:
 * - Duplicate keyboard clipboard manager panel removed (Part 1 #3 & Part 2 #7); CV App Shortcut
 *   opens MainActivity for full Clipboard History while background capture remains active.
 * - Visible ⓘ About Developer button in top toolbar for both English and Bengali layouts (Part 2 #8 & Feature 10).
 * - Safe keyboard-managed Undo/Redo stack via KeyboardUndoRedoManager (Part 2 #5 & Feature 3).
 * - Suggestion-First default auto-correction behavior (Part 2 #6 & Feature 2).
 * - Anchored Key Pop-up Preview above pressed key, clamped to screen bounds (Feature 12).
 * - One-Handed Mode (Off / Left / Right) with one-tap Full Width restore (Feature 20).
 * - Key Size Presets (Small / Medium / Large), Bengali Unicode Font presets, and TalkBack labels.
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

    // Lazy-initialized Bilingual Engine & Safe Undo/Redo Stack (Feature 3, 7, 18)
    private val userDictionary: BengaliUserDictionary by lazy { BengaliUserDictionary(applicationContext) }
    private val phoneticEngine: BengaliPhoneticEngine by lazy { BengaliPhoneticEngine(userDictionary) }
    private val suggestionEngine: BengaliSuggestionEngine by lazy { BengaliSuggestionEngine(phoneticEngine) }
    private val undoRedoManager = KeyboardUndoRedoManager(maxHistorySize = 30)

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
    private var rootOverlayContainer: FrameLayout? = null
    private var keyPopupPreviewView: TextView? = null
    private var shiftKeyView: TextView? = null
    private var modeKeyView: TextView? = null
    private var spacebarView: TextView? = null
    private var globeKeyView: TextView? = null
    private var periodKeyView: TextView? = null
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
        languageMode = resolveStartingLanguageMode()
        registerClipboardListenerIfNeeded()
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
        val isPassword = attribute != null && isInputFieldPassword(attribute.inputType)
        undoRedoManager.onEditorContextChanged(attribute?.fieldId ?: 0, isPassword)
        resetComposingState(finishEditorComposing = false)
        registerClipboardListenerIfNeeded()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isInputViewVisibleState = true
        val isPassword = info != null && isInputFieldPassword(info.inputType)
        undoRedoManager.onEditorContextChanged(info?.fieldId ?: 0, isPassword)
        languageMode = resolveStartingLanguageMode()
        resetComposingState(finishEditorComposing = false)
        registerClipboardListenerIfNeeded()
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

        // If user moves the cursor outside the active composing span, safely finish composition
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
        hideKeyPopupPreview()
        finishCurrentCompositionSafely()
        super.onFinishInputView(finishingInput)
        stopBackspaceRepeat()
        activePanel = ActivePanel.NONE
        isInputViewVisibleState = false
    }

    override fun onDestroy() {
        hideKeyPopupPreview()
        stopBackspaceRepeat()
        unregisterClipboardListener()
        isInputViewVisibleState = false
        isActiveInstance = false
        keyCellViews.clear()
        undoRedoManager.clearHistory()
        super.onDestroy()
    }

    /**
     * Crash-safe InputConnection wrapper (Feature 17).
     * Never logs user text or PII.
     */
    private inline fun <T> withSafeInputConnection(actionName: String, block: (InputConnection) -> T): T? {
        val ic = currentInputConnection ?: return null
        return try {
            block(ic)
        } catch (t: Throwable) {
            Log.w("ClipVaultIME", "Recovered from InputConnection error during " + actionName + ": " + t.javaClass.simpleName)
            null
        }
    }

    private fun resetComposingState(finishEditorComposing: Boolean) {
        composingRomanBuffer.clear()
        activeComposingPreview = ""
        if (finishEditorComposing) {
            withSafeInputConnection("finishComposingText") { it.finishComposingText() }
        }
        updateSuggestionsAndRefreshBar()
    }

    private fun finishCurrentCompositionSafely() {
        if (composingRomanBuffer.isNotEmpty()) {
            val committed = activeComposingPreview.ifEmpty { composingRomanBuffer.toString() }
            previousCommittedWord = committed
            composingRomanBuffer.clear()
            activeComposingPreview = ""
            withSafeInputConnection("finishCurrentComposition") { it.finishComposingText() }
            updateSuggestionsAndRefreshBar()
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

    private fun getKeySizePreset(): KeySizePreset {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        return KeySizePreset.fromPrefKey(prefs.getString("keyboard_key_size_preset", "MEDIUM"))
    }

    private fun getOneHandedMode(): OneHandedMode {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        return OneHandedMode.fromPrefKey(prefs.getString("keyboard_one_handed_mode", "OFF"))
    }

    private fun getBengaliFontStyle(): BengaliFontStyle {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        return BengaliFontStyle.fromPrefKey(prefs.getString("keyboard_bengali_font_style", "SYSTEM_DEFAULT"))
    }

    private fun resolveBengaliTypeface(bold: Boolean = false): Typeface {
        return when (getBengaliFontStyle()) {
            BengaliFontStyle.SYSTEM_DEFAULT -> if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            BengaliFontStyle.SANS_SERIF_CLEAN -> Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            BengaliFontStyle.SERIF_READABLE -> Typeface.create("serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
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
     * Switches between English (QWERTY) and বাংলা (Bangla Phonetic) cleanly in-place without rebuilding
     * the entire keyboard view hierarchy (Feature 6 & 13).
     */
    private fun switchKeyboardLanguage(targetMode: KeyboardLanguageMode? = null, viaSwipe: Boolean = false) {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val banglaEnabled = prefs.getBoolean("keyboard_bangla_phonetic_enabled", true)
        if (!banglaEnabled && targetMode != KeyboardLanguageMode.ENGLISH) {
            showTemporaryKeyPopup("Enable Bangla Phonetic in Settings")
            return
        }

        finishCurrentCompositionSafely()
        suggestionEngine.clearLastCorrectionState()
        suggestionEngine.resetLanguagePromptDismissal()

        languageMode = targetMode ?: languageMode.toggle()
        persistLanguageMode(languageMode)

        spacebarView?.text = resolveSpacebarLabel(languageMode)
        spacebarView?.contentDescription = "Spacebar, current language " + languageMode.displayName + ". Swipe left or right to switch language."
        globeKeyView?.text = "🌐 " + languageMode.badgeText
        globeKeyView?.contentDescription = "Switch Language, active " + languageMode.displayName
        periodKeyView?.text = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) "।" else "."
        suggestionLangBadge?.text = languageMode.badgeText

        updateSuggestionsAndRefreshBar()
        val methodDesc = if (viaSwipe) "Swiped to " else "Switched to "
        showTemporaryKeyPopup(methodDesc + languageMode.displayName)
    }

    /**
     * Launches ClipVault MainActivity directly from the top keyboard toolbar using singleTop/clearTop
     * so duplicate activity stacks are never created (Part 2 #8, Feature 10 & Feature 18).
     */
    fun launchClipVaultMainActivity(openSettings: Boolean = false, openAboutDeveloper: Boolean = false) {
        val intent = buildMainActivityLaunchIntent(
            packageName = packageName,
            openSettings = openSettings,
            openAboutDeveloper = openAboutDeveloper
        )
        try {
            intent.setClass(this, MainActivity::class.java)
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (openSettings) putExtra(EXTRA_OPEN_CLIPVAULT_SETTINGS, true)
                    if (openAboutDeveloper) putExtra(EXTRA_OPEN_ABOUT_DEVELOPER, true)
                }
                if (fallback != null) startActivity(fallback)
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

    private fun createRoundedBackground(
        fillColor: Int,
        strokeColor: Int,
        radiusDp: Int = 8,
        strokeWidthDp: Int = 1
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fillColor)
            setStroke(dp(strokeWidthDp), strokeColor)
        }
    }

    private fun rebuildActiveInputView() {
        hideKeyPopupPreview()
        setInputView(buildKeyboardLayout())
    }

    /**
     * Displays the anchored Key Pop-up Preview above the pressed key (Feature 12).
     * Clamped to screen bounds so edge keys (Q, P, A, L) never clip off-screen.
     */
    private fun showKeyPopupPreviewForKey(anchorView: View, previewLabel: String) {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("keyboard_key_popup_enabled", true)) return
        val editorInfo = currentInputEditorInfo
        if (editorInfo != null && isInputFieldPassword(editorInfo.inputType)) return
        if (previewLabel.isBlank()) return

        val overlay = rootOverlayContainer ?: return
        val bubble = keyPopupPreviewView ?: return

        bubble.text = previewLabel
        val bubbleW = dp(50)
        val bubbleH = dp(54)

        val rootLoc = IntArray(2)
        val keyLoc = IntArray(2)
        overlay.getLocationInWindow(rootLoc)
        anchorView.getLocationInWindow(keyLoc)

        val relLeft = keyLoc[0] - rootLoc[0]
        val relTop = keyLoc[1] - rootLoc[1]
        val screenW = overlay.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels

        val clampedX = computeClampedPopupX(
            keyLeftPx = relLeft,
            keyWidthPx = anchorView.width,
            popupWidthPx = bubbleW,
            containerWidthPx = screenW,
            horizontalMarginPx = dp(4)
        )
        val targetY = (relTop - bubbleH - dp(4)).coerceAtLeast(dp(2))

        val lp = (bubble.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(bubbleW, bubbleH)
        lp.width = bubbleW
        lp.height = bubbleH
        lp.gravity = Gravity.TOP or Gravity.START
        lp.leftMargin = clampedX
        lp.topMargin = targetY
        bubble.layoutParams = lp
        bubble.visibility = View.VISIBLE
        bubble.bringToFront()
    }

    private fun hideKeyPopupPreview() {
        keyPopupPreviewView?.visibility = View.GONE
    }

    private fun buildKeyboardLayout(): View {
        keyCellViews.clear()
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val theme = resolveEffectiveTheme()
        val preset = getKeyboardHeightPreset()
        val keySizePreset = getKeySizePreset()
        val oneHandedMode = getOneHandedMode()
        val highContrast = prefs.getBoolean("keyboard_high_contrast", false)
        val showNumberRow = prefs.getBoolean("keyboard_show_number_row", false)

        val effectiveKeyHeightDp = (preset.keyHeightDp * keySizePreset.heightScale).toInt().coerceAtLeast(38)
        val effectiveLetterSp = preset.letterSp * keySizePreset.textSizeScale
        val strokeWidthDp = if (highContrast) 2 else 1

        val boardBg = Color.parseColor(theme.boardBgHex)
        val keyBg = Color.parseColor(theme.keyBgHex)
        val keyPressedBg = Color.parseColor(theme.keyPressedHex)
        val actionKeyBg = Color.parseColor(theme.modifierBgHex)
        val keyBorder = if (highContrast) Color.parseColor("#94A3B8") else Color.parseColor(theme.borderHex)
        val textPrimary = Color.parseColor(theme.primaryTextHex)
        val textSecondary = Color.parseColor(theme.secondaryTextHex)
        val primaryAccent = Color.parseColor(theme.enterAccentHex)

        val overlayRoot = FrameLayout(this).apply {
            setBackgroundColor(boardBg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        rootOverlayContainer = overlayRoot

        val boardColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(8))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. TOP ICON TOOLBAR (CV App Shortcut, Emoji, Settings, About Developer ⓘ, Text Editing, 123, Theme, More)
        boardColumn.addView(
            buildTopToolbar(
                theme = theme,
                actionKeyBg = actionKeyBg,
                keyBorder = keyBorder,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                primaryAccent = primaryAccent,
                strokeWidthDp = strokeWidthDp
            )
        )

        // 2. BILINGUAL SMART SUGGESTION BAR (3-5 Bengali or English candidates + Smart Language Prompt + Personal Dictionary save)
        if (activePanel == ActivePanel.NONE) {
            boardColumn.addView(
                buildSuggestionBar(
                    actionKeyBg = actionKeyBg,
                    keyBorder = keyBorder,
                    primaryAccent = primaryAccent
                )
            )
        }

        // Subtle status / language transition feedback banner
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
        boardColumn.addView(hintBanner)

        // 3. ACTIVE BODY: Either one of the interactive tool panels or QWERTY rows (with One-Handed docking support)
        when (activePanel) {
            ActivePanel.EMOJI -> {
                boardColumn.addView(buildEmojiPanel(preset, keyBg, keyPressedBg, actionKeyBg, keyBorder, textPrimary, textSecondary, primaryAccent))
            }
            ActivePanel.TEXT_EDITING -> {
                boardColumn.addView(buildTextEditingPanel(preset, keyBg, keyPressedBg, actionKeyBg, keyBorder, textPrimary, textSecondary, primaryAccent))
            }
            ActivePanel.MORE_OPTIONS -> {
                boardColumn.addView(buildMoreOptionsPanel(preset, keyBg, actionKeyBg, keyBorder, textPrimary, textSecondary, primaryAccent))
            }
            ActivePanel.LANGUAGE_MENU -> {
                boardColumn.addView(buildLanguageMenuPanel(preset, keyBg, actionKeyBg, keyBorder, textPrimary, textSecondary, primaryAccent))
            }
            ActivePanel.NONE -> {
                val keysColumn = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                }

                // Optional Dedicated Number Row (1..0 or ১..০)
                if (showNumberRow && symbolPage == SymbolPage.LETTERS) {
                    keysColumn.addView(
                        buildDedicatedNumberRow(
                            keyHeightDp = effectiveKeyHeightDp,
                            keyBg = keyBg,
                            keyPressedBg = keyPressedBg,
                            keyBorder = keyBorder,
                            textPrimary = textPrimary
                        )
                    )
                }

                // Row 1: Q W E R T Y U I O P (with secondary numbers 1..9, 0)
                val row1Keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
                keysColumn.addView(
                    buildQwertyCharacterRow(
                        keys = row1Keys,
                        preset = preset,
                        effectiveKeyHeightDp = effectiveKeyHeightDp,
                        effectiveLetterSp = effectiveLetterSp,
                        keyBg = keyBg,
                        keyPressedBg = keyPressedBg,
                        actionKeyBg = actionKeyBg,
                        keyBorder = keyBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        primaryAccent = primaryAccent,
                        horizontalInsetDp = 0,
                        strokeWidthDp = strokeWidthDp
                    )
                )

                // Row 2: A S D F G H J K L (with secondary symbols @ # $ _ & - + ( ))
                val row2Keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
                keysColumn.addView(
                    buildQwertyCharacterRow(
                        keys = row2Keys,
                        preset = preset,
                        effectiveKeyHeightDp = effectiveKeyHeightDp,
                        effectiveLetterSp = effectiveLetterSp,
                        keyBg = keyBg,
                        keyPressedBg = keyPressedBg,
                        actionKeyBg = actionKeyBg,
                        keyBorder = keyBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        primaryAccent = primaryAccent,
                        horizontalInsetDp = if (oneHandedMode == OneHandedMode.OFF) 16 else 8,
                        strokeWidthDp = strokeWidthDp
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
                    heightDp = effectiveKeyHeightDp,
                    textSizeSp = preset.functionSp,
                    bgColor = if (isShiftActive) primaryAccent else actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = if (isShiftActive) Color.WHITE else textPrimary,
                    contentDesc = resolveKeyAccessibilityLabel("SHIFT", languageMode, shiftState)
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
                        effectiveKeyHeightDp = effectiveKeyHeightDp,
                        effectiveLetterSp = effectiveLetterSp,
                        keyBg = keyBg,
                        keyPressedBg = keyPressedBg,
                        actionKeyBg = actionKeyBg,
                        keyBorder = keyBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        primaryAccent = primaryAccent,
                        strokeWidthDp = strokeWidthDp
                    )
                    keyCellViews.add(cell)
                    row3.addView(cell.container)
                }

                val backspaceBtn = createRepeatingBackspaceKey(
                    weight = 1.48f,
                    heightDp = effectiveKeyHeightDp,
                    textSizeSp = preset.functionSp + 1f,
                    bgColor = actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary
                )
                row3.addView(backspaceBtn)
                keysColumn.addView(row3)

                // Row 4: ?123 + Globe (Language Switch) + Comma + Spacebar (with Swipe) + Period + Enter
                val row4 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, dp(3), 0, dp(2))
                }

                val symBtn = createFunctionKeyView(
                    label = if (symbolPage == SymbolPage.LETTERS) "?123" else "ABC",
                    weight = 1.35f,
                    heightDp = effectiveKeyHeightDp,
                    textSizeSp = preset.functionSp - 1.5f,
                    bgColor = actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary,
                    contentDesc = resolveKeyAccessibilityLabel("SYM", languageMode, shiftState)
                ) {
                    finishCurrentCompositionSafely()
                    symbolPage = toggleSymbolMode(symbolPage)
                    refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset)
                }
                modeKeyView = symBtn
                row4.addView(symBtn)

                val globeBtn = createGlobeLanguageKey(
                    weight = 1.15f,
                    heightDp = effectiveKeyHeightDp,
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
                    heightDp = effectiveKeyHeightDp,
                    textSizeSp = effectiveLetterSp - 2f,
                    bgColor = keyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary,
                    contentDesc = "Comma"
                ) {
                    commitPunctuationOrSeparator(",")
                }
                row4.addView(commaBtn)

                val spaceBtn = createSwipeableSpacebarView(
                    weight = 3.9f,
                    heightDp = effectiveKeyHeightDp,
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
                    heightDp = effectiveKeyHeightDp,
                    textSizeSp = effectiveLetterSp - 2f,
                    bgColor = keyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary,
                    contentDesc = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) "Bengali Dari" else "Period"
                ) {
                    val mark = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) "।" else "."
                    commitPunctuationOrSeparator(mark)
                }
                periodKeyView = periodBtn
                row4.addView(periodBtn)

                val enterBtn = createFunctionKeyView(
                    label = "↵",
                    weight = 1.48f,
                    heightDp = effectiveKeyHeightDp,
                    textSizeSp = preset.functionSp + 2f,
                    bgColor = primaryAccent,
                    pressedBgColor = primaryAccent,
                    borderColor = primaryAccent,
                    textColor = Color.WHITE,
                    contentDesc = resolveKeyAccessibilityLabel("ENTER", languageMode, shiftState)
                ) {
                    finishCurrentCompositionSafely()
                    handleEnterAction()
                }
                row4.addView(enterBtn)
                keysColumn.addView(row4)

                // Wrap in One-Handed Mode container if LEFT or RIGHT is active (Feature 20)
                if (oneHandedMode == OneHandedMode.OFF) {
                    boardColumn.addView(keysColumn)
                } else {
                    boardColumn.addView(
                        buildOneHandedWrapper(
                            keysColumn = keysColumn,
                            mode = oneHandedMode,
                            actionKeyBg = actionKeyBg,
                            keyBorder = keyBorder,
                            textPrimary = textPrimary,
                            primaryAccent = primaryAccent
                        )
                    )
                }

                refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset)
                updateSuggestionsAndRefreshBar()
            }
        }

        overlayRoot.addView(boardColumn)

        // Floating Key Pop-up Preview Bubble (Feature 12)
        val popupBubble = TextView(this).apply {
            text = ""
            visibility = View.GONE
            gravity = Gravity.CENTER
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            elevation = dp(8).toFloat()
            background = createRoundedBackground(keyPressedBg, primaryAccent, radiusDp = 10, strokeWidthDp = 2)
            layoutParams = FrameLayout.LayoutParams(dp(50), dp(54))
        }
        keyPopupPreviewView = popupBubble
        overlayRoot.addView(popupBubble)

        return overlayRoot
    }

    /**
     * Wraps the QWERTY rows in a docked left/right layout with one-tap Full Width restore
     * and side-swap controls (Feature 20).
     */
    private fun buildOneHandedWrapper(
        keysColumn: View,
        mode: OneHandedMode,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        primaryAccent: Int
    ): View {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val sideControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.18f)
        }

        val expandBtn = TextView(this).apply {
            text = "⇔"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            contentDescription = "Restore Full Width Keyboard"
            background = createRoundedBackground(primaryAccent, primaryAccent, radiusDp = 8)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                setMargins(0, dp(4), 0, dp(6))
            }
            setOnClickListener {
                performKeyFeedback(this)
                prefs.edit().putString("keyboard_one_handed_mode", OneHandedMode.OFF.prefKey).apply()
                rebuildActiveInputView()
            }
        }

        val swapSideBtn = TextView(this).apply {
            text = if (mode == OneHandedMode.LEFT) "▶" else "◀"
            gravity = Gravity.CENTER
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            contentDescription = if (mode == OneHandedMode.LEFT) "Dock Keyboard Right" else "Dock Keyboard Left"
            background = createRoundedBackground(actionKeyBg, keyBorder, radiusDp = 8)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                setMargins(0, dp(4), 0, dp(4))
            }
            setOnClickListener {
                performKeyFeedback(this)
                val target = if (mode == OneHandedMode.LEFT) OneHandedMode.RIGHT else OneHandedMode.LEFT
                prefs.edit().putString("keyboard_one_handed_mode", target.prefKey).apply()
                rebuildActiveInputView()
            }
        }

        sideControls.addView(expandBtn)
        sideControls.addView(swapSideBtn)

        val keysParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.82f)
        keysColumn.layoutParams = keysParams

        if (mode == OneHandedMode.LEFT) {
            wrapper.addView(keysColumn)
            wrapper.addView(sideControls)
        } else {
            wrapper.addView(sideControls)
            wrapper.addView(keysColumn)
        }
        return wrapper
    }

    /**
     * Builds the fixed-height Bilingual Suggestion Bar directly above the keyboard (Feature 15 & 19).
     * Includes extra vertical clearance so Bengali vowel signs (ি ী ু ূ ৃ ে ৈ ো ৌ) never clip.
     */
    private fun buildSuggestionBar(
        actionKeyBg: Int,
        keyBorder: Int,
        primaryAccent: Int
    ): View {
        val fontStyle = getBengaliFontStyle()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40 + fontStyle.extraVerticalPaddingDp)
            )
        }

        val langBadge = TextView(this).apply {
            text = languageMode.badgeText
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = resolveBengaliTypeface(bold = true)
            includeFontPadding = true
            setPadding(dp(8), dp(4), dp(8), dp(4))
            contentDescription = "Active Language Badge " + languageMode.displayName + ". Tap to switch."
            background = createRoundedBackground(
                if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) Color.parseColor("#0D9488") else primaryAccent,
                keyBorder,
                radiusDp = 6
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(32)
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
        val smartLangDetect = prefs.getBoolean("keyboard_smart_language_detection", true)

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
        val bnTypeface = resolveBengaliTypeface(bold = false)
        val bnBoldTypeface = resolveBengaliTypeface(bold = true)

        suggestionLangBadge?.apply {
            text = languageMode.badgeText
            background = createRoundedBackground(
                if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) Color.parseColor("#0D9488") else primaryAccent,
                keyBorder,
                radiusDp = 6
            )
        }

        // Non-intrusive Smart Language Detection Chip (Feature 11)
        val langPrompt = suggestionEngine.detectSmartLanguagePrompt(
            composingText = composingRomanBuffer.toString(),
            recentCommittedWords = userDictionary.getRecentWords(3),
            currentMode = languageMode,
            smartDetectionEnabled = smartLangDetect
        )
        if (langPrompt != null) {
            val promptContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createRoundedBackground(Color.parseColor("#0F766E"), Color.parseColor("#14B8A6"), radiusDp = 8)
                setPadding(dp(8), dp(2), dp(6), dp(2))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(32)
                ).apply { setMargins(dp(2), 0, dp(4), 0) }
            }
            val switchLabel = TextView(this).apply {
                text = "🌐 " + langPrompt.chipLabel
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = bnBoldTypeface
                contentDescription = langPrompt.chipLabel
                setPadding(0, 0, dp(6), 0)
                setOnClickListener {
                    performKeyFeedback(this)
                    switchKeyboardLanguage(targetMode = langPrompt.suggestedMode, viaSwipe = false)
                }
            }
            val dismissBtn = TextView(this).apply {
                text = "✕"
                setTextColor(Color.parseColor("#CCFBF1"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT_BOLD
                contentDescription = "Dismiss language suggestion"
                setPadding(dp(4), dp(2), dp(4), dp(2))
                setOnClickListener {
                    performKeyFeedback(this)
                    suggestionEngine.dismissLanguagePromptForCurrentMode(languageMode)
                    updateSuggestionsAndRefreshBar()
                }
            }
            promptContainer.addView(switchLabel)
            promptContainer.addView(dismissBtn)
            row.addView(promptContainer)
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
                    "Type Banglish (e.g. ami -> আমি, kemon acho -> কেমন আছো)"
                } else {
                    "ClipVault Bilingual Keyboard (Swipe Space for বাংলা)"
                }
                setTextColor(textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = bnTypeface
                includeFontPadding = true
                setPadding(dp(6), dp(4), dp(6), dp(4))
            }
            row.addView(hint)
            return
        }

        currentCandidates.forEachIndexed { index, candidate ->
            val isHighlighted = candidate.isPrimary && composingRomanBuffer.isNotEmpty()
            val chip = TextView(this).apply {
                // Non-color active indicator ("★ ") for accessibility (Feature 19)
                text = if (isHighlighted) "★ " + candidate.text else candidate.text
                gravity = Gravity.CENTER
                setTextColor(if (isHighlighted) Color.WHITE else textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                typeface = if (isHighlighted) bnBoldTypeface else bnTypeface
                includeFontPadding = true
                setPadding(dp(12), dp(4), dp(12), dp(4))
                contentDescription = (if (isHighlighted) "Primary suggestion " else "Suggestion ") + candidate.text
                background = createRoundedBackground(
                    if (isHighlighted) primaryAccent else keyBg,
                    if (isHighlighted) primaryAccent else keyBorder,
                    radiusDp = 8
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(32)
                ).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener {
                    performKeyFeedback(this)
                    commitSelectedCandidate(candidate)
                }
            }
            row.addView(chip)

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
                contentDescription = "Save " + rawRoman + " as " + topCandidate + " to Personal Dictionary"
                setPadding(dp(9), dp(4), dp(9), dp(4))
                background = createRoundedBackground(actionKeyBg, keyBorder, radiusDp = 8)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(30)
                ).apply {
                    setMargins(dp(6), 0, dp(2), 0)
                }
                setOnClickListener {
                    performKeyFeedback(this)
                    userDictionary.addOrUpdateWord(rawRoman, topCandidate, languageMode)
                    updateSuggestionsAndRefreshBar()
                    showTemporaryKeyPopup("Saved '" + rawRoman + " → " + topCandidate + "'")
                }
            }
            row.addView(saveChip)
        }
    }

    /**
     * Commits a user-tapped suggestion candidate into the active InputConnection,
     * records personal dictionary preference + Undo history, and advances to next-word prediction.
     */
    private fun commitSelectedCandidate(candidate: BengaliCandidate) {
        val rawRoman = composingRomanBuffer.toString()
        val beforeSnapshot = withSafeInputConnection("getTextBeforeCandidate") {
            it.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        }.orEmpty()
        val basePrefix = if (activeComposingPreview.isNotEmpty() && beforeSnapshot.endsWith(activeComposingPreview)) {
            beforeSnapshot.removeSuffix(activeComposingPreview)
        } else {
            beforeSnapshot
        }
        val committedText = candidate.text + " "

        isInternalComposingUpdate = true
        try {
            withSafeInputConnection("commitSelectedCandidate") { ic ->
                ic.beginBatchEdit()
                ic.commitText(committedText, 1)
                ic.finishComposingText()
                ic.endBatchEdit()
            }
        } finally {
            isInternalComposingUpdate = false
        }

        undoRedoManager.recordEdit(
            beforeText = basePrefix,
            afterText = basePrefix + committedText,
            description = "Commit Suggestion"
        )

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
     * Builds optional dedicated top number row (1..0 or ১..০) (Feature 4).
     */
    private fun buildDedicatedNumberRow(
        keyHeightDp: Int,
        keyBg: Int,
        keyPressedBg: Int,
        keyBorder: Int,
        textPrimary: Int
    ): View {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val useBanglaDigits = languageMode == KeyboardLanguageMode.BANGLA_PHONETIC &&
            prefs.getBoolean("keyboard_bangla_numerals", false)
        val digits = resolveNumberRowDigits(languageMode, useBanglaDigits)

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
                    heightDp = (keyHeightDp * 0.78f).toInt().coerceAtLeast(34),
                    textSizeSp = 16.5f,
                    bgColor = keyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary,
                    contentDesc = "Number " + d
                ) {
                    finishCurrentCompositionSafely()
                    commitDirectTextWithUndo(d, "Digit")
                }
            )
        }
        return row
    }

    /**
     * Builds the Clean Top Icon Toolbar (Part 1 #3, Part 2 #7, Part 2 #8, Part 4 & Feature 10):
     * 1. CV — ClipVault App Shortcut (tap -> MainActivity Clipboard History, long-press -> Settings)
     * 2. ☺ — Emoji Panel
     * 3. ⚙ — Keyboard Settings
     * 4. ⓘ — About Developer (visible in BOTH English and Bengali layouts!)
     * 5. ✎ — Text Editing Tools (Select All, Copy, Cut, Paste, Left/Right, Safe Undo/Redo)
     * 6. 123 — Number/Symbol Layout
     * 7. ◐ — Theme Cycler
     * 8. ⋯ — More Options (One-Handed, Key Size, Number Row, Font, Haptics, Switch Keyboard)
     */
    private fun buildTopToolbar(
        theme: KeyboardThemeMode,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        primaryAccent: Int,
        strokeWidthDp: Int
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

        // 1. FIRST ACTION: Recognizable ClipVault App Shortcut Icon (opens full Clipboard History in MainActivity)
        val clipVaultBrandBtn = TextView(this).apply {
            text = "CV"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            background = createRoundedBackground(primaryAccent, Color.parseColor("#38BDF8"), radiusDp = 10, strokeWidthDp = strokeWidthDp)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(30)).apply {
                setMargins(dp(2), 0, dp(6), 0)
            }
            contentDescription = "Open ClipVault Clipboard Manager App"
            setOnClickListener {
                performKeyFeedback(this)
                launchClipVaultMainActivity(openSettings = false, openAboutDeveloper = false)
            }
            setOnLongClickListener {
                performKeyFeedback(this)
                Toast.makeText(context, "Opening ClipVault Settings", Toast.LENGTH_SHORT).show()
                launchClipVaultMainActivity(openSettings = true, openAboutDeveloper = false)
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
                    createRoundedBackground(primaryAccent, primaryAccent, radiusDp = 8, strokeWidthDp = strokeWidthDp)
                } else {
                    createRoundedBackground(actionKeyBg, keyBorder, radiusDp = 8, strokeWidthDp = strokeWidthDp)
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
            launchClipVaultMainActivity(openSettings = true, openAboutDeveloper = false)
        }

        // 4. About Developer (Part 2 #8 & Feature 10 — Visible in BOTH English and Bengali layouts!)
        addToolbarAction("ⓘ", "About Developer", false) {
            finishCurrentCompositionSafely()
            launchClipVaultMainActivity(openSettings = false, openAboutDeveloper = true)
        }

        // 5. Text Editing Tools (with Safe Keyboard-Managed Undo/Redo)
        addToolbarAction("✎", "Text Editing and Undo Redo Tools", activePanel == ActivePanel.TEXT_EDITING) {
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
        effectiveKeyHeightDp: Int,
        effectiveLetterSp: Float,
        keyBg: Int,
        keyPressedBg: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        primaryAccent: Int,
        horizontalInsetDp: Int,
        strokeWidthDp: Int
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
                effectiveKeyHeightDp = effectiveKeyHeightDp,
                effectiveLetterSp = effectiveLetterSp,
                keyBg = keyBg,
                keyPressedBg = keyPressedBg,
                actionKeyBg = actionKeyBg,
                keyBorder = keyBorder,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                primaryAccent = primaryAccent,
                strokeWidthDp = strokeWidthDp
            )
            keyCellViews.add(cell)
            row.addView(cell.container)
        }
        return row
    }

    /**
     * Creates a keycap with secondary number/symbol, primary character, TalkBack accessibility description,
     * and anchored Key Pop-up Preview above the key on touch down (Feature 12, 16, 19).
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createCharacterKeyCell(
        baseLetter: String,
        globalIndex: Int,
        preset: KeyboardHeightPreset,
        effectiveKeyHeightDp: Int,
        effectiveLetterSp: Float,
        keyBg: Int,
        keyPressedBg: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        primaryAccent: Int,
        strokeWidthDp: Int
    ): KeyCellRefs {
        val normalDrawable = createRoundedBackground(keyBg, keyBorder, radiusDp = 8, strokeWidthDp = strokeWidthDp)
        val pressedDrawable = createRoundedBackground(keyPressedBg, primaryAccent, radiusDp = 8, strokeWidthDp = 2)

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

        val currentOutput = resolveKeyOutput(baseLetter, globalIndex, shiftState, symbolPage)
        val primaryText = TextView(this).apply {
            text = currentOutput
            gravity = Gravity.CENTER
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveLetterSp)
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
            contentDescription = resolveKeyAccessibilityLabel(currentOutput, languageMode, shiftState)
            layoutParams = LinearLayout.LayoutParams(0, dp(effectiveKeyHeightDp), 1f).apply {
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
                    showKeyPopupPreviewForKey(cellContainer, outSec)
                    commitDirectTextWithUndo(outSec, "Long-press symbol")
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
                    val activeChar = resolveKeyOutput(baseLetter, globalIndex, shiftState, symbolPage)
                    showKeyPopupPreviewForKey(v, activeChar)
                    mainHandler.postDelayed(longPressRunnable, 270L)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    hideKeyPopupPreview()
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
                    hideKeyPopupPreview()
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
     * Creates the dedicated Globe / Language button (Feature 6).
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
            typeface = resolveBengaliTypeface(bold = true)
            includeFontPadding = false
            background = normalDrawable
            contentDescription = resolveKeyAccessibilityLabel("GLOBE", languageMode, shiftState)
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
     * to toggle between English and বাংলা without inserting unwanted spaces (Feature 6).
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
            typeface = resolveBengaliTypeface(bold = true)
            includeFontPadding = false
            background = normalDrawable
            contentDescription = resolveKeyAccessibilityLabel("SPACE", languageMode, shiftState)
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
        contentDesc: String = label,
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
            contentDescription = contentDesc
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
            contentDescription = resolveKeyAccessibilityLabel("BACKSPACE", languageMode, shiftState)
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
                typeface = resolveBengaliTypeface(bold = true)
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
     * Interactive Emoji Selection Panel with 8 categories.
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
                    contentDescription = "Emoji " + emoji
                    background = createRoundedBackground(keyBg, keyBorder, radiusDp = 8)
                    layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                        setMargins(dp(2), 0, dp(2), 0)
                    }
                    setOnClickListener {
                        performKeyFeedback(this)
                        commitDirectTextWithUndo(emoji, "Emoji")
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
     * Text Editing Panel with Safe Keyboard-Managed Undo/Redo (Part 2 #5 & Feature 3).
     * Never calls unreliable android.R.id.undo / redo; uses KeyboardUndoRedoManager instead.
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
            text = "Text Editing & Safe Undo/Redo (" + undoRedoManager.getUndoDepth() + ")"
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
            createFunctionKeyView("Select All", 1f, preset.keyHeightDp, 13.5f, keyBg, keyPressedBg, keyBorder, textPrimary, contentDesc = "Select All Text") {
                withSafeInputConnection("selectAll") { it.performContextMenuAction(android.R.id.selectAll) }
            }
        )
        actionsRow1.addView(
            createFunctionKeyView("Copy", 1f, preset.keyHeightDp, 13.5f, keyBg, keyPressedBg, keyBorder, textPrimary, contentDesc = "Copy Selected Text") {
                withSafeInputConnection("copy") { it.performContextMenuAction(android.R.id.copy) }
                capturePermittedClipboard()
            }
        )
        actionsRow1.addView(
            createFunctionKeyView("Cut", 1f, preset.keyHeightDp, 13.5f, keyBg, keyPressedBg, keyBorder, textPrimary, contentDesc = "Cut Selected Text") {
                withSafeInputConnection("cut") { it.performContextMenuAction(android.R.id.cut) }
                capturePermittedClipboard()
            }
        )
        actionsRow1.addView(
            createFunctionKeyView("Paste", 1f, preset.keyHeightDp, 13.5f, primaryAccent, primaryAccent, primaryAccent, Color.WHITE, contentDesc = "Paste from Clipboard") {
                withSafeInputConnection("paste") { it.performContextMenuAction(android.R.id.paste) }
            }
        )
        container.addView(actionsRow1)

        val actionsRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(3))
        }
        actionsRow2.addView(
            createFunctionKeyView("◀ Left", 1f, preset.keyHeightDp, 13.5f, actionKeyBg, keyPressedBg, keyBorder, textPrimary, contentDesc = "Move Cursor Left") {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            }
        )
        actionsRow2.addView(
            createFunctionKeyView("Right ▶", 1f, preset.keyHeightDp, 13.5f, actionKeyBg, keyPressedBg, keyBorder, textPrimary, contentDesc = "Move Cursor Right") {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
        )
        val canUndoNow = undoRedoManager.canUndo()
        val canRedoNow = undoRedoManager.canRedo()
        actionsRow2.addView(
            createFunctionKeyView(
                label = "↶ Undo",
                weight = 1f,
                heightDp = preset.keyHeightDp,
                textSizeSp = 13.5f,
                bgColor = if (canUndoNow) keyBg else actionKeyBg,
                pressedBgColor = keyPressedBg,
                borderColor = keyBorder,
                textColor = if (canUndoNow) textPrimary else textSecondary,
                contentDesc = "Undo Last Keyboard Edit"
            ) {
                performKeyboardUndo()
                rebuildActiveInputView()
            }
        )
        actionsRow2.addView(
            createFunctionKeyView(
                label = "↷ Redo",
                weight = 1f,
                heightDp = preset.keyHeightDp,
                textSizeSp = 13.5f,
                bgColor = if (canRedoNow) keyBg else actionKeyBg,
                pressedBgColor = keyPressedBg,
                borderColor = keyBorder,
                textColor = if (canRedoNow) textPrimary else textSecondary,
                contentDesc = "Redo Keyboard Edit"
            ) {
                performKeyboardRedo()
                rebuildActiveInputView()
            }
        )
        container.addView(actionsRow2)

        return container
    }

    /**
     * Executes a safe keyboard-managed Undo operation using [undoRedoManager] (Part 2 #5 & Feature 3).
     */
    private fun performKeyboardUndo() {
        finishCurrentCompositionSafely()
        if (!undoRedoManager.canUndo()) {
            showTemporaryKeyPopup("Nothing to undo")
            return
        }
        withSafeInputConnection("performKeyboardUndo") { ic ->
            val currentBefore = ic.getTextBeforeCursor(128, 0)?.toString()
            val op = undoRedoManager.undo(currentBefore)
            if (op == null) {
                showTemporaryKeyPopup("Editor changed externally; undo history reset")
                return@withSafeInputConnection
            }
            ic.beginBatchEdit()
            if (op.afterText.length >= op.beforeText.length && op.afterText.startsWith(op.beforeText)) {
                val insertedLen = op.afterText.length - op.beforeText.length
                if (insertedLen > 0) ic.deleteSurroundingText(insertedLen, 0)
            } else if (op.beforeText.length > op.afterText.length && op.beforeText.startsWith(op.afterText)) {
                val deletedSuffix = op.beforeText.removePrefix(op.afterText)
                ic.commitText(deletedSuffix, 1)
            } else {
                ic.deleteSurroundingText(op.afterText.length, 0)
                if (op.beforeText.isNotEmpty()) ic.commitText(op.beforeText, 1)
            }
            ic.endBatchEdit()
            showTemporaryKeyPopup("Undid " + op.description)
        }
    }

    /**
     * Executes a safe keyboard-managed Redo operation using [undoRedoManager] (Part 2 #5 & Feature 3).
     */
    private fun performKeyboardRedo() {
        finishCurrentCompositionSafely()
        if (!undoRedoManager.canRedo()) {
            showTemporaryKeyPopup("Nothing to redo")
            return
        }
        withSafeInputConnection("performKeyboardRedo") { ic ->
            val currentBefore = ic.getTextBeforeCursor(128, 0)?.toString()
            val op = undoRedoManager.redo(currentBefore)
            if (op == null) {
                showTemporaryKeyPopup("Cannot redo after external edit")
                return@withSafeInputConnection
            }
            ic.beginBatchEdit()
            if (op.afterText.length >= op.beforeText.length && op.afterText.startsWith(op.beforeText)) {
                val insertedSuffix = op.afterText.removePrefix(op.beforeText)
                if (insertedSuffix.isNotEmpty()) ic.commitText(insertedSuffix, 1)
            } else if (op.beforeText.length > op.afterText.length && op.beforeText.startsWith(op.afterText)) {
                val deleteLen = op.beforeText.length - op.afterText.length
                if (deleteLen > 0) ic.deleteSurroundingText(deleteLen, 0)
            } else {
                ic.deleteSurroundingText(op.beforeText.length, 0)
                if (op.afterText.isNotEmpty()) ic.commitText(op.afterText, 1)
            }
            ic.endBatchEdit()
            showTemporaryKeyPopup("Redid " + op.description)
        }
    }

    private fun commitDirectTextWithUndo(textToCommit: String, description: String) {
        if (textToCommit.isEmpty()) return
        val before = withSafeInputConnection("getTextBeforeDirectCommit") {
            it.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        }.orEmpty()
        withSafeInputConnection("commitDirectText") { ic ->
            ic.commitText(textToCommit, 1)
        }
        undoRedoManager.recordEdit(
            beforeText = before,
            afterText = before + textToCommit,
            description = description
        )
    }

    /**
     * More Options Panel inside keyboard: Quick toggles for Language, Number Row, One-Handed Mode,
     * Key Size, Haptic Intensity, About Developer, and Switch IME.
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
        val showNumberRow = prefs.getBoolean("keyboard_show_number_row", false)
        val oneHanded = getOneHandedMode()
        val keySize = getKeySizePreset()

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
            text = "ClipVault v1.7.0 Quick Controls"
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

        // Row 2: One-Handed Mode + Key Size Preset
        val r2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        r2.addView(
            createFunctionKeyView(
                label = "One-Hand: " + oneHanded.displayName,
                weight = 1f,
                heightDp = 42,
                textSizeSp = 12.5f,
                bgColor = keyBg,
                pressedBgColor = actionKeyBg,
                borderColor = keyBorder,
                textColor = textPrimary
            ) {
                val nextMode = oneHanded.next()
                prefs.edit().putString("keyboard_one_handed_mode", nextMode.prefKey).apply()
                rebuildActiveInputView()
            }
        )
        r2.addView(
            createFunctionKeyView(
                label = "Key Size: " + keySize.displayName,
                weight = 1f,
                heightDp = 42,
                textSizeSp = 12.5f,
                bgColor = keyBg,
                pressedBgColor = actionKeyBg,
                borderColor = keyBorder,
                textColor = textPrimary
            ) {
                val nextSize = keySize.next()
                prefs.edit().putString("keyboard_key_size_preset", nextSize.prefKey).apply()
                rebuildActiveInputView()
            }
        )
        container.addView(r2)

        // Row 3: Haptic Intensity + About Developer + Switch Keyboard
        val r3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        r3.addView(
            createFunctionKeyView(
                label = "Haptics: " + currentIntensity.displayName,
                weight = 1f,
                heightDp = 42,
                textSizeSp = 12f,
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
        r3.addView(
            createFunctionKeyView(
                label = "ⓘ About Dev",
                weight = 1f,
                heightDp = 42,
                textSizeSp = 12f,
                bgColor = keyBg,
                pressedBgColor = actionKeyBg,
                borderColor = keyBorder,
                textColor = textPrimary
            ) {
                launchClipVaultMainActivity(openSettings = false, openAboutDeveloper = true)
            }
        )
        r3.addView(
            createFunctionKeyView(
                label = "Switch IME ⌨",
                weight = 1f,
                heightDp = 42,
                textSizeSp = 12f,
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
     * using incremental InputConnection composition and in-place UI updates (Feature 13).
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
            commitDirectTextWithUndo(outSym, "Symbol")
            return
        }

        if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) {
            composingRomanBuffer.append(output)
            updateSuggestionsAndRefreshBar()
            val primaryBangla = currentCandidates.firstOrNull()?.text
                ?: phoneticEngine.transliterateWord(composingRomanBuffer.toString(), banglaNumerals)
            activeComposingPreview = primaryBangla

            isInternalComposingUpdate = true
            try {
                withSafeInputConnection("setComposingBangla") { ic ->
                    ic.setComposingText(primaryBangla, 1)
                }
            } finally {
                isInternalComposingUpdate = false
            }
        } else {
            if (suggestionsEnabled) {
                composingRomanBuffer.append(output)
                activeComposingPreview = composingRomanBuffer.toString()
                isInternalComposingUpdate = true
                try {
                    withSafeInputConnection("setComposingEnglish") { ic ->
                        ic.setComposingText(activeComposingPreview, 1)
                    }
                } finally {
                    isInternalComposingUpdate = false
                }
                updateSuggestionsAndRefreshBar()
            } else {
                commitDirectTextWithUndo(output, "Character")
            }
        }

        if (symbolPage == SymbolPage.LETTERS && shiftState == ShiftState.SHIFTED_SINGLE) {
            shiftState = ShiftState.LOWERCASE
            refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset)
        }
    }

    /**
     * Handles Spacebar taps: commits the active composing word respecting Suggestion-First default
     * auto-correction (Part 2 #6 & Feature 2) and records Undo state.
     */
    private fun handleSpacebarTap() {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val autocorrectEnabled = prefs.getBoolean("keyboard_autocorrect_enabled", true)
        val autoApplyOnSpace = prefs.getBoolean("keyboard_auto_apply_on_space", false)
        val banglaNumerals = prefs.getBoolean("keyboard_bangla_numerals", false)

        if (composingRomanBuffer.isNotEmpty()) {
            val rawInput = composingRomanBuffer.toString()
            val beforeSnapshot = withSafeInputConnection("getTextBeforeSpace") {
                it.getTextBeforeCursor(64, 0)?.toString().orEmpty()
            }.orEmpty()
            val basePrefix = if (activeComposingPreview.isNotEmpty() && beforeSnapshot.endsWith(activeComposingPreview)) {
                beforeSnapshot.removeSuffix(activeComposingPreview)
            } else {
                beforeSnapshot
            }

            val committedWord = suggestionEngine.resolveWordCommitOnSpace(
                composingText = rawInput,
                previousWord = previousCommittedWord,
                languageMode = languageMode,
                autocorrectEnabled = autocorrectEnabled,
                autoApplyOnSpace = autoApplyOnSpace,
                useBengaliNumerals = banglaNumerals
            )
            val fullCommit = committedWord + " "

            isInternalComposingUpdate = true
            try {
                withSafeInputConnection("commitWordOnSpace") { ic ->
                    ic.beginBatchEdit()
                    ic.commitText(fullCommit, 1)
                    ic.finishComposingText()
                    ic.endBatchEdit()
                }
            } finally {
                isInternalComposingUpdate = false
            }

            undoRedoManager.recordEdit(
                beforeText = basePrefix,
                afterText = basePrefix + fullCommit,
                description = "Type Word"
            )
            suggestionEngine.recordAutoCommit(rawInput, committedWord, " ")
            userDictionary.recordRecentWord(committedWord)
            previousCommittedWord = committedWord
            composingRomanBuffer.clear()
            activeComposingPreview = ""
            updateSuggestionsAndRefreshBar()
        } else {
            suggestionEngine.clearLastCorrectionState()
            commitDirectTextWithUndo(" ", "Space")
        }
    }

    private fun commitPunctuationOrSeparator(punctuation: String) {
        if (composingRomanBuffer.isNotEmpty()) {
            val rawInput = composingRomanBuffer.toString()
            val committedWord = activeComposingPreview.ifEmpty { rawInput }
            val beforeSnapshot = withSafeInputConnection("getTextBeforePunctuation") {
                it.getTextBeforeCursor(64, 0)?.toString().orEmpty()
            }.orEmpty()
            val basePrefix = if (activeComposingPreview.isNotEmpty() && beforeSnapshot.endsWith(activeComposingPreview)) {
                beforeSnapshot.removeSuffix(activeComposingPreview)
            } else {
                beforeSnapshot
            }
            val fullCommit = committedWord + punctuation

            isInternalComposingUpdate = true
            try {
                withSafeInputConnection("commitPunctuation") { ic ->
                    ic.beginBatchEdit()
                    ic.commitText(fullCommit, 1)
                    ic.finishComposingText()
                    ic.endBatchEdit()
                }
            } finally {
                isInternalComposingUpdate = false
            }
            undoRedoManager.recordEdit(
                beforeText = basePrefix,
                afterText = basePrefix + fullCommit,
                description = "Punctuation"
            )
            previousCommittedWord = committedWord
            composingRomanBuffer.clear()
            activeComposingPreview = ""
            updateSuggestionsAndRefreshBar()
        } else {
            commitDirectTextWithUndo(punctuation, "Punctuation")
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
        val keySizePreset = getKeySizePreset()
        keyCellViews.forEachIndexed { idx, cell ->
            val baseChar = BASE_QWERTY_KEYS.getOrElse(idx) { "" }
            val outChar = resolveKeyOutput(baseChar, idx, shiftState, symbolPage)
            cell.primaryLabel.text = outChar
            cell.container.contentDescription = resolveKeyAccessibilityLabel(outChar, languageMode, shiftState)
            val targetSp = when {
                symbolPage != SymbolPage.LETTERS && idx < 10 -> preset.numberSp * keySizePreset.textSizeScale
                symbolPage != SymbolPage.LETTERS -> (preset.numberSp - 0.5f) * keySizePreset.textSizeScale
                else -> preset.letterSp * keySizePreset.textSizeScale
            }
            cell.primaryLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, targetSp)
            cell.secondaryLabel.visibility = if (symbolPage == SymbolPage.LETTERS) View.VISIBLE else View.INVISIBLE
            cell.secondaryLabel.setTextColor(textSecondary)
        }

        val isShiftHighlighted = symbolPage == SymbolPage.LETTERS && shiftState != ShiftState.LOWERCASE
        shiftKeyView?.apply {
            text = resolveLeftModifierLabel()
            contentDescription = resolveKeyAccessibilityLabel("SHIFT", languageMode, shiftState)
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
        periodKeyView?.text = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) "।" else "."
    }

    private fun handleBackspace() {
        // 1. If user is currently composing a word incrementally, remove last Roman character and re-transliterate
        if (composingRomanBuffer.isNotEmpty()) {
            composingRomanBuffer.deleteCharAt(composingRomanBuffer.length - 1)
            if (composingRomanBuffer.isEmpty()) {
                activeComposingPreview = ""
                isInternalComposingUpdate = true
                try {
                    withSafeInputConnection("clearComposingOnBackspace") { ic ->
                        ic.commitText("", 1)
                        ic.finishComposingText()
                    }
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
                    withSafeInputConnection("updateComposingOnBackspace") { ic ->
                        ic.setComposingText(updatedPreview, 1)
                    }
                } finally {
                    isInternalComposingUpdate = false
                }
                return
            }
            updateSuggestionsAndRefreshBar()
            return
        }

        // 2. Undo last autocorrection / transliteration when pressing Backspace right after space commit
        val lastCorrection = suggestionEngine.lastCorrectionState
        if (lastCorrection != null) {
            val totalReplacedLen = lastCorrection.committedReplacement.length + lastCorrection.trailingSpace.length
            isInternalComposingUpdate = true
            try {
                withSafeInputConnection("revertLastCorrection") { ic ->
                    ic.beginBatchEdit()
                    ic.deleteSurroundingText(totalReplacedLen, 0)
                    ic.commitText(lastCorrection.rawRomanOrTypo, 1)
                    ic.endBatchEdit()
                }
            } finally {
                isInternalComposingUpdate = false
            }
            showTemporaryKeyPopup("Reverted to '" + lastCorrection.rawRomanOrTypo + "'")
            suggestionEngine.clearLastCorrectionState()
            return
        }

        withSafeInputConnection("backspaceDelete") { ic ->
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
            } else {
                val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
                ic.deleteSurroundingText(1, 0)
                if (before.isNotEmpty()) {
                    undoRedoManager.recordEdit(
                        beforeText = before,
                        afterText = before.dropLast(1),
                        description = "Backspace"
                    )
                }
            }
        }
    }

    private fun handleEnterAction() {
        withSafeInputConnection("handleEnter") { ic ->
            val info = currentInputEditorInfo
            val actionId = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
            if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(actionId)
            } else {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
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
        const val EXTRA_OPEN_ABOUT_DEVELOPER = "com.clipvault.android.EXTRA_OPEN_ABOUT_DEVELOPER"
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
         * Resolves top number row digits (0..9 or ১..০) for Feature 4.
         */
        fun resolveNumberRowDigits(mode: KeyboardLanguageMode, useBengaliNumerals: Boolean): List<String> {
            return if (mode == KeyboardLanguageMode.BANGLA_PHONETIC && useBengaliNumerals) {
                listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০")
            } else {
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            }
        }

        /**
         * Computes the clamped horizontal X position for the Key Pop-up Preview above a key
         * so edge keys (Q, P, A, L) never overflow off-screen (Feature 12).
         */
        fun computeClampedPopupX(
            keyLeftPx: Int,
            keyWidthPx: Int,
            popupWidthPx: Int,
            containerWidthPx: Int,
            horizontalMarginPx: Int = 8
        ): Int {
            val centeredX = keyLeftPx + (keyWidthPx - popupWidthPx) / 2
            val maxRight = (containerWidthPx - popupWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx)
            return centeredX.coerceIn(horizontalMarginPx, maxRight)
        }

        /**
         * Builds the MainActivity launch intent with singleTop/clearTop flags and optional
         * Settings or About Developer extras (Part 2 #8 & Feature 10).
         */
        fun buildMainActivityLaunchIntent(
            packageName: String,
            openSettings: Boolean = false,
            openAboutDeveloper: Boolean = false
        ): Intent {
            return Intent().apply {
                setPackage(packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (openSettings) {
                    putExtra(EXTRA_OPEN_CLIPVAULT_SETTINGS, true)
                }
                if (openAboutDeveloper) {
                    putExtra(EXTRA_OPEN_ABOUT_DEVELOPER, true)
                }
            }
        }

        /**
         * Returns a clear TalkBack screen-reader label for any keyboard key or action (Feature 19).
         */
        fun resolveKeyAccessibilityLabel(
            keyRoleOrChar: String,
            mode: KeyboardLanguageMode,
            shiftState: ShiftState
        ): String {
            return when (keyRoleOrChar) {
                "SHIFT" -> when (shiftState) {
                    ShiftState.CAPS_LOCK -> "Caps Lock Active"
                    ShiftState.SHIFTED_SINGLE -> "Shift Active"
                    ShiftState.LOWERCASE -> "Shift"
                }
                "BACKSPACE" -> "Delete"
                "ENTER" -> "Enter"
                "SPACE" -> "Spacebar, " + mode.displayName + ". Swipe horizontally to switch language."
                "GLOBE" -> "Switch Language, current " + mode.displayName + ". Long press for language menu."
                "SYM" -> "Numbers and Symbols Layout"
                else -> {
                    if (keyRoleOrChar.length == 1 && keyRoleOrChar[0].isLetter()) {
                        val caseDesc = if (keyRoleOrChar[0].isUpperCase()) "Capital " else ""
                        caseDesc + keyRoleOrChar
                    } else {
                        keyRoleOrChar
                    }
                }
            }
        }

        /**
         * Determines whether a horizontal gesture on the Spacebar qualifies as a language switch swipe.
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
