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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _materials = MutableStateFlow<List<MaterialSpec>>(emptyList())
    val materials: StateFlow<List<MaterialSpec>> = _materials.asStateFlow()

    private val _events = MutableSharedFlow<ForgePartEditorEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private var currentPart: ForgePart? = null

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

    private fun rename(name: String) {
        val current = currentPart ?: return
        val updated = current.copy(
            artifact = current.artifact.copy(name = name, updatedAt = System.currentTimeMillis())
        )
        currentPart = updated
        _uiState.value = UiState.Ready(updated)
    }

    private fun setSafety(classification: SafetyClassification) {
        val current = currentPart ?: return
        val updated = current.copy(
            artifact = current.artifact.copy(
                safetyClassification = classification,
                updatedAt = System.currentTimeMillis()
            )
        )
        currentPart = updated
        _uiState.value = UiState.Ready(updated)
    }

    private fun updateDimension(field: DimensionField, value: Double) {
        val current = currentPart ?: return
        if (!value.isFinite() || value < 0.0) return
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
    }

    private fun assignMaterial(materialId: String) {
        val current = currentPart ?: return
        val updated = current.copy(
            artifact = current.artifact.copy(updatedAt = System.currentTimeMillis()),
            materialId = materialId
        )
        currentPart = updated
        _uiState.value = UiState.Ready(updated)
    }

    private fun addFeature(feature: ParametricFeature) {
        val current = currentPart ?: return
        val updated = current.copy(
            artifact = current.artifact.copy(updatedAt = System.currentTimeMillis()),
            featureTree = current.featureTree + feature
        )
        currentPart = updated
        _uiState.value = UiState.Ready(updated)
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
        viewModelScope.launch {
            repository.savePart(current)
            _events.emit(ForgePartEditorEvent.OnSaved)
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