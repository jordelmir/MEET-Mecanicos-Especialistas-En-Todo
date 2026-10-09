package com.elysium369.meet.safety.ui.intelligence

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.safety.intelligence.domain.InvestigativeCaseLifecycle
import com.elysium369.meet.ui.theme.MeetColors

enum class WorkspaceSectionTab(
    val title: String,
    val icon: ImageVector,
) {
    ENTITY_GRAPH("Grafo Societario", Icons.Default.AccountTree),
    OFFICIAL_SOURCES("Fuentes & SICOP", Icons.Default.FolderSpecial),
    ANOMALY_ENGINE("Motor de Anomalías", Icons.Default.WarningAmber),
    HYPOTHESIS_MATRIX("Falsación Popper", Icons.Default.Balance),
}

/**
 * World-Class Investigative Intelligence Workspace Screen.
 *
 * Designed for public-interest investigations, institutional integrity,
 * and parliamentary oversight:
 * - Case workspace with strict lifecycle and two-person review audit.
 * - Interactive Entity Relationship Graph (SICOP, Registro Nacional).
 * - Explainable anomaly engine with mandatory alternative explanations.
 * - Popperian hypothesis vs counter-hypothesis matrix.
 * - Privacy-preserving redacted export generator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyInvestigativeWorkspaceScreen(
    onNavigateBack: () -> Unit = {},
) {
    var selectedTab by remember { mutableStateOf(WorkspaceSectionTab.ENTITY_GRAPH) }
    var showExportDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "ESPACIO INVESTIGATIVO",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    letterSpacing = 1.sp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MeetColors.cardBackground,
                                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
                                ) {
                                    Text(
                                        "EXP-2026-CR-012",
                                        color = MeetColors.cyberCyan,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                "Inteligencia Financiera · SICOP · Redes Societarias",
                                fontSize = 10.sp,
                                color = MeetColors.textSecondary,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Exportar",
                            tint = MeetColors.neonGreen,
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Case Header & Governance Banner
            item {
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
                            Text(
                                "ESTADO DEL EXPEDIENTE",
                                color = MeetColors.cyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MeetColors.backgroundDeep,
                                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(MeetColors.neonGreen))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "REVISIÓN EDITORIAL (FASE 6/8)",
                                        color = MeetColors.neonGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Auditoría de Adjudicaciones SICOP en Obra Pública y Vínculos Societarios Cruzados",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            "Organización: Unidad de Integridad y Transparencia Institucional · Clasificación: CONFIDENCIAL_INSTITUCIONAL",
                            color = MeetColors.textSecondary,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            // Tabs Selector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WorkspaceSectionTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedTab = tab },
                            color = if (isSelected) MeetColors.cyberCyan.copy(alpha = 0.2f) else MeetColors.cardBackground,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MeetColors.cyberCyan else MeetColors.borderSubtle,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (isSelected) MeetColors.cyberCyan else MeetColors.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    tab.title,
                                    color = if (isSelected) Color.White else MeetColors.textSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // Tab Content
            when (selectedTab) {
                WorkspaceSectionTab.ENTITY_GRAPH -> {
                    item {
                        SafetyEntityGraphView()
                    }
                }
                WorkspaceSectionTab.OFFICIAL_SOURCES -> {
                    item {
                        SafetySourceRegistryCard()
                    }
                }
                WorkspaceSectionTab.ANOMALY_ENGINE -> {
                    item {
                        SafetyAnomalySignalCard()
                    }
                }
                WorkspaceSectionTab.HYPOTHESIS_MATRIX -> {
                    item {
                        SafetyHypothesisMatrixCard()
                    }
                }
            }

            // Export Action Button
            item {
                Button(
                    onClick = { showExportDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "EXPORTAR DOSSIER FORENSE REDACTADO (OIJ / FISCALÍA)",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showExportDialog) {
        SafetyRedactedDossierExportDialog(onDismiss = { showExportDialog = false })
    }
}
