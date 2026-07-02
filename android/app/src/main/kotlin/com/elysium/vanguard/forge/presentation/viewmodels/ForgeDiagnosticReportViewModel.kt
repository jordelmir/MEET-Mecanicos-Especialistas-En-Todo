package com.elysium.vanguard.forge.presentation.viewmodels

import androidx.lifecycle.ViewModel
import com.elysium.vanguard.forge.domain.DiagnosticReport
import com.elysium.vanguard.forge.presentation.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ForgeDiagnosticReportViewModel — recibe un reporte ya construido.
 * En flujo normal lo crea el FailureLab o el DiagnosticEngine.
 */
class ForgeDiagnosticReportViewModel(
    initialReport: DiagnosticReport? = null
) : ViewModel() {

    private val _report = MutableStateFlow<UiState<DiagnosticReport>>(
        initialReport?.let { UiState.Ready(it) } ?: UiState.Empty
    )
    val report: StateFlow<UiState<DiagnosticReport>> = _report.asStateFlow()

    fun setReport(r: DiagnosticReport) {
        _report.value = UiState.Ready(r)
    }
}