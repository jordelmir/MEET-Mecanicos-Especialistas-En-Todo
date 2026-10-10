package com.elysium369.meet.safety.ui.hub

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.R
import com.elysium369.meet.safety.domain.RemoteAvailability
import com.elysium369.meet.safety.domain.SafetyReportCategory
import com.elysium369.meet.safety.ui.common.PulseState
import com.elysium369.meet.safety.ui.common.SafetyHaptics
import com.elysium369.meet.safety.ui.common.SafetyOfflineBanner
import com.elysium369.meet.safety.ui.common.SafetyPulse
import com.elysium369.meet.safety.ui.institutional.components.SafetyEpistemicTraceabilityCard
import com.elysium369.meet.safety.ui.institutional.components.SafetyEvidenceChainVisualizer
import com.elysium369.meet.safety.ui.institutional.components.SafetyEvidenceManagerPanel
import com.elysium369.meet.safety.ui.institutional.components.SafetyFinancialIntelligenceCard
import com.elysium369.meet.safety.ui.institutional.components.SafetyIncidentTypologyExplorer
import com.elysium369.meet.safety.ui.institutional.components.SafetyInstitutionalBridgeCard
import com.elysium369.meet.safety.ui.institutional.components.SafetyInstitutionalBriefExportDialog
import com.elysium369.meet.safety.ui.institutional.components.SafetyTerritorialIntelligenceConsole
import com.elysium369.meet.safety.ui.intelligence.SafetyInvestigativeWorkspaceScreen
import com.elysium369.meet.ui.theme.MeetColors

enum class SafetyHubTab(
    val title: String,
    val icon: ImageVector,
) {
    OPERATIONS("Operaciones & Reporte", Icons.Default.Shield),
    TYPOLOGIES("8 Tipologías Orden Maestra", Icons.Default.WarningAmber),
    FINANCIAL_SICOP("Inteligencia SICOP", Icons.Default.AccountTree),
    EVIDENCE_FORENSICS("Custodia & Integridad", Icons.Default.Fingerprint),
    TERRITORIAL_RADAR("Inteligencia Territorial", Icons.Default.Place),
}

/**
 * Unified Elysium Safety Core Hub.
 *
 * Implements the complete master directive:
 * - Direct citizen reporting and operational modules (Map, My Reports, Cases, Timelines).
 * - Master 8 incident typologies with evidence requirements and direct report actions.
 * - Public-interest financial intelligence (SICOP Ley 9986, non-criminality of wealth safeguard).
 * - Full investigative workspace (corporate entity graph, cycle detection, deterministic anomaly engine, Popperian hypothesis matrix).
 * - Digital evidence management (SHA-256 on original bytes, 1-byte tamper simulation, SAFETY-CUSTODY-V2).
 * - Territorial intelligence console (cantonal breakdown, 25 km blur).
 * - Institutional bridge (Fuerza Pública, OIJ, Fiscalía, Poder Judicial).
 * - Forensic brief generator with QR and Merkle/SHA-256 manifest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyHubScreen(
    onNavigateToMap: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToReportCategory: (SafetyReportCategory) -> Unit = { onNavigateToReport() },
    onNavigateToMyReports: () -> Unit = {},
    onNavigateToCases: () -> Unit = {},
    onNavigateToTimelines: () -> Unit = {},
    onNavigateToAccountability: () -> Unit = {},
    onNavigateToObservatory: () -> Unit = {},
    onNavigateToResearch: () -> Unit = {},
    onNavigateToInstitutional: () -> Unit = {},
    onNavigateToInvestigativeWorkspace: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SafetyHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    var selectedTab by remember { mutableStateOf(SafetyHubTab.OPERATIONS) }
    var isContinuousDocumentMode by remember { mutableStateOf(false) }
    var showInvestigativeWorkspace by remember { mutableStateOf(false) }
    var showExportBriefDialog by remember { mutableStateOf(false) }

    val pulseState = when {
        uiState.error != null -> PulseState.ERROR
        uiState.pendingLocalReports > 0 -> PulseState.PENDING
        else -> PulseState.NOMINAL
    }

    BackHandler(enabled = true) {
        if (showInvestigativeWorkspace) {
            showInvestigativeWorkspace = false
        } else {
            onBack()
        }
    }

    if (showInvestigativeWorkspace) {
        SafetyInvestigativeWorkspaceScreen(
            onNavigateBack = { showInvestigativeWorkspace = false },
        )
        return
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MeetColors.cardBackground,
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("🇨🇷", fontSize = 13.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "SEGURIDAD",
                                    color = MeetColors.cyberCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "ELYSIUM SAFETY",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = Color.White,
                                letterSpacing = 1.2.sp,
                            )
                            Text(
                                "Seguridad Ciudadana · Evidencia Digital · Inteligencia Territorial",
                                fontSize = 10.sp,
                                color = MeetColors.textSecondary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.safety_back),
                            tint = MeetColors.textPrimary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        SafetyHaptics.selectionTick(view)
                        onNavigateToInstitutional()
                    }) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = "Presentación Institucional",
                            tint = MeetColors.neonGreen,
                        )
                    }
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

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
                                    color = MeetColors.textPrimary,
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

            // Parliamentary Deputies Mode Hero Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            SafetyHaptics.selectionTick(view)
                            onNavigateToInstitutional()
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.2.dp, MeetColors.neonGreen.copy(alpha = 0.5f)),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MeetColors.neonGreen.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("🏛️", fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "MODO DIPUTADOS (COSTA RICA)",
                                        color = MeetColors.neonGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("🇨🇷", fontSize = 11.sp)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Presentación Institucional: Los 7 Pilares de Seguridad y Evidencia",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Abrir presentación",
                            tint = MeetColors.neonGreen,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // View Mode & Tab Switcher Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "MÓDULOS DE LA ORDEN MAESTRA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MeetColors.cyberCyan,
                        letterSpacing = 1.2.sp,
                    )
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                SafetyHaptics.selectionTick(view)
                                isContinuousDocumentMode = !isContinuousDocumentMode
                            },
                        color = if (isContinuousDocumentMode) MeetColors.neonGreen.copy(alpha = 0.15f) else MeetColors.cardBackground,
                        border = BorderStroke(
                            1.dp,
                            if (isContinuousDocumentMode) MeetColors.neonGreen else MeetColors.borderSubtle,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            if (isContinuousDocumentMode) "📄 TODO EN UNO" else "📑 POR PESTAÑAS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isContinuousDocumentMode) MeetColors.neonGreen else MeetColors.textSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Tab Selector (when not in full continuous mode)
            if (!isContinuousDocumentMode) {
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(SafetyHubTab.entries.toTypedArray()) { tab ->
                            val isSelected = selectedTab == tab
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        SafetyHaptics.selectionTick(view)
                                        selectedTab = tab
                                    },
                                color = if (isSelected) MeetColors.cyberCyan.copy(alpha = 0.15f) else MeetColors.cardBackground,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MeetColors.cyberCyan else MeetColors.borderSubtle,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) MeetColors.cyberCyan else MeetColors.textSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        tab.title,
                                        color = if (isSelected) Color.White else MeetColors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SECTIONS DISPATCH
            // ==========================================

            // 1. OPERATIONS & FIELD MODULES
            if (isContinuousDocumentMode || selectedTab == SafetyHubTab.OPERATIONS) {
                // Quick Report Hero Button
                item {
                    Button(
                        onClick = {
                            SafetyHaptics.selectionTick(view)
                            onNavigateToReport()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    ) {
                        Icon(Icons.Default.AddAlert, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "🚨 REPORTAR INCIDENTE DE SEGURIDAD AHORA",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text("⚖️", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "SALVAGUARDA CONSTITUCIONAL & DEBIDO PROCESO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB300),
                                    letterSpacing = 0.5.sp,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Evidencia ≠ Culpabilidad · Anomalía ≠ Delito. La posesión de bienes de alto valor no constituye delito por sí misma. Elysium Safety estructura y preserva la verdad fáctica sin emitir condenas ni vulnerar el debido proceso.",
                                    fontSize = 11.sp,
                                    color = MeetColors.textSecondary,
                                    lineHeight = 15.sp,
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        "⚡ REPORTE RÁPIDO POR TIPOLOGÍA (1 TAP)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MeetColors.cyberCyan,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }

                item {
                    SafetyQuickTypologiesGrid(
                        onSelectCategory = { category ->
                            SafetyHaptics.selectionTick(view)
                            onNavigateToReportCategory(category)
                        },
                    )
                }

                item {
                    SafetyHubCard(
                        title = "🏛️ Presentación Institucional (Diputados)",
                        subtitle = "Vista ejecutiva parlamentaria, 7 pilares y marco de soberanía nacional",
                        icon = Icons.Filled.AccountBalance,
                        iconColor = MeetColors.neonGreen,
                        onClick = {
                            SafetyHaptics.selectionTick(view)
                            onNavigateToInstitutional()
                        },
                        enabled = true,
                    )
                }

                item {
                    SafetyHubCard(
                        title = "💼 Espacio Investigativo & Grafos SICOP",
                        subtitle = "Auditoría de contratación pública, redes de control y matriz popperiana",
                        icon = Icons.Filled.AccountTree,
                        iconColor = Color(0xFFFFB300),
                        onClick = {
                            SafetyHaptics.selectionTick(view)
                            showInvestigativeWorkspace = true
                        },
                        enabled = true,
                    )
                }

                item {
                    SafetyHubCard(
                        title = "🔬 Plataforma Científica & Research",
                        subtitle = "Claims, Timeline, Hipótesis, Replicaciones, Paquetes Forenses",
                        icon = Icons.Filled.Analytics,
                        iconColor = MeetColors.electricBlue,
                        onClick = {
                            SafetyHaptics.selectionTick(view)
                            onNavigateToResearch()
                        },
                        enabled = true,
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
                        enabled = true,
                    )
                }

                item {
                    SafetyHubCard(
                        title = stringResource(R.string.safety_my_reports_title),
                        subtitle = stringResource(R.string.safety_hub_card_my_reports_desc),
                        icon = Icons.Filled.Assignment,
                        iconColor = MeetColors.electricBlue,
                        onClick = {
                            SafetyHaptics.selectionTick(view)
                            onNavigateToMyReports()
                        },
                        enabled = true,
                    )
                }

                item {
                    SafetyHubCard(
                        title = stringResource(R.string.safety_hub_card_cases),
                        subtitle = stringResource(R.string.safety_hub_card_cases_desc),
                        icon = Icons.Filled.FolderOpen,
                        iconColor = MeetColors.cyberCyan,
                        onClick = {
                            SafetyHaptics.selectionTick(view)
                            onNavigateToCases()
                        },
                        enabled = true,
                    )
                }

                item {
                    SafetyHubCard(
                        title = stringResource(R.string.safety_hub_card_timelines),
                        subtitle = stringResource(R.string.safety_hub_card_timelines_desc),
                        icon = Icons.Filled.Timeline,
                        iconColor = MeetColors.hotMagenta,
                        onClick = {
                            SafetyHaptics.selectionTick(view)
                            onNavigateToTimelines()
                        },
                        enabled = true,
                    )
                }

                item {
                    SafetyHubCard(
                        title = stringResource(R.string.safety_hub_card_accountability),
                        subtitle = stringResource(R.string.safety_hub_card_accountability_desc),
                        icon = Icons.Filled.AccountBalance,
                        iconColor = MeetColors.neonGreen,
                        onClick = {
                            SafetyHaptics.selectionTick(view)
                            onNavigateToAccountability()
                        },
                        enabled = true,
                    )
                }

                item {
                    SafetyHubCard(
                        title = stringResource(R.string.safety_hub_card_observatory),
                        subtitle = stringResource(R.string.safety_hub_card_observatory_desc),
                        icon = Icons.Filled.Analytics,
                        iconColor = MeetColors.hotMagenta,
                        onClick = {
                            SafetyHaptics.selectionTick(view)
                            onNavigateToObservatory()
                        },
                        enabled = true,
                    )
                }
            }

            // 2. MASTER DIRECTIVE 8 TYPOLOGIES
            if (isContinuousDocumentMode || selectedTab == SafetyHubTab.TYPOLOGIES) {
                item {
                    SafetyIncidentTypologyExplorer(
                        onNavigateToReport = onNavigateToReport,
                        onNavigateToReportCategory = onNavigateToReportCategory,
                    )
                }
            }

            // 3. FINANCIAL INTELLIGENCE & SICOP
            if (isContinuousDocumentMode || selectedTab == SafetyHubTab.FINANCIAL_SICOP) {
                item {
                    SafetyFinancialIntelligenceCard(
                        onOpenInvestigativeWorkspace = { showInvestigativeWorkspace = true },
                    )
                }
            }

            // 4. EVIDENCE CUSTODY & INTEGRITY FORENSICS
            if (isContinuousDocumentMode || selectedTab == SafetyHubTab.EVIDENCE_FORENSICS) {
                item {
                    SafetyEvidenceManagerPanel()
                }
                item {
                    SafetyEvidenceChainVisualizer()
                }
                item {
                    SafetyEpistemicTraceabilityCard()
                }
            }

            // 5. TERRITORIAL RADAR & INSTITUTIONAL BRIDGE
            if (isContinuousDocumentMode || selectedTab == SafetyHubTab.TERRITORIAL_RADAR) {
                item {
                    SafetyTerritorialIntelligenceConsole()
                }
                item {
                    SafetyInstitutionalBridgeCard()
                }
                item {
                    Button(
                        onClick = {
                            SafetyHaptics.selectionTick(view)
                            showExportBriefDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "GENERAR EXPEDIENTE INSTITUCIONAL (OIJ / FISCALÍA)",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
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

    if (showExportBriefDialog) {
        SafetyInstitutionalBriefExportDialog(onDismiss = { showExportBriefDialog = false })
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
                    color = if (enabled) MeetColors.textPrimary else MeetColors.textSecondary,
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

@Composable
private fun SafetyQuickTypologiesGrid(
    onSelectCategory: (SafetyReportCategory) -> Unit,
) {
    val typologies = remember {
        listOf(
            Triple(SafetyReportCategory.EMERGENCY, "🚨 Emergencia", MeetColors.neonGreen),
            Triple(SafetyReportCategory.HOMICIDE, "🔫 Homicidio", MeetColors.error),
            Triple(SafetyReportCategory.MISSING_PERSON, "👤 Desaparición", MeetColors.cyberCyan),
            Triple(SafetyReportCategory.DRUG_SALE_ACTIVITY, "💊 Narcotráfico", Color(0xFFBA68C8)),
            Triple(SafetyReportCategory.ASSAULT_ROBBERY, "🔪 Asalto / Robo", MeetColors.warning),
            Triple(SafetyReportCategory.VIOLENT_INCIDENT, "⚠️ Violencia", MeetColors.hotMagenta),
            Triple(SafetyReportCategory.SUSPICIOUS_SITUATION, "👁️ Sospecha", Color(0xFF80D8FF)),
            Triple(SafetyReportCategory.ZONE_INCIDENT, "🏘️ Territorial", MeetColors.electricBlue),
            Triple(SafetyReportCategory.CORRUPTION_PUBLIC_PROCUREMENT, "🏛️ SICOP / Compras", Color(0xFFFFB300)),
            Triple(SafetyReportCategory.CORPORATE_OPACITY_CONFLICT, "🏢 Red Corporativa", Color(0xFFAB47BC)),
            Triple(SafetyReportCategory.FINANCIAL_FRAUD, "💰 Fraude Financiero", Color(0xFF00E676)),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in typologies.indices step 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val first = typologies[i]
                QuickTypologyButton(
                    modifier = Modifier.weight(1f),
                    title = first.second,
                    color = first.third,
                    onClick = { onSelectCategory(first.first) },
                )
                if (i + 1 < typologies.size) {
                    val second = typologies[i + 1]
                    QuickTypologyButton(
                        modifier = Modifier.weight(1f),
                        title = second.second,
                        color = second.third,
                        onClick = { onSelectCategory(second.first) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTypologyButton(
    modifier: Modifier,
    title: String,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MeetColors.cardBackground,
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.2f),
                modifier = Modifier.size(20.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
    }
}

