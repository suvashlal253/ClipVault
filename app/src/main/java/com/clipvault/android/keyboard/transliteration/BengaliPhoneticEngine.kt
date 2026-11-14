package com.clipvault.android.keyboard.transliteration

/**
 * Incremental Bengali Phonetic Transliteration Engine (v1.6.0).
 * Combines user personal dictionary preferences, offline Bengali lexicon, and
 * context-sensitive Avro/Phonetic rules.
 */
class BengaliPhoneticEngine(
    val userDictionary: BengaliUserDictionary = BengaliUserDictionary()
) {

    /**
     * Transliterates a single Banglish word into its highest-ranked Bengali output.
     */
    fun transliterateWord(
        romanWord: String,
        useBengaliNumerals: Boolean = false,
        preserveMixedEnglish: Boolean = false
    ): String {
        val trimmed = romanWord.trim()
        if (trimmed.isEmpty()) return ""

        // 1. Personal dictionary preference takes highest priority (e.g., "suvash" -> "সুভাষ")
        userDictionary.lookupPreferredWord(trimmed)?.let { personal ->
            return personal.preferredWord
        }

        // 2. Custom shortcut expansion
        userDictionary.lookupShortcut(trimmed)?.let { shortcut ->
            return shortcut.expansion
        }

        // 3. Mixed English loanword preservation when requested (e.g., "today" in "ami today office jabo")
        if (preserveMixedEnglish && trimmed.lowercase() == "today") {
            return trimmed
        }

        // 4. Offline Bengali lexicon exact match
        BengaliWordDictionary.lookupExactBangla(trimmed)?.let { entry ->
            if (entry.candidates.isNotEmpty()) {
                return entry.candidates.first()
            }
        }

        // 5. Algorithmic phonetic rule engine
        return BengaliTransliterationRules.transliterateWordPrimary(
            rawWord = trimmed,
            useBengaliNumerals = useBengaliNumerals
        )
    }

    /**
     * Transliterates a full Banglish phrase or sentence into Bengali script while preserving
     * whitespace, punctuation, and multi-word personal phrases.
     *
     * Examples:
     * - "ami" -> "আমি"
     * - "tumi" -> "তুমি"
     * - "amar nam suvash" -> "আমার নাম সুভাষ"
     * - "kemon acho" -> "কেমন আছো"
     * - "apni kemon achen" -> "আপনি কেমন আছেন"
     * - "bhalobashi" -> "ভালোবাসি"
     * - "bangladesh" -> "বাংলাদেশ"
     */
    fun transliterateSentence(
        banglishText: String,
        useBengaliNumerals: Boolean = false,
        preserveMixedEnglish: Boolean = false
    ): String {
        if (banglishText.isEmpty()) return ""

        // Split into alternating word and non-word separator tokens
        val tokenRegex = Regex("([A-Za-z0-9^:]+|[^A-Za-z0-9^:]+)")
        val rawTokens = tokenRegex.findAll(banglishText).map { it.value }.toList()
        val sb = StringBuilder()

        for (token in rawTokens) {
            if (token.first().isLetterOrDigit() || token.first() == '^' || token.first() == ':') {
                sb.append(
                    transliterateWord(
                        romanWord = token,
                        useBengaliNumerals = useBengaliNumerals,
                        preserveMixedEnglish = preserveMixedEnglish
                    )
                )
            } else {
                sb.append(token)
            }
        }
        return sb.toString()
    }

    /**
     * Generates ranked Bengali candidates for the currently composing Banglish word.
     */
    fun generateCandidatesForWord(
        composingRoman: String,
        previousCommittedWord: String = "",
        useBengaliNumerals: Boolean = false,
        maxCandidates: Int = 5
    ): List<BengaliCandidate> {
        val clean = composingRoman.trim()
        if (clean.isEmpty()) return emptyList()

        val candidatesByText = LinkedHashMap<String, BengaliCandidate>()

        fun addCandidate(text: String, score: Int, source: CandidateSource) {
            if (text.isBlank()) return
            val existing = candidatesByText[text]
            if (existing == null || score > existing.score) {
                candidatesByText[text] = BengaliCandidate(
                    text = text,
                    rawRoman = clean,
                    score = score,
                    source = source,
                    isPrimary = false
                )
            }
        }

        // 1. Check multi-word phrase shortcut with previousCommittedWord (e.g. "amar" + "nam" -> "আমার নাম সুভাষ")
        if (previousCommittedWord.isNotBlank()) {
            val prevRoman = if (previousCommittedWord == "আমার") "amar" else previousCommittedWord
            val phraseKey = prevRoman + " " + clean
            userDictionary.lookupShortcut(phraseKey)?.let { phraseShortcut ->
                val suffix = if (phraseShortcut.expansion.startsWith(previousCommittedWord + " ")) {
                    phraseShortcut.expansion.removePrefix(previousCommittedWord + " ")
                } else {
                    phraseShortcut.expansion
                }
                addCandidate(suffix, CandidateSource.CUSTOM_SHORTCUT.basePriority + 40, CandidateSource.CUSTOM_SHORTCUT)
            }
        }

        // 2. Personal Dictionary preference (highest single-word priority)
        userDictionary.lookupPreferredWord(clean)?.let { personal ->
            addCandidate(
                personal.preferredWord,
                CandidateSource.PERSONAL_DICTIONARY.basePriority + (personal.frequency * 5),
                CandidateSource.PERSONAL_DICTIONARY
            )
        }

        // 3. Single-word Custom Shortcut
        userDictionary.lookupShortcut(clean)?.let { shortcut ->
            addCandidate(
                shortcut.expansion,
                CandidateSource.CUSTOM_SHORTCUT.basePriority,
                CandidateSource.CUSTOM_SHORTCUT
            )
        }

        // 4. Contextual Bigram Boost from previousCommittedWord
        val contextualNext = if (previousCommittedWord.isNotBlank()) {
            BengaliWordDictionary.getNextWordPredictions(
                previousWord = previousCommittedWord,
                languageMode = KeyboardLanguageMode.BANGLA_PHONETIC
            ).toSet()
        } else {
            emptySet()
        }

        // 5. Offline Lexicon Exact Match
        BengaliWordDictionary.lookupExactBangla(clean)?.let { lexiconEntry ->
            lexiconEntry.candidates.forEachIndexed { idx, word ->
                val contextBonus = if (contextualNext.contains(word)) 80 else 0
                val rankOffset = (lexiconEntry.candidates.size - idx) * 12
                addCandidate(
                    text = word,
                    score = CandidateSource.LEXICON_EXACT.basePriority + lexiconEntry.baseFrequency + rankOffset + contextBonus,
                    source = if (contextBonus > 0) CandidateSource.CONTEXTUAL_BIGRAM else CandidateSource.LEXICON_EXACT
                )
            }
        }

        // 6. Algorithmic Phonetic Rule Engine & Orthographic Variants
        val ruleVariants = BengaliTransliterationRules.generatePhoneticVariants(
            rawWord = clean,
            useBengaliNumerals = useBengaliNumerals,
            maxVariants = 5
        )
        ruleVariants.forEachIndexed { idx, variant ->
            val contextBonus = if (contextualNext.contains(variant)) 70 else 0
            val source = if (idx == 0) CandidateSource.PHONETIC_RULE_PRIMARY else CandidateSource.PHONETIC_VARIANT
            addCandidate(
                text = variant,
                score = source.basePriority - (idx * 15) + contextBonus,
                source = source
            )
        }

        // 7. Prefix Completions from Offline Lexicon
        val prefixEntries = BengaliWordDictionary.lookupPrefixBangla(clean, limit = 3)
        prefixEntries.forEachIndexed { idx, entry ->
            val topWord = entry.candidates.firstOrNull() ?: return@forEachIndexed
            addCandidate(
                text = topWord,
                score = CandidateSource.LEXICON_PREFIX.basePriority + (entry.baseFrequency / 2) - (idx * 10),
                source = CandidateSource.LEXICON_PREFIX
            )
        }

        // 8. Include literal English word as last option for easy mixed Bengali-English typing
        val englishBonus = if (BengaliWordDictionary.isCommonEnglishLoanWord(clean)) 90 else 0
        addCandidate(
            text = clean,
            score = CandidateSource.ENGLISH_LITERAL.basePriority + englishBonus,
            source = CandidateSource.ENGLISH_LITERAL
        )

        val sorted = candidatesByText.values
            .sortedByDescending { it.score }
            .take(maxCandidates)

        return sorted.mapIndexed { index, candidate ->
            candidate.copy(isPrimary = (index == 0))
        }
    }
}
