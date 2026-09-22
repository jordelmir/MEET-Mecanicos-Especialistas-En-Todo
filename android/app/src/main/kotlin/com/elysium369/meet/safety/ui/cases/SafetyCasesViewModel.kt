package com.elysium369.meet.safety.ui.cases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyPublicRepository
import com.elysium369.meet.safety.data.local.SafetyPublicCaseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SafetyCasesUiState(
    val cases: List<SafetyPublicCaseEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val totalCases: Int = 0,
)

@HiltViewModel
class SafetyCasesViewModel @Inject constructor(
    private val publicRepository: SafetyPublicRepository,
) : ViewModel() {

    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SafetyCasesUiState> =
        combine(publicRepository.observeCases(), loading, error) { allCases, busy, failure ->
                val cases = allCases.filter { it.serverVersion > 0 }
                SafetyCasesUiState(
                    cases = cases,
                    isLoading = busy,
                    error = failure,
                    totalCases = cases.size,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                SafetyCasesUiState(),
            )

    init { viewModelScope.launch { publicRepository.realtimeWakeUps().collect { refreshNow() } } }
    fun refresh() { viewModelScope.launch { refreshNow() } }
    private suspend fun refreshNow() {
        loading.value = true
        try { publicRepository.refreshCases(); error.value = null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error.value = "No se pudo actualizar. Se muestra la última información pública guardada." }
        finally { loading.value = false }
    }
}
