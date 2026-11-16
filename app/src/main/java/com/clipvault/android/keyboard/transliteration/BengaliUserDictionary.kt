package com.clipvault.android.keyboard.transliteration

import android.content.Context
import android.content.SharedPreferences

/**
 * Offline-first Personal Dictionary & Custom Text Shortcut manager (v1.7.0).
 * Starts empty on fresh installs (Part 2 #4), preserves existing v1.6.0 user-saved entries,
 * and supports longest-match-first multi-word phrase expansion (Part 2 #3).
 */
class BengaliUserDictionary(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // In-memory cache of romanKey (lowercase) -> PersonalDictionaryEntry
    private val personalEntries = LinkedHashMap<String, PersonalDictionaryEntry>()

    // In-memory cache of trigger (lowercase) -> CustomShortcutEntry
    private val customShortcuts = LinkedHashMap<String, CustomShortcutEntry>()

    // Recently committed words for quick recall
    private val recentWords = ArrayDeque<String>()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val sp = prefs ?: return
        loadFromSerializedStrings(
            rawPersonal = sp.getString(KEY_PERSONAL_WORDS, null),
            rawShortcuts = sp.getString(KEY_CUSTOM_SHORTCUTS, null)
        )
    }

    /**
     * Loads saved personal words and custom shortcuts from TSV strings (used by SharedPreferences
     * persistence and v1.6.0 -> v1.7.0 migration verification).
     */
    @Synchronized
    fun loadFromSerializedStrings(rawPersonal: String?, rawShortcuts: String?) {
        if (!rawPersonal.isNullOrBlank()) {
            personalEntries.clear()
            rawPersonal.split("\n").forEach { line ->
                val parts = line.split("\t")
                if (parts.size >= 4) {
                    val key = parts[0].trim().lowercase()
                    val word = parts[1].trim()
                    val lang = parts[2].trim()
                    val freq = parts[3].toIntOrNull() ?: 1
                    if (key.isNotEmpty() && word.isNotEmpty()) {
                        personalEntries[key] = PersonalDictionaryEntry(key, word, lang, freq)
                    }
                }
            }
        }

        if (!rawShortcuts.isNullOrBlank()) {
            customShortcuts.clear()
            rawShortcuts.split("\n").forEach { line ->
                val parts = line.split("\t")
                if (parts.size >= 2) {
                    val trigger = normalizePhraseKey(parts[0])
                    val expansion = parts[1].trim()
                    if (trigger.isNotEmpty() && expansion.isNotEmpty()) {
                        customShortcuts[trigger.lowercase()] = CustomShortcutEntry(trigger, expansion)
                    }
                }
            }
        }
    }

    private fun persistToPrefs() {
        val sp = prefs ?: return
        val personalSerialized = personalEntries.values.joinToString("\n") { entry ->
            entry.romanKey + "\t" + entry.preferredWord + "\t" + entry.languageCode + "\t" + entry.frequency
        }
        val shortcutsSerialized = customShortcuts.values.joinToString("\n") { sc ->
            sc.trigger + "\t" + sc.expansion
        }
        sp.edit()
            .putString(KEY_PERSONAL_WORDS, personalSerialized)
            .putString(KEY_CUSTOM_SHORTCUTS, shortcutsSerialized)
            .apply()
    }

    /**
     * Adds or updates a personal dictionary mapping (e.g., "suvash" -> "সুভাষ").
     */
    @Synchronized
    fun addOrUpdateWord(
        romanKey: String,
        preferredWord: String,
        languageMode: KeyboardLanguageMode = KeyboardLanguageMode.BANGLA_PHONETIC
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
            updatedAt = System.currentTimeMillis()
        )
        recordRecentWord(cleanWord)
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
        private const val KEY_PERSONAL_WORDS = "personal_words_tsv"
        private const val KEY_CUSTOM_SHORTCUTS = "custom_shortcuts_tsv"
    }
}
