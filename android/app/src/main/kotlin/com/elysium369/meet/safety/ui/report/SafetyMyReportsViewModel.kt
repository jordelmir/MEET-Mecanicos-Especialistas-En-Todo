package com.elysium369.meet.safety.ui.report

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyRepository
import com.elysium369.meet.safety.data.local.SafetyReportEntity
import com.elysium369.meet.safety.evidence.SafetyEvidenceEntity
import com.elysium369.meet.safety.evidence.SafetyEvidenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
    /** Evidence grouped by reportId */
    val evidenceByReport: Map<String, List<SafetyEvidenceEntity>> = emptyMap(),
)

@HiltViewModel
class SafetyMyReportsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SafetyRepository,
    private val evidenceRepository: SafetyEvidenceRepository,
) : ViewModel() {

    private val withdrawal = MutableStateFlow<Pair<String?, String?>>(null to null)

    val state: StateFlow<SafetyMyReportsUiState> = combine(
        repository.observeMyReports(),
        evidenceRepository.observeOwner(),
        withdrawal,
    ) { reports, allEvidence, action ->
        val pending = reports.count { it.syncState != "SYNCED" }
        val evidenceByReport = allEvidence.groupBy { it.reportId }
        SafetyMyReportsUiState(
            reports = reports,
            totalReports = reports.size,
            pendingCount = pending,
            isLoading = false,
            error = null,
            withdrawingReportId = action.first,
            actionMessage = action.second,
            evidenceByReport = evidenceByReport,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SafetyMyReportsUiState(isLoading = true),
        )

    init {
        // Trigger pending uploads on screen open
        viewModelScope.launch { repository.resumePendingUploads() }
    }

    fun retrySyncAll() {
        viewModelScope.launch {
            repository.resumePendingUploads()
        }
    }

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
