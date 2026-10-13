package com.android.multimediastudio.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height

@Composable
fun PlaybackSpeedSelector(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    showBackground: Boolean = true,
    showText: Boolean = true,
    isColumnLayout: Boolean = false,
    onMenuExpandedChanged: ((Boolean) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val speeds = PlayerUiState.AVAILABLE_PLAYBACK_SPEEDS
    val textColor = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface

    Box(modifier = modifier) {
        if (isColumnLayout) {
            val isSpeedActive = currentSpeed != 1.0f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        expanded = true
                        onMenuExpandedChanged?.invoke(true)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = "Playback Speed",
                    tint = textColor,
                    modifier = Modifier.size(22.dp)
                )
                if (showText) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (currentSpeed == 1.0f) "Speed" else "${currentSpeed}x",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSpeedActive) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = textColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (showBackground) {
            Surface(
                onClick = {
                    expanded = true
                    onMenuExpandedChanged?.invoke(true)
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                contentColor = textColor,
                tonalElevation = 2.dp,
                modifier = Modifier.clip(CircleShape)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = "Playback Speed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    if (showText) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${currentSpeed}x",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 44.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        expanded = true
                        onMenuExpandedChanged?.invoke(true)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = "Playback Speed",
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
                if (showText) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (currentSpeed == 1.0f) "Speed" else "${currentSpeed}x",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 48.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                onMenuExpandedChanged?.invoke(false)
            },
            offset = DpOffset(x = 0.dp, y = 4.dp)
        ) {
            Text(
                text = "Playback Speed",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            speeds.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${speed}x",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (speed == currentSpeed)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        if (speed == currentSpeed) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.size(20.dp))
                        }
                    },
                    onClick = {
                        onSpeedSelected(speed)
                        expanded = false
                        onMenuExpandedChanged?.invoke(false)
                    }
                )
            }
        }
    }
}
