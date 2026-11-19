package com.android.multimediastudio.ui.player

import android.view.LayoutInflater
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.android.multimediastudio.R
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

enum class SeekSide { NONE, LEFT, RIGHT }

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerSurface(
    player: Player?,
    onSurfaceClick: () -> Unit,
    onRewind: () -> Unit = {},
    onForward: () -> Unit = {},
    isScreenLocked: Boolean = false,
    enableGestures: Boolean = true,
    externalSeekSide: SeekSide = SeekSide.NONE,
    externalSeekTrigger: Long = 0L,
    externalZoomScale: Float? = null,
    externalPanOffset: Offset? = null,
    fillScale: Float = 1.0f,
    onZoomChanged: ((Float, Offset) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var internalZoomScale by remember { mutableFloatStateOf(1.0f) }
    var internalPanOffset by remember { mutableStateOf(Offset.Zero) }

    val zoomScale = externalZoomScale ?: internalZoomScale
    val panOffset = externalPanOffset ?: internalPanOffset
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val currentZoomScale by rememberUpdatedState(zoomScale)
    val currentPanOffset by rememberUpdatedState(panOffset)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)

    var accumulatedSeekSeconds by remember { mutableIntStateOf(0) }
    var activeSeekSide by remember { mutableStateOf(SeekSide.NONE) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player) {
        internalZoomScale = 1.0f
        internalPanOffset = Offset.Zero
    }

    val triggerRewind = {
        val now = System.currentTimeMillis()
        if (accumulatedSeekSeconds > 0 && activeSeekSide == SeekSide.LEFT && (now - lastTapTimestamp) < 800L) {
            accumulatedSeekSeconds += 10
        } else {
            accumulatedSeekSeconds = 10
            activeSeekSide = SeekSide.LEFT
        }
        lastTapTimestamp = now
        onRewind()
    }

    val triggerForward = {
        val now = System.currentTimeMillis()
        if (accumulatedSeekSeconds > 0 && activeSeekSide == SeekSide.RIGHT && (now - lastTapTimestamp) < 800L) {
            accumulatedSeekSeconds += 10
        } else {
            accumulatedSeekSeconds = 10
            activeSeekSide = SeekSide.RIGHT
        }
        lastTapTimestamp = now
        onForward()
    }

    LaunchedEffect(externalSeekTrigger) {
        if (externalSeekTrigger > 0L) {
            if (externalSeekSide == SeekSide.LEFT) {
                triggerRewind()
            } else if (externalSeekSide == SeekSide.RIGHT) {
                triggerForward()
            }
        }
    }

    LaunchedEffect(accumulatedSeekSeconds, activeSeekSide, lastTapTimestamp) {
        if (accumulatedSeekSeconds > 0) {
            delay(800)
            accumulatedSeekSeconds = 0
            activeSeekSide = SeekSide.NONE
        }
    }

    var surfaceModifier = modifier
        .fillMaxSize()
        .clipToBounds()
        .onSizeChanged { containerSize = it }

    if (enableGestures) {
        surfaceModifier = surfaceModifier
            .pointerInput(isScreenLocked) {
                detectTapGestures(
                    onTap = {
                        onSurfaceClick()
                    },
                    onDoubleTap = { offset ->
                        if (isScreenLocked) return@detectTapGestures
                        val isLeft = offset.x < containerSize.width / 2f
                        if (isLeft) {
                            triggerRewind()
                        } else {
                            triggerForward()
                        }
                    }
                )
            }
            .pointerInput(isScreenLocked) {
                if (isScreenLocked) return@pointerInput
                detectVideoTransformGestures(
                    isZoomed = { currentZoomScale > 1.01f },
                    onGesture = { _, pan, zoom, _ ->
                        val newScale = (currentZoomScale * zoom).coerceIn(1.0f, 8.0f)
                        val newPan = if (newScale <= 1.01f) {
                            Offset.Zero
                        } else {
                            val width = if (containerSize.width > 0) containerSize.width.toFloat() else 1080f
                            val height = if (containerSize.height > 0) containerSize.height.toFloat() else 1920f
                            val maxPanX = (width * (newScale - 1f)) / 2f
                            val maxPanY = (height * (newScale - 1f)) / 2f
                            val newPanX = (currentPanOffset.x + pan.x).coerceIn(-maxPanX, maxPanX)
                            val newPanY = (currentPanOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                            Offset(newPanX, newPanY)
                        }

                        if (currentOnZoomChanged != null) {
                            currentOnZoomChanged?.invoke(newScale, newPan)
                        } else {
                            internalZoomScale = newScale
                            internalPanOffset = newPan
                        }
                    }
                )
            }
    }

    Box(
        modifier = surfaceModifier
    ) {
        AndroidView(
            factory = { context ->
                val playerView = LayoutInflater.from(context).inflate(R.layout.player_view, null, false) as PlayerView
                playerView.useController = false
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                playerView.player = player
                playerView.clipChildren = true
                playerView.clipToPadding = true
                playerView.keepScreenOn = player?.isPlaying == true
                playerView
            },
            update = { playerView ->
                playerView.player = player
                playerView.keepScreenOn = player?.isPlaying == true
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = if (zoomScale <= 1.01f) 0f else panOffset.x
                    translationY = if (zoomScale <= 1.01f) 0f else panOffset.y
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
        )

        // DOUBLE-TAP SEEK INDICATOR (-10s / +10s / +20s / +50s)
        if (accumulatedSeekSeconds > 0) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // LEFT HALF (REWIND) - CENTERED AT 25% SCREEN WIDTH
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeSeekSide == SeekSide.LEFT) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.75f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Replay10,
                                    contentDescription = "Rewind",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "-${accumulatedSeekSeconds}s",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // RIGHT HALF (FORWARD) - CENTERED AT 75% SCREEN WIDTH
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeSeekSide == SeekSide.RIGHT) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.75f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "+${accumulatedSeekSeconds}s",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Forward10,
                                    contentDescription = "Forward",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

suspend fun PointerInputScope.detectVideoTransformGestures(
    isZoomed: () -> Boolean,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop || panMotion > touchSlop || event.changes.size > 1 || isZoomed()) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val isTransforming = (zoomChange != 1f) || (event.changes.size > 1) || isZoomed()

                    if (isTransforming) {
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            onGesture(centroid, panChange, zoomChange, 0f)
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
