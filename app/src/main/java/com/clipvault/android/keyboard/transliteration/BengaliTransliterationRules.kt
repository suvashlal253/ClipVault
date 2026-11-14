package com.clipvault.android.keyboard.transliteration

/**
 * Context-sensitive Avro/Phonetic-style rule engine for converting Banglish (Roman) input
 * into Bengali Unicode script with multiple orthographic candidate variants.
 */
object BengaliTransliterationRules {

    private data class ConsonantToken(
        val pattern: String,
        val primary: String,
        val alternates: List<String> = emptyList()
    )

    private data class VowelToken(
        val pattern: String,
        val independent: String,
        val kar: String,
        val alternatesKar: List<String> = emptyList()
    )

    // Ordered longest-first (4 -> 3 -> 2 -> 1)
    private val CONSONANT_TOKENS: List<ConsonantToken> = listOf(
        ConsonantToken("ksh", "ক্ষ", listOf("কশ")),
        ConsonantToken("kkh", "ক্ষ", listOf("কখ")),
        ConsonantToken("cch", "চ্ছ"),
        ConsonantToken("chh", "ছ", listOf("চ")),
        ConsonantToken("ngg", "ঙ্গ"),
        ConsonantToken("nch", "ঞ্চ"),
        ConsonantToken("nth", "ন্থ"),
        ConsonantToken("ndh", "ন্ধ"),
        ConsonantToken("ddh", "দ্ধ"),
        ConsonantToken("bhr", "ভ্র"),
        ConsonantToken("shr", "শ্র", listOf("স্র")),
        ConsonantToken("str", "স্ত্র"),
        ConsonantToken("shw", "শ্ব", listOf("স্ব")),
        ConsonantToken("jñ", "জ্ঞ"),
        ConsonantToken("gg", "জ্ঞ", listOf("গগ")),
        ConsonantToken("gY", "জ্ঞ"),
        ConsonantToken("kh", "খ", listOf("ক")),
        ConsonantToken("gh", "ঘ", listOf("গ")),
        ConsonantToken("Ng", "ঙ", listOf("ং")),
        ConsonantToken("ng", "ং", listOf("ঙ্গ", "ঙ")),
        ConsonantToken("ch", "চ", listOf("ছ")),
        ConsonantToken("jh", "ঝ", listOf("জ")),
        ConsonantToken("NG", "ঞ"),
        ConsonantToken("Th", "ঠ", listOf("থ")),
        ConsonantToken("Dh", "ঢ", listOf("ধ")),
        ConsonantToken("th", "থ", listOf("ঠ", "ত")),
        ConsonantToken("dh", "ধ", listOf("ঢ", "দ")),
        ConsonantToken("ph", "ফ", listOf("প")),
        ConsonantToken("bh", "ভ", listOf("ব")),
        ConsonantToken("sh", "শ", listOf("ষ", "স")),
        ConsonantToken("Sh", "ষ", listOf("শ")),
        ConsonantToken("Rh", "ঢ়", listOf("ড়")),
        ConsonantToken("nk", "ঙ্ক", listOf("ংক")),
        ConsonantToken("nj", "ঞ্জ"),
        ConsonantToken("nt", "ন্ত", listOf("ন্ট")),
        ConsonantToken("nd", "ন্দ", listOf("ন্ড")),
        ConsonantToken("mp", "ম্প"),
        ConsonantToken("mb", "ম্ব"),
        ConsonantToken("st", "স্ত", listOf("ষ্ট", "স্ট")),
        ConsonantToken("sk", "স্ক", listOf("ষ্ক")),
        ConsonantToken("sp", "স্প", listOf("ষ্প")),
        ConsonantToken("sn", "স্ন", listOf("ষ্ণ")),
        ConsonantToken("sm", "স্ম", listOf("ষ্ম")),
        ConsonantToken("sr", "স্র", listOf("শ্র")),
        ConsonantToken("pr", "প্র"),
        ConsonantToken("tr", "ত্র"),
        ConsonantToken("kr", "ক্র"),
        ConsonantToken("gr", "গ্র"),
        ConsonantToken("br", "ব্র"),
        ConsonantToken("dr", "দ্র"),
        ConsonantToken("kl", "ক্ল"),
        ConsonantToken("pl", "প্ল"),
        ConsonantToken("bl", "ব্ল"),
        ConsonantToken("kk", "ক্ক"),
        ConsonantToken("kt", "ক্ত"),
        ConsonantToken("ll", "ল্ল"),
        ConsonantToken("mm", "ম্ম"),
        ConsonantToken("nn", "ন্ন", listOf("ণ্ণ")),
        ConsonantToken("tt", "ত্ত", listOf("ট্ট")),
        ConsonantToken("dd", "দ্দ", listOf("ড্ড")),
        ConsonantToken("bb", "ব্ব"),
        ConsonantToken("pp", "প্প"),
        ConsonantToken("jj", "জ্জ"),
        ConsonantToken("bd", "ব্দ"),
        ConsonantToken("k", "ক", listOf("খ")),
        ConsonantToken("g", "গ", listOf("ঘ")),
        ConsonantToken("c", "চ", listOf("ছ", "ক")),
        ConsonantToken("j", "জ", listOf("য", "ঝ")),
        ConsonantToken("T", "ট", listOf("ত")),
        ConsonantToken("D", "ড", listOf("দ")),
        ConsonantToken("N", "ণ", listOf("ন")),
        ConsonantToken("t", "ত", listOf("ট", "থ")),
        ConsonantToken("d", "দ", listOf("ড", "ধ")),
        ConsonantToken("n", "ন", listOf("ণ")),
        ConsonantToken("p", "প", listOf("ফ")),
        ConsonantToken("f", "ফ", listOf("প")),
        ConsonantToken("b", "ব", listOf("ভ")),
        ConsonantToken("v", "ভ", listOf("ব")),
        ConsonantToken("m", "ম"),
        ConsonantToken("z", "য", listOf("জ")),
        ConsonantToken("r", "র", listOf("ড়")),
        ConsonantToken("l", "ল"),
        ConsonantToken("S", "ষ", listOf("শ", "স")),
        ConsonantToken("s", "স", listOf("শ", "ষ", "ছ")),
        ConsonantToken("h", "হ"),
        ConsonantToken("R", "ড়", listOf("র", "ঢ়")),
        ConsonantToken("y", "য়", listOf("য")),
        ConsonantToken("Y", "য়", listOf("য")),
        ConsonantToken("w", "ও", listOf("ব")),
        ConsonantToken("q", "ক")
    )

    // Ordered longest-first
    private val VOWEL_TOKENS: List<VowelToken> = listOf(
        VowelToken("rri", "ঋ", "ৃ"),
        VowelToken("ou", "ঔ", "ৌ"),
        VowelToken("OU", "ঔ", "ৌ"),
        VowelToken("oi", "ঐ", "ৈ"),
        VowelToken("OI", "ঐ", "ৈ"),
        VowelToken("aa", "আ", "া"),
        VowelToken("ii", "ঈ", "ী", listOf("ি")),
        VowelToken("ee", "ঈ", "ী", listOf("ি")),
        VowelToken("uu", "ঊ", "ূ", listOf("ু")),
        VowelToken("oo", "ঊ", "ু", listOf("ূ", "ো")),
        VowelToken("A", "আ", "া"),
        VowelToken("I", "ঈ", "ী", listOf("ি")),
        VowelToken("U", "ঊ", "ূ", listOf("ু")),
        VowelToken("O", "ও", "ো"),
        VowelToken("E", "এ", "ে"),
        VowelToken("a", "আ", "া"),
        VowelToken("i", "ই", "ি", listOf("ী")),
        VowelToken("u", "উ", "ু", listOf("ূ")),
        VowelToken("e", "এ", "ে"),
        VowelToken("o", "অ", "", listOf("ো"))
    )

    private val BENGALI_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    /**
     * Converts ASCII digits 0-9 to Bengali numerals ০-৯ when enabled.
     */
    fun convertDigitsToBengali(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(BENGALI_DIGITS[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Performs primary algorithmic phonetic transliteration of a single Roman word token.
     */
    fun transliterateWordPrimary(
        rawWord: String,
        useBengaliNumerals: Boolean = false,
        terminalOMode: TerminalOMode = TerminalOMode.SMART_DEFAULT
    ): String {
        if (rawWord.isEmpty()) return ""
        if (rawWord.all { it.isDigit() }) {
            return if (useBengaliNumerals) convertDigitsToBengali(rawWord) else rawWord
        }

        // Normalize common Banglish shorthands before state-machine parsing
        val word = normalizeBanglishWord(rawWord)
        val sb = StringBuilder()
        var index = 0
        var prevWasConsonant = false

        while (index < word.length) {
            val remaining = word.substring(index)

            // 1. Check punctuation / special markers
            if (remaining.startsWith("..")) {
                sb.append(".")
                index += 2
                prevWasConsonant = false
                continue
            }
            if (remaining[0] == '.') {
                sb.append("।")
                index += 1
                prevWasConsonant = false
                continue
            }
            if (remaining[0] == '^') {
                sb.append("ঁ")
                index += 1
                prevWasConsonant = false
                continue
            }
            if (remaining[0] == ':') {
                sb.append("ঃ")
                index += 1
                prevWasConsonant = false
                continue
            }
            if (remaining[0] in '0'..'9') {
                sb.append(if (useBengaliNumerals) BENGALI_DIGITS[remaining[0] - '0'] else remaining[0])
                index += 1
                prevWasConsonant = false
                continue
            }

            // 2. Check 'y' / 'w' immediately after consonant (Jo-phola / Bo-phola)
            if (prevWasConsonant && (remaining[0] == 'y' || remaining[0] == 'Y') && remaining.length > 1 && isVowelStart(remaining.substring(1))) {
                sb.append("্য")
                index += 1
                // Keep prevWasConsonant = true so the following vowel attaches as a Kar
                continue
            }
            if (prevWasConsonant && remaining[0] == 'w' && remaining.length > 1 && isVowelStart(remaining.substring(1))) {
                sb.append("্ব")
                index += 1
                continue
            }

            // 3. Check Vowel tokens
            val vowelMatch = matchVowel(remaining)
            if (vowelMatch != null) {
                val isAtWordEnd = (index + vowelMatch.pattern.length == word.length)
                if (!prevWasConsonant) {
                    // At start of word or after another vowel -> Independent vowel
                    // Special case: 'o' after a vowel (e.g., "khao" -> "খাও")
                    if (vowelMatch.pattern == "o" && index > 0) {
                        sb.append("ও")
                    } else {
                        sb.append(vowelMatch.independent)
                    }
                } else {
                    // After a consonant -> Dependent Kar (or inherent vowel for 'o')
                    if (vowelMatch.pattern == "o") {
                        val useOKar = when (terminalOMode) {
                            TerminalOMode.ALWAYS_O_KAR -> true
                            TerminalOMode.ALWAYS_INHERENT -> false
                            TerminalOMode.SMART_DEFAULT -> isAtWordEnd && shouldUseTerminalOKar(word)
                        }
                        if (useOKar) {
                            sb.append("ো")
                        }
                    } else {
                        sb.append(vowelMatch.kar)
                    }
                }
                index += vowelMatch.pattern.length
                prevWasConsonant = false
                continue
            }

            // 4. Check Consonant tokens
            val consonantMatch = matchConsonant(remaining)
            if (consonantMatch != null) {
                // Contextual 'ng': before a vowel inside a word (e.g., "bangali") or at end ("bangla")
                if (consonantMatch.pattern == "ng") {
                    val afterNg = remaining.substring(2)
                    if (afterNg.isNotEmpty() && isVowelStart(afterNg)) {
                        sb.append("ঙ্গ")
                        index += 2
                        prevWasConsonant = true
                        continue
                    } else {
                        sb.append("ং")
                        index += 2
                        prevWasConsonant = false
                        continue
                    }
                }

                // Check if two separate consonants should form a conjunct via Hasanta
                if (prevWasConsonant && shouldJoinWithHasanta(word, index, consonantMatch.pattern)) {
                    sb.append("্")
                }

                sb.append(consonantMatch.primary)
                index += consonantMatch.pattern.length
                // Pre-composed tokens ending with Anusvara/Visarga don't take Kar
                prevWasConsonant = !consonantMatch.primary.endsWith("ং") && !consonantMatch.primary.endsWith("ঃ")
                continue
            }

            // Fallback: preserve non-alphabetic character
            sb.append(remaining[0])
            index += 1
            prevWasConsonant = false
        }

        return sb.toString()
    }

    enum class TerminalOMode {
        SMART_DEFAULT,
        ALWAYS_O_KAR,
        ALWAYS_INHERENT
    }

    /**
     * Generates up to [maxVariants] distinct phonetic transliteration candidates for a Roman word
     * by exploring vowel/consonant ambiguities (e.g. শ/ষ/স, চ/ছ, ভ/ব, ি/ী, terminal ো vs inherent অ).
     */
    fun generatePhoneticVariants(
        rawWord: String,
        useBengaliNumerals: Boolean = false,
        maxVariants: Int = 6
    ): List<String> {
        if (rawWord.isBlank()) return emptyList()
        val results = LinkedHashSet<String>()

        // 1. Primary smart transliteration
        val primary = transliterateWordPrimary(rawWord, useBengaliNumerals, TerminalOMode.SMART_DEFAULT)
        if (primary.isNotEmpty()) results.add(primary)

        // 2. Terminal 'o' variations (e.g. "bhalo" -> "ভালো" and "ভাল")
        if (rawWord.endsWith("o", ignoreCase = true) && rawWord.length >= 3) {
            results.add(transliterateWordPrimary(rawWord, useBengaliNumerals, TerminalOMode.ALWAYS_O_KAR))
            results.add(transliterateWordPrimary(rawWord, useBengaliNumerals, TerminalOMode.ALWAYS_INHERENT))
        }

        // 3. Explore common Banglish consonant/vowel substitutions
        val lower = rawWord.lowercase()
        val substitutionSeeds = mutableListOf<String>()

        if ("sh" in lower) {
            substitutionSeeds.add(rawWord.replace(Regex("sh", RegexOption.IGNORE_CASE), "S"))
            substitutionSeeds.add(rawWord.replace(Regex("sh", RegexOption.IGNORE_CASE), "s"))
        }
        if ("s" in lower && "sh" !in lower) {
            substitutionSeeds.add(rawWord.replace(Regex("s", RegexOption.IGNORE_CASE), "sh"))
            substitutionSeeds.add(rawWord.replace(Regex("s", RegexOption.IGNORE_CASE), "S"))
        }
        if ("v" in lower) {
            substitutionSeeds.add(rawWord.replace(Regex("v", RegexOption.IGNORE_CASE), "bh"))
            substitutionSeeds.add(rawWord.replace(Regex("v", RegexOption.IGNORE_CASE), "b"))
            if ("sh" in lower) {
                substitutionSeeds.add(
                    rawWord.replace(Regex("v", RegexOption.IGNORE_CASE), "bh")
                        .replace(Regex("sh", RegexOption.IGNORE_CASE), "S")
                )
            }
        }
        if ("ch" in lower && "chh" !in lower) {
            substitutionSeeds.add(rawWord.replace(Regex("ch", RegexOption.IGNORE_CASE), "chh"))
        }
        if ("n" in lower) {
            substitutionSeeds.add(rawWord.replace(Regex("n$", RegexOption.IGNORE_CASE), "N"))
        }
        if (lower.endsWith("i") && !lower.endsWith("ii")) {
            substitutionSeeds.add(rawWord + "i")
        }

        for (seed in substitutionSeeds) {
            if (results.size >= maxVariants) break
            val variant = transliterateWordPrimary(seed, useBengaliNumerals, TerminalOMode.SMART_DEFAULT)
            if (variant.isNotEmpty()) {
                results.add(variant)
            }
        }

        // 4. Emphatic Bengali clitic variants (-ই / -ও) if still under limit
        if (results.size < maxVariants && primary.length >= 2 && !primary.endsWith("ই")) {
            results.add(primary + "ই")
        }

        return results.take(maxVariants)
    }

    private fun normalizeBanglishWord(raw: String): String {
        var w = raw
        // Keep case only where phonetically meaningful in Avro (T, Th, D, Dh, N, S, R, Rh, O, I, U, NG, Ng)
        // Lowercase initial letter if the rest of the word is lowercase so Shift at sentence start doesn't force retroflex
        if (w.length > 1 && w[0].isUpperCase() && w.substring(1).all { it.isLowerCase() }) {
            w = w[0].lowercaseChar() + w.substring(1)
        }
        // Common multi-syllable medial 'o' where 'ো' is standard in Bengali ("bhalobashi", "bhalobasha", "manobota")
        w = w.replace("bhalob", "bhalOb")
            .replace("valob", "bhalOb")
            .replace("manob", "manOb")
            .replace("shomoy", "shomoy")
        return w
    }

    private fun shouldUseTerminalOKar(word: String): Boolean {
        val lower = word.lowercase()
        // Words like "kemon" don't end in 'o', words like "bhalo", "acho", "koro", "bolo", "cholo", "jeno", "kono" do
        val inherentEndings = setOf("bonto", "shonto", "ponto")
        if (inherentEndings.any { lower.endsWith(it) }) return false
        return true
    }

    private fun shouldJoinWithHasanta(word: String, index: Int, nextPattern: String): Boolean {
        // Join with Hasanta if next consonant is 'r' (Ra-phola) or 'l' (La-phola) at start of syllable
        if ((nextPattern == "r" || nextPattern == "l") && index + 1 < word.length && isVowelStart(word.substring(index + 1))) {
            return true
        }
        return false
    }

    private fun matchVowel(s: String): VowelToken? {
        for (token in VOWEL_TOKENS) {
            if (s.startsWith(token.pattern)) return token
        }
        return null
    }

    private fun matchConsonant(s: String): ConsonantToken? {
        // First try exact case-sensitive match for retroflex/palatal distinctions (T, D, N, S, R, Ng, NG)
        for (token in CONSONANT_TOKENS) {
            if (s.startsWith(token.pattern, ignoreCase = false)) return token
        }
        // Fallback case-insensitive match
        for (token in CONSONANT_TOKENS) {
            if (s.startsWith(token.pattern, ignoreCase = true)) return token
        }
        return null
    }

    private fun isVowelStart(s: String): Boolean {
        if (s.isEmpty()) return false
        val ch = s[0].lowercaseChar()
        return ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u'
    }
}
