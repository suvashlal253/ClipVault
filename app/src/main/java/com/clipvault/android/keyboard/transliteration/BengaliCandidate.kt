package com.clipvault.android.keyboard.transliteration

/**
 * Primary keyboard language modes for ClipVault Bilingual Keyboard (v1.7.0).
 */
enum class KeyboardLanguageMode(
    val prefCode: String,
    val displayName: String,
    val spacebarLabel: String,
    val badgeText: String
) {
    ENGLISH("en", "English (QWERTY)", "English", "EN"),
    BANGLA_PHONETIC("bn_phonetic", "বাংলা (Bangla Phonetic)", "বাংলা", "বাং");

    fun toggle(): KeyboardLanguageMode = when (this) {
        ENGLISH -> BANGLA_PHONETIC
        BANGLA_PHONETIC -> ENGLISH
    }

    companion object {
        fun fromPrefCode(code: String?): KeyboardLanguageMode {
            return entries.firstOrNull { it.prefCode.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

/**
 * Script/language classification of a suggestion candidate for bilingual suggestion chips.
 */
enum class CandidateLanguage {
    BENGALI,
    ENGLISH,
    MIXED
}

/**
 * Offline token language classification for Mixed Language Typing & Smart Language Detection.
 */
enum class TokenLanguageClassification {
    PURE_BENGALI_SCRIPT,
    BANGLISH_WORD,
    AMBIGUOUS_BILINGUAL,
    ENGLISH_WORD,
    URL_OR_EMAIL,
    NUMBER_OR_CODE
}

/**
 * Non-intrusive language switch suggestion prompt shown when the user appears to be typing
 * in the other language mode (never forcibly switches layout without user tap).
 */
data class LanguageSwitchPrompt(
    val suggestedMode: KeyboardLanguageMode,
    val chipLabel: String,
    val detectedReason: String
)

/**
 * One-Handed Mode docking alignment (Feature 20).
 */
enum class OneHandedMode(val prefKey: String, val displayName: String) {
    OFF("OFF", "Full Width"),
    LEFT("LEFT", "Left Handed"),
    RIGHT("RIGHT", "Right Handed");

    fun next(): OneHandedMode = when (this) {
        OFF -> LEFT
        LEFT -> RIGHT
        RIGHT -> OFF
    }

    companion object {
        fun fromPrefKey(key: String?): OneHandedMode {
            return entries.firstOrNull { it.prefKey.equals(key, ignoreCase = true) } ?: OFF
        }
    }
}

/**
 * Key Size & Touch Target preset (Feature 16).
 */
enum class KeySizePreset(
    val prefKey: String,
    val displayName: String,
    val heightScale: Float,
    val textSizeScale: Float,
    val horizontalGapDp: Int
) {
    SMALL("SMALL", "Small", 0.90f, 0.92f, 4),
    MEDIUM("MEDIUM", "Medium", 1.00f, 1.00f, 4),
    LARGE("LARGE", "Large", 1.12f, 1.10f, 5);

    fun next(): KeySizePreset = when (this) {
        SMALL -> MEDIUM
        MEDIUM -> LARGE
        LARGE -> SMALL
    }

    companion object {
        fun fromPrefKey(key: String?): KeySizePreset {
            return entries.firstOrNull { it.prefKey.equals(key, ignoreCase = true) } ?: MEDIUM
        }
    }
}

/**
 * Legible Bengali Unicode Font rendering preset (Feature 15).
 */
enum class BengaliFontStyle(
    val prefKey: String,
    val displayName: String,
    val extraVerticalPaddingDp: Int,
    val lineSpacingMultiplier: Float
) {
    SYSTEM_DEFAULT("SYSTEM_DEFAULT", "System Unicode Bangla", 3, 1.15f),
    SANS_SERIF_CLEAN("SANS_SERIF_CLEAN", "Clean Sans Bangla", 4, 1.18f),
    SERIF_READABLE("SERIF_READABLE", "Classic Readable Bangla", 4, 1.20f);

    fun next(): BengaliFontStyle = when (this) {
        SYSTEM_DEFAULT -> SANS_SERIF_CLEAN
        SANS_SERIF_CLEAN -> SERIF_READABLE
        SERIF_READABLE -> SYSTEM_DEFAULT
    }

    companion object {
        fun fromPrefKey(key: String?): BengaliFontStyle {
            return entries.firstOrNull { it.prefKey.equals(key, ignoreCase = true) } ?: SYSTEM_DEFAULT
        }
    }
}

/**
 * 6-Tier Suggestion Accuracy Ranking Hierarchy (Feature 14):
 * 1. Exact personal dictionary match
 * 2. Multi-word / single-word custom shortcut match
 * 3. Context-aware next-word prediction / bigram match
 * 4. High-frequency Bengali or English lexicon match
 * 5. Phonetic transliteration rule match & variants
 * 6. Prefix completion & bilingual candidates
 */
enum class CandidateSource(val basePriority: Int) {
    PERSONAL_DICTIONARY(1000),
    CUSTOM_SHORTCUT(950),
    CONTEXTUAL_BIGRAM(860),
    LEXICON_EXACT(800),
    PHONETIC_RULE_PRIMARY(720),
    PHONETIC_VARIANT(650),
    LEXICON_PREFIX(580),
    BILINGUAL_EQUIVALENT(540),
    ENGLISH_LITERAL(500),
    NEXT_WORD_PREDICTION(450)
}

/**
 * Represents a ranked transliteration or word prediction candidate shown in the suggestion bar.
 */
data class BengaliCandidate(
    val text: String,
    val rawRoman: String,
    val score: Int,
    val source: CandidateSource,
    val isPrimary: Boolean = false,
    val candidateLanguage: CandidateLanguage = CandidateLanguage.BENGALI
)

/**
 * Represents a user-saved personal dictionary entry (e.g., "suvash" -> "সুভাষ").
 */
data class PersonalDictionaryEntry(
    val romanKey: String,
    val preferredWord: String,
    val languageCode: String = KeyboardLanguageMode.BANGLA_PHONETIC.prefCode,
    val frequency: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Represents a user-defined text shortcut or phrase expansion (e.g., "brb" -> "be right back",
 * "amar nam" -> "আমার নাম সুভাষ").
 */
data class CustomShortcutEntry(
    val trigger: String,
    val expansion: String
)

/**
 * Safe Keyboard-Managed Undo/Redo Stack (Part 2 #5 & Feature 3).
 * Tracks recent keyboard-initiated text edits in a bounded in-memory stack, verifies editor
 * context before applying undo/redo, clears history on editor field changes, and stays disabled
 * in password or sensitive fields.
 */
class KeyboardUndoRedoManager(private val maxHistorySize: Int = 30) {

    data class EditOperation(
        val beforeText: String,
        val afterText: String,
        val cursorBefore: Int = beforeText.length,
        val cursorAfter: Int = afterText.length,
        val editorFieldId: Int = 0,
        val description: String = "Keyboard Edit",
        val timestamp: Long = System.currentTimeMillis()
    )

    private val undoStack = ArrayDeque<EditOperation>()
    private val redoStack = ArrayDeque<EditOperation>()
    private var activeEditorFieldId: Int = 0
    var isSensitiveOrPasswordField: Boolean = false
        private set

    @Synchronized
    fun onEditorContextChanged(newFieldId: Int, isPasswordOrSensitive: Boolean) {
        val fieldChanged = activeEditorFieldId != 0 && activeEditorFieldId != newFieldId
        activeEditorFieldId = newFieldId
        isSensitiveOrPasswordField = isPasswordOrSensitive
        if (fieldChanged || isPasswordOrSensitive) {
            undoStack.clear()
            redoStack.clear()
        }
    }

    @Synchronized
    fun recordEdit(
        beforeText: String,
        afterText: String,
        cursorBefore: Int = beforeText.length,
        cursorAfter: Int = afterText.length,
        description: String = "Keyboard Edit"
    ) {
        if (isSensitiveOrPasswordField) return
        if (beforeText == afterText) return

        val op = EditOperation(
            beforeText = beforeText,
            afterText = afterText,
            cursorBefore = cursorBefore.coerceAtLeast(0),
            cursorAfter = cursorAfter.coerceAtLeast(0),
            editorFieldId = activeEditorFieldId,
            description = description
        )
        undoStack.addLast(op)
        while (undoStack.size > maxHistorySize) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    @Synchronized
    fun canUndo(): Boolean = !isSensitiveOrPasswordField && undoStack.isNotEmpty()

    @Synchronized
    fun canRedo(): Boolean = !isSensitiveOrPasswordField && redoStack.isNotEmpty()

    /**
     * Pops the latest keyboard edit if [currentTextBeforeCursor] is compatible with the recorded
     * post-edit snapshot, pushes it to the redo stack, and returns the operation to restore.
     */
    @Synchronized
    fun undo(currentTextBeforeCursor: String? = null): EditOperation? {
        if (isSensitiveOrPasswordField || undoStack.isEmpty()) return null
        val top = undoStack.last()

        // Verify editor state compatibility when a snapshot is provided
        if (currentTextBeforeCursor != null &&
            currentTextBeforeCursor != top.afterText &&
            !currentTextBeforeCursor.endsWith(top.afterText)
        ) {
            // External edit diverged from keyboard history; clear stale history safely
            undoStack.clear()
            redoStack.clear()
            return null
        }

        val popped = undoStack.removeLast()
        redoStack.addLast(popped)
        while (redoStack.size > maxHistorySize) {
            redoStack.removeFirst()
        }
        return popped
    }

    /**
     * Re-applies the most recently undone keyboard edit if [currentTextBeforeCursor] matches.
     */
    @Synchronized
    fun redo(currentTextBeforeCursor: String? = null): EditOperation? {
        if (isSensitiveOrPasswordField || redoStack.isEmpty()) return null
        val top = redoStack.last()

        if (currentTextBeforeCursor != null &&
            currentTextBeforeCursor != top.beforeText &&
            !currentTextBeforeCursor.endsWith(top.beforeText)
        ) {
            redoStack.clear()
            return null
        }

        val popped = redoStack.removeLast()
        undoStack.addLast(popped)
        return popped
    }

    @Synchronized
    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    @Synchronized
    fun getUndoDepth(): Int = undoStack.size

    @Synchronized
    fun getRedoDepth(): Int = redoStack.size
}
