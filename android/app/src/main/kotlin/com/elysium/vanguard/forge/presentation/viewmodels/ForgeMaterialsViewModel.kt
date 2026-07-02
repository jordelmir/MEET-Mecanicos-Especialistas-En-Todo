package com.elysium.vanguard.forge.presentation.viewmodels

import androidx.lifecycle.ViewModel
import com.elysium.vanguard.forge.data.ForgeArtifactRepository
import com.elysium.vanguard.forge.domain.MaterialSpec
import com.elysium.vanguard.forge.presentation.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ForgeMaterialsViewModel(
    private val repository: ForgeArtifactRepository = ForgeArtifactRepository()
) : ViewModel() {

    private val _materials = MutableStateFlow<UiState<List<MaterialSpec>>>(UiState.Loading)
    val materials: StateFlow<UiState<List<MaterialSpec>>> = _materials.asStateFlow()

    init {
        _materials.value = UiState.Ready(repository.materials.value.values.toList())
    }
}