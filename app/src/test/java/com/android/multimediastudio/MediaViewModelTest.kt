package com.android.multimediastudio

import com.android.multimediastudio.data.datasource.DeleteResult
import com.android.multimediastudio.data.model.MediaFilter
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import com.android.multimediastudio.data.repository.MediaRepository
import com.android.multimediastudio.ui.media.MediaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeMediaRepository : MediaRepository {
    private val _mediaState = MutableStateFlow<List<MediaItem>>(emptyList())
    override val mediaStream: Flow<List<MediaItem>> = _mediaState

    var shouldThrowError = false
    var refreshDelayMs = 0L

    val videoItem = MediaItem(
        id = 10L,
        title = "Video Clip 1",
        contentUri = null,
        durationMs = 50000L,
        sizeBytes = 2000000L,
        mimeType = "video/mp4",
        dateAdded = 10L,
        dateModified = 20L,
        thumbnailUri = null,
        mediaType = MediaType.VIDEO
    )

    val audioItem = MediaItem(
        id = 20L,
        title = "Acoustic Audio",
        contentUri = null,
        durationMs = 120000L,
        sizeBytes = 1000000L,
        mimeType = "audio/mp3",
        dateAdded = 15L,
        dateModified = 25L,
        thumbnailUri = null,
        mediaType = MediaType.AUDIO
    )

    val currentItems = mutableListOf(videoItem, audioItem)

    override suspend fun scanMedia(filter: MediaFilter): List<MediaItem> {
        if (shouldThrowError) {
            throw RuntimeException("Permission or storage error")
        }
        _mediaState.value = currentItems
        return filterMedia(currentItems, filter)
    }

    override suspend fun refresh(filter: MediaFilter): List<MediaItem> {
        if (refreshDelayMs > 0L) {
            delay(refreshDelayMs)
        }
        return scanMedia(filter)
    }

    override fun filterMedia(
        items: List<MediaItem>,
        filter: MediaFilter,
        searchQuery: String,
        hideSocialMedia: Boolean
    ): List<MediaItem> {
        val typeFiltered = when (filter) {
            MediaFilter.ALL -> items
            MediaFilter.VIDEO -> items.filter { it.mediaType == MediaType.VIDEO }
            MediaFilter.AUDIO -> items.filter { it.mediaType == MediaType.AUDIO }
        }
        if (searchQuery.isBlank()) return typeFiltered
        val q = searchQuery.lowercase()
        return typeFiltered.filter { it.title.lowercase().contains(q) }
    }

    override suspend fun deleteMediaFiles(items: List<MediaItem>): DeleteResult {
        currentItems.removeAll(items)
        _mediaState.value = currentItems
        return DeleteResult.Success
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeMediaRepository
    private lateinit var viewModel: MediaViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeMediaRepository()
        viewModel = MediaViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun permissionChanged_whenGranted_loadsMedia() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.permissionGranted)
        assertEquals(2, state.allMediaItems.size)
        assertEquals(2, state.filteredMediaItems.size)
    }

    @Test
    fun filterChanged_updatesFilteredMediaItems() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFilter(MediaFilter.VIDEO)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MediaFilter.VIDEO, state.selectedFilter)
        assertEquals(1, state.filteredMediaItems.size)
        assertEquals(MediaType.VIDEO, state.filteredMediaItems.first().mediaType)
    }

    @Test
    fun searchQueryChanged_filtersByTitle() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setSearchQuery("Acoustic")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Acoustic", state.searchQuery)
        assertEquals(1, state.filteredMediaItems.size)
        assertEquals("Acoustic Audio", state.filteredMediaItems.first().title)
    }

    @Test
    fun toggleLayoutMode_switchesGridAndListView() = runTest {
        assertFalse(viewModel.uiState.value.isGridView)

        viewModel.toggleLayoutMode()
        assertTrue(viewModel.uiState.value.isGridView)

        viewModel.toggleLayoutMode()
        assertFalse(viewModel.uiState.value.isGridView)
    }

    @Test
    fun selectMediaItem_updatesSelectedItemInState() = runTest {
        assertNull(viewModel.uiState.value.selectedMediaItem)

        viewModel.selectMediaItem(fakeRepository.videoItem)
        assertEquals(fakeRepository.videoItem, viewModel.uiState.value.selectedMediaItem)

        viewModel.selectMediaItem(null)
        assertNull(viewModel.uiState.value.selectedMediaItem)
    }

    @Test
    fun selectionMode_enterAndExit() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.enterSelectionMode(fakeRepository.videoItem)
        var state = viewModel.uiState.value
        assertTrue(state.isSelectionMode)
        assertTrue(state.isItemSelected(fakeRepository.videoItem))
        assertEquals(1, state.selectedItemKeys.size)

        viewModel.exitSelectionMode()
        state = viewModel.uiState.value
        assertFalse(state.isSelectionMode)
        assertTrue(state.selectedItemKeys.isEmpty())
    }

    @Test
    fun selectionMode_selectAllAndDeselectAll() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectAll()
        var state = viewModel.uiState.value
        assertTrue(state.isSelectionMode)
        assertEquals(2, state.selectedItemKeys.size)

        viewModel.deselectAll()
        state = viewModel.uiState.value
        assertFalse(state.isSelectionMode)
        assertTrue(state.selectedItemKeys.isEmpty())
    }

    @Test
    fun deleteSelectedItems_removesItemsAndExitsSelectionMode() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.enterSelectionMode(fakeRepository.videoItem)
        viewModel.deleteSelectedItems()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSelectionMode)
        assertEquals(1, state.filteredMediaItems.size)
        assertEquals("Acoustic Audio", state.filteredMediaItems.first().title)
    }

    @Test
    fun loadMedia_whenErrorOccurs_updatesErrorState() = runTest {
        fakeRepository.shouldThrowError = true
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Permission or storage error", state.errorMessage)
    }

    @Test
    fun refresh_reloadsMediaAndUpdatesState() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        fakeRepository.currentItems.clear()
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.allMediaItems.size)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun refresh_setsIsRefreshingTrueDuringRefreshAndFalseAfterCompletion() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        fakeRepository.refreshDelayMs = 500L
        viewModel.refresh()
        testDispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isRefreshing)

        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun refresh_whenErrorOccurs_resetsIsRefreshingToFalse() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        fakeRepository.shouldThrowError = true
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertFalse(state.isLoading)
        assertEquals("Permission or storage error", state.errorMessage)
    }

    @Test
    fun refresh_preservesSearchQueryAndFilterSelection() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFilter(MediaFilter.AUDIO)
        viewModel.setSearchQuery("Acoustic")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MediaFilter.AUDIO, state.selectedFilter)
        assertEquals("Acoustic", state.searchQuery)
        assertEquals(1, state.filteredMediaItems.size)
        assertEquals("Acoustic Audio", state.filteredMediaItems.first().title)
    }
}
