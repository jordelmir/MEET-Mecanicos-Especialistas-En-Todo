package com.elysium369.meet.safety.ui.report

import android.content.Context
import android.net.Uri
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
    /** Private map points with decrypted videoUrls and narrative */
    val privatePointsByReport: Map<String, com.elysium369.meet.safety.data.SafetyPrivateMapPoint> = emptyMap(),
    /** Updates / Sightings grouped by reportId */
    val updatesByReport: Map<String, List<com.elysium369.meet.safety.data.local.SafetyReportUpdateEntity>> = emptyMap(),
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
        repository.observeMyPrivateMapPoints(),
        repository.observeAllReportUpdates(),
        withdrawal,
    ) { reports, allEvidence, privatePoints, allUpdates, action ->
        val pending = reports.count { it.syncState != "SYNCED" }
        val evidenceByReport = allEvidence.groupBy { it.reportId }
        val pointsByReport = privatePoints.associateBy { it.reportId }
        val updatesByReport = allUpdates.groupBy { it.reportId }
        SafetyMyReportsUiState(
            reports = reports,
            totalReports = reports.size,
            pendingCount = pending,
            isLoading = false,
            error = null,
            withdrawingReportId = action.first,
            actionMessage = action.second,
            evidenceByReport = evidenceByReport,
            privatePointsByReport = pointsByReport,
            updatesByReport = updatesByReport,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SafetyMyReportsUiState(isLoading = true),
        )

    init {
        // Trigger pending uploads and pull remote reports on screen open
        viewModelScope.launch {
            repository.resumePendingUploads()
            repository.refreshMyReports()
        }
    }

    fun retrySyncAll() {
        viewModelScope.launch {
            repository.resumePendingUploads()
            repository.refreshMyReports()
        }
    }

    fun openEvidence(context: Context, evidenceId: String) {
        evidenceRepository.openEvidence(context, evidenceId)
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

    fun addReportUpdate(
        reportId: String,
        occurredAt: Long,
        locationLabel: String,
        clothingAndFeatures: String,
        narrative: String,
        videoUrls: List<String>,
        attachmentUris: List<Uri> = emptyList(),
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                var stagedCount = 0
                attachmentUris.forEach { uri ->
                    try {
                        evidenceRepository.stage(reportId, uri)
                        stagedCount++
                    } catch (e: Exception) {
                        Log.e("SafetyMyReports", "Error staging attachment for report $reportId", e)
                    }
                }
                if (stagedCount > 0) {
                    repository.resumePendingUploads()
                }
                repository.addReportUpdate(
                    reportId = reportId,
                    occurredAt = occurredAt,
                    locationLabel = locationLabel,
                    clothingAndFeatures = clothingAndFeatures,
                    narrative = narrative,
                    videoUrls = videoUrls,
                    attachmentsCount = stagedCount,
                )
            }.onSuccess {
                withdrawal.value = null to "Nuevo avistamiento añadido al expediente con éxito."
                onSuccess()
            }.onFailure { err ->
                Log.e("SafetyMyReports", "Error adding update to report $reportId", err)
                withdrawal.value = null to "Error al registrar avistamiento: ${err.message}"
            }
        }
    }
}
