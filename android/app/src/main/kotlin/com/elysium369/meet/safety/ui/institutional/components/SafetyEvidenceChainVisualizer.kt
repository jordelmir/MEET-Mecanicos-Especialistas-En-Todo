package com.elysium369.meet.safety.ui.institutional.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QrCode2
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
 * Visualizer for the Scientific & Investigative Chain of Evidence.
 *
 * Implements Pillar 5:
 * Evento → Afirmación → Hipótesis → Evidencia → Análisis → Conclusión
 *
 * Includes SHA-256 content hashes, QR codes, Merkle trees, and cryptographic custody verification.
 */
@Composable
fun SafetyEvidenceChainVisualizer(
    modifier: Modifier = Modifier,
    onNavigateToResearch: () -> Unit = {},
) {
    var activeStepIndex by remember { mutableIntStateOf(0) }

    val chainSteps = listOf(
        ChainStep(
            stepNumber = 1,
            name = "Evento",
            subtitle = "Acontecimiento Concreto",
            description = "Punto de partida espaciotemporal con fecha, hora de ocurrencia y georreferencia precisa.",
            forensicArtifact = "UUID: 8a4f-event-202610 · Timestamps UTC & Local",
            icon = Icons.Default.Event,
            accentColor = MeetColors.cyberCyan,
        ),
        ChainStep(
            stepNumber = 2,
            name = "Afirmación",
            subtitle = "Declaración Testimonial",
            description = "Aporte testimonial o institucional atribuido a una fuente identificada o protegida.",
            forensicArtifact = "ClaimID: clm-739 · EpistemicState: OBSERVED",
            icon = Icons.Default.Description,
            accentColor = MeetColors.electricBlue,
        ),
        ChainStep(
            stepNumber = 3,
            name = "Hipótesis",
            subtitle = "Propuesta Falsable",
            description = "Explicación tentativa estructurada que admite refutación formal mediante evidencia en contrario.",
            forensicArtifact = "Hypothesis: hyp-104 · Falsifiable: TRUE",
            icon = Icons.Default.Lightbulb,
            accentColor = Color(0xFFFFB300),
        ),
        ChainStep(
            stepNumber = 4,
            name = "Evidencia",
            subtitle = "Archivos & Hashes SHA-256",
            description = "Fotografías, videos, documentos forenses con huella inmutable calculada sobre bytes originales.",
            forensicArtifact = "SHA-256: e3b0c44298fc1c149afbf4c8996fb92427ae41e4... · QR Verifier",
            icon = Icons.Default.Fingerprint,
            accentColor = MeetColors.neonGreen,
        ),
        ChainStep(
            stepNumber = 5,
            name = "Análisis",
            subtitle = "Cruce de Fuentes",
            description = "Correlación reproducible entre testimonios, peritajes técnicos y registros públicos.",
            forensicArtifact = "Merkle Tree Root: 7d1c... · Cross-validation: 3 sources",
            icon = Icons.Default.Analytics,
            accentColor = MeetColors.hotMagenta,
        ),
        ChainStep(
            stepNumber = 6,
            name = "Conclusión",
            subtitle = "Expediente Estructurado",
            description = "Paquete investigativo auditable para entrega formal a las autoridades competentes (OIJ/Fiscalía).",
            forensicArtifact = "Signed Package: Ed25519 · Custody Sealed",
            icon = Icons.Default.CheckCircle,
            accentColor = MeetColors.neonGreen,
        ),
    )

    val current = chainSteps[activeStepIndex]

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.electricBlue.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "5. CADENA DE EVIDENCIA DIGITAL",
                        color = MeetColors.electricBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Evento → Afirmación → Hipótesis → Evidencia",
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

            // Step timeline nodes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                chainSteps.forEachIndexed { index, step ->
                    val isSelected = index == activeStepIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeStepIndex = index },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) step.accentColor else MeetColors.backgroundDeep)
                                .border(
                                    1.dp,
                                    if (isSelected) step.accentColor else MeetColors.borderSubtle,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${step.stepNumber}",
                                color = if (isSelected) Color.Black else MeetColors.textSecondary,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            step.name,
                            color = if (isSelected) step.accentColor else MeetColors.textSecondary,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Active step card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, current.accentColor.copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(current.accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                current.icon,
                                contentDescription = null,
                                tint = current.accentColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Paso ${current.stepNumber}: ${current.name}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                current.subtitle,
                                color = current.accentColor,
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

                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MeetColors.cardBackground,
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = MeetColors.neonGreen,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Registro Criptográfico Inmutable",
                                    color = MeetColors.neonGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    current.forensicArtifact,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Forensic action button
            OutlinedButton(
                onClick = onNavigateToResearch,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MeetColors.electricBlue.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MeetColors.electricBlue,
                ),
            ) {
                Icon(
                    Icons.Default.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "EXPLORAR NÚCLEO CIENTÍFICO FORENSE (ROOM + MERKLE)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

private data class ChainStep(
    val stepNumber: Int,
    val name: String,
    val subtitle: String,
    val description: String,
    val forensicArtifact: String,
    val icon: ImageVector,
    val accentColor: Color,
)
