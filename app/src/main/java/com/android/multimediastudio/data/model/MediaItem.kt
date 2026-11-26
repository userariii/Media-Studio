package com.android.multimediastudio.data.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Locale

enum class MediaType {
    VIDEO,
    AUDIO
}

enum class MediaFilter {
    ALL,
    VIDEO,
    AUDIO
}

enum class MediaSortOption(val label: String, val shortLabel: String) {
    DATE_MODIFIED("Date Modified", "Date"),
    DATE_ADDED("Date Added", "Added"),
    NAME("Name", "Name"),
    SIZE("Size", "Size"),
    DURATION("Duration", "Duration");

    fun defaultDirection(): SortDirection = when (this) {
        NAME -> SortDirection.ASCENDING
        DATE_MODIFIED, DATE_ADDED, SIZE, DURATION -> SortDirection.DESCENDING
    }
}

enum class SortDirection(val label: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending");

    fun toggled(): SortDirection = if (this == ASCENDING) DESCENDING else ASCENDING
}

fun sanitizeArtist(artist: String?): String? {
    if (artist.isNullOrBlank()) return null
    val trimmed = artist.trim()
    val lower = trimmed.lowercase(Locale.ROOT)
    return if (lower == "<unknown>" || lower == "unknown" || lower == "unknown artist") {
        null
    } else {
        trimmed
    }
}

fun sanitizeAlbum(album: String?): String? {
    if (album.isNullOrBlank()) return null
    val trimmed = album.trim()
    val lower = trimmed.lowercase(Locale.ROOT)
    return if (lower == "<unknown>" || lower == "unknown" || lower == "unknown album") {
        null
    } else {
        trimmed
    }
}

private val SOCIAL_APP_KEYWORDS = arrayOf(
    "whatsapp", "telegram", "facebook", "instagram", "snapchat",
    "tiktok", "twitter", "messenger", "viber", "signal", "line"
)

private val WA_MEDIA_REGEX = Regex("(vid|aud|ptt|img|doc)-\\d{8}-wa\\d+", RegexOption.IGNORE_CASE)

private val REEL_KEYWORDS = arrayOf(
    "shorts", "short", "reels", "reel", "tiktok", "status", "stories", "story"
)

@Parcelize
data class MediaItem(
    val id: Long,
    val title: String,
    val contentUri: Uri? = null,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String = "",
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val thumbnailUri: Uri? = null,
    val mediaType: MediaType = MediaType.VIDEO,
    val artist: String? = null,
    val album: String? = null,
    val resolution: String? = null,
    val displayName: String? = null,
    val relativePath: String? = null,
    val filePath: String? = null,
) : Parcelable {
    val cleanArtist: String? get() = sanitizeArtist(artist)
    val cleanAlbum: String? get() = sanitizeAlbum(album)

    private fun containsAnyKeyword(keywords: Array<String>, includeArtistAndAlbum: Boolean): Boolean {
        for (keyword in keywords) {
            if (title.contains(keyword, ignoreCase = true) ||
                (displayName != null && displayName.contains(keyword, ignoreCase = true)) ||
                (relativePath != null && relativePath.contains(keyword, ignoreCase = true)) ||
                (filePath != null && filePath.contains(keyword, ignoreCase = true)) ||
                (includeArtistAndAlbum && (
                    (artist != null && artist.contains(keyword, ignoreCase = true)) ||
                    (album != null && album.contains(keyword, ignoreCase = true))
                ))
            ) {
                return true
            }
        }
        return false
    }

    fun isSocialMedia(): Boolean {
        if (containsAnyKeyword(SOCIAL_APP_KEYWORDS, includeArtistAndAlbum = true)) {
            return true
        }

        if (WA_MEDIA_REGEX.containsMatchIn(title) ||
            (displayName != null && WA_MEDIA_REGEX.containsMatchIn(displayName))
        ) {
            return true
        }

        return false
    }

    fun isReelOrShort(): Boolean {
        if (mediaType != MediaType.VIDEO) return false
        val res = resolution
        if (!res.isNullOrBlank()) {
            val xIndex = res.indexOf('x')
            if (xIndex > 0 && xIndex < res.length - 1) {
                val w = res.substring(0, xIndex).toIntOrNull() ?: 0
                val h = res.substring(xIndex + 1).toIntOrNull() ?: 0
                if (w > 0 && h > 0) {
                    return h > w
                }
            }
        }
        return containsAnyKeyword(REEL_KEYWORDS, includeArtistAndAlbum = false)
    }

    val formattedDuration: String @JvmName("getFormattedDurationProp") get() = getFormattedDuration()
    val formattedSize: String @JvmName("getFormattedSizeProp") get() = getFormattedSize()

    fun getFormattedSubtitle(
        fallback: String? = if (mediaType == MediaType.AUDIO) "Local Audio Folder" else null,
        includeFolder: Boolean = true
    ): String? {
        val a = cleanArtist
        val b = cleanAlbum
        if (!includeFolder) {
            return if (!a.isNullOrBlank()) a else null
        }
        val formattedAlbum = if (!b.isNullOrBlank()) {
            if (b.contains("folder", ignoreCase = true)) b else "$b Folder"
        } else null

        return when {
            !a.isNullOrBlank() && formattedAlbum != null -> "$a • $formattedAlbum"
            a.isNullOrBlank() && formattedAlbum != null -> formattedAlbum
            !a.isNullOrBlank() && formattedAlbum == null -> a
            else -> fallback
        }
    }

    fun getFormattedDuration(): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = durationMs / 1000L
        val seconds = (totalSeconds % 60L).toInt()
        val minutes = ((totalSeconds / 60L) % 60L).toInt()
        val hours = totalSeconds / 3600L
        return buildString(8) {
            if (hours > 0L) {
                append(hours)
                append(':')
            }
            if (minutes < 10) append('0')
            append(minutes)
            append(':')
            if (seconds < 10) append('0')
            append(seconds)
        }
    }

    fun getFormattedSize(): String {
        if (sizeBytes <= 0) return "0 B"
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.getDefault(), "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.getDefault(), "%.0f KB", kb)
            else -> "$sizeBytes B"
        }
    }
}
