package cr.dragon.calc.ui.document

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.dragon.calc.ui.*
import cr.dragon.calc.graphing.GraphRenderer
import cr.dragon.calc.graphing.GraphViewport
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AIPromptOverlay(
    activeEngineName: String,
    onPrompt: (String) -> Unit,
    isExecuting: Boolean = false
) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .border(1.dp, DragonOrange.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = DragonOrange,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(color = DragonWhite, fontSize = 13.sp),
            cursorBrush = SolidColor(DragonOrange),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text("Pide al Dragon sobre esta celda...", color = DragonGray, fontSize = 13.sp)
                }
                innerTextField()
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (text.isNotBlank()) {
                    onPrompt(text)
                    text = ""
                }
            })
        )

        if (isExecuting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DragonOrange, strokeWidth = 2.dp)
        }
    }
}

/** Color base de la tarjeta Notebook translucida */
val CellBackground = Color(0xFF1F2833).copy(alpha = 0.85f)

@Composable
fun MathCell(
    cell: Cell,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onContentChange: (TextFieldValue) -> Unit,
    activeEngineName: String = "DragonBrain",
    onPrompt: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val borderColor = if (cell.isError) DragonOrange else if (isFocused) DragonGreen else Color.Transparent

    val clipboardController = rememberClipboardController()
    var showContextMenu by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(cell.isError) {
        if (cell.isError) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Expression Input
                val scrollState = rememberScrollState()
                LaunchedEffect(cell.content.text) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                BasicTextField(
                    value = cell.content,
                    onValueChange = { onContentChange(it) },
                    readOnly = true, // Hide Android OS Keyboard, we use DragonKeyboard
                    textStyle = TextStyle(
                        color = if (cell.content.text.isEmpty()) DragonGray else DragonDimWhite,
                        fontSize = 20.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Light,
                    ),
                    cursorBrush = SolidColor(if (isFocused) DragonCyan else Color.Transparent),
                    visualTransformation = DragonSyntaxTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    decorationBox = { innerTextField ->
                        if (cell.content.text.isEmpty() && !isFocused) {
                            Text(
                                text = "...",
                                color = DragonGray,
                                fontSize = 20.sp,
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Light
                            )
                        } else {
                            innerTextField()
                        }
                    }
                )

                // Visual AST Editor Toggle
                if (cell.ast != null && !cell.isError) {
                    var showVisualEditor by remember { mutableStateOf(false) }

                    Text(
                        text = if (showVisualEditor) "OCULTAR EDITOR ESTRUCTURAL" else "ABRIR EDITOR ESTRUCTURAL [💎]",
                        color = if (showVisualEditor) DragonOrange else DragonTeal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = JetBrainsMono,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clickable { showVisualEditor = !showVisualEditor }
                    )

                    AnimatedVisibility(
                        visible = showVisualEditor,
                        enter = expandVertically(spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                        exit = shrinkVertically(spring(stiffness = Spring.StiffnessLow)) + fadeOut()
                    ) {
                        VisualASTEditor(
                            expr = cell.ast!!,
                            onExprChange = { newExpr ->
                                onContentChange(TextFieldValue(newExpr.toDragonScript()))
                            },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }

                // Result Display
                if (cell.result.isNotEmpty()) {
                    var showSteps by remember { mutableStateOf(false) }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (cell.ast != null && !cell.isError) {
                            val latex = dragoncore.cas.LatexTranspiler().transpile(cell.ast)
                            MathView(
                                latex = "= $latex",
                                modifier = Modifier.weight(1f),
                                textColor = DragonCyan,
                                fontSize = 22
                            )
                        } else {
                            Text(
                                text = "= ${cell.result}",
                                color = if (cell.isError) DragonOrange else DragonCyan,
                                fontSize = 22.sp,
                                fontFamily = JetBrainsMono,
                                fontWeight = if (cell.isError) FontWeight.Normal else FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (cell.evaluationSteps.isNotEmpty() && !cell.isError) {
                            Text(
                                text = if (showSteps) "Ocultar" else "Ver Pasos",
                                color = DragonGray,
                                fontSize = 12.sp,
                                fontFamily = JetBrainsMono,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable { showSteps = !showSteps }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Tutor Mode: Paso a Paso
                    AnimatedVisibility(
                        visible = showSteps && cell.evaluationSteps.isNotEmpty(),
                        enter = expandVertically(spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                        exit = shrinkVertically(spring(stiffness = Spring.StiffnessLow)) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.2f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Rastro Simbólico (Tutor Mode):",
                                color = DragonGreen,
                                fontSize = 12.sp,
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            cell.evaluationSteps.forEachIndexed { index, step ->
                                if (step.contains("\\") || step.contains("{")) {
                                    // Si contiene LaTeX, intentamos separar descripción de fórmula
                                    val parts = step.split(":", limit = 2)
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text(
                                            text = "${index + 1}. ${parts[0]}:",
                                            color = DragonGold,
                                            fontSize = 14.sp,
                                            fontFamily = JetBrainsMono
                                        )
                                        if (parts.size > 1) {
                                            MathView(
                                                latex = parts[1].trim(),
                                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                                                textColor = DragonWhite,
                                                fontSize = 16
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "${index + 1}. $step",
                                        color = DragonDimWhite,
                                        fontSize = 14.sp,
                                        fontFamily = JetBrainsMono,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // [V2] AI Explanation & Prompt Overlay
                if (cell.aiExplanation?.isNotEmpty() == true) {
                    Text(
                        text = cell.aiExplanation,
                        color = DragonWhite.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DragonGreen.copy(alpha = 0.05f))
                            .padding(8.dp)
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

@Composable
fun MarkdownCell(
    cell: Cell,
    isFocused: Boolean,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardController = rememberClipboardController()
    var showContextMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onFocus() },
                    onLongPress = { showContextMenu = true }
                )
            },
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = cell.content.text,
                    color = DragonWhite.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Light,
                    lineHeight = 24.sp
                )

                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .height(2.dp)
                            .fillMaxWidth(0.3f)
                            .background(DragonGreen.copy(alpha = 0.5f))
                    )
                }
            }

            // Context Menu Overlay (Copy Only for Markdown)
            DragonContextMenu(
                isVisible = showContextMenu,
                onCopy = {
                    clipboardController.copy(cell.content.text)
                    showContextMenu = false
                },
                onPaste = {
                    showContextMenu = false
                },
                hasPasteContent = false,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun GraphCell(
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

    val clipboardController = rememberClipboardController()
    var showContextMenu by remember { mutableStateOf(false) }

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
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Function Definition Input
                BasicTextField(
                    value = cell.content,
                    onValueChange = onContentChange,
                    readOnly = true,
                    textStyle = TextStyle(
                        color = DragonTeal,
                        fontSize = 18.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(if (isFocused) DragonCyan else Color.Transparent),
                    visualTransformation = DragonSyntaxTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("f(x) = ", color = DragonGray, fontSize = 18.sp, fontFamily = JetBrainsMono)
                            innerTextField()
                        }
                    }
                )

                // Graph Renderer Mini Canvas
                if (cell.ast != null && !cell.isError) {
                    val viewport = remember { GraphViewport() }
                    var size by remember { mutableStateOf(IntSize.Zero) }
                    var graphPath by remember { mutableStateOf(Path()) }

                    LaunchedEffect(cell.ast, viewport.xMin, viewport.xMax, viewport.yMin, viewport.yMax, size) {
                        if (size.width > 0 && size.height > 0) {
                            withContext(Dispatchers.Default) {
                                graphPath = GraphRenderer.buildGraphPath(
                                    expr = cell.ast!!,
                                    viewport = viewport,
                                    screenWidth = size.width.toFloat(),
                                    screenHeight = size.height.toFloat(),
                                    evaluator = evaluator,
                                    context = context
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF15181E))
                            .onSizeChanged { size = it }
                            .pointerInput(Unit) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    viewport.applyZoom(zoom, centroid.x, centroid.y, size.width.toFloat(), size.height.toFloat())
                                    viewport.applyPan(pan.x, pan.y, size.width.toFloat(), size.height.toFloat())
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Axes
                            val xAxisY = viewport.toPixelY(0.0, size.height.toFloat())
                            if (xAxisY in 0f..size.height.toFloat()) {
                                drawLine(Color.DarkGray, Offset(0f, xAxisY), Offset(size.width.toFloat(), xAxisY), 2f)
                            }
                            val yAxisX = viewport.toPixelX(0.0, size.width.toFloat())
                            if (yAxisX in 0f..size.width.toFloat()) {
                                drawLine(Color.DarkGray, Offset(yAxisX, 0f), Offset(yAxisX, size.height.toFloat()), 2f)
                            }
                            // Path
                            drawPath(path = graphPath, color = DragonCyan, style = Stroke(width = 3f))
                        }
                    }
                } else if (cell.isError) {
                    Text(
                        text = cell.result,
                        color = DragonOrange,
                        fontSize = 14.sp,
                        fontFamily = JetBrainsMono
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

class DragonSyntaxTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val inputText = text.text
        val builder = AnnotatedString.Builder()

        // DragonScript Keywords & Symbols
        val keywords = setOf("def", "if", "else", "while", "for", "return", "in", "and", "or", "not", "true", "false", "nil")
        val operators = setOf("+", "-", "*", "/", "%", "^", "==", "!=", "<", ">", "<=", ">=", "=", "&&", "||", "!")

        // Use regex for simple tokenization
        val tokens = Regex("""(\b\w+\b|[^\w\s]|\s+)""").findAll(inputText)

        for (token in tokens) {
            val value = token.value
            when {
                value in keywords -> {
                    builder.pushStyle(SpanStyle(color = cr.dragon.calc.ui.DragonCyan, fontWeight = FontWeight.Bold))
                    builder.append(value)
                    builder.pop()
                }
                value in operators -> {
                    builder.pushStyle(SpanStyle(color = cr.dragon.calc.ui.DragonTeal))
                    builder.append(value)
                    builder.pop()
                }
                value.toDoubleOrNull() != null -> {
                    builder.pushStyle(SpanStyle(color = cr.dragon.calc.ui.DragonOrange))
                    builder.append(value)
                    builder.pop()
                }
                else -> {
                    builder.append(value)
                }
            }
        }

        // Fallback for unexpected regex gaps
        val result = if (builder.length == inputText.length) builder.toAnnotatedString() else text
        return TransformedText(result, OffsetMapping.Identity)
    }
}

@Composable
fun StatsCell(
    cell: Cell,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onContentChange: (TextFieldValue) -> Unit,
    activeEngineName: String = "DragonBrain",
    onPrompt: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val borderColor = if (cell.isError) DragonOrange else if (isFocused) DragonTeal else Color.Transparent

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
            Text(
                text = "Data Explorer [📉]",
                color = DragonTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetBrainsMono
            )

            // [V2] AI Explanation
            if (cell.aiExplanation?.isNotEmpty() == true) {
                Text(
                    text = cell.aiExplanation!!,
                    color = DragonWhite.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DragonGreen.copy(alpha = 0.05f))
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            BasicTextField(
                value = cell.content,
                onValueChange = onContentChange,
                readOnly = true,
                textStyle = TextStyle(
                    color = DragonWhite,
                    fontSize = 14.sp,
                    fontFamily = JetBrainsMono
                ),
                cursorBrush = SolidColor(DragonCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            )

            if (cell.result.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = cell.result,
                    color = DragonCyan,
                    fontSize = 15.sp,
                    fontFamily = JetBrainsMono,
                    lineHeight = 20.sp
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
}
