package com.clipvault.android.keyboard.transliteration

/**
 * Primary keyboard language modes for ClipVault Bilingual Keyboard (v1.7.1).
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
 * Legible Bengali Unicode Font rendering preset (Feature 15 & Fix 7).
 */
enum class BengaliFontStyle(
    val prefKey: String,
    val displayName: String,
    val fontFamilyName: String,
    val extraVerticalPaddingDp: Int,
    val lineSpacingMultiplier: Float
) {
    SYSTEM_DEFAULT("SYSTEM_DEFAULT", "System Unicode Bangla", "sans-serif", 4, 1.18f),
    SANS_SERIF_CLEAN("SANS_SERIF_CLEAN", "Clean Sans Bangla", "sans-serif-medium", 5, 1.20f),
    SERIF_READABLE("SERIF_READABLE", "Classic Readable Bangla", "serif", 5, 1.22f);

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
 * Validates that Bengali Unicode strings containing কার (vowel signs), ফলা (conjunct glides),
 * যুক্তাক্ষর (conjunct consonants), হসন্ত (virama), Bengali numerals (০-৯), and Dari (।)
 * are well-formed UTF-16 sequences ready for HarfBuzz shaping with elegantTextHeight enabled.
 */
object BengaliUnicodeRenderValidator {
    private val VOWEL_SIGNS = setOf('া', 'ি', 'ী', 'ু', 'ূ', 'ৃ', 'ে', 'ৈ', 'ো', 'ৌ')
    private const val HASANTA = '্'
    private val BENGALI_DIGITS = ('০'..'৯').toSet()

    fun containsKar(text: String): Boolean = text.any { it in VOWEL_SIGNS }
    fun containsHasantaOrJuktakkhor(text: String): Boolean = text.contains(HASANTA)
    fun containsPhala(text: String): Boolean = text.contains("্য") || text.contains("্র") || text.contains("্ব") || text.contains("্ম") || text.contains("্ল")
    fun containsBengaliNumerals(text: String): Boolean = text.any { it in BENGALI_DIGITS }
    fun containsBengaliPunctuation(text: String): Boolean = text.contains('।')

    fun verifyOrthographicIntegrity(text: String): Boolean {
        if (text.isEmpty()) return false
        // Ensure no orphaned vowel sign or virama at index 0
        val first = text.first()
        if (first in VOWEL_SIGNS || first == HASANTA) return false
        return true
    }
}

/**
 * Suggestion Accuracy Ranking Hierarchy (Feature 14 & Fix 3/6):
 * 1. Exact personal dictionary match
 * 2. Multi-word / single-word custom shortcut match
 * 3. Mixed-language sentence candidate (when typing mixed Banglish + English clauses)
 * 4. Context-aware next-word prediction / bigram match
 * 5. Typo / spelling correction & exact lexicon match
 * 6. Phonetic transliteration rule match & variants
 * 7. Prefix completion, bilingual semantic equivalents & literal typed input
 */
enum class CandidateSource(val basePriority: Int) {
    PERSONAL_DICTIONARY(1000),
    CUSTOM_SHORTCUT(950),
    MIXED_SENTENCE(910),
    CONTEXTUAL_BIGRAM(860),
    TYPO_CORRECTION(830),
    LEXICON_EXACT(800),
    PHONETIC_RULE_PRIMARY(720),
    PHONETIC_VARIANT(650),
    LEXICON_PREFIX(580),
    BILINGUAL_EQUIVALENT(540),
    ENGLISH_LITERAL(500),
    NEXT_WORD_PREDICTION(450)
}

/**
 * Represents a ranked transliteration, word prediction, or mixed-sentence candidate shown in the suggestion bar.
 * When [source] == [CandidateSource.MIXED_SENTENCE], [replacePrecedingSentenceChars] specifies the
 * character length of the already-committed prefix in the current sentence so selecting the chip
 * replaces the entire clause cleanly while preserving surrounding punctuation.
 */
data class BengaliCandidate(
    val text: String,
    val rawRoman: String,
    val score: Int,
    val source: CandidateSource,
    val isPrimary: Boolean = false,
    val candidateLanguage: CandidateLanguage = CandidateLanguage.BENGALI,
    val replacePrecedingSentenceChars: Int = 0
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
 * Identifies an active editor session reliably across apps and fields (Fix 4).
 * Treats fieldId == 0 as a valid editor field ID and distinguishes sessions by packageName,
 * fieldId, inputType, and sessionCounter.
 */
data class EditorSessionIdentity(
    val packageName: String,
    val fieldId: Int,
    val inputType: Int,
    val sessionId: Long
)

/**
 * Safe Keyboard-Managed Undo/Redo Stack (Fix 4).
 * - Treats field ID 0 as a valid editor field ID.
 * - Uses [EditorSessionIdentity] (packageName + fieldId + inputType + sessionId) to isolate sessions
 *   across different apps and fields even when two fields both report fieldId == 0.
 * - Handles cursor movement, selection changes, and selection replacements safely.
 * - Refuses to overwrite external editor changes or unsupported active selections.
 * - Respects password and sensitive input restrictions and bounds history to [maxHistorySize].
 */
class KeyboardUndoRedoManager(private val maxHistorySize: Int = 30) {

    data class EditOperation(
        val beforeText: String,
        val afterText: String,
        val cursorBefore: Int = beforeText.length,
        val cursorAfter: Int = afterText.length,
        val selectionStartBefore: Int = cursorBefore,
        val selectionEndBefore: Int = cursorBefore,
        val replacedSelection: Boolean = false,
        val editorFieldId: Int = 0,
        val packageName: String = "",
        val sessionId: Long = 0L,
        val description: String = "Keyboard Edit",
        val timestamp: Long = System.currentTimeMillis()
    )

    private val undoStack = ArrayDeque<EditOperation>()
    private val redoStack = ArrayDeque<EditOperation>()
    private var activeSession: EditorSessionIdentity? = null
    private var nextSessionCounter: Long = 1L
    private var hasActiveNonEmptySelection: Boolean = false
    private var lastKnownCursorPosition: Int = -1

    var isSensitiveOrPasswordField: Boolean = false
        private set

    /**
     * Notifies the manager that an editor session started or restarted.
     * Note: [newFieldId] == 0 is treated as a valid editor field ID. Whenever [restarting] is false
     * (or when packageName, fieldId, or inputType changes), a new session is started and history is cleared.
     */
    @Synchronized
    fun onEditorContextChanged(
        newFieldId: Int,
        isPasswordOrSensitive: Boolean,
        packageName: String = "",
        inputType: Int = 0,
        restarting: Boolean = false
    ) {
        val current = activeSession
        val isGenuinelyNewSession = current == null ||
            !restarting ||
            current.packageName != packageName ||
            current.fieldId != newFieldId ||
            current.inputType != inputType

        isSensitiveOrPasswordField = isPasswordOrSensitive
        hasActiveNonEmptySelection = false

        if (isGenuinelyNewSession || isPasswordOrSensitive) {
            val assignedSessionId = nextSessionCounter++
            activeSession = EditorSessionIdentity(
                packageName = packageName,
                fieldId = newFieldId,
                inputType = inputType,
                sessionId = assignedSessionId
            )
            undoStack.clear()
            redoStack.clear()
            lastKnownCursorPosition = -1
        }
    }

    /**
     * Tracks cursor and selection changes from InputMethodService.onUpdateSelection.
     */
    @Synchronized
    fun onSelectionChanged(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        isInternalEdit: Boolean = false
    ) {
        if (newSelStart >= 0 && newSelEnd >= 0) {
            hasActiveNonEmptySelection = newSelStart != newSelEnd
            lastKnownCursorPosition = newSelEnd
        } else {
            hasActiveNonEmptySelection = false
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

        val session = activeSession ?: EditorSessionIdentity("", 0, 0, nextSessionCounter++).also {
            activeSession = it
        }

        val op = EditOperation(
            beforeText = beforeText,
            afterText = afterText,
            cursorBefore = cursorBefore.coerceAtLeast(0),
            cursorAfter = cursorAfter.coerceAtLeast(0),
            selectionStartBefore = cursorBefore.coerceAtLeast(0),
            selectionEndBefore = cursorBefore.coerceAtLeast(0),
            replacedSelection = false,
            editorFieldId = session.fieldId,
            packageName = session.packageName,
            sessionId = session.sessionId,
            description = description
        )
        undoStack.addLast(op)
        while (undoStack.size > maxHistorySize) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        hasActiveNonEmptySelection = false
        lastKnownCursorPosition = op.cursorAfter
    }

    /**
     * Safely records a keyboard edit that replaced a selected text span [selectedText] after [beforePrefix].
     */
    @Synchronized
    fun recordSelectionReplacement(
        beforePrefix: String,
        selectedText: String,
        replacementText: String,
        description: String = "Replace Selection"
    ) {
        if (isSensitiveOrPasswordField) return
        if (selectedText.isEmpty() && replacementText.isEmpty()) return
        val fullBefore = beforePrefix + selectedText
        val fullAfter = beforePrefix + replacementText
        if (fullBefore == fullAfter) return

        val session = activeSession ?: EditorSessionIdentity("", 0, 0, nextSessionCounter++).also {
            activeSession = it
        }

        val op = EditOperation(
            beforeText = fullBefore,
            afterText = fullAfter,
            cursorBefore = fullBefore.length,
            cursorAfter = fullAfter.length,
            selectionStartBefore = beforePrefix.length,
            selectionEndBefore = fullBefore.length,
            replacedSelection = true,
            editorFieldId = session.fieldId,
            packageName = session.packageName,
            sessionId = session.sessionId,
            description = description
        )
        undoStack.addLast(op)
        while (undoStack.size > maxHistorySize) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        hasActiveNonEmptySelection = false
        lastKnownCursorPosition = op.cursorAfter
    }

    @Synchronized
    fun canUndo(): Boolean = !isSensitiveOrPasswordField && !hasActiveNonEmptySelection && undoStack.isNotEmpty()

    @Synchronized
    fun canRedo(): Boolean = !isSensitiveOrPasswordField && !hasActiveNonEmptySelection && redoStack.isNotEmpty()

    /**
     * Pops the latest keyboard edit if [currentTextBeforeCursor] is compatible with the recorded
     * post-edit snapshot and there is no unsafe active text selection, pushes it to the redo stack,
     * and returns the operation to restore.
     */
    @Synchronized
    fun undo(currentTextBeforeCursor: String? = null, hasActiveSelection: Boolean = hasActiveNonEmptySelection): EditOperation? {
        if (isSensitiveOrPasswordField || hasActiveSelection || undoStack.isEmpty()) return null
        val top = undoStack.last()

        val session = activeSession
        if (session != null && top.sessionId != session.sessionId) {
            undoStack.clear()
            redoStack.clear()
            return null
        }

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
     * Re-applies the most recently undone keyboard edit if [currentTextBeforeCursor] matches
     * and there is no unsafe active selection.
     */
    @Synchronized
    fun redo(currentTextBeforeCursor: String? = null, hasActiveSelection: Boolean = hasActiveNonEmptySelection): EditOperation? {
        if (isSensitiveOrPasswordField || hasActiveSelection || redoStack.isEmpty()) return null
        val top = redoStack.last()

        val session = activeSession
        if (session != null && top.sessionId != session.sessionId) {
            redoStack.clear()
            return null
        }

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
        hasActiveNonEmptySelection = false
    }

    @Synchronized
    fun getActiveSession(): EditorSessionIdentity? = activeSession

    @Synchronized
    fun getUndoDepth(): Int = undoStack.size

    @Synchronized
    fun getRedoDepth(): Int = redoStack.size
}
