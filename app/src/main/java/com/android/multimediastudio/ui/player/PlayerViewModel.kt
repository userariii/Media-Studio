package com.android.multimediastudio.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.android.multimediastudio.PlaybackService
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerViewModel(
    playerInstance: Player? = null
) : ViewModel() {

    private var player: Player? = playerInstance
    private var isSelfManagedPlayer = false
    private var appContext: Context? = null
    internal var screenInteractiveChecker: (() -> Boolean)? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var positionUpdateJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startPositionTracker()
            } else {
                stopPositionTracker()
                updatePositionFromPlayer()
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateTracksFromPlayer(tracks)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _uiState.update { it.copy(isBuffering = true) }
                }
                Player.STATE_READY -> {
                    val duration = player?.duration?.coerceAtLeast(0L) ?: 0L
                    _uiState.update {
                        it.copy(
                            isBuffering = false,
                            durationMs = if (duration > 0) duration else it.durationMs,
                            hasEnded = false
                        )
                    }
                    updateTracksFromPlayer()
                }
                Player.STATE_ENDED -> {
                    val currentMode = _uiState.value.loopMode
                    val isShuffle = _uiState.value.isShuffleModeEnabled
                    val queue = _uiState.value.mediaQueue
                    val currentItem = _uiState.value.currentMediaItem
                    val currentIndex = queue.indexOfFirst { it.id == currentItem?.id }
                    val isLastItem = currentIndex != -1 && currentIndex == queue.size - 1

                    when {
                        currentMode == LoopMode.ONE -> {
                            seekTo(0L)
                            play()
                        }
                        currentMode == LoopMode.ALL || isShuffle -> {
                            playNext()
                        }
                        !isLastItem && currentIndex != -1 -> {
                            playNext()
                        }
                        else -> {
                            _uiState.update {
                                it.copy(
                                    isBuffering = false,
                                    isPlaying = false,
                                    hasEnded = true
                                )
                            }
                            stopPositionTracker()
                        }
                    }
                }
                Player.STATE_IDLE -> {
                    _uiState.update { it.copy(isBuffering = false) }
                }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.update { it.copy(isShuffleModeEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mode = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> LoopMode.ONE
                Player.REPEAT_MODE_ALL -> LoopMode.ALL
                else -> LoopMode.OFF
            }
            _uiState.update { it.copy(loopMode = mode) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = error.localizedMessage ?: "Playback error occurred"
                )
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updatePositionFromPlayer()
        }
    }

    init {
        player?.addListener(playerListener)
        player?.currentTracks?.let { updateTracksFromPlayer(it) }
    }

    /**
     * Initializes the player with PlaybackService / ExoPlayer configured for
     * foreground playback and MediaSession controls.
     */
    fun initializePlayer(context: Context): Player {
        appContext = context.applicationContext
        val existing = player
        if (existing != null) return existing

        startPlaybackService(context)

        val exoPlayer = try {
            PlaybackService.getSharedPlayer(context)
        } catch (_: Exception) {
            val renderersFactory = DefaultRenderersFactory(context.applicationContext)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

            ExoPlayer.Builder(context.applicationContext)
                .setRenderersFactory(renderersFactory)
                .build()
        }

        exoPlayer.addListener(playerListener)
        exoPlayer.shuffleModeEnabled = _uiState.value.isShuffleModeEnabled
        exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
        this.player = exoPlayer
        this.isSelfManagedPlayer = false

        // If media item was set before player initialization, load it
        val currentItem = _uiState.value.currentMediaItem
        if (currentItem != null) {
            playMedia(currentItem)
        }

        return exoPlayer
    }

    private fun startPlaybackService(context: Context) {
        try {
            val intent = Intent(context.applicationContext, PlaybackService::class.java)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        } catch (_: SecurityException) {
            // Security / permission exception starting foreground service
        } catch (_: IllegalStateException) {
            // Catches ForegroundServiceStartNotAllowedException on Android 12+ or illegal service state
        } catch (_: Exception) {
            // General defensive exception catch so app never crashes
        }
    }

    fun getPlayer(): Player? = player

    fun setMediaQueue(queue: List<MediaItem>) {
        _uiState.update { it.copy(mediaQueue = queue) }
    }

    fun playMedia(mediaItem: MediaItem, queue: List<MediaItem> = emptyList()) {
        val currentQueue = _uiState.value.mediaQueue
        val updatedQueue = when {
            queue.isNotEmpty() -> queue
            currentQueue.any { it.id == mediaItem.id } -> currentQueue
            else -> listOf(mediaItem)
        }

        val currentActiveItem = _uiState.value.currentMediaItem
        val isSameItem = currentActiveItem?.id == mediaItem.id
        val isPlayerIdle = player != null && player?.playbackState == Player.STATE_IDLE

        if (isSameItem && !isPlayerIdle) {
            _uiState.update { it.copy(mediaQueue = updatedQueue) }
            if (_uiState.value.hasEnded) {
                seekTo(0L)
                play()
            }
            return
        }

        val wasLandscape = _uiState.value.isLandscape
        val isTargetVideo = mediaItem.mediaType == MediaType.VIDEO
        val newLandscape = if (isTargetVideo) wasLandscape else false
        val newFullScreen = if (isTargetVideo) _uiState.value.isFullScreen else false

        _uiState.update {
            it.copy(
                currentMediaItem = mediaItem,
                mediaQueue = updatedQueue,
                durationMs = mediaItem.durationMs,
                currentPositionMs = 0L,
                hasEnded = false,
                errorMessage = null,
                isLandscape = newLandscape,
                isFullScreen = newFullScreen,
                isScreenLocked = false,
                isExpanded = it.isExpanded
            )
        }

        player?.let { p ->
            val uri = mediaItem.contentUri
            if (uri != null) {
                val metadata = MediaMetadata.Builder()
                    .setTitle(mediaItem.title)
                    .apply {
                        mediaItem.cleanArtist?.let { setArtist(it) }
                        mediaItem.cleanAlbum?.let { setAlbumTitle(it) }
                        mediaItem.thumbnailUri?.let { setArtworkUri(it) }
                    }
                    .build()

                val media3Item = Media3Item.Builder()
                    .setUri(uri)
                    .setMediaId(mediaItem.id.toString())
                    .setMediaMetadata(metadata)
                    .build()

                p.setMediaItem(media3Item)
                p.setPlaybackSpeed(_uiState.value.playbackSpeed)
                p.prepare()
                p.playWhenReady = true
            }
        }
    }

    private fun isScreenInteractive(): Boolean {
        screenInteractiveChecker?.let { return it.invoke() }
        val pm = appContext?.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isInteractive ?: true
    }

    private fun isTransitionAllowedInCurrentScreenState(fromItem: MediaItem?, toItem: MediaItem): Boolean {
        if (fromItem == null) return true
        if (isScreenInteractive()) return true

        val fromType = fromItem.mediaType
        val toType = toItem.mediaType

        return when {
            fromType == MediaType.AUDIO && toType == MediaType.VIDEO -> false
            fromType == MediaType.VIDEO && toType == MediaType.AUDIO -> false
            else -> true
        }
    }

    fun playNext() {
        val queue = _uiState.value.mediaQueue
        if (queue.isEmpty()) return
        val currentItem = _uiState.value.currentMediaItem
        val currentIndex = queue.indexOfFirst { it.id == currentItem?.id }
        val nextIndex = if (_uiState.value.isShuffleModeEnabled && queue.size > 1) {
            var randomIndex = (0 until queue.size).random()
            if (randomIndex == currentIndex) {
                randomIndex = (currentIndex + 1) % queue.size
            }
            randomIndex
        } else if (currentIndex != -1) {
            (currentIndex + 1) % queue.size
        } else {
            0
        }
        val nextItem = queue[nextIndex]

        if (!isTransitionAllowedInCurrentScreenState(currentItem, nextItem)) {
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    hasEnded = true
                )
            }
            pause()
            return
        }

        playMedia(nextItem, queue)
    }

    fun playPrevious() {
        val queue = _uiState.value.mediaQueue
        if (queue.isEmpty()) return
        val currentItem = _uiState.value.currentMediaItem
        val currentIndex = queue.indexOfFirst { it.id == currentItem?.id }
        val prevIndex = if (_uiState.value.isShuffleModeEnabled && queue.size > 1) {
            var randomIndex = (0 until queue.size).random()
            if (randomIndex == currentIndex) {
                randomIndex = if (currentIndex - 1 < 0) queue.size - 1 else currentIndex - 1
            }
            randomIndex
        } else if (currentIndex != -1) {
            if (currentIndex - 1 < 0) queue.size - 1 else currentIndex - 1
        } else {
            queue.size - 1
        }
        val prevItem = queue[prevIndex]

        if (!isTransitionAllowedInCurrentScreenState(currentItem, prevItem)) {
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    hasEnded = true
                )
            }
            pause()
            return
        }

        playMedia(prevItem, queue)
    }

    fun touchControls() {
        _uiState.update {
            it.copy(
                areControlsVisible = true,
                interactionNonce = it.interactionNonce + 1
            )
        }
    }

    fun toggleShuffleMode() {
        touchControls()
        val newShuffle = !_uiState.value.isShuffleModeEnabled
        player?.shuffleModeEnabled = newShuffle
        _uiState.update { it.copy(isShuffleModeEnabled = newShuffle) }
    }

    fun toggleLoopMode() {
        touchControls()
        val nextMode = _uiState.value.loopMode.next()
        _uiState.update { it.copy(loopMode = nextMode) }
    }

    fun play() {
        player?.play()
        _uiState.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        player?.pause()
        _uiState.update { it.copy(isPlaying = false) }
        stopPositionTracker()
        updatePositionFromPlayer()
    }

    fun togglePlayPause() {
        touchControls()
        if (_uiState.value.isPlaying) {
            pause()
        } else {
            if (_uiState.value.hasEnded) {
                seekTo(0L)
            }
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        touchControls()
        val duration = _uiState.value.durationMs
        val targetPos = if (duration > 0) {
            positionMs.coerceIn(0L, duration)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        player?.seekTo(targetPos)
        _uiState.update { it.copy(currentPositionMs = targetPos) }
    }

    fun seekForward(deltaMs: Long = 10000L) {
        val current = player?.currentPosition ?: _uiState.value.currentPositionMs
        seekTo(current + deltaMs)
    }

    fun seekRewind(deltaMs: Long = 10000L) {
        val current = player?.currentPosition ?: _uiState.value.currentPositionMs
        seekTo(current - deltaMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        touchControls()
        val validSpeed = speed.coerceIn(0.25f, 3.0f)
        player?.playbackParameters = PlaybackParameters(validSpeed)
        _uiState.update { it.copy(playbackSpeed = validSpeed) }
    }

    fun toggleFullScreen() {
        _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
    }

    fun setFullScreen(enabled: Boolean) {
        _uiState.update { it.copy(isFullScreen = enabled) }
    }

    fun toggleControlsVisibility() {
        _uiState.update { it.copy(areControlsVisible = !it.areControlsVisible) }
    }

    fun setControlsVisible(visible: Boolean) {
        _uiState.update { it.copy(areControlsVisible = visible) }
    }

    fun setUserScrubbing(isScrubbing: Boolean) {
        touchControls()
        _uiState.update {
            it.copy(
                isUserScrubbing = isScrubbing,
                areControlsVisible = if (isScrubbing) true else it.areControlsVisible
            )
        }
    }

    fun toggleScreenLock() {
        touchControls()
        _uiState.update { it.copy(isScreenLocked = !it.isScreenLocked) }
    }

    fun setScreenLocked(locked: Boolean) {
        _uiState.update { it.copy(isScreenLocked = locked) }
    }

    fun toggleOrientation() {
        touchControls()
        _uiState.update { it.copy(isLandscape = !it.isLandscape) }
    }

    fun setOrientation(landscape: Boolean) {
        _uiState.update { it.copy(isLandscape = landscape) }
    }

    fun setInPipMode(inPip: Boolean) {
        _uiState.update {
            it.copy(
                isInPipMode = inPip,
                areControlsVisible = if (inPip) false else it.areControlsVisible
            )
        }
    }

    fun expandPlayer() {
        _uiState.update { it.copy(isExpanded = true) }
    }

    fun collapsePlayer() {
        _uiState.update { it.copy(isExpanded = false) }
    }

    fun togglePlayerExpansion() {
        _uiState.update { it.copy(isExpanded = !it.isExpanded) }
    }

    fun clearActiveTrack() {
        pause()
        player?.stop()
        _uiState.update {
            it.copy(
                currentMediaItem = null,
                isExpanded = false,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = 0L
            )
        }
    }

    fun pauseOnBackground() {
        val isAudio = _uiState.value.currentMediaItem?.mediaType == MediaType.AUDIO
        val isInPip = _uiState.value.isInPipMode
        if (!isAudio && !isInPip) {
            pause()
        }
    }

    fun updateTracksFromPlayer(tracks: Tracks? = player?.currentTracks) {
        if (tracks == null) return
        val audioList = mutableListOf<AudioTrackInfo>()
        val subtitleList = mutableListOf<SubtitleTrackInfo>()
        var activeAudioId: String? = null
        var activeSubtitleId: String? = null

        val isTextDisabled = player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true

        for (group in tracks.groups) {
            val mediaTrackGroup = group.mediaTrackGroup
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    if (group.isTrackSupported(i)) {
                        val format = group.getTrackFormat(i)
                        val id = "${mediaTrackGroup.id}:$i"
                        val isSelected = group.isTrackSelected(i)
                        if (isSelected) {
                            activeAudioId = id
                        }
                        audioList.add(
                            AudioTrackInfo(
                                id = id,
                                group = group,
                                trackIndex = i,
                                language = format.language,
                                label = format.label,
                                channelCount = format.channelCount,
                                mimeType = format.sampleMimeType,
                                isSelected = isSelected
                            )
                        )
                    }
                }
            } else if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    if (group.isTrackSupported(i)) {
                        val format = group.getTrackFormat(i)
                        val id = "${mediaTrackGroup.id}:$i"
                        val isSelected = !isTextDisabled && group.isTrackSelected(i)
                        if (isSelected) {
                            activeSubtitleId = id
                        }
                        subtitleList.add(
                            SubtitleTrackInfo(
                                id = id,
                                group = group,
                                trackIndex = i,
                                language = format.language,
                                label = format.label,
                                mimeType = format.sampleMimeType,
                                isSelected = isSelected
                            )
                        )
                    }
                }
            }
        }

        _uiState.update {
            it.copy(
                audioTracks = audioList,
                selectedAudioTrackId = activeAudioId,
                subtitleTracks = subtitleList,
                selectedSubtitleTrackId = if (isTextDisabled) null else activeSubtitleId
            )
        }
    }

    fun selectAudioTrack(trackInfo: AudioTrackInfo) {
        touchControls()
        player?.let { p ->
            val newParams = p.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(trackInfo.group.mediaTrackGroup, trackInfo.trackIndex))
                .build()
            p.trackSelectionParameters = newParams
            updateTracksFromPlayer()
        }
        setShowAudioTrackSheet(false)
    }

    fun selectSubtitleTrack(trackInfo: SubtitleTrackInfo?) {
        touchControls()
        player?.let { p ->
            val builder = p.trackSelectionParameters.buildUpon()
            if (trackInfo == null) {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            } else {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(trackInfo.group.mediaTrackGroup, trackInfo.trackIndex))
            }
            p.trackSelectionParameters = builder.build()
            updateTracksFromPlayer()
        }
        setShowSubtitleTrackSheet(false)
    }

    fun setShowAudioTrackSheet(show: Boolean) {
        touchControls()
        if (show) updateTracksFromPlayer()
        _uiState.update { it.copy(showAudioTrackSheet = show) }
    }

    fun setShowSubtitleTrackSheet(show: Boolean) {
        touchControls()
        if (show) updateTracksFromPlayer()
        _uiState.update { it.copy(showSubtitleTrackSheet = show) }
    }

    fun setShowSpeedMenu(show: Boolean) {
        touchControls()
        _uiState.update { it.copy(showSpeedMenu = show) }
    }

    fun addExternalSubtitle(context: Context, subtitleUri: Uri) {
        val currentItem = _uiState.value.currentMediaItem ?: return
        val contentUri = currentItem.contentUri ?: return
        player?.let { p ->
            val currentPos = p.currentPosition
            val wasPlaying = p.isPlaying

            val subtitleConfig = Media3Item.SubtitleConfiguration.Builder(subtitleUri)
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            val newMedia3Item = Media3Item.Builder()
                .setUri(contentUri)
                .setMediaId(currentItem.id.toString())
                .setSubtitleConfigurations(listOf(subtitleConfig))
                .build()

            p.setMediaItem(newMedia3Item)
            p.prepare()
            p.seekTo(currentPos)
            p.playWhenReady = wasPlaying

            val builder = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            p.trackSelectionParameters = builder.build()
        }
    }

    fun releasePlayer() {
        stopPositionTracker()
        player?.removeListener(playerListener)
        if (isSelfManagedPlayer) {
            player?.release()
        }
        player = null
        isSelfManagedPlayer = false
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }

    private fun startPositionTracker() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                updatePositionFromPlayer()
                delay(250L)
            }
        }
    }

    private fun stopPositionTracker() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updatePositionFromPlayer() {
        player?.let { p ->
            val pos = p.currentPosition.coerceAtLeast(0L)
            val dur = p.duration.coerceAtLeast(0L)
            _uiState.update { state ->
                state.copy(
                    currentPositionMs = pos,
                    durationMs = if (dur > 0) dur else state.durationMs
                )
            }
        }
    }

    companion object {
        fun provideFactory(player: Player? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(player) as T
                }
            }
    }
}
