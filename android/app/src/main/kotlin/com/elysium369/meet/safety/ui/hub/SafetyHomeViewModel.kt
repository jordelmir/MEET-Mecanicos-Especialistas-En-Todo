package com.elysium369.meet.safety.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyRuntimeFeatureGates
import com.elysium369.meet.safety.data.SafetyRepository
import com.elysium369.meet.safety.domain.RemoteAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SafetyHomeUiState(
    val featureGates: Map<String, Boolean> = mapOf(
        "safety_foundation" to true,
        "safety_reporting" to true,
        "safety_evidence_upload" to true,
        "safety_public_map" to true,
        "safety_public_cases" to true,
        "safety_accountability" to true,
        "safety_observatory" to true,
        "safety_realtime" to true,
        "safety_guardian" to true,
    ),
    val pendingLocalReports: Int = 0,
    val totalReportCount: Int = 0,
    val publishedCaseCount: Int = 0,
    val publicPointCount: Int = 0,
    val remoteAvailability: RemoteAvailability = RemoteAvailability.UNKNOWN,
    val lastConfirmedRemoteAt: Long? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class SafetyHomeViewModel @Inject constructor(
    private val safetyRepository: SafetyRepository,
    private val gates: SafetyRuntimeFeatureGates,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SafetyHomeUiState())
    val uiState: StateFlow<SafetyHomeUiState> = _uiState.asStateFlow()

    init {
        safetyRepository.resumePendingUploads()
        viewModelScope.launch {
            gates.state.collect { features -> _uiState.update { it.copy(featureGates = features) } }
        }
        loadHomeState()
    }

    private fun loadHomeState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val features = gates.refresh()
                val pending = safetyRepository.pendingCount()
                val total = safetyRepository.totalReportCount()
                val remote = RemoteAvailability.UNKNOWN

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        featureGates = features,
                        pendingLocalReports = pending,
                        totalReportCount = total,
                        remoteAvailability = remote,
                        lastConfirmedRemoteAt = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Error al cargar datos de seguridad",
                    )
                }
            }
        }
    }

    fun refresh() = loadHomeState()
}
