package com.elysium369.meet.safety.ui.institutional.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Epistemic Traceability Visualizer for Parliamentary Presentation.
 *
 * Demonstrates to the Deputies of Costa Rica how Elysium Safety formally separates:
 * "Una persona reportó este acontecimiento" (OBSERVED)
 * from
 * "La autoridad competente confirmó este acontecimiento" (AUTHORITATIVE).
 *
 * Prevents rumors, assumptions, or unverified claims from becoming official findings.
 */
@Composable
fun SafetyEpistemicTraceabilityCard(
    modifier: Modifier = Modifier,
) {
    var selectedStateIndex by remember { mutableIntStateOf(0) }

    val epistemicStates = listOf(
        EpistemicStateInfo(
            code = "OBSERVED",
            title = "Reportado / Observado",
            tag = "Ciudadano / Testigo",
            description = "Una persona u origen específico aporta el testimonio o material. NO constituye un hecho probado judicialmente ni imputa culpabilidad.",
            example = "\"Una persona reportó haber presenciado detonaciones en este cuadrante a las 21:15.\"",
            icon = Icons.Default.Visibility,
            color = MeetColors.cyberCyan,
        ),
        EpistemicStateInfo(
            code = "AUTHORITATIVE",
            title = "Confirmado por Autoridad",
            tag = "OIJ / Ministerio Público",
            description = "Resolución o peritaje oficial emitido por la institución pública competente. Proviene de una fuente legalmente calificada.",
            example = "\"El Organismo de Investigación Judicial confirmó el hallazgo de indicios balísticos en el sitio.\"",
            icon = Icons.Default.Security,
            color = MeetColors.neonGreen,
        ),
        EpistemicStateInfo(
            code = "DERIVED",
            title = "Derivado / Correlacionado",
            tag = "Análisis Estructurado",
            description = "Conclusión obtenida mediante cruce reproducible de fuentes primarias independientes. Requiere fundamentación documental.",
            example = "\"Coincidencia de vehículo con matrícula reportada en dos registros independientes.\"",
            icon = Icons.Default.CheckCircle,
            color = MeetColors.electricBlue,
        ),
        EpistemicStateInfo(
            code = "ESTIMATED",
            title = "Estimado / Hipótesis",
            tag = "Incertidumbre Modelada",
            description = "Aproximación probabilística o hipótesis de trabajo falsable. Debe señalar explícitamente su margen de error y datos faltantes.",
            example = "\"Área de dispersión probable calculada en un radio de 500 metros con 68% de confianza.\"",
            icon = Icons.Default.Warning,
            color = Color(0xFFFFB300),
        ),
        EpistemicStateInfo(
            code = "UNKNOWN",
            title = "Desconocido / No Capturado",
            tag = "Honestidad Epistémica",
            description = "El sistema reconoce explícitamente cuando un dato no ha sido verificado o no está disponible. Nunca se inventa información.",
            example = "\"Móvil del hecho: DATO NO CAPTURADO · Pendiente de peritaje oficial.\"",
            icon = Icons.Default.Info,
            color = MeetColors.textSecondary,
        ),
    )

    val current = epistemicStates[selectedStateIndex]

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "4. TRAZABILIDAD EPISTEMOLÓGICA",
                        color = MeetColors.cyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Separación estricta de estados de verdad",
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

            // Epistemic progression chain chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                epistemicStates.forEachIndexed { index, state ->
                    val isSelected = index == selectedStateIndex
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedStateIndex = index },
                        color = if (isSelected) state.color.copy(alpha = 0.25f) else MeetColors.backgroundDeep,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) state.color else MeetColors.borderSubtle,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                state.code.take(4),
                                color = if (isSelected) state.color else MeetColors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Active state detail box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, current.color.copy(alpha = 0.35f)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(current.color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = current.icon,
                                contentDescription = null,
                                tint = current.color,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                current.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                current.tag,
                                color = current.color,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        current.description,
                        color = MeetColors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )

                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MeetColors.cardBackground,
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    ) {
                        Row(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "Ejemplo: ",
                                color = MeetColors.cyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                current.example,
                                color = Color.White,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Epistemic Contrast Card: 'Una persona reportó' vs 'La autoridad confirmó'
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "DISTINCIÓN INSTITUCIONAL CLAVE",
                        color = MeetColors.cyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "El sistema puede mantener la diferencia estricta entre:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MeetColors.cardBackground,
                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f)),
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("👤", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "“Una persona reportó este acontecimiento.”",
                                color = MeetColors.cyberCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        "y:",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    Spacer(Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MeetColors.cardBackground,
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f)),
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⚖️", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "“La autoridad competente confirmó este acontecimiento.”",
                                color = MeetColors.neonGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Esta separación reduce el riesgo de convertir rumores, hipótesis o información no verificada en conclusiones oficiales. Un reporte ciudadano no debe convertirse automáticamente en un hecho probado.",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

private data class EpistemicStateInfo(
    val code: String,
    val title: String,
    val tag: String,
    val description: String,
    val example: String,
    val icon: ImageVector,
    val color: Color,
)
