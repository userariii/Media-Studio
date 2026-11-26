package com.android.multimediastudio.ui.player

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
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
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.android.multimediastudio.PlaybackService
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerLayoutState(
    val isInPipMode: Boolean = false,
    val isExpanded: Boolean = false,
    val isLandscape: Boolean = false,
    val isFullScreen: Boolean = false,
    val currentMediaItem: MediaItem? = null,
    val isPlaying: Boolean = false
)

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

    val playerLayoutState: StateFlow<PlayerLayoutState> = _uiState
        .map {
            PlayerLayoutState(
                isInPipMode = it.isInPipMode,
                isExpanded = it.isExpanded,
                isLandscape = it.isLandscape,
                isFullScreen = it.isFullScreen,
                currentMediaItem = it.currentMediaItem,
                isPlaying = it.isPlaying
            )
        }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            PlayerLayoutState(
                isInPipMode = _uiState.value.isInPipMode,
                isExpanded = _uiState.value.isExpanded,
                isLandscape = _uiState.value.isLandscape,
                isFullScreen = _uiState.value.isFullScreen,
                currentMediaItem = _uiState.value.currentMediaItem,
                isPlaying = _uiState.value.isPlaying
            )
        )

    val activeMediaItemId: StateFlow<Long?> = _uiState
        .map { it.currentMediaItem?.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.currentMediaItem?.id)

    val isPlaybackActive: StateFlow<Boolean> = _uiState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.isPlaying)

    val mediaQueue: StateFlow<List<MediaItem>> = _uiState
        .map { it.mediaQueue }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.mediaQueue)

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

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            val rot = videoSize.unappliedRotationDegrees
            val effW = if (rot == 90 || rot == 270) videoSize.height else videoSize.width
            val effH = if (rot == 90 || rot == 270) videoSize.width else videoSize.height
            if (effW > 0 && effH > 0) {
                _uiState.update { it.copy(videoWidth = effW, videoHeight = effH) }
            }
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
                    if (_uiState.value.isInPipMode) {
                        _uiState.update {
                            it.copy(
                                isBuffering = false,
                                isPlaying = false,
                                hasEnded = true
                            )
                        }
                        stopPositionTracker()
                        return
                    }

                    val currentMode = _uiState.value.loopMode

                    when {
                        currentMode == LoopMode.ONE -> {
                            seekTo(0L)
                            play()
                        }
                        else -> {
                            playNext()
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

    @OptIn(UnstableApi::class)
    fun isPlayerAlive(p: Player? = player): Boolean {
        if (p == null) return false
        return if (p is ExoPlayer) {
            try {
                p.playbackLooper.thread.isAlive && p.applicationLooper.thread.isAlive
            } catch (_: Exception) {
                false
            }
        } else {
            true
        }
    }

    fun ensurePlayer(context: Context? = null): Player? {
        val current = player
        if (current != null && isPlayerAlive(current)) {
            return current
        }
        val ctx = context ?: appContext ?: return null
        return initializePlayer(ctx)
    }

    /**
     * Initializes the player with PlaybackService / ExoPlayer configured for
     * foreground playback and MediaSession controls.
     */
    fun initializePlayer(context: Context): Player {
        appContext = context.applicationContext
        val existing = player
        if (existing != null && isPlayerAlive(existing)) return existing
        if (existing != null) {
            try {
                existing.removeListener(playerListener)
            } catch (_: Exception) {}
            player = null
        }

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

        setupQueueNavigator()

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
            context.applicationContext.startService(intent)
        } catch (_: SecurityException) {
            // Security / permission exception starting service
        } catch (_: IllegalStateException) {
            // Catches background start restrictions if any
        } catch (_: Exception) {
            // General defensive exception catch so app never crashes
        }
    }

    private fun setupQueueNavigator() {
        PlaybackService.queueNavigator = object : PlaybackService.QueueNavigator {
            override fun onSkipNext() {
                playNext()
            }

            override fun onSkipPrevious() {
                playPrevious()
            }

            override fun canSkipNext(): Boolean {
                val queue = _uiState.value.mediaQueue
                val loopMode = _uiState.value.loopMode
                return queue.size > 1 || loopMode != LoopMode.OFF
            }

            override fun canSkipPrevious(): Boolean {
                val queue = _uiState.value.mediaQueue
                return queue.size > 1 || (_uiState.value.currentPositionMs > 3000L)
            }
        }
    }

    fun getPlayer(): Player? {
        val p = player
        if (p != null && isPlayerAlive(p)) {
            return p
        }
        return appContext?.let { initializePlayer(it) } ?: player
    }

    fun setMediaQueue(queue: List<MediaItem>) {
        _uiState.update { it.copy(mediaQueue = queue) }
        setupQueueNavigator()
    }

    fun playMedia(
        mediaItem: MediaItem,
        queue: List<MediaItem> = emptyList(),
        playedFromLandscape: Boolean? = null
    ) {
        setupQueueNavigator()
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

        val effectivePlayedFromLandscape = playedFromLandscape ?: _uiState.value.playedFromLandscape
        val wasLandscape = _uiState.value.isLandscape
        val isTargetVideo = mediaItem.mediaType == MediaType.VIDEO
        val isReel = isTargetVideo && mediaItem.isReelOrShort()
        val newLandscape = if (isReel) {
            false
        } else if (effectivePlayedFromLandscape) {
            true
        } else if (isTargetVideo) {
            wasLandscape
        } else {
            false
        }
        val newFullScreen = if (isTargetVideo && !isReel) _uiState.value.isFullScreen else false

        val parsedDims = mediaItem.resolution?.let { res ->
            if (res.contains("x")) {
                val parts = res.split("x")
                val w = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val h = parts.getOrNull(1)?.toIntOrNull() ?: 0
                if (w > 0 && h > 0) Pair(w, h) else null
            } else null
        }
        val initialW = parsedDims?.first ?: 0
        val initialH = parsedDims?.second ?: 0

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
                isExpanded = it.isExpanded,
                playedFromLandscape = effectivePlayedFromLandscape,
                videoSourceRect = null,
                videoWidth = initialW,
                videoHeight = initialH
            )
        }

        val p = ensurePlayer()
        if (p != null) {
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

                try {
                    p.setMediaItem(media3Item)
                    p.setPlaybackSpeed(_uiState.value.playbackSpeed)
                    p.prepare()
                    p.playWhenReady = true
                } catch (e: Exception) {
                    Log.w("PlayerViewModel", "Error in playMedia, recreating player", e)
                    appContext?.let { ctx ->
                        player = null
                        val freshPlayer = initializePlayer(ctx)
                        freshPlayer.setMediaItem(media3Item)
                        freshPlayer.setPlaybackSpeed(_uiState.value.playbackSpeed)
                        freshPlayer.prepare()
                        freshPlayer.playWhenReady = true
                    }
                }
            }
        }
    }

    private var isAppInForeground = true

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    private fun isScreenInteractive(): Boolean {
        screenInteractiveChecker?.let { return it.invoke() }
        val pm = appContext?.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isInteractive ?: true
    }

    private fun findNextMediaItem(
        queue: List<MediaItem>,
        currentIndex: Int,
        isShuffle: Boolean,
        loopMode: LoopMode,
        isBackground: Boolean,
        fromItem: MediaItem?
    ): MediaItem? {
        if (queue.isEmpty()) return null

        if (isBackground) {
            val fromType = fromItem?.mediaType ?: MediaType.AUDIO
            if (fromType == MediaType.VIDEO) {
                val nextCandidateIndex = if (isShuffle && queue.size > 1) {
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
                val candidate = queue[nextCandidateIndex]
                return if (candidate.mediaType == MediaType.VIDEO) candidate else null
            }

            val audioItems = queue.filter { it.mediaType == MediaType.AUDIO }
            if (audioItems.isEmpty()) return null

            if (isShuffle) {
                val currentId = fromItem?.id
                val candidates = if (audioItems.size > 1 && currentId != null) {
                    audioItems.filter { it.id != currentId }
                } else {
                    audioItems
                }
                return candidates.randomOrNull() ?: audioItems.randomOrNull()
            }

            if (currentIndex != -1) {
                for (i in (currentIndex + 1) until queue.size) {
                    if (queue[i].mediaType == MediaType.AUDIO) {
                        return queue[i]
                    }
                }
            }

            return when (loopMode) {
                LoopMode.ALL -> audioItems.firstOrNull()
                LoopMode.ONE -> fromItem?.takeIf { it.mediaType == MediaType.AUDIO } ?: audioItems.firstOrNull()
                LoopMode.OFF -> null
            }
        } else {
            val nextIndex = if (isShuffle && queue.size > 1) {
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
            return queue[nextIndex]
        }
    }

    private fun findPreviousMediaItem(
        queue: List<MediaItem>,
        currentIndex: Int,
        isShuffle: Boolean,
        loopMode: LoopMode,
        isBackground: Boolean,
        fromItem: MediaItem?
    ): MediaItem? {
        if (queue.isEmpty()) return null

        if (isBackground) {
            val fromType = fromItem?.mediaType ?: MediaType.AUDIO
            if (fromType == MediaType.VIDEO) {
                val prevCandidateIndex = if (isShuffle && queue.size > 1) {
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
                val candidate = queue[prevCandidateIndex]
                return if (candidate.mediaType == MediaType.VIDEO) candidate else null
            }

            val audioItems = queue.filter { it.mediaType == MediaType.AUDIO }
            if (audioItems.isEmpty()) return null

            if (isShuffle) {
                val currentId = fromItem?.id
                val candidates = if (audioItems.size > 1 && currentId != null) {
                    audioItems.filter { it.id != currentId }
                } else {
                    audioItems
                }
                return candidates.randomOrNull() ?: audioItems.randomOrNull()
            }

            if (currentIndex != -1) {
                for (i in (currentIndex - 1) downTo 0) {
                    if (queue[i].mediaType == MediaType.AUDIO) {
                        return queue[i]
                    }
                }
            }

            return when (loopMode) {
                LoopMode.ALL -> audioItems.lastOrNull()
                LoopMode.ONE -> fromItem?.takeIf { it.mediaType == MediaType.AUDIO } ?: audioItems.lastOrNull()
                LoopMode.OFF -> audioItems.lastOrNull()
            }
        } else {
            val prevIndex = if (isShuffle && queue.size > 1) {
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
            return queue[prevIndex]
        }
    }

    fun playNext() {
        val queue = _uiState.value.mediaQueue
        if (queue.isEmpty()) return
        val currentItem = _uiState.value.currentMediaItem
        val currentIndex = queue.indexOfFirst { it.id == currentItem?.id }

        val isBackground = (!isAppInForeground || !isScreenInteractive()) && !_uiState.value.isInPipMode

        val nextItem = findNextMediaItem(
            queue = queue,
            currentIndex = currentIndex,
            isShuffle = _uiState.value.isShuffleModeEnabled,
            loopMode = _uiState.value.loopMode,
            isBackground = isBackground,
            fromItem = currentItem
        )

        if (nextItem == null) {
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

        playMedia(
            mediaItem = nextItem,
            queue = queue,
            playedFromLandscape = _uiState.value.playedFromLandscape
        )
    }

    fun playPrevious() {
        val queue = _uiState.value.mediaQueue
        if (queue.isEmpty()) return
        val currentItem = _uiState.value.currentMediaItem
        val currentIndex = queue.indexOfFirst { it.id == currentItem?.id }

        val isBackground = (!isAppInForeground || !isScreenInteractive()) && !_uiState.value.isInPipMode

        val prevItem = findPreviousMediaItem(
            queue = queue,
            currentIndex = currentIndex,
            isShuffle = _uiState.value.isShuffleModeEnabled,
            loopMode = _uiState.value.loopMode,
            isBackground = isBackground,
            fromItem = currentItem
        )

        if (prevItem == null) {
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

        playMedia(
            mediaItem = prevItem,
            queue = queue,
            playedFromLandscape = _uiState.value.playedFromLandscape
        )
    }

    fun stopVideoPlaybackOnPipClose() {
        val currentItem = _uiState.value.currentMediaItem
        if (currentItem?.mediaType == MediaType.VIDEO) {
            pause()
            try {
                player?.stop()
                player?.clearMediaItems()
            } catch (_: Exception) {}
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isInPipMode = false,
                    isExpanded = false,
                    currentMediaItem = null,
                    currentPositionMs = 0L,
                    durationMs = 0L,
                    hasEnded = false,
                    errorMessage = null
                )
            }
        }
    }

    fun pauseOnPipClose() {
        val isVideo = _uiState.value.currentMediaItem?.mediaType == MediaType.VIDEO
        if (isVideo) {
            pause()
        }
        setInPipMode(false)
    }

    internal fun handlePipModeExited(
        isFinishing: Boolean,
        isDestroyed: Boolean,
        isAtLeastStarted: Boolean
    ): MediaItem? {
        val currentItem = _uiState.value.currentMediaItem
        val isVideo = currentItem?.mediaType == MediaType.VIDEO

        if (isFinishing || isDestroyed) {
            setInPipMode(false)
            if (isVideo) {
                stopVideoPlaybackOnPipClose()
            }
            return null
        }

        if (!isAtLeastStarted) {
            pauseOnPipClose()
            return null
        }

        setInPipMode(false)
        if (isVideo) {
            expandPlayer()
            return currentItem
        }
        return null
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
        val p = ensurePlayer()
        try {
            p?.play()
        } catch (e: Exception) {
            Log.w("PlayerViewModel", "Error in play, recreating player", e)
            appContext?.let { ctx ->
                player = null
                initializePlayer(ctx).play()
            }
        }
        _uiState.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        try {
            player?.pause()
        } catch (_: Exception) {}
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

    fun seekTo(positionMs: Long, showControls: Boolean = true) {
        if (showControls) {
            touchControls()
        }
        val duration = _uiState.value.durationMs
        val targetPos = if (duration > 0) {
            positionMs.coerceIn(0L, duration)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        val p = ensurePlayer()
        try {
            p?.seekTo(targetPos)
        } catch (e: Exception) {
            Log.w("PlayerViewModel", "Error in seekTo, recreating player", e)
            appContext?.let { ctx ->
                player = null
                val freshPlayer = initializePlayer(ctx)
                try {
                    freshPlayer.seekTo(targetPos)
                } catch (_: Exception) {}
            }
        }
        _uiState.update {
            it.copy(
                currentPositionMs = targetPos,
                interactionNonce = if (it.areControlsVisible) it.interactionNonce + 1 else it.interactionNonce
            )
        }
    }

    fun seekForward(deltaMs: Long = 10000L, showControls: Boolean = false) {
        val current = player?.currentPosition ?: _uiState.value.currentPositionMs
        seekTo(current + deltaMs, showControls = showControls)
    }

    fun seekRewind(deltaMs: Long = 10000L, showControls: Boolean = false) {
        val current = player?.currentPosition ?: _uiState.value.currentPositionMs
        seekTo(current - deltaMs, showControls = showControls)
    }

    fun setPlaybackSpeed(speed: Float, showControls: Boolean = true) {
        if (showControls) {
            touchControls()
        }
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
        if (_uiState.value.currentMediaItem?.isReelOrShort() == true) return
        if (_uiState.value.playedFromLandscape) return
        touchControls()
        _uiState.update { it.copy(isLandscape = !it.isLandscape) }
    }

    fun setOrientation(landscape: Boolean) {
        if (landscape && _uiState.value.currentMediaItem?.isReelOrShort() == true) return
        _uiState.update { it.copy(isLandscape = landscape) }
    }

    fun setPlayedFromLandscape(playedFromLandscape: Boolean) {
        _uiState.update { it.copy(playedFromLandscape = playedFromLandscape) }
    }

    fun setInPipMode(inPip: Boolean) {
        _uiState.update { state ->
            val isCompleted = state.hasEnded || (state.currentMediaItem != null && state.currentPositionMs >= state.durationMs && state.durationMs > 0)
            val isReelOrShort = state.currentMediaItem?.isReelOrShort() == true
            val shouldShowControlsOnExit = !inPip && (isCompleted || (isReelOrShort && !state.isPlaying))

            state.copy(
                isInPipMode = inPip,
                areControlsVisible = if (inPip) false else if (shouldShowControlsOnExit) true else state.areControlsVisible
            )
        }
    }

    fun updateVideoSourceRect(rect: Rect?) {
        if (_uiState.value.videoSourceRect != rect) {
            _uiState.update { it.copy(videoSourceRect = rect) }
        }
    }

    fun expandPlayer() {
        _uiState.update { state ->
            val isCompleted = state.hasEnded || (state.currentMediaItem != null && state.currentPositionMs >= state.durationMs && state.durationMs > 0)
            val isReelOrShort = state.currentMediaItem?.isReelOrShort() == true
            val shouldShowControls = isCompleted || (isReelOrShort && !state.isPlaying)

            state.copy(
                isExpanded = true,
                areControlsVisible = if (shouldShowControls) true else state.areControlsVisible
            )
        }
    }

    fun collapsePlayer() {
        _uiState.update {
            it.copy(
                isExpanded = false,
                isFullScreen = false,
                isLandscape = false,
                isScreenLocked = false
            )
        }
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
                durationMs = 0L,
                playedFromLandscape = false,
                videoSourceRect = null,
                videoWidth = 0,
                videoHeight = 0
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
        PlaybackService.queueNavigator = null
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
        val p = player ?: return
        if (!isPlayerAlive(p)) return
        try {
            val pos = p.currentPosition.coerceAtLeast(0L)
            val dur = p.duration.coerceAtLeast(0L)
            _uiState.update { state ->
                state.copy(
                    currentPositionMs = pos,
                    durationMs = if (dur > 0) dur else state.durationMs
                )
            }
        } catch (_: Exception) {}
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
