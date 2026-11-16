package com.clipvault.android.keyboard.transliteration

/**
 * Incremental Bengali Phonetic Transliteration Engine (v1.7.0).
 * Enforces the strict 5-tier lookup order:
 * 1. Exact personal dictionary matches
 * 2. Multi-word phrase shortcuts using longest-match-first logic
 * 3. Single-word shortcuts
 * 4. Offline language detection (URLs, emails, numbers, general English words)
 * 5. Phonetic transliteration (lexicon + context-sensitive rules)
 */
class BengaliPhoneticEngine(
    val userDictionary: BengaliUserDictionary = BengaliUserDictionary()
) {

    /**
     * Transliterates a single Banglish word into its highest-ranked Bengali (or mixed-English) output.
     */
    fun transliterateWord(
        romanWord: String,
        useBengaliNumerals: Boolean = false,
        preserveMixedEnglish: Boolean = false
    ): String {
        val trimmed = romanWord.trim()
        if (trimmed.isEmpty()) return ""

        // 1. Exact Personal Dictionary match (highest priority, e.g., "suvash" -> "সুভাষ" when saved)
        userDictionary.lookupPreferredWord(trimmed)?.let { personal ->
            return personal.preferredWord
        }

        // 2. Multi-word phrase shortcut if input contains spaces
        if (trimmed.contains(" ")) {
            userDictionary.lookupShortcut(trimmed)?.let { phraseShortcut ->
                return phraseShortcut.expansion
            }
        }

        // 3. Single-word Custom Shortcut expansion
        userDictionary.lookupShortcut(trimmed)?.let { shortcut ->
            return shortcut.expansion
        }

        // 4. Offline Language Detection for URLs, emails, numbers, and mixed-language English words
        val classification = BengaliWordDictionary.classifyToken(trimmed)
        if (classification == TokenLanguageClassification.URL_OR_EMAIL ||
            classification == TokenLanguageClassification.PURE_BENGALI_SCRIPT
        ) {
            return trimmed
        }
        if (classification == TokenLanguageClassification.NUMBER_OR_CODE && !trimmed.all { it.isDigit() }) {
            return trimmed
        }
        if (preserveMixedEnglish) {
            if (classification == TokenLanguageClassification.ENGLISH_WORD ||
                classification == TokenLanguageClassification.AMBIGUOUS_BILINGUAL
            ) {
                return trimmed
            }
        }

        // 5. Offline Bengali lexicon exact match + Algorithmic phonetic rule engine
        BengaliWordDictionary.lookupExactBangla(trimmed)?.let { entry ->
            if (entry.candidates.isNotEmpty()) {
                return entry.candidates.first()
            }
        }

        return BengaliTransliterationRules.transliterateWordPrimary(
            rawWord = trimmed,
            useBengaliNumerals = useBengaliNumerals
        )
    }

    /**
     * Transliterates a full Banglish phrase or sentence into Bengali script while preserving
     * whitespace, punctuation, and applying longest-match-first multi-word phrase shortcuts.
     */
    fun transliterateSentence(
        banglishText: String,
        useBengaliNumerals: Boolean = false,
        preserveMixedEnglish: Boolean = false
    ): String {
        if (banglishText.isEmpty()) return ""

        // Check if entire trimmed sentence is a registered phrase shortcut
        userDictionary.lookupShortcut(banglishText.trim())?.let { exactPhrase ->
            val leadingWs = banglishText.takeWhile { it.isWhitespace() }
            val trailingWs = banglishText.takeLastWhile { it.isWhitespace() }
            return leadingWs + exactPhrase.expansion + trailingWs
        }

        val tokenRegex = Regex("(:[A-Za-z0-9^]+|[A-Za-z0-9^_\\-\u0980-\u09FF]+(?:[.:@/][A-Za-z0-9^_\\-\u0980-\u09FF]+)*|[^A-Za-z0-9^_\\-\u0980-\u09FF]+)")
        val rawTokens = tokenRegex.findAll(banglishText).map { it.value }.toList()
        val multiWordShortcuts = userDictionary.getMultiWordShortcutsLongestFirst()
        val sb = StringBuilder()

        var i = 0
        while (i < rawTokens.size) {
            val token = rawTokens[i]
            val isWordToken = token.first().isLetterOrDigit() ||
                token.first() == '^' ||
                token.first() == ':' ||
                token.first().code in 0x0980..0x09FF

            if (isWordToken && multiWordShortcuts.isNotEmpty()) {
                // Step 2: Try longest-match-first multi-word phrase shortcut starting at token i
                var matchedShortcut: CustomShortcutEntry? = null
                var consumedTokens = 0

                for (shortcut in multiWordShortcuts) {
                    val triggerWords = shortcut.trigger.split(" ")
                    val requiredSpan = triggerWords.size * 2 - 1
                    if (i + requiredSpan <= rawTokens.size) {
                        var matchesAll = true
                        for (wIdx in triggerWords.indices) {
                            val candidateToken = rawTokens[i + wIdx * 2]
                            if (!candidateToken.equals(triggerWords[wIdx], ignoreCase = true)) {
                                matchesAll = false
                                break
                            }
                            if (wIdx < triggerWords.size - 1) {
                                val sepToken = rawTokens[i + wIdx * 2 + 1]
                                if (sepToken.isBlank().not()) {
                                    matchesAll = false
                                    break
                                }
                            }
                        }
                        if (matchesAll) {
                            matchedShortcut = shortcut
                            consumedTokens = requiredSpan
                            break
                        }
                    }
                }

                if (matchedShortcut != null) {
                    sb.append(matchedShortcut.expansion)
                    i += consumedTokens
                    continue
                }
            }

            if (isWordToken) {
                sb.append(
                    transliterateWord(
                        romanWord = token,
                        useBengaliNumerals = useBengaliNumerals,
                        preserveMixedEnglish = preserveMixedEnglish
                    )
                )
            } else {
                sb.append(if (token == ".") "।" else token)
            }
            i++
        }
        return sb.toString()
    }

    /**
     * Generates ranked sentence-level mixed-language & full-Bengali candidates for natural
     * code-switching inputs (Part 2 #2 & Feature 9).
     *
     * Example for "ami tomorrow office jabo":
     * 1. "আমি tomorrow office যাব" (English loanwords preserved)
     * 2. "আমি আগামীকাল অফিস যাব" (Full Bengali equivalents)
     * 3. "আমি tomorrow অফিস যাব" (Hybrid conversational Banglish)
     */
    fun generateMixedSentenceCandidates(
        inputSentence: String,
        useBengaliNumerals: Boolean = false
    ): List<String> {
        val trimmed = inputSentence.trim()
        if (trimmed.isEmpty()) return emptyList()

        val words = trimmed.split(Regex("\\s+"))
        val results = LinkedHashSet<String>()

        // Candidate 1: Keep all English/bilingual words in English, transliterate Banglish words (with concise verb form e.g. যাব)
        val mixedEnglishKept = words.joinToString(" ") { w ->
            val cls = BengaliWordDictionary.classifyToken(w)
            when {
                userDictionary.lookupPreferredWord(w) != null -> userDictionary.lookupPreferredWord(w)!!.preferredWord
                cls == TokenLanguageClassification.AMBIGUOUS_BILINGUAL ||
                    cls == TokenLanguageClassification.ENGLISH_WORD ||
                    cls == TokenLanguageClassification.URL_OR_EMAIL -> w
                w.equals("jabo", ignoreCase = true) -> "যাব"
                else -> transliterateWord(w, useBengaliNumerals, preserveMixedEnglish = true)
            }
        }
        results.add(mixedEnglishKept)

        // Candidate 2: Translate/transliterate all bilingual English words to their Bengali semantic equivalents
        val fullBengali = words.joinToString(" ") { w ->
            val preferred = userDictionary.lookupPreferredWord(w)?.preferredWord
            val bnEquivalent = BengaliWordDictionary.getBengaliEquivalentForEnglishWord(w)
            when {
                preferred != null -> preferred
                bnEquivalent != null -> bnEquivalent
                w.equals("jabo", ignoreCase = true) -> "যাব"
                else -> transliterateWord(w, useBengaliNumerals, preserveMixedEnglish = false)
            }
        }
        results.add(fullBengali)

        // Candidate 3: Hybrid conversational form (preserve temporal/status English words like tomorrow/today, convert place/noun loanwords like office -> অফিস)
        val hybridSentence = words.joinToString(" ") { w ->
            val lower = w.lowercase()
            when {
                userDictionary.lookupPreferredWord(w) != null -> userDictionary.lookupPreferredWord(w)!!.preferredWord
                lower == "tomorrow" || lower == "today" || lower == "yesterday" -> w
                w.equals("jabo", ignoreCase = true) -> "যাব"
                else -> transliterateWord(w, useBengaliNumerals, preserveMixedEnglish = false)
            }
        }
        results.add(hybridSentence)

        // Candidate 4: Standard sentence transliteration with preserveMixedEnglish = true
        results.add(transliterateSentence(trimmed, useBengaliNumerals, preserveMixedEnglish = true))

        return results.toList()
    }

    /**
     * Generates ranked Bengali + Bilingual candidates for the currently composing word (Feature 5, 9, 14).
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

        fun addCandidate(
            text: String,
            score: Int,
            source: CandidateSource,
            lang: CandidateLanguage = CandidateLanguage.BENGALI
        ) {
            if (text.isBlank()) return
            val existing = candidatesByText[text]
            if (existing == null || score > existing.score) {
                candidatesByText[text] = BengaliCandidate(
                    text = text,
                    rawRoman = clean,
                    score = score,
                    source = source,
                    isPrimary = false,
                    candidateLanguage = lang
                )
            }
        }

        // 1. Exact Personal Dictionary match (Tier 1 - Highest priority)
        userDictionary.lookupPreferredWord(clean)?.let { personal ->
            addCandidate(
                personal.preferredWord,
                CandidateSource.PERSONAL_DICTIONARY.basePriority + (personal.frequency * 5),
                CandidateSource.PERSONAL_DICTIONARY,
                if (personal.preferredWord.any { it.code in 0x0980..0x09FF }) CandidateLanguage.BENGALI else CandidateLanguage.ENGLISH
            )
        }

        // 2. Multi-word phrase shortcut with previousCommittedWord (Tier 2 - Longest-match-first)
        if (previousCommittedWord.isNotBlank()) {
            val prevVariants = linkedSetOf(previousCommittedWord)
            if (previousCommittedWord == "আমার") prevVariants.add("amar")
            if (previousCommittedWord == "আমি") prevVariants.add("ami")
            for (prev in prevVariants) {
                val phraseKey = prev + " " + clean
                userDictionary.lookupShortcut(phraseKey)?.let { phraseShortcut ->
                    val suffix = if (phraseShortcut.expansion.startsWith(previousCommittedWord + " ")) {
                        phraseShortcut.expansion.removePrefix(previousCommittedWord + " ")
                    } else {
                        phraseShortcut.expansion
                    }
                    addCandidate(suffix, CandidateSource.CUSTOM_SHORTCUT.basePriority + 40, CandidateSource.CUSTOM_SHORTCUT)
                }
            }
        }

        // 3. Single-word Custom Shortcut
        userDictionary.lookupShortcut(clean)?.let { shortcut ->
            addCandidate(
                shortcut.expansion,
                CandidateSource.CUSTOM_SHORTCUT.basePriority,
                CandidateSource.CUSTOM_SHORTCUT
            )
        }

        // 4. Contextual Bigram Boost from previousCommittedWord (Tier 3)
        val contextualNext = if (previousCommittedWord.isNotBlank()) {
            BengaliWordDictionary.getNextWordPredictions(
                previousWord = previousCommittedWord,
                languageMode = KeyboardLanguageMode.BANGLA_PHONETIC
            ).toSet()
        } else {
            emptySet()
        }

        // 5. Offline Lexicon Exact Match (Tier 4)
        BengaliWordDictionary.lookupExactBangla(clean)?.let { lexiconEntry ->
            lexiconEntry.candidates.forEachIndexed { idx, word ->
                val contextBonus = if (contextualNext.contains(word)) 80 else 0
                val rankOffset = (lexiconEntry.candidates.size - idx) * 12
                val candLang = if (word.any { it.code in 0x0980..0x09FF }) CandidateLanguage.BENGALI else CandidateLanguage.ENGLISH
                addCandidate(
                    text = word,
                    score = CandidateSource.LEXICON_EXACT.basePriority + lexiconEntry.baseFrequency + rankOffset + contextBonus,
                    source = if (contextBonus > 0) CandidateSource.CONTEXTUAL_BIGRAM else CandidateSource.LEXICON_EXACT,
                    lang = candLang
                )
            }
        }

        // 6. Bilingual Semantic Equivalent (e.g. "tomorrow" -> "আগামীকাল", "office" -> "অফিস")
        BengaliWordDictionary.getBengaliEquivalentForEnglishWord(clean)?.let { bnEquiv ->
            addCandidate(
                text = bnEquiv,
                score = CandidateSource.BILINGUAL_EQUIVALENT.basePriority + 180,
                source = CandidateSource.BILINGUAL_EQUIVALENT,
                lang = CandidateLanguage.BENGALI
            )
        }

        // 7. Algorithmic Phonetic Rule Engine & Orthographic Variants (Tier 5)
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
                source = source,
                lang = CandidateLanguage.BENGALI
            )
        }

        // 8. Prefix Completions from Offline Lexicon (Tier 6)
        val prefixEntries = BengaliWordDictionary.lookupPrefixBangla(clean, limit = 3)
        prefixEntries.forEachIndexed { idx, entry ->
            val topWord = entry.candidates.firstOrNull() ?: return@forEachIndexed
            addCandidate(
                text = topWord,
                score = CandidateSource.LEXICON_PREFIX.basePriority + (entry.baseFrequency / 2) - (idx * 10),
                source = CandidateSource.LEXICON_PREFIX,
                lang = CandidateLanguage.BENGALI
            )
        }

        // 9. Include literal English word as a bilingual candidate for seamless mixed typing (Feature 5 & 9)
        val tokenClass = BengaliWordDictionary.classifyToken(clean)
        val englishBonus = when (tokenClass) {
            TokenLanguageClassification.ENGLISH_WORD,
            TokenLanguageClassification.AMBIGUOUS_BILINGUAL,
            TokenLanguageClassification.URL_OR_EMAIL -> 240
            else -> 60
        }
        addCandidate(
            text = clean,
            score = CandidateSource.ENGLISH_LITERAL.basePriority + englishBonus,
            source = CandidateSource.ENGLISH_LITERAL,
            lang = CandidateLanguage.ENGLISH
        )

        val sorted = candidatesByText.values
            .sortedByDescending { it.score }
            .take(maxCandidates)
            .toMutableList()

        val literalCandidate = candidatesByText[clean]
        if (literalCandidate != null && sorted.size >= 3 && sorted.none { it.text == clean }) {
            sorted[sorted.lastIndex] = literalCandidate
        }

        return sorted.mapIndexed { index, candidate ->
            candidate.copy(isPrimary = (index == 0))
        }
    }
}
