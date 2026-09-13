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
 *  B 2 B   F L E E T   C O M M A N D   C E N T E R
 *  ──────────────────────────────────────────────────────────────
 *  Answers: "¿Cómo opera y rinde mi flota de vehículos y conductores?"
 *  Strictly scoped to authorized fleet organization (tenant isolated).
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetCommandCenterScreen(
    fleetId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTrustCenter: (String) -> Unit,
) {
    val engine = remember { IntelligenceEngine() }
    val scope = remember(fleetId) {
        AnalyticsScope(
            principalId = "fleet-owner-session",
            organizationId = fleetId,
            scopeType = ScopeType.FLEET,
            capabilities = setOf(ActorCapability.MANAGE_FLEET, ActorCapability.VIEW_ORGANIZATION_ANALYTICS),
        )
    }

    val projection = remember(scope, fleetId) {
        engine.projectFleet(scope = scope, targetFleetId = fleetId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "FLEET COMMAND CENTER",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                        Text(
                            projection.fleetName,
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

            // ── Section 1: Fleet Economic Overview ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1626)),
                    border = BorderStroke(1.5.dp, MeetColors.cyberCyan.copy(alpha = 0.7f)),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "VOLUMEN DE FLOTA HOY (GMV)",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MeetColors.cyberCyan.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    "${projection.tripsToday} VIAJES",
                                    color = MeetColors.cyberCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = projection.fleetGmv.formatted(),
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                        )

                        Spacer(Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Ganancias de Flota", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(projection.fleetEarnings.formatted(), color = MeetColors.neonGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Pago a Choferes", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(projection.driverEarnings.formatted(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Section 2: Fleet Asset Vitals ──
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FleetStatBox(
                        modifier = Modifier.weight(1f),
                        title = "Vehículos",
                        value = "${projection.activeVehicles}/${projection.totalVehicles}",
                        subtitle = "37 operativos",
                        tint = MeetColors.cyberCyan,
                    )
                    FleetStatBox(
                        modifier = Modifier.weight(1f),
                        title = "Choferes Online",
                        value = "${projection.onlineDrivers}",
                        subtitle = "Conectados",
                        tint = MeetColors.neonGreen,
                    )
                    FleetStatBox(
                        modifier = Modifier.weight(1f),
                        title = "Utilización",
                        value = "${projection.fleetUtilizationPercent}%",
                        subtitle = "Excelente",
                        tint = Color(0xFFFFB300),
                    )
                }
            }

            // ── Section 3: Fleet Trust & Compliance Alerts ──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTrustCenter(fleetId) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141D29)),
                    border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MeetColors.warning)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("CUMPLIMIENTO Y RIESGO DE FLOTA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "${projection.driversExpiringDocuments} choferes requieren renovación de documentos",
                                    color = MeetColors.warning,
                                    fontSize = 11.sp,
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MeetColors.textMuted)
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(6.dp), color = MeetColors.cardBackground) {
                                Text("🛠️ 3 Mantenimientos Pendientes", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = MeetColors.warning.copy(alpha = 0.15f)) {
                                Text("📄 2 Licencias por Vencer", color = MeetColors.warning, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FleetStatBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    tint: Color,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(value, color = tint, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = MeetColors.textSecondary, fontSize = 9.sp)
        }
    }
}
