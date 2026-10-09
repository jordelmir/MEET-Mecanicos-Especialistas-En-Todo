package com.elysium369.meet.safety.ui.intelligence

import androidx.compose.foundation.BorderStroke
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

data class DisplaySourceRecord(
    val id: String,
    val title: String,
    val authority: String,
    val referenceNumber: String,
    val sha256Hex: String,
    val legalBasis: String,
    val timestampFormatted: String,
    val status: String = "VERIFICADO",
)

/**
 * Visual Repository of Official Source Records and Legal Evidentiary Documents.
 *
 * Demonstrates:
 * - Lawful public records ingestion (SICOP, Registro Nacional, CGR).
 * - Exact SHA-256 fingerprint preservation of original files.
 * - Legal provenance citations and zero reliance on unverified rumors.
 */
@Composable
fun SafetySourceRegistryCard(
    modifier: Modifier = Modifier,
) {
    val sampleSources = remember {
        listOf(
            DisplaySourceRecord(
                id = "src-1",
                title = "Expediente de Licitación Pública 2026LN-000012-0001100001",
                authority = "SICOP Costa Rica (Hacienda / CGR)",
                referenceNumber = "SICOP-2026-LN-012",
                sha256Hex = "8f3b2c91a0e4d58721bf6390deca8411b7f8e3291823ab401928dfae543210ab",
                legalBasis = "Ley General de Contratación Pública N.° 9986 - Registro Público",
                timestampFormatted = "2026-05-14 08:30 UTC",
            ),
            DisplaySourceRecord(
                id = "src-2",
                title = "Certificación de Personería Jurídica y Composición de Capital",
                authority = "Registro Nacional de la República de Costa Rica",
                referenceNumber = "RN-PJ-2026-998811",
                sha256Hex = "14e9f022ab7834bcde1029384756abcdef0192837465bcde8901234567abcdef",
                legalBasis = "Código de Comercio de Costa Rica & Ley del Registro Nacional",
                timestampFormatted = "2026-05-10 11:15 UTC",
            ),
            DisplaySourceRecord(
                id = "src-3",
                title = "Informe de Auditoría Financiera Operativa DFOE-CI-0122",
                authority = "Contraloría General de la República (CGR)",
                referenceNumber = "CGR-DFOE-2026-0122",
                sha256Hex = "c592b10a47382910fedcba98765432101234567890abcdef1234567890abcdef",
                legalBasis = "Ley Orgánica de la Contraloría General de la República N.° 7428",
                timestampFormatted = "2026-06-02 16:45 UTC",
            ),
        )
    }

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
                            Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = MeetColors.cyberCyan,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "REGISTRO DE FUENTES Y DOCUMENTOS OFICIALES",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                    }
                    Text(
                        "Documentos legalmente obtenidos con huella SHA-256 inmutable",
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
                        "3 FUENTES OFICIALES",
                        color = MeetColors.neonGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Document Cards
            sampleSources.forEach { doc ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
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
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MeetColors.cyberCyan,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    doc.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MeetColors.cardBackground,
                                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f)),
                            ) {
                                Text(
                                    doc.status,
                                    color = MeetColors.neonGreen,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Autoridad Emisora: ${doc.authority} · Ref: ${doc.referenceNumber}",
                            color = MeetColors.cyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Base legal de procedencia: ${doc.legalBasis}",
                            color = MeetColors.textSecondary,
                            fontSize = 10.sp,
                        )

                        Spacer(Modifier.height(6.dp))
                        // Hash chip
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MeetColors.neonGreen,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "SHA-256: ${doc.sha256Hex.take(24)}...${doc.sha256Hex.takeLast(8)}",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
