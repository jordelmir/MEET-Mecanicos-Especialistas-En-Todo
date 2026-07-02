package com.elysium.vanguard.forge.presentation.viewmodels

import androidx.lifecycle.ViewModel
import com.elysium.vanguard.forge.data.ForgeArtifactRepository
import com.elysium.vanguard.forge.domain.ManufacturingProcess
import com.elysium.vanguard.forge.presentation.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ForgeManufacturingViewModel(
    private val repository: ForgeArtifactRepository = ForgeArtifactRepository()
) : ViewModel() {

    private val _processes = MutableStateFlow<UiState<List<ManufacturingProcess>>>(UiState.Loading)
    val processes: StateFlow<UiState<List<ManufacturingProcess>>> = _processes.asStateFlow()

    init {
        _processes.value = UiState.Ready(repository.processes.value.values.toList())
    }
}