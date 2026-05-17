package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.obd.DiagnosticSeverity
import com.elysium369.meet.core.obd.ExpertDiagnosticProcedure
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors

import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar

@Composable
fun ExpertDiagnosticScreen(
    viewModel: ObdViewModel,
    onNavigateBack: () -> Unit
) {
    val diagnostics by viewModel.localDiagnostics.collectAsState()

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "DIAGNÓSTICO EXPERTO LOCAL",
                onBackClick = onNavigateBack,
                backgroundColor = MeetColors.backgroundDark
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { padding ->
        if (diagnostics.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay datos de telemetría disponibles.\nConecte el escáner y encienda el motor.",
                    color = MeetColors.textSecondary,
                    fontSize = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(diagnostics) { procedure ->
                    DiagnosticProcedureCard(procedure)
                }
            }
        }
    }
}

@Composable
fun DiagnosticProcedureCard(procedure: ExpertDiagnosticProcedure) {
    val severityColor = when (procedure.severity) {
        DiagnosticSeverity.INFO -> MeetColors.success
        DiagnosticSeverity.MODERATE -> MeetColors.warning
        DiagnosticSeverity.HIGH -> MeetColors.warning // Naranja intenso
        DiagnosticSeverity.CRITICAL -> MeetColors.error
    }

    val icon = when (procedure.severity) {
        DiagnosticSeverity.INFO -> Icons.Default.Info
        DiagnosticSeverity.CRITICAL, DiagnosticSeverity.HIGH -> Icons.Default.Warning
        else -> Icons.Default.Build
    }

    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MeetColors.cardBackground,
        borderColor = severityColor.copy(alpha = 0.3f),
        glowColor = severityColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = procedure.title,
                    color = severityColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = procedure.description,
                color = MeetColors.textPrimary,
                fontSize = 14.sp
            )

            if (procedure.probableCauses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Causas Probables:",
                    color = MeetColors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                procedure.probableCauses.forEach { cause ->
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        Text("•", color = MeetColors.textSecondary, modifier = Modifier.padding(end = 6.dp))
                        Text(text = cause, color = MeetColors.textPrimary, fontSize = 14.sp)
                    }
                }
            }

            if (procedure.testSteps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Procedimiento de Prueba:",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                procedure.testSteps.forEach { step ->
                    Text(
                        text = step,
                        color = MeetColors.textPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
