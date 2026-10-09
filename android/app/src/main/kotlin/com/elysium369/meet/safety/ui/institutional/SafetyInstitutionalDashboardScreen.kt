package com.elysium369.meet.safety.ui.institutional

import android.view.View
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.safety.ui.PresentationMode
import com.elysium369.meet.safety.ui.SafetyInstitutionalPresentationMode
import com.elysium369.meet.safety.ui.common.SafetyHaptics
import com.elysium369.meet.safety.ui.institutional.components.SafetyEpistemicTraceabilityCard
import com.elysium369.meet.safety.ui.institutional.components.SafetyEvidenceChainVisualizer
import com.elysium369.meet.safety.ui.institutional.components.SafetyEvidenceManagerPanel
import com.elysium369.meet.safety.ui.institutional.components.SafetyFinancialIntelligenceCard
import com.elysium369.meet.safety.ui.institutional.components.SafetyIncidentTypologyExplorer
import com.elysium369.meet.safety.ui.institutional.components.SafetyInstitutionalBridgeCard
import com.elysium369.meet.safety.ui.institutional.components.SafetyInstitutionalBriefExportDialog
import com.elysium369.meet.safety.ui.institutional.components.SafetyTerritorialIntelligenceConsole
import com.elysium369.meet.ui.theme.MeetColors

enum class InstitutionalNavTab(
    val title: String,
    val icon: ImageVector,
) {
    VISION("Visión & 7 Pilares", Icons.Default.AccountBalance),
    TERRITORIAL_MAP("Mapa Territorial", Icons.Default.Map),
    REPORT_EVIDENCE("Reporte & Evidencia", Icons.Default.ReportProblem),
    INVESTIGATION_CASES("Cadena & Casos", Icons.Default.AccountTree),
    FINANCIAL_SICOP("Inteligencia SICOP", Icons.Default.BusinessCenter),
}

/**
 * World-Class Executive Parliamentary Dashboard for Elysium Safety.
 *
 * Exclusively presents:
 * - Citizen Security & Incident Reporting (8 official categories)
 * - Immutable Digital Evidence (SHA-256 + QR + CustodyProtocolV2)
 * - Territorial Geospatial Analysis (Cantons, hot zones, 25km privacy blur)
 * - Epistemic Traceability (OBSERVED -> AUTHORITATIVE)
 * - Evidence Chain (Evento -> Afirmación -> Hipótesis -> Evidencia -> Análisis -> Conclusión)
 * - Territorial Intelligence (Temporal trends, repetitive clusters)
 * - Citizen-Institution Bridge (OIJ, Fuerza Pública, Ministerio Público, Poder Judicial)
 * - Public-Interest Financial Intelligence & Anti-Corruption (SICOP Ley N.° 9986)
 *
 * Completely eliminates mechanics, automotive OBD, mobility/rides, and marketplace from the view,
 * while preserving the complete platform code intact in compliance with AGENTS.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyInstitutionalDashboardScreen(
    onNavigateToMap: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToMyReports: () -> Unit = {},
    onNavigateToCases: () -> Unit = {},
    onNavigateToTimelines: () -> Unit = {},
    onNavigateToObservatory: () -> Unit = {},
    onNavigateToResearch: () -> Unit = {},
    onTogglePresentationMode: () -> Unit = {},
    currentMode: PresentationMode = PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY,
) {
    var selectedTab by remember { mutableStateOf(InstitutionalNavTab.VISION) }
    var isContinuousDocumentMode by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    var showExportBriefDialog by remember { mutableStateOf(false) }
    val view = LocalView.current

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
                                Text("🇨🇷", fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "COSTA RICA",
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
                                "Seguridad Ciudadana · Evidencia · Inteligencia Territorial",
                                fontSize = 10.sp,
                                color = MeetColors.textSecondary,
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                SafetyHaptics.selectionTick(view)
                                showModeDialog = true
                            },
                        color = MeetColors.cardBackground,
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MeetColors.neonGreen),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "MODO DIPUTADOS",
                                color = MeetColors.neonGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MeetColors.cardBackground,
                tonalElevation = 8.dp,
            ) {
                InstitutionalNavTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            SafetyHaptics.selectionTick(view)
                            selectedTab = tab
                            isContinuousDocumentMode = false
                        },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) MeetColors.cyberCyan else MeetColors.textSecondary,
                            )
                        },
                        label = {
                            Text(
                                tab.title,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MeetColors.cyberCyan else MeetColors.textSecondary,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MeetColors.cyberCyan.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
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

            // Mode Selector Bar: Tabbed Focus vs Full Continuous Document
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MeetColors.cardBackground,
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        SafetyHaptics.selectionTick(view)
                                        isContinuousDocumentMode = false
                                    },
                                color = if (!isContinuousDocumentMode) MeetColors.cyberCyan.copy(alpha = 0.2f) else Color.Transparent,
                                border = if (!isContinuousDocumentMode) BorderStroke(1.dp, MeetColors.cyberCyan) else null,
                            ) {
                                Text(
                                    "📑 Vista por Pestaña",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isContinuousDocumentMode) MeetColors.cyberCyan else MeetColors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        SafetyHaptics.selectionTick(view)
                                        isContinuousDocumentMode = true
                                    },
                                color = if (isContinuousDocumentMode) MeetColors.neonGreen.copy(alpha = 0.2f) else Color.Transparent,
                                border = if (isContinuousDocumentMode) BorderStroke(1.dp, MeetColors.neonGreen) else null,
                            ) {
                                Text(
                                    "📄 Documento Completo",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isContinuousDocumentMode) MeetColors.neonGreen else MeetColors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }

                    if (!isContinuousDocumentMode) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MeetColors.backgroundDeep,
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f)),
                        ) {
                            Text(
                                selectedTab.title,
                                color = MeetColors.cyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // SECTION: VISION & OPENING DECLARATION (TAB 1 OR CONTINUOUS)
            // ═══════════════════════════════════════════════════════════════
            if (isContinuousDocumentMode || selectedTab == InstitutionalNavTab.VISION) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.5.dp, MeetColors.cyberCyan),
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "DECLARACIÓN DE APERTURA INSTITUCIONAL",
                                    color = MeetColors.cyberCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                )
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MeetColors.cyberCyan,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            Text(
                                "“${SafetyInstitutionalPresentationMode.PARLIAMENTARY_OPENING_STATEMENT}”",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 21.sp,
                            )

                            Spacer(Modifier.height(12.dp))

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MeetColors.backgroundDeep,
                                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MeetColors.neonGreen,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "No reemplaza a la Fuerza Pública, OIJ, Fiscalía ni Tribunales. Proporciona infraestructura tecnológica para capturar, preservar, organizar y analizar evidencia.",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickActionCard(
                            title = "Reportar",
                            subtitle = "8 incidentes",
                            icon = Icons.Default.AddAlert,
                            accentColor = MeetColors.neonGreen,
                            onClick = {
                                selectedTab = InstitutionalNavTab.REPORT_EVIDENCE
                                isContinuousDocumentMode = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionCard(
                            title = "Mapa",
                            subtitle = "Territorial",
                            icon = Icons.Default.Map,
                            accentColor = MeetColors.cyberCyan,
                            onClick = {
                                selectedTab = InstitutionalNavTab.TERRITORIAL_MAP
                                isContinuousDocumentMode = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionCard(
                            title = "Casos",
                            subtitle = "Evidencias",
                            icon = Icons.Default.FolderOpen,
                            accentColor = MeetColors.electricBlue,
                            onClick = {
                                selectedTab = InstitutionalNavTab.INVESTIGATION_CASES
                                isContinuousDocumentMode = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionCard(
                            title = "Tendencias",
                            subtitle = "Observatorio",
                            icon = Icons.Default.Analytics,
                            accentColor = MeetColors.hotMagenta,
                            onClick = onNavigateToObservatory,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    Text(
                        "LOS 7 PILARES TECNOLÓGICOS DE ELYSIUM SAFETY",
                        color = MeetColors.cyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                // Resumen de los 7 Pilares
                item {
                    InstitutionalSectionCard(
                        sectionNumber = 1,
                        title = "1. Reportar incidentes de seguridad",
                        description = "Estructura información en 8 categorías tipificadas: Asaltos, Homicidios, Desapariciones, Situaciones sospechosas, Violencia, Narcotráfico, Emergencias e Incidentes de zona territorial.",
                        statusBadge = "DEMOSTRADA",
                        statusColor = MeetColors.neonGreen,
                        actionLabel = "EXPLORAR REPORTE & TIPOLOGÍAS",
                        onAction = {
                            selectedTab = InstitutionalNavTab.REPORT_EVIDENCE
                            isContinuousDocumentMode = false
                        },
                        icon = Icons.Default.ReportProblem,
                    )
                }
                item {
                    InstitutionalSectionCard(
                        sectionNumber = 2,
                        title = "2. Adjuntar y organizar evidencia",
                        description = "Fotografías, videos, documentos forenses y procedencia testimonial. Huella criptográfica SHA-256 sobre bytes originales y códigos QR de verificación inmediata.",
                        statusBadge = "DEMOSTRADA",
                        statusColor = MeetColors.neonGreen,
                        actionLabel = "EXPLORAR CUSTODIA DE EVIDENCIA",
                        onAction = {
                            selectedTab = InstitutionalNavTab.REPORT_EVIDENCE
                            isContinuousDocumentMode = false
                        },
                        icon = Icons.Default.AttachFile,
                    )
                }
                item {
                    InstitutionalSectionCard(
                        sectionNumber = 3,
                        title = "3. Georreferenciar los acontecimientos",
                        description = "Visualización territorial con celdas de calor y concentraciones. Desenfoque residencial mínimo de 25 km en áreas públicas para evitar represalias contra víctimas.",
                        statusBadge = "DEMOSTRADA",
                        statusColor = MeetColors.neonGreen,
                        actionLabel = "VER MAPA TERRITORIAL",
                        onAction = {
                            selectedTab = InstitutionalNavTab.TERRITORIAL_MAP
                            isContinuousDocumentMode = false
                        },
                        icon = Icons.Default.LocationOn,
                    )
                }
                item {
                    InstitutionalSectionCard(
                        sectionNumber = 4,
                        title = "4. Mantener trazabilidad de la información",
                        description = "Taxonomía epistémica: OBSERVED → AUTHORITATIVE → DERIVED → ESTIMATED → UNKNOWN. Ningún reporte ciudadano se convierte automáticamente en conclusión oficial sin autoridad.",
                        statusBadge = "DEMOSTRADA",
                        statusColor = MeetColors.neonGreen,
                        actionLabel = "VER TRAZABILIDAD EPISTÉMICA",
                        onAction = {
                            selectedTab = InstitutionalNavTab.INVESTIGATION_CASES
                            isContinuousDocumentMode = false
                        },
                        icon = Icons.Default.Visibility,
                    )
                }
                item {
                    InstitutionalSectionCard(
                        sectionNumber = 5,
                        title = "5. Construir una cadena de evidencia",
                        description = "Estructura formal: Evento → Afirmación → Hipótesis → Evidencia → Análisis → Conclusión. Hashes SHA-256, códigos QR y árboles de Merkle auditables.",
                        statusBadge = "DEMOSTRADA",
                        statusColor = MeetColors.neonGreen,
                        actionLabel = "VER CADENA INVESTIGATIVA",
                        onAction = {
                            selectedTab = InstitutionalNavTab.INVESTIGATION_CASES
                            isContinuousDocumentMode = false
                        },
                        icon = Icons.Default.AccountTree,
                    )
                }
                item {
                    InstitutionalSectionCard(
                        sectionNumber = 6,
                        title = "6. Crear inteligencia territorial",
                        description = "Detección de incrementos en zonas calientes, repetición temporal y correlación espacial para orientar eficazmente los recursos preventivos y policiales.",
                        statusBadge = "DEMOSTRADA",
                        statusColor = MeetColors.neonGreen,
                        actionLabel = "VER INTELIGENCIA TERRITORIAL",
                        onAction = {
                            selectedTab = InstitutionalNavTab.TERRITORIAL_MAP
                            isContinuousDocumentMode = false
                        },
                        icon = Icons.Default.Timeline,
                    )
                }
                item {
                    InstitutionalSectionCard(
                        sectionNumber = 7,
                        title = "7. Facilitar la colaboración ciudadano–institución",
                        description = "Puente formal: Ciudadano → Evidencia → Información estructurada → Fuerza Pública, OIJ, Ministerio Público y Poder Judicial.",
                        statusBadge = "DEMOSTRADA",
                        statusColor = MeetColors.neonGreen,
                        actionLabel = "VER PUENTE INSTITUCIONAL",
                        onAction = {
                            selectedTab = InstitutionalNavTab.INVESTIGATION_CASES
                            isContinuousDocumentMode = false
                        },
                        icon = Icons.Default.AccountBalance,
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // SECTION: MAPA TERRITORIAL & INTELIGENCIA (TAB 2 OR CONTINUOUS)
            // ═══════════════════════════════════════════════════════════════
            if (isContinuousDocumentMode || selectedTab == InstitutionalNavTab.TERRITORIAL_MAP) {
                item {
                    Text(
                        "PILARES 3 & 6: GEORREFERENCIACIÓN E INTELIGENCIA TERRITORIAL",
                        color = MeetColors.cyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                item {
                    SafetyTerritorialIntelligenceConsole(onOpenFullMap = onNavigateToMap)
                }
                item {
                    Button(
                        onClick = onNavigateToMap,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ABRIR MAPA COMPLETO INTERACTIVO DE COSTA RICA",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // SECTION: REPORTE & EVIDENCIA (TAB 3 OR CONTINUOUS)
            // ═══════════════════════════════════════════════════════════════
            if (isContinuousDocumentMode || selectedTab == InstitutionalNavTab.REPORT_EVIDENCE) {
                item {
                    Text(
                        "PILARES 1 & 2: REPORTE ESTRUCTURADO Y CUSTODIA DIGITAL",
                        color = MeetColors.cyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                item {
                    SafetyIncidentTypologyExplorer(onNavigateToReport = onNavigateToReport)
                }
                item {
                    SafetyEvidenceManagerPanel()
                }
                item {
                    Button(
                        onClick = onNavigateToMyReports,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.backgroundDeep,
                            contentColor = MeetColors.cyberCyan,
                        ),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "CONSULTAR MIS REPORTES Y ARCHIVOS HISTÓRICOS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // SECTION: CADENA, TRAZABILIDAD & CASOS (TAB 4 OR CONTINUOUS)
            // ═══════════════════════════════════════════════════════════════
            if (isContinuousDocumentMode || selectedTab == InstitutionalNavTab.INVESTIGATION_CASES) {
                item {
                    Text(
                        "PILARES 4, 5 & 7: TRAZABILIDAD, CADENA DE EVIDENCIA Y ENLACE JUDICIAL",
                        color = MeetColors.cyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                item {
                    SafetyEpistemicTraceabilityCard()
                }
                item {
                    SafetyEvidenceChainVisualizer(
                        onNavigateToResearch = onNavigateToResearch,
                    )
                }
                item {
                    SafetyInstitutionalBridgeCard()
                }
                item {
                    Button(
                        onClick = { showExportBriefDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    ) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "GENERAR EXPEDIENTE FORENSE PARA OIJ / FISCALÍA",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
                item {
                    Button(
                        onClick = onNavigateToCases,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.backgroundDeep,
                            contentColor = Color.White,
                        ),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "EXPLORAR CASOS Y EXPEDIENTES PÚBLICOS EN CURSO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // SECTION: SICOP & COMPRAS PÚBLICAS (TAB 5 OR CONTINUOUS)
            // ═══════════════════════════════════════════════════════════════
            if (isContinuousDocumentMode || selectedTab == InstitutionalNavTab.FINANCIAL_SICOP) {
                item {
                    Text(
                        "PISTA COMPLEMENTARIA: INTELIGENCIA DE CONTRATACIÓN PÚBLICA (SICOP)",
                        color = MeetColors.cyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                item {
                    SafetyFinancialIntelligenceCard()
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // NATIONAL IMPACT SUMMARY (FOOTER FOR ALL OR CONTINUOUS)
            // ═══════════════════════════════════════════════════════════════
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            "POTENCIAL PARA LA REPÚBLICA DE COSTA RICA",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Elysium Safety propone tecnología para que la información crítica de seguridad no se pierda entre teléfonos, redes sociales y archivos aislados. " +
                                "Permite construir una infraestructura nacional de trazabilidad probatoria, respetando plenamente las competencias constitucionales y el debido proceso.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // Dialog to toggle Presentation Mode for Auditor / Developer
    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = {
                Text(
                    "Configuración de Presentación",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            },
            text = {
                Column {
                    Text(
                        "Actualmente está activo el Modo Institucional Parlamentario (Elysium Safety aislado para diputados).",
                        fontSize = 13.sp,
                        color = MeetColors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "¿Desea alternar a la plataforma completa (Full Operating System con diagnóstico, movilidad y marketplace)?",
                        fontSize = 13.sp,
                        color = Color.White,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showModeDialog = false
                        onTogglePresentationMode()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                ) {
                    Text("Alternar Modo", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showModeDialog = false }) {
                    Text("Permanecer en Modo Diputados", color = MeetColors.textSecondary)
                }
            },
            containerColor = MeetColors.cardBackground,
        )
    }

    if (showExportBriefDialog) {
        SafetyInstitutionalBriefExportDialog(onDismiss = { showExportBriefDialog = false })
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MeetColors.cardBackground,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
            )
            Text(
                subtitle,
                color = MeetColors.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun InstitutionalSectionCard(
    sectionNumber: Int,
    title: String,
    description: String,
    statusBadge: String,
    statusColor: Color,
    actionLabel: String,
    onAction: () -> Unit,
    icon: ImageVector,
) {
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MeetColors.cyberCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$sectionNumber",
                            color = MeetColors.cyberCyan,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                ) {
                    Text(
                        statusBadge,
                        color = statusColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                description,
                color = MeetColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.backgroundDeep,
                    contentColor = MeetColors.cyberCyan,
                ),
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    actionLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}
