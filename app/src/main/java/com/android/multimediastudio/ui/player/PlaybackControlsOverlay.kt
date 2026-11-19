package com.android.multimediastudio.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Forward10
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import com.android.multimediastudio.ui.components.AnimatedWaveformIcon
import com.android.multimediastudio.ui.components.GlowingSeekbarThumb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.android.multimediastudio.util.TitleUtils
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerContent(
    uiState: PlayerUiState,
    player: Player?,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleLoop: () -> Unit = {},
    onSpeedSelected: (Float) -> Unit = {},
    onSpeedMenuExpandedChanged: (Boolean) -> Unit = {},
    onOpenAudioTracks: () -> Unit = {},
    onOpenSubtitleTracks: () -> Unit = {},
    onBack: () -> Unit,
    onOverlayClick: () -> Unit,
    onToggleScreenLock: () -> Unit,
    onToggleOrientation: () -> Unit,
    onScrubbingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (uiState.isInPipMode) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            VideoPlayerSurface(
                player = player,
                onSurfaceClick = { },
                onRewind = { },
                onForward = { },
                isScreenLocked = false,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val density = LocalDensity.current
    val collapseThresholdPx = with(density) { 120.dp.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var containerHeight by remember { mutableFloatStateOf(1500f) }
    var velocityTracker by remember { mutableStateOf(VelocityTracker()) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    var brightnessValue by remember {
        val currentWinBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        val initialVal = if (currentWinBrightness >= 0f) {
            currentWinBrightness
        } else {
            try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
            } catch (_: Exception) {
                0.5f
            }
        }
        mutableFloatStateOf(initialVal.coerceIn(0.01f, 1.0f))
    }

    var volumeValue by remember {
        val currentVol = try {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        } catch (_: Exception) {
            (maxVolume / 2)
        }
        mutableFloatStateOf((currentVol.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f))
    }

    var isBrightnessVisible by remember { mutableStateOf(false) }
    var isVolumeVisible by remember { mutableStateOf(false) }
    var lastGestureTime by remember { mutableLongStateOf(0L) }

    var lastAppliedVol by remember { mutableIntStateOf(-1) }
    var lastAppliedBrightness by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(isBrightnessVisible, isVolumeVisible, lastGestureTime) {
        if (isBrightnessVisible || isVolumeVisible) {
            delay(1500)
            isBrightnessVisible = false
            isVolumeVisible = false
        }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.let { act ->
                act.window?.attributes = act.window.attributes?.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    var isUserScrubbing by remember { mutableStateOf(false) }
    var userScrubPositionMs by remember { mutableFloatStateOf(0f) }

    var externalSeekSide by remember { mutableStateOf(SeekSide.NONE) }
    var externalSeekTrigger by remember { mutableLongStateOf(0L) }
    var overlayWidth by remember { mutableIntStateOf(0) }

    val displayPositionMs = if (isUserScrubbing) userScrubPositionMs.toLong() else uiState.currentPositionMs
    val currentPosText = PlayerUiState.formatTime(displayPositionMs)
    val durationText = PlayerUiState.formatTime(uiState.durationMs)
    val shortTitle = TitleUtils.formatShortTitle(uiState.currentMediaItem?.title)

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val currentZoomScale by rememberUpdatedState(zoomScale)

    var overlaySize by remember { mutableStateOf(IntSize.Zero) }

    var zoomBadgeText by remember { mutableStateOf("") }
    var isZoomIndicatorVisible by remember { mutableStateOf(false) }
    var zoomBadgeTrigger by remember { mutableIntStateOf(0) }

    var isSpeedBoostActive by remember { mutableStateOf(false) }

    val currentOnSpeedSelected by rememberUpdatedState(onSpeedSelected)
    val currentPlaybackSpeed by rememberUpdatedState(uiState.playbackSpeed)
    val haptic = LocalHapticFeedback.current

    val showZoomBadge: (String) -> Unit = { text ->
        zoomBadgeText = text
        zoomBadgeTrigger++
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(zoomBadgeTrigger) {
        if (zoomBadgeTrigger > 0 && zoomBadgeText.isNotEmpty()) {
            isZoomIndicatorVisible = true
            delay(1200)
            isZoomIndicatorVisible = false
        }
    }

    LaunchedEffect(uiState.currentMediaItem?.id, uiState.isLandscape) {
        zoomScale = 1.0f
        panOffset = Offset.Zero
        zoomBadgeText = ""
        isZoomIndicatorVisible = false
    }

    // Video aspect ratio & fill scale calculation for landscape
    val videoWidth = player?.videoSize?.width ?: 0
    val videoHeight = player?.videoSize?.height ?: 0
    val videoRotation = player?.videoSize?.unappliedRotationDegrees ?: 0

    val effectiveVideoWidth = if (videoRotation == 90 || videoRotation == 270) videoHeight.toFloat() else videoWidth.toFloat()
    val effectiveVideoHeight = if (videoRotation == 90 || videoRotation == 270) videoWidth.toFloat() else videoHeight.toFloat()

    val fillScale = remember(effectiveVideoWidth, effectiveVideoHeight, overlaySize) {
        val vw = effectiveVideoWidth
        val vh = effectiveVideoHeight
        val pw = overlaySize.width.toFloat()
        val ph = overlaySize.height.toFloat()

        if (vw > 0f && vh > 0f && pw > 0f && ph > 0f) {
            val viewportAspect = pw / ph
            val videoAspect = vw / vh
            val rawScale = if (viewportAspect > videoAspect) {
                viewportAspect / videoAspect
            } else {
                videoAspect / viewportAspect
            }
            if (rawScale.isNaN() || rawScale < 1.01f) 1.0f else rawScale
        } else {
            1.0f
        }
    }

    val isPortraitFromMetadata = if (effectiveVideoWidth <= 0f || effectiveVideoHeight <= 0f) {
        val res = uiState.currentMediaItem?.resolution ?: ""
        if (res.contains("x")) {
            val parts = res.split("x")
            val w = parts.getOrNull(0)?.toFloatOrNull() ?: 0f
            val h = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
            h > w && w > 0f
        } else {
            false
        }
    } else {
        effectiveVideoHeight > effectiveVideoWidth
    }

    val isPortraitVideo = isPortraitFromMetadata || (effectiveVideoWidth > 0f && effectiveVideoHeight > 0f && effectiveVideoHeight > effectiveVideoWidth)

    val calculatedAspect = if (effectiveVideoWidth > 0f && effectiveVideoHeight > 0f) {
        effectiveVideoWidth / effectiveVideoHeight
    } else {
        val res = uiState.currentMediaItem?.resolution ?: ""
        if (res.contains("x")) {
            val parts = res.split("x")
            val w = parts.getOrNull(0)?.toFloatOrNull() ?: 0f
            val h = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
            if (w > 0f && h > 0f) w / h else 16f / 9f
        } else {
            16f / 9f
        }
    }

    val portraitAspectRatio = if (isPortraitVideo) {
        calculatedAspect.coerceIn(0.52f, 0.85f)
    } else {
        16f / 9f
    }

    if (uiState.isLandscape) {
        // LANDSCAPE MODE (Full screen video)
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged {
                    overlayWidth = it.width
                    overlaySize = it
                }
                .pointerInput(uiState.isScreenLocked) {
                    if (uiState.isScreenLocked) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var isHolding = false
                        var savedSpeed = currentPlaybackSpeed

                        val holdJob = coroutineScope.launch {
                            delay(400)
                            savedSpeed = currentPlaybackSpeed
                            isHolding = true
                            currentOnSpeedSelected(2.0f)
                            isSpeedBoostActive = true
                            try {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } catch (_: Exception) {
                            }
                        }

                        try {
                            val touchSlop = viewConfiguration.touchSlop
                            var totalPan = Offset.Zero

                            do {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val panChange = event.calculatePan()
                                totalPan += panChange

                                if (!isHolding && (totalPan.getDistance() > touchSlop || event.changes.size > 1)) {
                                    holdJob.cancel()
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            holdJob.cancel()
                            if (isHolding) {
                                isSpeedBoostActive = false
                                currentOnSpeedSelected(savedSpeed)
                                try {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
                .pointerInput(uiState.isScreenLocked) {
                    detectTapGestures(
                        onTap = {
                            onOverlayClick()
                        },
                        onDoubleTap = { offset ->
                            if (uiState.isScreenLocked) return@detectTapGestures
                            val width = if (overlayWidth > 0) overlayWidth else size.width
                            val isLeft = offset.x < width / 2f
                            if (isLeft) {
                                externalSeekSide = SeekSide.LEFT
                                externalSeekTrigger = System.currentTimeMillis()
                            } else {
                                externalSeekSide = SeekSide.RIGHT
                                externalSeekTrigger = System.currentTimeMillis()
                            }
                        }
                    )
                }
                .pointerInput(uiState.isScreenLocked) {
                    if (uiState.isScreenLocked) return@pointerInput
                    var isDraggingLeft = false
                    var isDraggingRight = false

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val width = if (overlayWidth > 0) overlayWidth.toFloat() else size.width.toFloat()
                            isDraggingLeft = offset.x < width * 0.4f
                            isDraggingRight = offset.x > width * 0.6f

                            if (isDraggingLeft) {
                                isBrightnessVisible = true
                                isVolumeVisible = false
                                lastGestureTime = System.currentTimeMillis()
                            } else if (isDraggingRight) {
                                val currentVol = try {
                                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                } catch (_: Exception) {
                                    (volumeValue * maxVolume).roundToInt()
                                }
                                lastAppliedVol = currentVol
                                volumeValue = (currentVol.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f)
                                isVolumeVisible = true
                                isBrightnessVisible = false
                                lastGestureTime = System.currentTimeMillis()
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (isDraggingLeft || isDraggingRight) {
                                change.consume()
                                val sensitivity = 0.0028f
                                val delta = -dragAmount * sensitivity

                                if (isDraggingLeft) {
                                    brightnessValue = (brightnessValue + delta).coerceIn(0.01f, 1.0f)
                                    if (abs(brightnessValue - lastAppliedBrightness) >= 0.005f) {
                                        lastAppliedBrightness = brightnessValue
                                        activity?.let { act ->
                                            act.window?.attributes = act.window.attributes?.apply {
                                                screenBrightness = brightnessValue
                                            }
                                        }
                                    }
                                    isBrightnessVisible = true
                                    lastGestureTime = System.currentTimeMillis()
                                } else if (isDraggingRight) {
                                    volumeValue = (volumeValue + delta).coerceIn(0f, 1f)
                                    val targetVol = (volumeValue * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                    if (targetVol != lastAppliedVol) {
                                        lastAppliedVol = targetVol
                                        try {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                        } catch (_: Exception) {
                                        }
                                    }
                                    isVolumeVisible = true
                                    lastGestureTime = System.currentTimeMillis()
                                }
                            }
                        },
                        onDragEnd = {
                            isDraggingLeft = false
                            isDraggingRight = false
                            lastGestureTime = System.currentTimeMillis()
                        },
                        onDragCancel = {
                            isDraggingLeft = false
                            isDraggingRight = false
                            lastGestureTime = System.currentTimeMillis()
                        }
                    )
                }
                .pointerInput(uiState.isScreenLocked, fillScale) {
                    if (uiState.isScreenLocked) return@pointerInput
                    detectLandscapeVideoTransformGestures(
                        currentZoomScale = { currentZoomScale },
                        fillScale = { fillScale },
                        onGestureUpdate = { newScale, _, badgeText ->
                            zoomScale = newScale
                            panOffset = Offset.Zero
                            if (badgeText != null) {
                                showZoomBadge(badgeText)
                            }
                        }
                    )
                }
        ) {
            VideoPlayerSurface(
                player = player,
                onSurfaceClick = onOverlayClick,
                onRewind = onRewind,
                onForward = onForward,
                isScreenLocked = uiState.isScreenLocked,
                enableGestures = false,
                externalZoomScale = zoomScale,
                externalPanOffset = Offset.Zero,
                fillScale = fillScale,
                onZoomChanged = null,
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = uiState.areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                if (uiState.isScreenLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onOverlayClick
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Surface(
                            onClick = onToggleScreenLock,
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 16.dp, end = 16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Unlock Screen",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tap to Unlock",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.75f),
                                        Color.Black.copy(alpha = 0.2f),
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        // TOP BAR
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Minimize Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = shortTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Top-Right Action Buttons: Lock/Unlock (Frameless)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {

                                val isLockActive = uiState.isScreenLocked
                                IconButton(onClick = onToggleScreenLock) {
                                    Icon(
                                        imageVector = if (isLockActive) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                        contentDescription = if (isLockActive) "Unlock Screen" else "Lock Screen",
                                        tint = if (isLockActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // CENTER CONTROLS (Previous | Play/Pause | Next)
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth(0.6f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onPrevious,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Track",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Surface(
                                onClick = onPlayPauseToggle,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (uiState.isBuffering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(36.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 3.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = onNext,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Track",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        // BOTTOM CONTROLS (SEEKBAR, TIME LABELS & EXIT LANDSCAPE BUTTON)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(start = 56.dp, end = 56.dp, bottom = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp)
                                    .offset(y = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentPosText,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = Color.White
                                    )
                                    Text(
                                        text = " / ",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = durationText,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }

                                IconButton(
                                    onClick = onToggleOrientation,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FullscreenExit,
                                        contentDescription = "Exit Landscape",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }

                            Slider(
                                value = if (isUserScrubbing) userScrubPositionMs else uiState.currentPositionMs.toFloat(),
                                onValueChange = { newValue ->
                                    if (!isUserScrubbing) {
                                        isUserScrubbing = true
                                        onScrubbingChanged(true)
                                    }
                                    userScrubPositionMs = newValue
                                },
                                onValueChangeFinished = {
                                    isUserScrubbing = false
                                    onScrubbingChanged(false)
                                    onSeek(userScrubPositionMs.toLong())
                                },
                                valueRange = 0f..(uiState.durationMs.toFloat().coerceAtLeast(1f)),
                                track = { sliderState ->
                                    SliderDefaults.Track(
                                        sliderState = sliderState,
                                        modifier = Modifier.height(6.dp),
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                                        ),
                                        drawStopIndicator = null
                                    )
                                },
                                thumb = {
                                    GlowingSeekbarThumb(isScrubbing = isUserScrubbing)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                            
                            // NEW THIRD ROW: Audio | Subtitles | Speed
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = (-8).dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isAudioActive = uiState.selectedAudioTrackId != null
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenAudioTracks() }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Audiotrack,
                                        contentDescription = "Audio Track",
                                        tint = if (isAudioActive) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Audio",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isAudioActive) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                }

                                Text("  |  ", color = Color.White.copy(alpha = 0.5f))

                                val isSubActive = uiState.selectedSubtitleTrackId != null
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenSubtitleTracks() }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ClosedCaption,
                                        contentDescription = "Subtitles",
                                        tint = if (isSubActive) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Subtitles",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isSubActive) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                }

                                Text("  |  ", color = Color.White.copy(alpha = 0.5f))

                                val isSpeedActive = uiState.playbackSpeed != 1.0f
                                val speedTint = if (isSpeedActive) MaterialTheme.colorScheme.primary else Color.White
                                PlaybackSpeedSelector(
                                    currentSpeed = uiState.playbackSpeed,
                                    onSpeedSelected = onSpeedSelected,
                                    tint = speedTint,
                                    showBackground = false,
                                    showText = true,
                                    onMenuExpandedChanged = onSpeedMenuExpandedChanged
                                )
                            }
                        }
                    }
                }
            }

            // BRIGHTNESS SLIDER (LEFT SIDE)
            AnimatedVisibility(
                visible = isBrightnessVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
            ) {
                VerticalVolumeBrightnessSlider(
                    value = brightnessValue,
                    icon = Icons.Rounded.WbSunny,
                    contentDescription = "Brightness Level"
                )
            }

            // VOLUME SLIDER (RIGHT SIDE)
            AnimatedVisibility(
                visible = isVolumeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
            ) {
                val volumeIcon = when {
                    volumeValue <= 0.01f -> Icons.AutoMirrored.Rounded.VolumeOff
                    volumeValue < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                }
                VerticalVolumeBrightnessSlider(
                    value = volumeValue,
                    icon = volumeIcon,
                    contentDescription = "Volume Level"
                )
            }

            // PRESS & HOLD 2.0X SPEED BOOST BADGE IN LANDSCAPE
            if (isSpeedBoostActive) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "2.0x",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.FastForward,
                            contentDescription = "2x Speed Active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // PINCH TO ZOOM SCALE BADGE IN LANDSCAPE (FIXED POSITION BELOW TOP BAR AREA)
            if (isZoomIndicatorVisible && zoomBadgeText.isNotEmpty()) {
                val badgeModifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = badgeModifier
                ) {
                    Text(
                        text = zoomBadgeText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    } else {
        // PORTRAIT MODE
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { containerHeight = it.height.toFloat() }
                .graphicsLayer {
                    translationY = offsetY.value
                }
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .pointerInput(uiState.isScreenLocked) {
                    if (uiState.isScreenLocked) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { _ ->
                            velocityTracker = VelocityTracker()
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            coroutineScope.launch {
                                val rawOffset = offsetY.value + dragAmount
                                val newOffset = if (rawOffset < 0f) {
                                    rawOffset * 0.35f
                                } else {
                                    rawOffset
                                }
                                offsetY.snapTo(newOffset)
                            }
                        },
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity().y
                            coroutineScope.launch {
                                if (offsetY.value > collapseThresholdPx || velocity > 400f) {
                                    offsetY.animateTo(
                                        targetValue = containerHeight,
                                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                    )
                                    onBack()
                                } else {
                                    offsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(
                                            durationMillis = 300,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            }
                        }
                    )
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                // 1. TOP BAR: Minimize & Now Playing
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimize",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AnimatedWaveformIcon(
                                isAnimating = uiState.isPlaying,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Now Playing",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.size(48.dp))
                }

                // 2. VIDEO DISPLAY SECTION
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(portraitAspectRatio)
                        .clipToBounds()
                        .background(Color.Black)
                        .pointerInput(uiState.isScreenLocked) {
                            if (uiState.isScreenLocked) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                var isHolding = false
                                var savedSpeed = currentPlaybackSpeed

                                val holdJob = coroutineScope.launch {
                                    delay(400)
                                    savedSpeed = currentPlaybackSpeed
                                    isHolding = true
                                    currentOnSpeedSelected(2.0f)
                                    isSpeedBoostActive = true
                                    try {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } catch (_: Exception) {
                                    }
                                }

                                try {
                                    val touchSlop = viewConfiguration.touchSlop
                                    var totalPan = Offset.Zero

                                    do {
                                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                        val panChange = event.calculatePan()
                                        totalPan += panChange

                                        if (!isHolding && (totalPan.getDistance() > touchSlop || event.changes.size > 1)) {
                                            holdJob.cancel()
                                        }
                                    } while (event.changes.any { it.pressed })
                                } finally {
                                    holdJob.cancel()
                                    if (isHolding) {
                                        isSpeedBoostActive = false
                                        currentOnSpeedSelected(savedSpeed)
                                        try {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    VideoPlayerSurface(
                        player = player,
                        onSurfaceClick = onOverlayClick,
                        onRewind = onRewind,
                        onForward = onForward,
                        isScreenLocked = uiState.isScreenLocked,
                        externalZoomScale = zoomScale,
                        externalPanOffset = panOffset,
                        onZoomChanged = { scale, offset ->
                            zoomScale = scale
                            panOffset = offset
                            val formattedScale = String.format(Locale.US, "%.1fx", scale)
                            if (zoomBadgeText != formattedScale) {
                                zoomBadgeText = formattedScale
                                zoomBadgeTrigger++
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                    )

                    // PRESS & HOLD 2.0X SPEED BOOST BADGE IN PORTRAIT
                    if (isSpeedBoostActive) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "2.0x",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.FastForward,
                                    contentDescription = "2x Speed Active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // PINCH TO ZOOM SCALE BADGE IN PORTRAIT
                    if (isZoomIndicatorVisible && zoomBadgeText.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = if (isSpeedBoostActive) 64.dp else 16.dp)
                        ) {
                            Text(
                                text = zoomBadgeText,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }

                    // EXPAND TO LANDSCAPE BUTTON
                    IconButton(
                        onClick = onToggleOrientation,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = "Expand to Landscape",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // 3. TITLE, SUBTITLE & PLAYBACK CONTROLS SECTION
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = shortTitle,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val subtitleText = uiState.currentMediaItem?.getFormattedSubtitle(includeFolder = false)
                    if (!subtitleText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                    ) {
                        Text(
                            text = uiState.currentMediaItem?.resolution ?: "1920x1080",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (!uiState.isScreenLocked) {
                        // Seekbar
                        Slider(
                            value = if (isUserScrubbing) userScrubPositionMs else uiState.currentPositionMs.toFloat(),
                            onValueChange = { newValue ->
                                if (!isUserScrubbing) {
                                    isUserScrubbing = true
                                    onScrubbingChanged(true)
                                }
                                userScrubPositionMs = newValue
                            },
                            onValueChangeFinished = {
                                isUserScrubbing = false
                                onScrubbingChanged(false)
                                onSeek(userScrubPositionMs.toLong())
                            },
                            valueRange = 0f..(uiState.durationMs.toFloat().coerceAtLeast(1f)),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Time Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = currentPosText,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Text(
                                text = durationText,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Primary Controls Row: Shuffle, Previous, Play/Pause, Next, Loop
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onToggleShuffle,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = if (uiState.isShuffleModeEnabled) "Shuffle On" else "Shuffle Off",
                                    tint = if (uiState.isShuffleModeEnabled)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            IconButton(
                                onClick = onPrevious,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Track",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(36.dp),
                                )
                            }

                            Surface(
                                onClick = onPlayPauseToggle,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(68.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (uiState.isBuffering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(36.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 3.dp,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(44.dp),
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = onNext,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Track",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(36.dp),
                                )
                            }

                            val (repeatIcon, repeatTint, repeatDesc) = when (uiState.loopMode) {
                                LoopMode.OFF -> Triple(
                                    Icons.Rounded.Repeat,
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    "Loop Off"
                                )
                                LoopMode.ALL -> Triple(
                                    Icons.Rounded.Repeat,
                                    MaterialTheme.colorScheme.primary,
                                    "Loop All"
                                )
                                LoopMode.ONE -> Triple(
                                    Icons.Rounded.RepeatOne,
                                    MaterialTheme.colorScheme.primary,
                                    "Loop One"
                                )
                            }

                            IconButton(
                                onClick = onToggleLoop,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = repeatIcon,
                                    contentDescription = repeatDesc,
                                    tint = repeatTint,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Secondary Controls Row: Rewind (-10s), Speed Selector, Fast-Forward (+10s)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onRewind,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            PlaybackSpeedSelector(
                                currentSpeed = uiState.playbackSpeed,
                                onSpeedSelected = onSpeedSelected,
                                tint = MaterialTheme.colorScheme.onSurface,
                                onMenuExpandedChanged = onSpeedMenuExpandedChanged
                            )

                            IconButton(
                                onClick = onForward,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // NEW THIRD ROW: Audio | Subtitles | Lock/Unlock (Directly below existing controls)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Audio Option
                            val isAudioActive = uiState.selectedAudioTrackId != null
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenAudioTracks() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Audiotrack,
                                    contentDescription = "Audio Track",
                                    tint = if (isAudioActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Audio",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isAudioActive) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isAudioActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Subtitles Option
                            val isSubActive = uiState.selectedSubtitleTrackId != null
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenSubtitleTracks() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ClosedCaption,
                                    contentDescription = "Subtitles",
                                    tint = if (isSubActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Subtitles",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSubActive) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSubActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Lock/Unlock Option
                            val isLockActive = uiState.isScreenLocked
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onToggleScreenLock() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLockActive) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                    contentDescription = if (isLockActive) "Unlock" else "Lock",
                                    tint = if (isLockActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isLockActive) "Unlock" else "Lock",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isLockActive) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isLockActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        // Locked Screen Indicator in Portrait Mode
                        Spacer(modifier = Modifier.height(32.dp))
                        Surface(
                            onClick = onToggleScreenLock,
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Tap to Unlock",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Screen Locked - Tap to Unlock",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun VerticalVolumeBrightnessSlider(
    value: Float,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Transparent,
        modifier = modifier.width(46.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(100.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction = value.coerceIn(0f, 1f))
                        .align(Alignment.BottomCenter)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PlaybackControlsOverlay(
    uiState: PlayerUiState,
    player: Player? = null,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onOpenAudioTracks: () -> Unit = {},
    onOpenSubtitleTracks: () -> Unit = {},
    onClose: () -> Unit,
    onBack: () -> Unit = onClose,
    onOverlayClick: () -> Unit,
    onToggleScreenLock: () -> Unit = {},
    onToggleOrientation: () -> Unit = {},
    onScrubbingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    VideoPlayerContent(
        uiState = uiState,
        player = player,
        onPlayPauseToggle = onPlayPauseToggle,
        onSeek = onSeek,
        onRewind = onRewind,
        onForward = onForward,
        onNext = onNext,
        onPrevious = onPrevious,
        onOpenAudioTracks = onOpenAudioTracks,
        onOpenSubtitleTracks = onOpenSubtitleTracks,
        onBack = onBack,
        onOverlayClick = onOverlayClick,
        onToggleScreenLock = onToggleScreenLock,
        onToggleOrientation = onToggleOrientation,
        onScrubbingChanged = onScrubbingChanged,
        modifier = modifier
    )
}

data class LandscapeZoomResult(
    val scale: Float,
    val pan: Offset,
    val badgeText: String?
)

fun calculatePanBounds(
    scale: Float,
    proposedPan: Offset,
    pw: Float,
    ph: Float,
    vw: Float,
    vh: Float
): Offset {
    if (scale <= 1.0f || pw <= 0f || ph <= 0f) {
        return Offset.Zero
    }

    val effectiveVw = if (vw > 0f) vw else pw
    val effectiveVh = if (vh > 0f) vh else ph

    val viewportAspect = pw / ph
    val videoAspect = effectiveVw / effectiveVh

    val fittedW = if (viewportAspect > videoAspect) ph * videoAspect else pw
    val fittedH = if (viewportAspect > videoAspect) ph else pw / videoAspect

    val scaledW = fittedW * scale
    val scaledH = fittedH * scale

    val maxPanX = if (scaledW > pw) (scaledW - pw) / 2f else 0f
    val maxPanY = if (scaledH > ph) (scaledH - ph) / 2f else 0f

    val newX = proposedPan.x.coerceIn(-maxPanX, maxPanX)
    val newY = proposedPan.y.coerceIn(-maxPanY, maxPanY)

    return Offset(newX, newY)
}

fun calculateLandscapeZoomState(
    initialScale: Float,
    accumulatedZoom: Float,
    fillScale: Float
): LandscapeZoomResult {
    val effectiveFill = if (fillScale > 1.01f) fillScale else 1.0f

    if (effectiveFill <= 1.01f) {
        return LandscapeZoomResult(1.0f, Offset.Zero, null)
    }

    if (initialScale <= 1.01f) {
        if (accumulatedZoom > 1.02f) {
            return LandscapeZoomResult(effectiveFill, Offset.Zero, "Fill screen")
        } else {
            return LandscapeZoomResult(1.0f, Offset.Zero, null)
        }
    } else {
        if (accumulatedZoom < 0.98f) {
            return LandscapeZoomResult(1.0f, Offset.Zero, "Original")
        } else {
            return LandscapeZoomResult(effectiveFill, Offset.Zero, null)
        }
    }
}

suspend fun PointerInputScope.detectLandscapeVideoTransformGestures(
    currentZoomScale: () -> Float,
    fillScale: () -> Float,
    onGestureUpdate: (newScale: Float, newPan: Offset, badgeText: String?) -> Unit
) {
    awaitEachGesture {
        var accumulatedZoom = 1f
        var pastTouchSlop = false
        var snappedInThisGesture = false
        val touchSlop = viewConfiguration.touchSlop

        val initialScale = currentZoomScale()
        val targetFillScale = fillScale()

        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Main)
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val isMultiTouch = event.changes.size > 1

                if (!pastTouchSlop) {
                    accumulatedZoom *= zoomChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - accumulatedZoom) * centroidSize

                    if (isMultiTouch && zoomMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    if (isMultiTouch) {
                        accumulatedZoom *= zoomChange

                        val result = calculateLandscapeZoomState(
                            initialScale = initialScale,
                            accumulatedZoom = accumulatedZoom,
                            fillScale = targetFillScale
                        )

                        val isStateChanged = abs(result.scale - initialScale) > 0.01f
                        if (isStateChanged && !snappedInThisGesture) {
                            snappedInThisGesture = true
                            onGestureUpdate(result.scale, result.pan, result.badgeText)
                        }

                        event.changes.forEach {
                            if (it.positionChanged()) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}
