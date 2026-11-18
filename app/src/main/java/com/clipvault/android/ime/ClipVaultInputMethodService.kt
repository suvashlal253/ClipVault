package com.clipvault.android.ime

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.clipvault.android.R
import com.clipvault.android.keyboard.transliteration.BengaliCandidate
import com.clipvault.android.keyboard.transliteration.BengaliFontStyle
import com.clipvault.android.keyboard.transliteration.BengaliPhoneticEngine
import com.clipvault.android.keyboard.transliteration.BengaliSuggestionEngine
import com.clipvault.android.keyboard.transliteration.BengaliTransliterationRules
import com.clipvault.android.keyboard.transliteration.BengaliUserDictionary
import com.clipvault.android.keyboard.transliteration.CandidateSource
import com.clipvault.android.keyboard.transliteration.KeySizePreset
import com.clipvault.android.keyboard.transliteration.KeyboardLanguageMode
import com.clipvault.android.keyboard.transliteration.KeyboardUndoRedoManager
import com.clipvault.android.keyboard.transliteration.OneHandedMode
import com.clipvault.android.service.ClipboardServiceHelper
import com.clipvault.android.ui.MainActivity
import kotlin.math.abs

/**
 * ClipVault Keyboard (IME) v1.7.1 — Production Bilingual English + বাংলা Phonetic Keyboard.
 *
 * Key v1.7.1 Critical Fixes & Stability Upgrades:
 * 1. Canonical preference keys (PREF_KEY_POPUP_PREVIEW, PREF_HIGH_CONTRAST) with safe legacy migration.
 * 2. Tapping a suggestion updates adaptive ranking only and never auto-creates a Personal Dictionary entry.
 * 3. Real-time mixed-sentence candidates surfaced in the suggestion bar and committed cleanly.
 * 4. EditorSessionIdentity Undo/Redo handling fieldId == 0, app switches, and selection replacements safely.
 * 5. Genuine v1.6.0 Personal Dictionary migration preserving all user data.
 * 6. Bengali spelling corrections & ambiguous word alternatives with Suggestion-First default.
 * 7. Elegant Bengali Unicode vertical metrics (isElegantTextHeight, includeFontPadding, SUBPIXEL_TEXT_FLAG).
 * 8. High-contrast accessibility borders + atomic TalkBack focus hierarchy on key cells.
 * 9. SafeInputConnectionExecutor catching recoverable Exceptions only (never swallowing JVM Errors).
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

    enum class EnterKeyBehavior(val prefKey: String, val displayName: String) {
        SMART_MULTILINE("SMART_MULTILINE", "Smart Multiline (Newline in Chat/Notes)"),
        ALWAYS_NEWLINE("ALWAYS_NEWLINE", "Always Insert Newline"),
        EDITOR_ACTION("EDITOR_ACTION", "Prefer Editor Action");

        companion object {
            fun fromKey(raw: String?): EnterKeyBehavior =
                values().firstOrNull { it.prefKey.equals(raw, ignoreCase = true) } ?: SMART_MULTILINE
        }
    }

    enum class EnterKeyDisposition {
        INSERT_NEWLINE,
        PERFORM_EDITOR_ACTION,
        SEND_ENTER_KEY_EVENT
    }

    enum class KeyboardHeightPreset(
        val prefKey: String,
        val keyHeightDp: Int,
        val letterSp: Float,
        val secondarySp: Float,
        val numberSp: Float,
        val functionSp: Float
    ) {
        COMPACT("COMPACT", keyHeightDp = 45, letterSp = 24f, secondarySp = 10.5f, numberSp = 20.5f, functionSp = 14.5f),
        NORMAL("NORMAL", keyHeightDp = 50, letterSp = 25f, secondarySp = 11.5f, numberSp = 22f, functionSp = 15.5f),
        TALL("TALL", keyHeightDp = 56, letterSp = 26f, secondarySp = 12f, numberSp = 23.5f, functionSp = 16.5f);

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
        val enterAccentHex: String,
        val enterPressedHex: String
    ) {
        DARK_CHARCOAL(
            prefKey = "DARK_CHARCOAL",
            displayName = "Dark Charcoal",
            boardBgHex = "#121318",
            keyBgHex = "#25272E",
            keyPressedHex = "#343741",
            modifierBgHex = "#1B1D23",
            borderHex = "#30333D",
            primaryTextHex = "#F8FAFC",
            secondaryTextHex = "#94A3B8",
            enterAccentHex = "#4F46E5",
            enterPressedHex = "#4338CA"
        ),
        PURE_BLACK(
            prefKey = "PURE_BLACK",
            displayName = "Pure Black",
            boardBgHex = "#000000",
            keyBgHex = "#1A1B20",
            keyPressedHex = "#2A2C34",
            modifierBgHex = "#111216",
            borderHex = "#262830",
            primaryTextHex = "#F8FAFC",
            secondaryTextHex = "#94A3B8",
            enterAccentHex = "#6366F1",
            enterPressedHex = "#4F46E5"
        ),
        LIGHT(
            prefKey = "LIGHT",
            displayName = "Light",
            boardBgHex = "#ECEFF4",
            keyBgHex = "#FFFFFF",
            keyPressedHex = "#DDE2EA",
            modifierBgHex = "#DCE1E9",
            borderHex = "#CBD2DC",
            primaryTextHex = "#0F172A",
            secondaryTextHex = "#64748B",
            enterAccentHex = "#4F46E5",
            enterPressedHex = "#4338CA"
        ),
        FOLLOW_SYSTEM(
            prefKey = "FOLLOW_SYSTEM",
            displayName = "Follow System",
            boardBgHex = "#121318",
            keyBgHex = "#25272E",
            keyPressedHex = "#343741",
            modifierBgHex = "#1B1D23",
            borderHex = "#30333D",
            primaryTextHex = "#F8FAFC",
            secondaryTextHex = "#94A3B8",
            enterAccentHex = "#4F46E5",
            enterPressedHex = "#4338CA"
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

    // Lazy-initialized Bilingual Engine & Safe Undo/Redo Stack
    private val userDictionary: BengaliUserDictionary by lazy { BengaliUserDictionary(applicationContext) }
    private val phoneticEngine: BengaliPhoneticEngine by lazy { BengaliPhoneticEngine(userDictionary) }
    private val suggestionEngine: BengaliSuggestionEngine by lazy { BengaliSuggestionEngine(phoneticEngine) }
    private val undoRedoManager = KeyboardUndoRedoManager(maxHistorySize = 30)

    private var languageMode: KeyboardLanguageMode = KeyboardLanguageMode.ENGLISH
    private val composingRomanBuffer = StringBuilder()
    private var activeComposingPreview: String = ""
    private var previousCommittedWord: String = ""
    private val currentSentenceRawTokens = mutableListOf<String>()
    private val currentSentenceCommittedText = StringBuilder()
    private var lastClauseTerminatedWithPunctuation: Boolean = false
    private var currentCandidates: List<BengaliCandidate> = emptyList()
    private var isInternalComposingUpdate: Boolean = false

    private var shiftState: ShiftState = ShiftState.SHIFTED_SINGLE
    private var symbolPage: SymbolPage = SymbolPage.LETTERS
    private var activePanel: ActivePanel = ActivePanel.NONE
    private var selectedEmojiCategoryIndex: Int = 1 // Default to Smileys
    private var lastShiftTapUptimeMs: Long = 0L

    private val keyCellViews = mutableListOf<KeyCellRefs>()
    private val numberRowKeyViews = mutableListOf<TextView>()
    private var rootOverlayContainer: FrameLayout? = null
    private var keyPopupPreviewView: TextView? = null
    private var shiftKeyView: TextView? = null
    private var modeKeyView: TextView? = null
    private var spacebarView: TextView? = null
    private var globeKeyView: TextView? = null
    private var periodKeyView: TextView? = null
    private var enterKeyView: TextView? = null
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
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        migrateLegacySettingsPreferencesIfNeeded(prefs)
        languageMode = resolveStartingLanguageMode()
        registerClipboardListenerIfNeeded()
    }

    private fun resolveStartingLanguageMode(): KeyboardLanguageMode {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        undoRedoManager.onEditorContextChanged(
            packageName = attribute?.packageName.orEmpty(),
            newFieldId = attribute?.fieldId ?: 0,
            inputType = attribute?.inputType ?: 0,
            isPasswordOrSensitive = isPassword,
            restarting = restarting
        )
        if (!restarting) {
            clearSentenceClauseContext()
        }
        resetComposingState(finishEditorComposing = false)
        registerClipboardListenerIfNeeded()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isInputViewVisibleState = true
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        migrateLegacySettingsPreferencesIfNeeded(prefs)
        val isPassword = info != null && isInputFieldPassword(info.inputType)
        undoRedoManager.onEditorContextChanged(
            packageName = info?.packageName.orEmpty(),
            newFieldId = info?.fieldId ?: 0,
            inputType = info?.inputType ?: 0,
            isPasswordOrSensitive = isPassword,
            restarting = restarting
        )
        if (!restarting) {
            clearSentenceClauseContext()
        }
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
        undoRedoManager.onSelectionChanged(
            oldSelStart = oldSelStart,
            oldSelEnd = oldSelEnd,
            newSelStart = newSelStart,
            newSelEnd = newSelEnd,
            isInternalEdit = isInternalComposingUpdate
        )
        if (isInternalComposingUpdate) return

        // If user moves the cursor outside the active composing span, safely finish composition
        if (composingRomanBuffer.isNotEmpty()) {
            val movedOutsideComposition = candidatesStart >= 0 &&
                (newSelStart < candidatesStart || newSelEnd > candidatesEnd || newSelStart != newSelEnd)
            val cursorJumpedNonSequentially = candidatesStart < 0 && abs(newSelStart - oldSelStart) > 1
            if (movedOutsideComposition || cursorJumpedNonSequentially) {
                finishCurrentCompositionSafely()
                clearSentenceClauseContext()
            }
        } else if (abs(newSelStart - oldSelStart) > 1 || newSelStart != newSelEnd) {
            clearSentenceClauseContext()
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
        mainHandler.removeCallbacksAndMessages(null)
        unregisterClipboardListener()
        isInputViewVisibleState = false
        isActiveInstance = false
        keyCellViews.clear()
        numberRowKeyViews.clear()
        clearSentenceClauseContext()
        undoRedoManager.clearHistory()
        super.onDestroy()
    }

    private fun clearSentenceClauseContext() {
        currentSentenceRawTokens.clear()
        currentSentenceCommittedText.clear()
        previousCommittedWord = ""
        lastClauseTerminatedWithPunctuation = false
    }

    /**
     * Crash-safe InputConnection wrapper (Fix 9).
     * Catches recoverable [Exception]s only — NEVER catches fatal JVM [Error]s (OutOfMemoryError,
     * VirtualMachineError, LinkageError, etc.). Resets invalid composing state on recoverable failure
     * and never logs user typed text or PII.
     */
    private inline fun <T> withSafeInputConnection(
        actionName: String,
        resetComposingOnError: Boolean = true,
        block: (InputConnection) -> T
    ): T? {
        val ic = currentInputConnection ?: return null
        return SafeInputConnectionExecutor.execute(
            inputConnection = ic,
            actionName = actionName,
            onRecoverableException = { ex ->
                Log.w("ClipVaultIME", "Recovered from InputConnection exception during " + actionName + ": " + ex.javaClass.simpleName)
                if (resetComposingOnError) {
                    composingRomanBuffer.clear()
                    activeComposingPreview = ""
                }
            },
            block = block
        )
    }

    private fun resetComposingState(finishEditorComposing: Boolean) {
        composingRomanBuffer.clear()
        activeComposingPreview = ""
        if (finishEditorComposing) {
            withSafeInputConnection("finishComposingText", resetComposingOnError = false) { it.finishComposingText() }
        }
        updateSuggestionsAndRefreshBar()
    }

    private fun finishCurrentCompositionSafely() {
        if (composingRomanBuffer.isNotEmpty()) {
            val rawWord = composingRomanBuffer.toString()
            val committed = activeComposingPreview.ifEmpty { rawWord }
            previousCommittedWord = committed
            recordCommittedWordInSentenceClause(rawWord, committed, " ")
            composingRomanBuffer.clear()
            activeComposingPreview = ""
            withSafeInputConnection("finishCurrentComposition", resetComposingOnError = false) { it.finishComposingText() }
            updateSuggestionsAndRefreshBar()
        }
    }

    private fun recordCommittedWordInSentenceClause(rawToken: String, committedWord: String, separator: String) {
        val cleanRaw = rawToken.trim()
        val cleanCommitted = committedWord.trim()
        if (cleanRaw.isEmpty() || cleanCommitted.isEmpty()) return
        val nonSpaceSep = separator.trim()
        val rawWithPunct = if (nonSpaceSep.isNotEmpty() && !cleanRaw.endsWith(nonSpaceSep)) {
            cleanRaw + nonSpaceSep
        } else {
            cleanRaw
        }
        currentSentenceRawTokens.add(rawWithPunct)
        currentSentenceCommittedText.append(cleanCommitted).append(separator)
        while (currentSentenceRawTokens.size > 10) {
            currentSentenceRawTokens.removeAt(0)
        }
        if (currentSentenceCommittedText.length > 160) {
            clearSentenceClauseContext()
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
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return BengaliFontStyle.fromPrefKey(prefs.getString("keyboard_bengali_font_style", "SYSTEM_DEFAULT"))
    }

    private fun resolveBengaliTypeface(bold: Boolean = false): Typeface {
        return try {
            when (getBengaliFontStyle()) {
                BengaliFontStyle.SYSTEM_DEFAULT -> if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                BengaliFontStyle.SANS_SERIF_CLEAN -> Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
                BengaliFontStyle.SERIF_READABLE -> Typeface.create("serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
        } catch (_: Exception) {
            if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    /**
     * Applies proper Bengali Unicode rendering metrics (Fix 7) so কার (ি ী ু ূ ৃ ে ৈ ো ৌ),
     * ফলা (্য ্র), যুক্তাক্ষর, হসন্ত (্), Bengali numerals (০-৯), and দাড়ি (।) never clip vertically.
     */
    private fun applyBengaliTypography(textView: TextView, bold: Boolean = false) {
        textView.typeface = resolveBengaliTypeface(bold = bold)
        textView.includeFontPadding = true
        textView.paintFlags = textView.paintFlags or Paint.SUBPIXEL_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            textView.isElegantTextHeight = true
        }
    }

    private fun getVibrationIntensity(): VibrationIntensity {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        globeKeyView?.contentDescription = "Switch Language, current " + languageMode.displayName
        periodKeyView?.text = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) "।" else "."
        periodKeyView?.contentDescription = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) "Bengali Dari" else "Period"
        suggestionLangBadge?.text = languageMode.badgeText

        // Update dedicated number row & top-row secondary digit labels immediately in-place (v1.7.2 Requirement 2)
        val useBanglaDigits = languageMode == KeyboardLanguageMode.BANGLA_PHONETIC &&
            prefs.getBoolean("keyboard_bangla_numerals", true)
        val numRowDigits = resolveNumberRowDigits(languageMode, useBanglaDigits)
        numberRowKeyViews.forEachIndexed { idx, tv ->
            val digitLabel = numRowDigits.getOrElse(idx) { "" }
            tv.text = digitLabel
            tv.contentDescription = "Number " + digitLabel
        }
        val theme = resolveEffectiveTheme()
        val preset = getKeyboardHeightPreset()
        val highContrast = resolveHighContrastEnabled(prefs)
        val strokeWidthDp = resolveKeyBorderStrokeWidthDp(highContrast)
        refreshKeyLabels(
            primaryAccent = Color.parseColor(theme.enterAccentHex),
            actionKeyBg = Color.parseColor(theme.modifierBgHex),
            keyBorder = Color.parseColor(resolveKeyBorderHex(theme, highContrast)),
            textPrimary = Color.parseColor(theme.primaryTextHex),
            textSecondary = Color.parseColor(theme.secondaryTextHex),
            preset = preset,
            strokeWidthDp = strokeWidthDp
        )

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
     * Displays the anchored Key Pop-up Preview above the pressed key (Fix 1 & Feature 12).
     * Reads canonical [PREF_KEY_POPUP_PREVIEW] (with legacy fallback) so turning Key Pop-up OFF
     * in Settings immediately disables the preview.
     */
    private fun showKeyPopupPreviewForKey(anchorView: View, previewLabel: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editorInfo = currentInputEditorInfo
        val isPassword = editorInfo != null && isInputFieldPassword(editorInfo.inputType)
        if (!shouldShowKeyPopupPreview(resolveKeyPopupEnabled(prefs), isPassword, previewLabel)) {
            hideKeyPopupPreview()
            return
        }

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
        numberRowKeyViews.clear()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val theme = resolveEffectiveTheme()
        val preset = getKeyboardHeightPreset()
        val keySizePreset = getKeySizePreset()
        val oneHandedMode = getOneHandedMode()
        val highContrast = resolveHighContrastEnabled(prefs)
        val showNumberRow = prefs.getBoolean("keyboard_show_number_row", true)

        val effectiveKeyHeightDp = (preset.keyHeightDp * keySizePreset.heightScale).toInt().coerceAtLeast(38)
        val effectiveLetterSp = preset.letterSp * keySizePreset.textSizeScale
        val strokeWidthDp = resolveKeyBorderStrokeWidthDp(highContrast)
        val screenWidthDp = resources.configuration.screenWidthDp.takeIf { it > 0 } ?: 392

        val boardBg = Color.parseColor(theme.boardBgHex)
        val keyBg = Color.parseColor(theme.keyBgHex)
        val keyPressedBg = Color.parseColor(theme.keyPressedHex)
        val actionKeyBg = Color.parseColor(theme.modifierBgHex)
        val keyBorder = Color.parseColor(resolveKeyBorderHex(theme, highContrast))
        val textPrimary = Color.parseColor(theme.primaryTextHex)
        val textSecondary = if (highContrast && theme != KeyboardThemeMode.LIGHT) {
            Color.parseColor("#E2E8F0")
        } else if (highContrast) {
            Color.parseColor("#1F2937")
        } else {
            Color.parseColor(theme.secondaryTextHex)
        }
        val primaryAccent = Color.parseColor(theme.enterAccentHex)
        val enterPressedBg = Color.parseColor(theme.enterPressedHex)

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
            setPadding(dp(4), dp(4), dp(4), dp(6))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. TOP ICON TOOLBAR (Clipboard Shortcut, Emoji, Settings, Text Editing, 123, Theme, More)
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

        // 2. BILINGUAL SMART SUGGESTION BAR (3-5 Bengali or English candidates + Mixed Sentence candidates + Smart Language Prompt + Personal Dictionary save)
        if (activePanel == ActivePanel.NONE) {
            boardColumn.addView(
                buildSuggestionBar(
                    actionKeyBg = actionKeyBg,
                    keyBorder = keyBorder,
                    primaryAccent = primaryAccent,
                    strokeWidthDp = strokeWidthDp
                )
            )
        }

        // Subtle status / language transition feedback banner
        val hintBanner = TextView(this).apply {
            text = ""
            visibility = View.GONE
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            applyBengaliTypography(this, bold = true)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = createRoundedBackground(primaryAccent, primaryAccent, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)
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
                            preset = preset,
                            keyBg = keyBg,
                            keyPressedBg = keyPressedBg,
                            keyBorder = keyBorder,
                            textPrimary = textPrimary,
                            strokeWidthDp = strokeWidthDp
                        )
                    )
                }

                // Row 1: Q W E R T Y U I O P (with secondary numbers 1..9, 0 or ১..৯, ০)
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

                // Row 2: A S D F G H J K L (with responsive horizontal inset for small/standard/large screens)
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
                        horizontalInsetDp = resolveResponsiveRow2InsetDp(screenWidthDp, oneHandedMode),
                        strokeWidthDp = strokeWidthDp
                    )
                )

                // Row 3: Shift + Z X C V B N M + Backspace
                val row3 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, dp(ROW_VERTICAL_PADDING_DP), 0, dp(ROW_VERTICAL_PADDING_DP))
                }

                val isShiftActive = symbolPage == SymbolPage.LETTERS && shiftState != ShiftState.LOWERCASE
                val shiftBtn = createFunctionKeyView(
                    label = resolveLeftModifierLabel(),
                    weight = 1.45f,
                    heightDp = effectiveKeyHeightDp,
                    textSizeSp = preset.functionSp,
                    bgColor = if (isShiftActive) primaryAccent else actionKeyBg,
                    pressedBgColor = if (isShiftActive) enterPressedBg else keyPressedBg,
                    borderColor = if (isShiftActive) primaryAccent else keyBorder,
                    textColor = if (isShiftActive) Color.WHITE else textPrimary,
                    strokeWidthDp = strokeWidthDp,
                    contentDesc = resolveKeyAccessibilityLabel("SHIFT", languageMode, shiftState)
                ) {
                    onLeftModifierTapped(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset, strokeWidthDp)
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
                    weight = 1.45f,
                    heightDp = effectiveKeyHeightDp,
                    textSizeSp = preset.functionSp + 1f,
                    bgColor = actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary,
                    strokeWidthDp = strokeWidthDp
                )
                row3.addView(backspaceBtn)
                keysColumn.addView(row3)

                // Row 4: ?123 + Globe (Language Switch) + Comma + Spacebar (with Swipe) + Period + Enter
                val row4 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, dp(ROW_VERTICAL_PADDING_DP), 0, dp(ROW_VERTICAL_PADDING_DP))
                }

                val symBtn = createFunctionKeyView(
                    label = if (symbolPage == SymbolPage.LETTERS) "?123" else "ABC",
                    weight = 1.28f,
                    heightDp = effectiveKeyHeightDp,
                    textSizeSp = 13f,
                    bgColor = actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary,
                    strokeWidthDp = strokeWidthDp,
                    contentDesc = resolveKeyAccessibilityLabel("SYM", languageMode, shiftState)
                ) {
                    finishCurrentCompositionSafely()
                    symbolPage = toggleSymbolMode(symbolPage)
                    refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset, strokeWidthDp)
                }
                modeKeyView = symBtn
                row4.addView(symBtn)

                val globeBtn = createGlobeLanguageKey(
                    weight = 1.18f,
                    heightDp = effectiveKeyHeightDp,
                    bgColor = actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary,
                    primaryAccent = primaryAccent,
                    strokeWidthDp = strokeWidthDp
                )
                globeKeyView = globeBtn
                row4.addView(globeBtn)

                val commaBtn = createFunctionKeyView(
                    label = ",",
                    weight = 0.90f,
                    heightDp = effectiveKeyHeightDp,
                    textSizeSp = preset.functionSp + 1.5f,
                    bgColor = actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary,
                    strokeWidthDp = strokeWidthDp,
                    contentDesc = "Comma"
                ) {
                    commitPunctuationOrSeparator(",")
                }
                row4.addView(commaBtn)

                val spaceBtn = createSwipeableSpacebarView(
                    weight = 3.98f,
                    heightDp = effectiveKeyHeightDp,
                    bgColor = keyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary,
                    primaryAccent = primaryAccent,
                    strokeWidthDp = strokeWidthDp
                )
                spacebarView = spaceBtn
                row4.addView(spaceBtn)

                val periodBtn = createFunctionKeyView(
                    label = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) "।" else ".",
                    weight = 0.90f,
                    heightDp = effectiveKeyHeightDp,
                    textSizeSp = preset.functionSp + 1.5f,
                    bgColor = actionKeyBg,
                    pressedBgColor = keyPressedBg,
                    borderColor = keyBorder,
                    textColor = textPrimary,
                    strokeWidthDp = strokeWidthDp,
                    contentDesc = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) "Bengali Dari" else "Period"
                ) {
                    val mark = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) "।" else "."
                    commitPunctuationOrSeparator(mark)
                }
                periodKeyView = periodBtn
                row4.addView(periodBtn)

                val editorInfo = currentInputEditorInfo
                val enterBehavior = EnterKeyBehavior.fromKey(
                    prefs.getString(PREF_ENTER_KEY_BEHAVIOR, EnterKeyBehavior.SMART_MULTILINE.prefKey)
                )
                val initialEnterLabel = resolveEnterKeyLabel(
                    inputType = editorInfo?.inputType ?: InputType.TYPE_CLASS_TEXT,
                    imeOptions = editorInfo?.imeOptions ?: EditorInfo.IME_ACTION_NONE,
                    behavior = enterBehavior
                )
                val enterBtn = createFunctionKeyView(
                    label = initialEnterLabel,
                    weight = 1.42f,
                    heightDp = effectiveKeyHeightDp,
                    textSizeSp = preset.functionSp + 1.5f,
                    bgColor = primaryAccent,
                    pressedBgColor = enterPressedBg,
                    borderColor = if (highContrast) Color.WHITE else primaryAccent,
                    textColor = Color.WHITE,
                    strokeWidthDp = strokeWidthDp,
                    contentDesc = resolveKeyAccessibilityLabel("ENTER", languageMode, shiftState)
                ) {
                    handleEnterAction()
                }
                enterKeyView = enterBtn
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

                refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset, strokeWidthDp)
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            typeface = Typeface.DEFAULT_BOLD
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            elevation = dp(6).toFloat()
            background = createRoundedBackground(keyPressedBg, primaryAccent, radiusDp = POPUP_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)
            layoutParams = FrameLayout.LayoutParams(dp(48), dp(52))
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
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.16f)
        }

        val expandBtn = TextView(this).apply {
            text = "⇔"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.DEFAULT_BOLD
            contentDescription = "Restore Full Width Keyboard"
            background = createRoundedBackground(primaryAccent, primaryAccent, radiusDp = KEY_CORNER_RADIUS_DP)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                setMargins(0, dp(3), 0, dp(5))
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.DEFAULT_BOLD
            contentDescription = if (mode == OneHandedMode.LEFT) "Dock Keyboard Right" else "Dock Keyboard Left"
            background = createRoundedBackground(actionKeyBg, keyBorder, radiusDp = KEY_CORNER_RADIUS_DP)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                setMargins(0, dp(3), 0, dp(3))
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

        val keysParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.84f)
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
     * Builds the fixed-height Bilingual Suggestion Bar directly above the keyboard (Fix 7 & 8).
     * Refined for visual consistency: uniform 34dp chip heights, 8dp corner radius, purple accent
     * language indicator, and single-line ellipsized candidate layout without double divider lines.
     */
    private fun buildSuggestionBar(
        actionKeyBg: Int,
        keyBorder: Int,
        primaryAccent: Int,
        strokeWidthDp: Int = 1
    ): View {
        val fontStyle = getBengaliFontStyle()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(SUGGESTION_BAR_HEIGHT_DP + fontStyle.extraVerticalPaddingDp)
            )
        }

        val langBadge = TextView(this).apply {
            text = languageMode.badgeText
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            applyBengaliTypography(this, bold = true)
            isSingleLine = true
            minWidth = dp(40)
            setPadding(dp(9), dp(3), dp(9), dp(3))
            contentDescription = "Active Language Badge " + languageMode.displayName + ". Tap to switch."
            background = createRoundedBackground(
                primaryAccent,
                primaryAccent,
                radiusDp = KEY_CORNER_RADIUS_DP,
                strokeWidthDp = strokeWidthDp
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(SUGGESTION_CHIP_HEIGHT_DP)
            ).apply {
                setMargins(dp(2), 0, dp(4), 0)
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
     * Recomputes bilingual word and mixed-sentence suggestions for the current composing word
     * or next-word context and updates the suggestion bar in-place with zero layout jumping.
     */
    private fun updateSuggestionsAndRefreshBar() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val suggestionsEnabled = prefs.getBoolean("keyboard_suggestions_enabled", true)
        val autocorrectEnabled = prefs.getBoolean("keyboard_autocorrect_enabled", true)
        val nextWordEnabled = prefs.getBoolean("keyboard_next_word_prediction", true)
        val banglaNumerals = prefs.getBoolean("keyboard_bangla_numerals", true)
        val smartLangDetect = prefs.getBoolean("keyboard_smart_language_detection", true)
        val highContrast = resolveHighContrastEnabled(prefs)
        val strokeWidthDp = resolveKeyBorderStrokeWidthDp(highContrast)

        currentCandidates = suggestionEngine.getSuggestions(
            composingText = composingRomanBuffer.toString(),
            previousWord = previousCommittedWord,
            languageMode = languageMode,
            suggestionsEnabled = suggestionsEnabled,
            autocorrectEnabled = autocorrectEnabled,
            nextWordPredictionEnabled = nextWordEnabled,
            useBengaliNumerals = banglaNumerals,
            maxSuggestions = 5,
            sentenceRawTokens = currentSentenceRawTokens,
            committedSentencePrefix = currentSentenceCommittedText.toString()
        )

        val row = suggestionChipsRow ?: return
        row.removeAllViews()

        val theme = resolveEffectiveTheme()
        val keyBg = Color.parseColor(theme.keyBgHex)
        val actionKeyBg = Color.parseColor(theme.modifierBgHex)
        val keyBorder = Color.parseColor(resolveKeyBorderHex(theme, highContrast))
        val textPrimary = Color.parseColor(theme.primaryTextHex)
        val textSecondary = Color.parseColor(theme.secondaryTextHex)
        val primaryAccent = Color.parseColor(theme.enterAccentHex)
        val maxCandidateWidthPx = (resources.displayMetrics.widthPixels * 0.56f).toInt().coerceAtLeast(dp(140))

        suggestionLangBadge?.apply {
            text = languageMode.badgeText
            background = createRoundedBackground(
                primaryAccent,
                primaryAccent,
                radiusDp = KEY_CORNER_RADIUS_DP,
                strokeWidthDp = strokeWidthDp
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
                background = createRoundedBackground(actionKeyBg, primaryAccent, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)
                setPadding(dp(9), dp(2), dp(7), dp(2))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(SUGGESTION_CHIP_HEIGHT_DP)
                ).apply { setMargins(dp(2), 0, dp(4), 0) }
            }
            val switchLabel = TextView(this).apply {
                text = "🌐 " + langPrompt.chipLabel
                setTextColor(textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                applyBengaliTypography(this, bold = true)
                isSingleLine = true
                contentDescription = langPrompt.chipLabel
                setPadding(0, 0, dp(6), 0)
                setOnClickListener {
                    performKeyFeedback(this)
                    switchKeyboardLanguage(targetMode = langPrompt.suggestedMode, viaSwipe = false)
                }
            }
            val dismissBtn = TextView(this).apply {
                text = "✕"
                setTextColor(textSecondary)
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
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                applyBengaliTypography(this, bold = false)
                setPadding(dp(6), dp(4), dp(6), dp(4))
            }
            row.addView(hint)
            return
        }

        currentCandidates.forEach { candidate ->
            val isHighlighted = candidate.isPrimary && composingRomanBuffer.isNotEmpty()
            val isMixedSentence = candidate.source == CandidateSource.MIXED_SENTENCE
            val chip = TextView(this).apply {
                // Non-color active indicator ("★ " or "⇄ ") for accessibility
                text = when {
                    isHighlighted -> "★ " + candidate.text
                    isMixedSentence -> "⇄ " + candidate.text
                    else -> candidate.text
                }
                gravity = Gravity.CENTER
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                minWidth = dp(48)
                maxWidth = maxCandidateWidthPx
                setTextColor(if (isHighlighted) Color.WHITE else textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                applyBengaliTypography(this, bold = isHighlighted || isMixedSentence)
                setPadding(dp(12), dp(3), dp(12), dp(3))
                contentDescription = when {
                    isHighlighted -> "Primary suggestion " + candidate.text
                    isMixedSentence -> "Mixed sentence suggestion " + candidate.text
                    else -> "Suggestion " + candidate.text
                }
                background = createRoundedBackground(
                    if (isHighlighted) primaryAccent else keyBg,
                    if (isHighlighted) primaryAccent else keyBorder,
                    radiusDp = KEY_CORNER_RADIUS_DP,
                    strokeWidthDp = strokeWidthDp
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(SUGGESTION_CHIP_HEIGHT_DP)
                ).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener {
                    performKeyFeedback(this)
                    commitSelectedCandidate(candidate)
                }
            }
            row.addView(chip)
        }

        // Personal Dictionary quick "+ Save" chip when user is composing a word (only explicit action saves to Personal Dictionary — Fix 2)
        val rawRoman = composingRomanBuffer.toString().trim()
        val topWordCandidate = currentCandidates.firstOrNull { it.source != CandidateSource.MIXED_SENTENCE }?.text.orEmpty()
        if (rawRoman.length >= 2 && topWordCandidate.isNotEmpty()) {
            val saveChip = TextView(this).apply {
                text = "+ Save"
                gravity = Gravity.CENTER
                isSingleLine = true
                setTextColor(textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT_BOLD
                contentDescription = "Save " + rawRoman + " as " + topWordCandidate + " to Personal Dictionary"
                setPadding(dp(10), dp(3), dp(10), dp(3))
                background = createRoundedBackground(actionKeyBg, keyBorder, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(SUGGESTION_CHIP_HEIGHT_DP)
                ).apply {
                    setMargins(dp(4), 0, dp(2), 0)
                }
                setOnClickListener {
                    performKeyFeedback(this)
                    userDictionary.addOrUpdateWord(rawRoman, topWordCandidate, languageMode)
                    updateSuggestionsAndRefreshBar()
                    showTemporaryKeyPopup("Saved '" + rawRoman + " → " + topWordCandidate + "'")
                }
            }
            row.addView(saveChip)
        }
    }

    /**
     * Commits a user-tapped suggestion candidate into the active InputConnection (Fix 2 & Fix 3 & v1.7.2 Requirement 1).
     * - If the candidate is a [CandidateSource.MIXED_SENTENCE], replaces the preceding committed clause
     *   prefix cleanly while preserving punctuation, cursor position, and Undo history.
     * - Calls [BengaliSuggestionEngine.onCandidateSelectedByUser] which updates adaptive ranking ONLY
     *   and never automatically saves an entry into the Personal Dictionary (Fix 2).
     */
    private fun commitSelectedCandidate(candidate: BengaliCandidate) {
        val rawRoman = composingRomanBuffer.toString()
        val beforeSnapshot = withSafeInputConnection("getTextBeforeCandidate", resetComposingOnError = false) {
            it.getTextBeforeCursor(160, 0)?.toString().orEmpty()
        }.orEmpty()
        val basePrefix = if (activeComposingPreview.isNotEmpty() && beforeSnapshot.endsWith(activeComposingPreview)) {
            beforeSnapshot.removeSuffix(activeComposingPreview)
        } else {
            beforeSnapshot
        }

        if (candidate.source == CandidateSource.MIXED_SENTENCE && candidate.replacePrecedingSentenceChars > 0) {
            val replaceLen = candidate.replacePrecedingSentenceChars.coerceAtMost(basePrefix.length)
            val replacedPortion = basePrefix.takeLast(replaceLen).trimEnd()
            val retainedPrefix = basePrefix.dropLast(replaceLen)
            val trailingInReplaced = replacedPortion.takeLastWhile { it in setOf('.', '?', '!', ',', ';', ':', '।') }
            val candidateHasTerminalPunct = candidate.text.lastOrNull() in setOf('.', '?', '!', ',', ';', ':', '।')
            val finalSentence = if (trailingInReplaced.isNotEmpty() && !candidateHasTerminalPunct) {
                candidate.text + trailingInReplaced
            } else {
                candidate.text
            }
            val committedText = finalSentence + " "

            isInternalComposingUpdate = true
            try {
                withSafeInputConnection("commitMixedSentenceCandidate") { ic ->
                    ic.beginBatchEdit()
                    ic.finishComposingText()
                    if (activeComposingPreview.isNotEmpty()) {
                        ic.deleteSurroundingText(activeComposingPreview.length, 0)
                    }
                    if (replaceLen > 0) {
                        ic.deleteSurroundingText(replaceLen, 0)
                    }
                    ic.commitText(committedText, 1)
                    ic.endBatchEdit()
                }
            } finally {
                isInternalComposingUpdate = false
            }

            undoRedoManager.recordEdit(
                beforeText = basePrefix,
                afterText = retainedPrefix + committedText,
                description = "Commit Mixed Sentence"
            )
            currentSentenceCommittedText.clear()
            currentSentenceCommittedText.append(committedText)
            if (rawRoman.isNotBlank()) {
                currentSentenceRawTokens.add(rawRoman.trim())
            }
            lastClauseTerminatedWithPunctuation = committedText.trimEnd().lastOrNull() in setOf('.', '?', '!', '।')
        } else {
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
            recordCommittedWordInSentenceClause(rawRoman.ifBlank { candidate.text }, candidate.text, " ")
            lastClauseTerminatedWithPunctuation = candidate.text.lastOrNull() in setOf('.', '?', '!', '।')
        }

        suggestionEngine.onCandidateSelectedByUser(
            rawRoman = rawRoman,
            selectedWord = candidate.text,
            languageMode = languageMode
        )

        previousCommittedWord = candidate.text.substringAfterLast(' ').ifBlank { candidate.text }
        composingRomanBuffer.clear()
        activeComposingPreview = ""
        updateSuggestionsAndRefreshBar()
    }

    /**
     * Builds dedicated top number row (1..0 in English mode or ১..০ in বাংলা mode) (v1.7.2 Requirement 2).
     * Tapping a key dynamically resolves the numeral for the currently active [languageMode].
     */
    private fun buildDedicatedNumberRow(
        keyHeightDp: Int,
        preset: KeyboardHeightPreset,
        keyBg: Int,
        keyPressedBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        strokeWidthDp: Int = 1
    ): View {
        numberRowKeyViews.clear()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val useBanglaDigits = languageMode == KeyboardLanguageMode.BANGLA_PHONETIC &&
            prefs.getBoolean("keyboard_bangla_numerals", true)
        val digits = resolveNumberRowDigits(languageMode, useBanglaDigits)
        val rowHeightDp = resolveNumberRowHeightDp(keyHeightDp)
        val digitSp = (preset.numberSp * 0.76f).coerceIn(15f, 18f)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(ROW_VERTICAL_PADDING_DP), 0, dp(ROW_VERTICAL_PADDING_DP))
        }
        digits.forEachIndexed { digitIndex, initialDigit ->
            val keyView = createFunctionKeyView(
                label = initialDigit,
                weight = 1f,
                heightDp = rowHeightDp,
                textSizeSp = digitSp,
                bgColor = keyBg,
                pressedBgColor = keyPressedBg,
                borderColor = keyBorder,
                textColor = textPrimary,
                strokeWidthDp = strokeWidthDp,
                contentDesc = "Number " + initialDigit
            ) {
                finishCurrentCompositionSafely()
                val activeBanglaNum = languageMode == KeyboardLanguageMode.BANGLA_PHONETIC &&
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("keyboard_bangla_numerals", true)
                val currentDigit = resolveNumberRowDigits(languageMode, activeBanglaNum).getOrElse(digitIndex) { initialDigit }
                commitDirectTextWithUndo(currentDigit, "Digit")
            }
            numberRowKeyViews.add(keyView)
            row.addView(keyView)
        }
        return row
    }

    /**
     * Builds the Clean Top Icon Toolbar (v1.7.2 Requirements 5 & 6 + Visual Polish):
     * All 7 toolbar buttons share uniform weight (1f), height (34dp), corner radius (8dp),
     * balanced icon sizing, and distinct selected/unselected visual states.
     * 1. Clipboard Icon (ic_content_copy) — ClipVault App Shortcut (tap -> MainActivity Clipboard History, long-press -> Settings)
     * 2. ☺ — Emoji Panel
     * 3. ⚙ — Keyboard Settings
     * 4. ✎ — Text Editing Tools (Select All, Copy, Cut, Paste, Left/Right, Safe Undo/Redo)
     * 5. 123 — Number/Symbol Layout
     * 6. ◐ — Theme Cycler
     * 7. ⋯ — More Options (One-Handed, Key Size, Number Row, Haptics, About Developer ⓘ, Switch Keyboard)
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
            setPadding(dp(2), dp(2), dp(2), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(TOOLBAR_HEIGHT_DP)
            )
        }

        // 1. FIRST ACTION: Clean Vector Clipboard Shortcut Icon (uniform 1f weight, 34dp height, 8dp corner radius)
        val clipVaultBrandBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_content_copy)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = createRoundedBackground(primaryAccent, primaryAccent, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)
            layoutParams = LinearLayout.LayoutParams(0, dp(TOOLBAR_BUTTON_HEIGHT_DP), 1f).apply {
                setMargins(dp(KEY_HORIZONTAL_MARGIN_DP), 0, dp(KEY_HORIZONTAL_MARGIN_DP), 0)
            }
            contentDescription = "Open ClipVault"
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
            val isTextBadge = iconLabel.length > 1
            val btn = TextView(this).apply {
                text = iconLabel
                gravity = Gravity.CENTER
                isSingleLine = true
                includeFontPadding = false
                setTextColor(if (isSelected) Color.WHITE else textPrimary)
                alpha = if (isSelected) 1.0f else 0.90f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isTextBadge) 12f else 14.5f)
                typeface = Typeface.DEFAULT_BOLD
                contentDescription = desc
                background = if (isSelected) {
                    createRoundedBackground(primaryAccent, primaryAccent, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)
                } else {
                    createRoundedBackground(actionKeyBg, keyBorder, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(TOOLBAR_BUTTON_HEIGHT_DP), 1f).apply {
                    setMargins(dp(KEY_HORIZONTAL_MARGIN_DP), 0, dp(KEY_HORIZONTAL_MARGIN_DP), 0)
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

        // 4. Text Editing Tools (with Safe Keyboard-Managed Undo/Redo)
        addToolbarAction("✎", "Text Editing and Undo Redo Tools", activePanel == ActivePanel.TEXT_EDITING) {
            finishCurrentCompositionSafely()
            activePanel = if (activePanel == ActivePanel.TEXT_EDITING) ActivePanel.NONE else ActivePanel.TEXT_EDITING
            rebuildActiveInputView()
        }

        // 5. Number/Symbol Layout
        addToolbarAction("123", "Number and Symbol Layout", symbolPage != SymbolPage.LETTERS && activePanel == ActivePanel.NONE) {
            finishCurrentCompositionSafely()
            activePanel = ActivePanel.NONE
            symbolPage = toggleSymbolMode(symbolPage)
            rebuildActiveInputView()
        }

        // 6. Theme Switcher
        addToolbarAction("◐", "Switch Keyboard Theme", false) {
            val nextTheme = cycleNextKeyboardTheme(theme)
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("keyboard_theme_mode", nextTheme.prefKey)
                .apply()
            rebuildActiveInputView()
        }

        // 7. More Options (contains About Developer ⓘ so main toolbar stays uncluttered — v1.7.2 Requirement 6)
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
            setPadding(dp(horizontalInsetDp), dp(ROW_VERTICAL_PADDING_DP), dp(horizontalInsetDp), dp(ROW_VERTICAL_PADDING_DP))
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
     * Creates a keycap with secondary number/symbol, primary character, TalkBack atomic focus hierarchy (Fix 8),
     * and anchored Key Pop-up Preview above the key on touch down (Fix 1).
     * Refined for visual consistency: uniform 8dp corner radius, consistent stroke width on press,
     * and balanced primary/secondary label positioning.
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
        val normalDrawable = createRoundedBackground(keyBg, keyBorder, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)
        val pressedDrawable = createRoundedBackground(keyPressedBg, keyBorder, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)

        val prefsForInit = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val banglaNumeralsInit = prefsForInit.getBoolean("keyboard_bangla_numerals", true)
        val initialSecSymbol = resolveSecondarySymbol(globalIndex, languageMode, banglaNumeralsInit)

        val secondaryText = TextView(this).apply {
            text = initialSecSymbol
            gravity = Gravity.TOP or Gravity.END
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, preset.secondarySp)
            applyBengaliTypography(this, bold = true)
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(0, dp(2), dp(4), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val currentOutput = resolveKeyOutput(baseLetter, globalIndex, shiftState, symbolPage, languageMode, banglaNumeralsInit)
        val primaryText = TextView(this).apply {
            text = currentOutput
            gravity = Gravity.CENTER
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveLetterSp)
            applyBengaliTypography(this, bold = false)
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(0, dp(3), 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val cellContainer = FrameLayout(this).apply {
            background = normalDrawable
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = resolveKeyAccessibilityLabel(currentOutput, languageMode, shiftState, initialSecSymbol)
            layoutParams = LinearLayout.LayoutParams(0, dp(effectiveKeyHeightDp), 1f).apply {
                setMargins(dp(KEY_HORIZONTAL_MARGIN_DP), 0, dp(KEY_HORIZONTAL_MARGIN_DP), 0)
            }
            addView(secondaryText)
            addView(primaryText)
        }

        var longPressConsumed = false
        val longPressRunnable = Runnable {
            if (symbolPage == SymbolPage.LETTERS) {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val useBanglaDigits = prefs.getBoolean("keyboard_bangla_numerals", true)
                val outSec = resolveSecondarySymbol(globalIndex, languageMode, useBanglaDigits)
                if (outSec.isNotEmpty()) {
                    longPressConsumed = true
                    performKeyFeedback(cellContainer)
                    finishCurrentCompositionSafely()
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
                    v.scaleX = 0.97f
                    v.scaleY = 0.97f
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
                            textSecondary,
                            strokeWidthDp
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
        primaryAccent: Int,
        strokeWidthDp: Int = 1
    ): TextView {
        val normalDrawable = createRoundedBackground(bgColor, borderColor, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)
        val pressedDrawable = createRoundedBackground(pressedBgColor, borderColor, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)

        return TextView(this).apply {
            text = "🌐 " + languageMode.badgeText
            gravity = Gravity.CENTER
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(3), 0, dp(3), 0)
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            applyBengaliTypography(this, bold = true)
            background = normalDrawable
            contentDescription = resolveKeyAccessibilityLabel("GLOBE", languageMode, shiftState)
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
                setMargins(dp(KEY_HORIZONTAL_MARGIN_DP), 0, dp(KEY_HORIZONTAL_MARGIN_DP), 0)
            }
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.scaleX = 0.97f
                        v.scaleY = 0.97f
                        v.background = pressedDrawable
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.scaleX = 1f
                        v.scaleY = 1f
                        v.background = normalDrawable
                    }
                }
                false
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
        primaryAccent: Int,
        strokeWidthDp: Int = 1
    ): TextView {
        val normalDrawable = createRoundedBackground(bgColor, borderColor, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)
        val pressedDrawable = createRoundedBackground(pressedBgColor, borderColor, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)

        var downX = 0f
        var downY = 0f
        var swipeTriggered = false

        return TextView(this).apply {
            text = resolveSpacebarLabel(languageMode)
            gravity = Gravity.CENTER
            isSingleLine = true
            setTextColor(textColor)
            alpha = 0.90f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            applyBengaliTypography(this, bold = true)
            background = normalDrawable
            contentDescription = resolveKeyAccessibilityLabel("SPACE", languageMode, shiftState)
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
                setMargins(dp(KEY_HORIZONTAL_MARGIN_DP), 0, dp(KEY_HORIZONTAL_MARGIN_DP), 0)
            }
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        swipeTriggered = false
                        v.scaleX = 0.98f
                        v.scaleY = 0.98f
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
        strokeWidthDp: Int = 1,
        isSpacebar: Boolean = false,
        contentDesc: String = label,
        onClick: () -> Unit
    ): TextView {
        val normalDrawable = createRoundedBackground(bgColor, borderColor, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)
        val pressedDrawable = createRoundedBackground(pressedBgColor, borderColor, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)

        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            isSingleLine = true
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            applyBengaliTypography(this, bold = true)
            contentDescription = contentDesc
            background = normalDrawable
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
                setMargins(dp(KEY_HORIZONTAL_MARGIN_DP), 0, dp(KEY_HORIZONTAL_MARGIN_DP), 0)
            }
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.scaleX = 0.97f
                        v.scaleY = 0.97f
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
        textColor: Int,
        strokeWidthDp: Int = 1
    ): TextView {
        val normalDrawable = createRoundedBackground(bgColor, borderColor, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)
        val pressedDrawable = createRoundedBackground(pressedBgColor, borderColor, radiusDp = KEY_CORNER_RADIUS_DP, strokeWidthDp = strokeWidthDp)

        return TextView(this).apply {
            text = "⌫"
            gravity = Gravity.CENTER
            isSingleLine = true
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            contentDescription = resolveKeyAccessibilityLabel("BACKSPACE", languageMode, shiftState)
            background = normalDrawable
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
                setMargins(dp(KEY_HORIZONTAL_MARGIN_DP), 0, dp(KEY_HORIZONTAL_MARGIN_DP), 0)
            }
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.scaleX = 0.96f
                        v.scaleY = 0.96f
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
                applyBengaliTypography(this, bold = true)
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
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getString("keyboard_recent_emojis", "😂,❤️,🔥,👍,🎉,✨,🙏") ?: "")
            .split(",")
            .filter { it.isNotBlank() && it != emoji }
        val updated = (listOf(emoji) + current).take(14)
        prefs.edit().putString("keyboard_recent_emojis", updated.joinToString(",")).apply()
    }

    private fun getEmojisForCategory(categoryIndex: Int): List<String> {
        if (categoryIndex == 0) {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = (prefs.getString("keyboard_recent_emojis", "😂,❤️,🔥,👍,🎉,✨,🙏,😊,🚀,💯,✅,⭐,💡,🎯") ?: "")
                .split(",")
                .filter { it.isNotBlank() }
            if (saved.isNotEmpty()) return saved
        }
        return EMOJI_CATEGORIES.getOrElse(categoryIndex) { EMOJI_CATEGORIES[1] }.second
    }

    /**
     * Text Editing Panel with Safe Keyboard-Managed Undo/Redo (Fix 4 & Feature 3).
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
     * Executes a safe keyboard-managed Undo operation using [undoRedoManager] (Fix 4 & v1.7.2 Requirement 2).
     * Restores text and cursor position when supported by the editor.
     */
    private fun performKeyboardUndo() {
        finishCurrentCompositionSafely()
        if (!undoRedoManager.canUndo()) {
            showTemporaryKeyPopup("Nothing to undo")
            return
        }
        isInternalComposingUpdate = true
        try {
            withSafeInputConnection("performKeyboardUndo") { ic ->
                val currentBefore = ic.getTextBeforeCursor(160, 0)?.toString()
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
                if (currentBefore != null && currentBefore.length == op.afterText.length && op.cursorBefore >= 0) {
                    try {
                        ic.setSelection(op.cursorBefore, op.cursorBefore)
                    } catch (_: Exception) {
                    }
                }
                ic.endBatchEdit()
                clearSentenceClauseContext()
                showTemporaryKeyPopup("Undid " + op.description)
            }
        } finally {
            isInternalComposingUpdate = false
        }
    }

    /**
     * Executes a safe keyboard-managed Redo operation using [undoRedoManager] (Fix 4 & v1.7.2 Requirement 2).
     * Re-applies text and restores cursor position when supported by the editor.
     */
    private fun performKeyboardRedo() {
        finishCurrentCompositionSafely()
        if (!undoRedoManager.canRedo()) {
            showTemporaryKeyPopup("Nothing to redo")
            return
        }
        isInternalComposingUpdate = true
        try {
            withSafeInputConnection("performKeyboardRedo") { ic ->
                val currentBefore = ic.getTextBeforeCursor(160, 0)?.toString()
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
                if (currentBefore != null && currentBefore.length == op.beforeText.length && op.cursorAfter >= 0) {
                    try {
                        ic.setSelection(op.cursorAfter, op.cursorAfter)
                    } catch (_: Exception) {
                    }
                }
                ic.endBatchEdit()
                clearSentenceClauseContext()
                showTemporaryKeyPopup("Redid " + op.description)
            }
        } finally {
            isInternalComposingUpdate = false
        }
    }

    private fun commitDirectTextWithUndo(textToCommit: String, description: String) {
        if (textToCommit.isEmpty()) return
        var beforePrefix = ""
        var selectedText = ""
        withSafeInputConnection("getTextBeforeDirectCommit", resetComposingOnError = false) { ic ->
            beforePrefix = ic.getTextBeforeCursor(96, 0)?.toString().orEmpty()
            selectedText = ic.getSelectedText(0)?.toString().orEmpty()
        }
        isInternalComposingUpdate = true
        try {
            withSafeInputConnection("commitDirectText") { ic ->
                ic.commitText(textToCommit, 1)
            }
        } finally {
            isInternalComposingUpdate = false
        }
        if (selectedText.isNotEmpty()) {
            undoRedoManager.recordSelectionReplacement(
                beforePrefix = beforePrefix,
                selectedText = selectedText,
                replacementText = textToCommit,
                description = description
            )
        } else {
            undoRedoManager.recordEdit(
                beforeText = beforePrefix,
                afterText = beforePrefix + textToCommit,
                description = description
            )
        }
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
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentIntensity = getVibrationIntensity()
        val showNumberRow = prefs.getBoolean("keyboard_show_number_row", true)
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
            text = "ClipVault v1.7.3 Quick Controls"
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
        preset: KeyboardHeightPreset,
        strokeWidthDp: Int = 1
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
        refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset, strokeWidthDp)
    }

    /**
     * Handles character key taps for both Mode A (English) and Mode B (Bangla Phonetic)
     * using incremental InputConnection composition and in-place UI updates (Feature 13 & Fix 10).
     */
    private fun commitKeyCharacter(
        baseLetter: String,
        index: Int,
        preset: KeyboardHeightPreset,
        primaryAccent: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        strokeWidthDp: Int = 1
    ) {
        if (lastClauseTerminatedWithPunctuation && composingRomanBuffer.isEmpty()) {
            clearSentenceClauseContext()
        }
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val suggestionsEnabled = prefs.getBoolean("keyboard_suggestions_enabled", true)
        val banglaNumerals = prefs.getBoolean("keyboard_bangla_numerals", true)
        val output = resolveKeyOutput(baseLetter, index, shiftState, symbolPage, languageMode, banglaNumerals)
        suggestionEngine.clearLastCorrectionState()

        val editorInfo = currentInputEditorInfo
        val isPassword = editorInfo != null && isInputFieldPassword(editorInfo.inputType)

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
            val primaryBangla = currentCandidates.firstOrNull { it.source != CandidateSource.MIXED_SENTENCE }?.text
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
            refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, textSecondary, preset, strokeWidthDp)
        }
    }

    /**
     * Handles Spacebar taps: commits the active composing word respecting Suggestion-First default
     * auto-correction (Fix 6) and records Undo + sentence clause state (Fix 3 & 4 & v1.7.2 Requirement 1).
     */
    private fun handleSpacebarTap() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autocorrectEnabled = prefs.getBoolean("keyboard_autocorrect_enabled", true)
        val autoApplyOnSpace = prefs.getBoolean("keyboard_auto_apply_on_space", false)
        val banglaNumerals = prefs.getBoolean("keyboard_bangla_numerals", true)

        if (composingRomanBuffer.isNotEmpty()) {
            if (lastClauseTerminatedWithPunctuation) {
                clearSentenceClauseContext()
            }
            val rawInput = composingRomanBuffer.toString()
            val beforeSnapshot = withSafeInputConnection("getTextBeforeSpace", resetComposingOnError = false) {
                it.getTextBeforeCursor(96, 0)?.toString().orEmpty()
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
            recordCommittedWordInSentenceClause(rawInput, committedWord, " ")
            suggestionEngine.recordAutoCommit(rawInput, committedWord, " ")
            userDictionary.recordRecentWord(committedWord)
            previousCommittedWord = committedWord
            composingRomanBuffer.clear()
            activeComposingPreview = ""
            updateSuggestionsAndRefreshBar()
        } else {
            suggestionEngine.clearLastCorrectionState()
            if (currentSentenceCommittedText.isNotEmpty() && !currentSentenceCommittedText.endsWith(" ")) {
                currentSentenceCommittedText.append(" ")
            }
            commitDirectTextWithUndo(" ", "Space")
            updateSuggestionsAndRefreshBar()
        }
    }

    private fun commitPunctuationOrSeparator(punctuation: String) {
        val isSentenceTerminator = punctuation in setOf(".", "।", "?", "!")
        if (composingRomanBuffer.isNotEmpty()) {
            if (lastClauseTerminatedWithPunctuation) {
                clearSentenceClauseContext()
            }
            val rawInput = composingRomanBuffer.toString()
            val committedWord = activeComposingPreview.ifEmpty { rawInput }
            val beforeSnapshot = withSafeInputConnection("getTextBeforePunctuation", resetComposingOnError = false) {
                it.getTextBeforeCursor(96, 0)?.toString().orEmpty()
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
            // Preserve commas AND terminal punctuation (?, ., !, ।) in currentSentenceRawTokens and
            // currentSentenceCommittedText so full-sentence mixed candidates retain punctuation!
            val rawPunct = if (punctuation == "।" && rawInput.none { it.code in 0x0980..0x09FF }) "." else punctuation
            recordCommittedWordInSentenceClause(rawInput, committedWord, rawPunct)
            if (rawPunct != punctuation) {
                // Keep committed editor text exact ('।' vs '.')
                currentSentenceCommittedText.setLength((currentSentenceCommittedText.length - rawPunct.length).coerceAtLeast(0))
                currentSentenceCommittedText.append(punctuation)
            }
            previousCommittedWord = committedWord
            lastClauseTerminatedWithPunctuation = isSentenceTerminator
            composingRomanBuffer.clear()
            activeComposingPreview = ""
            updateSuggestionsAndRefreshBar()
        } else {
            if (currentSentenceRawTokens.isNotEmpty()) {
                val lastIdx = currentSentenceRawTokens.lastIndex
                val rawPunct = if (punctuation == "।" && currentSentenceRawTokens[lastIdx].none { it.code in 0x0980..0x09FF }) "." else punctuation
                if (!currentSentenceRawTokens[lastIdx].endsWith(rawPunct)) {
                    currentSentenceRawTokens[lastIdx] = currentSentenceRawTokens[lastIdx] + rawPunct
                }
                if (currentSentenceCommittedText.endsWith(" ")) {
                    currentSentenceCommittedText.setLength(currentSentenceCommittedText.length - 1)
                }
                currentSentenceCommittedText.append(punctuation)
                lastClauseTerminatedWithPunctuation = isSentenceTerminator
            } else if (isSentenceTerminator) {
                clearSentenceClauseContext()
            }
            commitDirectTextWithUndo(punctuation, "Punctuation")
            updateSuggestionsAndRefreshBar()
        }
    }

    private fun refreshKeyLabels(
        primaryAccent: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        textSecondary: Int,
        preset: KeyboardHeightPreset,
        strokeWidthDp: Int = 1
    ) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val banglaNumerals = prefs.getBoolean("keyboard_bangla_numerals", true)
        val keySizePreset = getKeySizePreset()

        // Refresh dedicated number row labels ("১..০" in বাংলা mode, "1..0" in English mode)
        val numRowDigits = resolveNumberRowDigits(languageMode, languageMode == KeyboardLanguageMode.BANGLA_PHONETIC && banglaNumerals)
        numberRowKeyViews.forEachIndexed { idx, tv ->
            val digitLabel = numRowDigits.getOrElse(idx) { "" }
            tv.text = digitLabel
            tv.contentDescription = "Number " + digitLabel
        }

        keyCellViews.forEachIndexed { idx, cell ->
            val baseChar = BASE_QWERTY_KEYS.getOrElse(idx) { "" }
            val outChar = resolveKeyOutput(baseChar, idx, shiftState, symbolPage, languageMode, banglaNumerals)
            val secSymbol = resolveSecondarySymbol(idx, languageMode, banglaNumerals)
            cell.primaryLabel.text = outChar
            cell.secondaryLabel.text = secSymbol
            cell.container.contentDescription = resolveKeyAccessibilityLabel(outChar, languageMode, shiftState, secSymbol)
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
                if (isShiftHighlighted) primaryAccent else keyBorder,
                radiusDp = KEY_CORNER_RADIUS_DP,
                strokeWidthDp = strokeWidthDp
            )
            setTextColor(if (isShiftHighlighted) Color.WHITE else textPrimary)
        }
        modeKeyView?.text = if (symbolPage == SymbolPage.LETTERS) "?123" else "ABC"
        spacebarView?.text = resolveSpacebarLabel(languageMode)
        globeKeyView?.text = "🌐 " + languageMode.badgeText
        periodKeyView?.text = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) "।" else "."
        periodKeyView?.contentDescription = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) "Bengali Dari" else "Period"

        val editorInfo = currentInputEditorInfo
        val enterBehavior = EnterKeyBehavior.fromKey(
            prefs.getString(PREF_ENTER_KEY_BEHAVIOR, EnterKeyBehavior.SMART_MULTILINE.prefKey)
        )
        enterKeyView?.text = resolveEnterKeyLabel(
            inputType = editorInfo?.inputType ?: InputType.TYPE_CLASS_TEXT,
            imeOptions = editorInfo?.imeOptions ?: EditorInfo.IME_ACTION_NONE,
            behavior = enterBehavior
        )
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
                    currentCandidates.firstOrNull { it.source != CandidateSource.MIXED_SENTENCE }?.text
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
            val beforeSnapshot = withSafeInputConnection("getTextBeforeRevert", resetComposingOnError = false) {
                it.getTextBeforeCursor(96, 0)?.toString().orEmpty()
            }.orEmpty()
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
            if (beforeSnapshot.length >= totalReplacedLen) {
                val retained = beforeSnapshot.dropLast(totalReplacedLen)
                undoRedoManager.recordEdit(
                    beforeText = beforeSnapshot,
                    afterText = retained + lastCorrection.rawRomanOrTypo,
                    description = "Revert Autocorrect"
                )
            }
            showTemporaryKeyPopup("Reverted to '" + lastCorrection.rawRomanOrTypo + "'")
            suggestionEngine.clearLastCorrectionState()
            return
        }

        isInternalComposingUpdate = true
        try {
            withSafeInputConnection("backspaceDelete") { ic ->
                val selected = ic.getSelectedText(0)?.toString().orEmpty()
                val before = ic.getTextBeforeCursor(96, 0)?.toString().orEmpty()
                if (selected.isNotEmpty()) {
                    ic.commitText("", 1)
                    undoRedoManager.recordSelectionReplacement(
                        beforePrefix = before,
                        selectedText = selected,
                        replacementText = "",
                        description = "Delete Selection"
                    )
                } else {
                    ic.deleteSurroundingText(1, 0)
                    if (before.isNotEmpty()) {
                        undoRedoManager.recordEdit(
                            beforeText = before,
                            afterText = before.dropLast(1),
                            description = "Backspace"
                        )
                    }
                    if (currentSentenceCommittedText.isNotEmpty()) {
                        currentSentenceCommittedText.deleteCharAt(currentSentenceCommittedText.length - 1)
                    }
                }
            }
        } finally {
            isInternalComposingUpdate = false
        }
    }

    /**
     * Handles the Enter / Action key across multiline chat fields (WhatsApp, Messenger, Gemini, Notes)
     * and single-line search/URL/form inputs (v1.7.2 Requirement 4).
     *
     * CRITICAL:
     * - Commits any active composing word first so Bengali/English composition is never lost.
     * - In multiline text fields (TYPE_TEXT_FLAG_MULTI_LINE, TYPE_TEXT_FLAG_IME_MULTI_LINE, or IME_FLAG_NO_ENTER_ACTION),
     *   commits a newline ("\n") and keeps the keyboard open (never calls requestHideSelf or unwanted Done/Send).
     * - In single-line Search / Go / Send / Next / Done fields without multiline flags, performs the editor action.
     */
    private fun handleEnterAction() {
        finishCurrentCompositionSafely()
        clearSentenceClauseContext()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val behavior = EnterKeyBehavior.fromKey(
            prefs.getString(PREF_ENTER_KEY_BEHAVIOR, EnterKeyBehavior.SMART_MULTILINE.prefKey)
        )
        val info = currentInputEditorInfo
        val inputType = info?.inputType ?: InputType.TYPE_CLASS_TEXT
        val imeOptions = info?.imeOptions ?: EditorInfo.IME_ACTION_NONE

        when (resolveEnterKeyDisposition(inputType, imeOptions, behavior)) {
            EnterKeyDisposition.INSERT_NEWLINE -> {
                commitDirectTextWithUndo("\n", "Newline")
                updateSuggestionsAndRefreshBar()
            }
            EnterKeyDisposition.PERFORM_EDITOR_ACTION -> {
                val actionId = imeOptions and EditorInfo.IME_MASK_ACTION
                withSafeInputConnection("performEditorAction") { ic ->
                    ic.performEditorAction(actionId)
                }
            }
            EnterKeyDisposition.SEND_ENTER_KEY_EVENT -> {
                withSafeInputConnection("sendEnterKeyEvent") { ic ->
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
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

    /**
     * Testable crash-safe executor for [InputConnection] actions (Fix 9).
     * Catches recoverable [Exception]s only and NEVER catches fatal JVM [Error]s
     * (such as OutOfMemoryError, StackOverflowError, LinkageError, etc.).
     */
    object SafeInputConnectionExecutor {
        inline fun <I, T> execute(
            inputConnection: I?,
            actionName: String,
            onRecoverableException: (Exception) -> Unit = {},
            block: (I) -> T
        ): T? {
            val ic = inputConnection ?: return null
            return try {
                block(ic)
            } catch (e: Exception) {
                onRecoverableException(e)
                null
            }
        }
    }

    companion object {
        const val PREFS_NAME = "clipvault_settings"

        // Canonical Settings Preference Keys (Fix 1)
        const val PREF_KEY_POPUP_PREVIEW = "keyboard_key_popup_preview"
        const val LEGACY_PREF_KEY_POPUP_ENABLED = "keyboard_key_popup_enabled"

        const val PREF_HIGH_CONTRAST = "keyboard_high_contrast_accessibility"
        const val LEGACY_PREF_HIGH_CONTRAST = "keyboard_high_contrast"

        const val PREF_ENTER_KEY_BEHAVIOR = "keyboard_enter_key_behavior"

        // Keyboard Picker Display Names & Toolbar Metadata (v1.7.2 Requirements 5, 6, 7)
        const val IME_DISPLAY_NAME = "ClipVault Keyboard"
        const val IME_SUBTYPE_LABEL_EN = "English (US)"
        const val IME_SUBTYPE_LABEL_BN = "বাংলা"
        const val TOOLBAR_SHORTCUT_ICON_RESOURCE = "ic_content_copy"
        const val TOOLBAR_HAS_DUPLICATE_ABOUT_BUTTON = false
        val TOP_TOOLBAR_ACTION_IDS = listOf(
            "CLIPBOARD_SHORTCUT",
            "EMOJI",
            "SETTINGS",
            "TEXT_EDITING",
            "SYMBOLS_123",
            "THEME",
            "MORE_OPTIONS"
        )

        // Unified Keyboard Visual Design System Constants (Final UI Polish)
        const val KEY_CORNER_RADIUS_DP = 8
        const val POPUP_CORNER_RADIUS_DP = 10
        const val KEY_HORIZONTAL_MARGIN_DP = 2
        const val ROW_VERTICAL_PADDING_DP = 2
        const val TOOLBAR_HEIGHT_DP = 42
        const val TOOLBAR_BUTTON_HEIGHT_DP = 34
        const val SUGGESTION_BAR_HEIGHT_DP = 44
        const val SUGGESTION_CHIP_HEIGHT_DP = 34

        fun resolveResponsiveRow2InsetDp(screenWidthDp: Int, oneHandedMode: OneHandedMode): Int {
            if (oneHandedMode != OneHandedMode.OFF) return 6
            return when {
                screenWidthDp < 360 -> 10
                screenWidthDp >= 420 -> 18
                else -> 14
            }
        }

        fun resolveNumberRowHeightDp(effectiveKeyHeightDp: Int): Int {
            return (effectiveKeyHeightDp * 0.80f).toInt().coerceIn(36, 48)
        }

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
         * Resolves whether Key Pop-up Preview is enabled from canonical key [PREF_KEY_POPUP_PREVIEW]
         * with safe fallback to legacy key [LEGACY_PREF_KEY_POPUP_ENABLED] (Fix 1).
         */
        fun resolveKeyPopupEnabled(prefs: SharedPreferences): Boolean {
            return resolveKeyPopupSetting(
                canonicalValue = if (prefs.contains(PREF_KEY_POPUP_PREVIEW)) prefs.getBoolean(PREF_KEY_POPUP_PREVIEW, true) else null,
                legacyValue = if (prefs.contains(LEGACY_PREF_KEY_POPUP_ENABLED)) prefs.getBoolean(LEGACY_PREF_KEY_POPUP_ENABLED, true) else null
            )
        }

        fun resolveKeyPopupSetting(canonicalValue: Boolean?, legacyValue: Boolean?): Boolean {
            return canonicalValue ?: legacyValue ?: true
        }

        /**
         * Resolves whether High Contrast Accessibility is enabled from canonical key [PREF_HIGH_CONTRAST]
         * with safe fallback to legacy key [LEGACY_PREF_HIGH_CONTRAST] (Fix 1 & Fix 8).
         */
        fun resolveHighContrastEnabled(prefs: SharedPreferences): Boolean {
            return resolveHighContrastSetting(
                canonicalValue = if (prefs.contains(PREF_HIGH_CONTRAST)) prefs.getBoolean(PREF_HIGH_CONTRAST, false) else null,
                legacyValue = if (prefs.contains(LEGACY_PREF_HIGH_CONTRAST)) prefs.getBoolean(LEGACY_PREF_HIGH_CONTRAST, false) else null
            )
        }

        fun resolveHighContrastSetting(canonicalValue: Boolean?, legacyValue: Boolean?): Boolean {
            return canonicalValue ?: legacyValue ?: false
        }

        /**
         * Safely migrates legacy settings keys (keyboard_key_popup_enabled and keyboard_high_contrast)
         * to their canonical v1.7.1 keys (keyboard_key_popup_preview and keyboard_high_contrast_accessibility)
         * without losing existing user preferences (Fix 1).
         */
        fun migrateLegacySettingsPreferencesIfNeeded(prefs: SharedPreferences) {
            val editor = prefs.edit()
            var changed = false
            if (!prefs.contains(PREF_KEY_POPUP_PREVIEW) && prefs.contains(LEGACY_PREF_KEY_POPUP_ENABLED)) {
                editor.putBoolean(PREF_KEY_POPUP_PREVIEW, prefs.getBoolean(LEGACY_PREF_KEY_POPUP_ENABLED, true))
                changed = true
            }
            if (!prefs.contains(PREF_HIGH_CONTRAST) && prefs.contains(LEGACY_PREF_HIGH_CONTRAST)) {
                editor.putBoolean(PREF_HIGH_CONTRAST, prefs.getBoolean(LEGACY_PREF_HIGH_CONTRAST, false))
                changed = true
            }
            if (changed) {
                editor.apply()
            }
        }

        /**
         * Pure helper determining whether a Key Pop-up Preview should be shown (Fix 1).
         */
        fun shouldShowKeyPopupPreview(
            keyPopupEnabled: Boolean,
            isPasswordField: Boolean,
            previewLabel: String
        ): Boolean {
            return keyPopupEnabled && !isPasswordField && previewLabel.isNotBlank()
        }

        /**
         * Resolves key border stroke width in dp when High Contrast is toggled ON vs OFF (Fix 1 & Fix 8).
         */
        fun resolveKeyBorderStrokeWidthDp(highContrast: Boolean): Int = if (highContrast) 2 else 1

        /**
         * Resolves key border color hex when High Contrast is toggled ON vs OFF (Fix 1 & Fix 8).
         */
        fun resolveKeyBorderHex(theme: KeyboardThemeMode, highContrast: Boolean): String {
            if (!highContrast) return theme.borderHex
            return if (theme == KeyboardThemeMode.LIGHT) "#000000" else "#FFFFFF"
        }

        /**
         * Resolves the Spacebar label for the active language mode ("English" or "বাংলা").
         */
        fun resolveSpacebarLabel(mode: KeyboardLanguageMode): String = mode.spacebarLabel

        /**
         * Resolves top number row digits:
         * - When [mode] is [KeyboardLanguageMode.BANGLA_PHONETIC] and [useBengaliNumerals] is true (default),
         *   returns Bengali numerals ১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯ ০.
         * - When [mode] is [KeyboardLanguageMode.ENGLISH], returns 1 2 3 4 5 6 7 8 9 0.
         */
        fun resolveNumberRowDigits(
            mode: KeyboardLanguageMode,
            useBengaliNumerals: Boolean = true
        ): List<String> {
            return if (mode == KeyboardLanguageMode.BANGLA_PHONETIC && useBengaliNumerals) {
                listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০")
            } else {
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            }
        }

        /**
         * Resolves how the Enter key behaves for the target editor field (v1.7.2 Requirement 4):
         * - In multiline text fields (WhatsApp, Messenger, Gemini, Notes, long-text inputs where
         *   [InputType.TYPE_TEXT_FLAG_MULTI_LINE], [InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE], or
         *   [EditorInfo.IME_FLAG_NO_ENTER_ACTION] is present), returns [EnterKeyDisposition.INSERT_NEWLINE]
         *   so pressing Enter inserts a newline ("\n") and never minimizes the keyboard.
         * - In single-line Search / Go / Send / Next / Done fields without multiline flags, returns
         *   [EnterKeyDisposition.PERFORM_EDITOR_ACTION].
         */
        fun resolveEnterKeyDisposition(
            inputType: Int,
            imeOptions: Int,
            behavior: EnterKeyBehavior = EnterKeyBehavior.SMART_MULTILINE
        ): EnterKeyDisposition {
            if (behavior == EnterKeyBehavior.ALWAYS_NEWLINE) {
                return EnterKeyDisposition.INSERT_NEWLINE
            }
            val isMultiLine = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 ||
                (inputType and InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0
            val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
            val actionId = imeOptions and EditorInfo.IME_MASK_ACTION

            if (behavior == EnterKeyBehavior.SMART_MULTILINE && (isMultiLine || noEnterAction)) {
                return EnterKeyDisposition.INSERT_NEWLINE
            }

            return when (actionId) {
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_ACTION_SEND,
                EditorInfo.IME_ACTION_NEXT,
                EditorInfo.IME_ACTION_DONE -> {
                    if (noEnterAction) EnterKeyDisposition.INSERT_NEWLINE else EnterKeyDisposition.PERFORM_EDITOR_ACTION
                }
                else -> {
                    if (isMultiLine || noEnterAction) {
                        EnterKeyDisposition.INSERT_NEWLINE
                    } else {
                        EnterKeyDisposition.SEND_ENTER_KEY_EVENT
                    }
                }
            }
        }

        /**
         * Resolves the visual icon/label for the Enter key based on editor flags and [EnterKeyBehavior].
         */
        fun resolveEnterKeyLabel(
            inputType: Int,
            imeOptions: Int,
            behavior: EnterKeyBehavior = EnterKeyBehavior.SMART_MULTILINE
        ): String {
            val disposition = resolveEnterKeyDisposition(inputType, imeOptions, behavior)
            if (disposition == EnterKeyDisposition.INSERT_NEWLINE) return "↵"
            return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_SEARCH -> "🔍"
                EditorInfo.IME_ACTION_GO -> "→"
                EditorInfo.IME_ACTION_SEND -> "➤"
                EditorInfo.IME_ACTION_NEXT -> "⇥"
                EditorInfo.IME_ACTION_DONE -> "✓"
                else -> "↵"
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
         * Returns a clear TalkBack screen-reader label for any keyboard key or action (Fix 8 & Feature 19).
         */
        fun resolveKeyAccessibilityLabel(
            keyRoleOrChar: String,
            mode: KeyboardLanguageMode,
            shiftState: ShiftState,
            secondarySymbol: String = ""
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
                    val primaryDesc = if (keyRoleOrChar.length == 1 && keyRoleOrChar[0].isLetter()) {
                        val caseDesc = if (keyRoleOrChar[0].isUpperCase()) "Capital " else ""
                        caseDesc + keyRoleOrChar
                    } else {
                        keyRoleOrChar
                    }
                    if (secondarySymbol.isNotEmpty()) {
                        primaryDesc + ", long press for " + secondarySymbol
                    } else {
                        primaryDesc
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
         * Returns the secondary number or symbol mapped to a QWERTY key index (e.g., Q=0 -> "1" / "১", P=9 -> "0" / "০").
         */
        fun resolveSecondarySymbol(
            index: Int,
            mode: KeyboardLanguageMode = KeyboardLanguageMode.ENGLISH,
            useBengaliNumerals: Boolean = true
        ): String {
            val raw = SECONDARY_QWERTY_SYMBOLS.getOrElse(index) { "" }
            return if (mode == KeyboardLanguageMode.BANGLA_PHONETIC && useBengaliNumerals && index in 0..9) {
                BengaliTransliterationRules.convertDigitsToBengali(raw)
            } else {
                raw
            }
        }

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
         * Resolves the output character for a given key index, ShiftState, SymbolPage, and LanguageMode.
         */
        fun resolveKeyOutput(
            baseLetter: String,
            index: Int,
            shiftState: ShiftState,
            symbolPage: SymbolPage,
            mode: KeyboardLanguageMode = KeyboardLanguageMode.ENGLISH,
            useBengaliNumerals: Boolean = true
        ): String {
            return when (symbolPage) {
                SymbolPage.NUMBERS_SYMBOLS_1 -> {
                    val raw = SYMBOL_KEYS_PAGE_1.getOrElse(index) { baseLetter }
                    if (mode == KeyboardLanguageMode.BANGLA_PHONETIC && useBengaliNumerals && index in 0..9) {
                        BengaliTransliterationRules.convertDigitsToBengali(raw)
                    } else {
                        raw
                    }
                }
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
