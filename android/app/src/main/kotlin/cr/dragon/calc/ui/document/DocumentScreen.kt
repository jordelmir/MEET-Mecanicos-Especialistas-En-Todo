package cr.dragon.calc.ui.document

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.dragon.calc.export.PdfExporter
import cr.dragon.calc.export.PdfExportManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cr.dragon.calc.ui.*
import cr.dragon.calc.ui.components.MasterCommandBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    viewModel: CalculatorViewModel = hiltViewModel(),
    onOpenGraph: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showVisionScreen by remember { mutableStateOf(false) }
    var showNeuralSettings by remember { mutableStateOf(false) }
    var showOcrEditDialog by remember { mutableStateOf(false) }
    var ocrScannedText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNewBookDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var bookToRename by remember { mutableStateOf<Pair<String, String>?>(null) }
    var newBookTitle by remember { mutableStateOf("") }

    // API Validation State
    val apiValidationState by viewModel.apiValidationState.collectAsStateWithLifecycle()
    var showSuccessOverlay by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new cell is added
    LaunchedEffect(state.document.cells.size) {
        if (state.document.cells.isNotEmpty()) {
            listState.animateScrollToItem(state.document.cells.lastIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            BookshelfDrawerContent(
                books = books,
                currentBookId = state.document.id,
                onLoadBook = { viewModel.loadBook(it) },
                onDeleteBook = { viewModel.deleteBook(it) },
                onRenameBook = { id, title ->
                    bookToRename = id to title
                    newBookTitle = title
                    showRenameDialog = true
                },
                onNewBook = { showNewBookDialog = true },
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            containerColor = DragonBlack,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = DragonCyan)
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = state.document.title,
                                color = DragonWhite,
                                fontSize = 16.sp,
                                fontFamily = JetBrainsMono
                            )
                            Text(
                                text = "Engine: ${viewModel.activeEngineName}",
                                color = DragonGreen,
                                fontSize = 10.sp,
                                fontFamily = JetBrainsMono
                            )
                        }
                    },
                    actions = {
                        var exporting by remember { mutableStateOf(false) }

                        if (exporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(end = 16.dp),
                                color = DragonGreen,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { showNeuralSettings = true }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Portal Neural",
                                    tint = if (viewModel.activeEngineName.contains("Gemini")) DragonGreen else DragonCyan
                                )
                            }

                            var showShareMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showShareMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "Share",
                                        tint = DragonCyan
                                    )
                                }
                                DropdownMenu(
                                    expanded = showShareMenu,
                                    onDismissRequest = { showShareMenu = false },
                                    modifier = Modifier.background(DragonDarkGray)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Compartir como PDF", color = DragonWhite) },
                                        onClick = {
                                            showShareMenu = false
                                            exporting = true
                                            scope.launch {
                                                try {
                                                    val pdfFile = PdfExporter.generatePdf(
                                                        context,
                                                        state.document.cells,
                                                        emptyMap()
                                                    )
                                                    PdfExportManager.sharePdf(context, pdfFile)
                                                } catch (_: Exception) {
                                                } finally {
                                                    exporting = false
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Compartir como Web (HTML) [🌐]", color = DragonGreen) },
                                        onClick = {
                                            showShareMenu = false
                                            viewModel.shareAsHtml()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Compartir DragonBook (.drbk)", color = DragonCyan) },
                                        onClick = {
                                            showShareMenu = false
                                            viewModel.shareBook()
                                        }
                                    )
                                }
                            }
                        }
                        var showAddCellMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showAddCellMenu = true }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Añadir Celda", tint = DragonCyan)
                            }
                            DropdownMenu(
                                expanded = showAddCellMenu,
                                onDismissRequest = { showAddCellMenu = false },
                                modifier = Modifier.background(DragonDarkGray)
                            ) {
                                DropdownMenuItem(text = { Text("Math Cell", color = DragonWhite, fontFamily = JetBrainsMono) }, onClick = { viewModel.addCell(CellType.MATH); showAddCellMenu = false })
                                DropdownMenuItem(text = { Text("Graph 2D", color = DragonTeal, fontFamily = JetBrainsMono) }, onClick = { viewModel.addCell(CellType.GRAPH); showAddCellMenu = false })
                                DropdownMenuItem(text = { Text("Surface 3D", color = DragonCyan, fontFamily = JetBrainsMono) }, onClick = { viewModel.addCell(CellType.GRAPH_3D); showAddCellMenu = false })
                                DropdownMenuItem(text = { Text("Neural (Prompt)", color = DragonGreen, fontFamily = JetBrainsMono) }, onClick = { viewModel.addCell(CellType.PROMPT); showAddCellMenu = false })
                                DropdownMenuItem(text = { Text("Scan (OCR)", color = DragonOrange, fontFamily = JetBrainsMono) }, onClick = { showVisionScreen = true; showAddCellMenu = false })
                                DropdownMenuItem(text = { Text("Physics [⚛️]", color = DragonTeal, fontFamily = JetBrainsMono) }, onClick = { viewModel.addCell(CellType.PHYSICS); showAddCellMenu = false })
                                DropdownMenuItem(text = { Text("Chemistry Lab [🧪]", color = DragonCyan, fontFamily = JetBrainsMono) }, onClick = { viewModel.addCell(CellType.CHEMISTRY); showAddCellMenu = false })
                                DropdownMenuItem(text = { Text("Stats & Data [📊]", color = DragonGreen, fontFamily = JetBrainsMono) }, onClick = { viewModel.addCell(CellType.STATISTICS); showAddCellMenu = false })
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.toggleAngleMode() },
                            color = DragonMidGray,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = state.angleMode.name.take(3),
                                color = DragonCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DragonBlack)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 🧪 PROBABILITY & SCIENCE BRIDGE [RESTORED]
                val categories = listOf(
                    "Math" to CellType.MATH,
                    "Graph 2D" to CellType.GRAPH,
                    "Surface 3D" to CellType.GRAPH_3D,
                    "Physics" to CellType.PHYSICS,
                    "Chemistry" to CellType.CHEMISTRY,
                    "Stats" to CellType.STATISTICS,
                    "Neural" to CellType.PROMPT
                )

                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DragonBlack)
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                ) {
                    items(categories.size) { index ->
                        val (label, type) = categories[index]
                        Surface(
                            modifier = Modifier.clickable { viewModel.addCell(type) },
                            color = DragonDarkGray,
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DragonCyan.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = when(type) {
                                    CellType.PHYSICS -> Icons.Default.Science
                                    CellType.CHEMISTRY -> Icons.Default.Science
                                    CellType.STATISTICS -> Icons.Default.BarChart
                                    CellType.GRAPH -> Icons.Default.Timeline
                                    CellType.GRAPH_3D -> Icons.Default.Layers
                                    CellType.PROMPT -> Icons.Default.AutoAwesome
                                    else -> Icons.Default.Calculate
                                }
                                Icon(icon, contentDescription = null, tint = DragonCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(label, color = DragonWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(state.document.cells, key = { it.id }) { cell ->
                        val isFocused = state.focusedCellId == cell.id

                        when (cell.type) {
                            CellType.MATH -> {
                                MathCell(
                                    cell = cell,
                                    isFocused = isFocused,
                                    onFocus = { viewModel.onFocusCell(cell.id) },
                                    onContentChange = { viewModel.onExpressionChange(it, cell.id) },
                                    activeEngineName = viewModel.activeEngineName,
                                    onPrompt = { viewModel.onPromptSubmitted(cell.id, it) }
                                )
                            }
                            CellType.GRAPH -> {
                                GraphCell(
                                    cell = cell,
                                    isFocused = isFocused,
                                    onFocus = { viewModel.onFocusCell(cell.id) },
                                    onContentChange = { viewModel.onExpressionChange(it, cell.id) },
                                    context = viewModel.dragonContextTemplate.apply { angleMode = state.angleMode },
                                    evaluator = viewModel.evaluator,
                                    activeEngineName = viewModel.activeEngineName,
                                    onPrompt = { viewModel.onPromptSubmitted(cell.id, it) }
                                )
                            }
                            CellType.GRAPH_3D -> {
                                Graph3DCell(
                                    cell = cell,
                                    isFocused = isFocused,
                                    onFocus = { viewModel.onFocusCell(cell.id) },
                                    onContentChange = { viewModel.onExpressionChange(it, cell.id) },
                                    context = viewModel.dragonContextTemplate.apply { angleMode = state.angleMode },
                                    evaluator = viewModel.evaluator,
                                    activeEngineName = viewModel.activeEngineName,
                                    onPrompt = { viewModel.onPromptSubmitted(cell.id, it) }
                                )
                            }
                            CellType.MARKDOWN -> {
                                MarkdownCell(
                                    cell = cell,
                                    isFocused = isFocused,
                                    onFocus = { viewModel.onFocusCell(cell.id) }
                                )
                            }
                            CellType.PROMPT -> {
                                PromptCellView(
                                    cell = cell,
                                    activeEngineName = viewModel.activeEngineName,
                                    onValueChange = { updatedCell -> viewModel.onExpressionChange(updatedCell.content, cell.id) },
                                    onSubmit = { submittedCell ->
                                        viewModel.processNeuralIntent(submittedCell, submittedCell.content.text)
                                        viewModel.onPromptSubmitted(submittedCell.id, submittedCell.content.text)
                                    }
                                )
                            }
                            CellType.PHYSICS -> {
                                PhysicsCell(
                                    cell = cell,
                                    isFocused = isFocused,
                                    onFocus = { viewModel.onFocusCell(cell.id) },
                                    onContentChange = { viewModel.onExpressionChange(it, cell.id) },
                                    context = viewModel.dragonContextTemplate.apply { angleMode = state.angleMode },
                                    engine = viewModel.physicsEngine,
                                    onStateUpdate = { newState -> viewModel.onPhysicsStateUpdate(cell.id, newState) },
                                    activeEngineName = viewModel.activeEngineName,
                                    onPrompt = { viewModel.onPromptSubmitted(cell.id, it) }
                                )
                            }
                            CellType.CHEMISTRY -> {
                                ChemistryCell(
                                    cell = cell,
                                    isFocused = isFocused,
                                    onFocus = { viewModel.onFocusCell(cell.id) },
                                    onContentChange = { viewModel.onExpressionChange(it, cell.id) },
                                    activeEngineName = viewModel.activeEngineName,
                                    onPrompt = { viewModel.onPromptSubmitted(cell.id, it) }
                                )
                            }
                            CellType.STATISTICS -> {
                                StatsCell(
                                    cell = cell,
                                    isFocused = isFocused,
                                    onFocus = { viewModel.onFocusCell(cell.id) },
                                    onContentChange = { viewModel.onExpressionChange(it, cell.id) },
                                    activeEngineName = viewModel.activeEngineName,
                                    onPrompt = { viewModel.onPromptSubmitted(cell.id, it) }
                                )
                            }
                            CellType.DATAFRAME -> {
                                // Fallback to Markdown or future DataFrame component
                                MarkdownCell(
                                    cell = cell,
                                    isFocused = isFocused,
                                    onFocus = { viewModel.onFocusCell(cell.id) }
                                )
                            }
                        }
                    }
                }

                MasterCommandBar(
                    onSendPrompt = { viewModel.sendGlobalPrompt(it) },
                    onCameraClick = { showVisionScreen = true },
                    onGalleryClick = { viewModel.shareAsHtml() }, // Optimized share action
                    onDrawClick = { /* Placeholder for drawing */ },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }
        }
    }

    if (showNewBookDialog) {
        var newTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewBookDialog = false },
            title = { Text("Nuevo DragonBook", color = DragonGreen) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Título de la hoja", color = DragonGray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = DragonWhite, unfocusedTextColor = DragonWhite, focusedBorderColor = DragonGreen)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNewBook(if (newTitle.isBlank()) "Untitled Book" else newTitle)
                        showNewBookDialog = false
                        scope.launch { drawerState.close() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DragonGreen)
                ) { Text("Crear", color = DragonBlack) }
            },
            dismissButton = {
                TextButton(onClick = { showNewBookDialog = false }) { Text("Cancelar", color = DragonWhite) }
            },
            containerColor = DragonDarkGray
        )
    }

    if (showNeuralSettings) {
        var tempKey by remember { mutableStateOf(viewModel.currentApiKey) }
        var tempOpenAiKey by remember { mutableStateOf(viewModel.getSavedKey("openai")) }
        var tempAnthropicKey by remember { mutableStateOf(viewModel.getSavedKey("anthropic")) }

        val apiValidationState by viewModel.apiValidationState.collectAsState()
        val isValidating = apiValidationState is CalculatorViewModel.ApiValidationState.Validating
        val validationError = (apiValidationState as? CalculatorViewModel.ApiValidationState.Error)?.message
        val shakeOffset = remember { Animatable(0f) }

        LaunchedEffect(validationError) {
            if (validationError != null) {
                repeat(4) {
                    shakeOffset.animateTo(12f, tween(40))
                    shakeOffset.animateTo(-12f, tween(40))
                }
                shakeOffset.animateTo(0f, tween(40))
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isValidating) { showNeuralSettings = false; viewModel.resetValidationState() } },
            title = { Text("Portal Neural V7 [🌌]", color = DragonGreen, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) },
            text = {
                Box {
                    Column(modifier = Modifier.graphicsLayer { translationX = shakeOffset.value }.verticalScroll(rememberScrollState())) {
                        Text("Configura tu motor de inteligencia DragonBrain. El uso de la nube requiere tu propia API Key (BYOK) para máxima privacidad.", color = DragonWhite.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))

                        OutlinedTextField(
                            value = tempKey,
                            onValueChange = { tempKey = it; viewModel.resetValidationState() },
                            label = { Text("Gemini API Key (Principal)", color = DragonGray) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            textStyle = TextStyle(color = DragonWhite, fontFamily = JetBrainsMono),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            isError = validationError != null,
                            enabled = !isValidating,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DragonGreen, unfocusedBorderColor = DragonGray)
                        )

                        OutlinedTextField(
                            value = tempOpenAiKey,
                            onValueChange = { tempOpenAiKey = it },
                            label = { Text("OpenAI API Key (Opcional)", color = DragonGray) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            textStyle = TextStyle(color = DragonWhite, fontFamily = JetBrainsMono),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !isValidating,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DragonCyan, unfocusedBorderColor = DragonGray)
                        )

                        OutlinedTextField(
                            value = tempAnthropicKey,
                            onValueChange = { tempAnthropicKey = it },
                            label = { Text("Anthropic API Key (Opcional)", color = DragonGray) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = DragonWhite, fontFamily = JetBrainsMono),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !isValidating,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DragonOrange, unfocusedBorderColor = DragonGray)
                        )

                        if (validationError != null) {
                            Text(text = validationError, color = DragonOrange, fontSize = 11.sp, fontFamily = JetBrainsMono, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    if (showSuccessOverlay) {
                        Box(modifier = Modifier.matchParentSize().background(DragonBlack.copy(alpha = 0.8f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DragonGreen, modifier = Modifier.size(64.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.validateAndApplyKey(tempKey, tempOpenAiKey, tempAnthropicKey) },
                    enabled = !isValidating && tempKey.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = DragonGreen)
                ) {
                    if (isValidating) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DragonBlack) else Text("Aplicar", color = DragonBlack)
                }
            },
            dismissButton = {
                if (!isValidating) {
                    TextButton(onClick = { showNeuralSettings = false; viewModel.resetValidationState() }) { Text("Cerrar", color = DragonWhite) }
                }
            },
            containerColor = DragonDarkGray,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Borrar Historial?", color = Color.Red) },
            text = { Text("Se eliminarán todas las celdas de este libro.", color = DragonWhite) },
            confirmButton = {
                Button(onClick = { viewModel.deleteAllCells(); showDeleteDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Borrar", color = DragonWhite) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar", color = DragonWhite) }
            },
            containerColor = DragonDarkGray
        )
    }

    if (showVisionScreen) {
        cr.dragon.calc.ui.vision.VisionScreen(
            onDismiss = { showVisionScreen = false },
            onResult = { scannedText ->
                showVisionScreen = false
                ocrScannedText = scannedText
                showOcrEditDialog = true
            }
        )
    }

    if (showOcrEditDialog) {
        AlertDialog(
            onDismissRequest = { showOcrEditDialog = false },
            title = { Text("OCR Scan Result", color = DragonGreen) },
            text = {
                OutlinedTextField(value = ocrScannedText, onValueChange = { ocrScannedText = it }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = DragonWhite, unfocusedTextColor = DragonWhite))
            },
            confirmButton = {
                Button(onClick = { showOcrEditDialog = false; viewModel.onScanResult(ocrScannedText) }, colors = ButtonDefaults.buttonColors(containerColor = DragonGreen)) { Text("Insert", color = DragonBlack) }
            },
            dismissButton = {
                TextButton(onClick = { showOcrEditDialog = false }) { Text("Cancel", color = DragonWhite) }
            },
            containerColor = DragonDarkGray
        )
    }

    if (showNewBookDialog) {
        var newTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewBookDialog = false },
            title = { Text("Nuevo DragonBook 📚", color = DragonCyan, fontFamily = JetBrainsMono) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Título del Libro", color = DragonGray) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = DragonWhite, fontFamily = JetBrainsMono),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DragonCyan, unfocusedBorderColor = DragonGray)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            viewModel.createNewBook(newTitle)
                            showNewBookDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DragonCyan)
                ) { Text("Crear", color = DragonBlack) }
            },
            dismissButton = {
                TextButton(onClick = { showNewBookDialog = false }) { Text("Cancelar", color = DragonWhite) }
            },
            containerColor = DragonDarkGray
        )
    }

    if (showRenameDialog && bookToRename != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renombrar 📚", color = DragonCyan, fontFamily = JetBrainsMono) },
            text = {
                OutlinedTextField(
                    value = newBookTitle,
                    onValueChange = { newBookTitle = it },
                    label = { Text("Nuevo Título", color = DragonGray) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = DragonWhite, fontFamily = JetBrainsMono),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DragonCyan, unfocusedBorderColor = DragonGray)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newBookTitle.isNotBlank()) {
                            viewModel.renameBook(bookToRename!!.first, newBookTitle)
                            showRenameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DragonCyan)
                ) { Text("Guardar", color = DragonBlack) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancelar", color = DragonWhite) }
            },
            containerColor = DragonDarkGray
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfDrawerContent(
    books: List<DragonDocument>,
    currentBookId: String,
    onLoadBook: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onRenameBook: (String, String) -> Unit,
    onNewBook: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredBooks = books.filter { it.title.contains(searchQuery, ignoreCase = true) }

    ModalDrawerSheet {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📚 Dragon Bookshelf",
                        modifier = Modifier.weight(1f),
                        color = DragonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        fontFamily = JetBrainsMono
                    )
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Syncing",
                        tint = DragonGreen.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Buscar en la biblioteca...", color = DragonGray, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DragonGray, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    textStyle = TextStyle(color = DragonWhite, fontSize = 14.sp, fontFamily = JetBrainsMono),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DragonCyan,
                        unfocusedBorderColor = DragonGray.copy(alpha = 0.3f),
                        cursorColor = DragonCyan
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                HorizontalDivider(color = DragonGray.copy(alpha = 0.2f))
            }

            items(filteredBooks, key = { it.id }) { book ->
                NavigationDrawerItem(
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(book.title, color = DragonWhite, fontWeight = if (book.id == currentBookId) FontWeight.Bold else FontWeight.Normal)
                                Text(
                                    "Last Modified: ${java.text.SimpleDateFormat("MMM dd, HH:mm").format(book.lastModified)}",
                                    fontSize = 10.sp,
                                    color = DragonGray
                                )
                            }
                            Row {
                                IconButton(onClick = { onRenameBook(book.id, book.title) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = DragonCyan.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                                }
                                if (books.size > 1) {
                                    IconButton(onClick = { onDeleteBook(book.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    },
                    selected = book.id == currentBookId,
                    onClick = {
                        onLoadBook(book.id)
                        onCloseDrawer()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = DragonCyan.copy(alpha = 0.1f),
                        unselectedContainerColor = Color.Transparent
                    )
                )
            }

            item {
                HorizontalDivider(color = DragonGray.copy(alpha = 0.2f))
                NavigationDrawerItem(
                    label = { Text("Add New Book", color = DragonGreen) },
                    selected = false,
                    onClick = { onNewBook() },
                    icon = { Icon(Icons.Default.Add, contentDescription = null, tint = DragonGreen) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
