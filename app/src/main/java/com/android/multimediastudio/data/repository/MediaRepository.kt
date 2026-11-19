package com.android.multimediastudio.data.repository

import com.android.multimediastudio.data.datasource.DeleteResult
import com.android.multimediastudio.data.datasource.MediaStoreDataSource
import com.android.multimediastudio.data.model.MediaFilter
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface MediaRepository {
    val mediaStream: Flow<List<MediaItem>>
    suspend fun scanMedia(filter: MediaFilter = MediaFilter.ALL): List<MediaItem>
    suspend fun refresh(filter: MediaFilter = MediaFilter.ALL): List<MediaItem>
    fun filterMedia(
        items: List<MediaItem>,
        filter: MediaFilter,
        searchQuery: String = "",
        hideSocialMedia: Boolean = false
    ): List<MediaItem>
    suspend fun deleteMediaFiles(items: List<MediaItem>): DeleteResult
}

class MediaRepositoryImpl(
    private val dataSource: MediaStoreDataSource
) : MediaRepository {

    private val _mediaState = MutableStateFlow<List<MediaItem>>(emptyList())
    override val mediaStream: Flow<List<MediaItem>> = _mediaState.asStateFlow()

    override suspend fun scanMedia(filter: MediaFilter): List<MediaItem> {
        val allItems = dataSource.fetchAllMediaFiles()
        _mediaState.value = allItems
        return filterMedia(allItems, filter)
    }

    override suspend fun refresh(filter: MediaFilter): List<MediaItem> {
        return scanMedia(filter)
    }

    override fun filterMedia(
        items: List<MediaItem>,
        filter: MediaFilter,
        searchQuery: String,
        hideSocialMedia: Boolean
    ): List<MediaItem> {
        val socialFiltered = if (hideSocialMedia) {
            items.filter { !it.isSocialMedia() }
        } else {
            items
        }
        val typeFiltered = when (filter) {
            MediaFilter.ALL -> socialFiltered
            MediaFilter.VIDEO -> socialFiltered.filter { it.mediaType == MediaType.VIDEO }
            MediaFilter.AUDIO -> socialFiltered.filter { it.mediaType == MediaType.AUDIO }
        }
        if (searchQuery.isBlank()) return typeFiltered
        val query = searchQuery.trim().lowercase()
        return typeFiltered.filter { item ->
            item.title.lowercase().contains(query) ||
            (item.artist?.lowercase()?.contains(query) == true) ||
            (item.album?.lowercase()?.contains(query) == true) ||
            (item.resolution?.lowercase()?.contains(query) == true) ||
            item.mimeType.lowercase().contains(query)
        }
    }

    override suspend fun deleteMediaFiles(items: List<MediaItem>): DeleteResult {
        val result = dataSource.deleteMediaFiles(items)
        if (result is DeleteResult.Success) {
            scanMedia()
        }
        return result
    }
}
