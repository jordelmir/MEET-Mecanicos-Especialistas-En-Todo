package com.elysium369.meet.safety.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyPublicRepository
import com.elysium369.meet.safety.data.SafetyRuntimeFeatureGates
import com.elysium369.meet.safety.data.SafetyRepository
import com.elysium369.meet.safety.domain.RemoteAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    private val publicRepository: SafetyPublicRepository,
    private val gates: SafetyRuntimeFeatureGates,
) : ViewModel() {

    private val _base = MutableStateFlow(SafetyHomeUiState())

    // Reactively combine Room Flows so counts auto-update when any report/point/case changes
    val uiState: StateFlow<SafetyHomeUiState> = combine(
        _base,
        safetyRepository.observeMyReports(),
        publicRepository.observePoints(),
        publicRepository.observeCases(),
    ) { base, myReports, publicPoints, publicCases ->
        base.copy(
            totalReportCount = myReports.size,
            pendingLocalReports = myReports.count { it.syncState != "SYNCED" },
            publicPointCount = publicPoints.size,
            publishedCaseCount = publicCases.size,
            remoteAvailability = if (publicPoints.isNotEmpty() || publicCases.isNotEmpty())
                RemoteAvailability.ONLINE else base.remoteAvailability,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SafetyHomeUiState())

    init {
        safetyRepository.resumePendingUploads()
        viewModelScope.launch {
            gates.state.collect { features -> _base.update { it.copy(featureGates = features) } }
        }
        loadHomeState()
    }

    private fun loadHomeState() {
        viewModelScope.launch {
            _base.update { it.copy(isLoading = true, error = null) }
            try {
                val features = gates.refresh()
                // Refresh public data from Supabase into Room cache
                try { publicRepository.refreshPoints() } catch (_: Exception) {}
                try { publicRepository.refreshCases() } catch (_: Exception) {}
                _base.update {
                    it.copy(
                        isLoading = false,
                        featureGates = features,
                        lastConfirmedRemoteAt = System.currentTimeMillis(),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _base.update {
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
