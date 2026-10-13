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

    fun isSocialMedia(): Boolean {
        val combinedText = buildString {
            append(title.lowercase(Locale.ROOT)).append(" ")
            displayName?.let { append(it.lowercase(Locale.ROOT)).append(" ") }
            artist?.let { append(it.lowercase(Locale.ROOT)).append(" ") }
            album?.let { append(it.lowercase(Locale.ROOT)).append(" ") }
            relativePath?.let { append(it.lowercase(Locale.ROOT)).append(" ") }
            filePath?.let { append(it.lowercase(Locale.ROOT)).append(" ") }
        }

        val socialAppKeywords = listOf(
            "whatsapp", "telegram", "facebook", "instagram", "snapchat",
            "tiktok", "twitter", "messenger", "viber", "signal", "line"
        )

        if (socialAppKeywords.any { combinedText.contains(it) }) {
            return true
        }

        val waPattern = Regex("(vid|aud|ptt|img|doc)-\\d{8}-wa\\d+", RegexOption.IGNORE_CASE)
        if (waPattern.containsMatchIn(title) || (displayName != null && waPattern.containsMatchIn(displayName))) {
            return true
        }

        return false
    }

    fun isReelOrShort(): Boolean {
        if (mediaType != MediaType.VIDEO) return false
        val res = resolution
        if (!res.isNullOrBlank() && res.contains("x")) {
            val parts = res.split("x")
            val w = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val h = parts.getOrNull(1)?.toIntOrNull() ?: 0
            if (w > 0 && h > 0) {
                return h > w
            }
        }
        val combined = buildString {
            append(title).append(" ")
            displayName?.let { append(it).append(" ") }
            relativePath?.let { append(it).append(" ") }
            filePath?.let { append(it).append(" ") }
        }.lowercase(Locale.ROOT)

        val reelKeywords = listOf("shorts", "short", "reels", "reel", "tiktok", "status", "stories", "story")
        return reelKeywords.any { combined.contains(it) }
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
        if (durationMs <= 0) return "00:00"
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
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
