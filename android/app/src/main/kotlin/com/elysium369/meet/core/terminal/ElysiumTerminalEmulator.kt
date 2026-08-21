package com.elysium369.meet.core.terminal

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * High-performance VT100 / ANSI / xterm Terminal Matrix Emulator.
 * Maintains a 2D character and color grid with alternate screen buffer support,
 * cursor positioning, ANSI escape parsing, and scrollback history.
 */
class ElysiumTerminalEmulator(
    var cols: Int = 80,
    var rows: Int = 28,
    private val maxScrollback: Int = 3000
) {
    data class Cell(
        val char: Char = ' ',
        val fgColor: Color = Color(0xFF00FF9D),
        val bgColor: Color = Color.Transparent,
        val isBold: Boolean = false,
        val isUnderline: Boolean = false,
        val isReverse: Boolean = false
    )

    data class TerminalState(
        val lines: List<List<Cell>>,
        val cursorRow: Int,
        val cursorCol: Int,
        val cursorVisible: Boolean,
        val cols: Int,
        val rows: Int,
        val isAlternateScreen: Boolean,
        val revision: Long = 0L
    )

    private val lock = ReentrantLock()

    // Screen buffers: Primary and Alternate (for vim, htop, nano, less)
    private var primaryGrid = Array(rows) { Array(cols) { Cell() } }
    private var alternateGrid = Array(rows) { Array(cols) { Cell() } }
    private var isAlternateScreen = false

    private val activeGrid: Array<Array<Cell>>
        get() = if (isAlternateScreen) alternateGrid else primaryGrid

    private val scrollbackBuffer = ArrayList<List<Cell>>()

    // Cursor state
    var cursorRow = 0
        private set
    var cursorCol = 0
        private set
    var cursorVisible = true
        private set

    private var savedCursorRow = 0
    private var savedCursorCol = 0

    // Current text attributes
    private var currentFgColor: Color = Color(0xFF00FF9D)
    private var currentBgColor: Color = Color.Transparent
    private var currentBold = false
    private var currentUnderline = false
    private var currentReverse = false

    // State flow for UI observation
    private var revision = 0L
    private val _stateFlow = MutableStateFlow(buildSnapshot())
    val stateFlow: StateFlow<TerminalState> = _stateFlow.asStateFlow()

    // ANSI escape parser state machine
    private enum class ParserState {
        NORMAL,
        ESC,
        CSI,
        OSC,
        CHARSET
    }

    private var parserState = ParserState.NORMAL
    private val csiParams = StringBuilder()

    // Standard 16 ANSI colors
    private val ansiColors = arrayOf(
        Color(0xFF000000), // 0: Black
        Color(0xFFFF5555), // 1: Red
        Color(0xFF00FF9D), // 2: Green
        Color(0xFFFFB86C), // 3: Yellow
        Color(0xFF8BE9FD), // 4: Blue
        Color(0xFFFF79C6), // 5: Magenta
        Color(0xFF00E5FF), // 6: Cyan
        Color(0xFFF8F8F2), // 7: White
        Color(0xFF6272A4), // 8: Bright Black (Gray)
        Color(0xFFFF6E6E), // 9: Bright Red
        Color(0xFF69FF94), // 10: Bright Green
        Color(0xFFFFFFA5), // 11: Bright Yellow
        Color(0xFFD6ACFF), // 12: Bright Blue
        Color(0xFFFF92DF), // 13: Bright Magenta
        Color(0xFFA4FFFF), // 14: Bright Cyan
        Color(0xFFFFFFFF)  // 15: Bright White
    )

    fun reset() {
        lock.withLock {
            primaryGrid = Array(rows) { Array(cols) { Cell() } }
            alternateGrid = Array(rows) { Array(cols) { Cell() } }
            scrollbackBuffer.clear()
            isAlternateScreen = false
            cursorRow = 0
            cursorCol = 0
            cursorVisible = true
            resetAttributes()
            parserState = ParserState.NORMAL
            publishState()
        }
    }

    fun resize(newCols: Int, newRows: Int) {
        if (newCols <= 0 || newRows <= 0) return
        lock.withLock {
            if (cols == newCols && rows == newRows) return
            val oldPrimary = primaryGrid
            val oldAlt = alternateGrid
            cols = newCols
            rows = newRows

            primaryGrid = Array(rows) { r ->
                Array(cols) { c ->
                    if (r < oldPrimary.size && c < oldPrimary[r].size) oldPrimary[r][c] else Cell()
                }
            }
            alternateGrid = Array(rows) { r ->
                Array(cols) { c ->
                    if (r < oldAlt.size && c < oldAlt[r].size) oldAlt[r][c] else Cell()
                }
            }

            cursorRow = cursorRow.coerceIn(0, rows - 1)
            cursorCol = cursorCol.coerceIn(0, cols - 1)
            publishState()
        }
    }

    /**
     * Feeds raw output bytes/chars from the shell process into the emulator.
     */
    fun process(text: String) {
        lock.withLock {
            var i = 0
            val len = text.length
            while (i < len) {
                val ch = text[i]
                when (parserState) {
                    ParserState.NORMAL -> {
                        when (ch) {
                            '\u001B' -> parserState = ParserState.ESC
                            '\r' -> cursorCol = 0
                            '\n' -> lineFeed()
                            '\b' -> if (cursorCol > 0) cursorCol--
                            '\t' -> {
                                val nextTab = ((cursorCol / 8) + 1) * 8
                                cursorCol = nextTab.coerceAtMost(cols - 1)
                            }
                            '\u0007' -> { /* Bell - ignore */ }
                            else -> {
                                if (ch.code >= 32) {
                                    writeChar(ch)
                                }
                            }
                        }
                    }
                    ParserState.ESC -> {
                        when (ch) {
                            '[' -> {
                                parserState = ParserState.CSI
                                csiParams.clear()
                            }
                            ']' -> {
                                parserState = ParserState.OSC
                            }
                            '(', ')', '*', '+' -> {
                                parserState = ParserState.CHARSET
                            }
                            '7' -> {
                                savedCursorRow = cursorRow
                                savedCursorCol = cursorCol
                                parserState = ParserState.NORMAL
                            }
                            '8' -> {
                                cursorRow = savedCursorRow.coerceIn(0, rows - 1)
                                cursorCol = savedCursorCol.coerceIn(0, cols - 1)
                                parserState = ParserState.NORMAL
                            }
                            'c' -> {
                                reset()
                                parserState = ParserState.NORMAL
                            }
                            'M' -> {
                                if (cursorRow == 0) scrollDown() else cursorRow--
                                parserState = ParserState.NORMAL
                            }
                            else -> {
                                parserState = ParserState.NORMAL
                            }
                        }
                    }
                    ParserState.CSI -> {
                        if (ch in '0'..'9' || ch == ';' || ch == '?' || ch == '>') {
                            csiParams.append(ch)
                        } else {
                            executeCsi(ch, csiParams.toString())
                            parserState = ParserState.NORMAL
                        }
                    }
                    ParserState.OSC -> {
                        if (ch == '\u0007' || (ch == '\\' && i > 0 && text[i - 1] == '\u001B')) {
                            parserState = ParserState.NORMAL
                        }
                    }
                    ParserState.CHARSET -> {
                        parserState = ParserState.NORMAL
                    }
                }
                i++
            }
            publishState()
        }
    }

    private fun writeChar(ch: Char) {
        if (cursorCol >= cols) {
            cursorCol = 0
            lineFeed()
        }
        val effectiveFg = if (currentReverse) (if (currentBgColor == Color.Transparent) Color.Black else currentBgColor) else currentFgColor
        val effectiveBg = if (currentReverse) currentFgColor else currentBgColor

        activeGrid[cursorRow][cursorCol] = Cell(
            char = ch,
            fgColor = effectiveFg,
            bgColor = effectiveBg,
            isBold = currentBold,
            isUnderline = currentUnderline,
            isReverse = currentReverse
        )
        cursorCol++
    }

    private fun lineFeed() {
        if (cursorRow >= rows - 1) {
            scrollUp()
        } else {
            cursorRow++
        }
    }

    private fun scrollUp() {
        if (!isAlternateScreen) {
            scrollbackBuffer.add(primaryGrid[0].toList())
            if (scrollbackBuffer.size > maxScrollback) {
                scrollbackBuffer.removeAt(0)
            }
        }
        val grid = activeGrid
        for (r in 0 until rows - 1) {
            grid[r] = grid[r + 1]
        }
        grid[rows - 1] = Array(cols) { Cell(bgColor = currentBgColor) }
    }

    private fun scrollDown() {
        val grid = activeGrid
        for (r in rows - 1 downTo 1) {
            grid[r] = grid[r - 1]
        }
        grid[0] = Array(cols) { Cell(bgColor = currentBgColor) }
    }

    private fun executeCsi(cmd: Char, params: String) {
        val isPrivate = params.startsWith("?")
        val cleanParams = if (isPrivate) params.substring(1) else params
        val parts = if (cleanParams.isEmpty()) emptyList() else cleanParams.split(";").mapNotNull { it.toIntOrNull() }

        fun param(index: Int, default: Int): Int = parts.getOrNull(index) ?: default

        when (cmd) {
            'A' -> cursorRow = (cursorRow - param(0, 1)).coerceAtLeast(0)
            'B' -> cursorRow = (cursorRow + param(0, 1)).coerceAtMost(rows - 1)
            'C' -> cursorCol = (cursorCol + param(0, 1)).coerceAtMost(cols - 1)
            'D' -> cursorCol = (cursorCol - param(0, 1)).coerceAtLeast(0)
            'E' -> {
                cursorRow = (cursorRow + param(0, 1)).coerceAtMost(rows - 1)
                cursorCol = 0
            }
            'F' -> {
                cursorRow = (cursorRow - param(0, 1)).coerceAtLeast(0)
                cursorCol = 0
            }
            'G' -> cursorCol = (param(0, 1) - 1).coerceIn(0, cols - 1)
            'H', 'f' -> {
                cursorRow = (param(0, 1) - 1).coerceIn(0, rows - 1)
                cursorCol = (param(1, 1) - 1).coerceIn(0, cols - 1)
            }
            'J' -> {
                when (param(0, 0)) {
                    0 -> {
                        for (c in cursorCol until cols) activeGrid[cursorRow][c] = Cell()
                        for (r in cursorRow + 1 until rows) {
                            activeGrid[r] = Array(cols) { Cell() }
                        }
                    }
                    1 -> {
                        for (r in 0 until cursorRow) {
                            activeGrid[r] = Array(cols) { Cell() }
                        }
                        for (c in 0..cursorCol.coerceAtMost(cols - 1)) activeGrid[cursorRow][c] = Cell()
                    }
                    2, 3 -> {
                        for (r in 0 until rows) {
                            activeGrid[r] = Array(cols) { Cell() }
                        }
                        if (param(0, 0) == 3) scrollbackBuffer.clear()
                    }
                }
            }
            'K' -> {
                when (param(0, 0)) {
                    0 -> for (c in cursorCol until cols) activeGrid[cursorRow][c] = Cell()
                    1 -> for (c in 0..cursorCol.coerceAtMost(cols - 1)) activeGrid[cursorRow][c] = Cell()
                    2 -> activeGrid[cursorRow] = Array(cols) { Cell() }
                }
            }
            'L' -> {
                val count = param(0, 1)
                repeat(count) {
                    for (r in rows - 1 downTo cursorRow + 1) {
                        activeGrid[r] = activeGrid[r - 1]
                    }
                    activeGrid[cursorRow] = Array(cols) { Cell() }
                }
            }
            'M' -> {
                val count = param(0, 1)
                repeat(count) {
                    for (r in cursorRow until rows - 1) {
                        activeGrid[r] = activeGrid[r + 1]
                    }
                    activeGrid[rows - 1] = Array(cols) { Cell() }
                }
            }
            'P' -> {
                val count = param(0, 1)
                for (c in cursorCol until cols - count) {
                    activeGrid[cursorRow][c] = activeGrid[cursorRow][c + count]
                }
                for (c in cols - count until cols) {
                    activeGrid[cursorRow][c] = Cell()
                }
            }
            'm' -> parseSgr(parts)
            'h' -> {
                if (isPrivate) {
                    when (param(0, 0)) {
                        25 -> cursorVisible = true
                        1049, 47 -> {
                            isAlternateScreen = true
                            alternateGrid = Array(rows) { Array(cols) { Cell() } }
                        }
                    }
                }
            }
            'l' -> {
                if (isPrivate) {
                    when (param(0, 0)) {
                        25 -> cursorVisible = false
                        1049, 47 -> {
                            isAlternateScreen = false
                        }
                    }
                }
            }
            's' -> {
                savedCursorRow = cursorRow
                savedCursorCol = cursorCol
            }
            'u' -> {
                cursorRow = savedCursorRow.coerceIn(0, rows - 1)
                cursorCol = savedCursorCol.coerceIn(0, cols - 1)
            }
        }
    }

    private fun parseSgr(parts: List<Int>) {
        if (parts.isEmpty()) {
            resetAttributes()
            return
        }
        var i = 0
        while (i < parts.size) {
            when (val code = parts[i]) {
                0 -> resetAttributes()
                1 -> currentBold = true
                4 -> currentUnderline = true
                7 -> currentReverse = true
                22 -> currentBold = false
                24 -> currentUnderline = false
                27 -> currentReverse = false
                in 30..37 -> currentFgColor = ansiColors[code - 30]
                38 -> {
                    if (i + 2 < parts.size && parts[i + 1] == 5) {
                        currentFgColor = parse256Color(parts[i + 2])
                        i += 2
                    } else if (i + 4 < parts.size && parts[i + 1] == 2) {
                        currentFgColor = Color(parts[i + 2], parts[i + 3], parts[i + 4])
                        i += 4
                    }
                }
                39 -> currentFgColor = Color(0xFF00FF9D)
                in 40..47 -> currentBgColor = ansiColors[code - 40]
                48 -> {
                    if (i + 2 < parts.size && parts[i + 1] == 5) {
                        currentBgColor = parse256Color(parts[i + 2])
                        i += 2
                    } else if (i + 4 < parts.size && parts[i + 1] == 2) {
                        currentBgColor = Color(parts[i + 2], parts[i + 3], parts[i + 4])
                        i += 4
                    }
                }
                49 -> currentBgColor = Color.Transparent
                in 90..97 -> currentFgColor = ansiColors[code - 90 + 8]
                in 100..107 -> currentBgColor = ansiColors[code - 100 + 8]
            }
            i++
        }
    }

    private fun parse256Color(index: Int): Color {
        return when {
            index in 0..15 -> ansiColors[index]
            index in 16..231 -> {
                val n = index - 16
                val r = (n / 36) * 51
                val g = ((n % 36) / 6) * 51
                val b = (n % 6) * 51
                Color(r, g, b)
            }
            index in 232..255 -> {
                val gray = (index - 232) * 10 + 8
                Color(gray, gray, gray)
            }
            else -> Color.White
        }
    }

    private fun resetAttributes() {
        currentFgColor = Color(0xFF00FF9D)
        currentBgColor = Color.Transparent
        currentBold = false
        currentUnderline = false
        currentReverse = false
    }

    private fun buildSnapshot(): TerminalState {
        val grid = activeGrid
        val lines = grid.map { row -> row.toList() }
        return TerminalState(
            lines = lines,
            cursorRow = cursorRow,
            cursorCol = cursorCol,
            cursorVisible = cursorVisible,
            cols = cols,
            rows = rows,
            isAlternateScreen = isAlternateScreen,
            revision = ++revision
        )
    }

    private fun publishState() {
        _stateFlow.value = buildSnapshot()
    }
}
