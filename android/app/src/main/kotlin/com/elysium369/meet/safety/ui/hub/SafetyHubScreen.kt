package com.elysium369.meet.safety.ui.hub

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.elysium369.meet.R
import com.elysium369.meet.safety.domain.RemoteAvailability
import com.elysium369.meet.safety.ui.common.PulseState
import com.elysium369.meet.safety.ui.common.SafetyHaptics
import com.elysium369.meet.safety.ui.common.SafetyOfflineBanner
import com.elysium369.meet.safety.ui.common.SafetyPulse
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyHubScreen(
    onNavigateToMap: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToMyReports: () -> Unit = {},
    onNavigateToCases: () -> Unit = {},
    onNavigateToTimelines: () -> Unit = {},
    onNavigateToAccountability: () -> Unit = {},
    onNavigateToObservatory: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SafetyHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    val pulseState = when {
        uiState.error != null -> PulseState.ERROR
        uiState.pendingLocalReports > 0 -> PulseState.PENDING
        else -> PulseState.NOMINAL
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.safety_map_vanguard_title),
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                        Text(
                            stringResource(R.string.safety_hub_subtitle),
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.safety_back),
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        SafetyHaptics.selectionTick(view)
                        viewModel.refresh()
                    }) {
                        Icon(
                            Icons.Filled.Sync,
                            contentDescription = "Sincronizar",
                            tint = MeetColors.cyberCyan,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Offline Banner if offline or pending reports
            if (uiState.remoteAvailability == RemoteAvailability.OFFLINE || uiState.pendingLocalReports > 0) {
                item {
                    SafetyOfflineBanner(pendingCount = uiState.pendingLocalReports)
                }
            }

            // Hero Global Center Card with Pulse
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.55f)),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.safety_hub_global_center),
                                    color = MeetColors.cyberCyan,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                    fontSize = 12.sp,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.safety_hub_global_center_desc),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            SafetyPulse(state = pulseState, size = 52.dp)
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SafetyHubMetric(
                                stringResource(R.string.safety_my_reports_title),
                                uiState.totalReportCount.toString(),
                                Modifier.weight(1f),
                            )
                            SafetyHubMetric(
                                stringResource(R.string.safety_hub_pending),
                                uiState.pendingLocalReports.toString(),
                                Modifier.weight(1f),
                                highlightColor = if (uiState.pendingLocalReports > 0) MeetColors.warning else MeetColors.neonGreen,
                            )
                            SafetyHubMetric(
                                stringResource(R.string.safety_hub_network),
                                if (uiState.error == null && !uiState.isLoading) stringResource(R.string.safety_hub_network_active) else stringResource(R.string.safety_hub_network_review),
                                Modifier.weight(1f),
                            )
                        }

                        if (uiState.isLoading) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.safety_hub_verifying), color = MeetColors.textSecondary, fontSize = 12.sp)
                        }

                        uiState.error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.safety_hub_network_error), color = MeetColors.warning, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Navigation Sections
            item {
                Text(
                    "MÓDULOS DE SEGURIDAD CIUDADANA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MeetColors.cyberCyan,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                SafetyHubCard(
                    title = stringResource(R.string.safety_hub_card_map),
                    subtitle = stringResource(R.string.safety_hub_card_map_desc),
                    icon = Icons.Filled.Map,
                    iconColor = MeetColors.cyberCyan,
                    onClick = {
                        SafetyHaptics.selectionTick(view)
                        onNavigateToMap()
                    },
                    enabled = uiState.featureGates["safety_foundation"] == true && uiState.featureGates["safety_public_map"] == true,
                )
            }

            item {
                SafetyHubCard(
                    title = stringResource(R.string.safety_hub_card_report),
                    subtitle = stringResource(R.string.safety_hub_card_report_desc),
                    icon = Icons.Filled.ReportProblem,
                    iconColor = MeetColors.neonGreen,
                    onClick = {
                        SafetyHaptics.selectionTick(view)
                        onNavigateToReport()
                    },
                    enabled = uiState.featureGates["safety_foundation"] == true && uiState.featureGates["safety_reporting"] == true,
                )
            }

            item {
                SafetyHubCard(
                    title = stringResource(R.string.safety_my_reports_title),
                    subtitle = stringResource(R.string.safety_hub_card_my_reports_desc),
                    icon = Icons.Filled.Assignment,
                    iconColor = Color(0xFF00BCD4),
                    onClick = {
                        SafetyHaptics.selectionTick(view)
                        onNavigateToMyReports()
                    },
                )
            }

            item {
                SafetyHubCard(
                    title = stringResource(R.string.safety_hub_card_cases),
                    subtitle = stringResource(R.string.safety_hub_card_cases_desc),
                    icon = Icons.Filled.FolderOpen,
                    iconColor = Color(0xFFFFD600),
                    onClick = {
                        SafetyHaptics.selectionTick(view)
                        onNavigateToCases()
                    },
                    enabled = uiState.featureGates["safety_foundation"] == true && uiState.featureGates["safety_public_cases"] == true,
                )
            }

            item {
                SafetyHubCard(
                    title = stringResource(R.string.safety_hub_card_timelines),
                    subtitle = stringResource(R.string.safety_hub_card_timelines_desc),
                    icon = Icons.Filled.Timeline,
                    iconColor = Color(0xFFFF8C00),
                    onClick = {
                        SafetyHaptics.selectionTick(view)
                        onNavigateToTimelines()
                    },
                    enabled = uiState.featureGates["safety_foundation"] == true && uiState.featureGates["safety_public_cases"] == true,
                )
            }

            item {
                SafetyHubCard(
                    title = stringResource(R.string.safety_hub_card_accountability),
                    subtitle = stringResource(R.string.safety_hub_card_accountability_desc),
                    icon = Icons.Filled.AccountBalance,
                    iconColor = Color(0xFF4CAF50),
                    onClick = {
                        SafetyHaptics.selectionTick(view)
                        onNavigateToAccountability()
                    },
                    enabled = uiState.featureGates["safety_foundation"] == true && uiState.featureGates["safety_accountability"] == true,
                )
            }

            item {
                SafetyHubCard(
                    title = stringResource(R.string.safety_hub_card_observatory),
                    subtitle = stringResource(R.string.safety_hub_card_observatory_desc),
                    icon = Icons.Filled.Analytics,
                    iconColor = Color(0xFF9C27B0),
                    onClick = {
                        SafetyHaptics.selectionTick(view)
                        onNavigateToObservatory()
                    },
                    enabled = uiState.featureGates["safety_foundation"] == true && uiState.featureGates["safety_observatory"] == true,
                )
            }

            // Guardian Notice Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.safety_hub_guardian),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.textSecondary,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.safety_hub_guardian_desc),
                            fontSize = 12.sp,
                            color = MeetColors.textSecondary,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SafetyHubMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlightColor: Color = MeetColors.neonGreen,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MeetColors.backgroundDeep)
            .padding(12.dp),
    ) {
        Text(value, color = highlightColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(label, color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SafetyHubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MeetColors.cardBackground,
            disabledContainerColor = MeetColors.cardBackground.copy(alpha = 0.5f),
        ),
        border = BorderStroke(1.dp, if (enabled) MeetColors.borderSubtle else MeetColors.borderSubtle.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = if (enabled) 0.15f else 0.05f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) iconColor else iconColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color.White else MeetColors.textSecondary,
                    letterSpacing = 1.sp,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MeetColors.textSecondary,
                    lineHeight = 16.sp,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (enabled) MeetColors.cyberCyan else MeetColors.textSecondary.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
