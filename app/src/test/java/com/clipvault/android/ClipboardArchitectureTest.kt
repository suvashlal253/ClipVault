package com.clipvault.android

import android.content.SharedPreferences
import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.data.local.entity.ClipboardContentNormalizer
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardArchitectureTest {

    @Before
    fun resetGlobalClipboardFingerprints() {
        ClipboardServiceHelper.clearGlobalAppOriginatedCopies()
    }

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
        override suspend fun getAllNonVaultedClipsSync(): List<ClipEntity> =
            synchronized(items) { items.filter { !it.isVaulted }.toList() }
        override fun getVaultClipsFlow(): Flow<List<ClipEntity>> = flow.map { list -> list.filter { it.isVaulted } }
        override fun getPinnedClipsFlow(): Flow<List<ClipEntity>> = flow.map { list -> list.filter { it.isPinned } }
        override fun getClipsByCategoryFlow(category: ClipCategory): Flow<List<ClipEntity>> =
            flow.map { list -> list.filter { it.category == category } }
        override fun searchClips(query: String): Flow<List<ClipEntity>> = flow
        override suspend fun findClipByContent(content: String): ClipEntity? =
            synchronized(items) { items.firstOrNull { it.content == content } }
        override suspend fun findClipsByContentHash(contentHash: String): List<ClipEntity> =
            synchronized(items) { items.filter { !it.isVaulted && it.contentHash == contentHash } }
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

    // 4. Re-copying an already-saved clip after another clip (A -> B -> A) does NOT create a duplicate (v1.7.2 Requirement 1)
    @Test
    fun testReCopyingSameTextAfterDifferentClipIsSaved() = runBlocking {
        val dao = FakeClipDao()
        val helper = ClipboardServiceHelper(dao, Dispatchers.Unconfined)

        helper.processQueuedItem("Clip Alpha", "Source")
        helper.processQueuedItem("Clip Beta", "Source")
        val duplicateAttempt = helper.processQueuedItem("Clip Alpha", "Source")

        assertNull(duplicateAttempt)
        val saved = dao.snapshot()
        assertEquals(2, saved.size)
        assertEquals(listOf("Clip Beta", "Clip Alpha"), saved.map { it.content })
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

    // 14. Fix 2 (v1.7.2): Safe Keyboard-Managed Undo/Redo Stack, Cursor & Selection Restoration Tests
    @Test
    fun testKeyboardManagedUndoRedoStackAndSafetyGuards() {
        val undoRedo = KeyboardUndoRedoManager(maxHistorySize = 20)

        // 1. fieldId == 0 is treated as a valid editor field ID
        undoRedo.onEditorContextChanged(
            packageName = "com.whatsapp",
            newFieldId = 0,
            inputType = InputType.TYPE_CLASS_TEXT,
            isPasswordOrSensitive = false,
            restarting = false
        )

        // 2. Word commits, punctuation, backspace, mixed-sentence replacement, and cursor restoration
        undoRedo.recordEdit(beforeText = "", afterText = "আমি ")
        assertEquals(4, undoRedo.getLastKnownCursorPosition())

        undoRedo.recordEdit(beforeText = "আমি ", afterText = "আমি ভালো ")
        assertEquals(9, undoRedo.getLastKnownCursorPosition())

        // Punctuation commit
        undoRedo.recordEdit(beforeText = "আমি ভালো ", afterText = "আমি ভালো, ")
        assertEquals(10, undoRedo.getLastKnownCursorPosition())

        // Backspace edit
        undoRedo.recordEdit(beforeText = "আমি ভালো, ", afterText = "আমি ভালো ")
        assertEquals(9, undoRedo.getLastKnownCursorPosition())

        // Mixed-sentence replacement
        undoRedo.recordEdit(beforeText = "আমি ভালো ", afterText = "আমি আগামীকাল অফিস যাব। ")
        assertEquals("আমি আগামীকাল অফিস যাব। ".length, undoRedo.getLastKnownCursorPosition())
        assertTrue(undoRedo.canUndo())

        // Undo mixed-sentence replacement and verify cursorBefore / cursorAfter restoration
        val undoMixed = undoRedo.undo(currentTextBeforeCursor = "আমি আগামীকাল অফিস যাব। ")
        assertNotNull(undoMixed)
        assertEquals("আমি ভালো ", undoMixed?.beforeText)
        assertEquals(9, undoMixed?.cursorBefore)
        assertEquals("আমি আগামীকাল অফিস যাব। ".length, undoMixed?.cursorAfter)
        assertEquals(9, undoRedo.getLastKnownCursorPosition())

        // Redo mixed-sentence replacement and verify cursor restoration
        assertTrue(undoRedo.canRedo())
        val redoMixed = undoRedo.redo(currentTextBeforeCursor = "আমি ভালো ")
        assertNotNull(redoMixed)
        assertEquals("আমি আগামীকাল অফিস যাব। ", redoMixed?.afterText)
        assertEquals("আমি আগামীকাল অফিস যাব। ".length, redoMixed?.cursorAfter)
        assertEquals("আমি আগামীকাল অফিস যাব। ".length, undoRedo.getLastKnownCursorPosition())

        // Undo back to "আমি ভালো " so we can test selection replacement
        undoRedo.undo(currentTextBeforeCursor = "আমি আগামীকাল অফিস যাব। ")

        // 3. Replacing selected text and preserving selection replacement metadata
        undoRedo.recordSelectionReplacement(
            beforePrefix = "আমি ",
            selectedText = "ভালো ",
            replacementText = "সুস্থ "
        )
        assertEquals(10, undoRedo.getLastKnownCursorPosition())

        val undoReplace = undoRedo.undo(currentTextBeforeCursor = "আমি সুস্থ ")
        assertNotNull(undoReplace)
        assertEquals("আমি ভালো ", undoReplace?.beforeText)
        assertEquals("আমি সুস্থ ", undoReplace?.afterText)
        assertTrue(undoReplace?.replacedSelection == true)
        assertEquals(4, undoReplace?.selectionStartBefore)
        assertEquals(9, undoReplace?.selectionEndBefore)
        assertEquals("ভালো ", undoReplace?.selectedTextBefore)
        assertEquals(9, undoReplace?.cursorBefore)
        assertEquals(10, undoReplace?.cursorAfter)
        assertEquals(9, undoRedo.getLastKnownCursorPosition())

        // 4. Active multi-character selection blocks unsafe Undo/Redo
        undoRedo.recordEdit(beforeText = "আমি ভালো ", afterText = "আমি ভালো আছি ")
        undoRedo.onSelectionChanged(oldSelStart = 9, oldSelEnd = 9, newSelStart = 0, newSelEnd = 4, isInternalEdit = false)
        assertFalse(undoRedo.canUndo())
        assertNull(undoRedo.undo(currentTextBeforeCursor = "আমি "))
        // Collapsing selection back to cursor restores safe undo
        undoRedo.onSelectionChanged(oldSelStart = 0, oldSelEnd = 4, newSelStart = 13, newSelEnd = 13, isInternalEdit = false)
        assertTrue(undoRedo.canUndo())

        // 5. External edit divergence guard (never modify unrelated text)
        val divergentUndo = undoRedo.undo(currentTextBeforeCursor = "COMPLETELY_DIFFERENT_EXTERNAL_TEXT")
        assertNull(divergentUndo)
        assertFalse(undoRedo.canUndo())

        // 6. Switching between two editors where BOTH have fieldId == 0 clears history (restarting = false)
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

        // 7. Switching applications clears history even with same fieldId
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

        // 8. Never record sensitive or password-field content
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

    // 18. Fix 1 (v1.7.2): Mixed-Language Sentence Punctuation & Full-Sentence Selection Workflow
    @Test
    fun testMixedSentenceSuggestionsSurfacedInKeyboardWorkflow() {
        val userDict = BengaliUserDictionary()
        val phoneticEngine = BengaliPhoneticEngine(userDict)
        val suggestionEngine = BengaliSuggestionEngine(phoneticEngine)

        // Case A: "ami tomorrow office jabo"
        val candidatesA = suggestionEngine.getSuggestions(
            composingText = "jabo",
            previousWord = "office",
            languageMode = KeyboardLanguageMode.BANGLA_PHONETIC,
            autocorrectEnabled = true,
            nextWordPredictionEnabled = true,
            sentenceRawTokens = listOf("ami", "tomorrow", "office"),
            committedSentencePrefix = "আমি tomorrow office "
        )
        val textsA = candidatesA.map { it.text }
        assertTrue(textsA.contains("যাব"))
        assertTrue(textsA.contains("আমি tomorrow office যাব"))
        assertTrue(textsA.contains("আমি আগামীকাল অফিস যাব"))
        assertTrue(textsA.contains("আমি tomorrow অফিস যাব"))

        // Verify selecting a full-sentence suggestion replaces the clause cleanly without losing spacing
        val selectedFullA = candidatesA.first { it.text == "আমি আগামীকাল অফিস যাব" }
        val appliedA = suggestionEngine.applySelectedCandidateToEditorText(
            currentTextBeforeCursor = "আমি tomorrow office যাব",
            activeComposingPreview = "যাব",
            candidate = selectedFullA
        )
        assertEquals("আমি আগামীকাল অফিস যাব ", appliedA)

        // Case B: "ami tomorrow, office jabo" (mid-sentence comma preserved in workflow)
        val candidatesB = suggestionEngine.getSuggestions(
            composingText = "jabo",
            previousWord = "office",
            languageMode = KeyboardLanguageMode.BANGLA_PHONETIC,
            autocorrectEnabled = true,
            nextWordPredictionEnabled = true,
            sentenceRawTokens = listOf("ami", "tomorrow,", "office"),
            committedSentencePrefix = "আমি tomorrow, office "
        )
        val textsB = candidatesB.map { it.text }
        assertTrue(textsB.contains("আমি tomorrow, office যাব"))
        assertTrue(textsB.contains("আমি আগামীকাল, অফিস যাব"))
        assertTrue(textsB.contains("আমি tomorrow, অফিস যাব"))

        val selectedFullB = candidatesB.first { it.text == "আমি আগামীকাল, অফিস যাব" }
        val appliedB = suggestionEngine.applySelectedCandidateToEditorText(
            currentTextBeforeCursor = "আমি tomorrow, office যাব",
            activeComposingPreview = "যাব",
            candidate = selectedFullB
        )
        assertEquals("আমি আগামীকাল, অফিস যাব ", appliedB)

        // Case C: "ami tomorrow office jabo?" (question mark preserved during composing and after punctuation commit)
        val candidatesC = suggestionEngine.getSuggestions(
            composingText = "jabo?",
            previousWord = "office",
            languageMode = KeyboardLanguageMode.BANGLA_PHONETIC,
            autocorrectEnabled = true,
            nextWordPredictionEnabled = true,
            sentenceRawTokens = listOf("ami", "tomorrow", "office"),
            committedSentencePrefix = "আমি tomorrow office "
        )
        val textsC = candidatesC.map { it.text }
        assertTrue(textsC.contains("যাব?"))
        assertTrue(textsC.contains("আমি tomorrow office যাব?"))
        assertTrue(textsC.contains("আমি আগামীকাল অফিস যাব?"))
        assertTrue(textsC.contains("আমি tomorrow অফিস যাব?"))

        // Also verify when '?' was committed right after 'jabo' (composingText is empty, sentenceRawTokens has '?')
        val candidatesCAfterCommit = suggestionEngine.getSuggestions(
            composingText = "",
            previousWord = "যাব",
            languageMode = KeyboardLanguageMode.BANGLA_PHONETIC,
            autocorrectEnabled = true,
            nextWordPredictionEnabled = true,
            sentenceRawTokens = listOf("ami", "tomorrow", "office", "jabo", "?"),
            committedSentencePrefix = "আমি tomorrow office যাব? "
        )
        val selectedFullC = candidatesCAfterCommit.first { it.text == "আমি আগামীকাল অফিস যাব?" }
        val appliedC = suggestionEngine.applySelectedCandidateToEditorText(
            currentTextBeforeCursor = "আমি tomorrow office যাব? ",
            activeComposingPreview = "",
            candidate = selectedFullC
        )
        assertEquals("আমি আগামীকাল অফিস যাব? ", appliedC)

        // Case D: "ami ajke meeting kore bari jabo." (period and Bengali danda preserved)
        val candidatesD = suggestionEngine.getSuggestions(
            composingText = "jabo.",
            previousWord = "bari",
            languageMode = KeyboardLanguageMode.BANGLA_PHONETIC,
            autocorrectEnabled = true,
            nextWordPredictionEnabled = true,
            sentenceRawTokens = listOf("ami", "ajke", "meeting", "kore", "bari"),
            committedSentencePrefix = "আমি আজকে meeting করে বাড়ি "
        )
        val textsD = candidatesD.map { it.text }
        assertTrue(textsD.contains("যাব."))
        assertTrue(textsD.contains("আমি আজকে meeting করে বাড়ি যাব."))
        assertTrue(textsD.contains("আমি আজকে মিটিং করে বাড়ি যাব."))
        assertTrue(textsD.contains("আমি আজকে মিটিং করে বাড়ি যাব।"))

        val selectedFullD = candidatesD.first { it.text == "আমি আজকে মিটিং করে বাড়ি যাব." }
        val appliedD = suggestionEngine.applySelectedCandidateToEditorText(
            currentTextBeforeCursor = "আমি আজকে meeting করে বাড়ি যাব.",
            activeComposingPreview = "যাব.",
            candidate = selectedFullD
        )
        assertEquals("আমি আজকে মিটিং করে বাড়ি যাব. ", appliedD)

        // Unknown English technical tokens must never be translated or transliterated into meaningless Bengali
        val unknownEnglish = phoneticEngine.generateMixedSentenceCandidates("ami kubernetes cluster deploy korbo!")
        assertEquals("আমি kubernetes cluster deploy করবো!", unknownEnglish.first())
    }

    // In-memory implementation of android.content.SharedPreferences for realistic migration verification
    private class InMemorySharedPreferences(
        initialData: Map<String, Any?> = emptyMap()
    ) : SharedPreferences {
        private val data = LinkedHashMap<String, Any?>(initialData)

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val staged = LinkedHashMap<String, Any?>()
            private val removals = HashSet<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) removals.add(key)
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) data.clear()
                for (k in removals) data.remove(k)
                for ((k, v) in staged) {
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    // 19. Fix 3 (v1.7.2): Realistic SharedPreferences Personal Dictionary Migration Verification
    @Test
    fun testGenuineV160PersonalDictionaryMigration() {
        // Verify canonical SharedPreferences file name and legacy preference keys
        assertEquals("clipvault_personal_dictionary", BengaliUserDictionary.PREFS_NAME)
        assertEquals("personal_words_tsv", BengaliUserDictionary.KEY_LEGACY_PERSONAL_WORDS_V160)
        assertEquals("custom_shortcuts_tsv", BengaliUserDictionary.KEY_LEGACY_SHORTCUTS_V160)
        assertEquals("seeded_v160_defaults", BengaliUserDictionary.KEY_LEGACY_SEEDED_V160)

        val legacyPersonalRaw = listOf(
            "bhalobashi\tভালোবাসি\tbn\t1\t1700000000",     // Unmodified v1.6.0 auto-seeded demo entry
            "ami\tআমি নিজে\tbn\t4\t1700000100",            // User-modified entry for a seeded key
            "suvash\tসুভাষ পুরকায়স্থ\tbn\t7\t1700000200"  // User-created custom entry
        ).joinToString("\n")

        val legacyShortcutsRaw = listOf(
            "omw\ton my way\ten",                            // Unmodified v1.6.0 auto-seeded demo shortcut
            "addr\tSylhet, Bangladesh\ten"                   // User-created custom shortcut
        ).joinToString("\n")

        val sharedPrefs = InMemorySharedPreferences(
            mapOf<String, Any?>(
                BengaliUserDictionary.KEY_LEGACY_PERSONAL_WORDS_V160 to legacyPersonalRaw,
                BengaliUserDictionary.KEY_LEGACY_SHORTCUTS_V160 to legacyShortcutsRaw,
                BengaliUserDictionary.KEY_LEGACY_SEEDED_V160 to true
            )
        )

        val userDict = BengaliUserDictionary()
        userDict.migrateFromLegacyPreferencesIfNeeded(sharedPrefs)

        // 1. Unmodified auto-seeded demo entry "bhalobashi -> ভালোবাসি" (freq 1) is filtered out
        assertNull(userDict.lookupPersonalWord("bhalobashi"))
        // 2. User-modified entry "ami -> আমি নিজে" is preserved
        assertEquals("আমি নিজে", userDict.lookupPersonalWord("ami"))
        // 3. User-created entry "suvash -> সুভাষ পুরকায়স্থ" is preserved
        assertEquals("সুভাষ পুরকায়স্থ", userDict.lookupPersonalWord("suvash"))
        // 4. Unmodified auto-seeded shortcut "omw" is filtered out, user-created "addr" is preserved
        assertNull(userDict.lookupShortcut("omw"))
        assertEquals("Sylhet, Bangladesh", userDict.lookupShortcut("addr")?.expansion)

        // 5. Verify SharedPreferences state after migration:
        //    - Migration completion flag is set
        //    - Legacy keys are NEVER deleted (non-destructive guarantee)
        assertTrue(sharedPrefs.getBoolean(BengaliUserDictionary.KEY_MIGRATION_V171_COMPLETE, false))
        assertEquals(legacyPersonalRaw, sharedPrefs.getString(BengaliUserDictionary.KEY_LEGACY_PERSONAL_WORDS_V160, null))
        assertEquals(legacyShortcutsRaw, sharedPrefs.getString(BengaliUserDictionary.KEY_LEGACY_SHORTCUTS_V160, null))

        // 6. Verify idempotency: adding a new word and re-running migrateFromLegacyPreferencesIfNeeded
        //    on a fresh BengaliUserDictionary instance loads the migrated state without duplicating or losing data
        userDict.addOrUpdateWord("dhaka", "ঢাকা", KeyboardLanguageMode.BANGLA_PHONETIC)
        sharedPrefs.edit().putString(BengaliUserDictionary.KEY_PERSONAL_WORDS_V171, userDict.serializePersonalWords()).apply()

        val reloadedDict = BengaliUserDictionary()
        reloadedDict.migrateFromLegacyPreferencesIfNeeded(sharedPrefs)
        assertEquals("আমি নিজে", reloadedDict.lookupPersonalWord("ami"))
        assertEquals("সুভাষ পুরকায়স্থ", reloadedDict.lookupPersonalWord("suvash"))
        assertEquals("ঢাকা", reloadedDict.lookupPersonalWord("dhaka"))
        assertEquals("Sylhet, Bangladesh", reloadedDict.lookupShortcut("addr")?.expansion)
    }

    // 20. Fix 5 (v1.7.2): Bengali Auto Correction, Unicode Spelling Corrections & Ambiguous Alternatives
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

    // 21. Fix 4 (v1.7.2): Bengali Font Rendering Orthographic Integrity & Crash Isolation
    @Test
    fun testBengaliUnicodeIntegrityAndCrashIsolation() {
        // 1. Verify কার, মাত্রা, যুক্তাক্ষর, রেফ, য-ফলা, র-ফলা, হসন্ত, Bengali numerals, and দাড়ি (।)
        assertTrue(BengaliUnicodeRenderValidator.containsKar("আমি"))
        assertTrue(BengaliUnicodeRenderValidator.containsMatra("আমার নাম সুভাষ"))
        assertTrue(BengaliUnicodeRenderValidator.containsHasantaOrJuktakkhor("সুন্দর"))
        assertTrue(BengaliUnicodeRenderValidator.containsHasantaOrJuktakkhor("বৃষ্টি"))
        assertTrue(BengaliUnicodeRenderValidator.containsReph("কর্ম"))
        assertTrue(BengaliUnicodeRenderValidator.containsJaPhala("ব্যবহার"))
        assertTrue(BengaliUnicodeRenderValidator.containsRaPhala("প্রিয়"))
        assertTrue(BengaliUnicodeRenderValidator.containsPhala("স্বাগতম"))
        assertTrue(BengaliUnicodeRenderValidator.containsExplicitHasanta("হঠাৎ"))
        assertTrue(BengaliUnicodeRenderValidator.containsExplicitHasanta("ক্"))
        assertTrue(BengaliUnicodeRenderValidator.containsBengaliNumerals("০১২৩৪৫৬৭৮৯"))
        assertTrue(BengaliUnicodeRenderValidator.containsBengaliPunctuation("আমার নাম সুভাষ।"))
        assertTrue(BengaliUnicodeRenderValidator.verifyOrthographicIntegrity("আমি বাংলায় গান গাই। কর্ম, ব্যবহার, প্রিয়, হঠাৎ — ২০২৬"))

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

    // 22. Fix 5 & Fix 6 (v1.7.2): Bounded/Clearable Adaptive Ranking & Measured Performance Telemetry
    @Test
    fun testSuggestionQualityAdaptiveLearningBoundsAndMeasuredPerformance() {
        val initStartNs = System.nanoTime()
        val userDict = BengaliUserDictionary()
        val phoneticEngine = BengaliPhoneticEngine(userDict)
        val suggestionEngine = BengaliSuggestionEngine(phoneticEngine)
        val initElapsedMs = (System.nanoTime() - initStartNs) / 1_000_000.0

        // 1. Verify adaptive learning is bounded (<= 120 entries), persisted separately from Personal Dictionary, and clearable
        for (i in 1..160) {
            suggestionEngine.onCandidateSelectedByUser("word$i", "শব্দ$i", KeyboardLanguageMode.BANGLA_PHONETIC)
        }
        assertEquals(0, userDict.getAllPersonalWords().size)
        assertTrue(userDict.getAdaptiveLearningEntryCount() <= BengaliUserDictionary.MAX_ADAPTIVE_ENTRIES)
        assertEquals(BengaliUserDictionary.MAX_ADAPTIVE_ENTRIES, userDict.getAdaptiveLearningEntryCount())

        // Verify round-trip persistence of bounded adaptive ranking via InMemorySharedPreferences
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString(BengaliUserDictionary.KEY_ADAPTIVE_RANKING_V172, userDict.serializeAdaptiveRanking()).apply()
        val reloadedDict = BengaliUserDictionary()
        reloadedDict.migrateFromLegacyPreferencesIfNeeded(prefs)
        assertEquals(BengaliUserDictionary.MAX_ADAPTIVE_ENTRIES, reloadedDict.getAdaptiveLearningEntryCount())

        // Clear adaptive learning and verify it resets cleanly to 0 without touching Personal Dictionary
        userDict.addOrUpdateWord("suvash", "সুভাষ", KeyboardLanguageMode.BANGLA_PHONETIC)
        userDict.clearAdaptiveLearning()
        assertEquals(0, userDict.getAdaptiveLearningEntryCount())
        assertEquals("সুভাষ", userDict.lookupPersonalWord("suvash"))

        // 2. Measure First-Key response time, Subsequent-Key response time, Suggestion latency, and Prolonged Typing Memory usage
        val runtime = Runtime.getRuntime()
        val memBeforeBytes = runtime.totalMemory() - runtime.freeMemory()

        val firstKeyStartNs = System.nanoTime()
        val firstKeyCandidates = suggestionEngine.getSuggestions("a", "", KeyboardLanguageMode.BANGLA_PHONETIC)
        val firstKeyElapsedMs = (System.nanoTime() - firstKeyStartNs) / 1_000_000.0
        assertTrue(firstKeyCandidates.isNotEmpty())

        val subsequentWords = listOf(
            "am", "ami", "bha", "bhalo", "bangla", "bangladesh",
            "tom", "tomorrow", "off", "office", "jab", "jabo"
        )
        var totalSubsequentNs = 0L
        for (token in subsequentWords) {
            val t0 = System.nanoTime()
            val list = suggestionEngine.getSuggestions(token, "আমি", KeyboardLanguageMode.BANGLA_PHONETIC)
            totalSubsequentNs += (System.nanoTime() - t0)
            assertTrue(list.isNotEmpty())
        }
        val avgSubsequentKeyMs = (totalSubsequentNs / subsequentWords.size.toDouble()) / 1_000_000.0

        // Prolonged typing session simulation (500 mixed sentence & word suggestion evaluations)
        val prolongedStartNs = System.nanoTime()
        for (iteration in 1..500) {
            val token = subsequentWords[iteration % subsequentWords.size]
            suggestionEngine.getSuggestions(
                composingText = token,
                previousWord = "office",
                languageMode = KeyboardLanguageMode.BANGLA_PHONETIC,
                sentenceRawTokens = listOf("ami", "tomorrow,", "office"),
                committedSentencePrefix = "আমি tomorrow, office "
            )
        }
        val avgProlongedSuggestionMs = ((System.nanoTime() - prolongedStartNs) / 500.0) / 1_000_000.0
        val memAfterBytes = runtime.totalMemory() - runtime.freeMemory()
        val netHeapDeltaKb = ((memAfterBytes - memBeforeBytes).coerceAtLeast(0L)) / 1024L

        println(
            "CLIPVAULT_V172_TELEMETRY: " +
                "initMs=${String.format(java.util.Locale.US, "%.3f", initElapsedMs)}, " +
                "firstKeyMs=${String.format(java.util.Locale.US, "%.3f", firstKeyElapsedMs)}, " +
                "subsequentKeyAvgMs=${String.format(java.util.Locale.US, "%.3f", avgSubsequentKeyMs)}, " +
                "prolonged500AvgMs=${String.format(java.util.Locale.US, "%.3f", avgProlongedSuggestionMs)}, " +
                "netHeapDeltaKb=$netHeapDeltaKb"
        )
    }

    // 23. Requirement 1 (v1.7.2): Prevent Duplicate Clipboard Content Across All 8 Required Scenarios
    @Test
    fun testPreventDuplicateClipboardContentAllEightScenarios() = runBlocking {
        val pinnedOriginal = ClipEntity(
            id = "pinned-fav-100",
            content = "Important API Docs https://clipvault.dev",
            category = ClipCategory.URL,
            createdAt = 5_000L,
            isPinned = true,
            tags = listOf("Favorite", "Work"),
            sourceApp = "Chrome"
        )
        val dao = FakeClipDao(listOf(pinnedOriginal))
        val helper = ClipboardServiceHelper(dao, Dispatchers.Unconfined)

        // Scenario 1: Copying a saved ClipVault item again (both via recordAppOriginatedCopy and DB duplicate check)
        val savedItem = helper.processQueuedItem("Saved Note From ClipVault", "ClipVault")
        assertNotNull(savedItem)
        assertEquals(2, dao.snapshot().size)

        // User taps Copy inside ClipVault -> records app-originated fingerprint -> OS fires clipboard listener
        ClipboardServiceHelper.recordAppOriginatedCopy("Saved Note From ClipVault")
        val selfRecapture = helper.processQueuedItem("Saved Note From ClipVault", "Auto-Capture")
        assertNull(selfRecapture)
        assertEquals(2, dao.snapshot().size)

        // Even after fingerprint TTL expires, database content_hash check still blocks duplication
        ClipboardServiceHelper.clearGlobalAppOriginatedCopies()
        val dbBlockedDuplicate = helper.processQueuedItem("Saved Note From ClipVault", "Auto-Capture")
        assertNull(dbBlockedDuplicate)
        assertEquals(2, dao.snapshot().size)

        // Scenario 2: Copying identical text twice from an external app
        val extFirst = helper.processQueuedItem("External App Text", "WhatsApp")
        val extSecond = helper.processQueuedItem("External App Text", "WhatsApp")
        assertNotNull(extFirst)
        assertNull(extSecond)
        assertEquals(3, dao.snapshot().size)

        // Scenario 3: Copying different text
        val diffText = helper.processQueuedItem("Genuinely New Text", "Firefox")
        assertNotNull(diffText)
        assertEquals(4, dao.snapshot().size)

        // Scenario 4: Copying the same text rapidly multiple times
        repeat(6) {
            helper.enqueueClip("Rapid Burst Text", "Telegram")
        }
        delay(60)
        assertEquals(1, dao.snapshot().count { it.content == "Rapid Burst Text" })
        assertEquals(5, dao.snapshot().size)

        // Scenario 5: Unicode-equivalent text (NFC composed vs NFD decomposed)
        val composedCafe = "Café Résumé" // \u00E9
        val decomposedCafe = "Cafe\u0301 Re\u0301sume\u0301" // e + combining acute accent
        assertTrue(ClipboardContentNormalizer.areEquivalent(composedCafe, decomposedCafe))
        assertEquals(
            ClipboardContentNormalizer.computeContentHash(composedCafe),
            ClipboardContentNormalizer.computeContentHash(decomposedCafe)
        )
        val firstUnicode = helper.processQueuedItem(composedCafe, "Notes")
        val secondUnicode = helper.processQueuedItem(decomposedCafe, "Notes")
        assertNotNull(firstUnicode)
        assertNull(secondUnicode)
        assertEquals(6, dao.snapshot().size)

        // Scenario 6: Text containing line breaks (\r\n vs \n) and trailing whitespace
        val crlfText = "Line One\r\nLine Two\r\nLine Three   "
        val lfText = "Line One\nLine Two\nLine Three"
        assertTrue(ClipboardContentNormalizer.areEquivalent(crlfText, lfText))
        val firstMultiline = helper.processQueuedItem(crlfText, "Editor")
        val secondMultiline = helper.processQueuedItem(lfText, "Editor")
        assertNotNull(firstMultiline)
        assertNull(secondMultiline)
        // Verify original internal line structure is preserved cleanly
        assertEquals("Line One\nLine Two\nLine Three", firstMultiline?.content)

        // Scenario 7: Existing pinned and favorite/tagged items preserve ID, timestamp, category, pin, and tags
        val duplicateOfPinned = helper.processQueuedItem("  Important API Docs https://clipvault.dev  ", "ExternalBrowser")
        assertNull(duplicateOfPinned)
        val preservedPinned = dao.getClipById("pinned-fav-100")
        assertNotNull(preservedPinned)
        assertEquals("pinned-fav-100", preservedPinned?.id)
        assertEquals(5_000L, preservedPinned?.createdAt)
        assertEquals(ClipCategory.URL, preservedPinned?.category)
        assertTrue(preservedPinned?.isPinned == true)
        assertEquals(listOf("Favorite", "Work"), preservedPinned?.tags)

        // Scenario 8: Concurrent clipboard events across multiple coroutines
        val concurrentResults = (1..12).map { idx ->
            async(Dispatchers.Default) {
                helper.processQueuedItem("Concurrent Shared Payload", "Source-$idx")
            }
        }.awaitAll()
        assertEquals(1, concurrentResults.filterNotNull().size)
        assertEquals(1, dao.snapshot().count { it.content == "Concurrent Shared Payload" })
    }

    // 24. Requirement 2 (v1.7.2): Bengali Layout Numeric Row & Secondary Digit Output
    @Test
    fun testBengaliLayoutNumericRowAndKeyOutputs() {
        // 1. Bengali layout numeric row displays ১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯ ০ by default
        val banglaDigits = ClipVaultInputMethodService.resolveNumberRowDigits(KeyboardLanguageMode.BANGLA_PHONETIC)
        assertEquals(listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০"), banglaDigits)

        // 2. English layout numeric row displays 1 2 3 4 5 6 7 8 9 0
        val englishDigits = ClipVaultInputMethodService.resolveNumberRowDigits(KeyboardLanguageMode.ENGLISH)
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), englishDigits)

        // 3. Top-row secondary symbols (Q..P = indices 0..9) resolve to ১..০ in Bengali mode and 1..0 in English mode
        val banglaSecondaryTopRow = (0..9).map { idx ->
            ClipVaultInputMethodService.resolveSecondarySymbol(idx, KeyboardLanguageMode.BANGLA_PHONETIC, true)
        }
        assertEquals(listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০"), banglaSecondaryTopRow)

        val englishSecondaryTopRow = (0..9).map { idx ->
            ClipVaultInputMethodService.resolveSecondarySymbol(idx, KeyboardLanguageMode.ENGLISH, true)
        }
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), englishSecondaryTopRow)

        // 4. Symbol Page 1 top row outputs ১..০ in Bengali mode and 1..0 in English mode
        assertEquals(
            "১",
            ClipVaultInputMethodService.resolveKeyOutput(
                baseLetter = "q",
                index = 0,
                shiftState = ClipVaultInputMethodService.ShiftState.LOWERCASE,
                symbolPage = ClipVaultInputMethodService.SymbolPage.NUMBERS_SYMBOLS_1,
                mode = KeyboardLanguageMode.BANGLA_PHONETIC,
                useBengaliNumerals = true
            )
        )
        assertEquals(
            "০",
            ClipVaultInputMethodService.resolveKeyOutput(
                baseLetter = "p",
                index = 9,
                shiftState = ClipVaultInputMethodService.ShiftState.LOWERCASE,
                symbolPage = ClipVaultInputMethodService.SymbolPage.NUMBERS_SYMBOLS_1,
                mode = KeyboardLanguageMode.BANGLA_PHONETIC,
                useBengaliNumerals = true
            )
        )
    }

    // 25. Requirement 3 (v1.7.2): Avro-Grade Bengali Phonetic Transliteration & All 18 Complex Conjuncts
    @Test
    fun testAvroGradeBengaliPhoneticTransliterationAndAll18ComplexConjuncts() {
        val userDict = BengaliUserDictionary()
        val phoneticEngine = BengaliPhoneticEngine(userDict)
        val suggestionEngine = BengaliSuggestionEngine(phoneticEngine)

        // 1. Verify all 16 mandatory examples from Requirement 3
        val bektigotoCandidates = suggestionEngine.getSuggestions("bektigoto", "", KeyboardLanguageMode.BANGLA_PHONETIC).map { it.text }
        assertEquals("ব্যক্তিগত", phoneticEngine.transliterateWord("bektigoto"))
        assertTrue(bektigotoCandidates.contains("ব্যক্তিগত"))
        assertTrue(bektigotoCandidates.contains("ব্যক্তিগতভাবে"))

        assertEquals("বিদ্যুৎ", phoneticEngine.transliterateWord("bidyut"))
        assertEquals("কৃষি", phoneticEngine.transliterateWord("krishi"))
        assertEquals("চাঁদ", phoneticEngine.transliterateWord("chad"))
        assertEquals("উৎসব", phoneticEngine.transliterateWord("utshob"))
        assertEquals("হ্যাঁ", phoneticEngine.transliterateWord("ha"))
        assertEquals("হ্যাঁ", phoneticEngine.transliterateWord("haa"))
        assertEquals("ভালো", phoneticEngine.transliterateWord("valo"))
        assertEquals("ভালো", phoneticEngine.transliterateWord("bhalo"))
        assertEquals("করছি", phoneticEngine.transliterateWord("korsi"))
        assertEquals("ফেসবুক", phoneticEngine.transliterateWord("facebook"))
        assertEquals("বিকাশ", phoneticEngine.transliterateWord("bkash"))
        assertEquals("অঙ্ক", phoneticEngine.transliterateWord("onko"))
        assertEquals("বাক্য", phoneticEngine.transliterateWord("bakko"))
        assertEquals("পুরকায়স্থ", phoneticEngine.transliterateWord("purkaystha"))
        assertEquals("ধর্ম", phoneticEngine.transliterateWord("dhormo"))

        // 2. Verify all 18 mandatory complex conjuncts in real Bengali words:
        // ক্ত, ক্ষ, জ্ঞ, ত্র, দ্ধ, দ্য, ন্দ, ন্ধ, ম্প, ষ্ট, স্থ, স্ক, স্ত, ন্ত, ত্ত, দ্ম, হ্ন, ল্প
        val conjunctWordCases = listOf(
            Triple("shokti", "শক্তি", "ক্ত"),
            Triple("shikkha", "শিক্ষা", "ক্ষ"),
            Triple("biggan", "বিজ্ঞান", "জ্ঞ"),
            Triple("chatro", "ছাত্র", "ত্র"),
            Triple("buddhi", "বুদ্ধি", "দ্ধ"),
            Triple("bidyut", "বিদ্যুৎ", "দ্য"),
            Triple("anondo", "আনন্দ", "ন্দ"),
            Triple("bondhu", "বন্ধু", "ন্ধ"),
            Triple("shomporko", "সম্পর্ক", "ম্প"),
            Triple("kosto", "কষ্ট", "ষ্ট"),
            Triple("shastho", "স্বাস্থ্য", "স্থ"),
            Triple("nomoshkar", "নমস্কার", "স্ক"),
            Triple("rasta", "রাস্তা", "স্ত"),
            Triple("shanto", "শান্ত", "ন্ত"),
            Triple("uttor", "উত্তর", "ত্ত"),
            Triple("podmo", "পদ্ম", "দ্ম"),
            Triple("chihno", "চিহ্ন", "হ্ন"),
            Triple("golpo", "গল্প", "ল্প")
        )
        for ((roman, expectedBangla, conjunct) in conjunctWordCases) {
            val transliterated = phoneticEngine.transliterateWord(roman)
            assertEquals("Expected $roman -> $expectedBangla", expectedBangla, transliterated)
            assertTrue("Expected $transliterated to contain conjunct $conjunct", transliterated.contains(conjunct))
        }
    }

    // 26. Requirement 4 (v1.7.2): Enter Key Behaviour in WhatsApp, Messenger, Gemini, Search, and Notes
    @Test
    fun testEnterKeyBehaviourInWhatsAppMessengerGeminiSearchAndNotes() {
        // 1. WhatsApp / Facebook Messenger multiline chat composer (TYPE_TEXT_FLAG_MULTI_LINE + IME_ACTION_SEND or IME_ACTION_DONE)
        val whatsAppInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val whatsAppImeOptions = EditorInfo.IME_ACTION_SEND
        assertEquals(
            ClipVaultInputMethodService.EnterKeyDisposition.INSERT_NEWLINE,
            ClipVaultInputMethodService.resolveEnterKeyDisposition(whatsAppInputType, whatsAppImeOptions)
        )
        assertEquals("↵", ClipVaultInputMethodService.resolveEnterKeyLabel(whatsAppInputType, whatsAppImeOptions))

        val messengerImeOptions = EditorInfo.IME_ACTION_DONE
        assertEquals(
            ClipVaultInputMethodService.EnterKeyDisposition.INSERT_NEWLINE,
            ClipVaultInputMethodService.resolveEnterKeyDisposition(whatsAppInputType, messengerImeOptions)
        )

        // 2. Gemini / ChatGPT / Notes / Email body composer (TYPE_TEXT_FLAG_IME_MULTI_LINE or IME_FLAG_NO_ENTER_ACTION)
        val geminiInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE
        val geminiImeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertEquals(
            ClipVaultInputMethodService.EnterKeyDisposition.INSERT_NEWLINE,
            ClipVaultInputMethodService.resolveEnterKeyDisposition(geminiInputType, geminiImeOptions)
        )
        assertEquals("↵", ClipVaultInputMethodService.resolveEnterKeyLabel(geminiInputType, geminiImeOptions))

        // 3. Single-line Search input (e.g. Google Search, YouTube Search)
        val searchInputType = InputType.TYPE_CLASS_TEXT
        val searchImeOptions = EditorInfo.IME_ACTION_SEARCH
        assertEquals(
            ClipVaultInputMethodService.EnterKeyDisposition.PERFORM_EDITOR_ACTION,
            ClipVaultInputMethodService.resolveEnterKeyDisposition(searchInputType, searchImeOptions)
        )
        assertEquals("🔍", ClipVaultInputMethodService.resolveEnterKeyLabel(searchInputType, searchImeOptions))

        // 4. Single-line Browser URL bar (IME_ACTION_GO) and Form fields (IME_ACTION_NEXT, IME_ACTION_DONE)
        assertEquals(
            ClipVaultInputMethodService.EnterKeyDisposition.PERFORM_EDITOR_ACTION,
            ClipVaultInputMethodService.resolveEnterKeyDisposition(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, EditorInfo.IME_ACTION_GO)
        )
        assertEquals(
            ClipVaultInputMethodService.EnterKeyDisposition.PERFORM_EDITOR_ACTION,
            ClipVaultInputMethodService.resolveEnterKeyDisposition(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_NEXT)
        )
    }

    // 27. Requirements 5, 6, 7 (v1.7.2): Toolbar Clipboard Icon, Duplicate About Removal, and Keyboard Picker Display Name
    @Test
    fun testToolbarClipboardIconDuplicateAboutRemovalAndKeyboardPickerDisplayName() {
        // Requirement 5: Toolbar uses ic_content_copy clipboard icon instead of "CV" text
        assertEquals("ic_content_copy", ClipVaultInputMethodService.TOOLBAR_SHORTCUT_ICON_RESOURCE)
        assertEquals("CLIPBOARD_SHORTCUT", ClipVaultInputMethodService.TOP_TOOLBAR_ACTION_IDS.first())

        // Requirement 6: Duplicate About Developer button is removed from main toolbar (kept inside MORE_OPTIONS)
        assertFalse(ClipVaultInputMethodService.TOOLBAR_HAS_DUPLICATE_ABOUT_BUTTON)
        assertFalse(ClipVaultInputMethodService.TOP_TOOLBAR_ACTION_IDS.contains("ABOUT_DEVELOPER"))
        assertTrue(ClipVaultInputMethodService.TOP_TOOLBAR_ACTION_IDS.contains("MORE_OPTIONS"))

        // Requirement 7: Android Keyboard Picker Display Name and Subtype Labels
        assertEquals("ClipVault Keyboard", ClipVaultInputMethodService.IME_DISPLAY_NAME)
        assertEquals("English (US)", ClipVaultInputMethodService.IME_SUBTYPE_LABEL_EN)
        assertEquals("বাংলা", ClipVaultInputMethodService.IME_SUBTYPE_LABEL_BN)

        // Final Visual Polish Verification: Unified corner radius, toolbar/suggestion dimensions, and responsive layout helpers
        assertEquals(8, ClipVaultInputMethodService.KEY_CORNER_RADIUS_DP)
        assertEquals(34, ClipVaultInputMethodService.TOOLBAR_BUTTON_HEIGHT_DP)
        assertEquals(34, ClipVaultInputMethodService.SUGGESTION_CHIP_HEIGHT_DP)
        assertEquals(10, ClipVaultInputMethodService.resolveResponsiveRow2InsetDp(340, OneHandedMode.OFF))
        assertEquals(14, ClipVaultInputMethodService.resolveResponsiveRow2InsetDp(392, OneHandedMode.OFF))
        assertEquals(18, ClipVaultInputMethodService.resolveResponsiveRow2InsetDp(440, OneHandedMode.OFF))
        assertEquals(6, ClipVaultInputMethodService.resolveResponsiveRow2InsetDp(392, OneHandedMode.LEFT))
        assertEquals(40, ClipVaultInputMethodService.resolveNumberRowHeightDp(50))
    }
}
