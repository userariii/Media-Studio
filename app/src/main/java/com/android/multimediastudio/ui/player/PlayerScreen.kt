package com.android.multimediastudio.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import com.android.multimediastudio.util.ImmersiveUtils
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        (currentContext as? Activity)?.let { return it }
        currentContext = currentContext.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    mediaItem: MediaItem,
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    mediaQueue: List<MediaItem> = emptyList(),
    onNext: () -> Unit = { viewModel.playNext() },
    onPrevious: () -> Unit = { viewModel.playPrevious() },
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val currentMediaItem = uiState.currentMediaItem ?: mediaItem

    LaunchedEffect(mediaItem, mediaQueue) {
        viewModel.initializePlayer(context)
        if (mediaQueue.isNotEmpty()) {
            viewModel.setMediaQueue(mediaQueue)
        }
        if (uiState.currentMediaItem?.id != mediaItem.id) {
            viewModel.playMedia(mediaItem, mediaQueue)
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val isLightSurface = androidx.core.graphics.ColorUtils.calculateLuminance(surfaceColor) > 0.5
    val isDarkTheme = !isLightSurface

    // Keep screen awake while watching video
    DisposableEffect(currentMediaItem.mediaType, uiState.isPlaying, activity) {
        val window = activity?.window
        val isVideo = currentMediaItem.mediaType == MediaType.VIDEO
        val shouldKeepScreenOn = isVideo && uiState.isPlaying
        if (shouldKeepScreenOn && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (isVideo && window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Auto hide controls / unlock overlay when visible (Video only)
    LaunchedEffect(
        uiState.areControlsVisible,
        uiState.isPlaying,
        currentMediaItem.mediaType,
        uiState.isScreenLocked,
        uiState.isUserScrubbing,
        uiState.showAudioTrackSheet,
        uiState.showSubtitleTrackSheet,
        uiState.showSpeedMenu,
        uiState.interactionNonce
    ) {
        if ((currentMediaItem.mediaType == MediaType.VIDEO) && uiState.areControlsVisible && uiState.isPlaying && !uiState.isUserScrubbing && !uiState.showAudioTrackSheet && !uiState.showSubtitleTrackSheet && !uiState.showSpeedMenu) {
            delay(5.seconds)
            if (!viewModel.uiState.value.isUserScrubbing && viewModel.uiState.value.isPlaying && !viewModel.uiState.value.showAudioTrackSheet && !viewModel.uiState.value.showSubtitleTrackSheet && !viewModel.uiState.value.showSpeedMenu) {
                viewModel.setControlsVisible(visible = false)
            }
        }
    }

    // Handle landscape orientation toggling and immersive fullscreen mode
    DisposableEffect(uiState.isLandscape, activity, isDarkTheme) {
        val currentActivity = activity
        if (currentActivity != null) {
            if (uiState.isLandscape) {
                currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ImmersiveUtils.setImmersiveFullscreen(currentActivity, enabled = true, isDarkTheme = isDarkTheme)
            } else {
                currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {
            if (currentActivity != null) {
                currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                ImmersiveUtils.setImmersiveFullscreen(currentActivity, enabled = false, isDarkTheme = isDarkTheme)
            }
        }
    }

    // PiP Mode listener & orientation/immersive reset on dispose
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            viewModel.setInPipMode(info.isInPictureInPictureMode)
        }
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && (activity is ComponentActivity)) {
            activity.addOnPictureInPictureModeChangedListener(listener)
        }
        onDispose {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && (activity is ComponentActivity)) {
                activity.removeOnPictureInPictureModeChangedListener(listener)
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ImmersiveUtils.setImmersiveFullscreen(activity, enabled = false)
        }
    }

    // Lifecycle observer to pause playback when activity/screen goes to background,
    // and re-apply immersive mode on resume
    DisposableEffect(lifecycleOwner, currentMediaItem, uiState.isLandscape, activity) {
        val observer = LifecycleEventObserver { _, event ->
            val currentActivity = activity
            if (event == Lifecycle.Event.ON_RESUME) {
                if (currentActivity != null && uiState.isLandscape) {
                    ImmersiveUtils.setImmersiveFullscreen(currentActivity, enabled = true)
                }
            } else if ((event == Lifecycle.Event.ON_PAUSE) || (event == Lifecycle.Event.ON_STOP)) {
                val isAudio = currentMediaItem.mediaType == MediaType.AUDIO
                val isInPip = (context.findActivity()?.isInPictureInPictureMode == true) || viewModel.uiState.value.isInPipMode
                if (!isAudio && !isInPip) {
                    viewModel.pauseOnBackground()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val handleBack: () -> Unit = onClose

    val isVideo = currentMediaItem.mediaType == MediaType.VIDEO

    androidx.compose.runtime.SideEffect {
        val window = activity?.window
        if (window != null) {
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            // Force status/nav bar icons to be white (light) during video playback since the video background is dark
            windowInsetsController.isAppearanceLightStatusBars = if (isVideo) false else isLightSurface
            windowInsetsController.isAppearanceLightNavigationBars = if (isVideo) false else isLightSurface
        }
    }

    DisposableEffect(activity) {
        onDispose {
            val window = activity?.window
            if (window != null) {
                val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                // When leaving, restore based on the current system theme (to avoid capturing a stale isLightSurface)
                val isCurrentlyDark = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                windowInsetsController.isAppearanceLightStatusBars = !isCurrentlyDark
                windowInsetsController.isAppearanceLightNavigationBars = !isCurrentlyDark
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isVideo && uiState.isLandscape) Color.Black else Color.Transparent),
    ) {
        if (currentMediaItem.mediaType == MediaType.VIDEO) {
            VideoPlayerContent(
                uiState = uiState,
                player = viewModel.getPlayer(),
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onSeek = { pos -> viewModel.seekTo(pos) },
                onRewind = { viewModel.seekRewind() },
                onForward = { viewModel.seekForward() },
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = { viewModel.toggleShuffleMode() },
                onToggleLoop = { viewModel.toggleLoopMode() },
                onSpeedSelected = { speed -> viewModel.setPlaybackSpeed(speed) },
                onSpeedMenuExpandedChanged = { expanded -> viewModel.setShowSpeedMenu(expanded) },
                onOpenAudioTracks = { viewModel.setShowAudioTrackSheet(true) },
                onOpenSubtitleTracks = { viewModel.setShowSubtitleTrackSheet(true) },
                onBack = handleBack,
                onOverlayClick = { viewModel.toggleControlsVisibility() },
                onToggleScreenLock = { viewModel.toggleScreenLock() },
                onToggleOrientation = { viewModel.toggleOrientation() },
                onScrubbingChanged = { isScrubbing -> viewModel.setUserScrubbing(isScrubbing) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AudioPlayerContent(
                mediaItem = currentMediaItem,
                uiState = uiState,
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onSeek = { pos -> viewModel.seekTo(pos) },
                onRewind = { viewModel.seekRewind() },
                onForward = { viewModel.seekForward() },
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = { viewModel.toggleShuffleMode() },
                onToggleLoop = { viewModel.toggleLoopMode() },
                onSpeedSelected = { speed -> viewModel.setPlaybackSpeed(speed) },
                onBack = onClose,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (uiState.showAudioTrackSheet) {
            AudioTrackSelectionSheet(
                audioTracks = uiState.audioTracks,
                selectedTrackId = uiState.selectedAudioTrackId,
                mediaTitle = currentMediaItem.title,
                onSelectTrack = { track -> viewModel.selectAudioTrack(track) },
                onDismiss = { viewModel.setShowAudioTrackSheet(false) }
            )
        }

        if (uiState.showSubtitleTrackSheet) {
            SubtitleTrackSelectionSheet(
                subtitleTracks = uiState.subtitleTracks,
                selectedTrackId = uiState.selectedSubtitleTrackId,
                mediaTitle = currentMediaItem.title,
                onSelectTrack = { track -> viewModel.selectSubtitleTrack(track) },
                onExternalSubtitleSelected = { uri -> viewModel.addExternalSubtitle(context, uri) },
                onDismiss = { viewModel.setShowSubtitleTrackSheet(false) }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
