package com.clipvault.android

import android.text.InputType
import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.ime.ClipVaultInputMethodService
import com.clipvault.android.keyboard.transliteration.BengaliFontStyle
import com.clipvault.android.keyboard.transliteration.BengaliPhoneticEngine
import com.clipvault.android.keyboard.transliteration.BengaliSuggestionEngine
import com.clipvault.android.keyboard.transliteration.BengaliTransliterationRules
import com.clipvault.android.keyboard.transliteration.BengaliUnicodeRenderValidator
import com.clipvault.android.keyboard.transliteration.BengaliUserDictionary
import com.clipvault.android.keyboard.transliteration.BengaliWordDictionary
import com.clipvault.android.keyboard.transliteration.CandidateSource
import com.clipvault.android.keyboard.transliteration.KeySizePreset
import com.clipvault.android.keyboard.transliteration.KeyboardLanguageMode
import com.clipvault.android.keyboard.transliteration.KeyboardUndoRedoManager
import com.clipvault.android.keyboard.transliteration.OneHandedMode
import com.clipvault.android.keyboard.transliteration.TokenLanguageClassification
import com.clipvault.android.service.ClipboardServiceHelper
import com.clipvault.android.util.AccessibilityPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardArchitectureTest {

    private class FakeClipDao(initialClips: List<ClipEntity> = emptyList()) : ClipDao {
        private val items = mutableListOf<ClipEntity>().apply { addAll(initialClips) }
        private val flow = MutableStateFlow<List<ClipEntity>>(items.toList())
        var failNextInsert: Boolean = false

        fun snapshot(): List<ClipEntity> = synchronized(items) {
            items.sortedWith(
                compareByDescending<ClipEntity> { it.isPinned }
                    .thenByDescending { it.createdAt }
            )
        }

        override suspend fun insertClip(clip: ClipEntity): Long {
            synchronized(items) {
                if (failNextInsert) {
                    failNextInsert = false
                    throw IllegalStateException("Simulated Room SQLite insertion failure")
                }
                items.removeAll { it.id == clip.id }
                items.add(clip)
                flow.value = snapshot()
                return items.size.toLong()
            }
        }

        override suspend fun insertClips(clips: List<ClipEntity>) {
            synchronized(items) {
                items.addAll(clips)
                flow.value = snapshot()
            }
        }

        override suspend fun updateClip(clip: ClipEntity) {}
        override suspend fun deleteClip(clip: ClipEntity) {}
        override suspend fun deleteClipById(clipId: String) {}
        override suspend fun deleteClipsByIds(clipIds: List<String>) {}
        override fun getAllClipsFlow(): Flow<List<ClipEntity>> = flow
        override suspend fun getAllClipsIncludingVaultSync(): List<ClipEntity> = snapshot()
        override fun getVaultClipsFlow(): Flow<List<ClipEntity>> = flow.map { list -> list.filter { it.isVaulted } }
        override fun getPinnedClipsFlow(): Flow<List<ClipEntity>> = flow.map { list -> list.filter { it.isPinned } }
        override fun getClipsByCategoryFlow(category: ClipCategory): Flow<List<ClipEntity>> =
            flow.map { list -> list.filter { it.category == category } }
        override fun searchClips(query: String): Flow<List<ClipEntity>> = flow
        override suspend fun findClipByContent(content: String): ClipEntity? =
            synchronized(items) { items.firstOrNull { it.content == content } }
        override suspend fun updatePinStatus(clipId: String, isPinned: Boolean) {}
        override suspend fun updateClipTags(clipId: String, tags: List<String>) {}
        override suspend fun getClipById(clipId: String): ClipEntity? =
            synchronized(items) { items.firstOrNull { it.id == clipId } }
        override suspend fun updateClipContent(clipId: String, newContent: String, charCount: Int, category: ClipCategory) {}
        override suspend fun updateEncryptedClipContent(clipId: String, encryptedContent: String, iv: String, charCount: Int, category: ClipCategory) {}
        override suspend fun updateVaultStatus(clipId: String, isVaulted: Boolean, newContent: String, iv: String?) {}
        override suspend fun getLatestClipContent(): String? =
            synchronized(items) {
                items.filter { !it.isVaulted }.maxByOrNull { it.createdAt }?.content
            }
        override suspend fun clearAllUnpinned() {}
        override suspend fun clearAllHistory() {}
        override suspend fun rebuildFtsIndex() {}
        override suspend fun pruneOldClips(cutoffTimestamp: Long): Int = 0
        override fun getClipCountFlow(): Flow<Int> = flow.map { it.size }
        override fun getVaultCountFlow(): Flow<Int> = flow.map { list -> list.count { it.isVaulted } }
    }

    // 1. Sequential queue insertion
    @Test
    fun testSequentialQueueInsertion() = runBlocking {
        val dao = FakeClipDao()
        val helper = ClipboardServiceHelper(dao, Dispatchers.Unconfined)

        helper.processQueuedItem("First Clip", "TestSource")
        helper.processQueuedItem("Second Clip", "TestSource")

        val saved = dao.snapshot()
        assertEquals(2, saved.size)
        assertEquals("Second Clip", saved[0].content)
        assertEquals("First Clip", saved[1].content)
        assertTrue(saved[0].createdAt > saved[1].createdAt)
    }

    // 2. Rapid A -> B -> C capture events
    @Test
    fun testRapidABCQueueSubmissionOrder() = runBlocking {
        val dao = FakeClipDao()
        val helper = ClipboardServiceHelper(dao, Dispatchers.Unconfined)

        assertTrue(helper.enqueueClip("Text A", "App1"))
        assertTrue(helper.enqueueClip("Text B", "App2"))
        assertTrue(helper.enqueueClip("Text C", "App3"))

        delay(80)
        val saved = dao.snapshot()
        assertEquals(3, saved.size)
        assertEquals(listOf("Text C", "Text B", "Text A"), saved.map { it.content })
        assertTrue(saved[0].createdAt > saved[1].createdAt)
        assertTrue(saved[1].createdAt > saved[2].createdAt)
    }

    // 3. Duplicate consecutive clipboard events
    @Test
    fun testDuplicateConsecutiveClipsIgnored() = runBlocking {
        val dao = FakeClipDao()
        val helper = ClipboardServiceHelper(dao, Dispatchers.Unconfined)

        helper.processQueuedItem("Same Text", "Source1")
        helper.processQueuedItem("Same Text", "Source1")
        helper.processQueuedItem("   Same Text   ", "Source2")

        val saved = dao.snapshot()
        assertEquals(1, saved.size)
        assertEquals("Same Text", saved[0].content)
    }

    // 4. Re-copying the same text after another clip (A -> B -> A)
    @Test
    fun testReCopyingSameTextAfterDifferentClipIsSaved() = runBlocking {
        val dao = FakeClipDao()
        val helper = ClipboardServiceHelper(dao, Dispatchers.Unconfined)

        helper.processQueuedItem("Clip Alpha", "Source")
        helper.processQueuedItem("Clip Beta", "Source")
        helper.processQueuedItem("Clip Alpha", "Source")

        val saved = dao.snapshot()
        assertEquals(3, saved.size)
        assertEquals(listOf("Clip Alpha", "Clip Beta", "Clip Alpha"), saved.map { it.content })
    }

    // 5. Database insertion failure and retry behavior
    @Test
    fun testDatabaseInsertionFailureDoesNotMarkProcessedAndAllowsRetry() = runBlocking {
        val dao = FakeClipDao()
        val helper = ClipboardServiceHelper(dao, Dispatchers.Unconfined)

        dao.failNextInsert = true
        val firstAttempt = helper.processQueuedItem("Important Clip", "Source")
        assertNull(firstAttempt)
        assertNull(helper.getLastProcessedText())
        assertEquals(0, dao.snapshot().size)

        val retryAttempt = helper.processQueuedItem("Important Clip", "Source")
        assertNotNull(retryAttempt)
        assertEquals("Important Clip", helper.getLastProcessedText())
        assertEquals(1, dao.snapshot().size)
    }

    // 6. Multiple capture sources using the same queue
    @Test
    fun testMultipleCaptureSourcesUseUnifiedQueue() = runBlocking {
        val dao = FakeClipDao()
        val helper = ClipboardServiceHelper(dao, Dispatchers.Unconfined)

        helper.enqueueClip("From Foreground", "Foreground Sync")
        helper.enqueueClip("From Accessibility", "ClipAccessibilityService")
        helper.enqueueClip("From Optional IME", "ClipVault IME")

        delay(80)
        val saved = dao.snapshot()
        assertEquals(3, saved.size)
        assertEquals("ClipVault IME", saved[0].sourceApp)
        assertEquals("ClipAccessibilityService", saved[1].sourceApp)
        assertEquals("Foreground Sync", saved[2].sourceApp)
    }

    // 7. IME lifecycle listener registration and cleanup
    @Test
    fun testImeListenerRegistrationLifecycleGuard() {
        var isRegistered = false
        if (ClipVaultInputMethodService.ListenerRegistrationController.markRegistered(isRegistered)) {
            isRegistered = true
        }
        assertTrue(isRegistered)
        val duplicateAllowed = ClipVaultInputMethodService.ListenerRegistrationController.markRegistered(isRegistered)
        assertFalse(duplicateAllowed)
        if (ClipVaultInputMethodService.ListenerRegistrationController.markUnregistered(isRegistered)) {
            isRegistered = false
        }
        assertFalse(isRegistered)
        assertFalse(ClipVaultInputMethodService.ListenerRegistrationController.markUnregistered(isRegistered))
    }

    // 8. Password-field capture exclusion
    @Test
    fun testPasswordFieldCaptureExclusion() {
        val textPassword = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val webPassword = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        val visiblePassword = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        val numberPassword = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val normalText = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL

        assertTrue(ClipVaultInputMethodService.isInputFieldPassword(textPassword))
        assertTrue(ClipVaultInputMethodService.isInputFieldPassword(webPassword))
        assertTrue(ClipVaultInputMethodService.isInputFieldPassword(visiblePassword))
        assertTrue(ClipVaultInputMethodService.isInputFieldPassword(numberPassword))
        assertFalse(ClipVaultInputMethodService.isInputFieldPassword(normalText))
    }

    // 9. Existing database data preservation
    @Test
    fun testExistingDatabaseRecordsPreservedDuringNewCaptures() = runBlocking {
        val existingClip = ClipEntity(
            id = "legacy-clip-1",
            content = "Existing Legacy Record",
            category = ClipCategory.TEXT,
            createdAt = 1_000L,
            isPinned = true
        )
        val dao = FakeClipDao(listOf(existingClip))
        val helper = ClipboardServiceHelper(dao, Dispatchers.Unconfined)

        helper.processQueuedItem("New Upgraded Clip", "ClipVault IME")

        val allClips = dao.snapshot()
        assertEquals(2, allClips.size)
        assertTrue(allClips.any { it.id == "legacy-clip-1" && it.content == "Existing Legacy Record" })
        assertTrue(allClips.any { it.content == "New Upgraded Clip" })
    }

    // 10. Settings status, Keyboard Height, Shift/Caps Lock, Secondary Symbols, Theme/Haptics, One-Time Onboarding
    @Test
    fun testSettingsCaptureStatusAndKeyboardRedesignMetrics() {
        val pausedState = AccessibilityPermissionHelper.resolveAccurateCaptureState(
            autoCaptureEnabled = false,
            isAccessibilityEnabled = true,
            isImeEnabled = true,
            isImeDefault = true
        )
        assertEquals("Capture Permission Required", pausedState)

        val clipVaultImeActive = AccessibilityPermissionHelper.resolveAccurateCaptureState(
            autoCaptureEnabled = true,
            isAccessibilityEnabled = false,
            isImeEnabled = true,
            isImeDefault = true
        )
        assertEquals("ClipVault Keyboard Active", clipVaultImeActive)

        val gboardRestrictedState = AccessibilityPermissionHelper.resolveAccurateCaptureState(
            autoCaptureEnabled = true,
            isAccessibilityEnabled = false,
            isImeEnabled = false,
            isImeDefault = false,
            hasForegroundFocus = false
        )
        assertEquals("Background Capture Restricted", gboardRestrictedState)

        val compact = ClipVaultInputMethodService.KeyboardHeightPreset.fromKey("COMPACT")
        val normal = ClipVaultInputMethodService.KeyboardHeightPreset.fromKey("NORMAL")
        val tall = ClipVaultInputMethodService.KeyboardHeightPreset.fromKey("TALL")
        assertTrue(compact.letterSp in 24f..26f)
        assertTrue(normal.letterSp in 24f..26f)
        assertTrue(tall.letterSp in 24f..26f)
        assertTrue(compact.keyHeightDp < normal.keyHeightDp && normal.keyHeightDp < tall.keyHeightDp)

        val s1 = ClipVaultInputMethodService.computeNextShiftState(
            ClipVaultInputMethodService.ShiftState.LOWERCASE,
            elapsedSinceLastTapMs = 1000L
        )
        assertEquals(ClipVaultInputMethodService.ShiftState.SHIFTED_SINGLE, s1)

        val s2 = ClipVaultInputMethodService.computeNextShiftState(s1, elapsedSinceLastTapMs = 180L)
        assertEquals(ClipVaultInputMethodService.ShiftState.CAPS_LOCK, s2)

        val s3 = ClipVaultInputMethodService.computeNextShiftState(s2, elapsedSinceLastTapMs = 500L)
        assertEquals(ClipVaultInputMethodService.ShiftState.LOWERCASE, s3)

        val symPage = ClipVaultInputMethodService.toggleSymbolMode(ClipVaultInputMethodService.SymbolPage.LETTERS)
        assertEquals(ClipVaultInputMethodService.SymbolPage.NUMBERS_SYMBOLS_1, symPage)
        assertEquals("1", ClipVaultInputMethodService.resolveKeyOutput("q", 0, s3, symPage))
        assertEquals("Q", ClipVaultInputMethodService.resolveKeyOutput("q", 0, s2, ClipVaultInputMethodService.SymbolPage.LETTERS))

        assertEquals("1", ClipVaultInputMethodService.resolveSecondarySymbol(0))
        assertEquals("0", ClipVaultInputMethodService.resolveSecondarySymbol(9))
        assertEquals("@", ClipVaultInputMethodService.resolveSecondarySymbol(10))
        assertEquals(")", ClipVaultInputMethodService.resolveSecondarySymbol(18))

        assertEquals(
            ClipVaultInputMethodService.KeyboardThemeMode.PURE_BLACK,
            ClipVaultInputMethodService.cycleNextKeyboardTheme(ClipVaultInputMethodService.KeyboardThemeMode.DARK_CHARCOAL)
        )
        assertEquals(
            ClipVaultInputMethodService.VibrationIntensity.STRONG,
            ClipVaultInputMethodService.cycleNextVibrationIntensity(ClipVaultInputMethodService.VibrationIntensity.MEDIUM)
        )
        assertEquals(
            ClipVaultInputMethodService.VibrationIntensity.OFF,
            ClipVaultInputMethodService.cycleNextVibrationIntensity(ClipVaultInputMethodService.VibrationIntensity.STRONG)
        )

        assertTrue(AccessibilityPermissionHelper.shouldShowFirstLaunchOnboarding(onboardingAlreadyShown = false))
        assertFalse(AccessibilityPermissionHelper.shouldShowFirstLaunchOnboarding(onboardingAlreadyShown = true))
    }

    // 11. Part 6 Section 1: Bengali Phonetic Transliteration Tests
    @Test
    fun testBengaliPhoneticWordsAndSentences() {
        val userDict = BengaliUserDictionary()
        // Fresh dictionary starts empty on first run (Part 2 #4)
        assertEquals(0, userDict.getAllPersonalWords().size)
        assertEquals(0, userDict.getAllShortcuts().size)

        val engine = BengaliPhoneticEngine(userDict)

        assertEquals("আমি", engine.transliterateWord("ami"))
        assertEquals("তুমি", engine.transliterateWord("tumi"))
        assertEquals("আমার", engine.transliterateWord("amar"))
        assertEquals("নাম", engine.transliterateWord("nam"))
        assertEquals("বাংলাদেশ", engine.transliterateWord("bangladesh"))
        assertEquals("ভালোবাসি", engine.transliterateWord("bhalobashi"))
        assertEquals("কেমন আছো", engine.transliterateSentence("kemon acho"))
        assertEquals("আপনি কেমন আছেন", engine.transliterateSentence("apni kemon achen"))
        assertEquals("সুন্দর", engine.transliterateWord("shundor"))
        assertEquals("ভালো", engine.transliterateWord("valo"))
        assertEquals("ভালো", engine.transliterateWord("bhalo"))
        assertEquals("ধন্যবাদ", engine.transliterateWord("dhonnobad"))
        assertEquals("বৃষ্টি", engine.transliterateWord("bristi"))
        assertEquals("স্বাগতম", engine.transliterateWord("shagotom"))

        // suvash -> সুভাষ when saved in dictionary or via built-in dictionary
        userDict.addOrUpdateWord("suvash", "সুভাষ", KeyboardLanguageMode.BANGLA_PHONETIC)
        assertEquals("সুভাষ", engine.transliterateWord("suvash"))
    }

    // 12. Part 6 Section 2: Mixed Language Tests (General Token Classification & Sentence Candidates)
    @Test
    fun testMixedLanguageClassificationAndSentenceCandidates() {
        val userDict = BengaliUserDictionary()
        val engine = BengaliPhoneticEngine(userDict)
        val suggestionEngine = BengaliSuggestionEngine(engine)

        // General token classification (not limited to 2 hardcoded words)
        assertEquals(TokenLanguageClassification.BANGLISH_WORD, BengaliWordDictionary.classifyToken("ami"))
        assertEquals(TokenLanguageClassification.ENGLISH_WORD, BengaliWordDictionary.classifyToken("tomorrow"))
        assertEquals(TokenLanguageClassification.ENGLISH_WORD, BengaliWordDictionary.classifyToken("meeting"))
        assertEquals(TokenLanguageClassification.PURE_BENGALI_SCRIPT, BengaliWordDictionary.classifyToken("ভালো"))

        // Mixed sentence candidate generation: "ami tomorrow office jabo"
        val mixedCandidates = engine.generateMixedSentenceCandidates("ami tomorrow office jabo")
        assertEquals("আমি tomorrow office যাব", mixedCandidates[0])
        assertEquals("আমি আগামীকাল অফিস যাব", mixedCandidates[1])
        assertEquals("আমি tomorrow অফিস যাব", mixedCandidates[2])

        // Additional mixed-language phrases from Part 6 Section 2
        assertEquals("আমি meeting এ যাব", engine.transliterateSentence("ami meeting e jabo", preserveMixedEnglish = true))
        assertEquals("I am ভালো", engine.transliterateSentence("I am ভালো", preserveMixedEnglish = true))
        assertEquals("আজ office যাব", engine.transliterateSentence("আজ office যাব", preserveMixedEnglish = true))

        // Bilingual suggestions in both English and Bangla modes
        val enOffice = suggestionEngine.getSuggestions("office", "", KeyboardLanguageMode.ENGLISH).map { it.text }
        assertTrue(enOffice.contains("office"))
        assertTrue(enOffice.contains("অফিস"))

        val bnAmi = suggestionEngine.getSuggestions("ami", "", KeyboardLanguageMode.BANGLA_PHONETIC).map { it.text }
        assertEquals("আমি", bnAmi.first())
        assertTrue(bnAmi.contains("ami"))
    }

    // 13. Part 6 Section 3 & 4: Personal Dictionary & Longest-Match-First Phrase Shortcuts
    @Test
    fun testPersonalDictionaryEmptyDefaultCrudSearchAndPhraseShortcuts() {
        val userDict = BengaliUserDictionary()
        val phoneticEngine = BengaliPhoneticEngine(userDict)
        val suggestionEngine = BengaliSuggestionEngine(phoneticEngine)

        // 1. Starts empty on first run
        assertTrue(userDict.getAllPersonalWords().isEmpty())
        assertTrue(userDict.getAllShortcuts().isEmpty())

        // 2. Add, Edit, Delete, Search personal word
        userDict.addOrUpdateWord("suvash", "সুভাষ", KeyboardLanguageMode.BANGLA_PHONETIC)
        val suvashCandidates = suggestionEngine.getSuggestions("suvash", "", KeyboardLanguageMode.BANGLA_PHONETIC)
        assertEquals("সুভাষ", suvashCandidates.first().text)
        assertEquals(1, userDict.searchPersonalWords("suv").size)

        userDict.editWord("suvash", "sylhet", "সিলেট")
        assertEquals("সিলেট", phoneticEngine.transliterateWord("sylhet"))
        assertTrue(userDict.deleteWord("sylhet"))
        assertEquals(0, userDict.getAllPersonalWords().size)

        // 3. v1.6.0 TSV migration preservation
        userDict.loadFromSerializedStrings(
            rawPersonal = "suvash\tসুভাষ\tbn\t5\t1700000000",
            rawShortcuts = "brb\tbe right back\ten"
        )
        assertEquals("সুভাষ", userDict.lookupPersonalWord("suvash"))
        assertEquals("be right back", userDict.lookupShortcut("brb")?.expansion)

        // 4. Single-word & Multi-word phrase shortcuts with longest-match-first
        userDict.addOrUpdateShortcut("amar", "আমার প্রিয়", KeyboardLanguageMode.BANGLA_PHONETIC)
        userDict.addOrUpdateShortcut("amar nam", "আমার নাম সুভাষ", KeyboardLanguageMode.BANGLA_PHONETIC)
        userDict.addOrUpdateShortcut("amar nam suvash", "আমার নাম সুভাষ পুরকায়স্থ", KeyboardLanguageMode.BANGLA_PHONETIC)

        val multiWordOrdered = userDict.getMultiWordShortcutsLongestFirst()
        assertEquals("amar nam suvash", multiWordOrdered[0].trigger)
        assertEquals("amar nam", multiWordOrdered[1].trigger)

        // Longest-match-first sentence expansion + punctuation preservation + case-insensitive trigger
        assertEquals("আমার নাম সুভাষ!", phoneticEngine.transliterateSentence("Amar Nam!"))
        assertEquals("আমার নাম সুভাষ পুরকায়স্থ।", phoneticEngine.transliterateSentence("amar nam suvash."))

        val enShortcuts = suggestionEngine.getSuggestions("BRB", "", KeyboardLanguageMode.ENGLISH).map { it.text }
        assertEquals("be right back", enShortcuts.first())
    }

    // 14. Fix 4: Safe Keyboard-Managed Undo/Redo Stack & Editor Session Safety Tests
    @Test
    fun testKeyboardManagedUndoRedoStackAndSafetyGuards() {
        val undoRedo = KeyboardUndoRedoManager(maxHistorySize = 20)

        // 1. fieldId == 0 is treated as a valid editor field ID (Fix 4)
        undoRedo.onEditorContextChanged(
            packageName = "com.whatsapp",
            newFieldId = 0,
            inputType = InputType.TYPE_CLASS_TEXT,
            isPasswordOrSensitive = false,
            restarting = false
        )

        // 2. Typing words and multi-step undo/redo
        undoRedo.recordEdit(beforeText = "", afterText = "আমি ")
        undoRedo.recordEdit(beforeText = "আমি ", afterText = "আমি ভালো ")
        assertTrue(undoRedo.canUndo())

        val undoStep1 = undoRedo.undo(currentTextBeforeCursor = "আমি ভালো ")
        assertNotNull(undoStep1)
        assertEquals("আমি ভালো ", undoStep1?.afterText)
        assertEquals("আমি ", undoStep1?.beforeText)

        // 3. Redo after undo
        assertTrue(undoRedo.canRedo())
        val redoStep1 = undoRedo.redo(currentTextBeforeCursor = "আমি ")
        assertNotNull(redoStep1)
        assertEquals("আমি ", redoStep1?.beforeText)
        assertEquals("আমি ভালো ", redoStep1?.afterText)

        // 4. Replacing selected text and undoing selection replacement
        undoRedo.recordSelectionReplacement(
            beforePrefix = "আমি ",
            selectedText = "ভালো ",
            replacementText = "সুস্থ "
        )
        val undoReplace = undoRedo.undo(currentTextBeforeCursor = "আমি সুস্থ ")
        assertNotNull(undoReplace)
        assertEquals("আমি ভালো ", undoReplace?.beforeText)

        // 5. Active multi-character selection blocks unsafe Undo/Redo
        undoRedo.recordEdit(beforeText = "আমি ভালো ", afterText = "আমি ভালো আছি ")
        undoRedo.onSelectionChanged(oldSelStart = 9, oldSelEnd = 9, newSelStart = 0, newSelEnd = 4, isInternalEdit = false)
        assertFalse(undoRedo.canUndo())
        assertNull(undoRedo.undo(currentTextBeforeCursor = "আমি "))
        // Collapsing selection back to cursor restores safe undo
        undoRedo.onSelectionChanged(oldSelStart = 0, oldSelEnd = 4, newSelStart = 13, newSelEnd = 13, isInternalEdit = false)
        assertTrue(undoRedo.canUndo())

        // 6. External edit divergence guard (corrupt text protection)
        val divergentUndo = undoRedo.undo(currentTextBeforeCursor = "COMPLETELY_DIFFERENT_EXTERNAL_TEXT")
        assertNull(divergentUndo)
        assertFalse(undoRedo.canUndo())

        // 7. Switching between two editors where BOTH have fieldId == 0 clears history (restarting = false)
        undoRedo.recordEdit(beforeText = "a", afterText = "ab")
        assertTrue(undoRedo.canUndo())
        undoRedo.onEditorContextChanged(
            packageName = "com.whatsapp",
            newFieldId = 0,
            inputType = InputType.TYPE_CLASS_TEXT,
            isPasswordOrSensitive = false,
            restarting = false
        )
        assertFalse(undoRedo.canUndo())

        // 8. Switching applications clears history even with same fieldId
        undoRedo.recordEdit(beforeText = "x", afterText = "xy")
        assertTrue(undoRedo.canUndo())
        undoRedo.onEditorContextChanged(
            packageName = "org.telegram.messenger",
            newFieldId = 0,
            inputType = InputType.TYPE_CLASS_TEXT,
            isPasswordOrSensitive = false,
            restarting = false
        )
        assertFalse(undoRedo.canUndo())

        // 9. Disabling in password/sensitive field
        undoRedo.onEditorContextChanged(
            packageName = "org.telegram.messenger",
            newFieldId = 303,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            isPasswordOrSensitive = true,
            restarting = false
        )
        undoRedo.recordEdit(beforeText = "", afterText = "secret")
        assertFalse(undoRedo.canUndo())
    }

    // 15. Part 6 Section 6 & 7: UI, Settings, Suggestion-First Default & Deterministic Caching
    @Test
    fun testUiSettingsSuggestionFirstAboutIntentAndPerformance() {
        val userDict = BengaliUserDictionary()
        val phoneticEngine = BengaliPhoneticEngine(userDict)
        val suggestionEngine = BengaliSuggestionEngine(phoneticEngine)

        // 1. Suggestion-First Auto Correction Default (never aggressively replaces words unless enabled)
        val safeDefaultCommit = suggestionEngine.resolveWordCommitOnSpace(
            composingText = "teh",
            languageMode = KeyboardLanguageMode.ENGLISH,
            autocorrectEnabled = true,
            autoApplyOnSpace = false
        )
        assertEquals("teh", safeDefaultCommit)

        val optInAutoApplyCommit = suggestionEngine.resolveWordCommitOnSpace(
            composingText = "teh",
            languageMode = KeyboardLanguageMode.ENGLISH,
            autocorrectEnabled = true,
            autoApplyOnSpace = true
        )
        assertEquals("the", optInAutoApplyCommit)

        // 2. Contextual Next-Word Prediction
        val bnNextAfterAmi = suggestionEngine.getSuggestions("", "আমি", KeyboardLanguageMode.BANGLA_PHONETIC).map { it.text }
        assertTrue(bnNextAfterAmi.contains("তুমি"))
        assertTrue(bnNextAfterAmi.contains("আজ"))
        assertTrue(bnNextAfterAmi.contains("ভালো"))

        val enNextAfterI = suggestionEngine.getSuggestions("", "I", KeyboardLanguageMode.ENGLISH).map { it.text }
        assertTrue(enNextAfterI.contains("am"))
        assertTrue(enNextAfterI.contains("will"))
        assertTrue(enNextAfterI.contains("have"))

        // 3. Number Row digits in English and Bengali modes
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), ClipVaultInputMethodService.resolveNumberRowDigits(KeyboardLanguageMode.ENGLISH, false))
        assertEquals(listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০"), ClipVaultInputMethodService.resolveNumberRowDigits(KeyboardLanguageMode.BANGLA_PHONETIC, true))

        // 4. One-Handed Mode & Key Size Presets & Bengali Font Style
        assertEquals(OneHandedMode.LEFT, OneHandedMode.fromPrefKey("LEFT"))
        assertEquals(OneHandedMode.RIGHT, OneHandedMode.LEFT.next())
        assertEquals(OneHandedMode.OFF, OneHandedMode.RIGHT.next())

        assertEquals(KeySizePreset.SMALL, KeySizePreset.fromPrefKey("SMALL"))
        assertEquals(KeySizePreset.LARGE, KeySizePreset.fromPrefKey("LARGE"))
        assertTrue(KeySizePreset.SMALL.heightScale < KeySizePreset.MEDIUM.heightScale)
        assertTrue(KeySizePreset.LARGE.heightScale > KeySizePreset.MEDIUM.heightScale)

        assertEquals(BengaliFontStyle.SANS_SERIF_CLEAN, BengaliFontStyle.fromPrefKey("SANS_SERIF_CLEAN"))

        // 5. Key Pop-up Preview screen edge clamping
        assertEquals(8, ClipVaultInputMethodService.computeClampedPopupX(keyLeftPx = 0, keyWidthPx = 40, popupWidthPx = 120, containerWidthPx = 1080))
        assertEquals(952, ClipVaultInputMethodService.computeClampedPopupX(keyLeftPx = 1030, keyWidthPx = 40, popupWidthPx = 120, containerWidthPx = 1080))

        // 6. Accessibility TalkBack labels
        assertTrue(ClipVaultInputMethodService.resolveKeyAccessibilityLabel("GLOBE", KeyboardLanguageMode.ENGLISH, ClipVaultInputMethodService.ShiftState.LOWERCASE).contains("Switch Language"))
        assertTrue(ClipVaultInputMethodService.resolveKeyAccessibilityLabel("SPACE", KeyboardLanguageMode.BANGLA_PHONETIC, ClipVaultInputMethodService.ShiftState.LOWERCASE).contains("Spacebar"))

        // 7. Smart Language Detection prompt
        val promptToBangla = suggestionEngine.detectSmartLanguagePrompt(
            composingText = "kemon",
            recentCommittedWords = listOf("ami", "tumi"),
            currentMode = KeyboardLanguageMode.ENGLISH,
            smartDetectionEnabled = true
        )
        assertNotNull(promptToBangla)
        assertEquals(KeyboardLanguageMode.BANGLA_PHONETIC, promptToBangla?.suggestedMode)

        // 8. Deterministic LRU cache consistency across repeated lookups (no flaky wall-clock thresholds)
        val sampleWords = listOf("ami", "tumi", "bangladesh", "bhalobashi", "kemon", "shundor", "dhonnobad", "office", "tomorrow", "meeting")
        for (word in sampleWords) {
            val firstPass = suggestionEngine.getSuggestions(word, "আমি", KeyboardLanguageMode.BANGLA_PHONETIC).map { it.text }
            val cachedPass = suggestionEngine.getSuggestions(word, "আমি", KeyboardLanguageMode.BANGLA_PHONETIC).map { it.text }
            assertTrue(firstPass.isNotEmpty())
            assertEquals(firstPass, cachedPass)
        }
    }

    // 16. Fix 1 & Fix 8: Unified Settings Preference Keys, Migration, Popup Preview & High Contrast Borders
    @Test
    fun testSettingsPreferenceKeyUnificationAndMigration() {
        // Canonical preference keys shared by SettingsDialog and ClipVaultInputMethodService
        assertEquals("keyboard_key_popup_preview", ClipVaultInputMethodService.PREF_KEY_POPUP_PREVIEW)
        assertEquals("keyboard_high_contrast_accessibility", ClipVaultInputMethodService.PREF_HIGH_CONTRAST)

        // Legacy fallback resolution when canonical key is not yet set
        assertFalse(ClipVaultInputMethodService.resolveKeyPopupSetting(canonicalValue = null, legacyValue = false))
        assertTrue(ClipVaultInputMethodService.resolveKeyPopupSetting(canonicalValue = null, legacyValue = null))
        assertFalse(ClipVaultInputMethodService.resolveKeyPopupSetting(canonicalValue = false, legacyValue = true))

        assertTrue(ClipVaultInputMethodService.resolveHighContrastSetting(canonicalValue = null, legacyValue = true))
        assertFalse(ClipVaultInputMethodService.resolveHighContrastSetting(canonicalValue = null, legacyValue = null))
        assertTrue(ClipVaultInputMethodService.resolveHighContrastSetting(canonicalValue = true, legacyValue = false))

        // Turning Key Pop-up OFF disables the preview
        assertFalse(ClipVaultInputMethodService.shouldShowKeyPopupPreview(keyPopupEnabled = false, isPasswordField = false, previewLabel = "অ"))
        assertFalse(ClipVaultInputMethodService.shouldShowKeyPopupPreview(keyPopupEnabled = true, isPasswordField = true, previewLabel = "a"))
        assertTrue(ClipVaultInputMethodService.shouldShowKeyPopupPreview(keyPopupEnabled = true, isPasswordField = false, previewLabel = "অ"))

        // Turning High Contrast ON changes keyboard borders and contrast
        assertEquals(1, ClipVaultInputMethodService.resolveKeyBorderStrokeWidthDp(highContrast = false))
        assertEquals(2, ClipVaultInputMethodService.resolveKeyBorderStrokeWidthDp(highContrast = true))
        val normalBorder = ClipVaultInputMethodService.resolveKeyBorderHex(ClipVaultInputMethodService.KeyboardThemeMode.DARK_CHARCOAL, highContrast = false)
        val highContrastBorder = ClipVaultInputMethodService.resolveKeyBorderHex(ClipVaultInputMethodService.KeyboardThemeMode.DARK_CHARCOAL, highContrast = true)
        assertNotEquals(normalBorder, highContrastBorder)
        assertEquals("#FFFFFF", highContrastBorder)
    }

    // 17. Fix 2: Tapping a Suggestion Must NOT Auto-Save to Personal Dictionary
    @Test
    fun testSuggestionTapDoesNotAutoSaveToPersonalDictionary() {
        val userDict = BengaliUserDictionary()
        val phoneticEngine = BengaliPhoneticEngine(userDict)
        val suggestionEngine = BengaliSuggestionEngine(phoneticEngine)

        assertEquals(0, userDict.getAllPersonalWords().size)

        // User taps suggestions repeatedly during normal typing
        suggestionEngine.onCandidateSelectedByUser("ami", "আমি", KeyboardLanguageMode.BANGLA_PHONETIC)
        suggestionEngine.onCandidateSelectedByUser("bhalo", "ভালো", KeyboardLanguageMode.BANGLA_PHONETIC)
        suggestionEngine.onCandidateSelectedByUser("office", "অফিস", KeyboardLanguageMode.BANGLA_PHONETIC)

        // Personal Dictionary MUST remain empty (no automatic pollution)
        assertEquals(0, userDict.getAllPersonalWords().size)
        assertNull(userDict.lookupPersonalWord("ami"))

        // Adaptive candidate ranking tracks selection frequency separately
        assertTrue(userDict.getAdaptiveSelectionBoost("ami", "আমি") > 0)

        // Only explicit Add to Personal Dictionary action saves an entry
        userDict.addOrUpdateWord("suvash", "সুভাষ", KeyboardLanguageMode.BANGLA_PHONETIC)
        assertEquals(1, userDict.getAllPersonalWords().size)
        assertEquals("সুভাষ", userDict.lookupPersonalWord("suvash"))
    }

    // 18. Fix 3: Mixed Sentence Suggestions Surfaced in Real Keyboard Suggestion Workflow
    @Test
    fun testMixedSentenceSuggestionsSurfacedInKeyboardWorkflow() {
        val userDict = BengaliUserDictionary()
        val phoneticEngine = BengaliPhoneticEngine(userDict)
        val suggestionEngine = BengaliSuggestionEngine(phoneticEngine)

        // Simulate user typing "ami tomorrow office " and now composing "jabo"
        val candidates = suggestionEngine.getSuggestions(
            composingText = "jabo",
            previousWord = "office",
            languageMode = KeyboardLanguageMode.BANGLA_PHONETIC,
            autocorrectEnabled = true,
            nextWordPredictionEnabled = true,
            sentenceRawTokens = listOf("ami", "tomorrow", "office"),
            committedSentencePrefix = "আমি tomorrow office "
        )

        val candidateTexts = candidates.map { it.text }
        // Word-level suggestion is present
        assertTrue(candidateTexts.contains("যাব"))
        // All 3 mixed-sentence candidates are surfaced in the real suggestion workflow
        assertTrue(candidateTexts.contains("আমি tomorrow office যাব"))
        assertTrue(candidateTexts.contains("আমি আগামীকাল অফিস যাব"))
        assertTrue(candidateTexts.contains("আমি tomorrow অফিস যাব"))

        val mixedCandidates = candidates.filter { it.source == CandidateSource.MIXED_SENTENCE }
        assertTrue(mixedCandidates.size >= 3)
        // Verify replacePrecedingSentenceChars matches committedSentencePrefix.length
        assertTrue(mixedCandidates.all { it.replacePrecedingSentenceChars == "আমি tomorrow office ".length })

        // Verify punctuation preservation and unknown English token preservation
        val withPunctuation = phoneticEngine.generateMixedSentenceCandidates("ami tomorrow, office jabo?")
        assertEquals("আমি tomorrow, office যাব?", withPunctuation[0])
        assertEquals("আমি আগামীকাল, অফিস যাব?", withPunctuation[1])
        assertEquals("আমি tomorrow, অফিস যাব?", withPunctuation[2])

        // Unknown English technical tokens must never be translated or transliterated
        val unknownEnglish = phoneticEngine.generateMixedSentenceCandidates("ami kubernetes cluster deploy korbo")
        assertTrue(unknownEnglish.first().contains("kubernetes"))
        assertTrue(unknownEnglish.first().contains("cluster"))
    }

    // 19. Fix 5: Genuine v1.6.0 Personal Dictionary Migration Test
    @Test
    fun testGenuineV160PersonalDictionaryMigration() {
        val userDict = BengaliUserDictionary()

        // Representative v1.6.0 legacy data containing:
        // - Unmodified v1.6.0 auto-seeded demo entry ("bhalobashi\tভালোবাসি\tbn\t1\t1700000000")
        // - User-modified entry for a seeded key ("ami\tআমি নিজে\tbn\t4\t1700000100")
        // - User-created custom entry ("suvash\tসুভাষ পুরকায়স্থ\tbn\t7\t1700000200")
        val legacyPersonalRaw = listOf(
            "bhalobashi\tভালোবাসি\tbn\t1\t1700000000",
            "ami\tআমি নিজে\tbn\t4\t1700000100",
            "suvash\tসুভাষ পুরকায়স্থ\tbn\t7\t1700000200"
        ).joinToString("\n")

        // Representative v1.6.0 legacy shortcuts containing:
        // - Unmodified v1.6.0 auto-seeded demo shortcut ("omw\ton my way\ten")
        // - User-created shortcut ("addr\tSylhet, Bangladesh\ten")
        val legacyShortcutsRaw = listOf(
            "omw\ton my way\ten",
            "addr\tSylhet, Bangladesh\ten"
        ).joinToString("\n")

        userDict.migrateFromLegacyRawData(
            legacyPersonalTsv = legacyPersonalRaw,
            legacyShortcutsTsv = legacyShortcutsRaw,
            legacyWasDemoSeeded = true,
            existingNewPersonalTsv = null,
            existingNewShortcutsTsv = null
        )

        // Unmodified auto-seeded demo entry "bhalobashi -> ভালোবাসি" (freq 1) is filtered out
        assertNull(userDict.lookupPersonalWord("bhalobashi"))
        // User-modified entry "ami -> আমি নিজে" is preserved
        assertEquals("আমি নিজে", userDict.lookupPersonalWord("ami"))
        // User-created entry "suvash -> সুভাষ পুরকায়স্থ" is preserved
        assertEquals("সুভাষ পুরকায়স্থ", userDict.lookupPersonalWord("suvash"))

        // Unmodified auto-seeded shortcut "omw" is filtered out, user-created "addr" is preserved
        assertNull(userDict.lookupShortcut("omw"))
        assertEquals("Sylhet, Bangladesh", userDict.lookupShortcut("addr")?.expansion)

        // Newer v1.7.1 entries must never be overwritten by older legacy data on re-run
        val newerDict = BengaliUserDictionary()
        newerDict.migrateFromLegacyRawData(
            legacyPersonalTsv = "suvash\tপুরানো নাম\tbn\t1\t1600000000",
            legacyShortcutsTsv = null,
            legacyWasDemoSeeded = false,
            existingNewPersonalTsv = "suvash\tসুভাষ নতুন\tbn\t9\t1800000000",
            existingNewShortcutsTsv = null
        )
        assertEquals("সুভাষ নতুন", newerDict.lookupPersonalWord("suvash"))
    }

    // 20. Fix 6: Bengali Auto Correction, Unicode Spelling Corrections & Ambiguous Alternatives
    @Test
    fun testBengaliAutoCorrectionSpellingAndAmbiguousAlternatives() {
        val userDict = BengaliUserDictionary()
        val phoneticEngine = BengaliPhoneticEngine(userDict)
        val suggestionEngine = BengaliSuggestionEngine(phoneticEngine)

        // 1. Common Banglish typo correction
        val typoBhalobasi = suggestionEngine.getSuggestions("bhalobasi", "", KeyboardLanguageMode.BANGLA_PHONETIC)
        assertTrue(typoBhalobasi.any { it.text == "ভালোবাসি" })
        // Original typed word is always included as an available candidate
        assertTrue(typoBhalobasi.any { it.text == "bhalobasi" })

        // 2. Common Bengali Unicode spelling corrections (ভূল -> ভুল, মানুস -> মানুষ, পরিক্ষা -> পরীক্ষা)
        val unicodeBhul = suggestionEngine.getSuggestions("ভূল", "", KeyboardLanguageMode.BANGLA_PHONETIC)
        assertEquals("ভুল", unicodeBhul.first().text)
        assertTrue(unicodeBhul.any { it.text == "ভূল" }) // original typed word retained

        val unicodeManush = suggestionEngine.getSuggestions("মানুস", "", KeyboardLanguageMode.BANGLA_PHONETIC)
        assertEquals("মানুষ", unicodeManush.first().text)
        assertTrue(unicodeManush.any { it.text == "মানুস" })

        // 3. Ambiguous Bengali word alternatives (কি ↔ কী, কোন ↔ কোনো, কারন ↔ কারণ)
        val ambiguousKi = suggestionEngine.getSuggestions("ki", "", KeyboardLanguageMode.BANGLA_PHONETIC).map { it.text }
        assertTrue(ambiguousKi.contains("কি"))
        assertTrue(ambiguousKi.contains("কী"))

        val ambiguousKono = suggestionEngine.getSuggestions("kono", "", KeyboardLanguageMode.BANGLA_PHONETIC).map { it.text }
        assertTrue(ambiguousKono.contains("কোনো"))
        assertTrue(ambiguousKono.contains("কোন"))

        // 4. Suggestion-First default on Spacebar: never silently replace uncertain Bengali spelling unless autoApplyOnSpace = true
        val safeBanglaSpace = suggestionEngine.resolveWordCommitOnSpace(
            composingText = "ভূল",
            languageMode = KeyboardLanguageMode.BANGLA_PHONETIC,
            autocorrectEnabled = true,
            autoApplyOnSpace = false
        )
        assertEquals("ভূল", safeBanglaSpace)

        val optInBanglaSpace = suggestionEngine.resolveWordCommitOnSpace(
            composingText = "ভূল",
            languageMode = KeyboardLanguageMode.BANGLA_PHONETIC,
            autocorrectEnabled = true,
            autoApplyOnSpace = true
        )
        assertEquals("ভুল", optInBanglaSpace)
    }

    // 21. Fix 7 & Fix 9: Bengali Orthographic Integrity & Crash Isolation (Catch Exception Only, Rethrow Fatal Error)
    @Test
    fun testBengaliUnicodeIntegrityAndCrashIsolation() {
        // 1. Verify কার, ফলা, যুক্তাক্ষর, হসন্ত, Bengali numerals, and দাড়ি (।) integrity
        assertTrue(BengaliUnicodeRenderValidator.containsKar("আমি"))
        assertTrue(BengaliUnicodeRenderValidator.containsHasantaOrJuktakkhor("সুন্দর"))
        assertTrue(BengaliUnicodeRenderValidator.containsHasantaOrJuktakkhor("বৃষ্টি"))
        assertTrue(BengaliUnicodeRenderValidator.containsPhala("প্রিয়"))
        assertTrue(BengaliUnicodeRenderValidator.containsPhala("স্বাগতম"))
        assertTrue(BengaliUnicodeRenderValidator.containsBengaliNumerals("২০২৬"))
        assertTrue(BengaliUnicodeRenderValidator.containsBengaliPunctuation("আমার নাম সুভাষ।"))
        assertTrue(BengaliUnicodeRenderValidator.verifyOrthographicIntegrity("আমি বাংলায় গান গাই। ২০২৬"))

        // 2. SafeInputConnectionExecutor catches recoverable Exception and triggers state reset
        var resetCalled = false
        val recovered = ClipVaultInputMethodService.SafeInputConnectionExecutor.execute<Any, Boolean>(
            inputConnection = Any(),
            actionName = "commitText",
            onRecoverableException = { resetCalled = true }
        ) {
            throw IllegalStateException("Simulated dead InputConnection binder exception")
        }
        assertNull(recovered)
        assertTrue(resetCalled)

        // 3. SafeInputConnectionExecutor does NOT swallow fatal JVM Errors
        var fatalErrorPropagated = false
        try {
            ClipVaultInputMethodService.SafeInputConnectionExecutor.execute<Any, Boolean>(
                inputConnection = Any(),
                actionName = "fatalTest"
            ) {
                throw AssertionError("Simulated fatal JVM Error")
            }
        } catch (err: AssertionError) {
            fatalErrorPropagated = true
        }
        assertTrue(fatalErrorPropagated)
    }
}
