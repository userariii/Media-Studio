package com.android.multimediastudio.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import com.android.multimediastudio.data.model.MediaItem

@Composable
fun FloatingVideoPip(
    mediaItem: MediaItem,
    isPlaying: Boolean,
    player: Player?,
    onPlayPauseClick: () -> Unit,
    onExpandClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    isInPipMode: Boolean = false,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    onVideoBoundsChanged: ((android.graphics.Rect) -> Unit)? = null
) {
    val (effectiveVideoWidth, effectiveVideoHeight) = if (videoWidth > 0 && videoHeight > 0) {
        Pair(videoWidth.toFloat(), videoHeight.toFloat())
    } else {
        val vw = player?.videoSize?.width ?: 0
        val vh = player?.videoSize?.height ?: 0
        val rot = player?.videoSize?.unappliedRotationDegrees ?: 0
        val effW = if (rot == 90 || rot == 270) vh.toFloat() else vw.toFloat()
        val effH = if (rot == 90 || rot == 270) vw.toFloat() else vh.toFloat()
        Pair(effW, effH)
    }

    val isPortraitFromMetadata = mediaItem.isReelOrShort()

    val isPortraitVideo = if (effectiveVideoWidth > 0f && effectiveVideoHeight > 0f) {
        effectiveVideoHeight > effectiveVideoWidth
    } else {
        isPortraitFromMetadata
    }

    val videoAspect = if (effectiveVideoWidth > 0f && effectiveVideoHeight > 0f) {
        val raw = effectiveVideoWidth / effectiveVideoHeight
        if (isPortraitVideo) raw.coerceIn(0.35f, 1.0f) else raw.coerceIn(1.0f, 2.5f)
    } else {
        val parsedAspect = mediaItem.resolution?.let { res ->
            if (res.contains("x")) {
                val parts = res.split("x")
                val w = parts.getOrNull(0)?.toFloatOrNull() ?: 0f
                val h = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                if (w > 0f && h > 0f) {
                    val raw = w / h
                    if (isPortraitVideo) raw.coerceIn(0.35f, 1.0f) else raw.coerceIn(1.0f, 2.5f)
                } else null
            } else null
        }
        parsedAspect ?: if (isPortraitVideo) 9f / 16f else 16f / 9f
    }

    val pipAspectRatio = videoAspect.coerceIn(0.4184f, 2.39f)
    val defaultWidth = if (isPortraitVideo) 140.dp else 220.dp

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var pipWidthDp by remember(mediaItem.id, isPortraitVideo) { mutableStateOf(defaultWidth) }

    val effectiveProgress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val outerModifier = if (isInPipMode) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp)
    }

    Box(
        modifier = outerModifier,
        contentAlignment = if (isInPipMode) Alignment.Center else Alignment.BottomEnd
    ) {
        val cardModifier = if (isInPipMode) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .graphicsLayer {
                    translationX = offsetX
                    translationY = offsetY
                }
                .width(pipWidthDp)
                .aspectRatio(pipAspectRatio)
                .onGloballyPositioned { coordinates ->
                    if (coordinates.isAttached && !isInPipMode) {
                        val bounds = coordinates.boundsInWindow()
                        if (bounds.width > 0f && bounds.height > 0f) {
                            val rect = android.graphics.Rect(
                                bounds.left.toInt(),
                                bounds.top.toInt(),
                                bounds.right.toInt(),
                                bounds.bottom.toInt()
                            )
                            onVideoBoundsChanged?.invoke(rect)
                        }
                    }
                }
                .pointerInput(isPortraitVideo) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom != 1f) {
                            val minWidth = if (isPortraitVideo) 100.dp else 140.dp
                            val maxWidth = if (isPortraitVideo) 200.dp else 340.dp
                            pipWidthDp = (pipWidthDp * zoom).coerceIn(minWidth, maxWidth)
                        }
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onExpandClick() }
                    )
                }
        }

        Card(
            modifier = cardModifier,
            shape = if (isInPipMode) RectangleShape else RoundedCornerShape(16.dp),
            border = if (isInPipMode) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isInPipMode) 0.dp else 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            val innerBoxModifier = if (isInPipMode) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .clipToBounds()
            }
            Box(
                modifier = innerBoxModifier,
                contentAlignment = Alignment.Center
            ) {
                // Video Surface with inner surface gestures disabled in PiP mode
                VideoPlayerSurface(
                    player = player,
                    onSurfaceClick = if (isInPipMode) ({ }) else onExpandClick,
                    isScreenLocked = false,
                    enableGestures = false,
                    resizeMode = if (isPortraitVideo && !isInPipMode) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    modifier = Modifier.fillMaxSize()
                )

                if (!isInPipMode) {
                    // Top-Left: Play/Pause Button
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(32.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(onClick = onPlayPauseClick)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Top-Right: Close "X" Button
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(onClick = onCloseClick)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close Video",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Thin Seekbar Trackbar at Bottom
                    LinearProgressIndicator(
                        progress = { effectiveProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}
