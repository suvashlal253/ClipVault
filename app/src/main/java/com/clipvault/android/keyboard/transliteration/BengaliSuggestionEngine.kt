package com.clipvault.android.keyboard.transliteration

/**
 * Bilingual Smart Suggestion, Auto-Correction & Language Detection Engine (v1.7.0).
 * Supports Suggestion-First default behavior (Part 2 #6 & Feature 2), Bilingual Suggestions
 * in both modes (Feature 5), Contextual Next-Word Prediction (Feature 1), and Smart Language
 * Detection (Feature 11).
 */
class BengaliSuggestionEngine(
    val phoneticEngine: BengaliPhoneticEngine = BengaliPhoneticEngine()
) {

    data class LastCorrectionState(
        val rawRomanOrTypo: String,
        val committedReplacement: String,
        val trailingSpace: String = " "
    )

    var lastCorrectionState: LastCorrectionState? = null
        private set

    // Tracks whether the user dismissed the language switch prompt for the current session
    private var dismissedPromptForMode: KeyboardLanguageMode? = null

    /**
     * Generates 3-5 smart bilingual suggestions for the active [languageMode].
     */
    fun getSuggestions(
        composingText: String,
        previousWord: String = "",
        languageMode: KeyboardLanguageMode,
        suggestionsEnabled: Boolean = true,
        autocorrectEnabled: Boolean = true,
        nextWordPredictionEnabled: Boolean = true,
        useBengaliNumerals: Boolean = false,
        maxSuggestions: Int = 5
    ): List<BengaliCandidate> {
        if (!suggestionsEnabled) return emptyList()

        val clean = composingText.trim()

        // If user is not currently composing a word, return contextual next-word predictions (Feature 1)
        if (clean.isEmpty()) {
            if (!nextWordPredictionEnabled) return emptyList()
            val nextWords = BengaliWordDictionary.getNextWordPredictions(previousWord, languageMode)
            val candLang = if (languageMode == KeyboardLanguageMode.BANGLA_PHONETIC) {
                CandidateLanguage.BENGALI
            } else {
                CandidateLanguage.ENGLISH
            }
            return nextWords.take(maxSuggestions).mapIndexed { index, word ->
                BengaliCandidate(
                    text = word,
                    rawRoman = "",
                    score = CandidateSource.NEXT_WORD_PREDICTION.basePriority - (index * 10),
                    source = CandidateSource.NEXT_WORD_PREDICTION,
                    isPrimary = false,
                    candidateLanguage = candLang
                )
            }
        }

        return when (languageMode) {
            KeyboardLanguageMode.BANGLA_PHONETIC -> {
                phoneticEngine.generateCandidatesForWord(
                    composingRoman = clean,
                    previousCommittedWord = previousWord,
                    useBengaliNumerals = useBengaliNumerals,
                    maxCandidates = maxSuggestions
                )
            }
            KeyboardLanguageMode.ENGLISH -> {
                getEnglishAndBilingualCandidates(
                    composingEnglish = clean,
                    previousWord = previousWord,
                    autocorrectEnabled = autocorrectEnabled,
                    maxCandidates = maxSuggestions
                )
            }
        }
    }

    /**
     * Resolves what text should be committed when the user presses Space or Punctuation (Part 2 #6 & Feature 2).
     * - In BANGLA_PHONETIC mode: commits the primary phonetic candidate (or literal English if token is URL/email).
     * - In ENGLISH mode: defaults to "Suggestion First" ([autoApplyOnSpace] = false), preserving the literal
     *   typed word unless [autoApplyOnSpace] is explicitly enabled by the user or a custom shortcut matches.
     */
    fun resolveWordCommitOnSpace(
        composingText: String,
        previousWord: String = "",
        languageMode: KeyboardLanguageMode,
        autocorrectEnabled: Boolean = true,
        autoApplyOnSpace: Boolean = false,
        useBengaliNumerals: Boolean = false
    ): String {
        val clean = composingText.trim()
        if (clean.isEmpty()) return ""

        // Do not auto-correct URLs, email addresses, or alphanumeric codes
        val tokenClass = BengaliWordDictionary.classifyToken(clean)
        if (tokenClass == TokenLanguageClassification.URL_OR_EMAIL ||
            tokenClass == TokenLanguageClassification.NUMBER_OR_CODE
        ) {
            return clean
        }

        // Check multi-word phrase shortcut with previousWord first
        if (previousWord.isNotBlank()) {
            val prevKey = if (previousWord == "আমার") "amar" else previousWord
            phoneticEngine.userDictionary.lookupShortcut(prevKey + " " + clean)?.let { sc ->
                if (sc.expansion.startsWith(previousWord + " ")) {
                    return sc.expansion.removePrefix(previousWord + " ")
                }
                return sc.expansion
            }
        }

        // Check single-word shortcut or personal dictionary
        phoneticEngine.userDictionary.lookupPreferredWord(clean)?.let { return it.preferredWord }
        phoneticEngine.userDictionary.lookupShortcut(clean)?.let { return it.expansion }

        return when (languageMode) {
            KeyboardLanguageMode.BANGLA_PHONETIC -> {
                if (autocorrectEnabled) {
                    val top = getSuggestions(
                        composingText = clean,
                        previousWord = previousWord,
                        languageMode = languageMode,
                        autocorrectEnabled = true,
                        useBengaliNumerals = useBengaliNumerals
                    ).firstOrNull()
                    top?.text ?: phoneticEngine.transliterateWord(clean, useBengaliNumerals)
                } else {
                    phoneticEngine.transliterateWord(clean, useBengaliNumerals)
                }
            }
            KeyboardLanguageMode.ENGLISH -> {
                if (autocorrectEnabled && autoApplyOnSpace) {
                    BengaliWordDictionary.getEnglishAutocorrect(clean) ?: clean
                } else {
                    // Suggestion-First default: never forcefully replace English words on space unless opted in
                    clean
                }
            }
        }
    }

    /**
     * Generates English candidates plus Bilingual Bengali equivalents (Feature 5: e.g. "office" -> "office", "অফিস";
     * "ami" -> "ami", "আমি") so bilingual users can tap Bengali suggestions directly from English mode.
     */
    private fun getEnglishAndBilingualCandidates(
        composingEnglish: String,
        previousWord: String,
        autocorrectEnabled: Boolean,
        maxCandidates: Int
    ): List<BengaliCandidate> {
        val candidates = LinkedHashMap<String, BengaliCandidate>()

        fun putCandidate(
            text: String,
            score: Int,
            source: CandidateSource,
            lang: CandidateLanguage = CandidateLanguage.ENGLISH
        ) {
            if (text.isBlank()) return
            val existing = candidates[text]
            if (existing == null || score > existing.score) {
                candidates[text] = BengaliCandidate(
                    text = text,
                    rawRoman = composingEnglish,
                    score = score,
                    source = source,
                    isPrimary = false,
                    candidateLanguage = lang
                )
            }
        }

        // 1. Personal dictionary check (Tier 1)
        phoneticEngine.userDictionary.lookupPreferredWord(composingEnglish)?.let { entry ->
            val lang = if (entry.preferredWord.any { it.code in 0x0980..0x09FF }) CandidateLanguage.BENGALI else CandidateLanguage.ENGLISH
            putCandidate(entry.preferredWord, CandidateSource.PERSONAL_DICTIONARY.basePriority, CandidateSource.PERSONAL_DICTIONARY, lang)
        }

        // 2. Multi-word & single-word Custom shortcut check (Tier 2)
        if (previousWord.isNotBlank()) {
            phoneticEngine.userDictionary.lookupShortcut(previousWord + " " + composingEnglish)?.let { sc ->
                putCandidate(sc.expansion, CandidateSource.CUSTOM_SHORTCUT.basePriority + 30, CandidateSource.CUSTOM_SHORTCUT)
            }
        }
        phoneticEngine.userDictionary.lookupShortcut(composingEnglish)?.let { sc ->
            putCandidate(sc.expansion, CandidateSource.CUSTOM_SHORTCUT.basePriority, CandidateSource.CUSTOM_SHORTCUT)
        }

        // 3. English typo autocorrect (if enabled)
        if (autocorrectEnabled) {
            BengaliWordDictionary.getEnglishAutocorrect(composingEnglish)?.let { corrected ->
                putCandidate(corrected, CandidateSource.LEXICON_EXACT.basePriority + 50, CandidateSource.LEXICON_EXACT, CandidateLanguage.ENGLISH)
            }
        }

        // 4. Literal typed word (always available so user is never forced into an unwanted replacement)
        putCandidate(composingEnglish, CandidateSource.ENGLISH_LITERAL.basePriority + 160, CandidateSource.ENGLISH_LITERAL, CandidateLanguage.ENGLISH)

        // 5. Bilingual Bengali equivalent or high-confidence Banglish match (Feature 5: "office" -> "অফিস", "ami" -> "আমি")
        BengaliWordDictionary.getBengaliEquivalentForEnglishWord(composingEnglish)?.let { bnEquiv ->
            putCandidate(bnEquiv, CandidateSource.BILINGUAL_EQUIVALENT.basePriority + 110, CandidateSource.BILINGUAL_EQUIVALENT, CandidateLanguage.BENGALI)
        } ?: BengaliWordDictionary.lookupExactBangla(composingEnglish)?.candidates?.firstOrNull()?.let { bnWord ->
            putCandidate(bnWord, CandidateSource.BILINGUAL_EQUIVALENT.basePriority + 90, CandidateSource.BILINGUAL_EQUIVALENT, CandidateLanguage.BENGALI)
        }

        // 6. Common English completions
        val completions = BengaliWordDictionary.getEnglishCompletions(composingEnglish, limit = maxCandidates)
        completions.forEachIndexed { idx, word ->
            putCandidate(word, CandidateSource.LEXICON_PREFIX.basePriority - (idx * 10), CandidateSource.LEXICON_PREFIX, CandidateLanguage.ENGLISH)
        }

        val list = candidates.values.sortedByDescending { it.score }.take(maxCandidates)
        return list.mapIndexed { idx, c -> c.copy(isPrimary = (idx == 0)) }
    }

    /**
     * Smart Offline Language Detection (Feature 11).
     * Evaluates recent tokens and current composing text to offer a non-intrusive, dismissible
     * language switch chip when the user appears to be typing Banglish in English mode or
     * continuous English in Bangla Phonetic mode. Never forcibly switches layout automatically.
     */
    fun detectSmartLanguagePrompt(
        composingText: String,
        recentCommittedWords: List<String>,
        currentMode: KeyboardLanguageMode,
        smartDetectionEnabled: Boolean = true
    ): LanguageSwitchPrompt? {
        if (!smartDetectionEnabled) return null
        if (dismissedPromptForMode == currentMode) return null

        val clean = composingText.trim().lowercase()
        val sampleTokens = buildList {
            addAll(recentCommittedWords.takeLast(2))
            if (clean.length >= 3) add(clean)
        }
        if (sampleTokens.isEmpty()) return null

        return when (currentMode) {
            KeyboardLanguageMode.ENGLISH -> {
                val banglishCount = sampleTokens.count { token ->
                    BengaliWordDictionary.classifyToken(token) == TokenLanguageClassification.BANGLISH_WORD &&
                        BengaliWordDictionary.lookupExactBangla(token) != null
                }
                if (banglishCount >= 2 || (clean.length >= 4 && BengaliWordDictionary.classifyToken(clean) == TokenLanguageClassification.BANGLISH_WORD && BengaliWordDictionary.lookupExactBangla(clean) != null)) {
                    LanguageSwitchPrompt(
                        suggestedMode = KeyboardLanguageMode.BANGLA_PHONETIC,
                        chipLabel = "বাংলায় লিখুন (Switch to বাংলা)",
                        detectedReason = "Detected Banglish phonetic words"
                    )
                } else null
            }
            KeyboardLanguageMode.BANGLA_PHONETIC -> {
                val pureEnglishCount = sampleTokens.count { token ->
                    BengaliWordDictionary.classifyToken(token) == TokenLanguageClassification.ENGLISH_WORD
                }
                if (pureEnglishCount >= 2) {
                    LanguageSwitchPrompt(
                        suggestedMode = KeyboardLanguageMode.ENGLISH,
                        chipLabel = "Switch to English",
                        detectedReason = "Detected consecutive English words"
                    )
                } else null
            }
        }
    }

    fun dismissLanguagePromptForCurrentMode(mode: KeyboardLanguageMode) {
        dismissedPromptForMode = mode
    }

    fun resetLanguagePromptDismissal() {
        dismissedPromptForMode = null
    }

    /**
     * Records a user's explicit candidate selection so the personal dictionary learns their preferred spelling.
     */
    fun onCandidateSelectedByUser(
        rawRoman: String,
        selectedWord: String,
        languageMode: KeyboardLanguageMode
    ) {
        val cleanRoman = rawRoman.trim()
        val cleanSelected = selectedWord.trim()
        if (cleanRoman.isNotEmpty() && cleanSelected.isNotEmpty()) {
            phoneticEngine.userDictionary.addOrUpdateWord(cleanRoman, cleanSelected, languageMode)
            lastCorrectionState = LastCorrectionState(
                rawRomanOrTypo = cleanRoman,
                committedReplacement = cleanSelected
            )
        } else if (cleanSelected.isNotEmpty()) {
            phoneticEngine.userDictionary.recordRecentWord(cleanSelected)
            lastCorrectionState = null
        }
    }

    /**
     * Records an automatic space/punctuation word commit so the user can press Backspace immediately
     * to revert to the raw typed text if desired.
     */
    fun recordAutoCommit(rawInput: String, committedOutput: String, trailing: String = " ") {
        if (rawInput.isNotBlank() && committedOutput.isNotBlank() && rawInput != committedOutput) {
            lastCorrectionState = LastCorrectionState(rawInput, committedOutput, trailing)
        } else {
            lastCorrectionState = null
        }
    }

    fun clearLastCorrectionState() {
        lastCorrectionState = null
    }
}
