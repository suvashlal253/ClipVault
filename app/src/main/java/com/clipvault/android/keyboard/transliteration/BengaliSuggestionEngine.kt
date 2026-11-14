package com.clipvault.android.keyboard.transliteration

/**
 * Bilingual Smart Suggestion & Prediction Engine (v1.6.0).
 * Provides ranked word suggestions, spelling alternatives, next-word predictions,
 * personal dictionary learning, and undo-last-correction state.
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

    /**
     * Generates 3-5 smart suggestions for the active [languageMode].
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

        // If user is not currently composing a word, return contextual next-word predictions
        if (clean.isEmpty()) {
            if (!nextWordPredictionEnabled) return emptyList()
            val nextWords = BengaliWordDictionary.getNextWordPredictions(previousWord, languageMode)
            return nextWords.take(maxSuggestions).mapIndexed { index, word ->
                BengaliCandidate(
                    text = word,
                    rawRoman = "",
                    score = CandidateSource.NEXT_WORD_PREDICTION.basePriority - (index * 10),
                    source = CandidateSource.NEXT_WORD_PREDICTION,
                    isPrimary = false
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
                getEnglishCandidates(
                    composingEnglish = clean,
                    autocorrectEnabled = autocorrectEnabled,
                    maxCandidates = maxSuggestions
                )
            }
        }
    }

    private fun getEnglishCandidates(
        composingEnglish: String,
        autocorrectEnabled: Boolean,
        maxCandidates: Int
    ): List<BengaliCandidate> {
        val candidates = LinkedHashMap<String, BengaliCandidate>()

        fun putCandidate(text: String, score: Int, source: CandidateSource) {
            if (text.isBlank()) return
            if (!candidates.containsKey(text)) {
                candidates[text] = BengaliCandidate(
                    text = text,
                    rawRoman = composingEnglish,
                    score = score,
                    source = source,
                    isPrimary = false
                )
            }
        }

        // 1. Custom shortcut check (e.g., "brb" -> "be right back")
        phoneticEngine.userDictionary.lookupShortcut(composingEnglish)?.let { sc ->
            putCandidate(sc.expansion, CandidateSource.CUSTOM_SHORTCUT.basePriority, CandidateSource.CUSTOM_SHORTCUT)
        }

        // 2. Personal dictionary check
        phoneticEngine.userDictionary.lookupPreferredWord(composingEnglish)?.let { entry ->
            putCandidate(entry.preferredWord, CandidateSource.PERSONAL_DICTIONARY.basePriority, CandidateSource.PERSONAL_DICTIONARY)
        }

        // 3. English typo autocorrect (if enabled)
        if (autocorrectEnabled) {
            BengaliWordDictionary.getEnglishAutocorrect(composingEnglish)?.let { corrected ->
                putCandidate(corrected, CandidateSource.LEXICON_EXACT.basePriority + 50, CandidateSource.LEXICON_EXACT)
            }
        }

        // 4. Literal typed word (so user is never forced into an unwanted replacement)
        putCandidate(composingEnglish, CandidateSource.ENGLISH_LITERAL.basePriority + 100, CandidateSource.ENGLISH_LITERAL)

        // 5. Common English completions
        val completions = BengaliWordDictionary.getEnglishCompletions(composingEnglish, limit = maxCandidates)
        completions.forEachIndexed { idx, word ->
            putCandidate(word, CandidateSource.LEXICON_PREFIX.basePriority - (idx * 10), CandidateSource.LEXICON_PREFIX)
        }

        val list = candidates.values.sortedByDescending { it.score }.take(maxCandidates)
        return list.mapIndexed { idx, c -> c.copy(isPrimary = (idx == 0)) }
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
