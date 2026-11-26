package com.android.multimediastudio

import com.android.multimediastudio.data.datasource.DeleteResult
import com.android.multimediastudio.data.datasource.MediaChangeEvent
import com.android.multimediastudio.data.model.MediaFilter
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import com.android.multimediastudio.data.repository.MediaRepository
import com.android.multimediastudio.ui.media.MediaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
    var observeStartedCount = 0
    var syncCallCount = 0

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
        val snapshot = currentItems.toList()
        _mediaState.value = snapshot
        return filterMedia(snapshot, filter)
    }

    override suspend fun syncMediaChanges(event: MediaChangeEvent): List<MediaItem> {
        syncCallCount++
        if (shouldThrowError) {
            throw RuntimeException("Sync error")
        }
        val snapshot = currentItems.toList()
        _mediaState.value = snapshot
        return snapshot
    }

    override suspend fun observeMediaChanges() {
        observeStartedCount++
        awaitCancellation()
    }

    fun emitExternalLibraryChange(items: List<MediaItem>) {
        currentItems.clear()
        currentItems.addAll(items)
        _mediaState.value = items.toList()
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
        _mediaState.value = currentItems.toList()
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
    fun permissionChanged_whenGranted_loadsMediaAndStartsObserver() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.permissionGranted)
        assertEquals(2, state.allMediaItems.size)
        assertEquals(2, state.filteredMediaItems.size)
        assertEquals(1, fakeRepository.observeStartedCount)
    }

    @Test
    fun filterChanged_updatesFilteredMediaItems() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.runCurrent()

        viewModel.setFilter(MediaFilter.VIDEO)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(MediaFilter.VIDEO, state.selectedFilter)
        assertEquals(1, state.filteredMediaItems.size)
        assertEquals(MediaType.VIDEO, state.filteredMediaItems.first().mediaType)
    }

    @Test
    fun searchQueryChanged_filtersByTitle() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.runCurrent()

        viewModel.setSearchQuery("Acoustic")
        testDispatcher.scheduler.runCurrent()

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
        testDispatcher.scheduler.runCurrent()

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
        testDispatcher.scheduler.runCurrent()

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
        testDispatcher.scheduler.runCurrent()

        viewModel.enterSelectionMode(fakeRepository.videoItem)
        viewModel.deleteSelectedItems()
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isSelectionMode)
        assertEquals(1, state.filteredMediaItems.size)
        assertEquals("Acoustic Audio", state.filteredMediaItems.first().title)
    }

    @Test
    fun loadMedia_whenErrorOccurs_updatesErrorState() = runTest {
        fakeRepository.shouldThrowError = true
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Permission or storage error", state.errorMessage)
    }

    @Test
    fun externalMediaChanges_automaticallyUpdateListsPreservingFilterAndSearch() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.runCurrent()

        viewModel.setFilter(MediaFilter.AUDIO)
        viewModel.setSearchQuery("Acoustic")
        testDispatcher.scheduler.runCurrent()

        val newMatchingAudio = MediaItem(
            id = 30L,
            title = "Acoustic Live",
            mediaType = MediaType.AUDIO
        )
        val nonMatchingVideo = MediaItem(
            id = 40L,
            title = "Acoustic Video",
            mediaType = MediaType.VIDEO
        )
        fakeRepository.emitExternalLibraryChange(
            listOf(fakeRepository.videoItem, fakeRepository.audioItem, newMatchingAudio, nonMatchingVideo)
        )
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(4, state.allMediaItems.size)
        assertEquals(MediaFilter.AUDIO, state.selectedFilter)
        assertEquals("Acoustic", state.searchQuery)
        assertEquals(2, state.filteredMediaItems.size)
        assertTrue(state.filteredMediaItems.all { it.mediaType == MediaType.AUDIO })
    }

    @Test
    fun syncMediaChanges_updatesAddedAndDeletedFilesIncrementally() = runTest {
        viewModel.onPermissionChanged(true)
        testDispatcher.scheduler.runCurrent()

        fakeRepository.currentItems.remove(fakeRepository.videoItem)
        val modifiedAudio = fakeRepository.audioItem.copy(title = "Acoustic Audio (Remastered)")
        fakeRepository.currentItems[0] = modifiedAudio

        viewModel.syncMediaChanges()
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(1, fakeRepository.syncCallCount)
        assertEquals(1, state.allMediaItems.size)
        assertEquals("Acoustic Audio (Remastered)", state.filteredMediaItems.first().title)
    }

    @Test
    fun pipEnabled_defaultTrue_andCanBeToggled() = runTest {
        assertTrue(viewModel.uiState.value.pipEnabled)

        viewModel.setPipEnabled(false)

        assertFalse(viewModel.uiState.value.pipEnabled)

        viewModel.setPipEnabled(true)

        assertTrue(viewModel.uiState.value.pipEnabled)
    }
}
