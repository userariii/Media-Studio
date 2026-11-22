package com.android.multimediastudio.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.util.VelocityTracker
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import com.android.multimediastudio.ui.components.AnimatedWaveformIcon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.multimediastudio.data.model.MediaItem
import kotlinx.coroutines.launch

@Composable
private fun AudioArtworkCard(
    mediaItem: MediaItem,
    scalePulse: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.scale(scalePulse),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (mediaItem.thumbnailUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(mediaItem.thumbnailUri)
                        .crossfade(enable = true)
                        .build(),
                    contentDescription = mediaItem.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                        modifier = Modifier.size(72.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerContent(
    mediaItem: MediaItem,
    uiState: PlayerUiState,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleLoop: () -> Unit = {},
    onSpeedSelected: (Float) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val collapseThresholdPx = with(density) { 120.dp.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var containerHeight by remember { mutableFloatStateOf(1500f) }
    var velocityTracker by remember { mutableStateOf(VelocityTracker()) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (uiState.isPlaying) 1.04f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scalePulse",
    )

    var isUserScrubbing by remember { mutableStateOf(value = false) }
    var userScrubPositionMs by remember { mutableFloatStateOf(value = 0f) }

    val displayPositionMs = if (isUserScrubbing) {
        userScrubPositionMs.toLong()
    } else {
        uiState.currentPositionMs
    }

    val currentPosText = PlayerUiState.formatTime(displayPositionMs)
    val durationText = PlayerUiState.formatTime(uiState.durationMs)

    val subtitleText = remember(mediaItem.artist, mediaItem.album, mediaItem.mediaType) {
        mediaItem.getFormattedSubtitle(includeFolder = false)
    }

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
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { _ ->
                        velocityTracker = VelocityTracker()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        coroutineScope.launch {
                            val newOffset = (offsetY.value + dragAmount).coerceAtLeast(0f)
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
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // 1. TOP BAR FOR AUDIO PLAYER: Minimize & Now Playing
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.size(48.dp))
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        AudioArtworkCard(
                            mediaItem = mediaItem,
                            scalePulse = scalePulse,
                            modifier = Modifier
                                .sizeIn(maxWidth = 180.dp, maxHeight = 180.dp)
                                .aspectRatio(1f)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .padding(start = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = mediaItem.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE, spacing = MarqueeSpacing(24.dp)),
                        )

                        if (!subtitleText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Slider(
                            value = if (isUserScrubbing) userScrubPositionMs else uiState.currentPositionMs.toFloat(),
                            onValueChange = { newValue ->
                                isUserScrubbing = true
                                userScrubPositionMs = newValue
                            },
                            onValueChangeFinished = {
                                isUserScrubbing = false
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = currentPosText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = durationText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onToggleShuffle, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = if (uiState.isShuffleModeEnabled) "Shuffle On" else "Shuffle Off",
                                    tint = if (uiState.isShuffleModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Track",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(30.dp),
                                )
                            }

                            Surface(
                                onClick = onPlayPauseToggle,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(52.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (uiState.isBuffering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.5.dp,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(34.dp),
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Track",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(30.dp),
                                )
                            }

                            val (repeatIcon, repeatTint, repeatDesc) = when (uiState.loopMode) {
                                LoopMode.OFF -> Triple(Icons.Rounded.Repeat, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), "Loop Off")
                                LoopMode.ALL -> Triple(Icons.Rounded.Repeat, MaterialTheme.colorScheme.primary, "Loop All")
                                LoopMode.ONE -> Triple(Icons.Rounded.RepeatOne, MaterialTheme.colorScheme.primary, "Loop One")
                            }
                            IconButton(onClick = onToggleLoop, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = repeatIcon,
                                    contentDescription = repeatDesc,
                                    tint = repeatTint,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            PlaybackSpeedSelector(
                                currentSpeed = uiState.playbackSpeed,
                                onSpeedSelected = onSpeedSelected,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    AudioArtworkCard(
                        mediaItem = mediaItem,
                        scalePulse = scalePulse,
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .aspectRatio(1f)
                    )

                Spacer(modifier = Modifier.height(24.dp))

                // TITLE & SUBTITLE BELOW ALBUM ARTWORK
                Text(
                    text = mediaItem.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            spacing = MarqueeSpacing(24.dp),
                        ),
                )

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
                        text = "AUDIO TRACK",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 3. DEDICATED PLAYBACK CONTROLS SECTION BELOW TITLE
                // Seekbar
                Slider(
                    value = if (isUserScrubbing) userScrubPositionMs else uiState.currentPositionMs.toFloat(),
                    onValueChange = { newValue ->
                        isUserScrubbing = true
                        userScrubPositionMs = newValue
                    },
                    onValueChangeFinished = {
                        isUserScrubbing = false
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

                // Time Row below Seekbar
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

                Spacer(modifier = Modifier.height(20.dp))

                // Main Controls Row: Shuffle, Previous Track, Play/Pause, Next Track, Loop
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

                Spacer(modifier = Modifier.height(28.dp))

                // Secondary Controls Row: Rewind (-10s), Playback Speed Selector, Fast-Forward (+10s)
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

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
}
