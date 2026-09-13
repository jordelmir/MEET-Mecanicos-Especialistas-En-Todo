package com.elysium369.meet.ui.screens.intelligence

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import java.util.Locale

/**
 * ══════════════════════════════════════════════════════════════════════
 *  D R I V E R   C O M M A N D   C E N T E R
 *  ──────────────────────────────────────────────────────────────
 *  Answers: "¿Cómo va mi negocio de conductor y qué tan eficiente soy?"
 *  - Real metrics: Net Earnings, Utilization, Deadhead %, Rev/hr, Rev/km.
 *  - Trust & Compliance status card with document expiry warnings.
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverCommandCenterScreen(
    driverId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTrustCenter: (String) -> Unit,
) {
    val engine = remember { IntelligenceEngine() }
    val scope = remember(driverId) {
        AnalyticsScope(
            principalId = driverId,
            scopeType = ScopeType.DRIVER,
            subjectIds = setOf(driverId),
            capabilities = setOf(ActorCapability.VIEW_PERSONAL_ANALYTICS),
        )
    }

    val projection = remember(scope, driverId) {
        engine.projectDriver(scope = scope, driverId = driverId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "DRIVER COMMAND CENTER",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                        Text(
                            "Eficiencia y Rendimiento Operacional",
                            fontSize = 11.sp,
                            color = MeetColors.neonGreen,
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

            // ── Section 1: Today Earnings Hero ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1B1E)),
                    border = BorderStroke(1.5.dp, MeetColors.neonGreen.copy(alpha = 0.7f)),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "GANANCIAS NETAS HOY",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MeetColors.neonGreen.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    "${projection.todayTripsCount} VIAJES",
                                    color = MeetColors.neonGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = projection.todayNetEarnings.formatted(),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                        )

                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("Bruto Facturado", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(projection.todayGrossEarnings.formatted(), color = MeetColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Disponible para Retiro", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(projection.payoutAvailable.formatted(), color = MeetColors.cyberCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Section 2: Efficiency Ratios ──
            item {
                Text(
                    "MÉTRICAS PROFESIONALES DE CONDUCCIÓN",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricEfficiencyCard(
                        modifier = Modifier.weight(1f),
                        title = "Utilización",
                        value = String.format(Locale.US, "%.1f%%", projection.utilizationPercent),
                        subtitle = "${projection.occupiedHours}h de ${projection.onlineHours}h",
                        tint = MeetColors.neonGreen,
                    )
                    MetricEfficiencyCard(
                        modifier = Modifier.weight(1f),
                        title = "Deadhead (Vacío)",
                        value = String.format(Locale.US, "%.1f%%", projection.deadheadRatio * 100),
                        subtitle = "${(projection.totalKm - projection.paidKm).toInt()} km no pagados",
                        tint = if (projection.deadheadRatio > 0.4) Color(0xFFFF9500) else MeetColors.cyberCyan,
                    )
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricEfficiencyCard(
                        modifier = Modifier.weight(1f),
                        title = "Ingreso por Hora",
                        value = projection.revenuePerHour.formatted(),
                        subtitle = "Por hora conectado",
                        tint = MeetColors.cyberCyan,
                    )
                    MetricEfficiencyCard(
                        modifier = Modifier.weight(1f),
                        title = "Ingreso por Km",
                        value = projection.revenuePerKm.formatted(),
                        subtitle = "Por km con pasajero",
                        tint = MeetColors.cyberCyan,
                    )
                }
            }

            // ── Section 3: Trust & Compliance Status ──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTrustCenter(driverId) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131D24)),
                    border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MeetColors.warning)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ESTADO DE CUMPLIMIENTO Y CONFIANZA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Póliza de seguro vence en 25 días", color = MeetColors.warning, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MeetColors.textMuted)
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ComplianceChip("Licencia: Vigente", MeetColors.neonGreen)
                            ComplianceChip("RTV/Dekra: Al día", MeetColors.neonGreen)
                            ComplianceChip("Seguro: Renovar", MeetColors.warning)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MetricEfficiencyCard(
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
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = MeetColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(value, color = tint, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = MeetColors.textSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ComplianceChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
