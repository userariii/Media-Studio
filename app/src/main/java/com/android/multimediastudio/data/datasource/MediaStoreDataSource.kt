package com.android.multimediastudio.data.datasource

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import com.android.multimediastudio.data.model.sanitizeAlbum
import com.android.multimediastudio.data.model.sanitizeArtist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DeleteResult {
    data object Success : DeleteResult()
    data class RequiresPermission(val intentSender: IntentSender) : DeleteResult()
    data class Error(val message: String) : DeleteResult()
}

interface MediaStoreDataSource {
    suspend fun fetchVideoFiles(): List<MediaItem>
    suspend fun fetchAudioFiles(): List<MediaItem>
    suspend fun fetchAllMediaFiles(): List<MediaItem>
    suspend fun deleteMediaFiles(items: List<MediaItem>): DeleteResult
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

    override suspend fun fetchVideoFiles(): List<MediaItem> = withContext(Dispatchers.IO) {
        val videoList = mutableListOf<MediaItem>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
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

                while (cursor.moveToNext()) {
                    val id = if (idColumn != -1) cursor.getLong(idColumn) else -1L
                    if (id == -1L) continue

                    val displayName = if (displayNameColumn != -1) cursor.getString(displayNameColumn) else null
                    val rawTitle = if (titleColumn != -1) cursor.getString(titleColumn) else null
                    val title = extractMediaTitle(displayName, rawTitle, "Unknown Video")

                    val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                    val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                    val mimeType = if (mimeTypeColumn != -1) cursor.getString(mimeTypeColumn) ?: "video/*" else "video/*"
                    val dateAdded = if (dateAddedColumn != -1) cursor.getLong(dateAddedColumn) else 0L
                    val dateModified = if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn) else 0L

                    val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                    val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0
                    val resolution = if (width > 0 && height > 0) "${width}x${height}" else null

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
                            resolution = resolution
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        videoList
    }

    override suspend fun fetchAudioFiles(): List<MediaItem> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<MediaItem>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
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
            MediaStore.Audio.Media.ALBUM_ID
        )

        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
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

                while (cursor.moveToNext()) {
                    val id = if (idColumn != -1) cursor.getLong(idColumn) else -1L
                    if (id == -1L) continue

                    val displayName = if (displayNameColumn != -1) cursor.getString(displayNameColumn) else null
                    val rawTitle = if (titleColumn != -1) cursor.getString(titleColumn) else null
                    val title = extractMediaTitle(displayName, rawTitle, "Unknown Audio")

                    val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                    val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                    val mimeType = if (mimeTypeColumn != -1) cursor.getString(mimeTypeColumn) ?: "audio/*" else "audio/*"
                    val dateAdded = if (dateAddedColumn != -1) cursor.getLong(dateAddedColumn) else 0L
                    val dateModified = if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn) else 0L
                    val rawArtist = if (artistColumn != -1) cursor.getString(artistColumn) else null
                    val rawAlbum = if (albumColumn != -1) cursor.getString(albumColumn) else null
                    val artist = sanitizeArtist(rawArtist)
                    val album = sanitizeAlbum(rawAlbum)
                    val albumId = if (albumIdColumn != -1) cursor.getLong(albumIdColumn) else -1L

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
                            album = album
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        audioList
    }

    override suspend fun fetchAllMediaFiles(): List<MediaItem> = withContext(Dispatchers.IO) {
        val videos = fetchVideoFiles()
        val audios = fetchAudioFiles()
        (videos + audios).sortedByDescending { it.dateModified }
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
}
