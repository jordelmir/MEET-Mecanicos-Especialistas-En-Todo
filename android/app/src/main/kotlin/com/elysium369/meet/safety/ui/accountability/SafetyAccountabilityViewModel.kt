package com.elysium369.meet.safety.ui.accountability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyInsightsRepository
import com.elysium369.meet.safety.data.PublicAccountabilityEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SafetyAccountabilityUiState(
    val events: List<PublicAccountabilityEvent> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val totalEvents: Int = 0,
)

@HiltViewModel
class SafetyAccountabilityViewModel @Inject constructor(
    private val publicRepository: SafetyInsightsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SafetyAccountabilityUiState())
    val uiState: StateFlow<SafetyAccountabilityUiState> = _uiState.asStateFlow()

    private var request: Job? = null

    init {
        loadEvents()
    }

    fun loadEvents() {
        request?.cancel()
        request = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val events = publicRepository.accountability()
                _uiState.update {
                    it.copy(
                        events = events,
                        isLoading = false,
                        totalEvents = events.size,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "No se pudieron consultar los eventos publicados. Vuelve a intentar.",
                    )
                }
            }
        }
    }

    fun refresh() = loadEvents()
}
