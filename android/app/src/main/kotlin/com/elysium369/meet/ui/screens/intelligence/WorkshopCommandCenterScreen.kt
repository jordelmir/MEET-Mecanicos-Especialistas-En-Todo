package com.elysium369.meet.ui.screens.intelligence

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.identity.ActorCapability
import com.elysium369.meet.core.identity.AnalyticsScope
import com.elysium369.meet.core.identity.ScopeType
import com.elysium369.meet.core.intelligence.engine.IntelligenceEngine
import com.elysium369.meet.ui.theme.MeetColors

/**
 * ══════════════════════════════════════════════════════════════════════
 *  W O R K S H O P   C O M M A N D   C E N T E R
 *  ──────────────────────────────────────────────────────────────
 *  Answers: "¿Cómo opera y factura el taller mecánico (B2B Org)?"
 *  - Bay utilization & technician allocation.
 *  - Work in progress vs backlog.
 *  - Labor vs parts revenue breakdown.
 *  - Quote-to-accepted conversion rate.
 *  - Deep link to Trust Center for technicians & facility compliance.
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkshopCommandCenterScreen(
    workshopOrgId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTrustCenter: (String) -> Unit,
) {
    val engine = remember { IntelligenceEngine() }
    val scope = remember(workshopOrgId) {
        AnalyticsScope(
            principalId = "workshop-admin",
            organizationId = workshopOrgId,
            scopeType = ScopeType.WORKSHOP,
            capabilities = setOf(ActorCapability.VIEW_ORGANIZATION_ANALYTICS, ActorCapability.MANAGE_WORKSHOP),
        )
    }

    val workshop = remember(workshopOrgId) {
        engine.projectWorkshop(scope = scope, workshopOrgId = workshopOrgId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "WORKSHOP COMMAND CENTER",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            workshop.workshopName,
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable { onNavigateToTrustCenter(workshopOrgId) },
                        color = MeetColors.warning.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MeetColors.warning),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MeetColors.warning, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Trust (${workshop.expiringCertificationsCount})",
                                color = MeetColors.warning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
        containerColor = MeetColors.backgroundDeep,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── Section 1: Hero Financial Card ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF091726)),
                    border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF00E5FF), MeetColors.neonGreen))),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "FACTURACIÓN DE HOY",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MeetColors.neonGreen.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    "${workshop.activeJobsCount} EN PROCESO",
                                    color = MeetColors.neonGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = workshop.todayRevenue.formatted(),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                        )

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MeetColors.borderSubtle.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Mano de Obra", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(workshop.laborRevenue.formatted(), color = MeetColors.cyberCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Repuestos", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(workshop.partsRevenue.formatted(), color = Color(0xFFFFB300), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Section 2: Bay Utilization & Technicians ──
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Garage, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ocupación Bahías", color = MeetColors.textMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${workshop.occupiedBays}/${workshop.totalBays} (${String.format(java.util.Locale.US, "%.0f", workshop.bayUtilizationPercent)}%)",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PendingActions, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Backlog / Espera", color = MeetColors.textMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("${workshop.backlogJobsCount} órdenes", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // ── Section 3: Conversion & Quality ──
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Conversión Cotizaciones", color = MeetColors.textMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("${workshop.quoteAcceptedConversionPercent}%", color = MeetColors.neonGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Retrabajo ≤30d", color = MeetColors.textMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("${workshop.rework30dRatePercent}%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // ── Section 4: Deep Link to Trust Center ──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTrustCenter(workshopOrgId) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101C2C)),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TRUST & CUMPLIMIENTO DEL TALLER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("1 técnico con certificación por renovar y permisos comerciales al día.", color = MeetColors.textMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MeetColors.textMuted)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
