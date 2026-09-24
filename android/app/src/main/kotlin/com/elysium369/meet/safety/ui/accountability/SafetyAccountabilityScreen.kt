package com.elysium369.meet.safety.ui.accountability

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.R
import com.elysium369.meet.safety.data.PublicAccountabilityEvent
import com.elysium369.meet.safety.ui.common.AccountabilityGauge
import com.elysium369.meet.safety.ui.common.SafetyEmptyState
import com.elysium369.meet.safety.ui.common.SafetyShimmer
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyAccountabilityScreen(
    onBack: () -> Unit = {},
    viewModel: SafetyAccountabilityViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.safety_accountability_title),
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                        Text(
                            if (uiState.isLoading) stringResource(R.string.safety_loading)
                            else stringResource(R.string.safety_accountability_events_subtitle, uiState.totalEvents),
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.safety_public_refresh), color = MeetColors.cyberCyan)
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.events.isEmpty() -> {
                SafetyShimmer(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(top = 16.dp),
                )
            }

            uiState.error != null && uiState.events.isEmpty() -> {
                SafetyEmptyState(
                    icon = Icons.Filled.AccessTime,
                    title = stringResource(R.string.safety_error),
                    message = uiState.error!!,
                    actionLabel = stringResource(R.string.safety_retry),
                    onAction = viewModel::refresh,
                    modifier = Modifier.padding(padding),
                )
            }

            uiState.events.isEmpty() -> {
                SafetyEmptyState(
                    icon = Icons.Filled.AccountBalance,
                    title = stringResource(R.string.safety_accountability_empty_title),
                    message = stringResource(R.string.safety_accountability_empty_desc),
                    actionLabel = stringResource(R.string.safety_public_refresh),
                    onAction = viewModel::refresh,
                    modifier = Modifier.padding(padding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    // Epistemic Transparency Notice
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground.copy(alpha = 0.7f)),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MeetColors.cyberCyan,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.safety_public_epistemic_notice),
                                    fontSize = 11.sp,
                                    color = MeetColors.textSecondary,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }

                    // Accountability Gauges grouped by case & institution
                    val grouped = uiState.events.groupBy { it.case_id to it.institution_ref }
                    grouped.forEach { (_, events) ->
                        item {
                            AccountabilityClockCard(events)
                        }
                    }

                    item {
                        Text(
                            "EVENTOS DE RESPUESTA PÚBLICA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MeetColors.cyberCyan,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    items(uiState.events, key = { it.event_id }) { event ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { it / 4 },
                        ) {
                            AccountabilityEventCard(event = event)
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AccountabilityEventCard(event: PublicAccountabilityEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val statusColor = when (event.event_type) {
                "REPORT_SENT" -> MeetColors.cyberCyan
                "DELIVERY_CONFIRMED" -> MeetColors.neonGreen
                "PUBLIC_ACTION_FOUND" -> MeetColors.warning
                "RESPONSE_DOCUMENTED" -> MeetColors.neonGreen
                else -> MeetColors.textSecondary
            }

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        event.event_type.replace("_", " "),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                    )
                    Text(
                        event.occurred_at.take(10),
                        fontSize = 11.sp,
                        color = MeetColors.textSecondary,
                    )
                }

                if (event.case_title != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        event.case_title,
                        fontSize = 12.sp,
                        color = MeetColors.cyberCyan,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    event.institution_ref,
                    fontSize = 11.sp,
                    color = MeetColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun AccountabilityClockCard(events: List<PublicAccountabilityEvent>) {
    val metrics = AccountabilityClock.calculate(events, java.time.Instant.now())
    val firstEvent = events.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        firstEvent?.case_title ?: stringResource(R.string.safety_accountability_case_default),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color.White,
                    )
                    Text(
                        firstEvent?.institution_ref ?: "",
                        fontSize = 12.sp,
                        color = MeetColors.cyberCyan,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MeetColors.backgroundDeep)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "CRONÓMETRO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = MeetColors.neonGreen,
                        letterSpacing = 1.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Animated Visual Gauge
            AccountabilityGauge(
                metrics = metrics,
                milestoneTypes = events.map { it.event_type },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Detailed Metric Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeetColors.backgroundDeep)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Desde envío", fontSize = 10.sp, color = MeetColors.textSecondary, fontWeight = FontWeight.Bold)
                    Text(
                        metrics.daysSinceReport?.let { "${it}d" } ?: "N/D",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = MeetColors.cyberCyan,
                    )
                }
                Column {
                    Text("Acuse recibo", fontSize = 10.sp, color = MeetColors.textSecondary, fontWeight = FontWeight.Bold)
                    Text(
                        metrics.timeToReceipt?.toHours()?.let { "${it}h" } ?: "Pendiente",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = if (metrics.timeToReceipt != null) MeetColors.neonGreen else MeetColors.warning,
                    )
                }
                Column {
                    Text("1ª Respuesta", fontSize = 10.sp, color = MeetColors.textSecondary, fontWeight = FontWeight.Bold)
                    Text(
                        metrics.timeToFirstResponse?.toHours()?.let { "${it}h" } ?: "Sin registro",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = if (metrics.timeToFirstResponse != null) MeetColors.neonGreen else MeetColors.error,
                    )
                }
                Column {
                    Text("Última acción", fontSize = 10.sp, color = MeetColors.textSecondary, fontWeight = FontWeight.Bold)
                    Text(
                        metrics.daysSinceLastAction?.let { "${it}d" } ?: "—",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
