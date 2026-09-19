package com.elysium369.meet.safety.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(SafetyHomeUiState())
    val uiState: StateFlow<SafetyHomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeState()
    }

    private fun loadHomeState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val pending = safetyRepository.pendingCount()
                val total = safetyRepository.totalReportCount()
                val remote = when {
                    total == 0 -> RemoteAvailability.UNKNOWN
                    pending > 0 -> RemoteAvailability.DEGRADED
                    else -> RemoteAvailability.ONLINE
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingLocalReports = pending,
                        totalReportCount = total,
                        remoteAvailability = remote,
                        lastConfirmedRemoteAt = if (pending == 0 && total > 0) System.currentTimeMillis() else null,
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
