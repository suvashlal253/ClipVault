package com.clipvault.android.keyboard.transliteration

/**
 * Context-sensitive phonetic rule engine for converting Banglish (Roman) input
 * into Bengali Unicode script with bounded LRU caching and multi-variant expansion (v1.7.4).
 */
object BengaliTransliterationRules {

    private const val MAX_CACHE_ENTRIES = 512

    private val primaryCache = object : LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MAX_CACHE_ENTRIES
        }
    }

    private val variantsCache = object : LinkedHashMap<String, List<String>>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean {
            return size > MAX_CACHE_ENTRIES
        }
    }

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

    // Ordered longest-first (4 -> 3 -> 2 -> 1) covering all 18+ mandatory complex conjuncts & Reph clusters
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
        ConsonantToken("sth", "স্থ", listOf("স্ত")),
        ConsonantToken("sht", "ষ্ট", listOf("স্ত", "স্ট")),
        ConsonantToken("shT", "ষ্ট", listOf("স্ত")),
        ConsonantToken("shm", "শ্ম", listOf("স্ম", "ষ্ম")),
        ConsonantToken("shn", "শ্ন", listOf("স্ন", "ষ্ণ")),
        ConsonantToken("shk", "ষ্ক", listOf("স্ক")),
        ConsonantToken("shp", "ষ্প", listOf("স্প")),
        ConsonantToken("bhr", "ভ্র"),
        ConsonantToken("shr", "শ্র", listOf("স্র")),
        ConsonantToken("str", "স্ত্র"),
        ConsonantToken("shw", "শ্ব", listOf("স্ব")),
        ConsonantToken("rch", "র্চ", listOf("রচ")),
        ConsonantToken("rdh", "র্ধ", listOf("রধ")),
        ConsonantToken("rbh", "র্ভ", listOf("রভ")),
        ConsonantToken("rsh", "র্শ", listOf("র্ষ")),
        ConsonantToken("jñ", "জ্ঞ"),
        ConsonantToken("gg", "জ্ঞ", listOf("গগ")),
        ConsonantToken("gY", "জ্ঞ"),
        ConsonantToken("jn", "জ্ঞ", listOf("জন")),
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
        ConsonantToken("nt", "ন্ত", listOf("ন্ট", "ণ্ঠ")),
        ConsonantToken("nd", "ন্দ", listOf("ন্ড")),
        ConsonantToken("mp", "ম্প"),
        ConsonantToken("mb", "ম্ব"),
        ConsonantToken("st", "স্ত", listOf("ষ্ট", "স্থ", "স্ট")),
        ConsonantToken("St", "ষ্ট", listOf("স্ত")),
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
        ConsonantToken("mr", "ম্র"),
        ConsonantToken("kl", "ক্ল"),
        ConsonantToken("pl", "প্ল"),
        ConsonantToken("bl", "ব্ল"),
        ConsonantToken("gl", "গ্ল"),
        ConsonantToken("sl", "স্ল"),
        ConsonantToken("kk", "ক্ক", listOf("ক্য")),
        ConsonantToken("kY", "ক্য"),
        ConsonantToken("kt", "ক্ত"),
        ConsonantToken("dy", "দ্য"),
        ConsonantToken("dm", "দ্ম"),
        ConsonantToken("hn", "হ্ন"),
        ConsonantToken("hm", "হ্ম"),
        ConsonantToken("lp", "ল্প"),
        ConsonantToken("lk", "ল্ক"),
        ConsonantToken("lg", "ল্গ"),
        ConsonantToken("lm", "ল্ম"),
        ConsonantToken("lb", "ল্ব"),
        ConsonantToken("rk", "র্ক"),
        ConsonantToken("rg", "র্গ"),
        ConsonantToken("rj", "র্জ"),
        ConsonantToken("rt", "র্ত", listOf("র্ট")),
        ConsonantToken("rd", "র্দ"),
        ConsonantToken("rn", "র্ণ", listOf("র্ন")),
        ConsonantToken("rp", "র্প"),
        ConsonantToken("rb", "র্ব"),
        ConsonantToken("rm", "র্ম"),
        ConsonantToken("rS", "র্ষ"),
        ConsonantToken("ll", "ল্ল"),
        ConsonantToken("mm", "ম্ম"),
        ConsonantToken("nn", "ন্ন", listOf("ণ্ণ")),
        ConsonantToken("tt", "ত্ত", listOf("ট্ট")),
        ConsonantToken("dd", "দ্দ", listOf("ড্ড")),
        ConsonantToken("bb", "ব্ব"),
        ConsonantToken("pp", "প্প"),
        ConsonantToken("jj", "জ্জ"),
        ConsonantToken("bd", "ব্দ"),
        ConsonantToken("gd", "গ্ধ"),
        ConsonantToken("t^", "ৎ"),
        ConsonantToken("k", "ক", listOf("খ")),
        ConsonantToken("g", "গ", listOf("ঘ")),
        ConsonantToken("c", "চ", listOf("ছ", "ক")),
        ConsonantToken("j", "জ", listOf("য", "ঝ")),
        ConsonantToken("T", "ট", listOf("ত")),
        ConsonantToken("D", "ড", listOf("দ")),
        ConsonantToken("N", "ণ", listOf("ন")),
        ConsonantToken("t", "ত", listOf("ট", "থ", "ৎ")),
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
     * Performs primary algorithmic phonetic transliteration of a single Roman word token
     * with bounded LRU caching for rapid repeated lookups.
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

        val cacheKey = rawWord + "|" + useBengaliNumerals + "|" + terminalOMode.ordinal
        synchronized(primaryCache) {
            primaryCache[cacheKey]?.let { return it }
        }

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

            // 2. Check 'y' / 'w' immediately after consonant (Ya-phala / Bo-phola)
            if (prevWasConsonant && (remaining[0] == 'y' || remaining[0] == 'Y') && remaining.length > 1 && isVowelStart(remaining.substring(1))) {
                sb.append("্য")
                index += 1
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
                    if (vowelMatch.pattern == "o" && index > 0) {
                        sb.append("ও")
                    } else {
                        sb.append(vowelMatch.independent)
                    }
                } else {
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

                if (prevWasConsonant && shouldJoinWithHasanta(word, index, consonantMatch.pattern)) {
                    sb.append("্")
                }

                sb.append(consonantMatch.primary)
                index += consonantMatch.pattern.length
                prevWasConsonant = !consonantMatch.primary.endsWith("ং") &&
                    !consonantMatch.primary.endsWith("ঃ") &&
                    !consonantMatch.primary.endsWith("ৎ")
                continue
            }

            // Fallback: preserve non-alphabetic character
            sb.append(remaining[0])
            index += 1
            prevWasConsonant = false
        }

        val result = sb.toString()
        synchronized(primaryCache) {
            primaryCache[cacheKey] = result
        }
        return result
    }

    enum class TerminalOMode {
        SMART_DEFAULT,
        ALWAYS_O_KAR,
        ALWAYS_INHERENT
    }

    /**
     * Generates up to [maxVariants] distinct phonetic transliteration candidates for a Roman word
     * by exploring vowel/consonant ambiguities (e.g. শ/ষ/স, চ/ছ, ভ/ব, ি/ী, ঁ, terminal ো vs inherent অ).
     */
    fun generatePhoneticVariants(
        rawWord: String,
        useBengaliNumerals: Boolean = false,
        maxVariants: Int = 6
    ): List<String> {
        if (rawWord.isBlank()) return emptyList()
        val cacheKey = rawWord + "|" + useBengaliNumerals + "|" + maxVariants
        synchronized(variantsCache) {
            variantsCache[cacheKey]?.let { return it }
        }

        val results = LinkedHashSet<String>()

        // 1. Primary smart transliteration
        val primary = transliterateWordPrimary(rawWord, useBengaliNumerals, TerminalOMode.SMART_DEFAULT)
        if (primary.isNotEmpty()) results.add(primary)

        // 2. Terminal 'o' variations (e.g. "bhalo" -> "ভালো" and "ভাল", "dhormo" -> "ধর্ম" and "ধর্মো")
        if (rawWord.endsWith("o", ignoreCase = true) && rawWord.length >= 3) {
            results.add(transliterateWordPrimary(rawWord, useBengaliNumerals, TerminalOMode.ALWAYS_INHERENT))
            results.add(transliterateWordPrimary(rawWord, useBengaliNumerals, TerminalOMode.ALWAYS_O_KAR))
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
            substitutionSeeds.add(rawWord.replace(Regex("s", RegexOption.IGNORE_CASE), "chh"))
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
        if ("st" in lower && "sth" !in lower && "sht" !in lower) {
            substitutionSeeds.add(rawWord.replace(Regex("st", RegexOption.IGNORE_CASE), "sht"))
            substitutionSeeds.add(rawWord.replace(Regex("st", RegexOption.IGNORE_CASE), "sth"))
        }
        if ("kk" in lower) {
            substitutionSeeds.add(rawWord.replace(Regex("kk", RegexOption.IGNORE_CASE), "kY"))
            substitutionSeeds.add(rawWord.replace(Regex("kk", RegexOption.IGNORE_CASE), "kkh"))
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

        val finalVariants = results.take(maxVariants)
        synchronized(variantsCache) {
            variantsCache[cacheKey] = finalVariants
        }
        return finalVariants
    }

    fun clearCaches() {
        synchronized(primaryCache) { primaryCache.clear() }
        synchronized(variantsCache) { variantsCache.clear() }
    }

    private fun normalizeBanglishWord(raw: String): String {
        var w = raw
        if (w.length > 1 && w[0].isUpperCase() && w.substring(1).all { it.isLowerCase() }) {
            w = w[0].lowercaseChar() + w.substring(1)
        }
        val lower = w.lowercase()
        // Context-aware Avro-style phonetic normalizations for natural Banglish spelling
        when (lower) {
            "krishi" -> return "krrishi"
            "krishok" -> return "krrishok"
            "kriti" -> return "krriti"
            "kritoggo" -> return "krritoggo"
            "bektigoto" -> return "byoktigot"
            "bekti" -> return "byokti"
            "bidyut" -> return "bidyut^"
            "utshob", "utsob" -> return "ut^shob"
            "chad", "chaad" -> return "cha^d"
            "ha", "haa", "hya" -> return "hya^"
            "bakko" -> return "bakYo"
            "onko" -> return "onk"
            "dhormo" -> return "dhorm"
            "kormo" -> return "korm"
            "purkaystha" -> return "purkayosth"
        }
        w = w.replace("bektigoto", "byoktigot")
            .replace("bhalob", "bhalOb")
            .replace("valob", "bhalOb")
            .replace("manob", "manOb")
            .replace("dhonnobad", "dhonyobad")
        if (w.startsWith("krish", ignoreCase = true)) {
            w = "krrish" + w.substring(5)
        }
        return w
    }

    private fun shouldUseTerminalOKar(word: String): Boolean {
        val lower = word.lowercase()
        val inherentEndings = setOf(
            "bonto", "shonto", "ponto", "shagotom", "got", "goto", "dhormo", "kormo",
            "shorto", "chorto", "onko", "bakko", "bakyo", "rokto", "bhokto", "jukto",
            "mukto", "shuddho", "buddho", "juddho", "shundor", "anondo", "pochondo",
            "shomporko", "somporko", "shompurno", "sompurno", "kosto", "koshto",
            "nosto", "noshto", "shastho", "sthan", "bostu", "besto", "byasto",
            "shanto", "anonto", "pranto", "uttor", "gurutto", "podmo", "chihno",
            "golpo", "prokolpo", "olpo", "bikolpo"
        )
        if (inherentEndings.any { lower.endsWith(it) }) return false
        return true
    }

    private fun shouldJoinWithHasanta(word: String, index: Int, nextPattern: String): Boolean {
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
        for (token in CONSONANT_TOKENS) {
            if (s.startsWith(token.pattern, ignoreCase = false)) return token
        }
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
