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
 *  M E C H A N I C   B U S I N E S S   S C R E E N
 *  ──────────────────────────────────────────────────────────────
 *  Answers: "¿Cómo rinde mi negocio técnico profesional?"
 *  - Labor vs Parts revenue.
 *  - Billed hours & Revenue per hour.
 *  - Critical quality metric: REWORK_30D rate.
 *  - Trust Center deep link for technical certifications.
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MechanicBusinessScreen(
    mechanicId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTrustCenter: () -> Unit,
) {
    val engine = remember { IntelligenceEngine() }
    val scope = remember(mechanicId) {
        AnalyticsScope(
            principalId = mechanicId,
            scopeType = ScopeType.PERSONAL,
            capabilities = setOf(ActorCapability.VIEW_PERSONAL_ANALYTICS, ActorCapability.PROVIDE_REPAIR),
        )
    }

    val biz = remember(mechanicId) {
        engine.projectMechanic(scope = scope, mechanicId = mechanicId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "MECHANIC BUSINESS",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color.White,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            biz.mechanicName,
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
                    IconButton(onClick = onNavigateToTrustCenter) {
                        Icon(Icons.Default.Verified, contentDescription = "Certificaciones", tint = MeetColors.cyberCyan)
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

            // ── Section 1: Revenue Hero ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF091724)),
                    border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFFFB300)))),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "INGRESOS FACTURADOS (MES)",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                            ) {
                                Text(
                                    "${biz.jobsCompletedCount} TRABAJOS",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = biz.totalRevenue.formatted(),
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
                                Text(biz.laborRevenue.formatted(), color = MeetColors.cyberCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Repuestos / Partes", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(biz.partsRevenue.formatted(), color = Color(0xFFFFB300), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Section 2: Productive Efficiency & Ticket ──
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Ticket Promedio", color = MeetColors.textMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(biz.averageTicket.formatted(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Ingreso / Hora", color = MeetColors.textMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("${biz.revenuePerHour.formatted()}/h", color = MeetColors.neonGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // ── Section 3: Critical Quality Metric: REWORK_30D ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, if (biz.rework30dRatePercent <= 3.0) MeetColors.neonGreen else MeetColors.warning),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("CALIDAD DE REPARACIÓN (REWORK ≤30D)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Text(
                                "${String.format(java.util.Locale.US, "%.1f", biz.rework30dRatePercent)}%",
                                color = if (biz.rework30dRatePercent <= 3.0) MeetColors.neonGreen else MeetColors.warning,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Vehículos que regresaron por el mismo DTC/falla en menos de 30 días. Tu índice está por debajo de la media nacional (5.0%).",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            // ── Section 4: Rating & Customer Loyalty ──
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
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Rating Clientes", color = MeetColors.textMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("${biz.customerRating} ★", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
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
                                Icon(Icons.Default.Repeat, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Clientes Recurrentes", color = MeetColors.textMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("${biz.repeatCustomersPercent}%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // ── Section 5: Trust Center Certification Badge ──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTrustCenter() },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E2A)),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PERFIL CERTIFICADO DE TÉCNICO MEET", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Credenciales, pólizas de responsabilidad civil y especialidades validadas.", color = MeetColors.textMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MeetColors.textMuted)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
