package com.android.multimediastudio.ui.player

import android.graphics.Rect
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.android.multimediastudio.data.model.MediaItem
import java.util.Locale

enum class LoopMode(val label: String, val media3Mode: Int) {
    OFF("Loop Off", Player.REPEAT_MODE_OFF),
    ALL("Loop All", Player.REPEAT_MODE_ALL),
    ONE("Loop One", Player.REPEAT_MODE_ONE);

    fun next(): LoopMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}

data class AudioTrackInfo(
    val id: String,
    val group: Tracks.Group,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
    val channelCount: Int,
    val mimeType: String?,
    val isSelected: Boolean
) {
    fun getDisplayName(mediaTitle: String? = null): String {
        val langName = language?.let {
            try { Locale.forLanguageTag(it).displayLanguage } catch (_: Exception) { null }
        }?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: language

        val isContainerTitleLabel = !label.isNullOrBlank() && (
            (mediaTitle != null && label.contains(mediaTitle, ignoreCase = true)) ||
            label.contains(Regex("(?i)\\b(1080p|720p|2160p|4k|480p|blu-ray|bluray|web-dl|webrip|hdrip|dvdrip)\\b"))
        )

        val name = when {
            !label.isNullOrBlank() && !isContainerTitleLabel -> label
            !langName.isNullOrBlank() -> langName
            else -> "Audio Track ${trackIndex + 1}"
        }
        val channels = when (channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1 Surround"
            8 -> "7.1 Surround"
            else -> if (channelCount > 0) "$channelCount ch" else ""
        }
        return if (channels.isNotBlank()) "$name ($channels)" else name
    }
}

data class SubtitleTrackInfo(
    val id: String,
    val group: Tracks.Group,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
    val mimeType: String?,
    val isSelected: Boolean
) {
    fun getDisplayName(mediaTitle: String? = null): String {
        val langName = language?.let {
            try { Locale.forLanguageTag(it).displayLanguage } catch (_: Exception) { null }
        }?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: language

        val isContainerTitleLabel = !label.isNullOrBlank() && (
            (mediaTitle != null && label.contains(mediaTitle, ignoreCase = true)) ||
            label.contains(Regex("(?i)\\b(1080p|720p|2160p|4k|480p|blu-ray|bluray|web-dl|webrip|hdrip|dvdrip)\\b"))
        )

        return when {
            !label.isNullOrBlank() && !isContainerTitleLabel -> label
            !langName.isNullOrBlank() -> langName
            else -> "Subtitle ${trackIndex + 1}"
        }
    }
}

data class PlayerUiState(
    val currentMediaItem: MediaItem? = null,
    val mediaQueue: List<MediaItem> = emptyList(),
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isFullScreen: Boolean = false,
    val isBuffering: Boolean = false,
    val hasEnded: Boolean = false,
    val errorMessage: String? = null,
    val areControlsVisible: Boolean = true,
    val isUserScrubbing: Boolean = false,
    val isScreenLocked: Boolean = false,
    val isLandscape: Boolean = false,
    val isInPipMode: Boolean = false,
    val isExpanded: Boolean = false,
    val isShuffleModeEnabled: Boolean = false,
    val loopMode: LoopMode = LoopMode.OFF,
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val selectedAudioTrackId: String? = null,
    val subtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    val selectedSubtitleTrackId: String? = null,
    val showAudioTrackSheet: Boolean = false,
    val showSubtitleTrackSheet: Boolean = false,
    val showSpeedMenu: Boolean = false,
    val playedFromLandscape: Boolean = false,
    val interactionNonce: Int = 0,
    val videoSourceRect: Rect? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0
) {
    fun getFormattedCurrentPosition(): String = formatTime(currentPositionMs)

    fun getFormattedDuration(): String = formatTime(durationMs)

    val progressFraction: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    companion object {
        val AVAILABLE_PLAYBACK_SPEEDS = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)

        fun formatTime(timeMs: Long): String {
            if (timeMs <= 0) return "00:00"
            val totalSeconds = timeMs / 1000
            val seconds = totalSeconds % 60
            val minutes = (totalSeconds / 60) % 60
            val hours = totalSeconds / 3600
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        }
    }
}
