package com.android.multimediastudio.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun AnimatedEqualizerIcon(
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    AnimatedWaveformIcon(
        isAnimating = isAnimating,
        modifier = modifier,
        tint = tint
    )
}
