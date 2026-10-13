package com.android.multimediastudio.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.multimediastudio.ui.theme.MultimediaStudioTheme

/**
 * Custom glowing seekbar thumb inspired by futuristic video player seekbars,
 * adapted to Material 3 design system.
 *
 * Performance-optimized: uses [drawWithCache] to eliminate object allocations
 * during drag/scrub draw calls for buttery 120fps motion.
 */
@Composable
fun GlowingSeekbarThumb(
    modifier: Modifier = Modifier,
    isScrubbing: Boolean = false,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    thumbSize: Dp = 28.dp
) {
    val scale by animateFloatAsState(
        targetValue = if (isScrubbing) 1.20f else 1.0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "thumbScale"
    )

    val glowAlphaMultiplier by animateFloatAsState(
        targetValue = if (isScrubbing) 1.20f else 1.0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .size(thumbSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f

                val glowRadius = maxRadius * 0.95f
                val glowBrush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Color.White.copy(alpha = (0.90f * glowAlphaMultiplier).coerceAtMost(1f)),
                        0.28f to primaryColor.copy(alpha = (0.50f * glowAlphaMultiplier).coerceAtMost(1f)),
                        0.62f to primaryColor.copy(alpha = (0.20f * glowAlphaMultiplier).coerceAtMost(1f)),
                        0.88f to primaryColor.copy(alpha = (0.05f * glowAlphaMultiplier).coerceAtMost(1f)),
                        1.00f to Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                )

                val outerRingRadius = maxRadius * 0.82f
                val outerStrokeWidth = 1.0.dp.toPx()

                val centerHaloRadius = maxRadius * 0.42f
                val centerHaloBrush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White,
                        0.5f to Color.White.copy(alpha = 0.6f),
                        1.0f to Color.Transparent
                    ),
                    center = center,
                    radius = centerHaloRadius
                )

                val coreRadius = maxRadius * 0.24f

                onDrawWithContent {
                    // 1. Soft Outer Radial Glow
                    drawCircle(
                        brush = glowBrush,
                        radius = glowRadius,
                        center = center
                    )

                    // 2. Subtle Outer Concentric Ring
                    drawCircle(
                        color = Color.White.copy(alpha = 0.45f),
                        radius = outerRingRadius,
                        center = center,
                        style = Stroke(width = outerStrokeWidth)
                    )

                    // 3. Center White Halo
                    drawCircle(
                        brush = centerHaloBrush,
                        radius = centerHaloRadius,
                        center = center
                    )

                    // 4. Bright Solid White Core
                    drawCircle(
                        color = Color.White,
                        radius = coreRadius,
                        center = center
                    )
                }
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun GlowingSeekbarThumbPreview() {
    MultimediaStudioTheme {
        Surface(
            modifier = Modifier.padding(24.dp),
            color = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var sliderValue by remember { mutableFloatStateOf(0.35f) }

                Box(contentAlignment = Alignment.Center) {
                    GlowingSeekbarThumb(isScrubbing = false)
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0f..1f,
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
                        GlowingSeekbarThumb(isScrubbing = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
