package com.elysium369.meet.safety.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.safety.ui.hub.SafetyHomeViewModel

@Composable
fun SafetyHomeEntry(
    onNavigateToSafetyHub: () -> Unit,
    viewModel: SafetyHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SafetyHomeCard(
        pendingLocalReports = uiState.pendingLocalReports,
        remoteAvailability = uiState.remoteAvailability,
        onClick = onNavigateToSafetyHub,
    )
}
