package cr.dragon.calc.ui.document

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.dragon.calc.graphing.Graph3DViewport
import cr.dragon.calc.graphing.SurfaceRenderer
import cr.dragon.calc.ui.*
import cr.dragon.calc.ui.document.AIPromptOverlay
import cr.dragon.calc.ui.document.Cell
import cr.dragon.calc.ui.document.CellBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator

/**
 * Celda 3D interactiva para DragonDocs.
 * Renderiza superficies z = f(x, y) con rotación por arrastre y zoom por pellizco.
 */
@Composable
fun Graph3DCell(
    cell: Cell,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onContentChange: (TextFieldValue) -> Unit,
    context: DragonContext,
    evaluator: DragonEvaluator,
    activeEngineName: String = "DragonBrain",
    onPrompt: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val borderColor = if (cell.isError) DragonOrange else if (isFocused) DragonGreen else Color.Transparent

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onFocus() },
        color = CellBackground,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Expresión de entrada: z = f(x, y)
            BasicTextField(
                value = cell.content,
                onValueChange = onContentChange,
                readOnly = false,
                textStyle = TextStyle(
                    color = DragonCyan,
                    fontSize = 18.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(if (isFocused) DragonCyan else Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                decorationBox = { innerTextField ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        @Suppress("LocalVariableName")
                        Text("z = ", color = DragonGray, fontSize = 18.sp, fontFamily = JetBrainsMono)
                        innerTextField()
                    }
                }
            )

            // Canvas 3D interactivo
            if (cell.ast != null && !cell.isError) {
                val viewport = remember { Graph3DViewport() }
                var size by remember { mutableStateOf(IntSize.Zero) }
                var quads by remember { mutableStateOf(emptyList<SurfaceRenderer.ProjectedQuad>()) }

                LaunchedEffect(cell.ast, viewport.yaw, viewport.pitch, viewport.scale, viewport.distance, size) {
                    if (size.width > 0 && size.height > 0) {
                        withContext(Dispatchers.Default) {
                            val localCtx = DragonContext().apply { angleMode = context.angleMode }
                            quads = SurfaceRenderer.buildSurface(
                                expr = cell.ast!!,
                                viewport = viewport,
                                evaluator = evaluator,
                                context = localCtx,
                                screenW = size.width.toFloat(),
                                screenH = size.height.toFloat(),
                                gridSteps = 35
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0D0F14))
                        .onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                viewport.applyRotation(dYaw = -pan.x * 0.3f, dPitch = pan.y * 0.3f)
                                if (zoom != 1f) viewport.applyZoom(zoom)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (quad in quads) {
                            if (quad.points.size < 4) continue
                            val path = Path().apply {
                                moveTo(quad.points[0].x, quad.points[0].y)
                                lineTo(quad.points[1].x, quad.points[1].y)
                                lineTo(quad.points[2].x, quad.points[2].y)
                                lineTo(quad.points[3].x, quad.points[3].y)
                                close()
                            }
                            drawPath(path = path, color = quad.fillColor, style = Fill)
                            drawPath(path = path, color = quad.strokeColor, style = Stroke(width = 0.5f))
                        }

                        val vp = viewport
                        val axisLen = 3.0
                        val origin = vp.project(0.0, 0.0, 0.0, this.size.width, this.size.height)
                        val xEnd = vp.project(axisLen, 0.0, 0.0, this.size.width, this.size.height)
                        val yEnd = vp.project(0.0, axisLen, 0.0, this.size.width, this.size.height)
                        val zEnd = vp.project(0.0, 0.0, axisLen, this.size.width, this.size.height)

                        if (origin != null) {
                            val o = androidx.compose.ui.geometry.Offset(origin.first, origin.second)
                            xEnd?.let { drawLine(Color(0xAAFF5252), o, androidx.compose.ui.geometry.Offset(it.first, it.second), 2f) }
                            yEnd?.let { drawLine(Color(0xAA69F0AE), o, androidx.compose.ui.geometry.Offset(it.first, it.second), 2f) }
                            zEnd?.let { drawLine(Color(0xAA448AFF), o, androidx.compose.ui.geometry.Offset(it.first, it.second), 2f) }
                        }
                    }

                    Text(
                        text = "yaw:${viewport.yaw.toInt()}° pitch:${viewport.pitch.toInt()}°",
                        color = DragonGray,
                        fontSize = 10.sp,
                        fontFamily = JetBrainsMono,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    )
                }
            } else if (cell.content.text.isNotEmpty()) {
                Text(
                    text = if (cell.isError) cell.result else "Enter z = f(x, y)",
                    color = if (cell.isError) DragonOrange else DragonGray,
                    fontSize = 14.sp,
                    fontFamily = JetBrainsMono
                )
            }

            if (cell.aiExplanation?.isNotEmpty() == true) {
                Text(
                    text = cell.aiExplanation!!,
                    color = DragonWhite.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).background(DragonGreen.copy(alpha = 0.05f)).padding(8.dp)
                )
            }

            AIPromptOverlay(
                activeEngineName = activeEngineName,
                isExecuting = cell.isExecuting,
                onPrompt = { onPrompt(it) }
            )
        }
    }
}
