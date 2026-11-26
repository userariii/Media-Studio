package com.android.multimediastudio

import com.android.multimediastudio.data.datasource.DeleteResult
import com.android.multimediastudio.data.datasource.MediaChangeEvent
import com.android.multimediastudio.data.datasource.MediaStoreDataSource
import com.android.multimediastudio.data.model.MediaFilter
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import com.android.multimediastudio.data.repository.MediaRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FakeMediaStoreDataSource : MediaStoreDataSource {
    val sampleVideo = MediaItem(
        id = 1L,
        title = "Test Video",
        contentUri = null,
        durationMs = 60000L,
        sizeBytes = 1000000L,
        mimeType = "video/mp4",
        dateAdded = 100L,
        dateModified = 200L,
        thumbnailUri = null,
        mediaType = MediaType.VIDEO,
        resolution = "1920x1080"
    )

    val sampleAudio = MediaItem(
        id = 2L,
        title = "Test Audio",
        contentUri = null,
        durationMs = 180000L,
        sizeBytes = 500000L,
        mimeType = "audio/mpeg",
        dateAdded = 150L,
        dateModified = 250L,
        thumbnailUri = null,
        mediaType = MediaType.AUDIO,
        artist = "Test Artist",
        album = "Test Album"
    )

    val itemsList = mutableListOf(sampleVideo, sampleAudio)
    val changeEvents = MutableSharedFlow<MediaChangeEvent>(extraBufferCapacity = 16)
    var fullScanCount = 0
    var incrementalSyncCount = 0
    val syncedEvents = mutableListOf<MediaChangeEvent>()

    override suspend fun fetchVideoFiles(): List<MediaItem> = itemsList.filter { it.mediaType == MediaType.VIDEO }

    override suspend fun fetchAudioFiles(): List<MediaItem> = itemsList.filter { it.mediaType == MediaType.AUDIO }

    override suspend fun fetchAllMediaFiles(): List<MediaItem> {
        fullScanCount++
        return itemsList.sortedByDescending { it.dateModified }
    }

    override suspend fun deleteMediaFiles(items: List<MediaItem>): DeleteResult {
        itemsList.removeAll(items)
        return DeleteResult.Success
    }

    override fun observeMediaChanges(): Flow<MediaChangeEvent> = changeEvents

    override suspend fun syncIncremental(
        currentItems: List<MediaItem>,
        event: MediaChangeEvent
    ): List<MediaItem> {
        incrementalSyncCount++
        syncedEvents.add(event)
        return itemsList.sortedByDescending { it.dateModified }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MediaRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dataSource: FakeMediaStoreDataSource
    private lateinit var repository: MediaRepositoryImpl

    @Before
    fun setUp() {
        dataSource = FakeMediaStoreDataSource()
        repository = MediaRepositoryImpl(
            dataSource = dataSource,
            ioDispatcher = testDispatcher,
            debounceMs = 250L
        )
    }

    @Test
    fun scanMedia_updatesMediaStreamAndReturnsFilteredItems() = runTest(testDispatcher) {
        val result = repository.scanMedia(MediaFilter.ALL)

        assertEquals(2, result.size)
        val streamItems = repository.mediaStream.first()
        assertEquals(2, streamItems.size)
        assertEquals(1, dataSource.fullScanCount)
    }

    @Test
    fun filterMedia_correctlyFiltersByMediaType() {
        val allItems = listOf(dataSource.sampleVideo, dataSource.sampleAudio)

        val videosOnly = repository.filterMedia(allItems, MediaFilter.VIDEO)
        assertEquals(1, videosOnly.size)
        assertEquals(MediaType.VIDEO, videosOnly.first().mediaType)

        val audioOnly = repository.filterMedia(allItems, MediaFilter.AUDIO)
        assertEquals(1, audioOnly.size)
        assertEquals(MediaType.AUDIO, audioOnly.first().mediaType)

        val all = repository.filterMedia(allItems, MediaFilter.ALL)
        assertEquals(2, all.size)
    }

    @Test
    fun deleteMediaFiles_removesItemsWithoutFullRescan() = runTest(testDispatcher) {
        repository.scanMedia(MediaFilter.ALL)
        assertEquals(1, dataSource.fullScanCount)
        assertEquals(2, repository.mediaStream.first().size)

        val result = repository.deleteMediaFiles(listOf(dataSource.sampleVideo))
        assertEquals(DeleteResult.Success, result)

        val remaining = repository.mediaStream.first()
        assertEquals(1, remaining.size)
        assertEquals(MediaType.AUDIO, remaining.first().mediaType)
        assertEquals(1, dataSource.fullScanCount)
    }

    @Test
    fun syncMediaChanges_updatesStreamIncrementallyWithoutFullRescan() = runTest(testDispatcher) {
        repository.scanMedia(MediaFilter.ALL)
        assertEquals(1, dataSource.fullScanCount)

        val newAudio = MediaItem(
            id = 3L,
            title = "Added Audio",
            mediaType = MediaType.AUDIO,
            dateModified = 300L
        )
        dataSource.itemsList.add(newAudio)

        val synced = repository.syncMediaChanges(MediaChangeEvent.UriChanges(audioIds = setOf(3L)))
        assertEquals(3, synced.size)
        assertEquals(3, repository.mediaStream.first().size)
        assertEquals(1, dataSource.fullScanCount)
        assertEquals(1, dataSource.incrementalSyncCount)
    }

    @Test
    fun observeMediaChanges_coalescesBurstEventsAndUpdatesStream() = runTest(testDispatcher) {
        repository.scanMedia(MediaFilter.ALL)

        val observeJob = launch {
            repository.observeMediaChanges()
        }
        advanceUntilIdle()

        val addedVideo = MediaItem(
            id = 4L,
            title = "New Recording",
            mediaType = MediaType.VIDEO,
            dateModified = 400L
        )
        val addedAudio = MediaItem(
            id = 5L,
            title = "New Podcast",
            mediaType = MediaType.AUDIO,
            dateModified = 500L
        )
        dataSource.itemsList.add(addedVideo)
        dataSource.itemsList.add(addedAudio)

        // Emit burst events within debounce window
        dataSource.changeEvents.emit(MediaChangeEvent.UriChanges(videoIds = setOf(4L)))
        advanceTimeBy(100L)
        dataSource.changeEvents.emit(MediaChangeEvent.UriChanges(audioIds = setOf(5L)))
        advanceTimeBy(300L)
        advanceUntilIdle()

        assertEquals(4, repository.mediaStream.first().size)
        assertEquals(1, dataSource.incrementalSyncCount)
        val merged = dataSource.syncedEvents.single() as MediaChangeEvent.UriChanges
        assertEquals(setOf(4L), merged.videoIds)
        assertEquals(setOf(5L), merged.audioIds)

        observeJob.cancel()
    }
}
