package com.elysium369.meet.safety.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyRepository
import com.elysium369.meet.safety.domain.RemoteAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SafetyHomeUiState(
    val pendingLocalReports: Int = 0,
    val publishedCaseCount: Int = 0,
    val publicPointCount: Int = 0,
    val remoteAvailability: RemoteAvailability = RemoteAvailability.UNKNOWN,
    val lastConfirmedRemoteAt: Long? = null,
    val isLoading: Boolean = true,
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
            _uiState.update { it.copy(isLoading = true) }
            // TODO: Load actual counts from repository
            _uiState.update {
                it.copy(
                    isLoading = false,
                    remoteAvailability = RemoteAvailability.UNKNOWN,
                )
            }
        }
    }

    fun refresh() = loadHomeState()
}
