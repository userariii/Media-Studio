package com.android.multimediastudio.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import java.io.File
import java.util.Locale
import kotlin.math.absoluteValue

object ExternalMediaResolver {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "3gp", "mov", "flv", "wmv", "ts", "m4v", "mpg", "mpeg",
    )

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus", "wma", "amr", "mid", "midi", "oga"
    )

    fun resolveMediaItem(context: Context, intent: Intent): MediaItem? {
        val uri = intent.data
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: return null

        val scheme = uri.scheme?.lowercase(Locale.ROOT)

        var displayName: String? = null
        var sizeBytes = 0L

        // 1. Try querying OpenableColumns via ContentResolver
        if (scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if ((nameIndex != -1) && !cursor.isNull(nameIndex)) {
                            displayName = cursor.getString(nameIndex)
                        }
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            sizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {
                // Content provider query might fail or require specific permissions
            }
        }

        // 2. Fallbacks for displayName
        val resolvedDisplayName = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: uri.path?.substringAfterLast('/')
            ?: "External Media"

        // 3. Fallbacks for sizeBytes
        if (sizeBytes <= 0L) {
            try {
                if (scheme == ContentResolver.SCHEME_CONTENT) {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        sizeBytes = pfd.statSize
                    }
                } else if (scheme == ContentResolver.SCHEME_FILE || scheme == null) {
                    uri.path?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            sizeBytes = file.length()
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore stat error
            }
        }

        // 4. Determine MIME type
        val extension = resolvedDisplayName.substringAfterLast('.', "")
            .ifBlank { MimeTypeMap.getFileExtensionFromUrl(uri.toString()) }
            .lowercase(Locale.ROOT)

        val rawMimeType = intent.type?.takeIf { it.isNotBlank() && it != "*/*" && !it.endsWith("/*") }
            ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?: run {
                if (extension.isNotBlank()) {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                } else null
            }
            ?: ""

        // 5. Determine MediaType (VIDEO vs AUDIO)
        val isVideo = rawMimeType.startsWith("video/", ignoreCase = true) ||
                VIDEO_EXTENSIONS.contains(extension) ||
                intent.type?.startsWith("video/", ignoreCase = true) == true

        val isAudio = rawMimeType.startsWith("audio/", ignoreCase = true) ||
                AUDIO_EXTENSIONS.contains(extension) ||
                intent.type?.startsWith("audio/", ignoreCase = true) == true

        val mediaType = if (isVideo || !isAudio) MediaType.VIDEO else MediaType.AUDIO

        // 6. Extract metadata using MediaMetadataRetriever
        var durationMs = 0L
        var metaTitle: String? = null
        var metaArtist: String? = null
        var metaAlbum: String? = null
        var resolution: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            if (scheme == ContentResolver.SCHEME_CONTENT) {
                retriever.setDataSource(context, uri)
            } else {
                val path = uri.path
                if (path != null && File(path).exists()) {
                    retriever.setDataSource(path)
                } else {
                    retriever.setDataSource(context, uri)
                }
            }

            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }

            metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }

            metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }

            if (mediaType == MediaType.VIDEO) {
                val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0

                val (width, height) = if (rotation == 90 || rotation == 270) {
                    Pair(rawHeight, rawWidth)
                } else {
                    Pair(rawWidth, rawHeight)
                }

                if (width != null && height != null && width > 0 && height > 0) {
                    resolution = "${width}x$height"
                }
            }
        } catch (_: Exception) {
            // Ignore metadata extraction failure
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        // 7. Extract Title
        val title = metaTitle
            ?: resolvedDisplayName.substringBeforeLast('.').takeIf { it.isNotBlank() }
            ?: resolvedDisplayName

        // Unique negative ID to avoid collision with positive MediaStore IDs
        val generatedId = -(uri.toString().hashCode().toLong().absoluteValue.coerceAtLeast(1L)) - 10000L

        val currentTimeSec = System.currentTimeMillis() / 1000L

        return MediaItem(
            id = generatedId,
            title = title,
            contentUri = uri,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            mimeType = rawMimeType.ifBlank { if (mediaType == MediaType.VIDEO) "video/*" else "audio/*" },
            dateAdded = currentTimeSec,
            dateModified = currentTimeSec,
            thumbnailUri = if (mediaType == MediaType.VIDEO) uri else null,
            mediaType = mediaType,
            artist = metaArtist,
            album = metaAlbum,
            resolution = resolution,
            displayName = resolvedDisplayName,
            relativePath = null,
            filePath = if (scheme == ContentResolver.SCHEME_FILE) uri.path else null
        )
    }
}
