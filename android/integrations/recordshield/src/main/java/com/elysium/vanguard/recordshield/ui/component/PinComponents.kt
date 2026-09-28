package com.elysium.vanguard.recordshield.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.recordshield.ui.theme.*

@Composable
fun PinDot(isFilled: Boolean, isError: Boolean) {
    val color = when {
        isError -> RecordingRed
        isFilled -> MatrixGreen
        else -> SubtleBorder
    }

    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(if (isFilled) color else Color.Transparent)
            .border(2.dp, color, CircleShape)
    )
}

@Composable
fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(modifier = Modifier.size(72.dp))
                        "⌫" -> KeypadButton(
                            content = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Backspace",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = onBackspace
                        )
                        else -> KeypadButton(
                            content = {
                                Text(
                                    text = key,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Light,
                                    color = TextPrimary
                                )
                            },
                            onClick = { onDigit(key) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    content: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(GlassSurface)
            .border(1.dp, GlassBorder, CircleShape)
            .clickable(onClick = onClick)
    ) {
        content()
    }
}
