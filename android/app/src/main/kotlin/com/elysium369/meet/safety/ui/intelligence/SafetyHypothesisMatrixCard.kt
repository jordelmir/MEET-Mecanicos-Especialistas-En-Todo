package com.elysium369.meet.safety.ui.intelligence

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Popperian Falsification & Hypothesis Comparison Matrix.
 *
 * Implements scientific rigor in investigations:
 * - Compares the primary hypothesis against null/alternative hypotheses.
 * - Displays both supporting and contradicting evidence.
 * - Refuting evidence is NEVER hidden or deleted.
 */
@Composable
fun SafetyHypothesisMatrixCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
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
                            Icons.Default.Balance,
                            contentDescription = null,
                            tint = MeetColors.cyberCyan,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "MATRIZ DE HIPÓTESIS Y CONTRA-HIPÓTESIS",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                    }
                    Text(
                        "Criterio popperiano de falsación y contraste de evidencia contradictoria",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f)),
                ) {
                    Text(
                        "FALSABLE",
                        color = MeetColors.neonGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Two-column or structured comparison
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "HIPÓTESIS PRINCIPAL (H1):",
                        color = MeetColors.cyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "“Posible concentración indebida de contrataciones públicas debido a vinculación societaria común entre oferentes.”",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Evidencia que la apoya: 3 contratos SICOP adjudicados de manera consecutiva (Huella SHA-256 verificada).",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "CONTRA-HIPÓTESIS EXCULPATORIA (H0):",
                        color = Color(0xFFFFB300),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "“Las adjudicaciones obedecen a que el oferente es el único titular de patente y equipo especializado en el país para este tipo de pavimento.”",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Evidencia contradictoria: Certificación técnica de exclusividad de maquinaria registrada en el Colegio de Ingenieros.",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Criterio de falsación: Si se presenta constancia de 2 oferentes idóneos descalificados por vicios de trámite, se invalida H0. Si se acredita la patente exclusiva, se descarta H1.",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}
