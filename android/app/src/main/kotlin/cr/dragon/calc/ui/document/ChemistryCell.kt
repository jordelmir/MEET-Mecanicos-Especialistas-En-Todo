package cr.dragon.calc.ui.document

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import cr.dragon.calc.ui.document.AIPromptOverlay
import cr.dragon.calc.ui.document.Cell


/**
 * Celda de Química Atómica [🧪]
 * Visualiza y balancéa reacciones químicas con estética Imperial.
 */
@Composable
fun ChemistryCell(
    cell: Cell,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onContentChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    activeEngineName: String = "DragonBrain",
    onPrompt: (String) -> Unit = {}
) {
    val clipboardController = rememberClipboardController()
    var showContextMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onFocus() },
                    onLongPress = { showContextMenu = true }
                )
            },
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = if (isFocused) ButtonDefaults.outlinedButtonBorder else null
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "MOLECULAR CORE [🧪]",
                    color = cr.dragon.calc.ui.DragonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = cell.content,
                    onValueChange = { onContentChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Fe + O2 -> Fe2O3", color = Color.Gray) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 18.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.DarkGray,
                        focusedBorderColor = cr.dragon.calc.ui.DragonCyan
                    )
                )

                if (cell.result.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = cell.result,
                        color = if (cell.isError) Color.Red else cr.dragon.calc.ui.DragonTeal,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Área de Renderizado VSEPR (Heurística de puntos 2D proyectados)
                    if (!cell.isError) {
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .padding(8.dp)
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                        ) {
                            val parsed = try { dragoncore.chemistry.ChemParser().parseFormula(cell.content.text) } catch(e: Exception) { emptyMap<String, Int>() }
                            val totalAtoms = parsed.values.sum()
                            if (totalAtoms > 0) {
                                var currentAngle = 0.0
                                val radius = 45.dp.toPx()
                                for (entry in parsed) {
                                    val symbol = entry.key
                                    val count = entry.value
                                    repeat(count) {
                                        val x = center.x + radius * Math.cos(currentAngle).toFloat()
                                        val y = center.y + radius * Math.sin(currentAngle).toFloat()
                                        drawCircle(
                                            color = when(symbol) {
                                                "H" -> Color.White
                                                "O" -> Color.Red
                                                "C" -> Color.DarkGray
                                                "N" -> Color.Blue
                                                else -> cr.dragon.calc.ui.DragonCyan
                                            },
                                            radius = 10.dp.toPx(),
                                            center = androidx.compose.ui.geometry.Offset(x, y)
                                        )
                                        currentAngle += (2 * Math.PI) / totalAtoms
                                    }
                                }
                            }
                        }
                    }
                }

                // [V2] AI Explanation
                if (cell.aiExplanation?.isNotEmpty() == true) {
                    Text(
                        text = cell.aiExplanation!!,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(8.dp)
                    )
                }

                // [V2] Integrated Prompt Overlay
                AIPromptOverlay(
                    activeEngineName = activeEngineName,
                    isExecuting = cell.isExecuting,
                    onPrompt = { onPrompt(it) }
                )
            }

            // Context Menu Overlay
            DragonContextMenu(
                isVisible = showContextMenu,
                onCopy = {
                    clipboardController.copy(cell.content.text)
                    showContextMenu = false
                },
                onPaste = {
                    clipboardController.paste()?.let {
                        onContentChange(androidx.compose.ui.text.input.TextFieldValue(it))
                    }
                    showContextMenu = false
                },
                hasPasteContent = clipboardController.hasContent(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
    }
}
