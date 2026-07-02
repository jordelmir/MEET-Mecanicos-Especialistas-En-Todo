package com.elysium.vanguard.forge.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.forge.data.ForgeArtifactRepository
import com.elysium.vanguard.forge.manuals.ForgeManualEngine
import com.elysium.vanguard.forge.domain.FailureMode
import com.elysium.vanguard.forge.domain.ForgeManual
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.presentation.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForgeRepairManualViewModel(
    private val manualId: String? = null,
    private val repository: ForgeArtifactRepository = ForgeArtifactRepository(),
    private val manualEngine: ForgeManualEngine = ForgeManualEngine()
) : ViewModel() {

    private val _manual = MutableStateFlow<UiState<ForgeManual>>(UiState.Loading)
    val manual: StateFlow<UiState<ForgeManual>> = _manual.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val m = manualId?.let { repository.getManual(it) }
            if (m == null) {
                // Auto-generar desde una parte seed si no se pasa manualId.
                val part = repository.getPart("spark_plug_generic")
                _manual.value = if (part != null) UiState.Ready(manualEngine.createManualFromPart(part))
                else UiState.Empty
            } else {
                _manual.value = UiState.Ready(m)
            }
        }
    }

    fun generateRepairManualForFailure(part: ForgePart, failure: FailureMode) {
        viewModelScope.launch {
            _manual.value = UiState.Ready(manualEngine.generateRepairManual(failure, part))
        }
    }

    fun generateReplacementManualFor(part: ForgePart) {
        viewModelScope.launch {
            _manual.value = UiState.Ready(manualEngine.generateReplacementManual(part))
        }
    }
}