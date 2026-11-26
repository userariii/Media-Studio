package com.android.multimediastudio.data.repository

import com.android.multimediastudio.data.datasource.DeleteResult
import com.android.multimediastudio.data.datasource.MediaChangeEvent
import com.android.multimediastudio.data.datasource.MediaStoreDataSource
import com.android.multimediastudio.data.datasource.mergeMediaChangeEvents
import com.android.multimediastudio.data.model.MediaFilter
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface MediaRepository {
    val mediaStream: Flow<List<MediaItem>>
    suspend fun scanMedia(filter: MediaFilter = MediaFilter.ALL): List<MediaItem>
    suspend fun syncMediaChanges(event: MediaChangeEvent = MediaChangeEvent.FullSyncNeeded): List<MediaItem>
    suspend fun observeMediaChanges()
    fun filterMedia(
        items: List<MediaItem>,
        filter: MediaFilter,
        searchQuery: String = "",
        hideSocialMedia: Boolean = false
    ): List<MediaItem>
    suspend fun deleteMediaFiles(items: List<MediaItem>): DeleteResult
}

class MediaRepositoryImpl(
    private val dataSource: MediaStoreDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val debounceMs: Long = CHANGE_DEBOUNCE_MS
) : MediaRepository {

    private val _mediaState = MutableStateFlow<List<MediaItem>>(emptyList())
    override val mediaStream: Flow<List<MediaItem>> = _mediaState.asStateFlow()

    private val syncMutex = Mutex()

    override suspend fun scanMedia(filter: MediaFilter): List<MediaItem> {
        val allItems = syncMutex.withLock {
            val fetched = dataSource.fetchAllMediaFiles()
            _mediaState.value = fetched
            fetched
        }
        return filterMedia(allItems, filter)
    }

    override suspend fun syncMediaChanges(event: MediaChangeEvent): List<MediaItem> {
        return syncMutex.withLock {
            val current = _mediaState.value
            val updated = dataSource.syncIncremental(current, event)
            if (updated != current) {
                _mediaState.value = updated
            }
            updated
        }
    }

    override suspend fun observeMediaChanges(): Unit = coroutineScope {
        val pending = Channel<MediaChangeEvent>(Channel.UNLIMITED)
        val collectorJob = launch(ioDispatcher) {
            dataSource.observeMediaChanges().collect { event ->
                pending.send(event)
            }
        }
        try {
            withContext(ioDispatcher) {
                for (first in pending) {
                    var merged = first
                    if (debounceMs > 0L) {
                        delay(debounceMs)
                    }
                    while (true) {
                        val next = pending.tryReceive().getOrNull() ?: break
                        merged = mergeMediaChangeEvents(merged, next)
                    }
                    try {
                        syncMediaChanges(merged)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Preserve current media list on transient observer sync failure
                    }
                }
            }
        } finally {
            collectorJob.cancel()
            pending.close()
        }
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
            syncMutex.withLock {
                val deletedKeys = items.mapTo(HashSet(items.size)) { "${it.mediaType}_${it.id}" }
                val current = _mediaState.value
                val remaining = current.filterNot { "${it.mediaType}_${it.id}" in deletedKeys }
                if (remaining.size != current.size) {
                    _mediaState.value = remaining
                }
            }
        }
        return result
    }

    private companion object {
        private const val CHANGE_DEBOUNCE_MS = 250L
    }
}
