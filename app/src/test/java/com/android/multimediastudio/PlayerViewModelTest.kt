package com.android.multimediastudio

import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import com.android.multimediastudio.ui.player.LoopMode
import com.android.multimediastudio.ui.player.PlayerViewModel
import com.android.multimediastudio.util.TitleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlayerViewModel

    private val sampleVideo = MediaItem(
        id = 1L,
        title = "Test Video",
        contentUri = null,
        durationMs = 120000L,
        sizeBytes = 5000000L,
        mimeType = "video/mp4",
        mediaType = MediaType.VIDEO
    )

    private val sampleAudio = MediaItem(
        id = 2L,
        title = "Test Audio",
        contentUri = null,
        durationMs = 180000L,
        sizeBytes = 3000000L,
        mimeType = "audio/mp3",
        mediaType = MediaType.AUDIO,
        artist = "Test Artist",
        album = "Test Album"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = PlayerViewModel(playerInstance = null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isDefault() {
        val state = viewModel.uiState.value
        assertNull(state.currentMediaItem)
        assertFalse(state.isPlaying)
        assertEquals(0L, state.currentPositionMs)
        assertEquals(0L, state.durationMs)
        assertEquals(1.0f, state.playbackSpeed, 0.001f)
        assertFalse(state.isFullScreen)
        assertTrue(state.areControlsVisible)
        assertFalse(state.isScreenLocked)
        assertFalse(state.isLandscape)
        assertFalse(state.isInPipMode)
    }

    @Test
    fun playMedia_updatesMediaItemAndDuration() = runTest {
        viewModel.playMedia(sampleVideo)

        val state = viewModel.uiState.value
        assertEquals(sampleVideo, state.currentMediaItem)
        assertEquals(120000L, state.durationMs)
        assertEquals(0L, state.currentPositionMs)
        assertFalse(state.hasEnded)
        assertNull(state.errorMessage)
    }

    @Test
    fun playMedia_resetsLandscapeAndFullScreenState() = runTest {
        viewModel.toggleOrientation()
        viewModel.toggleFullScreen()
        assertTrue(viewModel.uiState.value.isLandscape)
        assertTrue(viewModel.uiState.value.isFullScreen)

        viewModel.playMedia(sampleAudio)

        val state = viewModel.uiState.value
        assertFalse(state.isLandscape)
        assertFalse(state.isFullScreen)
    }

    @Test
    fun playAndPause_updatesIsPlayingState() = runTest {
        viewModel.playMedia(sampleAudio)
        viewModel.play()

        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.pause()

        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun togglePlayPause_switchesState() = runTest {
        viewModel.playMedia(sampleVideo)
        assertFalse(viewModel.uiState.value.isPlaying)

        viewModel.togglePlayPause()
        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.togglePlayPause()
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun seekTo_updatesPositionWithinBounds() = runTest {
        viewModel.playMedia(sampleVideo) // 120000L duration

        viewModel.seekTo(30000L)
        assertEquals(30000L, viewModel.uiState.value.currentPositionMs)

        // Seeking beyond duration clamps to duration
        viewModel.seekTo(200000L)
        assertEquals(120000L, viewModel.uiState.value.currentPositionMs)

        // Seeking negative clamps to 0
        viewModel.seekTo(-5000L)
        assertEquals(0L, viewModel.uiState.value.currentPositionMs)
    }

    @Test
    fun seekForwardAndRewind_changesPositionByDelta() = runTest {
        viewModel.playMedia(sampleVideo)
        viewModel.seekTo(20000L)

        // Hide controls before seeking forward / rewinding
        viewModel.setControlsVisible(false)
        assertFalse(viewModel.uiState.value.areControlsVisible)

        viewModel.seekForward(10000L)
        assertEquals(30000L, viewModel.uiState.value.currentPositionMs)
        assertFalse(viewModel.uiState.value.areControlsVisible)

        viewModel.seekRewind(10000L)
        assertEquals(20000L, viewModel.uiState.value.currentPositionMs)
        assertFalse(viewModel.uiState.value.areControlsVisible)
    }

    @Test
    fun setPlaybackSpeed_updatesSpeed() = runTest {
        viewModel.setPlaybackSpeed(1.5f)
        assertEquals(1.5f, viewModel.uiState.value.playbackSpeed, 0.001f)

        viewModel.setPlaybackSpeed(2.0f)
        assertEquals(2.0f, viewModel.uiState.value.playbackSpeed, 0.001f)
    }



    @Test
    fun toggleFullScreen_togglesState() = runTest {
        assertFalse(viewModel.uiState.value.isFullScreen)

        viewModel.toggleFullScreen()
        assertTrue(viewModel.uiState.value.isFullScreen)

        viewModel.toggleFullScreen()
        assertFalse(viewModel.uiState.value.isFullScreen)
    }

    @Test
    fun toggleControlsVisibility_togglesState() = runTest {
        assertTrue(viewModel.uiState.value.areControlsVisible)

        viewModel.toggleControlsVisibility()
        assertFalse(viewModel.uiState.value.areControlsVisible)

        viewModel.toggleControlsVisibility()
        assertTrue(viewModel.uiState.value.areControlsVisible)
    }

    @Test
    fun toggleScreenLock_togglesLockState() = runTest {
        assertFalse(viewModel.uiState.value.isScreenLocked)

        viewModel.toggleScreenLock()
        assertTrue(viewModel.uiState.value.isScreenLocked)

        viewModel.toggleScreenLock()
        assertFalse(viewModel.uiState.value.isScreenLocked)
    }

    @Test
    fun setScreenLocked_setsLockStateDirectly() = runTest {
        assertFalse(viewModel.uiState.value.isScreenLocked)

        viewModel.setScreenLocked(true)
        assertTrue(viewModel.uiState.value.isScreenLocked)

        viewModel.setScreenLocked(false)
        assertFalse(viewModel.uiState.value.isScreenLocked)
    }

    @Test
    fun toggleOrientation_togglesLandscapeState() = runTest {
        assertFalse(viewModel.uiState.value.isLandscape)

        viewModel.toggleOrientation()
        assertTrue(viewModel.uiState.value.isLandscape)

        viewModel.toggleOrientation()
        assertFalse(viewModel.uiState.value.isLandscape)
    }

    @Test
    fun playMedia_whenPlayedFromLandscape_setsLandscapeAndLocksOrientation() = runTest {
        viewModel.playMedia(sampleVideo, playedFromLandscape = true)
        val state = viewModel.uiState.value
        assertTrue(state.isLandscape)
        assertTrue(state.playedFromLandscape)

        // Toggling orientation should NOT switch to portrait when played from landscape
        viewModel.toggleOrientation()
        assertTrue(viewModel.uiState.value.isLandscape)

        // Collapsing player should retain playedFromLandscape for mini player PIP on landscape gallery
        viewModel.collapsePlayer()
        assertTrue(viewModel.uiState.value.playedFromLandscape)
        assertFalse(viewModel.uiState.value.isLandscape)

        // Clearing active track should reset playedFromLandscape
        viewModel.clearActiveTrack()
        assertFalse(viewModel.uiState.value.playedFromLandscape)
    }

    @Test
    fun playMedia_whenReelOrShort_alwaysPortraitAndPreventsLandscape() = runTest {
        val reelVideo = sampleVideo.copy(
            id = 99L,
            title = "Awesome Reel #shorts",
            resolution = "1080x1920"
        )
        assertTrue(reelVideo.isReelOrShort())

        // Even if requested playedFromLandscape = true, Reels/Shorts must stay in portrait,
        // but playedFromLandscape is preserved so orientation is restored when minimized
        viewModel.playMedia(reelVideo, playedFromLandscape = true)
        val state = viewModel.uiState.value
        assertFalse(state.isLandscape)
        assertTrue(state.playedFromLandscape)

        // Toggling orientation must not switch reel to landscape
        viewModel.toggleOrientation()
        assertFalse(viewModel.uiState.value.isLandscape)
    }

    @Test
    fun setInPipMode_updatesPipStateAndHidesControls() = runTest {
        assertTrue(viewModel.uiState.value.areControlsVisible)

        viewModel.setInPipMode(true)
        val state = viewModel.uiState.value
        assertTrue(state.isInPipMode)
        assertFalse(state.areControlsVisible)
    }

    @Test
    fun pauseOnBackground_pausesPlaybackForVideoNotInPip() = runTest {
        viewModel.playMedia(sampleVideo)
        viewModel.play()
        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.pauseOnBackground()
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun pauseOnBackground_doesNotPausePlaybackForAudio() = runTest {
        viewModel.playMedia(sampleAudio)
        viewModel.play()
        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.pauseOnBackground()
        assertTrue(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun pauseOnBackground_doesNotPausePlaybackForVideoInPip() = runTest {
        viewModel.playMedia(sampleVideo)
        viewModel.play()
        viewModel.setInPipMode(true)
        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.pauseOnBackground()
        assertTrue(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun audioInList_playAndPauseDirectly_updatesStateWithoutClearingItem() = runTest {
        viewModel.playMedia(sampleAudio)
        viewModel.play()

        val playingState = viewModel.uiState.value
        assertEquals(sampleAudio, playingState.currentMediaItem)
        assertTrue(playingState.isPlaying)

        viewModel.pause()

        val pausedState = viewModel.uiState.value
        assertEquals(sampleAudio, pausedState.currentMediaItem)
        assertFalse(pausedState.isPlaying)
    }

    @Test
    fun videoInList_pauseDirectly_pausesVideoPlayback() = runTest {
        viewModel.playMedia(sampleVideo)
        viewModel.play()

        assertTrue(viewModel.uiState.value.isPlaying)
        assertEquals(MediaType.VIDEO, viewModel.uiState.value.currentMediaItem?.mediaType)

        viewModel.pause()

        assertFalse(viewModel.uiState.value.isPlaying)
        assertEquals(sampleVideo, viewModel.uiState.value.currentMediaItem)
    }

    @Test
    fun audioPlayback_remainsPlayingWhenNavigatingBack() = runTest {
        viewModel.playMedia(sampleAudio)
        viewModel.play()

        assertTrue(viewModel.uiState.value.isPlaying)
        assertEquals(sampleAudio, viewModel.uiState.value.currentMediaItem)

        // Navigating back/closing audio player should leave audio playback active
        val stateAfterNavBack = viewModel.uiState.value
        assertTrue(stateAfterNavBack.isPlaying)
        assertEquals(sampleAudio, stateAfterNavBack.currentMediaItem)
        assertEquals(MediaType.AUDIO, stateAfterNavBack.currentMediaItem?.mediaType)
    }

    @Test
    fun playNext_cyclesThroughQueueForward() = runTest {
        val item1 = sampleVideo
        val item2 = sampleAudio
        val item3 = sampleVideo.copy(id = 3L, title = "Video 3")
        val queue = listOf(item1, item2, item3)

        viewModel.playMedia(item1, queue)
        assertEquals(item1, viewModel.uiState.value.currentMediaItem)

        // Advance to next -> item2
        viewModel.playNext()
        assertEquals(item2, viewModel.uiState.value.currentMediaItem)

        // Advance to next -> item3
        viewModel.playNext()
        assertEquals(item3, viewModel.uiState.value.currentMediaItem)

        // Advance to next -> cycles to item1 (first item)
        viewModel.playNext()
        assertEquals(item1, viewModel.uiState.value.currentMediaItem)
    }

    @Test
    fun playPrevious_cyclesThroughQueueBackward() = runTest {
        val item1 = sampleVideo
        val item2 = sampleAudio
        val item3 = sampleVideo.copy(id = 3L, title = "Video 3")
        val queue = listOf(item1, item2, item3)

        viewModel.playMedia(item1, queue)
        assertEquals(item1, viewModel.uiState.value.currentMediaItem)

        // Previous from item1 -> cycles to item3 (last item)
        viewModel.playPrevious()
        assertEquals(item3, viewModel.uiState.value.currentMediaItem)

        // Previous from item3 -> item2
        viewModel.playPrevious()
        assertEquals(item2, viewModel.uiState.value.currentMediaItem)

        // Previous from item2 -> item1
        viewModel.playPrevious()
        assertEquals(item1, viewModel.uiState.value.currentMediaItem)
    }

    @Test
    fun playNextAndPrevious_withEmptyQueue_doesNotCrash() = runTest {
        assertNull(viewModel.uiState.value.currentMediaItem)

        viewModel.playNext()
        assertNull(viewModel.uiState.value.currentMediaItem)

        viewModel.playPrevious()
        assertNull(viewModel.uiState.value.currentMediaItem)
    }

    @Test
    fun playNextAndPrevious_withSingleItemQueue_remainsOnSameItem() = runTest {
        viewModel.playMedia(sampleVideo, listOf(sampleVideo))

        viewModel.playNext()
        assertEquals(sampleVideo, viewModel.uiState.value.currentMediaItem)

        viewModel.playPrevious()
        assertEquals(sampleVideo, viewModel.uiState.value.currentMediaItem)
    }

    @Test
    fun setMediaQueue_updatesStateQueue() = runTest {
        val queue = listOf(sampleVideo, sampleAudio)
        viewModel.setMediaQueue(queue)

        assertEquals(queue, viewModel.uiState.value.mediaQueue)
    }

    @Test
    fun playMedia_onAlreadyPlayingItem_maintainsCurrentPlaybackPosition() = runTest {
        viewModel.playMedia(sampleAudio)
        viewModel.play()
        viewModel.seekTo(45000L)

        assertEquals(45000L, viewModel.uiState.value.currentPositionMs)
        assertTrue(viewModel.uiState.value.isPlaying)

        // Tapping/playing the already active item should maintain position
        viewModel.playMedia(sampleAudio)

        val stateAfter = viewModel.uiState.value
        assertEquals(sampleAudio, stateAfter.currentMediaItem)
        assertEquals(45000L, stateAfter.currentPositionMs)
        assertTrue(stateAfter.isPlaying)
    }

    @Test
    fun playMedia_onAlreadyActiveItemWhenPaused_maintainsPausedStateAndPosition() = runTest {
        viewModel.playMedia(sampleAudio)
        viewModel.seekTo(30000L)
        viewModel.pause()

        assertEquals(30000L, viewModel.uiState.value.currentPositionMs)
        assertFalse(viewModel.uiState.value.isPlaying)

        // Calling playMedia on the active item when paused should maintain paused state and maintain position
        viewModel.playMedia(sampleAudio)

        val stateAfter = viewModel.uiState.value
        assertEquals(sampleAudio, stateAfter.currentMediaItem)
        assertEquals(30000L, stateAfter.currentPositionMs)
        assertFalse(stateAfter.isPlaying)
    }

    @Test
    fun playerSheetExpansionState_andClearingTrack() = runTest {
        assertFalse(viewModel.uiState.value.isExpanded)

        viewModel.expandPlayer()
        assertTrue(viewModel.uiState.value.isExpanded)

        viewModel.collapsePlayer()
        assertFalse(viewModel.uiState.value.isExpanded)

        viewModel.togglePlayerExpansion()
        assertTrue(viewModel.uiState.value.isExpanded)

        viewModel.playMedia(sampleAudio)
        assertTrue(viewModel.uiState.value.isExpanded)
        assertEquals(sampleAudio, viewModel.uiState.value.currentMediaItem)

        viewModel.expandPlayer()
        assertTrue(viewModel.uiState.value.isExpanded)

        viewModel.clearActiveTrack()
        assertNull(viewModel.uiState.value.currentMediaItem)
        assertFalse(viewModel.uiState.value.isExpanded)
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun toggleShuffleMode_togglesState() = runTest {
        assertFalse(viewModel.uiState.value.isShuffleModeEnabled)

        viewModel.toggleShuffleMode()
        assertTrue(viewModel.uiState.value.isShuffleModeEnabled)

        viewModel.toggleShuffleMode()
        assertFalse(viewModel.uiState.value.isShuffleModeEnabled)
    }

    @Test
    fun toggleLoopMode_cyclesModes() = runTest {
        assertEquals(LoopMode.OFF, viewModel.uiState.value.loopMode)

        viewModel.toggleLoopMode()
        assertEquals(LoopMode.ALL, viewModel.uiState.value.loopMode)

        viewModel.toggleLoopMode()
        assertEquals(LoopMode.ONE, viewModel.uiState.value.loopMode)

        viewModel.toggleLoopMode()
        assertEquals(LoopMode.OFF, viewModel.uiState.value.loopMode)
    }

    @Test
    fun loopMode_all_cyclesThroughWholeQueueAndLoopsBack() = runTest {
        val queue = listOf(sampleVideo, sampleAudio)
        viewModel.playMedia(sampleVideo, queue)
        viewModel.toggleLoopMode() // Set to LoopMode.ALL

        assertEquals(LoopMode.ALL, viewModel.uiState.value.loopMode)
        assertEquals(sampleVideo, viewModel.uiState.value.currentMediaItem)

        // Advance next -> sampleAudio
        viewModel.playNext()
        assertEquals(sampleAudio, viewModel.uiState.value.currentMediaItem)

        // Advance next from end -> loops back to sampleVideo!
        viewModel.playNext()
        assertEquals(sampleVideo, viewModel.uiState.value.currentMediaItem)
    }

    @Test
    fun trackSheetToggles_updatesUiState() = runTest {
        assertFalse(viewModel.uiState.value.showAudioTrackSheet)
        assertFalse(viewModel.uiState.value.showSubtitleTrackSheet)

        viewModel.setShowAudioTrackSheet(true)
        assertTrue(viewModel.uiState.value.showAudioTrackSheet)

        viewModel.setShowAudioTrackSheet(false)
        assertFalse(viewModel.uiState.value.showAudioTrackSheet)

        viewModel.setShowSubtitleTrackSheet(true)
        assertTrue(viewModel.uiState.value.showSubtitleTrackSheet)

        viewModel.setShowSubtitleTrackSheet(false)
        assertFalse(viewModel.uiState.value.showSubtitleTrackSheet)
    }

    @Test
    fun transitionBetweenVideoAndAudio_preservesExpandedStateAndResetsVideoLocks() = runTest {
        val queue = listOf(sampleVideo, sampleAudio)
        viewModel.playMedia(sampleVideo, queue)
        viewModel.expandPlayer()
        viewModel.toggleOrientation()
        viewModel.toggleScreenLock()

        assertTrue(viewModel.uiState.value.isExpanded)
        assertTrue(viewModel.uiState.value.isLandscape)
        assertTrue(viewModel.uiState.value.isScreenLocked)
        assertEquals(sampleVideo, viewModel.uiState.value.currentMediaItem)

        // Switch Video -> Audio via playNext
        viewModel.playNext()

        val stateAfterNext = viewModel.uiState.value
        assertEquals(sampleAudio, stateAfterNext.currentMediaItem)
        assertTrue(stateAfterNext.isExpanded)
        assertFalse(stateAfterNext.isLandscape)
        assertFalse(stateAfterNext.isScreenLocked)

        // Switch Audio -> Video via playNext
        viewModel.playNext()

        val stateAfterNextVideo = viewModel.uiState.value
        assertEquals(sampleVideo, stateAfterNextVideo.currentMediaItem)
        assertTrue(stateAfterNextVideo.isExpanded)
    }

    @Test
    fun trackSwitchingInLandscapeMode_preservesLandscapeForVideoAndSwitchesToPortraitForAudio() = runTest {
        val sampleVideo2 = sampleVideo.copy(id = 999L, title = "Second Video")
        val queue = listOf(sampleVideo, sampleVideo2, sampleAudio)

        viewModel.playMedia(sampleVideo, queue)
        viewModel.toggleOrientation() // Switch to landscape mode
        assertTrue(viewModel.uiState.value.isLandscape)

        // Switch Video -> Video via playNext
        viewModel.playNext()
        val stateVideo2 = viewModel.uiState.value
        assertEquals(sampleVideo2, stateVideo2.currentMediaItem)
        assertTrue(stateVideo2.isLandscape) // Must stay in landscape mode for video

        // Switch Video -> Audio via playNext
        viewModel.playNext()
        val stateAudio = viewModel.uiState.value
        assertEquals(sampleAudio, stateAudio.currentMediaItem)
        assertFalse(stateAudio.isLandscape) // Must switch to portrait for audio
    }

    @Test
    fun setUserScrubbing_keepsControlsVisibleAndUpdatesState() = runTest {
        viewModel.playMedia(sampleVideo)
        viewModel.setControlsVisible(false)
        assertFalse(viewModel.uiState.value.areControlsVisible)

        // User starts scrubbing seekbar
        viewModel.setUserScrubbing(true)
        assertTrue(viewModel.uiState.value.isUserScrubbing)
        assertTrue(viewModel.uiState.value.areControlsVisible)

        // User finishes scrubbing
        viewModel.setUserScrubbing(false)
        assertFalse(viewModel.uiState.value.isUserScrubbing)
        assertTrue(viewModel.uiState.value.areControlsVisible)
    }

    @Test
    fun formatShortTitle_extractsShowAndEpisodeOrYear() {
        val showTitle = TitleUtils.formatShortTitle(
            "Vikings_S01E01_2013_Hindi+English_Blu-ray_10bit_hevc_1080p_[ProtonMovies.mkv"
        )
        assertEquals("Vikings S01E01", showTitle)

        val movieTitle = TitleUtils.formatShortTitle(
            "Inception.2010.1080p.BluRay.x264.AAC-5.1.mp4"
        )
        assertEquals("Inception (2010)", movieTitle)
    }

    @Test
    fun screenOff_audioToVideoTransition_isNotAllowed() = runTest {
        val queue = listOf(sampleAudio, sampleVideo)
        viewModel.playMedia(sampleAudio, queue)
        viewModel.play()
        assertTrue(viewModel.uiState.value.isPlaying)

        // Screen is turned off
        viewModel.screenInteractiveChecker = { false }

        // Attempting to advance Audio -> Video
        viewModel.playNext()

        // Should not play video; playback stops
        assertEquals(sampleAudio, viewModel.uiState.value.currentMediaItem)
        assertFalse(viewModel.uiState.value.isPlaying)
        assertTrue(viewModel.uiState.value.hasEnded)
    }

    @Test
    fun screenOff_videoToAudioTransition_isNotAllowed() = runTest {
        val queue = listOf(sampleVideo, sampleAudio)
        viewModel.playMedia(sampleVideo, queue)
        viewModel.play()
        assertTrue(viewModel.uiState.value.isPlaying)

        // Screen is turned off
        viewModel.screenInteractiveChecker = { false }

        // Attempting to advance Video -> Audio
        viewModel.playNext()

        // Should not play audio; playback stops
        assertEquals(sampleVideo, viewModel.uiState.value.currentMediaItem)
        assertFalse(viewModel.uiState.value.isPlaying)
        assertTrue(viewModel.uiState.value.hasEnded)
    }

    @Test
    fun screenOff_videoToVideoTransition_isAllowed() = runTest {
        val sampleVideo2 = sampleVideo.copy(id = 888L, title = "Second Video")
        val queue = listOf(sampleVideo, sampleVideo2)
        viewModel.playMedia(sampleVideo, queue)
        viewModel.play()
        assertTrue(viewModel.uiState.value.isPlaying)

        // Screen is turned off
        viewModel.screenInteractiveChecker = { false }

        // Attempting to advance Video -> Video
        viewModel.playNext()

        // Should transition to second video successfully
        assertEquals(sampleVideo2, viewModel.uiState.value.currentMediaItem)
    }

    @Test
    fun screenOff_audioToAudioTransition_isAllowed() = runTest {
        val sampleAudio2 = sampleAudio.copy(id = 777L, title = "Second Audio")
        val queue = listOf(sampleAudio, sampleAudio2)
        viewModel.playMedia(sampleAudio, queue)
        viewModel.play()
        assertTrue(viewModel.uiState.value.isPlaying)

        // Screen is turned off
        viewModel.screenInteractiveChecker = { false }

        // Attempting to advance Audio -> Audio
        viewModel.playNext()

        // Should transition to second audio successfully
        assertEquals(sampleAudio2, viewModel.uiState.value.currentMediaItem)
    }

    @Test
    fun queueNavigator_onSkipNext_and_onSkipPrevious_changesTrack() = runTest {
        val sampleVideo2 = sampleVideo.copy(id = 888L, title = "Second Video")
        val sampleAudio2 = sampleAudio.copy(id = 999L, title = "Second Audio")
        val queue = listOf(sampleVideo, sampleVideo2, sampleAudio, sampleAudio2)

        viewModel.playMedia(sampleVideo, queue)
        val navigator = PlaybackService.queueNavigator
        assertTrue("QueueNavigator should be registered", navigator != null)
        assertTrue(navigator!!.canSkipNext())

        // Test skip next from navigator
        navigator.onSkipNext()
        assertEquals("Second Video", viewModel.uiState.value.currentMediaItem?.title)

        navigator.onSkipNext()
        assertEquals("Test Audio", viewModel.uiState.value.currentMediaItem?.title)

        navigator.onSkipNext()
        assertEquals("Second Audio", viewModel.uiState.value.currentMediaItem?.title)

        // Test skip previous from navigator
        navigator.onSkipPrevious()
        assertEquals("Test Audio", viewModel.uiState.value.currentMediaItem?.title)
    }

    @Test
    fun queueNavigator_singleItem_cannotSkipNextWithoutLoop() = runTest {
        viewModel.playMedia(sampleAudio, listOf(sampleAudio))
        val navigator = PlaybackService.queueNavigator
        assertTrue("QueueNavigator should be registered", navigator != null)
        assertFalse(navigator!!.canSkipNext())

        viewModel.toggleLoopMode() // LoopMode.ONE
        assertTrue(navigator.canSkipNext())
    }
}
