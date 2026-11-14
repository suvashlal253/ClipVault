package com.clipvault.android.ime

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
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
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import com.clipvault.android.service.ClipboardServiceHelper

/**
 * ClipVault Keyboard (IME) v1.4.0 — Redesigned for fast, comfortable thumb typing
 * with large high-contrast typography (24-26sp letters), Compact/Normal/Tall height modes,
 * single-tap Shift + double-tap Caps Lock, press-and-hold repeating Backspace,
 * and lifecycle-safe permitted clipboard capture.
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

    enum class KeyboardHeightPreset(
        val prefKey: String,
        val keyHeightDp: Int,
        val letterSp: Float,
        val numberSp: Float,
        val functionSp: Float
    ) {
        COMPACT("COMPACT", keyHeightDp = 45, letterSp = 24f, numberSp = 21f, functionSp = 20f),
        NORMAL("NORMAL", keyHeightDp = 52, letterSp = 25f, numberSp = 22.5f, functionSp = 21f),
        TALL("TALL", keyHeightDp = 58, letterSp = 26f, numberSp = 24f, functionSp = 22f);

        companion object {
            fun fromKey(raw: String?): KeyboardHeightPreset =
                values().firstOrNull { it.prefKey.equals(raw, ignoreCase = true) } ?: NORMAL
        }
    }

    private var clipboardManager: ClipboardManager? = null
    private var audioManager: AudioManager? = null
    private var isListenerRegistered = false

    private var shiftState: ShiftState = ShiftState.LOWERCASE
    private var symbolPage: SymbolPage = SymbolPage.LETTERS
    private var lastShiftTapUptimeMs: Long = 0L

    private val letterKeyViews = mutableListOf<TextView>()
    private var shiftKeyView: TextView? = null
    private var modeKeyView: TextView? = null
    private var statusBadgeView: TextView? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isBackspacePressed = false
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            if (!isBackspacePressed) return
            handleBackspace()
            mainHandler.postDelayed(this, 55L)
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
        registerClipboardListenerIfNeeded()
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
        // Rebuild input view if theme or height preset changed in Settings
        setInputView(buildKeyboardLayout())
        capturePermittedClipboard()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopBackspaceRepeat()
        isInputViewVisibleState = false
    }

    override fun onDestroy() {
        stopBackspaceRepeat()
        unregisterClipboardListener()
        isInputViewVisibleState = false
        isActiveInstance = false
        letterKeyViews.clear()
        super.onDestroy()
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

    private fun isDarkModeActive(): Boolean {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        return when (prefs.getString("app_theme_mode", "DARK")) {
            "LIGHT" -> false
            "DARK" -> true
            else -> {
                val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMask == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun getKeyboardHeightPreset(): KeyboardHeightPreset {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        return KeyboardHeightPreset.fromKey(prefs.getString("keyboard_height_mode", "NORMAL"))
    }

    private fun performKeyFeedback(view: View) {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("keyboard_vibrate_enabled", true)) {
            try {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            } catch (_: Exception) {
            }
        }
        if (prefs.getBoolean("keyboard_sound_enabled", false)) {
            try {
                audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
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

    private fun createRoundedBackground(fillColor: Int, strokeColor: Int, radiusDp: Int = 11): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
        }
    }

    private fun buildKeyboardLayout(): View {
        letterKeyViews.clear()
        val isDark = isDarkModeActive()
        val preset = getKeyboardHeightPreset()
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        val autoCaptureOn = prefs.getBoolean("auto_capture_enabled", true)

        // Premium high-contrast Dark & Light keyboard surfaces
        val boardBg = if (isDark) Color.parseColor("#0B1120") else Color.parseColor("#E2E8F0")
        val keyBg = if (isDark) Color.parseColor("#1E293B") else Color.parseColor("#FFFFFF")
        val keyPressedBg = if (isDark) Color.parseColor("#334155") else Color.parseColor("#CBD5E1")
        val keyBorder = if (isDark) Color.parseColor("#2A3A56") else Color.parseColor("#CBD5E1")
        val actionKeyBg = if (isDark) Color.parseColor("#151F34") else Color.parseColor("#CBD5E1")
        val primaryAccent = if (isDark) Color.parseColor("#6366F1") else Color.parseColor("#4F46E5")
        val emeraldAccent = Color.parseColor("#10B981")
        val textPrimary = if (isDark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        val textSecondary = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(boardBg)
            setPadding(dp(5), dp(6), dp(5), dp(12))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Compact Top Status Bar (13-14sp)
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(6))
        }

        val statusDot = TextView(this).apply {
            text = if (autoCaptureOn) "● " else "○ "
            setTextColor(if (autoCaptureOn) emeraldAccent else textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val statusLabel = TextView(this).apply {
            text = if (autoCaptureOn) {
                "ClipVault Keyboard Active · EN"
            } else {
                "ClipVault Keyboard · Capture Paused"
            }
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        statusBadgeView = statusLabel

        val switchKeyboardBtn = TextView(this).apply {
            text = "⌨ Switch"
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(11), dp(5), dp(11), dp(5))
            background = createRoundedBackground(actionKeyBg, keyBorder, radiusDp = 9)
            setOnClickListener {
                performKeyFeedback(this)
                switchInputMethodSafely()
            }
        }

        toolbar.addView(statusDot)
        toolbar.addView(statusLabel)
        toolbar.addView(switchKeyboardBtn)
        root.addView(toolbar)

        // Row 1: Q W E R T Y U I O P (10 keys)
        val row1Keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        root.addView(
            buildCharacterRow(
                keys = row1Keys,
                preset = preset,
                keyBg = keyBg,
                keyPressedBg = keyPressedBg,
                keyBorder = keyBorder,
                textColor = textPrimary,
                horizontalInsetDp = 0
            )
        )

        // Row 2: A S D F G H J K L (9 keys with balanced thumb inset)
        val row2Keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        root.addView(
            buildCharacterRow(
                keys = row2Keys,
                preset = preset,
                keyBg = keyBg,
                keyPressedBg = keyPressedBg,
                keyBorder = keyBorder,
                textColor = textPrimary,
                horizontalInsetDp = 16
            )
        )

        // Row 3: Shift / #+= + Z X C V B N M + Backspace
        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }

        val shiftBtn = createKeyView(
            label = resolveLeftModifierLabel(),
            weight = 1.5f,
            heightDp = preset.keyHeightDp,
            textSizeSp = preset.functionSp,
            bgColor = if (shiftState != ShiftState.LOWERCASE && symbolPage == SymbolPage.LETTERS) primaryAccent else actionKeyBg,
            pressedBgColor = keyPressedBg,
            borderColor = keyBorder,
            textColor = if (shiftState != ShiftState.LOWERCASE && symbolPage == SymbolPage.LETTERS) Color.WHITE else textPrimary
        ) {
            onLeftModifierTapped(primaryAccent, actionKeyBg, keyBorder, textPrimary, preset)
        }
        shiftKeyView = shiftBtn
        row3.addView(shiftBtn)

        val row3Keys = listOf("z", "x", "c", "v", "b", "n", "m")
        row3Keys.forEachIndexed { idx, ch ->
            val keyView = createKeyView(
                label = ch,
                weight = 1f,
                heightDp = preset.keyHeightDp,
                textSizeSp = preset.letterSp,
                bgColor = keyBg,
                pressedBgColor = keyPressedBg,
                borderColor = keyBorder,
                textColor = textPrimary
            ) {
                commitKeyCharacter(row3Keys[idx], idx + 19, preset, primaryAccent, actionKeyBg, keyBorder, textPrimary)
            }
            letterKeyViews.add(keyView)
            row3.addView(keyView)
        }

        val backspaceBtn = createRepeatingBackspaceKey(
            weight = 1.5f,
            heightDp = preset.keyHeightDp,
            textSizeSp = preset.functionSp,
            bgColor = actionKeyBg,
            pressedBgColor = keyPressedBg,
            borderColor = keyBorder,
            textColor = textPrimary
        )
        row3.addView(backspaceBtn)
        root.addView(row3)

        // Row 4: 123/ABC + Globe + Wide Spacebar + . + Enter
        val row4 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(2))
        }

        val symBtn = createKeyView(
            label = if (symbolPage == SymbolPage.LETTERS) "123" else "ABC",
            weight = 1.35f,
            heightDp = preset.keyHeightDp,
            textSizeSp = preset.functionSp - 1f,
            bgColor = actionKeyBg,
            pressedBgColor = keyPressedBg,
            borderColor = keyBorder,
            textColor = textPrimary
        ) {
            symbolPage = toggleSymbolMode(symbolPage)
            refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, preset)
        }
        modeKeyView = symBtn
        row4.addView(symBtn)

        val globeBtn = createKeyView(
            label = "🌐",
            weight = 1.05f,
            heightDp = preset.keyHeightDp,
            textSizeSp = preset.functionSp,
            bgColor = actionKeyBg,
            pressedBgColor = keyPressedBg,
            borderColor = keyBorder,
            textColor = textPrimary
        ) {
            switchInputMethodSafely()
        }
        row4.addView(globeBtn)

        val spaceBtn = createKeyView(
            label = "English (US)",
            weight = 4.3f,
            heightDp = preset.keyHeightDp,
            textSizeSp = 16.5f,
            bgColor = keyBg,
            pressedBgColor = keyPressedBg,
            borderColor = keyBorder,
            textColor = textPrimary
        ) {
            currentInputConnection?.commitText(" ", 1)
        }
        row4.addView(spaceBtn)

        val dotBtn = createKeyView(
            label = ".",
            weight = 1.0f,
            heightDp = preset.keyHeightDp,
            textSizeSp = preset.letterSp,
            bgColor = keyBg,
            pressedBgColor = keyPressedBg,
            borderColor = keyBorder,
            textColor = textPrimary
        ) {
            currentInputConnection?.commitText(".", 1)
        }
        row4.addView(dotBtn)

        val enterBtn = createKeyView(
            label = "↵",
            weight = 1.65f,
            heightDp = preset.keyHeightDp,
            textSizeSp = preset.functionSp + 1f,
            bgColor = primaryAccent,
            pressedBgColor = primaryAccent,
            borderColor = primaryAccent,
            textColor = Color.WHITE
        ) {
            handleEnterAction()
        }
        row4.addView(enterBtn)
        root.addView(row4)

        refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, preset)
        return root
    }

    private fun buildCharacterRow(
        keys: List<String>,
        preset: KeyboardHeightPreset,
        keyBg: Int,
        keyPressedBg: Int,
        keyBorder: Int,
        textColor: Int,
        horizontalInsetDp: Int
    ): LinearLayout {
        val isDark = isDarkModeActive()
        val primaryAccent = if (isDark) Color.parseColor("#6366F1") else Color.parseColor("#4F46E5")
        val actionKeyBg = if (isDark) Color.parseColor("#151F34") else Color.parseColor("#CBD5E1")
        val baseOffset = letterKeyViews.size
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(horizontalInsetDp), dp(4), dp(horizontalInsetDp), dp(4))
        }
        keys.forEachIndexed { index, rawKey ->
            val keyView = createKeyView(
                label = rawKey,
                weight = 1f,
                heightDp = preset.keyHeightDp,
                textSizeSp = preset.letterSp,
                bgColor = keyBg,
                pressedBgColor = keyPressedBg,
                borderColor = keyBorder,
                textColor = textColor
            ) {
                commitKeyCharacter(rawKey, baseOffset + index, preset, primaryAccent, actionKeyBg, keyBorder, textColor)
            }
            letterKeyViews.add(keyView)
            row.addView(keyView)
        }
        return row
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createKeyView(
        label: String,
        weight: Float,
        heightDp: Int,
        textSizeSp: Float,
        bgColor: Int,
        pressedBgColor: Int,
        borderColor: Int,
        textColor: Int,
        onClick: () -> Unit
    ): TextView {
        val normalDrawable = createRoundedBackground(bgColor, borderColor, radiusDp = 11)
        val pressedDrawable = createRoundedBackground(pressedBgColor, borderColor, radiusDp = 11)

        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            background = normalDrawable
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.scaleX = 0.96f
                        v.scaleY = 0.96f
                        v.background = pressedDrawable
                        performKeyFeedback(v)
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
        val normalDrawable = createRoundedBackground(bgColor, borderColor, radiusDp = 11)
        val pressedDrawable = createRoundedBackground(pressedBgColor, borderColor, radiusDp = 11)

        return TextView(this).apply {
            text = "⌫"
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            background = normalDrawable
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
                setMargins(dp(3), 0, dp(3), 0)
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
                        mainHandler.postDelayed(backspaceRepeatRunnable, 360L)
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
            SymbolPage.NUMBERS_SYMBOLS_1 -> "#+="
            SymbolPage.SYMBOLS_2 -> "123"
        }
    }

    private fun onLeftModifierTapped(
        primaryAccent: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
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
        refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, preset)
    }

    private fun commitKeyCharacter(
        baseLetter: String,
        index: Int,
        preset: KeyboardHeightPreset,
        primaryAccent: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int
    ) {
        val ic = currentInputConnection ?: return
        val output = resolveKeyOutput(baseLetter, index, shiftState, symbolPage)
        ic.commitText(output, 1)

        if (symbolPage == SymbolPage.LETTERS && shiftState == ShiftState.SHIFTED_SINGLE) {
            shiftState = ShiftState.LOWERCASE
            refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary, preset)
        }
    }

    private fun refreshKeyLabels(
        primaryAccent: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int,
        preset: KeyboardHeightPreset
    ) {
        letterKeyViews.forEachIndexed { idx, tv ->
            val baseChar = BASE_QWERTY_KEYS.getOrElse(idx) { "" }
            tv.text = resolveKeyOutput(baseChar, idx, shiftState, symbolPage)
            val targetSp = when {
                symbolPage != SymbolPage.LETTERS && idx < 10 -> preset.numberSp
                symbolPage != SymbolPage.LETTERS -> preset.numberSp - 0.5f
                else -> preset.letterSp
            }
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, targetSp)
        }

        val isShiftHighlighted = symbolPage == SymbolPage.LETTERS && shiftState != ShiftState.LOWERCASE
        shiftKeyView?.apply {
            text = resolveLeftModifierLabel()
            background = createRoundedBackground(
                if (isShiftHighlighted) primaryAccent else actionKeyBg,
                keyBorder,
                radiusDp = 11
            )
            setTextColor(if (isShiftHighlighted) Color.WHITE else textPrimary)
        }
        modeKeyView?.text = if (symbolPage == SymbolPage.LETTERS) "123" else "ABC"
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

        private val SYMBOL_KEYS_PAGE_1 = listOf(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
            "@", "#", "$", "%", "&", "-", "+", "(", ")",
            "*", "\"", "'", ":", ";", "!", "?"
        )

        private val SYMBOL_KEYS_PAGE_2 = listOf(
            "~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆",
            "£", "€", "¥", "^", "°", "=", "{", "}", "\\",
            "%", "©", "®", "™", "✓", "[", "]"
        )

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
