package com.elysium369.meet.safety.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.safety.data.local.SafetyReportEntity
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyMyReportsScreen(
    reports: List<SafetyReportEntity> = emptyList(),
    onBack: () -> Unit = {},
) {
    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MIS REPORTES", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.White)
                        Text("${reports.size} reporte(s)", fontSize = 11.sp, color = MeetColors.cyberCyan)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        if (reports.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
            ) {
                Text("SIN REPORTES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tus reportes de seguridad aparecerán aquí.", fontSize = 14.sp, color = MeetColors.textMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(reports) { report ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    report.category.replace("_", " "),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MeetColors.textPrimary,
                                )
                                Text(
                                    report.syncState,
                                    fontSize = 12.sp,
                                    color = when (report.syncState) {
                                        "SYNCED" -> MeetColors.neonGreen
                                        "FAILED" -> MeetColors.error
                                        else -> MeetColors.textMuted
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(report.localState, fontSize = 12.sp, color = MeetColors.textSecondary)
                            if (report.serverVersion > 0) {
                                Text("v${report.serverVersion}", fontSize = 11.sp, color = MeetColors.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
