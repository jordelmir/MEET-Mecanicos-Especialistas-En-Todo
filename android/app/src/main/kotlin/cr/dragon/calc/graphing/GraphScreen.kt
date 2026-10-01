package cr.dragon.calc.graphing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.dragon.calc.ui.CalculatorViewModel
import dragoncore.lexer.DragonLexer
import dragoncore.parser.DragonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val AxisColor = Color(0xFF1F2833)
val GridColor = Color(0x331F2833)
val DragonGreen = Color(0xFF66FCF1)

@Composable
fun GraphScreen(
    onBackToCalc: () -> Unit,
    viewModel: CalculatorViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusedCell = state.document.cells.find { it.id == state.focusedCellId }
    val expressionStr = focusedCell?.content?.text ?: ""

    // Parse expression once when it changes
    val expr = remember(expressionStr) {
        try {
            if (expressionStr.isNotBlank()) {
                val tokens = DragonLexer().tokenize(expressionStr)
                DragonParser(tokens).parse()
            } else null
        } catch(e: Exception) {
            null
        }
    }

    val viewport = remember { GraphViewport() }
    val context = viewModel.dragonContext
    val evaluator = viewModel.evaluator
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Evaluated path caching
    var graphPath by remember { mutableStateOf(Path()) }

    // Recalculate the path when viewport boundaries, canvas size or expression change
    LaunchedEffect(expr, viewport.xMin, viewport.xMax, viewport.yMin, viewport.yMax, size) {
        if (size.width > 0 && size.height > 0 && expr != null) {
            withContext(Dispatchers.Default) {
                val newPath = GraphRenderer.buildGraphPath(
                    expr = expr,
                    viewport = viewport,
                    screenWidth = size.width.toFloat(),
                    screenHeight = size.height.toFloat(),
                    evaluator = evaluator,
                    context = context
                )
                graphPath = newPath
            }
        } else {
            // Empy path when invalid
            graphPath = Path()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    // Apply inverse pan
                    viewport.applyPan(
                        dxPixel = -pan.x,
                        dyPixel = -pan.y,
                        screenWidth = size.width.toFloat(),
                        screenHeight = size.height.toFloat()
                    )
                    viewport.applyZoom(
                        scale = zoom,
                        focusXPixel = centroid.x,
                        focusYPixel = centroid.y,
                        screenWidth = size.width.toFloat(),
                        screenHeight = size.height.toFloat()
                    )
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            if (width <= 0f || height <= 0f) return@Canvas

            // Draw Grid
            // X lines ( vertical lines )
            val xStart = Math.floor(viewport.xMin).toInt()
            val xEnd = Math.ceil(viewport.xMax).toInt()
            for (x in xStart..xEnd) {
                val px = viewport.toPixelX(x.toDouble(), width)
                drawLine(
                    color = if (x == 0) AxisColor else GridColor,
                    start = Offset(px, 0f),
                    end = Offset(px, height),
                    strokeWidth = if (x == 0) 4f else 2f
                )
            }

            // Y lines ( horizontal lines )
            val yStart = Math.floor(viewport.yMin).toInt()
            val yEnd = Math.ceil(viewport.yMax).toInt()
            for (y in yStart..yEnd) {
                val py = viewport.toPixelY(y.toDouble(), height)
                drawLine(
                    color = if (y == 0) AxisColor else GridColor,
                    start = Offset(0f, py),
                    end = Offset(width, py),
                    strokeWidth = if (y == 0) 4f else 2f
                )
            }

            // Draw Curve
            drawPath(
                path = graphPath,
                color = DragonGreen,
                style = Stroke(width = 4f)
            )
        }

        // HUD: Top Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBackToCalc() },
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "◀ Back",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            // Viewport Info
            val hudText by remember {
                derivedStateOf {
                    "X: [%.1f, %.1f] | Y: [%.1f, %.1f]".format(
                        viewport.xMin, viewport.xMax,
                        viewport.yMin, viewport.yMax
                    )
                }
            }

            Surface(
                color = Color.White.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = hudText,
                    color = Color.Black,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
