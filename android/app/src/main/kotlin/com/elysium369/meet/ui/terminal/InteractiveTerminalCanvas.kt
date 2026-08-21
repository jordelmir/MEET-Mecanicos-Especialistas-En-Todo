package com.elysium369.meet.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.terminal.ElysiumInteractivePtySession
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.max
import kotlin.math.min

/**
 * Hardware-accelerated Interactive VT100 Canvas for Elysium Vanguard Terminal.
 * Features:
 * - 60fps Canvas 2D character matrix
 * - Drag-to-select specific text with highlight
 * - One-tap Copy selection to clipboard
 * - CRT scanlines & blinking cursor
 * - Full ANSI/VT100 color parsing
 */
@Composable
fun InteractiveTerminalCanvas(
    session: ElysiumInteractivePtySession,
    modifier: Modifier = Modifier,
    onFocusRequest: () -> Unit = {}
) {
    val terminalState by session.emulator.stateFlow.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current

    // Text selection coordinates (row, col)
    var selectionStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectionEnd by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedText by remember { mutableStateOf("") }

    // Cursor blink animation
    val infiniteTransition = rememberInfiniteTransition(label = "CursorBlink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CursorAlpha"
    )

    // Android native text paint for ultrafast canvas rendering
    val nativePaint = remember {
        android.graphics.Paint().apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 28f
            isAntiAlias = true
        }
    }

    val fontMetrics = remember(nativePaint.textSize) { nativePaint.fontMetrics }
    val charWidth = remember(nativePaint.textSize) { nativePaint.measureText("M") }
    val charHeight = remember(fontMetrics) { fontMetrics.bottom - fontMetrics.top }

    fun cellAt(offset: Offset): Pair<Int, Int> {
        val col = (offset.x / charWidth).toInt().coerceIn(0, (session.emulator.cols - 1).coerceAtLeast(0))
        val row = (offset.y / charHeight).toInt().coerceIn(0, (session.emulator.rows - 1).coerceAtLeast(0))
        return Pair(row, col)
    }

    fun extractSelectionText(start: Pair<Int, Int>, end: Pair<Int, Int>): String {
        val minR = min(start.first, end.first)
        val maxR = max(start.first, end.first)
        val minC = if (minR == maxR) min(start.second, end.second) else 0
        val maxC = if (minR == maxR) max(start.second, end.second) else session.emulator.cols - 1

        val sb = StringBuilder()
        val lines = terminalState.lines
        for (r in minR..maxR) {
            if (r in lines.indices) {
                val row = lines[r]
                val cStart = if (r == minR) min(start.second, end.second) else 0
                val cEnd = if (r == maxR) max(start.second, end.second) else row.size - 1
                val rowText = StringBuilder()
                for (c in cStart..cEnd) {
                    if (c in row.indices) {
                        rowText.append(row[c].char)
                    }
                }
                sb.append(rowText.toString().trimEnd())
                if (r < maxR) sb.append("\n")
            }
        }
        return sb.toString()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MeetColors.backgroundDeep)
            .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) {
                focusRequester.requestFocus()
                onFocusRequest()
                selectionStart = null
                selectionEnd = null
                selectedText = ""
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val cell = cellAt(offset)
                        selectionStart = cell
                        selectionEnd = cell
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val cell = cellAt(change.position)
                        selectionEnd = cell
                        val start = selectionStart
                        if (start != null) {
                            selectedText = extractSelectionText(start, cell)
                        }
                    },
                    onDragEnd = {
                        val start = selectionStart
                        val end = selectionEnd
                        if (start != null && end != null) {
                            selectedText = extractSelectionText(start, end)
                        }
                    },
                    onDragCancel = {
                        selectionStart = null
                        selectionEnd = null
                        selectedText = ""
                    }
                )
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Enter -> { session.sendEnter(); true }
                        Key.Backspace -> { session.sendBackspace(); true }
                        Key.Tab -> { session.sendTab(); true }
                        Key.Escape -> { session.sendEsc(); true }
                        Key.DirectionUp -> { session.sendArrowUp(); true }
                        Key.DirectionDown -> { session.sendArrowDown(); true }
                        Key.DirectionLeft -> { session.sendArrowLeft(); true }
                        Key.DirectionRight -> { session.sendArrowRight(); true }
                        Key.MoveHome -> { session.sendHome(); true }
                        Key.MoveEnd -> { session.sendEnd(); true }
                        Key.PageUp -> { session.sendPageUp(); true }
                        Key.PageDown -> { session.sendPageDown(); true }
                        Key.Delete -> { session.sendDelete(); true }
                        else -> {
                            val unicodeChar = keyEvent.utf16CodePoint
                            if (unicodeChar > 0) {
                                session.sendKey(unicodeChar.toChar())
                                true
                            } else false
                        }
                    }
                } else false
            }
            .padding(6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val fitCols = (canvasWidth / charWidth).toInt().coerceAtLeast(20)
            val fitRows = (canvasHeight / charHeight).toInt().coerceAtLeast(10)
            if (fitCols != session.emulator.cols || fitRows != session.emulator.rows) {
                session.emulator.resize(fitCols, fitRows)
            }

            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas

                val lines = terminalState.lines
                val selS = selectionStart
                val selE = selectionEnd
                val hasSel = selS != null && selE != null

                val minR = if (hasSel) min(selS!!.first, selE!!.first) else -1
                val maxR = if (hasSel) max(selS!!.first, selE!!.first) else -1
                val minC = if (hasSel && minR == maxR) min(selS.second, selE.second) else if (hasSel) 0 else -1
                val maxC = if (hasSel && minR == maxR) max(selS.second, selE.second) else if (hasSel) session.emulator.cols - 1 else -1

                for (r in lines.indices) {
                    val row = lines[r]
                    val y = (r * charHeight) - fontMetrics.top

                    for (c in row.indices) {
                        val cell = row[c]
                        val x = c * charWidth

                        val isSelected = hasSel && r in minR..maxR && (
                            (minR == maxR && c in minC..maxC) ||
                            (minR != maxR && ((r == minR && c >= selS.second) || (r == maxR && c <= selE.second) || (r in (minR + 1) until maxR)))
                        )

                        if (isSelected) {
                            // Highlight selection with cyan overlay
                            drawRect(
                                color = Color(0x6600E5FF),
                                topLeft = Offset(x, r * charHeight),
                                size = Size(charWidth, charHeight)
                            )
                        } else if (cell.bgColor != Color.Transparent) {
                            drawRect(
                                color = cell.bgColor,
                                topLeft = Offset(x, r * charHeight),
                                size = Size(charWidth, charHeight)
                            )
                        }

                        if (cell.char != ' ') {
                            nativePaint.color = if (isSelected) android.graphics.Color.WHITE else cell.fgColor.toArgb()
                            nativePaint.isFakeBoldText = cell.isBold
                            nativePaint.isUnderlineText = cell.isUnderline
                            nativeCanvas.drawText(cell.char.toString(), x, y, nativePaint)
                        }
                    }
                }

                // Draw hardware blinking cursor
                if (terminalState.cursorVisible && cursorAlpha > 0.3f && !hasSel) {
                    val cRow = terminalState.cursorRow
                    val cCol = terminalState.cursorCol
                    val cursorX = cCol * charWidth
                    val cursorY = cRow * charHeight

                    drawRect(
                        color = MeetColors.neonGreen.copy(alpha = 0.85f),
                        topLeft = Offset(cursorX, cursorY),
                        size = Size(charWidth, charHeight)
                    )

                    if (cRow < lines.size && cCol < lines[cRow].size) {
                        val underChar = lines[cRow][cCol].char
                        if (underChar != ' ') {
                            nativePaint.color = android.graphics.Color.BLACK
                            val textY = (cRow * charHeight) - fontMetrics.top
                            nativeCanvas.drawText(underChar.toString(), cursorX, textY, nativePaint)
                        }
                    }
                }
            }

            // CRT Scanlines Effect
            val scanlineSpacing = 6.dp.toPx()
            val scanlineHeight = 1.5.dp.toPx()
            var sy = 0f
            while (sy < size.height) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.12f),
                    topLeft = Offset(0f, sy),
                    size = Size(size.width, scanlineHeight)
                )
                sy += scanlineSpacing
            }
        }

        // Floating Selection Action Bar (Copy Selection)
        if (selectedText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.dp, MeetColors.cyberCyan, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Elysium Terminal", selectedText))
                            Toast.makeText(context, "Texto copiado al portapapeles (${selectedText.length} caracteres)", Toast.LENGTH_SHORT).show()
                            selectionStart = null
                            selectionEnd = null
                            selectedText = ""
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.neonGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("📋 COPIAR", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = {
                            selectionStart = null
                            selectionEnd = null
                            selectedText = ""
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.backgroundDeep,
                            contentColor = MeetColors.textMuted
                        ),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("✕", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
