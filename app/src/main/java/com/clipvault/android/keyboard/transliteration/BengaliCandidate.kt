package com.clipvault.android.keyboard.transliteration

/**
 * Primary keyboard language modes for ClipVault Bilingual Keyboard (v1.6.0).
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
 * Origin of a suggestion candidate for transparent multi-factor ranking.
 */
enum class CandidateSource(val basePriority: Int) {
    PERSONAL_DICTIONARY(1000),
    CUSTOM_SHORTCUT(950),
    CONTEXTUAL_BIGRAM(860),
    LEXICON_EXACT(800),
    PHONETIC_RULE_PRIMARY(720),
    PHONETIC_VARIANT(650),
    LEXICON_PREFIX(580),
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
    val isPrimary: Boolean = false
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
