package com.clipvault.android.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.PersistableBundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import com.clipvault.android.service.ClipboardServiceHelper

/**
 * Optional ClipVault Keyboard (IME).
 *
 * When enabled and active, registers a lifecycle-bound [ClipboardManager.OnPrimaryClipChangedListener]
 * to capture clipboard changes when Android permits IME clipboard access, routing captured clips
 * through the centralized [ClipboardServiceHelper] queue.
 */
class ClipVaultInputMethodService : InputMethodService() {

    private var clipboardManager: ClipboardManager? = null
    private var isListenerRegistered = false
    private var isShifted = false
    private var isSymbolsMode = false
    private val letterKeyViews = mutableListOf<TextView>()
    private var shiftKeyView: TextView? = null
    private var modeKeyView: TextView? = null

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        capturePermittedClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        isActiveInstance = true
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
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
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        isInputViewVisibleState = false
    }

    override fun onDestroy() {
        unregisterClipboardListener()
        isInputViewVisibleState = false
        isActiveInstance = false
        letterKeyViews.clear()
        super.onDestroy()
    }

    private fun registerClipboardListenerIfNeeded() {
        val manager = clipboardManager ?: return
        val shouldRegister = ListenerRegistrationController.markRegistered(isListenerRegistered)
        if (shouldRegister) {
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
     * Never logs clipboard content and never inserts clipboard text into the user's active input field.
     */
    private fun capturePermittedClipboard() {
        val prefs = getSharedPreferences("clipvault_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_capture_enabled", true)) return

        // Never capture while typing in a password or sensitive field
        val editorInfo = currentInputEditorInfo
        if (editorInfo != null && isInputFieldPassword(editorInfo.inputType)) {
            return
        }

        try {
            val manager = clipboardManager ?: return
            if (!manager.hasPrimaryClip()) return
            val clipData: ClipData = manager.primaryClip ?: return

            // Respect Android 13+ EXTRA_IS_SENSITIVE flag on ClipDescription
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
            // Android restricted clipboard access in current state; fail safely without logging content
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

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun createRoundedBackground(fillColor: Int, strokeColor: Int, radiusDp: Int = 10): GradientDrawable {
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

        val boardBg = if (isDark) Color.parseColor("#0F1626") else Color.parseColor("#E2E8F0")
        val keyBg = if (isDark) Color.parseColor("#1E293B") else Color.parseColor("#FFFFFF")
        val keyBorder = if (isDark) Color.parseColor("#2A3A56") else Color.parseColor("#CBD5E1")
        val actionKeyBg = if (isDark) Color.parseColor("#162038") else Color.parseColor("#CBD5E1")
        val primaryAccent = if (isDark) Color.parseColor("#6366F1") else Color.parseColor("#4F46E5")
        val textPrimary = if (isDark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        val textSecondary = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(boardBg)
            setPadding(dp(6), dp(6), dp(6), dp(10))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Top Toolbar Row: ClipVault Badge + Switch Keyboard Button
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(6))
        }

        val statusLabel = TextView(this).apply {
            text = "ClipVault Keyboard · Auto-Capture Ready"
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val switchKeyboardBtn = TextView(this).apply {
            text = "Switch Keyboard"
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = createRoundedBackground(actionKeyBg, keyBorder, radiusDp = 8)
            setOnClickListener { switchInputMethodSafely() }
        }

        toolbar.addView(statusLabel)
        toolbar.addView(switchKeyboardBtn)
        root.addView(toolbar)

        // Row 1: q w e r t y u i o p
        val row1Keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        root.addView(buildCharacterRow(row1Keys, keyBg, keyBorder, textPrimary, horizontalInsetDp = 0))

        // Row 2: a s d f g h j k l
        val row2Keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        root.addView(buildCharacterRow(row2Keys, keyBg, keyBorder, textPrimary, horizontalInsetDp = 16))

        // Row 3: Shift + z x c v b n m + Backspace
        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(3))
        }

        val shiftBtn = createKeyView(
            label = "⇧",
            weight = 1.45f,
            bgColor = if (isShifted) primaryAccent else actionKeyBg,
            borderColor = keyBorder,
            textColor = if (isShifted) Color.WHITE else textPrimary
        ) {
            isShifted = !isShifted
            refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary)
        }
        shiftKeyView = shiftBtn
        row3.addView(shiftBtn)

        val row3Keys = listOf("z", "x", "c", "v", "b", "n", "m")
        row3Keys.forEachIndexed { idx, ch ->
            val keyView = createKeyView(
                label = ch,
                weight = 1f,
                bgColor = keyBg,
                borderColor = keyBorder,
                textColor = textPrimary
            ) {
                commitKeyCharacter(row3Keys[idx], idx + 19)
            }
            letterKeyViews.add(keyView)
            row3.addView(keyView)
        }

        val backspaceBtn = createKeyView(
            label = "⌫",
            weight = 1.45f,
            bgColor = actionKeyBg,
            borderColor = keyBorder,
            textColor = textPrimary
        ) {
            handleBackspace()
        }
        row3.addView(backspaceBtn)
        root.addView(row3)

        // Row 4: 123 + Globe Switch + Space + . + Enter
        val row4 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(2))
        }

        val symBtn = createKeyView(
            label = "123",
            weight = 1.3f,
            bgColor = actionKeyBg,
            borderColor = keyBorder,
            textColor = textPrimary
        ) {
            isSymbolsMode = !isSymbolsMode
            refreshKeyLabels(primaryAccent, actionKeyBg, keyBorder, textPrimary)
        }
        modeKeyView = symBtn
        row4.addView(symBtn)

        val globeBtn = createKeyView(
            label = "🌐",
            weight = 1.1f,
            bgColor = actionKeyBg,
            borderColor = keyBorder,
            textColor = textPrimary
        ) {
            switchInputMethodSafely()
        }
        row4.addView(globeBtn)

        val spaceBtn = createKeyView(
            label = "ClipVault Space",
            weight = 4.2f,
            bgColor = keyBg,
            borderColor = keyBorder,
            textColor = textPrimary
        ) {
            currentInputConnection?.commitText(" ", 1)
        }
        row4.addView(spaceBtn)

        val dotBtn = createKeyView(
            label = ".",
            weight = 1.0f,
            bgColor = keyBg,
            borderColor = keyBorder,
            textColor = textPrimary
        ) {
            currentInputConnection?.commitText(".", 1)
        }
        row4.addView(dotBtn)

        val enterBtn = createKeyView(
            label = "↵",
            weight = 1.6f,
            bgColor = primaryAccent,
            borderColor = primaryAccent,
            textColor = Color.WHITE
        ) {
            handleEnterAction()
        }
        row4.addView(enterBtn)
        root.addView(row4)

        return root
    }

    private fun buildCharacterRow(
        keys: List<String>,
        keyBg: Int,
        keyBorder: Int,
        textColor: Int,
        horizontalInsetDp: Int
    ): LinearLayout {
        val baseOffset = letterKeyViews.size
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(horizontalInsetDp), dp(3), dp(horizontalInsetDp), dp(3))
        }
        keys.forEachIndexed { index, rawKey ->
            val keyView = createKeyView(
                label = rawKey,
                weight = 1f,
                bgColor = keyBg,
                borderColor = keyBorder,
                textColor = textColor
            ) {
                commitKeyCharacter(rawKey, baseOffset + index)
            }
            letterKeyViews.add(keyView)
            row.addView(keyView)
        }
        return row
    }

    private fun createKeyView(
        label: String,
        weight: Float,
        bgColor: Int,
        borderColor: Int,
        textColor: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundedBackground(bgColor, borderColor, radiusDp = 9)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), weight).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun commitKeyCharacter(baseLetter: String, index: Int) {
        val ic = currentInputConnection ?: return
        val output = if (isSymbolsMode) {
            SYMBOL_KEYS.getOrElse(index) { baseLetter }
        } else if (isShifted) {
            baseLetter.uppercase()
        } else {
            baseLetter
        }
        ic.commitText(output, 1)
        if (isShifted && !isSymbolsMode) {
            isShifted = false
            val isDark = isDarkModeActive()
            refreshKeyLabels(
                primaryAccent = if (isDark) Color.parseColor("#6366F1") else Color.parseColor("#4F46E5"),
                actionKeyBg = if (isDark) Color.parseColor("#162038") else Color.parseColor("#CBD5E1"),
                keyBorder = if (isDark) Color.parseColor("#2A3A56") else Color.parseColor("#CBD5E1"),
                textPrimary = if (isDark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
            )
        }
    }

    private fun refreshKeyLabels(
        primaryAccent: Int,
        actionKeyBg: Int,
        keyBorder: Int,
        textPrimary: Int
    ) {
        letterKeyViews.forEachIndexed { idx, tv ->
            val baseChar = BASE_QWERTY_KEYS.getOrElse(idx) { "" }
            tv.text = when {
                isSymbolsMode -> SYMBOL_KEYS.getOrElse(idx) { baseChar }
                isShifted -> baseChar.uppercase()
                else -> baseChar
            }
        }
        shiftKeyView?.apply {
            background = createRoundedBackground(
                if (isShifted) primaryAccent else actionKeyBg,
                keyBorder,
                radiusDp = 9
            )
            setTextColor(if (isShifted) Color.WHITE else textPrimary)
        }
        modeKeyView?.text = if (isSymbolsMode) "ABC" else "123"
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

        private val BASE_QWERTY_KEYS = listOf(
            "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
            "a", "s", "d", "f", "g", "h", "j", "k", "l",
            "z", "x", "c", "v", "b", "n", "m"
        )

        private val SYMBOL_KEYS = listOf(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
            "@", "#", "$", "%", "&", "-", "+", "(", ")",
            "*", "\"", "'", ":", ";", "!", "?"
        )

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
