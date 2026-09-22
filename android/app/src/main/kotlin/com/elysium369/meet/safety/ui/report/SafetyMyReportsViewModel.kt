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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject

data class SafetyMyReportsUiState(
    val reports: List<SafetyReportEntity> = emptyList(),
    val totalReports: Int = 0,
    val pendingCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val withdrawingReportId: String? = null,
    val actionMessage: String? = null,
)

@HiltViewModel
class SafetyMyReportsViewModel @Inject constructor(
    private val repository: SafetyRepository,
) : ViewModel() {

    private val withdrawal = MutableStateFlow<Pair<String?, String?>>(null to null)

    val state: StateFlow<SafetyMyReportsUiState> = kotlinx.coroutines.flow.combine(repository.observeMyReports(), withdrawal) { reports, action ->
            val pending = reports.count { it.syncState != "SYNCED" }
            SafetyMyReportsUiState(
                reports = reports,
                totalReports = reports.size,
                pendingCount = pending,
                isLoading = false,
                error = null,
                withdrawingReportId = action.first,
                actionMessage = action.second,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SafetyMyReportsUiState(isLoading = true),
        )

    fun withdraw(reportId: String) {
        if (withdrawal.value.first != null) return
        withdrawal.value = reportId to null
        viewModelScope.launch {
            runCatching { repository.withdrawReport(reportId) }
                .onSuccess { withdrawal.value = null to "Reporte retirado y contenido privado eliminado." }
                .onFailure {
                    Log.e("ElysiumSafetyWithdrawal", "Withdrawal failed for $reportId", it)
                    withdrawal.value = null to "No se pudo quitar: ${it.message ?: "error de sincronización"}. Reintenta."
                }
        }
    }
}
