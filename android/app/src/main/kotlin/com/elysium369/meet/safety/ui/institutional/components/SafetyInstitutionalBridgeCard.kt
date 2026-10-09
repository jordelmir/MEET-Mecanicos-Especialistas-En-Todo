package com.elysium369.meet.safety.ui.institutional.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Visualizer for Citizen-Institution Collaboration Bridge.
 *
 * Implements Pillar 7:
 * Ciudadano → Evidencia → Información Estructurada → Institución Competente
 *
 * Prepares the ground for formal pilots with Costa Rica security and judicial entities:
 * - Fuerza Pública (Ministerio de Seguridad Pública)
 * - Organismo de Investigación Judicial (OIJ)
 * - Ministerio Público (Fiscalía General)
 * - Poder Judicial
 */
@Composable
fun SafetyInstitutionalBridgeCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "7. COLABORACIÓN CIUDADANO–INSTITUCIÓN",
                        color = MeetColors.neonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Puente tecnológico de información y custodia",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f)),
                ) {
                    Text(
                        "FASE PILOTO",
                        color = Color(0xFFFFB300),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Collaboration flowchart row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BridgeNode(
                    icon = Icons.Default.Person,
                    label = "Ciudadano",
                    subtext = "Reporte & Evidencia",
                    color = MeetColors.cyberCyan,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MeetColors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
                BridgeNode(
                    icon = Icons.Default.Lock,
                    label = "Criptografía",
                    subtext = "SHA-256 & Custodia",
                    color = MeetColors.electricBlue,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MeetColors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
                BridgeNode(
                    icon = Icons.Default.AccountBalance,
                    label = "Institución",
                    subtext = "OIJ / Fiscalía",
                    color = MeetColors.neonGreen,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Institutional integration targets in Costa Rica
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "INSTITUCIONES DE DESTINO EN COSTA RICA",
                        color = MeetColors.neonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(8.dp))

                    InstitutionalEntityRow(
                        name = "Fuerza Pública (MSP)",
                        role = "Prevención territorial, patrullaje y respuesta rápida a emergencias.",
                        badge = "Operativo",
                    )
                    Spacer(Modifier.height(8.dp))
                    InstitutionalEntityRow(
                        name = "Organismo de Investigación Judicial (OIJ)",
                        role = "Investigación técnico-científica, recepción de indicios y peritajes forenses.",
                        badge = "Investigación",
                    )
                    Spacer(Modifier.height(8.dp))
                    InstitutionalEntityRow(
                        name = "Ministerio Público / Fiscalía General",
                        role = "Ejercicio de la acción penal pública y dirección funcional de la investigación.",
                        badge = "Acusación",
                    )
                    Spacer(Modifier.height(8.dp))
                    InstitutionalEntityRow(
                        name = "Poder Judicial",
                        role = "Tribunales de justicia y juzgamiento con estricto debido proceso.",
                        badge = "Jurisdicción",
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Constitutional Protection Notice
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Gavel,
                        contentDescription = null,
                        tint = MeetColors.neonGreen,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Principio de legalidad: La plataforma no suplanta funciones policiales ni jurisdiccionales. Actúa como canal seguro para que la evidencia ciudadana no se extravíe y llegue estructurada.",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BridgeNode(
    icon: ImageVector,
    label: String,
    subtext: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f))
                .border(1.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
        )
        Text(
            subtext,
            color = MeetColors.textSecondary,
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun InstitutionalEntityRow(
    name: String,
    role: String,
    badge: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                role,
                color = MeetColors.textSecondary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MeetColors.cardBackground,
            border = BorderStroke(1.dp, MeetColors.borderSubtle),
        ) {
            Text(
                badge,
                color = MeetColors.cyberCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
