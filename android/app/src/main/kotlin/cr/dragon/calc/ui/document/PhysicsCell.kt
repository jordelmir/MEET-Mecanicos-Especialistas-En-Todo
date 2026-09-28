package cr.dragon.calc.ui.document

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.dragon.calc.ui.document.AIPromptOverlay
import cr.dragon.calc.ui.document.Cell
import cr.dragon.calc.ui.document.CellBackground
import cr.dragon.calc.ui.DragonBlack
import cr.dragon.calc.ui.DragonCyan
import cr.dragon.calc.ui.DragonGray
import cr.dragon.calc.ui.DragonDimWhite
import cr.dragon.calc.ui.DragonGreen
import cr.dragon.calc.ui.DragonOrange
import cr.dragon.calc.ui.DragonTeal
import cr.dragon.calc.ui.DragonWhite
import cr.dragon.calc.ui.JetBrainsMono
import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator
import dragoncore.physics.DragonPhysicsEngine
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.cos
import kotlin.math.sin

/**
 * Elysium Vanguard — PhysicsCell
 *
 * Interfaz reactiva para el Laboratorio de Física [⚛️].
 * Renderiza trayectorias y estados de sistemas dinámicos resueltos por el ODESolver.
 */
@Composable
fun PhysicsCell(
    cell: Cell,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onContentChange: (TextFieldValue) -> Unit,
    context: DragonContext,
    engine: DragonPhysicsEngine,
    onStateUpdate: (Map<String, Double>) -> Unit,
    activeEngineName: String = "DragonBrain",
    onPrompt: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val borderColor = if (cell.isError) DragonOrange else if (isFocused) DragonGreen else Color.Transparent
    var isRunning by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val clipboardController = rememberClipboardController()
    var showContextMenu by remember { mutableStateOf(false) }

    // Efecto de Simulación: Lanza el Flow del engine cuando isRunning es true
    LaunchedEffect(isRunning, cell.content.text) {
        if (isRunning) {
            val initialState = mapOf(
                "theta" to (cell.physicsState["theta"] ?: 1.0),
                "omega" to (cell.physicsState["omega"] ?: 0.0)
            )

            try {
                val lexer = dragoncore.lexer.DragonLexer()
                val system = mapOf(
                    "theta" to dragoncore.parser.DragonParser(lexer.tokenize("omega")).parse(),
                    "omega" to dragoncore.parser.DragonParser(lexer.tokenize("-9.8/L * sin(theta)")).parse()
                )

                engine.startSimulation(initialState, system, context).collectLatest { newState ->
                    onStateUpdate(newState)
                }
            } catch (e: Exception) {
                isRunning = false
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onFocus() },
                    onLongPress = { showContextMenu = true }
                )
            },
        color = CellBackground,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header & Play Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Real-Time Physics [⚛️]",
                            color = DragonTeal,
                            fontSize = 14.sp,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "RK4 Dynamic Solver",
                            color = DragonGray,
                            fontSize = 11.sp,
                            fontFamily = JetBrainsMono
                        )
                    }

                    Surface(
                        color = if (isRunning) DragonOrange.copy(alpha = 0.2f) else DragonGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { isRunning = !isRunning }
                    ) {
                        Text(
                            text = if (isRunning) "STOP" else "SIMULATE",
                            color = if (isRunning) DragonOrange else DragonGreen,
                            fontSize = 12.sp,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ODE Definition Input
                BasicTextField(
                    value = cell.content,
                    onValueChange = onContentChange,
                    readOnly = true,
                    textStyle = TextStyle(
                        color = DragonDimWhite,
                        fontSize = 16.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Light
                    ),
                    cursorBrush = SolidColor(DragonCyan),
                    visualTransformation = DragonSyntaxTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (cell.content.text.isEmpty()) {
                            Column {
                                Text("# Pendulum Example:", color = DragonTeal, fontSize = 12.sp, fontFamily = JetBrainsMono)
                                Text("theta' = omega\nomega' = -9.8/L * sin(theta)", color = DragonGray, fontSize = 14.sp, fontFamily = JetBrainsMono)
                            }
                        }
                        inner()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Simulation Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D1217))
                        .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .onSizeChanged { size = it }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (size.width > 0 && size.height > 0) {
                            val centerX = size.width / 2f
                            val centerY = 60f
                            val theta = cell.physicsState["theta"] ?: 0.0
                            val l = 160f

                            val bobX = centerX + l * sin(theta).toFloat()
                            val bobY = centerY + l * cos(theta).toFloat()

                            drawLine(
                                color = DragonWhite.copy(alpha = 0.4f),
                                start = Offset(centerX, centerY),
                                end = Offset(bobX, bobY),
                                strokeWidth = 2f
                            )
                            drawCircle(color = Color.DarkGray, radius = 6f, center = Offset(centerX, centerY))
                            drawCircle(color = DragonCyan.copy(alpha = 0.2f), radius = 22f, center = Offset(bobX, bobY))
                            drawCircle(color = DragonCyan, radius = 16f, center = Offset(bobX, bobY))
                            drawCircle(color = Color.White.copy(alpha = 0.5f), radius = 8f, center = Offset(bobX - 4, bobY - 4))
                        }
                    }

                    // [V2] AI Explanation
                    if (cell.aiExplanation?.isNotEmpty() == true) {
                        Text(
                            text = cell.aiExplanation!!,
                            color = DragonWhite.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DragonGreen.copy(alpha = 0.05f))
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
                        onContentChange(TextFieldValue(it))
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
