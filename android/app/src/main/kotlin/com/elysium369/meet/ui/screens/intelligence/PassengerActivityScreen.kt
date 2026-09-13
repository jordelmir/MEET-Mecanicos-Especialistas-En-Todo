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
 *  P A S S E N G E R   A C T I V I T Y   S C R E E N
 *  ──────────────────────────────────────────────────────────────
 *  "Mi Actividad" — Consumer Activity & Personal Mobility Intelligence.
 *  - Trips, km traveled, travel time, spending breakdown.
 *  - Average cost per trip and per km.
 *  - Ecological impact (estimated CO2).
 *  - Deep link into Personal Trust & Safety profile.
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerActivityScreen(
    passengerId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTrustCenter: () -> Unit,
) {
    val engine = remember { IntelligenceEngine() }
    val scope = remember(passengerId) {
        AnalyticsScope(
            principalId = passengerId,
            scopeType = ScopeType.PASSENGER,
            capabilities = setOf(ActorCapability.VIEW_PERSONAL_ANALYTICS),
        )
    }

    val activity = remember(passengerId) {
        engine.projectPassenger(scope = scope, passengerId = passengerId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "MI ACTIVIDAD",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color.White,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            "Movilidad Personal & Resumen Ecológico",
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
                        Icon(Icons.Default.Security, contentDescription = "Trust & Safety", tint = MeetColors.cyberCyan)
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

            // ── Section 1: Hero Spending & Trips ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF091726)),
                    border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(MeetColors.cyberCyan, MeetColors.neonGreen))),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "TOTAL INVERTIDO ESTE MES",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    "${activity.completedTripsCount} VIAJES",
                                    color = MeetColors.cyberCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = activity.totalSpent.formatted(),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                        )

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MeetColors.borderSubtle.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Promedio por viaje", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(activity.averagePerTrip.formatted(), color = MeetColors.cyberCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Costo promedio / km", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(activity.averagePerKm.formatted(), color = MeetColors.neonGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Section 2: Distance & Travel Time ──
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
                                Icon(Icons.Default.Route, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Distancia Total", color = MeetColors.textMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("${activity.totalDistanceKm} km", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
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
                                Icon(Icons.Default.Timer, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Tiempo en Viaje", color = MeetColors.textMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("${activity.totalHoursTraveling} h", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // ── Section 3: Ecological & Impact ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Eco, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("HUELLA DE CARBONO Y SOSTENIBILIDAD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "CO₂ Estimado: ${String.format(java.util.Locale.US, "%.1f", activity.estimatedCo2Kg)} kg",
                            color = MeetColors.neonGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Calculado sobre ${activity.completedTripsCount} viajes en ${activity.citiesVisitedCount} cantones/ciudades recorridas.",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // ── Section 4: Deep Link to Trust & Safety ──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTrustCenter() },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A28)),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("MI CENTRO DE SEGURIDAD & TRUST", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Contactos SOS, verificación biométrica y métodos de pago protegidos.", color = MeetColors.textMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MeetColors.textMuted)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
