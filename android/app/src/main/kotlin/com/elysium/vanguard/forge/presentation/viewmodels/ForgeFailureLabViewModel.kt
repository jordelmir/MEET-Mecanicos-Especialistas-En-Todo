package com.elysium.vanguard.forge.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.forge.data.ForgeArtifactRepository
import com.elysium.vanguard.forge.diagnostics.ForgeDamageEngine
import com.elysium.vanguard.forge.diagnostics.ForgeDiagnosticEngine
import com.elysium.vanguard.forge.domain.DamageSeverity
import com.elysium.vanguard.forge.domain.DamageState
import com.elysium.vanguard.forge.domain.DamageType
import com.elysium.vanguard.forge.domain.DiagnosticReport
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.PartInstance
import com.elysium.vanguard.forge.presentation.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForgeFailureLabViewModel(
    private val assemblyId: String? = null,
    private val repository: ForgeArtifactRepository = ForgeArtifactRepository(),
    private val damageEngine: ForgeDamageEngine = ForgeDamageEngine(),
    private val diagnosticEngine: ForgeDiagnosticEngine = ForgeDiagnosticEngine()
) : ViewModel() {

    private val _assembly = MutableStateFlow<UiState<ForgeAssembly>>(UiState.Loading)
    val assembly: StateFlow<UiState<ForgeAssembly>> = _assembly.asStateFlow()

    private val _diagnostic = MutableStateFlow<DiagnosticReport?>(null)
    val diagnostic: StateFlow<DiagnosticReport?> = _diagnostic.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _assembly.value = UiState.Loading
            val asm = assemblyId?.let { repository.getAssembly(it) }
            if (asm == null) {
                _assembly.value = UiState.Empty
            } else {
                _assembly.value = UiState.Ready(asm)
            }
        }
    }

    fun injectDamage(instanceId: String, type: DamageType, severity: DamageSeverity) {
        val current = (_assembly.value as? UiState.Ready)?.data ?: return
        val updated = current.copy(
            instances = current.instances.map { inst ->
                if (inst.id == instanceId) damageEngine.applyDamage(inst, type, severity) else inst
            }
        )
        _assembly.value = UiState.Ready(updated)
        viewModelScope.launch { repository.saveAssembly(updated) }
    }

    fun repair(instanceId: String) {
        val current = (_assembly.value as? UiState.Ready)?.data ?: return
        val updated = current.copy(
            instances = current.instances.map { inst ->
                if (inst.id == instanceId) damageEngine.repairDamage(inst) else inst
            }
        )
        _assembly.value = UiState.Ready(updated)
        viewModelScope.launch { repository.saveAssembly(updated) }
    }

    fun replace(instanceId: String) {
        val current = (_assembly.value as? UiState.Ready)?.data ?: return
        val updated = current.copy(
            instances = current.instances.map { inst ->
                if (inst.id == instanceId) damageEngine.replacePart(inst) else inst
            }
        )
        _assembly.value = UiState.Ready(updated)
        viewModelScope.launch { repository.saveAssembly(updated) }
    }

    fun diagnose() {
        val current = (_assembly.value as? UiState.Ready)?.data ?: return
        viewModelScope.launch {
            val partsById = current.instances
                .mapNotNull { inst -> repository.getPart(inst.partId)?.let { inst.partId to it } }
                .toMap()
            _diagnostic.value = diagnosticEngine.diagnoseAssembly(current, partsById)
        }
    }
}