package com.elysium369.meet.safety.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyRepository
import com.elysium369.meet.safety.data.local.SafetyReportEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SafetyMyReportsUiState(
    val reports: List<SafetyReportEntity> = emptyList(),
    val totalReports: Int = 0,
    val pendingCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class SafetyMyReportsViewModel @Inject constructor(
    private val repository: SafetyRepository,
) : ViewModel() {

    val state: StateFlow<SafetyMyReportsUiState> = repository.observeMyReports()
        .map { reports ->
            val pending = reports.count { it.syncState != "SYNCED" }
            SafetyMyReportsUiState(
                reports = reports,
                totalReports = reports.size,
                pendingCount = pending,
                isLoading = false,
                error = null,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SafetyMyReportsUiState(isLoading = true),
        )
}
