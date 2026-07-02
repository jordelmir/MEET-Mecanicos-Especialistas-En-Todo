package com.elysium.vanguard.forge.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.forge.assembly.ForgeAssemblyEngine
import com.elysium.vanguard.forge.data.ForgeArtifactRepository
import com.elysium.vanguard.forge.domain.AssemblyValidationResult
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.JointType
import com.elysium.vanguard.forge.domain.MechanicalJoint
import com.elysium.vanguard.forge.domain.PartInstance
import com.elysium.vanguard.forge.domain.TransformData
import com.elysium.vanguard.forge.presentation.components.UiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForgeAssemblyEditorViewModel(
    private val assemblyId: String? = null,
    private val repository: ForgeArtifactRepository = ForgeArtifactRepository(),
    private val engine: ForgeAssemblyEngine = ForgeAssemblyEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ForgeAssembly>>(UiState.Loading)
    val uiState: StateFlow<UiState<ForgeAssembly>> = _uiState.asStateFlow()

    private val _parts = MutableStateFlow<List<ForgePart>>(emptyList())
    val parts: StateFlow<List<ForgePart>> = _parts.asStateFlow()

    private val _validation = MutableStateFlow<AssemblyValidationResult?>(null)
    val validation: StateFlow<AssemblyValidationResult?> = _validation.asStateFlow()

    private val _events = MutableSharedFlow<ForgeAssemblyEditorEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private var current: ForgeAssembly? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val asm = assemblyId?.let { repository.getAssembly(it) } ?: createBlank()
            current = asm
            _parts.value = repository.parts.value.values.toList()
            _uiState.value = if (asm != null) UiState.Ready(asm) else UiState.Empty
        }
    }

    fun onEvent(event: ForgeAssemblyEditorEvent) {
        when (event) {
            is ForgeAssemblyEditorEvent.OnAddPart -> addPart(event.partId, event.instanceId)
            is ForgeAssemblyEditorEvent.OnRemoveInstance -> removeInstance(event.instanceId)
            is ForgeAssemblyEditorEvent.OnCreateJoint -> createJoint(
                event.parentId, event.childId, event.jointType
            )
            is ForgeAssemblyEditorEvent.OnRename -> rename(event.name)
            ForgeAssemblyEditorEvent.OnValidate -> validate()
            ForgeAssemblyEditorEvent.OnSave -> save()
            is ForgeAssemblyEditorEvent.OnComputeExplodedView -> computeExploded()
            is ForgeAssemblyEditorEvent.OnJointCreationFailed -> { /* UI listens */ }
            is ForgeAssemblyEditorEvent.OnExplodedViewReady -> { /* UI listens */ }
            ForgeAssemblyEditorEvent.OnValidated -> { /* UI listens */ }
            ForgeAssemblyEditorEvent.OnSaved -> { /* UI listens */ }
        }
    }

    private fun rename(name: String) {
        val c = current ?: return
        val updated = c.copy(
            artifact = c.artifact.copy(name = name, updatedAt = System.currentTimeMillis())
        )
        current = updated
        _uiState.value = UiState.Ready(updated)
    }

    private fun addPart(partId: String, instanceId: String) {
        val c = current ?: return
        val updated = engine.addPart(c, partId, instanceId)
        current = updated
        _uiState.value = UiState.Ready(updated)
    }

    private fun removeInstance(instanceId: String) {
        val c = current ?: return
        val updated = engine.removePart(c, instanceId)
        current = updated
        _uiState.value = UiState.Ready(updated)
    }

    private fun createJoint(parentId: String, childId: String, type: JointType) {
        val c = current ?: return
        val result = engine.createJoint(c, "j_${System.currentTimeMillis()}", "", type, parentId, childId)
        result.onSuccess { newAsm ->
            current = newAsm
            _uiState.value = UiState.Ready(newAsm)
        }.onFailure { ex ->
            viewModelScope.launch {
                _events.emit(ForgeAssemblyEditorEvent.OnJointCreationFailed(ex.message ?: "Joint failed"))
            }
        }
    }

    private fun validate() {
        val c = current ?: return
        viewModelScope.launch {
            val partsById = _parts.value.associateBy { it.artifact.id }
            _validation.value = engine.validateAssembly(c, partsById)
            _events.emit(ForgeAssemblyEditorEvent.OnValidated)
        }
    }

    private fun save() {
        val c = current ?: return
        viewModelScope.launch {
            repository.saveAssembly(c)
            _events.emit(ForgeAssemblyEditorEvent.OnSaved)
        }
    }

    private fun computeExploded() {
        val c = current ?: return
        val exploded = engine.computeExplodedView(c)
        viewModelScope.launch {
            _events.emit(ForgeAssemblyEditorEvent.OnExplodedViewReady(exploded))
        }
    }

    private fun createBlank(): ForgeAssembly {
        return ForgeAssembly(
            artifact = ForgeArtifact(
                id = "asm_${System.currentTimeMillis()}",
                name = "Nuevo ensamble",
                artifactType = ForgeArtifactType.ASSEMBLY
            )
        )
    }
}

sealed class ForgeAssemblyEditorEvent {
    data class OnRename(val name: String) : ForgeAssemblyEditorEvent()
    data class OnAddPart(val partId: String, val instanceId: String) : ForgeAssemblyEditorEvent()
    data class OnRemoveInstance(val instanceId: String) : ForgeAssemblyEditorEvent()
    data class OnCreateJoint(
        val parentId: String,
        val childId: String,
        val jointType: JointType
    ) : ForgeAssemblyEditorEvent()
    data object OnValidate : ForgeAssemblyEditorEvent()
    data object OnSave : ForgeAssemblyEditorEvent()
    data object OnComputeExplodedView : ForgeAssemblyEditorEvent()
    data class OnJointCreationFailed(val message: String) : ForgeAssemblyEditorEvent()
    data object OnValidated : ForgeAssemblyEditorEvent()
    data object OnSaved : ForgeAssemblyEditorEvent()
    data class OnExplodedViewReady(
        val exploded: com.elysium.vanguard.forge.domain.ExplodedViewResult
    ) : ForgeAssemblyEditorEvent()
}