package com.elysium369.meet.safety.ui.observatory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyInsightsRepository
import com.elysium369.meet.safety.data.SafetyObservatoryFilters
import com.elysium369.meet.safety.data.SafetyObservatoryMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SafetyObservatoryUiState(
    val stats: SafetyObservatoryMetrics? = null,
    val filters: SafetyObservatoryFilters = SafetyObservatoryFilters(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class SafetyObservatoryViewModel @Inject constructor(private val repository: SafetyInsightsRepository) : ViewModel() {
    private val mutable = MutableStateFlow(SafetyObservatoryUiState())
    val uiState = mutable.asStateFlow()
    private var request: Job? = null
    init { refresh() }
    fun setFilters(filters: SafetyObservatoryFilters) { mutable.update { it.copy(filters = filters) } }
    fun refresh() {
        request?.cancel()
        request = viewModelScope.launch {
            val filters = mutable.value.filters
            mutable.update { it.copy(isLoading = true, error = null, stats = null) }
            try {
                val stats = repository.observatory(filters)
                mutable.update { it.copy(stats = stats, isLoading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutable.update { it.copy(isLoading = false, error = "No se pudieron consultar los datos. Revisa los filtros y vuelve a intentar.") }
            }
        }
    }
}
