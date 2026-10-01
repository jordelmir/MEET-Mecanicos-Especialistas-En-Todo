package com.elysium.vanguard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random
import com.elysium.vanguard.ui.theme.TitanColors

/**
 * MATRIX RAIN EFFECT
 * Canvas-based vertical rain of katakana/latin characters.
 * Renders behind content for a living, breathing cyber-void aesthetic.
 */
@Composable
fun MatrixRain(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF39FF14),
    columnSpacing: Int = 22,
    speed: Long = 55L,
    trailLength: Int = 18,
    alpha: Float = 0.7f,
    isMulticolor: Boolean = false
) {
    val matrixChars = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ@#$%&"
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    var tick by remember { mutableIntStateOf(0) }
    var columns by remember { mutableIntStateOf(0) }
    val drops = remember { mutableStateMapOf<Int, Int>() }
    val trailChars = remember { mutableStateMapOf<String, Char>() } // "col_row" -> char

    LaunchedEffect(Unit) {
        while (true) {
            delay(speed)
            tick++
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val charHeightPx = 20f * density.density
        val colSpacingPx = columnSpacing.toFloat() * density.density
        val colCount = (size.width / colSpacingPx).toInt().coerceAtLeast(1)
        val rowCount = (size.height / charHeightPx).toInt().coerceAtLeast(1)

        if (columns != colCount) {
            columns = colCount
            for (c in 0 until colCount) {
                if (drops[c] == null) {
                    drops[c] = Random.nextInt(-rowCount, 0)
                }
            }
        }

        for (col in 0 until colCount) {
            val headRow = drops[col] ?: 0

            for (t in 0 until trailLength) {
                val row = headRow - t
                if (row < 0 || row >= rowCount) continue

                val key = "${col}_${row}"
                val ch = trailChars.getOrPut(key) {
                    matrixChars[Random.nextInt(matrixChars.length)]
                }

                val fadeAlpha = ((trailLength - t).toFloat() / trailLength.toFloat()) * alpha
                val isHead = t == 0
                val charColor = if (isHead) {
                    Color.White.copy(alpha = (fadeAlpha * 1.2f).coerceAtMost(1f))
                } else {
                    val baseColor = if (isMulticolor) {
                        TitanColors.All[col % TitanColors.All.size]
                    } else {
                        color
                    }
                    baseColor.copy(alpha = fadeAlpha * 0.8f)
                }

                val x = col.toFloat() * colSpacingPx
                val y = row.toFloat() * charHeightPx

                drawText(
                    textMeasurer = textMeasurer,
                    text = ch.toString(),
                    topLeft = Offset(x, y),
                    style = TextStyle(
                        color = charColor,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            // Advance drop
            if (tick > 0) {
                val newHead = headRow + 1
                if (newHead - trailLength > rowCount) {
                    drops[col] = Random.nextInt(-15, -1)
                    // Randomize chars for this column
                    for (r in 0 until rowCount) {
                        trailChars["${col}_${r}"] = matrixChars[Random.nextInt(matrixChars.length)]
                    }
                } else {
                    drops[col] = newHead
                    // Random char at new head
                    val newKey = "${col}_${newHead}"
                    trailChars[newKey] = matrixChars[Random.nextInt(matrixChars.length)]
                }
            }
        }
    }
}
