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
 *  T O W   C O M M A N D   C E N T E R
 *  ──────────────────────────────────────────────────────────────
 *  Answers: "¿Cómo opera y rinde el servicio de grúa y rescate vial?"
 *  - Loaded distance vs Total distance.
 *  - Critical metric: loaded_ratio (loaded_distance / total_distance).
 *  - Response ETA, revenue per loaded km, active dispatches.
 *  - Deep link to Trust Center for truck permits & Dekra inspections.
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TowCommandCenterScreen(
    operatorOrOrgId: String,
    isOrganization: Boolean = false,
    onNavigateBack: () -> Unit,
    onNavigateToTrustCenter: () -> Unit,
) {
    val engine = remember { IntelligenceEngine() }
    val scope = remember(operatorOrOrgId) {
        AnalyticsScope(
            principalId = if (!isOrganization) operatorOrOrgId else "tow-admin",
            organizationId = if (isOrganization) operatorOrOrgId else null,
            scopeType = if (isOrganization) ScopeType.TOW_FLEET else ScopeType.PERSONAL,
            capabilities = setOf(
                if (isOrganization) ActorCapability.VIEW_ORGANIZATION_ANALYTICS else ActorCapability.VIEW_PERSONAL_ANALYTICS,
                if (isOrganization) ActorCapability.MANAGE_TOW_FLEET else ActorCapability.PROVIDE_TOW,
            ),
        )
    }

    val tow = remember(operatorOrOrgId) {
        engine.projectTow(scope = scope, operatorOrOrgId = operatorOrOrgId, isOrganization = isOrganization)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isOrganization) "TOW FLEET COMMAND CENTER" else "TOW OPERATOR DASHBOARD",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            tow.name,
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
                        Icon(Icons.Default.Security, contentDescription = "Trust & Permisos", tint = MeetColors.cyberCyan)
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

            // ── Section 1: Hero Earnings Card ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A24)),
                    border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFFFF5252), Color(0xFFFFB300)))),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "INGRESOS HOY",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFF5252).copy(alpha = 0.15f),
                            ) {
                                Text(
                                    "${tow.completedCallsCount} SERVICIOS REALIZADOS",
                                    color = Color(0xFFFF5252),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = tow.operatorEarnings.formatted(),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                        )

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MeetColors.borderSubtle.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Ingreso / Km Cargado", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(tow.revenuePerKm.formatted(), color = MeetColors.cyberCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("ETA Promedio Respuesta", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text("${tow.averageResponseEtaMinutes} min", color = MeetColors.neonGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Section 2: Loaded Ratio vs Deadhead ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("RATIO DE CARGA (LOADED RATIO)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Text(
                                "${String.format(java.util.Locale.US, "%.1f", tow.loadedRatio * 100)}%",
                                color = MeetColors.cyberCyan,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${tow.loadedDistanceKm} km con vehículo remolcado de ${tow.totalDistanceKm} km totales recorridos.",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // ── Section 3: Availability & Fleet Status ──
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Grúas Disponibles", color = MeetColors.textMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("${tow.availableTrucksCount} unidades", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Despachos Activos", color = MeetColors.textMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("${tow.activeCallsCount} en curso", color = Color(0xFFFFB300), fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // ── Section 4: Deep Link to Trust Center ──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTrustCenter() },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24)),
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
                            Text("CENTRO DE CONFIANZA & PERMISOS DE GRÚA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Inspecciones Dekra/RTV, permisos de arrastre MOPT y pólizas vigentes.", color = MeetColors.textMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MeetColors.textMuted)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
