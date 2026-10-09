package com.elysium369.meet.safety.ui.institutional.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Public-Interest Financial Intelligence & Anti-Corruption Visualizer.
 *
 * Implements the Complementary Investigative Track:
 * - Costa Rica Public Procurement (SICOP - Ley N.° 9986).
 * - Deterministic award concentration anomaly engine (ProcurementConcentrationRule).
 * - Mandatory alternative hypotheses (emergency decrees, sole authorized vendor).
 * - Invariant: Visible personal wealth alone is NEVER crime; zero civilian dossiers on neighbors.
 */
@Composable
fun SafetyFinancialIntelligenceCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "LÍNEA COMPLEMENTARIA · INTERÉS PÚBLICO",
                        color = Color(0xFFFFB300),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Inteligencia Financiera y Anticorrupción",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f)),
                ) {
                    Text(
                        "DEMOSTRADA",
                        color = MeetColors.neonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // SICOP Engine Overview Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Adaptador SICOP Costa Rica (Ley N.° 9986)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Normalización canónica y huella digital SHA-256 de compras públicas, contratos y expedientes de licitación sin depender de opiniones ni conjeturas.",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Anomaly engine rule demo
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "REGLA DETERMINISTA: CONCENTRACIÓN DE ADJUDICACIONES",
                        color = MeetColors.cyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Calcula si un solo contratista concentra más del 65% del monto total de una institución en una ventana temporal, evaluando siempre hipótesis alternativas legítimas (e.g. emergencias nacionales o proveedor único autorizado).",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Invariant: Wealth != Crime Protection Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MeetColors.neonGreen,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Principio inviolable: Riqueza visible ≠ Delito",
                            color = MeetColors.neonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "La tenencia de vehículos costosos o bienes no constituye indicio penal. El sistema prohíbe crear listas de vigilancia vecinal o emitir puntajes de criminalidad contra personas.",
                            color = MeetColors.textSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
