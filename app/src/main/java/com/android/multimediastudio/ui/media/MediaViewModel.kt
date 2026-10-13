package com.android.multimediastudio.ui.media

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.multimediastudio.data.datasource.DeleteResult
import com.android.multimediastudio.data.model.MediaFilter
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MediaUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val permissionGranted: Boolean = false,
    val isPermissionChecked: Boolean = false,
    val selectedFilter: MediaFilter = MediaFilter.ALL,
    val searchQuery: String = "",
    val isGridView: Boolean = false,
    val allMediaItems: List<MediaItem> = emptyList(),
    val filteredMediaItems: List<MediaItem> = emptyList(),
    val selectedMediaItem: MediaItem? = null,
    val errorMessage: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedItemKeys: Set<String> = emptySet(),
    val pendingDeleteIntentSender: IntentSender? = null
) {
    fun getItemKey(item: MediaItem): String = "${item.mediaType}_${item.id}"
    fun isItemSelected(item: MediaItem): Boolean = selectedItemKeys.contains(getItemKey(item))
}

class MediaViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.mediaStream.collect { items ->
                val currentFilter = _uiState.value.selectedFilter
                val currentQuery = _uiState.value.searchQuery
                _uiState.update { state ->
                    val filtered = repository.filterMedia(items, currentFilter, currentQuery)
                    val validKeys = filtered.map { state.getItemKey(it) }.toSet()
                    state.copy(
                        allMediaItems = items,
                        filteredMediaItems = filtered,
                        selectedItemKeys = state.selectedItemKeys.intersect(validKeys)
                    )
                }
            }
        }
    }

    fun onPermissionChanged(isGranted: Boolean) {
        _uiState.update {
            it.copy(
                permissionGranted = isGranted,
                isPermissionChecked = true,
                isLoading = if (!isGranted) false else it.isLoading
            )
        }
        if (isGranted) {
            loadMedia()
        }
    }

    fun setFilter(filter: MediaFilter) {
        _uiState.update { state ->
            val newFiltered = repository.filterMedia(state.allMediaItems, filter, state.searchQuery)
            state.copy(
                selectedFilter = filter,
                filteredMediaItems = newFiltered
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val newFiltered = repository.filterMedia(state.allMediaItems, state.selectedFilter, query)
            state.copy(
                searchQuery = query,
                filteredMediaItems = newFiltered
            )
        }
    }

    fun toggleLayoutMode() {
        _uiState.update { state ->
            state.copy(isGridView = !state.isGridView)
        }
    }

    fun selectMediaItem(item: MediaItem?) {
        _uiState.update { state ->
            state.copy(selectedMediaItem = item)
        }
    }

    fun enterSelectionMode(initialItem: MediaItem? = null) {
        _uiState.update { state ->
            val initialKeys = if (initialItem != null) setOf(state.getItemKey(initialItem)) else emptySet()
            state.copy(
                isSelectionMode = true,
                selectedItemKeys = initialKeys
            )
        }
    }

    fun exitSelectionMode() {
        _uiState.update { state ->
            state.copy(
                isSelectionMode = false,
                selectedItemKeys = emptySet()
            )
        }
    }

    fun toggleItemSelection(item: MediaItem) {
        _uiState.update { state ->
            val key = state.getItemKey(item)
            val newKeys = if (state.selectedItemKeys.contains(key)) {
                state.selectedItemKeys - key
            } else {
                state.selectedItemKeys + key
            }
            if (newKeys.isEmpty()) {
                state.copy(isSelectionMode = false, selectedItemKeys = emptySet())
            } else {
                state.copy(selectedItemKeys = newKeys)
            }
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val allKeys = state.filteredMediaItems.map { state.getItemKey(it) }.toSet()
            state.copy(
                isSelectionMode = true,
                selectedItemKeys = allKeys
            )
        }
    }

    fun deselectAll() {
        _uiState.update { state ->
            state.copy(
                isSelectionMode = false,
                selectedItemKeys = emptySet()
            )
        }
    }

    fun deleteSelectedItems() {
        val state = _uiState.value
        val selectedItems = state.filteredMediaItems.filter { state.isItemSelected(it) }
        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.deleteMediaFiles(selectedItems)
            when (result) {
                is DeleteResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSelectionMode = false,
                            selectedItemKeys = emptySet()
                        )
                    }
                    loadMedia()
                }
                is DeleteResult.RequiresPermission -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingDeleteIntentSender = result.intentSender
                        )
                    }
                }
                is DeleteResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun onDeleteConfirmed() {
        _uiState.update {
            it.copy(
                pendingDeleteIntentSender = null,
                isSelectionMode = false,
                selectedItemKeys = emptySet()
            )
        }
        loadMedia()
    }

    fun clearDeleteIntentSender() {
        _uiState.update { it.copy(pendingDeleteIntentSender = null) }
    }

    fun refresh() {
        if (!_uiState.value.permissionGranted) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    isLoading = it.allMediaItems.isEmpty(),
                    errorMessage = null
                )
            }
            try {
                repository.refresh(_uiState.value.selectedFilter)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = e.localizedMessage ?: "Failed to scan media files"
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun loadMedia() {
        refresh()
    }

    companion object {
        fun provideFactory(repository: MediaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MediaViewModel(repository) as T
                }
            }
    }
}
