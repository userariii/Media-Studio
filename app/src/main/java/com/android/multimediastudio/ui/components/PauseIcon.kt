package com.android.multimediastudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun PauseIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val totalHeight = size.height
        val barWidth = totalWidth * 0.36f
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

        // Left bar
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, 0f),
            size = Size(barWidth, totalHeight),
            cornerRadius = cornerRadius
        )

        // Right bar (ends exactly at totalWidth)
        drawRoundRect(
            color = tint,
            topLeft = Offset(totalWidth - barWidth, 0f),
            size = Size(barWidth, totalHeight),
            cornerRadius = cornerRadius
        )
    }
}
