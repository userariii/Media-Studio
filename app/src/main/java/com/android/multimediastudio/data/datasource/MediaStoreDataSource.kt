package com.android.multimediastudio.data.datasource

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import com.android.multimediastudio.data.model.sanitizeAlbum
import com.android.multimediastudio.data.model.sanitizeArtist
import com.android.multimediastudio.util.ThumbnailNegativeCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

sealed class DeleteResult {
    data object Success : DeleteResult()
    data class RequiresPermission(val intentSender: IntentSender) : DeleteResult()
    data class Error(val message: String) : DeleteResult()
}

sealed class MediaChangeEvent {
    data class UriChanges(
        val videoIds: Set<Long> = emptySet(),
        val audioIds: Set<Long> = emptySet(),
        val refreshVideoCollection: Boolean = false,
        val refreshAudioCollection: Boolean = false
    ) : MediaChangeEvent()

    data object FullSyncNeeded : MediaChangeEvent()
}

internal fun mergeMediaChangeEvents(a: MediaChangeEvent, b: MediaChangeEvent): MediaChangeEvent {
    if (a is MediaChangeEvent.FullSyncNeeded || b is MediaChangeEvent.FullSyncNeeded) {
        return MediaChangeEvent.FullSyncNeeded
    }
    val first = a as MediaChangeEvent.UriChanges
    val second = b as MediaChangeEvent.UriChanges
    val refreshVideo = first.refreshVideoCollection || second.refreshVideoCollection
    val refreshAudio = first.refreshAudioCollection || second.refreshAudioCollection
    return MediaChangeEvent.UriChanges(
        videoIds = if (refreshVideo) emptySet() else first.videoIds + second.videoIds,
        audioIds = if (refreshAudio) emptySet() else first.audioIds + second.audioIds,
        refreshVideoCollection = refreshVideo,
        refreshAudioCollection = refreshAudio
    )
}

interface MediaStoreDataSource {
    suspend fun fetchVideoFiles(): List<MediaItem>
    suspend fun fetchAudioFiles(): List<MediaItem>
    suspend fun fetchAllMediaFiles(): List<MediaItem>
    suspend fun deleteMediaFiles(items: List<MediaItem>): DeleteResult
    fun observeMediaChanges(): Flow<MediaChangeEvent> = emptyFlow()
    suspend fun syncIncremental(
        currentItems: List<MediaItem>,
        event: MediaChangeEvent
    ): List<MediaItem> = fetchAllMediaFiles()
}

internal fun extractMediaTitle(displayName: String?, rawTitle: String?, fallback: String): String {
    val primaryDisplayNameTitle = displayName?.takeIf { it.isNotBlank() }?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
    return primaryDisplayNameTitle
        ?: rawTitle?.takeIf { it.isNotBlank() }
        ?: displayName?.takeIf { it.isNotBlank() }
        ?: fallback
}

class MediaStoreDataSourceImpl(
    private val context: Context
) : MediaStoreDataSource {

    @Volatile
    private var lastVideoGeneration: Long = -1L

    @Volatile
    private var lastAudioGeneration: Long = -1L

    override suspend fun fetchVideoFiles(): List<MediaItem> = withContext(Dispatchers.IO) {
        val (items, maxGen) = queryVideos(selection = null, selectionArgs = null)
        lastVideoGeneration = if (maxGen >= 0L) maxGen else currentExternalGeneration()
        items
    }

    override suspend fun fetchAudioFiles(): List<MediaItem> = withContext(Dispatchers.IO) {
        val (items, maxGen) = queryAudios(selection = null, selectionArgs = null)
        lastAudioGeneration = if (maxGen >= 0L) maxGen else currentExternalGeneration()
        items
    }

    override suspend fun fetchAllMediaFiles(): List<MediaItem> = withContext(Dispatchers.IO) {
        val videos = fetchVideoFiles()
        val audios = fetchAudioFiles()
        (videos + audios).sortedByDescending { it.dateModified }
    }

    override fun observeMediaChanges(): Flow<MediaChangeEvent> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val videoUriPrefix = MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString()
        val audioUriPrefix = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString()

        fun classifyUri(uri: Uri?, defaultVideo: Boolean, defaultAudio: Boolean): MediaChangeEvent.UriChanges {
            if (uri == null) {
                return MediaChangeEvent.UriChanges(
                    refreshVideoCollection = defaultVideo,
                    refreshAudioCollection = defaultAudio
                )
            }
            val uriStr = uri.toString()
            val parsedId = try {
                ContentUris.parseId(uri)
            } catch (_: Exception) {
                -1L
            }
            return when {
                uriStr.startsWith(videoUriPrefix) -> {
                    if (parsedId >= 0L) {
                        MediaChangeEvent.UriChanges(videoIds = setOf(parsedId))
                    } else {
                        MediaChangeEvent.UriChanges(refreshVideoCollection = true)
                    }
                }
                uriStr.startsWith(audioUriPrefix) -> {
                    if (parsedId >= 0L) {
                        MediaChangeEvent.UriChanges(audioIds = setOf(parsedId))
                    } else {
                        MediaChangeEvent.UriChanges(refreshAudioCollection = true)
                    }
                }
                else -> MediaChangeEvent.UriChanges(
                    refreshVideoCollection = defaultVideo,
                    refreshAudioCollection = defaultAudio
                )
            }
        }

        fun createObserver(isVideo: Boolean): ContentObserver {
            return object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    onChange(selfChange, null)
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    trySend(classifyUri(uri, defaultVideo = isVideo, defaultAudio = !isVideo))
                }

                override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
                    if (uris.isEmpty()) {
                        trySend(
                            MediaChangeEvent.UriChanges(
                                refreshVideoCollection = isVideo,
                                refreshAudioCollection = !isVideo
                            )
                        )
                        return
                    }
                    var combined = MediaChangeEvent.UriChanges()
                    for (u in uris) {
                        val next = classifyUri(u, defaultVideo = isVideo, defaultAudio = !isVideo)
                        combined = mergeMediaChangeEvents(combined, next) as MediaChangeEvent.UriChanges
                    }
                    trySend(combined)
                }
            }
        }

        val videoObserver = createObserver(isVideo = true)
        val audioObserver = createObserver(isVideo = false)
        val resolver = context.contentResolver

        try {
            resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, videoObserver)
            resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, audioObserver)
        } catch (_: Exception) {
        }

        awaitClose {
            try { resolver.unregisterContentObserver(videoObserver) } catch (_: Exception) {}
            try { resolver.unregisterContentObserver(audioObserver) } catch (_: Exception) {}
        }
    }

    override suspend fun syncIncremental(
        currentItems: List<MediaItem>,
        event: MediaChangeEvent
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        if (currentItems.isEmpty() && lastVideoGeneration < 0L && lastAudioGeneration < 0L) {
            return@withContext fetchAllMediaFiles()
        }

        val currentVideos = currentItems.filter { it.mediaType == MediaType.VIDEO }
        val currentAudios = currentItems.filter { it.mediaType == MediaType.AUDIO }

        val (updatedVideos, videoChanged) = when (event) {
            is MediaChangeEvent.FullSyncNeeded -> syncVideoCollectionIncremental(currentVideos)
            is MediaChangeEvent.UriChanges -> when {
                event.refreshVideoCollection -> syncVideoCollectionIncremental(currentVideos)
                event.videoIds.isNotEmpty() -> syncVideosByIds(currentVideos, event.videoIds)
                else -> Pair(currentVideos, false)
            }
        }

        val (updatedAudios, audioChanged) = when (event) {
            is MediaChangeEvent.FullSyncNeeded -> syncAudioCollectionIncremental(currentAudios)
            is MediaChangeEvent.UriChanges -> when {
                event.refreshAudioCollection -> syncAudioCollectionIncremental(currentAudios)
                event.audioIds.isNotEmpty() -> syncAudiosByIds(currentAudios, event.audioIds)
                else -> Pair(currentAudios, false)
            }
        }

        if (!videoChanged && !audioChanged) {
            return@withContext currentItems
        }

        (updatedVideos + updatedAudios).sortedByDescending { it.dateModified }
    }

    private fun syncVideosByIds(
        currentVideos: List<MediaItem>,
        ids: Set<Long>
    ): Pair<List<MediaItem>, Boolean> {
        val fetched = fetchVideosByIds(ids)
        val fetchedById = fetched.associateBy { it.id }
        val resultMap = LinkedHashMap<Long, MediaItem>(currentVideos.size + fetchedById.size)
        for (item in currentVideos) {
            resultMap[item.id] = item
        }

        var changed = false
        for (id in ids) {
            val newItem = fetchedById[id]
            val existing = resultMap[id]
            if (newItem == null) {
                if (resultMap.remove(id) != null) {
                    changed = true
                }
            } else if (existing != newItem) {
                resultMap[id] = newItem
                newItem.contentUri?.toString()?.let { ThumbnailNegativeCache.invalidate(it) }
                changed = true
            }
        }
        return Pair(if (changed) resultMap.values.toList() else currentVideos, changed)
    }

    private fun syncAudiosByIds(
        currentAudios: List<MediaItem>,
        ids: Set<Long>
    ): Pair<List<MediaItem>, Boolean> {
        val fetched = fetchAudiosByIds(ids)
        val fetchedById = fetched.associateBy { it.id }
        val resultMap = LinkedHashMap<Long, MediaItem>(currentAudios.size + fetchedById.size)
        for (item in currentAudios) {
            resultMap[item.id] = item
        }

        var changed = false
        for (id in ids) {
            val newItem = fetchedById[id]
            val existing = resultMap[id]
            if (newItem == null) {
                if (resultMap.remove(id) != null) {
                    changed = true
                }
            } else if (existing != newItem) {
                resultMap[id] = newItem
                newItem.contentUri?.toString()?.let { ThumbnailNegativeCache.invalidate(it) }
                changed = true
            }
        }
        return Pair(if (changed) resultMap.values.toList() else currentAudios, changed)
    }

    private fun syncVideoCollectionIncremental(
        currentVideos: List<MediaItem>
    ): Pair<List<MediaItem>, Boolean> {
        val currentMap = LinkedHashMap<Long, MediaItem>(currentVideos.size)
        for (item in currentVideos) {
            currentMap[item.id] = item
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lastVideoGeneration >= 0L) {
            val (genUpdated, maxGen) = queryVideos(
                selection = "${MediaStore.MediaColumns.GENERATION_MODIFIED} > ?",
                selectionArgs = arrayOf(lastVideoGeneration.toString())
            )
            val existingIds = queryAllIds(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            if (existingIds != null) {
                val updatedById = genUpdated.associateByTo(HashMap(genUpdated.size)) { it.id }
                val uncachedMissingIds = existingIds.filterTo(HashSet()) { id ->
                    !currentMap.containsKey(id) && !updatedById.containsKey(id)
                }
                if (uncachedMissingIds.isNotEmpty()) {
                    for (item in fetchVideosByIds(uncachedMissingIds)) {
                        updatedById[item.id] = item
                    }
                }

                var changed = false
                val iterator = currentMap.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (!existingIds.contains(entry.key)) {
                        iterator.remove()
                        changed = true
                    }
                }
                for ((id, newItem) in updatedById) {
                    val existing = currentMap[id]
                    if (existing != newItem) {
                        currentMap[id] = newItem
                        newItem.contentUri?.toString()?.let { ThumbnailNegativeCache.invalidate(it) }
                        changed = true
                    }
                }
                if (maxGen > lastVideoGeneration) {
                    lastVideoGeneration = maxGen
                }
                return Pair(if (changed) currentMap.values.toList() else currentVideos, changed)
            }
        }

        // Pre-API 30 fallback: lightweight (_ID, DATE_MODIFIED, SIZE) diff, fetching full columns only for changed/new IDs
        val snapshot = queryLightSnapshot(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            ?: return Pair(currentVideos, false)

        val changedOrNewIds = HashSet<Long>()
        for ((id, meta) in snapshot) {
            val existing = currentMap[id]
            if (existing == null || existing.dateModified != meta.first || existing.sizeBytes != meta.second) {
                changedOrNewIds.add(id)
            }
        }

        var changed = false
        val iterator = currentMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!snapshot.containsKey(entry.key)) {
                iterator.remove()
                changed = true
            }
        }

        if (changedOrNewIds.isNotEmpty()) {
            for (newItem in fetchVideosByIds(changedOrNewIds)) {
                if (currentMap[newItem.id] != newItem) {
                    currentMap[newItem.id] = newItem
                    newItem.contentUri?.toString()?.let { ThumbnailNegativeCache.invalidate(it) }
                    changed = true
                }
            }
        }

        return Pair(if (changed) currentMap.values.toList() else currentVideos, changed)
    }

    private fun syncAudioCollectionIncremental(
        currentAudios: List<MediaItem>
    ): Pair<List<MediaItem>, Boolean> {
        val currentMap = LinkedHashMap<Long, MediaItem>(currentAudios.size)
        for (item in currentAudios) {
            currentMap[item.id] = item
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lastAudioGeneration >= 0L) {
            val (genUpdated, maxGen) = queryAudios(
                selection = "${MediaStore.MediaColumns.GENERATION_MODIFIED} > ?",
                selectionArgs = arrayOf(lastAudioGeneration.toString())
            )
            val existingIds = queryAllIds(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            if (existingIds != null) {
                val updatedById = genUpdated.associateByTo(HashMap(genUpdated.size)) { it.id }
                val uncachedMissingIds = existingIds.filterTo(HashSet()) { id ->
                    !currentMap.containsKey(id) && !updatedById.containsKey(id)
                }
                if (uncachedMissingIds.isNotEmpty()) {
                    for (item in fetchAudiosByIds(uncachedMissingIds)) {
                        updatedById[item.id] = item
                    }
                }

                var changed = false
                val iterator = currentMap.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (!existingIds.contains(entry.key)) {
                        iterator.remove()
                        changed = true
                    }
                }
                for ((id, newItem) in updatedById) {
                    val existing = currentMap[id]
                    if (existing != newItem) {
                        currentMap[id] = newItem
                        newItem.contentUri?.toString()?.let { ThumbnailNegativeCache.invalidate(it) }
                        changed = true
                    }
                }
                if (maxGen > lastAudioGeneration) {
                    lastAudioGeneration = maxGen
                }
                return Pair(if (changed) currentMap.values.toList() else currentAudios, changed)
            }
        }

        val snapshot = queryLightSnapshot(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            ?: return Pair(currentAudios, false)

        val changedOrNewIds = HashSet<Long>()
        for ((id, meta) in snapshot) {
            val existing = currentMap[id]
            if (existing == null || existing.dateModified != meta.first || existing.sizeBytes != meta.second) {
                changedOrNewIds.add(id)
            }
        }

        var changed = false
        val iterator = currentMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!snapshot.containsKey(entry.key)) {
                iterator.remove()
                changed = true
            }
        }

        if (changedOrNewIds.isNotEmpty()) {
            for (newItem in fetchAudiosByIds(changedOrNewIds)) {
                if (currentMap[newItem.id] != newItem) {
                    currentMap[newItem.id] = newItem
                    newItem.contentUri?.toString()?.let { ThumbnailNegativeCache.invalidate(it) }
                    changed = true
                }
            }
        }

        return Pair(if (changed) currentMap.values.toList() else currentAudios, changed)
    }

    private fun fetchVideosByIds(ids: Set<Long>): List<MediaItem> {
        if (ids.isEmpty()) return emptyList()
        val result = mutableListOf<MediaItem>()
        for (chunk in ids.chunked(SQLITE_IN_CHUNK_SIZE)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${MediaStore.Video.Media._ID} IN ($placeholders)"
            val args = chunk.map { it.toString() }.toTypedArray()
            val (items, _) = queryVideos(selection, args)
            result.addAll(items)
        }
        return result
    }

    private fun fetchAudiosByIds(ids: Set<Long>): List<MediaItem> {
        if (ids.isEmpty()) return emptyList()
        val result = mutableListOf<MediaItem>()
        for (chunk in ids.chunked(SQLITE_IN_CHUNK_SIZE)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${MediaStore.Audio.Media._ID} IN ($placeholders)"
            val args = chunk.map { it.toString() }.toTypedArray()
            val (items, _) = queryAudios(selection, args)
            result.addAll(items)
        }
        return result
    }

    private fun queryAllIds(uri: Uri): HashSet<Long>? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null
            )?.use { cursor ->
                val ids = HashSet<Long>(cursor.count.coerceAtLeast(0))
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                if (idCol != -1) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        if (id != -1L) ids.add(id)
                    }
                }
                ids
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryLightSnapshot(uri: Uri): HashMap<Long, Pair<Long, Long>>? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.SIZE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val map = HashMap<Long, Pair<Long, Long>>(cursor.count.coerceAtLeast(0))
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (idCol != -1) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        if (id == -1L) continue
                        val dateModified = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                        val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                        map[id] = Pair(dateModified, size)
                    }
                }
                map
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun currentExternalGeneration(): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return -1L
        return try {
            MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL)
        } catch (_: Exception) {
            -1L
        }
    }

    private fun queryVideos(
        selection: String?,
        selectionArgs: Array<String>?
    ): Pair<List<MediaItem>, Long> {
        val videoList = mutableListOf<MediaItem>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        var maxGen = -1L

        val projectionList = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            @Suppress("DEPRECATION")
            MediaStore.MediaColumns.DATA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.MediaColumns.GENERATION_MODIFIED)
        }
        val projection = projectionList.toTypedArray()
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val displayNameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val titleColumn = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE)
                val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val mimeTypeColumn = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val dateAddedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                @Suppress("DEPRECATION")
                val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1
                val generationColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.GENERATION_MODIFIED)
                } else -1

                while (cursor.moveToNext()) {
                    val id = if (idColumn != -1) cursor.getLong(idColumn) else -1L
                    if (id == -1L) continue

                    if (generationColumn != -1) {
                        val gen = cursor.getLong(generationColumn)
                        if (gen > maxGen) maxGen = gen
                    }

                    val displayName = getStringOrNull(cursor, displayNameColumn)
                    val rawTitle = getStringOrNull(cursor, titleColumn)
                    val title = extractMediaTitle(displayName, rawTitle, "Unknown Video")

                    val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                    val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                    val mimeType = getStringOrNull(cursor, mimeTypeColumn) ?: "video/*"
                    val dateAdded = if (dateAddedColumn != -1) cursor.getLong(dateAddedColumn) else 0L
                    val dateModified = if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn) else 0L

                    val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                    val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0
                    val resolution = if (width > 0 && height > 0) "${width}x${height}" else null

                    val filePath = getStringOrNull(cursor, dataColumn)
                    val relativePath = getStringOrNull(cursor, relativePathColumn)

                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    videoList.add(
                        MediaItem(
                            id = id,
                            title = title,
                            contentUri = contentUri,
                            durationMs = duration,
                            sizeBytes = size,
                            mimeType = mimeType,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            thumbnailUri = contentUri,
                            mediaType = MediaType.VIDEO,
                            resolution = resolution,
                            displayName = displayName,
                            relativePath = relativePath,
                            filePath = filePath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(videoList, maxGen)
    }

    private fun queryAudios(
        selection: String?,
        selectionArgs: Array<String>?
    ): Pair<List<MediaItem>, Long> {
        val audioList = mutableListOf<MediaItem>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        var maxGen = -1L

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            @Suppress("DEPRECATION")
            MediaStore.MediaColumns.DATA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.MediaColumns.GENERATION_MODIFIED)
        }
        val projection = projectionList.toTypedArray()
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val displayNameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val titleColumn = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE)
                val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeTypeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                @Suppress("DEPRECATION")
                val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1
                val generationColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.GENERATION_MODIFIED)
                } else -1

                while (cursor.moveToNext()) {
                    val id = if (idColumn != -1) cursor.getLong(idColumn) else -1L
                    if (id == -1L) continue

                    if (generationColumn != -1) {
                        val gen = cursor.getLong(generationColumn)
                        if (gen > maxGen) maxGen = gen
                    }

                    val displayName = getStringOrNull(cursor, displayNameColumn)
                    val rawTitle = getStringOrNull(cursor, titleColumn)
                    val title = extractMediaTitle(displayName, rawTitle, "Unknown Audio")

                    val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                    val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                    val mimeType = getStringOrNull(cursor, mimeTypeColumn) ?: "audio/*"
                    val dateAdded = if (dateAddedColumn != -1) cursor.getLong(dateAddedColumn) else 0L
                    val dateModified = if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn) else 0L
                    val rawArtist = getStringOrNull(cursor, artistColumn)
                    val rawAlbum = getStringOrNull(cursor, albumColumn)
                    val artist = sanitizeArtist(rawArtist)
                    val album = sanitizeAlbum(rawAlbum)
                    val albumId = if (albumIdColumn != -1) cursor.getLong(albumIdColumn) else -1L

                    val filePath = getStringOrNull(cursor, dataColumn)
                    val relativePath = getStringOrNull(cursor, relativePathColumn)

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val albumArtUri = if (albumId != -1L) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        )
                    } else null

                    audioList.add(
                        MediaItem(
                            id = id,
                            title = title,
                            contentUri = contentUri,
                            durationMs = duration,
                            sizeBytes = size,
                            mimeType = mimeType,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            thumbnailUri = albumArtUri,
                            mediaType = MediaType.AUDIO,
                            artist = artist,
                            album = album,
                            displayName = displayName,
                            relativePath = relativePath,
                            filePath = filePath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(audioList, maxGen)
    }

    private fun getStringOrNull(cursor: Cursor, columnIndex: Int): String? {
        return if (columnIndex != -1) cursor.getString(columnIndex) else null
    }

    override suspend fun deleteMediaFiles(items: List<MediaItem>): DeleteResult = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext DeleteResult.Success

        val uris = items.mapNotNull { it.contentUri }
        if (uris.isEmpty()) return@withContext DeleteResult.Success

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                return@withContext DeleteResult.RequiresPermission(pendingIntent.intentSender)
            } else {
                var allSucceeded = true
                for (item in items) {
                    val uri = item.contentUri ?: continue
                    try {
                        val rowsDeleted = context.contentResolver.delete(uri, null, null)
                        if (rowsDeleted <= 0) {
                            allSucceeded = false
                        }
                    } catch (e: SecurityException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val recoverableSecurityException = e as? RecoverableSecurityException
                            if (recoverableSecurityException != null) {
                                val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                                return@withContext DeleteResult.RequiresPermission(intentSender)
                            }
                        }
                        throw e
                    }
                }
                if (allSucceeded) {
                    DeleteResult.Success
                } else {
                    DeleteResult.Error("Failed to delete selected media files")
                }
            }
        } catch (e: Exception) {
            DeleteResult.Error(e.localizedMessage ?: "File deletion failed")
        }
    }

    private companion object {
        private const val SQLITE_IN_CHUNK_SIZE = 400
    }
}
