package com.elysium.vanguard.features.telemetry

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * TITAN TELEMETRY VIEWMODEL
 * Bridges the hardware system monitor to the UI layer.
 */
@HiltViewModel
class TelemetryViewModel @Inject constructor(
    val monitor: SystemMonitor
) : ViewModel()
