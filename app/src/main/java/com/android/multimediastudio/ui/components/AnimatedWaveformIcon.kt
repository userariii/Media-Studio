package com.android.multimediastudio.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun AnimatedWaveformIcon(
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 5
) {
    val transition = rememberInfiniteTransition(label = "waveform_transition")

    val wavePhase by if (isAnimating) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1300, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // Symmetrical 5-band waveform envelope (center tallest: 1.0, inner: 0.68, outer: 0.36)
    val baseEnvelope = remember(barCount) {
        if (barCount == 5) {
            floatArrayOf(0.36f, 0.68f, 1.0f, 0.68f, 0.36f)
        } else {
            val mid = (barCount - 1) / 2f
            FloatArray(barCount) { i ->
                val distFromCenter = abs(i - mid) / mid
                (1.0f - 0.65f * distFromCenter).coerceIn(0.3f, 1.0f)
            }
        }
    }

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val totalHeight = size.height

        val gapRatio = 0.55f
        val totalSlots = barCount + (barCount - 1) * gapRatio
        val barWidth = totalWidth / totalSlots
        val gapWidth = barWidth * gapRatio
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

        for (i in 0 until barCount) {
            val baseH = baseEnvelope[i]
            val hFraction = if (isAnimating) {
                val offsetPhase = wavePhase + i * 0.8f
                val modulation = 0.18f * sin(offsetPhase.toDouble()).toFloat()
                (baseH + modulation).coerceIn(0.2f, 1.0f)
            } else {
                baseH
            }

            val barH = (totalHeight * hFraction).coerceAtLeast(barWidth)
            val left = i * (barWidth + gapWidth)
            val top = (totalHeight - barH) / 2f // Vertically centered

            drawRoundRect(
                color = tint,
                topLeft = Offset(left, top),
                size = Size(barWidth, barH),
                cornerRadius = cornerRadius
            )
        }
    }
}
