package cr.dragon.calc.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cr.dragon.calc.data.storage.DragonDatabase
import cr.dragon.calc.data.storage.toEntity
import cr.dragon.calc.ui.document.Cell
import cr.dragon.calc.ui.document.CellType
import cr.dragon.calc.ui.document.DragonDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dragoncore.cas.SymbolicOptimizer
import dragoncore.chemistry.ChemBalancer
import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator
import dragoncore.lexer.DragonLexer
import dragoncore.parser.DragonParser
import dragoncore.parser.Expr
import dragoncore.security.DragonSandbox
import dragoncore.stats.DragonFrame
import dragoncore.stats.MonteCarloEngine
import dragoncore.symbolic.SymbolicIntegrator
import dragoncore.neural.NeuralProvider
import dragoncore.neural.NeuralRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * [V4 Cloud] Estados de arranque instantáneos.
 */
sealed class AppBootState {
    object Idle : AppBootState()
    object Ready : AppBootState()
    data class Error(val reason: String) : AppBootState()
}

/**
 * Estado de la UI del ecosistema DragonDocs
 */
data class CalcUiState(
    val document: DragonDocument = DragonDocument(),
    val focusedCellId: String? = null,
    val angleMode: DragonContext.AngleMode = DragonContext.AngleMode.RADIAN,
    val isEvaluating: Boolean = false
)

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val evaluator: DragonEvaluator,
    val dragonContextTemplate: DragonContext,
    val physicsEngine: dragoncore.physics.DragonPhysicsEngine,
    private val database: DragonDatabase,
    private val sandbox: DragonSandbox,
    val neuralVault: dragoncore.security.NeuralVault,
    val neuralRouter: dragoncore.neural.NeuralRouter
) : ViewModel() {

    private val chemBalancer = ChemBalancer()
    private val symbolicIntegrator = SymbolicIntegrator()
    private val monteCarloEngine = MonteCarloEngine()

    private val cellDao = database.cellDao()

    private val _uiState = MutableStateFlow(CalcUiState())
    val uiState: StateFlow<CalcUiState> = _uiState.asStateFlow()

    // --- 📚 BOOK MANAGEMENT ---
    private val _books = MutableStateFlow<List<DragonDocument>>(emptyList())
    val books: StateFlow<List<DragonDocument>> = _books.asStateFlow()

    // Vector [UI-Cloud]: Engine State
    var activeEngineName by mutableStateOf("DragonBrain (Nube requiere API)")
        private set

    var bootState by mutableStateOf<AppBootState>(AppBootState.Idle)
        private set

    val currentApiKey: String
        get() = neuralVault.getApiKey("gemini") ?: ""

    init {
        val apiKey = neuralVault.getApiKey("gemini")
        if (!apiKey.isNullOrEmpty()) {
            activeEngineName = "DragonBrain (Motor Activo)"
        }
        startBootSequence()
    }

    private fun startBootSequence() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 1. Cargamos el último libro o creamos uno nuevo
                val availableBooks = dragoncore.persistence.DragonPersistence.listBooks(context)
                _books.value = availableBooks

                val lastLibro = availableBooks.firstOrNull() ?: DragonDocument()
                if (availableBooks.isEmpty()) {
                    dragoncore.persistence.DragonPersistence.saveDocument(context, lastLibro)
                    _books.value = listOf(lastLibro)
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val mode = try {
                        DragonContext.AngleMode.valueOf(lastLibro.angleMode)
                    } catch(e: Exception) {
                        DragonContext.AngleMode.RADIAN
                    }
                    _uiState.update { it.copy(
                        document = lastLibro,
                        focusedCellId = lastLibro.focusedCellId ?: lastLibro.cells.firstOrNull()?.id,
                        angleMode = mode
                    ) }
                    bootState = AppBootState.Ready
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    bootState = AppBootState.Error("Fallo de inicialización interna: ${e.message}")
                }
            }
        }
    }

    fun loadBook(bookId: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val doc = dragoncore.persistence.DragonPersistence.loadDocument(context, bookId)
            if (doc != null) {
                val mode = try {
                    DragonContext.AngleMode.valueOf(doc.angleMode)
                } catch(e: Exception) {
                    DragonContext.AngleMode.RADIAN
                }
                _uiState.update { it.copy(
                    document = doc,
                    focusedCellId = doc.focusedCellId ?: doc.cells.firstOrNull()?.id,
                    angleMode = mode
                ) }
            }
        }
    }

    fun createNewBook(title: String = "Untitled Document") {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val newDoc = DragonDocument(title = title)
            dragoncore.persistence.DragonPersistence.saveDocument(context, newDoc)
            val updatedBooks = dragoncore.persistence.DragonPersistence.listBooks(context)
            _books.value = updatedBooks
            _uiState.update { it.copy(document = newDoc, focusedCellId = newDoc.cells.firstOrNull()?.id) }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            dragoncore.persistence.DragonPersistence.deleteBook(context, bookId)
            val updatedBooks = dragoncore.persistence.DragonPersistence.listBooks(context)
            _books.value = updatedBooks
            if (_uiState.value.document.id == bookId) {
                val next = updatedBooks.firstOrNull() ?: DragonDocument().apply {
                     dragoncore.persistence.DragonPersistence.saveDocument(context, this)
                }
                _uiState.update { it.copy(document = next, focusedCellId = next.cells.firstOrNull()?.id) }
            }
        }
    }

    fun renameBook(bookId: String, newTitle: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val doc = dragoncore.persistence.DragonPersistence.loadDocument(context, bookId) ?: return@launch
            val updatedDoc = doc.copy(title = newTitle, lastModified = System.currentTimeMillis())
            dragoncore.persistence.DragonPersistence.saveDocument(context, updatedDoc)

            // Refrescar lista de libros
            val updatedBooks = dragoncore.persistence.DragonPersistence.listBooks(context)
            _books.value = updatedBooks

            // Si es el libro actual, actualizar estado
            if (_uiState.value.document.id == bookId) {
                _uiState.update { it.copy(document = updatedDoc) }
            }
        }
    }

    private fun updateAndSave(updater: (CalcUiState) -> CalcUiState) {
        _uiState.update { state ->
            val newState = updater(state)
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                // Sync rawText before persist
                val persistedCells = newState.document.cells.map { it.copy(rawText = it.content.text) }
                val persistedDoc = newState.document.copy(
                    cells = persistedCells,
                    lastModified = System.currentTimeMillis(),
                    angleMode = newState.angleMode.name,
                    focusedCellId = newState.focusedCellId
                )
                dragoncore.persistence.DragonPersistence.saveDocument(context, persistedDoc)
            }
            newState
        }
    }

    val dragonContext: DragonContext
        get() = uiState.value.angleMode.let { mode ->
            DragonContext().apply { angleMode = mode }
        }

    // ================================================================
    // DAG & EVALUACION CASCADA
    // ================================================================

    private fun evaluateDAG(cells: List<Cell>, angleMode: DragonContext.AngleMode): List<Cell> {
        val workingContext = DragonContext().apply { this.angleMode = angleMode }
        val lexer = DragonLexer()

        return cells.map { cell ->
            if (cell.type == CellType.MARKDOWN) {
                return@map cell
            }

            val text = if (cell.type == CellType.PROMPT) {
                if (cell.translatedContent.isBlank()) return@map cell else cell.translatedContent
            } else {
                cell.content.text
            }

            if (text.isBlank()) {
                cell.copy(result = "", isError = false, ast = null)
            } else {
                val letters = text.count { it.isLetter() }
                val hasConversationalChars = text.any { it in "¿?¡!" }
                val hasMathOperators = text.any { it in "+-*/^=" }
                val isConversational = hasConversationalChars || (letters > 15 && !hasMathOperators)

                if (isConversational && cell.type == CellType.MATH) {
                    return@map cell.copy(type = CellType.PROMPT, result = "IA: Esperando orden...", isError = false, ast = null)
                }

                try {
                    val tokens = lexer.tokenize(text)
                    val expr = DragonParser(tokens).parse()

                    when {
                        cell.type == CellType.GRAPH || cell.type == CellType.GRAPH_3D || cell.type == CellType.PHYSICS -> {
                            cell.copy(
                                result = when(cell.type) {
                                    CellType.GRAPH -> "Graph View"
                                    CellType.GRAPH_3D -> "Surface 3D"
                                    else -> "Physics Lab"
                                },
                                isError = false,
                                ast = expr
                            )
                        }
                        cell.type == CellType.CHEMISTRY -> {
                            val parts = text.split("->", "—>", "➔")
                            if (parts.size == 2) {
                                val reactants = parts[0].split("+").map { it.trim() }
                                val products = parts[1].split("+").map { it.trim() }
                                val balanceResult = chemBalancer.balance(reactants, products)

                                if (balanceResult.success) {
                                    val balancedStr = buildString {
                                        balanceResult.reactants.forEachIndexed { i, c ->
                                            if (c > 1) append(c)
                                            append(reactants[i])
                                            if (i < balanceResult.reactants.size - 1) append(" + ")
                                        }
                                        append(" ➔ ")
                                        balanceResult.products.forEachIndexed { i, c ->
                                            if (c > 1) append(c)
                                            append(products[i])
                                            if (i < balanceResult.products.size - 1) append(" + ")
                                        }
                                    }
                                    cell.copy(result = balancedStr, isError = false)
                                } else {
                                    cell.copy(result = "Error de Balanceo: ${balanceResult.message}", isError = true)
                                }
                            } else {
                                cell.copy(result = "Formato: Reactivos -> Productos", isError = false)
                            }
                        }
                        else -> {
                            var optimizer = SymbolicOptimizer()
                            var simplifiedExpr = optimizer.simplify(expr)
                            var steps = optimizer.evaluationSteps.toList()

                            if (text.startsWith("int(") || text.contains("∫")) {
                                val integral = symbolicIntegrator.integrate(simplifiedExpr)
                                cell.copy(
                                    result = integral.toString() + " + C",
                                    isError = false,
                                    ast = integral
                                )
                            } else if (expr is Expr.DerivativeExpr || text.startsWith("d(")) {
                                cell.copy(
                                    result = simplifiedExpr.toString(),
                                    isError = false,
                                    ast = simplifiedExpr,
                                    evaluationSteps = steps
                                )
                            } else if (cell.type == CellType.STATISTICS || cell.type == CellType.DATAFRAME) {
                                val frame = DragonFrame.fromCsv(text)
                                val desc = frame.describe()
                                cell.copy(
                                    result = "DataFrame: ${frame.rowCount} rows\nMean: ${desc.values.firstOrNull()}",
                                    isError = false
                                )
                            } else {
                                val resultNum = sandbox.secureExecute(simplifiedExpr, workingContext)
                                val formatted = if (resultNum is Double) formatResult(resultNum) else resultNum.toString()
                                cell.copy(
                                    result = formatted,
                                    isError = false,
                                    ast = simplifiedExpr,
                                    evaluationSteps = steps
                                )
                            }
                        }
                    }

                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Syntax Error"
                    cell.copy(result = errorMsg, isError = true, ast = null)
                }
            }
        }
    }

    private fun formatResult(value: Double): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
        return if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            val s = "%.10g".format(value)
            s.trimEnd('0').trimEnd('.')
        }
    }

    // ================================================================
    // ACCIONES DE CELDAS
    // ================================================================

    fun onFocusCell(cellId: String) {
        updateAndSave { it.copy(focusedCellId = cellId) }
    }

    fun addCell(type: CellType = CellType.MATH) {
        updateAndSave { state ->
            val newCell = Cell(type = type)
            val newCells = state.document.cells + newCell
            state.copy(
                document = state.document.copy(cells = evaluateDAG(newCells, state.angleMode)),
                focusedCellId = newCell.id
            )
        }
    }

    fun onScanResult(scannedText: String) {
        updateAndSave { state ->
            val type = if (scannedText.contains("x") && scannedText.contains("y")) {
                CellType.GRAPH_3D
            } else if (scannedText.contains("x")) {
                CellType.GRAPH
            } else {
                CellType.MATH
            }

            val newCell = Cell(
                type = type,
                content = TextFieldValue(text = scannedText, selection = TextRange(scannedText.length))
            )
            val newCells = state.document.cells + newCell

            var finalCells = newCells
            try {
                val tokens = DragonLexer().tokenize(scannedText)
                DragonParser(tokens).parse()
                finalCells = evaluateDAG(newCells, state.angleMode)
            } catch (e: Exception) {}

            state.copy(
                document = state.document.copy(cells = finalCells),
                focusedCellId = newCell.id
            )
        }
    }

    fun removeCell(cellId: String) {
        updateAndSave { state ->
            val remaining = state.document.cells.filter { it.id != cellId }
            val newCells = if (remaining.isEmpty()) listOf(Cell(type = CellType.MATH)) else remaining
            val newFocused = if (state.focusedCellId == cellId) newCells.last().id else state.focusedCellId

            state.copy(
                document = state.document.copy(cells = evaluateDAG(newCells, state.angleMode)),
                focusedCellId = newFocused
            )
        }
    }

    fun deleteAllCells() {
        updateAndSave { state ->
            val freshCell = Cell(type = CellType.MATH)
            val newCells = listOf(freshCell)
            state.copy(
                document = state.document.copy(cells = evaluateDAG(newCells, state.angleMode)),
                focusedCellId = freshCell.id
            )
        }
    }

    private fun updateFocusedCell(updater: (TextFieldValue) -> TextFieldValue) {
        updateAndSave { state ->
            val focusedId = state.focusedCellId ?: return@updateAndSave state
            val newCells = state.document.cells.map { cell ->
                if (cell.id == focusedId) {
                    cell.copy(content = updater(cell.content))
                } else {
                    cell
                }
            }
            state.copy(
                document = state.document.copy(cells = evaluateDAG(newCells, state.angleMode))
            )
        }
    }

    fun onInput(text: String) {
        updateFocusedCell { content ->
            val currentText = content.text
            val cursorStart = content.selection.start
            val cursorEnd = content.selection.end
            val newText = currentText.substring(0, cursorStart) + text + currentText.substring(cursorEnd)
            val newCursorPos = cursorStart + text.length
            TextFieldValue(newText, TextRange(newCursorPos))
        }
    }

    fun onExpressionChange(newExpr: TextFieldValue, cellId: String) {
        updateAndSave { state ->
            val newCells = state.document.cells.map { cell ->
                if (cell.id == cellId) cell.copy(content = newExpr) else cell
            }
            state.copy(
                document = state.document.copy(cells = evaluateDAG(newCells, state.angleMode))
            )
        }
    }

    fun onNavigate(direction: Int) {
        updateFocusedCell { content ->
            val start = content.selection.start
            val newStart = (start + direction).coerceIn(0, content.text.length)
            content.copy(selection = TextRange(newStart))
        }
    }

    fun onClear() {
        updateFocusedCell { TextFieldValue("") }
    }

    fun onDelete() {
        updateFocusedCell { content ->
            val currentText = content.text
            val cursorStart = content.selection.start
            val cursorEnd = content.selection.end

            if (cursorStart != cursorEnd) {
                val newText = currentText.substring(0, cursorStart) + currentText.substring(cursorEnd)
                TextFieldValue(newText, TextRange(cursorStart))
            } else if (cursorStart > 0) {
                val newText = currentText.substring(0, cursorStart - 1) + currentText.substring(cursorStart)
                TextFieldValue(newText, TextRange(cursorStart - 1))
            } else content
        }
    }

    fun onEquals() {
        addCell(CellType.MATH)
    }

    fun toggleAngleMode() {
        updateAndSave { state ->
            val newMode = when (state.angleMode) {
                DragonContext.AngleMode.RADIAN  -> DragonContext.AngleMode.DEGREE
                DragonContext.AngleMode.DEGREE  -> DragonContext.AngleMode.GRADIAN
                DragonContext.AngleMode.GRADIAN -> DragonContext.AngleMode.RADIAN
            }
            state.copy(
                angleMode = newMode,
                document = state.document.copy(cells = evaluateDAG(state.document.cells, newMode))
            )
        }
    }

    // ================================================================
    // NEURO-SYMBOLIC REASONING (DRAGON BRAIN - CLOUD ONLY)
    // ================================================================

    /**
     * VECTOR [🧠V5] - CLOUD NEURAL CORE (WATERFALL)
     * Procesa la intención del usuario usando el enrutador inteligente con cascada.
     */
    fun processNeuralIntent(cell: Cell, prompt: String) {
        viewModelScope.launch {
            try {
                val currentCells = uiState.value.document.cells.joinToString("\n") { cellInfo ->
                    "[${cellInfo.type}]: ${cellInfo.content.text}"
                }
                val contextPrompt = """
                    [Contexto del Documento]:
                    $currentCells
                    [Celda Enfocada]: ${cell.type}
                    [Input de Usuario]: $prompt
                """.trimIndent()

                val response = neuralRouter.processIntent(contextPrompt, cell.type.name).getOrThrow()

                activeEngineName = response.sourceEngine

                updateAndSave { state ->
                    val updatedCells = state.document.cells.map { c ->
                        if (c.id == cell.id) {
                            c.copy(
                                aiExplanation = response.explanation,
                                aiModuleType = response.moduleType,
                                aiDragonCode = response.dragonCode,
                                translatedContent = response.dragonCode,
                                type = CellType.PROMPT,
                                sourceEngine = response.sourceEngine,
                                isExecuting = false
                            )
                        } else c
                    }

                    var finalCells = updatedCells
                    val targetCellType = when (response.moduleType.uppercase()) {
                        "GRAPH_2D" -> CellType.GRAPH
                        "GRAPH_3D" -> CellType.GRAPH_3D
                        "PHYSICS" -> CellType.PHYSICS
                        "CHEMISTRY" -> CellType.CHEMISTRY
                        "STATS" -> CellType.STATISTICS
                        else -> null
                    }

                    if (targetCellType != null) {
                        val autoCell = Cell(
                            type = targetCellType,
                            content = TextFieldValue(
                                text = response.dragonCode,
                                selection = TextRange(response.dragonCode.length)
                            )
                        )
                        finalCells = finalCells + autoCell
                    } else if (response.moduleType == "MATH" && response.dragonCode.isNotEmpty()) {
                        val mathCell = Cell(
                            type = CellType.MATH,
                            content = TextFieldValue(
                                text = response.dragonCode,
                                selection = TextRange(response.dragonCode.length)
                            )
                        )
                        finalCells = finalCells + mathCell
                    }

                    state.copy(document = state.document.copy(cells = evaluateDAG(finalCells, state.angleMode)))
                }
            } catch (e: Exception) {
                updateAndSave { state ->
                    state.copy(document = state.document.copy(cells = state.document.cells.map { c ->
                        if (c.id == cell.id) c.copy(result = "⚠️ ${e.message}", isExecuting = false) else c
                    }))
                }
            }
        }
    }

    fun onPromptSubmitted(cellId: String, prompt: String) {
        if (prompt.isBlank()) return

        val cell = uiState.value.document.cells.find { it.id == cellId } ?: return

        // Let UI know we are executing
        updateAndSave { state ->
            val newCells = state.document.cells.map {
                if (it.id == cellId) it.copy(isExecuting = true, result = "⚡ Conectando al enjambre Gemini...") else it
            }
            state.copy(document = state.document.copy(cells = evaluateDAG(newCells, state.angleMode)))
        }

        processNeuralIntent(cell, prompt)
    }

    /**
     * VECTOR [🌌V7] - GLOBAL MULTIMODAL PROMPT
     * Crea una celda de prompt global y la procesa inmediatamente.
     */
    fun sendGlobalPrompt(prompt: String) {
        if (prompt.isBlank()) return

        val newCell = Cell(
            type = CellType.PROMPT,
            content = TextFieldValue(text = prompt),
            isExecuting = true,
            result = "✨ DragonBrain está pensando..."
        )

        updateAndSave { state ->
            val updatedCells = state.document.cells + newCell
            state.copy(document = state.document.copy(cells = updatedCells))
        }

        processNeuralIntent(newCell, prompt)
    }

    fun setNeuralProvider(provider: NeuralProvider) {
        // updateAndSave { it.copy(activeEngineName = provider.name) }
    }

    // ================================================================
    // API KEY VALIDATION [🔑+🌐]
    // ================================================================

    sealed class ApiValidationState {
        object Idle : ApiValidationState()
        object Validating : ApiValidationState()
        data class Success(val model: String) : ApiValidationState()
        data class Error(val message: String) : ApiValidationState()
    }

    private val _apiValidationState = MutableStateFlow<ApiValidationState>(ApiValidationState.Idle)
    val apiValidationState: StateFlow<ApiValidationState> = _apiValidationState.asStateFlow()

    fun validateAndApplyKey(key: String, openAiKey: String = "", anthropicKey: String = "") {
        viewModelScope.launch {
            _apiValidationState.value = ApiValidationState.Validating

            val result = neuralRouter.validateGeminiKey(key)

            if (result.isSuccess) {
                // Save keys
                neuralVault.saveApiKey("gemini", key)
                if (openAiKey.isNotBlank()) neuralVault.saveApiKey("openai", openAiKey)
                if (anthropicKey.isNotBlank()) neuralVault.saveApiKey("anthropic", anthropicKey)

                // Set provider
                setNeuralProvider(NeuralProvider.Gemini(key))
                activeEngineName = "Gemini Cloud"

                _apiValidationState.value = ApiValidationState.Success(result.getOrDefault("gemini-1.5-flash"))

                // Re-evaluate
                updateAndSave { state ->
                    state.copy(document = state.document.copy(cells = evaluateDAG(state.document.cells, state.angleMode)))
                }
            } else {
                _apiValidationState.value = ApiValidationState.Error(
                    result.exceptionOrNull()?.message ?: "Error desconocido"
                )
            }
        }
    }

    fun resetValidationState() {
        _apiValidationState.value = ApiValidationState.Idle
    }

    fun updateNeuralEngine(engine: String, key: String) {
        viewModelScope.launch {
            if (engine == "gemini") {
                neuralVault.saveApiKey("gemini", key)
                setNeuralProvider(NeuralProvider.Gemini(key))
                activeEngineName = "Gemini Cloud"
            } else if (engine == "openai") {
                neuralVault.saveApiKey("openai", key)
                setNeuralProvider(NeuralProvider.OpenAI(key))
                activeEngineName = "OpenAI Cloud"
            }

            updateAndSave { state ->
                state.copy(
                    document = state.document.copy(cells = evaluateDAG(state.document.cells, state.angleMode))
                )
            }
        }
    }

    fun onPhysicsStateUpdate(cellId: String, newState: Map<String, Double>) {
        _uiState.update { state ->
            val newCells = state.document.cells.map { cell ->
                if (cell.id == cellId) cell.copy(physicsState = newState) else cell
            }
            state.copy(document = state.document.copy(cells = newCells))
        }
    }

    /**
     * Comparte el libro actual como un archivo JSON (.drbk).
     */
    fun shareBook() {
        viewModelScope.launch {
            val doc = _uiState.value.document
            val fileName = "${doc.title.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)}.drbk"
            val file = java.io.File(context.cacheDir, "DragonCalcExports").apply { mkdirs() }.resolve(fileName)

            try {
                // Preparamos el contenido JSON
                val json = kotlinx.serialization.json.Json { prettyPrint = true }
                val content = json.encodeToString(DragonDocument.serializer(), doc)
                file.writeText(content)

                // Compartimos con el FileProvider corregido
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = android.content.Intent.createChooser(intent, "Compartir DragonBook")
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Comparte el libro actual como un archivo HTML profesional.
     */
    fun shareAsHtml() {
        viewModelScope.launch {
            val doc = _uiState.value.document
            val result = try {
                cr.dragon.calc.export.HtmlExporter.generateHtml(context, doc.title, doc.cells)
            } catch (_: Exception) {
                null
            }

            result?.let { file ->
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/html"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = android.content.Intent.createChooser(intent, "Compartir como Web (HTML)")
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        }
    }

    fun getSavedKey(provider: String): String {
        return neuralVault.getApiKey(provider) ?: ""
    }
}
