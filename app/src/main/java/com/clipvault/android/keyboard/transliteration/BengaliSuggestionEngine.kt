package com.clipvault.android.keyboard.transliteration

/**
 * Bilingual Smart Suggestion, Mixed-Sentence Workflow, Auto-Correction & Language Detection Engine (v1.7.1).
 * - Fix 2: Tapping a suggestion updates adaptive ranking only; never auto-saves to Personal Dictionary.
 * - Fix 3: Surfaces mixed-language sentence alternatives directly in the keyboard suggestion workflow
 *   without running full-sentence processing on every keystroke.
 * - Fix 6: Suggestion-First default behavior (autoApplyOnSpace = false) for English and Bengali typos.
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
     * Generates smart bilingual word suggestions and (when a mixed Banglish + English clause is active)
     * mixed-language sentence candidates for the active [languageMode] (Fix 3, 6, 10).
     *
     * @param composingText Currently composing word (or full clause if multi-word input).
     * @param previousWord Immediately preceding committed word.
     * @param languageMode Active keyboard language mode.
     * @param sentenceRawTokens Prior raw tokens in the current active sentence before [composingText].
     * @param committedSentencePrefix Exact text already committed to the editor in the current sentence.
     */
    fun getSuggestions(
        composingText: String,
        previousWord: String = "",
        languageMode: KeyboardLanguageMode,
        suggestionsEnabled: Boolean = true,
        autocorrectEnabled: Boolean = true,
        nextWordPredictionEnabled: Boolean = true,
        useBengaliNumerals: Boolean = false,
        maxSuggestions: Int = 5,
        sentenceRawTokens: List<String> = emptyList(),
        committedSentencePrefix: String = ""
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

        // Check if the user is composing a multi-word clause directly OR finishing a mixed-language clause
        val isDirectMultiWord = clean.contains(" ")
        val activeClauseString: String? = when {
            isDirectMultiWord -> clean
            clean.length >= 2 && sentenceRawTokens.isNotEmpty() && sentenceRawTokens.size <= 7 -> {
                (sentenceRawTokens + clean).joinToString(" ")
            }
            else -> null
        }

        val mixedSentenceCandidates: List<BengaliCandidate> = if (
            activeClauseString != null &&
            shouldEvaluateMixedSentenceClause(activeClauseString)
        ) {
            val lastToken = activeClauseString.substringAfterLast(" ").trim()
            val wordCands = when (languageMode) {
                KeyboardLanguageMode.BANGLA_PHONETIC -> phoneticEngine.generateCandidatesForWord(
                    composingRoman = lastToken,
                    previousCommittedWord = previousWord,
                    useBengaliNumerals = useBengaliNumerals,
                    maxCandidates = 3
                )
                KeyboardLanguageMode.ENGLISH -> getEnglishAndBilingualCandidates(
                    composingEnglish = lastToken,
                    previousWord = previousWord,
                    autocorrectEnabled = autocorrectEnabled,
                    maxCandidates = 3
                )
            }
            val replaceChars = if (isDirectMultiWord) 0 else committedSentencePrefix.length
            val sentenceAlts = phoneticEngine.generateMixedSentenceCandidates(activeClauseString, useBengaliNumerals)
                .take(3)
                .mapIndexed { idx, sentenceText ->
                    BengaliCandidate(
                        text = sentenceText,
                        rawRoman = activeClauseString,
                        score = CandidateSource.MIXED_SENTENCE.basePriority - (idx * 10),
                        source = CandidateSource.MIXED_SENTENCE,
                        isPrimary = (idx == 0 && isDirectMultiWord),
                        candidateLanguage = CandidateLanguage.MIXED,
                        replacePrecedingSentenceChars = replaceChars
                    )
                }

            val combined = LinkedHashMap<String, BengaliCandidate>()
            if (isDirectMultiWord) {
                sentenceAlts.forEach { combined[it.text] = it }
                wordCands.forEach { combined.putIfAbsent(it.text, it) }
            } else {
                // Keep primary single-word candidate first so normal typing is never blocked,
                // followed immediately by the 3 mixed-sentence alternatives and literal word!
                wordCands.firstOrNull()?.let { combined[it.text] = it.copy(isPrimary = true) }
                sentenceAlts.forEach { combined.putIfAbsent(it.text, it.copy(isPrimary = false)) }
                wordCands.drop(1).forEach { combined.putIfAbsent(it.text, it.copy(isPrimary = false)) }
            }
            combined.values.take(maxSuggestions.coerceAtLeast(5)).mapIndexed { idx, c ->
                c.copy(isPrimary = (idx == 0))
            }
        } else {
            emptyList()
        }

        if (mixedSentenceCandidates.isNotEmpty()) {
            return mixedSentenceCandidates
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
     * Lightweight gate ensuring full-sentence mixed candidate generation only runs when the clause
     * has 2..8 tokens and genuinely mixes Banglish/Bengali with English/bilingual words (Fix 3 & Fix 10).
     */
    private fun shouldEvaluateMixedSentenceClause(clause: String): Boolean {
        val tokens = clause.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size !in 2..8) return false
        var hasBanglishOrBengali = false
        var hasEnglishOrBilingual = false
        for (raw in tokens) {
            val stripped = raw.trim(',', '.', '?', '!', ';', ':', '।')
            if (stripped.isEmpty()) continue
            when (BengaliWordDictionary.classifyToken(stripped)) {
                TokenLanguageClassification.BANGLISH_WORD,
                TokenLanguageClassification.PURE_BENGALI_SCRIPT -> hasBanglishOrBengali = true
                TokenLanguageClassification.ENGLISH_WORD,
                TokenLanguageClassification.AMBIGUOUS_BILINGUAL -> hasEnglishOrBilingual = true
                else -> {}
            }
        }
        return hasBanglishOrBengali && hasEnglishOrBilingual
    }

    /**
     * Resolves what text should be committed when the user presses Space or Punctuation (Part 2 #6 & Fix 6).
     * - Defaults to "Suggestion First" ([autoApplyOnSpace] = false):
     *   - Never silently replaces uncertain Bengali Unicode spellings or Banglish typos unless [autoApplyOnSpace] is true.
     *   - Never forcibly replaces English words on Space unless [autoApplyOnSpace] is true.
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
                // If input is already pure Bengali Unicode (e.g. "ভূল" or "মানুস"), do not silently
                // replace it on Space unless autoApplyOnSpace is explicitly enabled (Fix 6)
                if (clean.any { it.code in 0x0980..0x09FF }) {
                    return if (autocorrectEnabled && autoApplyOnSpace) {
                        BengaliWordDictionary.getBengaliUnicodeSpellingCorrections(clean).firstOrNull() ?: clean
                    } else {
                        clean
                    }
                }

                // If input is an uncertain Banglish typo (e.g. "kemno"), only auto-replace on Space if autoApplyOnSpace is true;
                // otherwise transliterate phonetically without silent dictionary typo replacement (Fix 6)
                if (BengaliWordDictionary.isBanglishTypoKey(clean)) {
                    return if (autocorrectEnabled && autoApplyOnSpace) {
                        BengaliWordDictionary.getBanglishTypoCorrection(clean)?.candidates?.firstOrNull()
                            ?: BengaliTransliterationRules.transliterateWordPrimary(clean, useBengaliNumerals)
                    } else {
                        BengaliTransliterationRules.transliterateWordPrimary(clean, useBengaliNumerals)
                    }
                }

                // Standard Banglish phonetic conversion
                BengaliWordDictionary.lookupExactBangla(clean, includeTypoCorrections = false)?.candidates?.firstOrNull()
                    ?: BengaliTransliterationRules.transliterateWordPrimary(clean, useBengaliNumerals)
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
            val adaptiveBoost = phoneticEngine.userDictionary.getAdaptiveSelectionBoost(composingEnglish, text)
            val totalScore = score + adaptiveBoost
            val existing = candidates[text]
            if (existing == null || totalScore > existing.score) {
                candidates[text] = BengaliCandidate(
                    text = text,
                    rawRoman = composingEnglish,
                    score = totalScore,
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
                putCandidate(corrected, CandidateSource.TYPO_CORRECTION.basePriority + 50, CandidateSource.TYPO_CORRECTION, CandidateLanguage.ENGLISH)
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
     * Records a user's candidate tap for adaptive ranking ONLY — never automatically saves
     * an entry into the Personal Dictionary (Fix 2).
     */
    fun onCandidateSelectedByUser(
        rawRoman: String,
        selectedWord: String,
        languageMode: KeyboardLanguageMode
    ) {
        val cleanRoman = rawRoman.trim()
        val cleanSelected = selectedWord.trim()
        if (cleanRoman.isNotEmpty() && cleanSelected.isNotEmpty()) {
            phoneticEngine.userDictionary.recordAdaptiveCandidateSelection(cleanRoman, cleanSelected)
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
