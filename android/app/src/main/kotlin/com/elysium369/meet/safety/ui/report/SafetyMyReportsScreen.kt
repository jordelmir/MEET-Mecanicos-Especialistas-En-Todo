package com.elysium369.meet.safety.ui.report

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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.elysium369.meet.R
import com.elysium369.meet.safety.ui.common.SafetyCategoryIcons
import com.elysium369.meet.safety.ui.common.SafetyEmptyState
import com.elysium369.meet.safety.ui.common.SafetyShimmer
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyMyReportsScreen(
    viewModel: SafetyMyReportsViewModel,
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var reportToWithdraw by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.safety_my_reports_title),
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                        Text(
                            stringResource(R.string.safety_my_reports_subtitle, state.totalReports, state.pendingCount),
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.reports.isEmpty() -> {
                SafetyShimmer(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(top = 16.dp),
                )
            }

            state.error != null && state.reports.isEmpty() -> {
                SafetyEmptyState(
                    icon = Icons.Filled.Assignment,
                    title = stringResource(R.string.safety_error),
                    message = state.error!!,
                    modifier = Modifier.padding(padding),
                )
            }

            state.reports.isEmpty() -> {
                SafetyEmptyState(
                    icon = Icons.Filled.Assignment,
                    title = stringResource(R.string.safety_my_reports_empty_title),
                    message = stringResource(R.string.safety_my_reports_empty_message),
                    modifier = Modifier.padding(padding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    items(state.reports, key = { it.reportId }) { report ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { it / 4 },
                        ) {
                            MyReportCard(
                                report = report,
                                withdrawing = state.withdrawingReportId == report.reportId,
                                onWithdraw = { reportToWithdraw = report.reportId },
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    reportToWithdraw?.let { reportId ->
        AlertDialog(
            onDismissRequest = { reportToWithdraw = null },
            containerColor = MeetColors.cardBackground,
            title = {
                Text(
                    stringResource(R.string.safety_my_reports_withdraw_dialog_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    stringResource(R.string.safety_my_reports_withdraw_dialog_text),
                    color = MeetColors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        reportToWithdraw = null
                        viewModel.withdraw(reportId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.error,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.safety_withdraw), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { reportToWithdraw = null }) {
                    Text(stringResource(R.string.safety_cancel), color = MeetColors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun MyReportCard(
    report: com.elysium369.meet.safety.data.local.SafetyReportEntity,
    withdrawing: Boolean,
    onWithdraw: () -> Unit,
) {
    val categoryColor = SafetyCategoryIcons.colorForString(report.category)
    val categoryIcon = SafetyCategoryIcons.iconForString(report.category)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(categoryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        report.category.replace("_", " "),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                    )
                }

                val syncColor = when (report.syncState) {
                    "SYNCED" -> MeetColors.neonGreen
                    "FAILED" -> MeetColors.error
                    else -> MeetColors.cyberCyan
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(syncColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        report.syncState,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = syncColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MeetColors.backgroundDeep)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("ESTADO LOCAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary)
                    Text(report.localState, fontSize = 12.sp, color = MeetColors.textPrimary)
                }

                if (report.serverVersion > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MeetColors.cyberCyan.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("v${report.serverVersion} autorizada", fontSize = 10.sp, color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onWithdraw,
                    enabled = !withdrawing,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MeetColors.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (withdrawing) stringResource(R.string.safety_withdrawing)
                        else stringResource(R.string.safety_my_reports_remove),
                        color = MeetColors.error,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
