package com.clipvault.android

import android.text.InputType
import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.ime.ClipVaultInputMethodService
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
        // Newest clip appears first: C, B, A
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

        // Subsequent retry of the exact same text must succeed because lastProcessedText was not updated on failure
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
        // First registration succeeds
        if (ClipVaultInputMethodService.ListenerRegistrationController.markRegistered(isRegistered)) {
            isRegistered = true
        }
        assertTrue(isRegistered)

        // Duplicate registration attempt while already registered is prevented
        val duplicateAllowed = ClipVaultInputMethodService.ListenerRegistrationController.markRegistered(isRegistered)
        assertFalse(duplicateAllowed)

        // Unregistration succeeds when registered
        if (ClipVaultInputMethodService.ListenerRegistrationController.markUnregistered(isRegistered)) {
            isRegistered = false
        }
        assertFalse(isRegistered)

        // Second unregistration is a safe no-op
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

    // 10. Settings status, Gboard vs ClipVault Keyboard detection, and v1.4.0 Keyboard UI state machine
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

        val gboardWithForegroundFocus = AccessibilityPermissionHelper.resolveAccurateCaptureState(
            autoCaptureEnabled = true,
            isAccessibilityEnabled = false,
            isImeEnabled = false,
            isImeDefault = false,
            hasForegroundFocus = true
        )
        assertEquals("Clipboard Capture Active", gboardWithForegroundFocus)

        // Verify v1.4.0 Keyboard Height presets (Compact / Normal / Tall) & 24-26sp typography
        val compact = ClipVaultInputMethodService.KeyboardHeightPreset.fromKey("COMPACT")
        val normal = ClipVaultInputMethodService.KeyboardHeightPreset.fromKey("NORMAL")
        val tall = ClipVaultInputMethodService.KeyboardHeightPreset.fromKey("TALL")
        assertTrue(compact.letterSp in 24f..26f)
        assertTrue(normal.letterSp in 24f..26f)
        assertTrue(tall.letterSp in 24f..26f)
        assertTrue(compact.keyHeightDp < normal.keyHeightDp && normal.keyHeightDp < tall.keyHeightDp)

        // Verify Shift -> Caps Lock -> Lowercase state machine
        val s1 = ClipVaultInputMethodService.computeNextShiftState(
            ClipVaultInputMethodService.ShiftState.LOWERCASE,
            elapsedSinceLastTapMs = 1000L
        )
        assertEquals(ClipVaultInputMethodService.ShiftState.SHIFTED_SINGLE, s1)

        // Rapid second tap (< 360ms) enters CAPS_LOCK
        val s2 = ClipVaultInputMethodService.computeNextShiftState(s1, elapsedSinceLastTapMs = 180L)
        assertEquals(ClipVaultInputMethodService.ShiftState.CAPS_LOCK, s2)

        // Next tap exits CAPS_LOCK back to LOWERCASE
        val s3 = ClipVaultInputMethodService.computeNextShiftState(s2, elapsedSinceLastTapMs = 500L)
        assertEquals(ClipVaultInputMethodService.ShiftState.LOWERCASE, s3)

        // Verify 123 / ABC symbol mode toggle and character output
        val symPage = ClipVaultInputMethodService.toggleSymbolMode(ClipVaultInputMethodService.SymbolPage.LETTERS)
        assertEquals(ClipVaultInputMethodService.SymbolPage.NUMBERS_SYMBOLS_1, symPage)
        assertEquals("1", ClipVaultInputMethodService.resolveKeyOutput("q", 0, s3, symPage))
        assertEquals("Q", ClipVaultInputMethodService.resolveKeyOutput("q", 0, s2, ClipVaultInputMethodService.SymbolPage.LETTERS))

        // Verify v1.5.0 top-right secondary symbol mapping (Q=1..P=0 and A=@..L=))
        assertEquals("1", ClipVaultInputMethodService.resolveSecondarySymbol(0))
        assertEquals("0", ClipVaultInputMethodService.resolveSecondarySymbol(9))
        assertEquals("@", ClipVaultInputMethodService.resolveSecondarySymbol(10))
        assertEquals(")", ClipVaultInputMethodService.resolveSecondarySymbol(18))

        // Verify v1.5.0 Keyboard Theme & Vibration Intensity cyclers
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

        // Verify v1.5.0 One-Time First-Launch Onboarding flag logic
        assertTrue(AccessibilityPermissionHelper.shouldShowFirstLaunchOnboarding(onboardingAlreadyShown = false))
        assertFalse(AccessibilityPermissionHelper.shouldShowFirstLaunchOnboarding(onboardingAlreadyShown = true))
    }
}
