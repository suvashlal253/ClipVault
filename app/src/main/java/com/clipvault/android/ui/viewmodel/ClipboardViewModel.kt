package com.clipvault.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.domain.repository.ClipboardRepository
import com.clipvault.android.security.SecurityHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class ClipboardUiState(
    val clips: List<ClipEntity> = emptyList(),
    val vaultClips: List<ClipEntity> = emptyList(),
    val selectedCategory: ClipCategory? = null,
    val selectedCustomTag: String? = null,
    val availableCustomTags: List<String> = listOf("Office", "Personal", "Study"),
    val selectedClipIds: Set<String> = emptySet(),
    val showPinnedOnly: Boolean = false,
    val selectedDateMillis: Long? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isVaultUnlocked: Boolean = false,
    val totalCount: Int = 0
)

sealed interface ClipboardIntent {
    data class Search(val query: String) : ClipboardIntent
    data class SelectCategory(val category: ClipCategory?) : ClipboardIntent
    data class SelectCustomTag(val tag: String?) : ClipboardIntent
    data class CreateCustomTag(val tag: String) : ClipboardIntent
    data class ToggleClipTag(val clipId: String, val tag: String) : ClipboardIntent
    data class ToggleSelectClip(val clipId: String) : ClipboardIntent
    data object SelectAllClips : ClipboardIntent
    data object ClearSelection : ClipboardIntent
    data object BatchDeleteSelected : ClipboardIntent
    data object BatchMoveSelectedToVault : ClipboardIntent
    data class BatchTagSelected(val tag: String) : ClipboardIntent
    data object TogglePinnedOnly : ClipboardIntent
    data class FilterByDate(val dateMillis: Long?) : ClipboardIntent
    data class EditClip(val clipId: String, val newContent: String) : ClipboardIntent
    data class TogglePin(val clipId: String, val currentStatus: Boolean) : ClipboardIntent
    data class ToggleVault(val clipId: String, val currentStatus: Boolean, val rawText: String) : ClipboardIntent
    data class DeleteClip(val clipId: String) : ClipboardIntent
    data class SaveNewClip(val content: String, val sourceApp: String? = "Manual") : ClipboardIntent
    data object UnlockVault : ClipboardIntent
    data object LockVault : ClipboardIntent
    data object ClearUnpinned : ClipboardIntent
    data object ClearAllHistory : ClipboardIntent
}

@HiltViewModel
class ClipboardViewModel @Inject constructor(
    private val repository: ClipboardRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<ClipCategory?>(null)
    private val _selectedCustomTag = MutableStateFlow<String?>(null)
    private val _availableCustomTags = MutableStateFlow(listOf("Office", "Personal", "Study"))
    private val _selectedClipIds = MutableStateFlow<Set<String>>(emptySet())
    private val _showPinnedOnly = MutableStateFlow(false)
    private val _selectedDate = MutableStateFlow<Long?>(null)
    private val _isVaultUnlocked = MutableStateFlow(false)
    // Epoch counter that forces immediate Flow re-evaluation and clears any cached items on ClearAllHistory
    private val _clearEpoch = MutableStateFlow(0L)

    // Unbounded FIFO Channel + Mutex so rapid SaveNewClip intents are processed strictly in order
    private val saveClipQueue = Channel<ClipboardIntent.SaveNewClip>(Channel.UNLIMITED)
    private val saveMutex = Mutex()

    init {
        viewModelScope.launch {
            for (intent in saveClipQueue) {
                saveMutex.withLock {
                    if (intent.content.isNotBlank()) {
                        repository.saveNewClip(
                            content = intent.content.trim(),
                            sourceApp = intent.sourceApp,
                            autoVault = false
                        )
                    }
                }
            }
        }
    }

    val uiState: StateFlow<ClipboardUiState> = combine(
        _searchQuery,
        _selectedCategory,
        _selectedCustomTag,
        _availableCustomTags,
        _selectedClipIds,
        _showPinnedOnly,
        _selectedDate,
        _isVaultUnlocked,
        _clearEpoch
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        CombinedFilter(
            query = values[0] as String,
            category = values[1] as ClipCategory?,
            customTag = values[2] as String?,
            availableTags = values[3] as List<String>,
            selectedIds = values[4] as Set<String>,
            pinnedOnly = values[5] as Boolean,
            selectedDate = values[6] as Long?,
            isVaultUnlocked = values[7] as Boolean,
            clearEpoch = values[8] as Long
        )
    }.flatMapLatest { filter ->
        val baseClipsFlow = when {
            filter.query.isNotBlank() -> repository.searchClips(filter.query.trim())
            filter.category != null -> repository.getClipsByCategory(filter.category)
            filter.pinnedOnly -> repository.getPinnedClips()
            else -> repository.getAllClips()
        }.map { list ->
            var filtered = list
            // Date filtering if selectedDate is non-null
            if (filter.selectedDate != null) {
                val startOfDay = filter.selectedDate
                val endOfDay = startOfDay + 86400000L
                filtered = filtered.filter { it.createdAt in startOfDay until endOfDay }
            }
            // Custom Tag filtering if selectedCustomTag is non-null
            if (!filter.customTag.isNullOrBlank()) {
                filtered = filtered.filter { clip ->
                    clip.tags.any { it.equals(filter.customTag, ignoreCase = true) }
                }
            }
            filtered
        }

        val vaultClipsFlow = if (filter.isVaultUnlocked) {
            repository.getVaultClips().map { list ->
                list.map { clip ->
                    if (clip.encryptedIv != null) {
                        try {
                            val decrypted = SecurityHelper.decrypt(clip.content, clip.encryptedIv)
                            clip.copy(content = decrypted)
                        } catch (e: Exception) {
                            clip
                        }
                    } else clip
                }
            }
        } else {
            flowOf(emptyList())
        }

        combine(baseClipsFlow, vaultClipsFlow) { clips, vaultClips ->
            // Discover any custom tags stored on clips and merge with preset tags
            val clipTags = clips.flatMap { it.tags }
                .filter { tag ->
                    !tag.startsWith("len:") &&
                        tag !in setOf("url", "code", "email", "phone", "otp", "sensitive", "color", "markdown", "text", "bengali", "multiline")
                }
            val mergedTags = (filter.availableTags + clipTags).distinctBy { it.lowercase() }

            ClipboardUiState(
                clips = clips,
                vaultClips = vaultClips,
                selectedCategory = filter.category,
                selectedCustomTag = filter.customTag,
                availableCustomTags = mergedTags,
                selectedClipIds = filter.selectedIds,
                showPinnedOnly = filter.pinnedOnly,
                selectedDateMillis = filter.selectedDate,
                searchQuery = filter.query,
                isLoading = false,
                isVaultUnlocked = filter.isVaultUnlocked,
                totalCount = clips.size + (if (filter.isVaultUnlocked) vaultClips.size else 0)
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ClipboardUiState(isLoading = true)
    )

    fun exportBackupJson(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val json = repository.exportBackupJson()
                onSuccess(json)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Cloud backup export failed")
            }
        }
    }

    fun importBackupJson(
        jsonPayload: String,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val count = repository.importBackupJson(jsonPayload)
                onSuccess(count)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Cloud backup restore failed")
            }
        }
    }

    fun onIntent(intent: ClipboardIntent) {
        if (intent is ClipboardIntent.SaveNewClip) {
            // Immediately enqueue into the unbounded FIFO Channel without coroutine launch race
            if (intent.content.isNotBlank()) {
                saveClipQueue.trySend(intent)
            }
            return
        }
        viewModelScope.launch {
            when (intent) {
                is ClipboardIntent.Search -> _searchQuery.value = intent.query
                is ClipboardIntent.SelectCategory -> {
                    _selectedCategory.value = intent.category
                    if (intent.category != null) {
                        _selectedCustomTag.value = null
                    }
                }
                is ClipboardIntent.SelectCustomTag -> {
                    _selectedCustomTag.value = intent.tag
                    if (intent.tag != null) {
                        _selectedCategory.value = null
                        _showPinnedOnly.value = false
                    }
                }
                is ClipboardIntent.CreateCustomTag -> {
                    val clean = intent.tag.trim()
                    if (clean.isNotEmpty() && _availableCustomTags.value.none { it.equals(clean, ignoreCase = true) }) {
                        _availableCustomTags.value = _availableCustomTags.value + clean
                    }
                }
                is ClipboardIntent.ToggleClipTag -> {
                    val clean = intent.tag.trim()
                    if (clean.isNotEmpty()) {
                        if (_availableCustomTags.value.none { it.equals(clean, ignoreCase = true) }) {
                            _availableCustomTags.value = _availableCustomTags.value + clean
                        }
                        repository.toggleClipTag(intent.clipId, clean)
                    }
                }
                is ClipboardIntent.ToggleSelectClip -> {
                    val current = _selectedClipIds.value.toMutableSet()
                    if (current.contains(intent.clipId)) {
                        current.remove(intent.clipId)
                    } else {
                        current.add(intent.clipId)
                    }
                    _selectedClipIds.value = current
                }
                is ClipboardIntent.SelectAllClips -> {
                    _selectedClipIds.value = uiState.value.clips.map { it.id }.toSet()
                }
                is ClipboardIntent.ClearSelection -> {
                    _selectedClipIds.value = emptySet()
                }
                is ClipboardIntent.BatchDeleteSelected -> {
                    val ids = _selectedClipIds.value.toList()
                    _selectedClipIds.value = emptySet()
                    repository.batchDeleteClips(ids)
                }
                is ClipboardIntent.BatchMoveSelectedToVault -> {
                    val ids = _selectedClipIds.value.toList()
                    _selectedClipIds.value = emptySet()
                    repository.batchMoveToVault(ids)
                }
                is ClipboardIntent.BatchTagSelected -> {
                    val clean = intent.tag.trim()
                    val ids = _selectedClipIds.value.toList()
                    if (clean.isNotEmpty() && ids.isNotEmpty()) {
                        if (_availableCustomTags.value.none { it.equals(clean, ignoreCase = true) }) {
                            _availableCustomTags.value = _availableCustomTags.value + clean
                        }
                        repository.batchAssignTag(ids, clean)
                        _selectedClipIds.value = emptySet()
                    }
                }
                is ClipboardIntent.TogglePinnedOnly -> {
                    _showPinnedOnly.value = !_showPinnedOnly.value
                    if (_showPinnedOnly.value) _selectedCustomTag.value = null
                }
                is ClipboardIntent.FilterByDate -> _selectedDate.value = intent.dateMillis
                is ClipboardIntent.EditClip -> repository.editClip(intent.clipId, intent.newContent)
                is ClipboardIntent.TogglePin -> repository.togglePin(intent.clipId, intent.currentStatus)
                is ClipboardIntent.ToggleVault -> repository.toggleVault(intent.clipId, intent.currentStatus, intent.rawText)
                is ClipboardIntent.DeleteClip -> {
                    _selectedClipIds.value = _selectedClipIds.value - intent.clipId
                    repository.deleteClip(intent.clipId)
                }
                is ClipboardIntent.UnlockVault -> _isVaultUnlocked.value = true
                is ClipboardIntent.LockVault -> _isVaultUnlocked.value = false
                is ClipboardIntent.ClearUnpinned -> {
                    _searchQuery.value = ""
                    _selectedCategory.value = null
                    _selectedCustomTag.value = null
                    _selectedClipIds.value = emptySet()
                    _showPinnedOnly.value = false
                    _selectedDate.value = null
                    repository.clearHistory()
                    _clearEpoch.value++
                }
                is ClipboardIntent.ClearAllHistory -> {
                    // Reset all active filters first, wipe Room DB + FTS4 index, and bump epoch
                    // so the UI immediately transitions to the "Clipboard is empty" state with zero ghost clips
                    _searchQuery.value = ""
                    _selectedCategory.value = null
                    _selectedCustomTag.value = null
                    _selectedClipIds.value = emptySet()
                    _showPinnedOnly.value = false
                    _selectedDate.value = null
                    repository.clearAllHistory()
                    _clearEpoch.value++
                }
                is ClipboardIntent.SaveNewClip -> Unit
            }
        }
    }

    private data class CombinedFilter(
        val query: String,
        val category: ClipCategory?,
        val customTag: String?,
        val availableTags: List<String>,
        val selectedIds: Set<String>,
        val pinnedOnly: Boolean,
        val selectedDate: Long?,
        val isVaultUnlocked: Boolean,
        val clearEpoch: Long = 0L
    )
}
