package com.elysium369.meet.safety.ui.intelligence

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Deterministic Anomaly & Pattern Detection Visualizer.
 *
 * Emphasizes:
 * - Deterministic, mathematical rules (no mysterious AI black-boxes).
 * - Transparent calculation formulas.
 * - Mandatory alternative explanations for every signal.
 * - Non-negotiable invariant: Anomaly != Crime.
 */
@Composable
fun SafetyAnomalySignalCard(
    modifier: Modifier = Modifier,
) {
    var expandedSignal1 by remember { mutableStateOf(true) }
    var expandedSignal2 by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "DETECCIÓN DE ANOMALÍAS EXPLICABLES",
                            color = Color(0xFFFFB300),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                    }
                    Text(
                        "Reglas matemáticas deterministas con hipótesis alternativas obligatorias",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
                ) {
                    Text(
                        "2 SEÑALES",
                        color = Color(0xFFFFB300),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Non-negotiable Invariant Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f)),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MeetColors.neonGreen,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Principio inviolable: Anomalía ≠ Delito. La plataforma no decide culpabilidad ni imputa delitos; identifica patrones documentales que ameritan revisión humana objetiva.",
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Signal 1: Procurement Concentration (SICOP)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedSignal1 = !expandedSignal1 },
                shape = RoundedCornerShape(12.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "SEÑAL 1: Concentración Inusual en SICOP (78.4%)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Icon(
                            if (expandedSignal1) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MeetColors.textSecondary,
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Proveedor: Consorcio Vial Del Este S.A. en licitaciones del MOPT",
                        color = MeetColors.cyberCyan,
                        fontSize = 11.sp,
                    )

                    AnimatedVisibility(visible = expandedSignal1) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        "FÓRMULA DETERMINISTA:",
                                        color = MeetColors.neonGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Ratio = (Monto Adjudicado Proveedor / Total Licitaciones Institución) = " +
                                            "₡1.450M / ₡1.850M = 78.38% (Umbral de alerta: 65.0%)",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(
                                "HIPÓTESIS ALTERNATIVAS OBLIGATORIAS (AUDITORÍA EXCULPATORIA):",
                                color = MeetColors.cyberCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "• 1. Fabricante o proveedor único debidamente acreditado ante la institución.\n" +
                                    "• 2. Contratación bajo Decreto de Emergencia Nacional o urgencia calificada.\n" +
                                    "• 3. Deserción de otros competidores en licitaciones públicas previas en la zona geográfica.",
                                color = MeetColors.textSecondary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Signal 2: Corporate Circular Ownership
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedSignal2 = !expandedSignal2 },
                shape = RoundedCornerShape(12.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Autorenew,
                                contentDescription = null,
                                tint = MeetColors.cyberCyan,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "SEÑAL 2: Estructura Societaria Circular Detectada",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Icon(
                            if (expandedSignal2) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MeetColors.textSecondary,
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ciclo: Consorcio Vial ➔ Infraestructuras Beta ➔ Holding Matriz ➔ Consorcio Vial",
                        color = MeetColors.cyberCyan,
                        fontSize = 11.sp,
                    )

                    AnimatedVisibility(visible = expandedSignal2) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                "EXPLICACIONES ALTERNATIVAS VÁLIDAS:",
                                color = MeetColors.cyberCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "• Estructura corporativa habitual de consolidación patrimonial empresarial legal.\n" +
                                    "• Participación cruzada resultante de fusiones corporativas previas inscritas en Registro.",
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
}
