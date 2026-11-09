package com.clipvault.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.domain.repository.ClipboardRepository
import com.clipvault.android.security.SecurityHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClipboardUiState(
    val clips: List<ClipEntity> = emptyList(),
    val vaultClips: List<ClipEntity> = emptyList(),
    val selectedCategory: ClipCategory? = null,
    val showPinnedOnly: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isVaultUnlocked: Boolean = false,
    val totalCount: Int = 0
)

sealed interface ClipboardIntent {
    data class Search(val query: String) : ClipboardIntent
    data class SelectCategory(val category: ClipCategory?) : ClipboardIntent
    data object TogglePinnedOnly : ClipboardIntent
    data class TogglePin(val clipId: String, val currentStatus: Boolean) : ClipboardIntent
    data class ToggleVault(val clipId: String, val currentStatus: Boolean, val rawText: String) : ClipboardIntent
    data class DeleteClip(val clipId: String) : ClipboardIntent
    data class SaveNewClip(val content: String, val sourceApp: String? = "Manual") : ClipboardIntent
    data object UnlockVault : ClipboardIntent
    data object LockVault : ClipboardIntent
    data object ClearUnpinned : ClipboardIntent
}

@HiltViewModel
class ClipboardViewModel @Inject constructor(
    private val repository: ClipboardRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<ClipCategory?>(null)
    private val _showPinnedOnly = MutableStateFlow(false)
    private val _isVaultUnlocked = MutableStateFlow(false)

    val uiState: StateFlow<ClipboardUiState> = combine(
        _searchQuery,
        _selectedCategory,
        _showPinnedOnly,
        _isVaultUnlocked
    ) { query, category, pinnedOnly, isVaultUnlocked ->
        CombinedFilter(query, category, pinnedOnly, isVaultUnlocked)
    }.flatMapLatest { filter ->
        val baseClipsFlow = when {
            filter.query.isNotBlank() -> repository.searchClips(filter.query.trim())
            filter.category != null -> repository.getClipsByCategory(filter.category)
            filter.pinnedOnly -> repository.getPinnedClips()
            else -> repository.getAllClips()
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
            ClipboardUiState(
                clips = clips,
                vaultClips = vaultClips,
                selectedCategory = filter.category,
                showPinnedOnly = filter.pinnedOnly,
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

    fun onIntent(intent: ClipboardIntent) {
        viewModelScope.launch {
            when (intent) {
                is ClipboardIntent.Search -> _searchQuery.value = intent.query
                is ClipboardIntent.SelectCategory -> _selectedCategory.value = intent.category
                is ClipboardIntent.TogglePinnedOnly -> _showPinnedOnly.value = !_showPinnedOnly.value
                is ClipboardIntent.TogglePin -> repository.togglePin(intent.clipId, intent.currentStatus)
                is ClipboardIntent.ToggleVault -> repository.toggleVault(intent.clipId, intent.currentStatus, intent.rawText)
                is ClipboardIntent.DeleteClip -> repository.deleteClip(intent.clipId)
                is ClipboardIntent.UnlockVault -> _isVaultUnlocked.value = true
                is ClipboardIntent.LockVault -> _isVaultUnlocked.value = false
                is ClipboardIntent.ClearUnpinned -> repository.clearHistory()
                is ClipboardIntent.SaveNewClip -> {
                    if (intent.content.isNotBlank()) {
                        repository.saveNewClip(intent.content.trim(), intent.sourceApp)
                    }
                }
            }
        }
    }

    private data class CombinedFilter(
        val query: String,
        val category: ClipCategory?,
        val pinnedOnly: Boolean,
        val isVaultUnlocked: Boolean
    )
}
