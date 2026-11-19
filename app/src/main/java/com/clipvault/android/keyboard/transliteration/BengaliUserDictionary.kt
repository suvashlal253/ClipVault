package com.clipvault.android.keyboard.transliteration

import android.content.Context
import android.content.SharedPreferences

/**
 * Offline-first Personal Dictionary & Custom Text Shortcut manager (v1.7.1).
 * - Starts empty on fresh installs (no demo seeding).
 * - Performs genuine, idempotent, non-destructive migration from v1.6.0 SharedPreferences
 *   (filtering out unmodified v1.6.0 auto-seeded demo entries when seeded_v160_defaults was set,
 *   while preserving all user-created or user-modified entries and never overwriting newer v1.7.1 data).
 * - Keeps adaptive candidate ranking strictly separate from manually saved Personal Dictionary entries (Fix 2).
 */
class BengaliUserDictionary(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Manually saved Personal Dictionary entries: romanKey (lowercase) -> PersonalDictionaryEntry
    private val personalEntries = LinkedHashMap<String, PersonalDictionaryEntry>()

    // User-defined custom text shortcuts: trigger (lowercase) -> CustomShortcutEntry
    private val customShortcuts = LinkedHashMap<String, CustomShortcutEntry>()

    // Separate bounded adaptive selection frequency map: "romanKey|candidateWord" -> count (Fix 2)
    // Never pollutes personalEntries!
    private val adaptiveSelectionCounts = object : LinkedHashMap<String, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean {
            return size > 120
        }
    }

    // Recently committed words for quick recall
    private val recentWords = ArrayDeque<String>()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val sp = prefs ?: return
        migrateFromLegacyPreferencesIfNeeded(sp)
        val rawV171Words = sp.getString(KEY_PERSONAL_WORDS_V171, null)
        val rawV171Shortcuts = sp.getString(KEY_CUSTOM_SHORTCUTS_V171, null)
        val rawAdaptive = sp.getString(KEY_ADAPTIVE_RANKING_V172, null)
        loadFromSerializedStrings(
            rawPersonal = rawV171Words,
            rawShortcuts = rawV171Shortcuts
        )
        loadAdaptiveRankingFromTsv(rawAdaptive)
    }

    /**
     * Performs genuine, idempotent, non-destructive migration from v1.6.0 SharedPreferences (Fix 3/5).
     * - Reads legacy keys [KEY_LEGACY_PERSONAL_WORDS_V160], [KEY_LEGACY_SHORTCUTS_V160], and [KEY_LEGACY_SEEDED_V160].
     * - Filters out unmodified v1.6.0 demo entries only if [KEY_LEGACY_SEEDED_V160] was true and frequency <= 1.
     * - Merges valid user entries into [KEY_PERSONAL_WORDS_V171] and [KEY_CUSTOM_SHORTCUTS_V171] without
     *   overwriting newer v1.7.1/v1.7.2 entries and without deleting legacy keys.
     * - Verifies serialized round-trip integrity before marking migration complete.
     */
    @Synchronized
    fun migrateFromLegacyPreferencesIfNeeded(sp: SharedPreferences): Boolean {
        val alreadyMigrated = sp.getBoolean(KEY_MIGRATION_V171_COMPLETE, false)
        val existingV171Words = sp.getString(KEY_PERSONAL_WORDS_V171, null)
        val existingV171Shortcuts = sp.getString(KEY_CUSTOM_SHORTCUTS_V171, null)

        // Load any existing v1.7.1/v1.7.2 entries first so newer entries are never overwritten
        loadFromSerializedStrings(existingV171Words, existingV171Shortcuts)
        loadAdaptiveRankingFromTsv(sp.getString(KEY_ADAPTIVE_RANKING_V172, null))

        if (alreadyMigrated) return false

        val legacyWordsRaw = sp.getString(KEY_LEGACY_PERSONAL_WORDS_V160, null)
        val legacyShortcutsRaw = sp.getString(KEY_LEGACY_SHORTCUTS_V160, null)
        val legacyWasDemoSeeded = sp.getBoolean(KEY_LEGACY_SEEDED_V160, false)

        migrateFromLegacyRawData(
            legacyPersonalTsv = legacyWordsRaw,
            legacyShortcutsTsv = legacyShortcutsRaw,
            legacyWasDemoSeeded = legacyWasDemoSeeded,
            existingNewPersonalTsv = existingV171Words,
            existingNewShortcutsTsv = existingV171Shortcuts
        )

        val personalSerialized = serializePersonalWords()
        val shortcutsSerialized = serializeShortcuts()

        // Verify migration round-trip before marking complete; never delete legacy keys
        val verifiedWords = parsePersonalWordsTsv(personalSerialized)
        val verifiedShortcuts = parseShortcutsTsv(shortcutsSerialized)
        if (verifiedWords.size != personalEntries.size || verifiedShortcuts.size != customShortcuts.size) {
            return false
        }

        // Non-destructively write canonical keys and mark migration verified (legacy keys remain untouched)
        sp.edit()
            .putString(KEY_PERSONAL_WORDS_V171, personalSerialized)
            .putString(KEY_CUSTOM_SHORTCUTS_V171, shortcutsSerialized)
            .putBoolean(KEY_MIGRATION_V171_COMPLETE, true)
            .apply()

        return true
    }

    /**
     * Pure, testable migration method that migrates legacy v1.6.0 TSV data while preserving
     * newer v1.7.1 entries and filtering unmodified v1.6.0 demo seeds when [legacyWasDemoSeeded] is true (Fix 5).
     */
    @Synchronized
    fun migrateFromLegacyRawData(
        legacyPersonalTsv: String?,
        legacyShortcutsTsv: String?,
        legacyWasDemoSeeded: Boolean = false,
        existingNewPersonalTsv: String? = null,
        existingNewShortcutsTsv: String? = null
    ) {
        val newerWords = parsePersonalWordsTsv(existingNewPersonalTsv)
        val newerShortcuts = parseShortcutsTsv(existingNewShortcutsTsv)

        val legacyWords = parsePersonalWordsTsv(legacyPersonalTsv)
        val legacyShortcuts = parseShortcutsTsv(legacyShortcutsTsv)

        personalEntries.clear()
        customShortcuts.clear()

        // 1. Migrate valid legacy personal words (skipping unmodified v1.6.0 demo seeds if legacyWasDemoSeeded)
        for ((key, legacyEntry) in legacyWords) {
            val isUnmodifiedDemoSeed = legacyWasDemoSeeded &&
                V160_SEEDED_DEMO_WORDS[key] == legacyEntry.preferredWord &&
                legacyEntry.frequency <= 1
            if (!isUnmodifiedDemoSeed) {
                personalEntries[key] = legacyEntry
            }
        }

        // 2. Overlay newer v1.7.1 personal words so older legacy data never overwrites newer entries
        for ((key, newEntry) in newerWords) {
            val existing = personalEntries[key]
            if (existing == null || newEntry.updatedAt >= existing.updatedAt) {
                personalEntries[key] = newEntry
            }
        }

        // 3. Migrate valid legacy shortcuts (skipping unmodified v1.6.0 demo shortcuts if legacyWasDemoSeeded)
        for ((key, legacySc) in legacyShortcuts) {
            val isUnmodifiedDemoShortcut = legacyWasDemoSeeded &&
                V160_SEEDED_DEMO_SHORTCUTS[key] == legacySc.expansion
            if (!isUnmodifiedDemoShortcut) {
                customShortcuts[key] = legacySc
            }
        }

        // 4. Overlay newer v1.7.1 shortcuts so older legacy data never overwrites newer entries
        for ((key, newSc) in newerShortcuts) {
            customShortcuts[key] = newSc
        }
    }

    private fun parsePersonalWordsTsv(rawPersonal: String?): LinkedHashMap<String, PersonalDictionaryEntry> {
        val result = LinkedHashMap<String, PersonalDictionaryEntry>()
        if (rawPersonal.isNullOrBlank()) return result
        rawPersonal.split("\n").forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isNotEmpty()) {
                val parts = line.split("\t")
                if (parts.size >= 2) {
                    val key = parts[0].trim().lowercase()
                    val word = parts[1].trim()
                    val lang = parts.getOrNull(2)?.trim()?.ifEmpty { "bn" } ?: "bn"
                    val freq = parts.getOrNull(3)?.trim()?.toIntOrNull() ?: 1
                    val updated = parts.getOrNull(4)?.trim()?.toLongOrNull() ?: 0L
                    if (key.isNotEmpty() && word.isNotEmpty()) {
                        result[key] = PersonalDictionaryEntry(key, word, lang, freq, updated)
                    }
                }
            }
        }
        return result
    }

    private fun parseShortcutsTsv(rawShortcuts: String?): LinkedHashMap<String, CustomShortcutEntry> {
        val result = LinkedHashMap<String, CustomShortcutEntry>()
        if (rawShortcuts.isNullOrBlank()) return result
        rawShortcuts.split("\n").forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isNotEmpty()) {
                val parts = line.split("\t")
                if (parts.size >= 2) {
                    val trigger = normalizePhraseKey(parts[0])
                    val expansion = parts[1].trim()
                    if (trigger.isNotEmpty() && expansion.isNotEmpty()) {
                        result[trigger.lowercase()] = CustomShortcutEntry(trigger, expansion)
                    }
                }
            }
        }
        return result
    }

    /**
     * Loads saved personal words and custom shortcuts from TSV strings.
     */
    @Synchronized
    fun loadFromSerializedStrings(rawPersonal: String?, rawShortcuts: String?) {
        if (!rawPersonal.isNullOrBlank()) {
            personalEntries.clear()
            personalEntries.putAll(parsePersonalWordsTsv(rawPersonal))
        }
        if (!rawShortcuts.isNullOrBlank()) {
            customShortcuts.clear()
            customShortcuts.putAll(parseShortcutsTsv(rawShortcuts))
        }
    }

    @Synchronized
    fun loadAdaptiveRankingFromTsv(rawAdaptive: String?) {
        if (rawAdaptive.isNullOrBlank()) return
        adaptiveSelectionCounts.clear()
        rawAdaptive.split("\n").forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isNotEmpty()) {
                val parts = line.split("\t")
                if (parts.size >= 2) {
                    val compositeKey = parts[0].trim()
                    val count = parts[1].trim().toIntOrNull() ?: 0
                    if (compositeKey.contains("|") && count > 0) {
                        adaptiveSelectionCounts[compositeKey] = count.coerceAtMost(20)
                    }
                }
            }
        }
    }

    @Synchronized
    fun serializePersonalWords(): String {
        return personalEntries.values.joinToString("\n") { entry ->
            entry.romanKey + "\t" + entry.preferredWord + "\t" + entry.languageCode + "\t" + entry.frequency + "\t" + entry.updatedAt
        }
    }

    @Synchronized
    fun serializeShortcuts(): String {
        return customShortcuts.values.joinToString("\n") { sc ->
            sc.trigger + "\t" + sc.expansion
        }
    }

    @Synchronized
    fun serializeAdaptiveRanking(): String {
        return adaptiveSelectionCounts.entries.joinToString("\n") { (compositeKey, count) ->
            compositeKey + "\t" + count
        }
    }

    private fun persistToPrefs() {
        val sp = prefs ?: return
        val personalSerialized = serializePersonalWords()
        val shortcutsSerialized = serializeShortcuts()
        val adaptiveSerialized = serializeAdaptiveRanking()
        sp.edit()
            .putString(KEY_PERSONAL_WORDS_V171, personalSerialized)
            .putString(KEY_CUSTOM_SHORTCUTS_V171, shortcutsSerialized)
            .putString(KEY_ADAPTIVE_RANKING_V172, adaptiveSerialized)
            .putBoolean(KEY_MIGRATION_V171_COMPLETE, true)
            .apply()
    }

    /**
     * Explicitly adds or updates a personal dictionary mapping (e.g., "suvash" -> "সুভাষ").
     * Only invoked by explicit user actions (+ Save button or Personal Dictionary Settings dialog) —
     * never called automatically when tapping a suggestion chip (Fix 2).
     */
    @Synchronized
    fun addOrUpdateWord(
        romanKey: String,
        preferredWord: String,
        languageMode: KeyboardLanguageMode = KeyboardLanguageMode.BANGLA_PHONETIC,
        explicitUpdatedAt: Long = System.currentTimeMillis()
    ) {
        val cleanKey = romanKey.trim().lowercase()
        val cleanWord = preferredWord.trim()
        if (cleanKey.isEmpty() || cleanWord.isEmpty()) return

        val existing = personalEntries[cleanKey]
        val nextFreq = if (existing != null && existing.preferredWord == cleanWord) {
            existing.frequency + 1
        } else {
            (existing?.frequency ?: 1) + 1
        }
        personalEntries[cleanKey] = PersonalDictionaryEntry(
            romanKey = cleanKey,
            preferredWord = cleanWord,
            languageCode = languageMode.prefCode,
            frequency = nextFreq,
            updatedAt = explicitUpdatedAt
        )
        recordRecentWord(cleanWord)
        persistToPrefs()
    }

    /**
     * Records an adaptive candidate selection boost in a separate bounded structure without
     * creating a Personal Dictionary entry (Fix 2 & Requirement 5).
     */
    @Synchronized
    fun recordAdaptiveCandidateSelection(romanKey: String, selectedWord: String) {
        val cleanKey = romanKey.trim().lowercase()
        val cleanWord = selectedWord.trim()
        if (cleanKey.isEmpty() || cleanWord.isEmpty()) return
        val compositeKey = cleanKey + "|" + cleanWord
        val current = adaptiveSelectionCounts[compositeKey] ?: 0
        adaptiveSelectionCounts[compositeKey] = (current + 1).coerceAtMost(20)
        recordRecentWord(cleanWord)
        persistToPrefs()
    }

    /**
     * Returns the adaptive ranking score boost for [romanKey] -> [candidateWord] (Fix 2).
     */
    @Synchronized
    fun getAdaptiveSelectionBoost(romanKey: String, candidateWord: String): Int {
        val cleanKey = romanKey.trim().lowercase()
        val cleanWord = candidateWord.trim()
        if (cleanKey.isEmpty() || cleanWord.isEmpty()) return 0
        val count = adaptiveSelectionCounts[cleanKey + "|" + cleanWord] ?: 0
        return count * 18
    }

    /**
     * Returns the current number of bounded adaptive learning entries (max 120).
     */
    @Synchronized
    fun getAdaptiveLearningEntryCount(): Int = adaptiveSelectionCounts.size

    /**
     * Clears persisted adaptive candidate ranking without touching manually saved Personal Dictionary entries.
     */
    @Synchronized
    fun clearAdaptiveLearning() {
        adaptiveSelectionCounts.clear()
        recentWords.clear()
        persistToPrefs()
    }

    /**
     * Edits an existing saved word in the personal dictionary.
     */
    @Synchronized
    fun editWord(oldRomanKey: String, newRomanKey: String, newPreferredWord: String) {
        val oldClean = oldRomanKey.trim().lowercase()
        val newClean = newRomanKey.trim().lowercase()
        val cleanWord = newPreferredWord.trim()
        if (newClean.isEmpty() || cleanWord.isEmpty()) return

        val prevFreq = personalEntries.remove(oldClean)?.frequency ?: 2
        personalEntries[newClean] = PersonalDictionaryEntry(
            romanKey = newClean,
            preferredWord = cleanWord,
            frequency = prevFreq,
            updatedAt = System.currentTimeMillis()
        )
        persistToPrefs()
    }

    /**
     * Deletes a saved word from the personal dictionary.
     */
    @Synchronized
    fun deleteWord(romanKey: String): Boolean {
        val removed = personalEntries.remove(romanKey.trim().lowercase()) != null
        if (removed) persistToPrefs()
        return removed
    }

    /**
     * Resets the personal dictionary to empty.
     */
    @Synchronized
    fun resetPersonalDictionary(keepDefaults: Boolean = false) {
        personalEntries.clear()
        adaptiveSelectionCounts.clear()
        recentWords.clear()
        persistToPrefs()
    }

    /**
     * Looks up a user-preferred word for the given [romanKey].
     */
    @Synchronized
    fun lookupPreferredWord(romanKey: String): PersonalDictionaryEntry? {
        return personalEntries[romanKey.trim().lowercase()]
    }

    /**
     * Convenience helper returning the preferred word string for [romanKey], if saved.
     */
    @Synchronized
    fun lookupPersonalWord(romanKey: String): String? {
        return lookupPreferredWord(romanKey)?.preferredWord
    }

    /**
     * Returns all saved personal dictionary entries sorted by frequency and recency.
     */
    @Synchronized
    fun getAllPersonalWords(): List<PersonalDictionaryEntry> {
        return personalEntries.values.sortedByDescending { it.frequency * 1000L + (it.updatedAt % 1000L) }
    }

    /**
     * Searches saved personal dictionary entries by key or preferred word (Feature 8).
     */
    @Synchronized
    fun searchPersonalWords(query: String): List<PersonalDictionaryEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return getAllPersonalWords()
        return getAllPersonalWords().filter {
            it.romanKey.lowercase().contains(q) || it.preferredWord.lowercase().contains(q)
        }
    }

    /**
     * Adds or updates a custom text shortcut or Bengali phrase (e.g., "brb" -> "be right back",
     * "amar nam" -> "আমার নাম সুভাষ").
     */
    @Synchronized
    fun addOrUpdateShortcut(
        trigger: String,
        expansion: String,
        languageMode: KeyboardLanguageMode = KeyboardLanguageMode.BANGLA_PHONETIC
    ) {
        val cleanTrigger = normalizePhraseKey(trigger)
        val cleanExpansion = expansion.trim()
        if (cleanTrigger.isEmpty() || cleanExpansion.isEmpty()) return
        customShortcuts[cleanTrigger.lowercase()] = CustomShortcutEntry(cleanTrigger, cleanExpansion)
        persistToPrefs()
    }

    /**
     * Edits an existing custom shortcut.
     */
    @Synchronized
    fun editShortcut(oldTrigger: String, newTrigger: String, newExpansion: String) {
        val oldClean = normalizePhraseKey(oldTrigger).lowercase()
        val newClean = normalizePhraseKey(newTrigger)
        val cleanExp = newExpansion.trim()
        if (newClean.isEmpty() || cleanExp.isEmpty()) return
        customShortcuts.remove(oldClean)
        customShortcuts[newClean.lowercase()] = CustomShortcutEntry(newClean, cleanExp)
        persistToPrefs()
    }

    /**
     * Deletes a custom text shortcut.
     */
    @Synchronized
    fun deleteShortcut(trigger: String): Boolean {
        val removed = customShortcuts.remove(normalizePhraseKey(trigger).lowercase()) != null
        if (removed) persistToPrefs()
        return removed
    }

    /**
     * Looks up a custom shortcut expansion by single-word trigger or phrase (case-insensitive).
     */
    @Synchronized
    fun lookupShortcut(triggerOrPhrase: String): CustomShortcutEntry? {
        return customShortcuts[normalizePhraseKey(triggerOrPhrase).lowercase()]
    }

    /**
     * Returns all multi-word phrase shortcuts ordered longest-trigger-first (by word count, then character length)
     * for deterministic longest-match-first matching (Part 2 #3).
     */
    @Synchronized
    fun getMultiWordShortcutsLongestFirst(): List<CustomShortcutEntry> {
        return customShortcuts.values
            .filter { it.trigger.contains(" ") }
            .sortedWith(
                compareByDescending<CustomShortcutEntry> { it.trigger.split(" ").size }
                    .thenByDescending { it.trigger.length }
            )
    }

    /**
     * Returns all configured custom shortcuts.
     */
    @Synchronized
    fun getAllShortcuts(): List<CustomShortcutEntry> {
        return customShortcuts.values.toList()
    }

    /**
     * Searches custom shortcuts by trigger or expansion (Feature 8).
     */
    @Synchronized
    fun searchShortcuts(query: String): List<CustomShortcutEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return getAllShortcuts()
        return getAllShortcuts().filter {
            it.trigger.lowercase().contains(q) || it.expansion.lowercase().contains(q)
        }
    }

    @Synchronized
    fun recordRecentWord(word: String) {
        val clean = word.trim()
        if (clean.isEmpty()) return
        recentWords.remove(clean)
        recentWords.addFirst(clean)
        while (recentWords.size > 15) {
            recentWords.removeLast()
        }
    }

    @Synchronized
    fun getRecentWords(limit: Int = 5): List<String> {
        return recentWords.take(limit)
    }

    private fun normalizePhraseKey(raw: String): String {
        return raw.trim().replace(Regex("\\s+"), " ")
    }

    companion object {
        const val PREFS_NAME = "clipvault_personal_dictionary"
        const val MAX_ADAPTIVE_ENTRIES = 120

        // Legacy v1.6.0 SharedPreferences keys (preserved non-destructively during migration)
        const val KEY_LEGACY_PERSONAL_WORDS_V160 = "personal_words_tsv"
        const val KEY_LEGACY_SHORTCUTS_V160 = "custom_shortcuts_tsv"
        const val KEY_LEGACY_SEEDED_V160 = "seeded_v160_defaults"

        // Canonical v1.7.1/v1.7.2 SharedPreferences keys
        const val KEY_PERSONAL_WORDS_V171 = "personal_words_v171_tsv"
        const val KEY_CUSTOM_SHORTCUTS_V171 = "custom_shortcuts_v171_tsv"
        const val KEY_ADAPTIVE_RANKING_V172 = "adaptive_ranking_v172_tsv"
        const val KEY_MIGRATION_V171_COMPLETE = "migration_v171_complete"

        // Exact demo entries that v1.6.0 seeded when seeded_v160_defaults == true
        private val V160_SEEDED_DEMO_WORDS = mapOf(
            "suvash" to "সুভাষ",
            "bangladesh" to "বাংলাদেশ",
            "clipvault" to "ক্লিপভল্ট",
            "dhonnobad" to "ধন্যবাদ",
            "bhalobashi" to "ভালোবাসি",
            "shagotom" to "স্বাগতম"
        )

        private val V160_SEEDED_DEMO_SHORTCUTS = mapOf(
            "brb" to "be right back",
            "omw" to "on my way",
            "ty" to "thank you",
            "assalamu" to "আসসালামু আলাইকুম",
            "jzk" to "জাযাকাল্লাহু খাইরান"
        )
    }
}
