package com.elysium.vanguard.forge.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.forge.data.ForgeArtifactRepository
import com.elysium.vanguard.forge.domain.DimensionSet
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.MaterialSpec
import com.elysium.vanguard.forge.domain.ParametricFeature
import com.elysium.vanguard.forge.domain.SafetyClassification
import com.elysium.vanguard.forge.engine.ForgeGeometryCompiler
import com.elysium.vanguard.forge.presentation.components.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ForgePartEditorViewModel — coordina la edición de una pieza.
 *
 * Reglas:
 * - Sin I/O en main thread (Dispatchers.Default para compilar geometría).
 * - Sin lógica de negocio en Composables.
 */
class ForgePartEditorViewModel(
    private val partId: String? = null,
    private val repository: ForgeArtifactRepository = ForgeArtifactRepository()
) : ViewModel() {

    private val compiler = ForgeGeometryCompiler()

    private val _uiState = MutableStateFlow<UiState<ForgePart>>(UiState.Loading)
    val uiState: StateFlow<UiState<ForgePart>> = _uiState.asStateFlow()

    /**
     * StateFlow dedicado a la clasificación de seguridad.
     * Derivado de uiState para mantener single-source-of-truth (la pieza siempre vive
     * dentro del UiState; este flow es solo una proyección tipada).
     *
     * Default: EDUCATIONAL — equivalente al valor que `createBlankPart()` asigna.
     */
    val safetyClassification: StateFlow<SafetyClassification> = _uiState
        .map { state ->
            when (state) {
                is UiState.Ready -> state.data.artifact.safetyClassification
                is UiState.Empty -> SafetyClassification.EDUCATIONAL
                is UiState.Error -> SafetyClassification.EDUCATIONAL
                is UiState.Loading -> SafetyClassification.EDUCATIONAL
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SafetyClassification.EDUCATIONAL
        )

    private val _materials = MutableStateFlow<List<MaterialSpec>>(emptyList())
    val materials: StateFlow<List<MaterialSpec>> = _materials.asStateFlow()

    private val _events = MutableSharedFlow<ForgePartEditorEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val _saveStatus = MutableStateFlow(SaveStatus.IDLE)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    /**
     * Historial de snapshots de la parte para undo/redo. Cada mutacion
     * (rename, setSafety, updateDimension, assignMaterial, addFeature) empuja
     * el snapshot PREVIO al undoStack.
     *
     * Limite: 50 entradas (FIFO si excede). Suficiente para sesiones tipicas
     * sin consumir memoria excesiva.
     */
    private val undoStack: ArrayDeque<ForgePart> = ArrayDeque()
    private val redoStack: ArrayDeque<ForgePart> = ArrayDeque()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var currentPart: ForgePart? = null

    // Job del auto-save en vuelo. Se cancela y reemplaza en cada cambio
    // (debounce) para evitar spamear el repo en pulsaciones rápidas.
    private var saveJob: Job? = null

    // Hash del part la última vez que se persistió. Permite detectar cambios
    // pendientes para el sync-save de onCleared().
    private var lastSavedHash: Int = 0

    private companion object {
        /** Espera entre el último cambio y el guardado automático. */
        const val AUTOSAVE_DELAY_MS = 1500L

        /** Tamaño máximo del historial undo/redo. FIFO si excede. */
        const val MAX_UNDO = 50
    }

    init {
        loadPart()
        loadMaterials()
    }

    private fun loadPart() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val part = withContext(Dispatchers.Default) {
                partId?.let { repository.getPart(it) } ?: createBlankPart()
            }
            currentPart = part
            _uiState.value = if (part != null) UiState.Ready(part) else UiState.Empty
        }
    }

    private fun loadMaterials() {
        viewModelScope.launch {
            _materials.value = repository.materials.value.values.toList()
        }
    }

    fun onEvent(event: ForgePartEditorEvent) {
        when (event) {
            is ForgePartEditorEvent.OnUpdateDimension -> updateDimension(event.field, event.value)
            is ForgePartEditorEvent.OnAssignMaterial -> assignMaterial(event.materialId)
            is ForgePartEditorEvent.OnAddFeature -> addFeature(event.feature)
            ForgePartEditorEvent.OnValidatePart -> validate()
            ForgePartEditorEvent.OnSavePart -> save()
            is ForgePartEditorEvent.OnRename -> rename(event.name)
            is ForgePartEditorEvent.OnSetSafetyClassification -> setSafety(event.classification)
            is ForgePartEditorEvent.OnValidationPassed -> { /* UI listens via events */ }
            is ForgePartEditorEvent.OnValidationFailed -> { /* UI listens via events */ }
            ForgePartEditorEvent.OnSaved -> { /* UI listens via events */ }
        }
    }

    /**
     * Captura el snapshot actual en el undoStack antes de una mutación.
     * Llamar desde cada mutator (rename, setSafety, etc).
     * Limpia el redoStack (cualquier redo pendiente ya no aplica).
     */
    private fun pushUndoSnapshot() {
        val current = currentPart ?: return
        undoStack.addLast(current)
        // Limitar tamaño. FIFO si excede.
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
    }

    /**
     * Deshace el último cambio, restaurando el snapshot anterior de `currentPart`.
     * El estado actual va al redoStack para permitir redo().
     */
    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        currentPart?.let { current ->
            redoStack.addLast(current)
            // Limitar redoStack.
            while (redoStack.size > MAX_UNDO) redoStack.removeFirst()
        }
        currentPart = previous
        viewModelScope.launch {
            _uiState.value = UiState.Ready(previous)
            // Re-trigger autosave con el snapshot restaurado.
            scheduleAutoSave()
        }
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    /**
     * Rehace un cambio previamente deshecho.
     */
    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        currentPart?.let { current ->
            undoStack.addLast(current)
            while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        }
        currentPart = next
        viewModelScope.launch {
            _uiState.value = UiState.Ready(next)
            scheduleAutoSave()
        }
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun rename(name: String) {
        val current = currentPart ?: return
        pushUndoSnapshot()
        val updated = current.copy(
            artifact = current.artifact.copy(name = name, updatedAt = System.currentTimeMillis())
        )
        currentPart = updated
        _uiState.value = UiState.Ready(updated)
        scheduleAutoSave()
    }

    private fun setSafety(classification: SafetyClassification) {
        val current = currentPart ?: return
        pushUndoSnapshot()
        val updated = current.copy(
            artifact = current.artifact.copy(
                safetyClassification = classification,
                updatedAt = System.currentTimeMillis()
            )
        )
        currentPart = updated
        _uiState.value = UiState.Ready(updated)
        scheduleAutoSave()
    }

    private fun updateDimension(field: DimensionField, value: Double) {
        val current = currentPart ?: return
        if (!value.isFinite() || value < 0.0) return
        pushUndoSnapshot()
        val currentDims = current.dimensions
        val newDims = when (field) {
            DimensionField.LENGTH -> currentDims.copy(lengthMm = value)
            DimensionField.WIDTH -> currentDims.copy(widthMm = value)
            DimensionField.HEIGHT -> currentDims.copy(heightMm = value)
            DimensionField.DIAMETER -> currentDims.copy(diameterMm = value)
            DimensionField.INNER_DIAMETER -> currentDims.copy(innerDiameterMm = value)
            DimensionField.OUTER_DIAMETER -> currentDims.copy(outerDiameterMm = value)
            DimensionField.THICKNESS -> currentDims.copy(thicknessMm = value)
            DimensionField.TOLERANCE -> currentDims.copy(toleranceMm = value)
        }
        val updated = current.copy(
            artifact = current.artifact.copy(updatedAt = System.currentTimeMillis()),
            dimensions = newDims
        )
        currentPart = updated
        _uiState.value = UiState.Ready(updated)
        scheduleAutoSave()
    }

    private fun assignMaterial(materialId: String) {
        val current = currentPart ?: return
        pushUndoSnapshot()
        val updated = current.copy(
            artifact = current.artifact.copy(updatedAt = System.currentTimeMillis()),
            materialId = materialId
        )
        currentPart = updated
        _uiState.value = UiState.Ready(updated)
        scheduleAutoSave()
    }

    private fun addFeature(feature: ParametricFeature) {
        val current = currentPart ?: return
        pushUndoSnapshot()
        val updated = current.copy(
            artifact = current.artifact.copy(updatedAt = System.currentTimeMillis()),
            featureTree = current.featureTree + feature
        )
        currentPart = updated
        _uiState.value = UiState.Ready(updated)
        scheduleAutoSave()
    }

    private fun validate() {
        val current = currentPart ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val result = compiler.compilePart(part = current)
            _events.emit(
                if (result.errors.isEmpty()) {
                    ForgePartEditorEvent.OnValidationPassed(
                        vertexCount = result.mesh.vertices.size,
                        faceCount = result.mesh.faces.size
                    )
                } else {
                    ForgePartEditorEvent.OnValidationFailed(result.errors)
                }
            )
        }
    }

    private fun save() {
        val current = currentPart ?: return
        // Cancelar cualquier auto-save pendiente — el usuario quiere forzar ya.
        saveJob?.cancel()
        viewModelScope.launch {
            _saveStatus.value = SaveStatus.SAVING
            try {
                withContext(Dispatchers.IO) { repository.savePart(current) }
                lastSavedHash = current.hashCode()
                _saveStatus.value = SaveStatus.SAVED
                _events.emit(ForgePartEditorEvent.OnSaved)
            } catch (e: Exception) {
                _saveStatus.value = SaveStatus.ERROR
            }
        }
    }

    /**
     * Programa un guardado automático tras [AUTOSAVE_DELAY_MS] de inactividad.
     * Cada llamada cancela la anterior, así pulsaciones rápidas solo disparan
     * un único guardado al final.
     */
    private fun scheduleAutoSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            _saveStatus.value = SaveStatus.SCHEDULED
            delay(AUTOSAVE_DELAY_MS)
            val part = currentPart ?: return@launch
            _saveStatus.value = SaveStatus.SAVING
            try {
                withContext(Dispatchers.IO) { repository.savePart(part) }
                lastSavedHash = part.hashCode()
                _saveStatus.value = SaveStatus.SAVED
            } catch (e: Exception) {
                _saveStatus.value = SaveStatus.ERROR
            }
        }
    }

    /**
     * Llamado por el framework cuando el VM se destruye (navegación, finish, etc).
     * Delega a [flushPendingSaveSync] para que el comportamiento sea testeable
     * sin necesidad de provocar la destrucción real del VM.
     *
     * `runBlocking` aquí es aceptable: el VM está destruyéndose, no hay UI
     * thread activo que pueda deadlockear, y no hay alternativa suspendida ya
     * que viewModelScope también está cancelándose.
     */
    override fun onCleared() {
        super.onCleared()
        flushPendingSaveSync()
    }

    /**
     * Persiste sincrónicamente cualquier cambio pendiente.
     *
     * Usado por:
     *  - `onCleared()` cuando el VM se destruye.
     *  - Tests que necesitan validar el comportamiento de "último momento"
     *    sin tener que destruir el VM.
     *
     * No-op si no hay cambios desde el último guardado. No-op si no hay
     * `currentPart` (estado de carga o vacío).
     */
    internal fun flushPendingSaveSync() {
        val part = currentPart ?: return
        if (part.hashCode() == lastSavedHash) return
        try {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                repository.savePart(part)
            }
            lastSavedHash = part.hashCode()
        } catch (_: Exception) {
            // No podemos notificar al usuario en onCleared. Trade-off:
            // cambios críticos deberían pasar por el botón "Guardar".
        }
    }

    private fun createBlankPart(): ForgePart = ForgePart(
        artifact = ForgeArtifact(
            id = "part_${System.currentTimeMillis()}",
            name = "Nueva pieza",
            artifactType = ForgeArtifactType.PART,
            safetyClassification = SafetyClassification.EDUCATIONAL
        ),
        dimensions = DimensionSet(lengthMm = 50.0, widthMm = 50.0, heightMm = 50.0)
    )
}

enum class DimensionField(val displayName: String) {
    LENGTH("Largo"),
    WIDTH("Ancho"),
    HEIGHT("Alto"),
    DIAMETER("Diámetro"),
    INNER_DIAMETER("Diámetro interior"),
    OUTER_DIAMETER("Diámetro exterior"),
    THICKNESS("Espesor"),
    TOLERANCE("Tolerancia")
}

sealed class ForgePartEditorEvent {
    data class OnRename(val name: String) : ForgePartEditorEvent()
    data class OnSetSafetyClassification(val classification: SafetyClassification) : ForgePartEditorEvent()
    data class OnUpdateDimension(val field: DimensionField, val value: Double) : ForgePartEditorEvent()
    data class OnAssignMaterial(val materialId: String) : ForgePartEditorEvent()
    data class OnAddFeature(val feature: ParametricFeature) : ForgePartEditorEvent()
    data object OnValidatePart : ForgePartEditorEvent()
    data object OnSavePart : ForgePartEditorEvent()
    data class OnValidationPassed(val vertexCount: Int, val faceCount: Int) : ForgePartEditorEvent()
    data class OnValidationFailed(val errors: List<String>) : ForgePartEditorEvent()
    data object OnSaved : ForgePartEditorEvent()
}

/**
 * Estado del guardado automático de la pieza.
 * - IDLE: sin cambios pendientes.
 * - SCHEDULED: hay cambios, esperando el fin del debounce para guardar.
 * - SAVING: llamada activa a repository.savePart.
 * - SAVED: persistencia confirmada.
 * - ERROR: la persistencia falló (mutex, I/O, etc).
 */
enum class SaveStatus { IDLE, SCHEDULED, SAVING, SAVED, ERROR }