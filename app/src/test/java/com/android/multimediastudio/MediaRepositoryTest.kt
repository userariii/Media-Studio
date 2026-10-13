package com.android.multimediastudio

import com.android.multimediastudio.data.datasource.DeleteResult
import com.android.multimediastudio.data.datasource.MediaStoreDataSource
import com.android.multimediastudio.data.model.MediaFilter
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import com.android.multimediastudio.data.repository.MediaRepositoryImpl
import kotlinx.coroutines.flow.first
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

    override suspend fun fetchVideoFiles(): List<MediaItem> = itemsList.filter { it.mediaType == MediaType.VIDEO }

    override suspend fun fetchAudioFiles(): List<MediaItem> = itemsList.filter { it.mediaType == MediaType.AUDIO }

    override suspend fun fetchAllMediaFiles(): List<MediaItem> =
        itemsList.sortedByDescending { it.dateModified }

    override suspend fun deleteMediaFiles(items: List<MediaItem>): DeleteResult {
        itemsList.removeAll(items)
        return DeleteResult.Success
    }
}

class MediaRepositoryTest {

    private lateinit var dataSource: FakeMediaStoreDataSource
    private lateinit var repository: MediaRepositoryImpl

    @Before
    fun setUp() {
        dataSource = FakeMediaStoreDataSource()
        repository = MediaRepositoryImpl(dataSource)
    }

    @Test
    fun scanMedia_updatesMediaStreamAndReturnsFilteredItems() = runTest {
        val result = repository.scanMedia(MediaFilter.ALL)

        assertEquals(2, result.size)
        val streamItems = repository.mediaStream.first()
        assertEquals(2, streamItems.size)
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
    fun deleteMediaFiles_removesFilesAndScansMedia() = runTest {
        repository.scanMedia(MediaFilter.ALL)
        assertEquals(2, repository.mediaStream.first().size)

        val result = repository.deleteMediaFiles(listOf(dataSource.sampleVideo))
        assertEquals(DeleteResult.Success, result)

        val remaining = repository.mediaStream.first()
        assertEquals(1, remaining.size)
        assertEquals(MediaType.AUDIO, remaining.first().mediaType)
    }

    @Test
    fun refresh_queriesDataSourceAndUpdatesMediaStream() = runTest {
        repository.scanMedia(MediaFilter.ALL)
        assertEquals(2, repository.mediaStream.first().size)

        dataSource.itemsList.clear()
        val refreshed = repository.refresh(MediaFilter.ALL)
        assertEquals(0, refreshed.size)
        assertEquals(0, repository.mediaStream.first().size)
    }
}
