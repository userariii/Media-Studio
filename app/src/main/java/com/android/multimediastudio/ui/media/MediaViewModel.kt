package com.android.multimediastudio.ui.media

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.multimediastudio.data.datasource.DeleteResult
import com.android.multimediastudio.data.model.AppThemeColor
import com.android.multimediastudio.data.model.MediaFilter
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaSortOption
import com.android.multimediastudio.data.model.SortDirection
import com.android.multimediastudio.data.model.ThemeMode
import com.android.multimediastudio.data.repository.MediaRepository
import com.android.multimediastudio.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MediaUiState(
    val isLoading: Boolean = true,
    val permissionGranted: Boolean = false,
    val isPermissionChecked: Boolean = false,
    val selectedFilter: MediaFilter = MediaFilter.ALL,
    val searchQuery: String = "",
    val sortOption: MediaSortOption = MediaSortOption.DATE_MODIFIED,
    val sortDirection: SortDirection = SortDirection.DESCENDING,
    val isGridView: Boolean = false,
    val allMediaItems: List<MediaItem> = emptyList(),
    val filteredMediaItems: List<MediaItem> = emptyList(),
    val selectedMediaItem: MediaItem? = null,
    val errorMessage: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedItemKeys: Set<String> = emptySet(),
    val pendingDeleteIntentSender: IntentSender? = null,
    val isSettingsOpen: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appThemeColor: AppThemeColor = AppThemeColor.DEFAULT,
    val hideSocialMedia: Boolean = true,
    val pipEnabled: Boolean = true
) {
    fun getItemKey(item: MediaItem): String = "${item.mediaType}_${item.id}"
    fun isItemSelected(item: MediaItem): Boolean = selectedItemKeys.contains(getItemKey(item))
}

class MediaViewModel(
    private val repository: MediaRepository,
    private val preferencesRepository: UserPreferencesRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MediaUiState(
            themeMode = preferencesRepository?.getInitialThemeMode() ?: ThemeMode.SYSTEM,
            appThemeColor = preferencesRepository?.getInitialAppThemeColor() ?: AppThemeColor.DEFAULT,
            hideSocialMedia = preferencesRepository?.getInitialHideSocialMedia() ?: true,
            pipEnabled = preferencesRepository?.getInitialPipEnabled() ?: true,
            sortOption = preferencesRepository?.getInitialSortOption() ?: MediaSortOption.DATE_MODIFIED,
            sortDirection = preferencesRepository?.getInitialSortDirection() ?: SortDirection.DESCENDING
        )
    )
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null
    private var hasLoadedInitialMedia = false

    init {
        preferencesRepository?.let { prefs ->
            viewModelScope.launch {
                prefs.themeModeStream.collect { mode ->
                    _uiState.update { it.copy(themeMode = mode) }
                }
            }
            viewModelScope.launch {
                prefs.appThemeColorStream.collect { color ->
                    _uiState.update { it.copy(appThemeColor = color) }
                }
            }
            viewModelScope.launch {
                prefs.pipEnabledStream.collect { enabled ->
                    _uiState.update { it.copy(pipEnabled = enabled) }
                }
            }
            viewModelScope.launch {
                prefs.hideSocialMediaStream.collect { hide ->
                    _uiState.update { state ->
                        val newFiltered = repository.filterMedia(
                            state.allMediaItems,
                            state.selectedFilter,
                            state.searchQuery,
                            hide,
                            state.sortOption,
                            state.sortDirection
                        )
                        state.copy(
                            hideSocialMedia = hide,
                            filteredMediaItems = newFiltered
                        )
                    }
                }
            }
            viewModelScope.launch {
                combine(prefs.sortOptionStream, prefs.sortDirectionStream) { option, direction ->
                    Pair(option, direction)
                }.collect { (option, direction) ->
                    _uiState.update { state ->
                        if (state.sortOption == option && state.sortDirection == direction) {
                            state
                        } else {
                            val newFiltered = repository.filterMedia(
                                state.allMediaItems,
                                state.selectedFilter,
                                state.searchQuery,
                                state.hideSocialMedia,
                                option,
                                direction
                            )
                            state.copy(
                                sortOption = option,
                                sortDirection = direction,
                                filteredMediaItems = newFiltered
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.mediaStream.collect { items ->
                _uiState.update { state ->
                    val filtered = repository.filterMedia(
                        items,
                        state.selectedFilter,
                        state.searchQuery,
                        state.hideSocialMedia,
                        state.sortOption,
                        state.sortDirection
                    )
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
        val wasGranted = _uiState.value.permissionGranted
        _uiState.update {
            it.copy(
                permissionGranted = isGranted,
                isPermissionChecked = true,
                isLoading = if (!isGranted) false else it.isLoading
            )
        }
        if (isGranted) {
            startObservingMediaChanges()
            if (!wasGranted || !hasLoadedInitialMedia) {
                loadMedia()
            } else {
                syncMediaChanges()
            }
        } else {
            stopObservingMediaChanges()
        }
    }

    private fun startObservingMediaChanges() {
        if (observationJob?.isActive == true) return
        observationJob = viewModelScope.launch {
            repository.observeMediaChanges()
        }
    }

    private fun stopObservingMediaChanges() {
        observationJob?.cancel()
        observationJob = null
    }

    fun setFilter(filter: MediaFilter) {
        if (_uiState.value.selectedFilter == filter) return
        _uiState.update { state ->
            val newFiltered = repository.filterMedia(
                state.allMediaItems,
                filter,
                state.searchQuery,
                state.hideSocialMedia,
                state.sortOption,
                state.sortDirection
            )
            state.copy(
                selectedFilter = filter,
                filteredMediaItems = newFiltered
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val newFiltered = repository.filterMedia(
                state.allMediaItems,
                state.selectedFilter,
                query,
                state.hideSocialMedia,
                state.sortOption,
                state.sortDirection
            )
            state.copy(
                searchQuery = query,
                filteredMediaItems = newFiltered
            )
        }
    }

    fun setSortOption(option: MediaSortOption) {
        val currentState = _uiState.value
        val newDirection = if (currentState.sortOption == option) {
            currentState.sortDirection.toggled()
        } else {
            option.defaultDirection()
        }
        applySort(option, newDirection)
    }

    fun setSortDirection(direction: SortDirection) {
        val currentState = _uiState.value
        if (currentState.sortDirection == direction) return
        applySort(currentState.sortOption, direction)
    }

    private fun applySort(option: MediaSortOption, direction: SortDirection) {
        _uiState.update { state ->
            val newFiltered = repository.filterMedia(
                state.allMediaItems,
                state.selectedFilter,
                state.searchQuery,
                state.hideSocialMedia,
                option,
                direction
            )
            state.copy(
                sortOption = option,
                sortDirection = direction,
                filteredMediaItems = newFiltered
            )
        }
        preferencesRepository?.let { prefs ->
            viewModelScope.launch {
                prefs.setSortPreference(option, direction)
            }
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

    fun openSettings() {
        _uiState.update { it.copy(isSettingsOpen = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
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
        syncMediaChanges()
    }

    fun clearDeleteIntentSender() {
        _uiState.update { it.copy(pendingDeleteIntentSender = null) }
    }

    fun loadMedia() {
        if (!_uiState.value.permissionGranted) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = it.allMediaItems.isEmpty(),
                    errorMessage = null
                )
            }
            try {
                repository.scanMedia(_uiState.value.selectedFilter)
                hasLoadedInitialMedia = true
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = e.localizedMessage ?: "Failed to scan media files"
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun syncMediaChanges() {
        if (!_uiState.value.permissionGranted) return
        if (!hasLoadedInitialMedia) {
            loadMedia()
            return
        }
        viewModelScope.launch {
            try {
                repository.syncMediaChanges()
            } catch (_: Exception) {
                // Keep existing library state intact on transient background sync errors
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        preferencesRepository?.let { prefs ->
            viewModelScope.launch {
                prefs.setThemeMode(mode)
            }
        }
    }

    fun setAppThemeColor(color: AppThemeColor) {
        _uiState.update { it.copy(appThemeColor = color) }
        preferencesRepository?.let { prefs ->
            viewModelScope.launch {
                prefs.setAppThemeColor(color)
            }
        }
    }

    fun setHideSocialMedia(hide: Boolean) {
        _uiState.update { state ->
            val newFiltered = repository.filterMedia(
                state.allMediaItems,
                state.selectedFilter,
                state.searchQuery,
                hide,
                state.sortOption,
                state.sortDirection
            )
            state.copy(
                hideSocialMedia = hide,
                filteredMediaItems = newFiltered
            )
        }
        preferencesRepository?.let { prefs ->
            viewModelScope.launch {
                prefs.setHideSocialMedia(hide)
            }
        }
    }

    fun setPipEnabled(enabled: Boolean) {
        _uiState.update { it.copy(pipEnabled = enabled) }
        preferencesRepository?.let { prefs ->
            viewModelScope.launch {
                prefs.setPipEnabled(enabled)
            }
        }
    }

    companion object {
        fun provideFactory(
            repository: MediaRepository,
            preferencesRepository: UserPreferencesRepository? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MediaViewModel(repository, preferencesRepository) as T
                }
            }
    }
}
