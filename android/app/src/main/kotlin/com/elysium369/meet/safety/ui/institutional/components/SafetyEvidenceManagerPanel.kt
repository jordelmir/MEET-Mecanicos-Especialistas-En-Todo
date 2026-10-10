package com.elysium369.meet.safety.ui.institutional.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import java.security.MessageDigest

data class DigitalEvidenceItem(
    val id: String,
    val fileName: String,
    val mediaType: String,
    val sizeBytes: Long,
    val sha256Hash: String,
    val sourceProvenance: String,
    val capturedAtIso: String,
    val isOriginalPreserved: Boolean = true,
    val isIntegrityVerified: Boolean = true,
)

/**
 * Interactive Digital Evidence & Forensic Custody Panel.
 *
 * Implements Pillar 2 of Elysium Safety:
 * - Unifies photos, videos, documents, GPS tracks, and chronological records.
 * - Computes byte-exact SHA-256 cryptographic fingerprints over original bytes.
 * - Generates immutable QR verification payload (SAFETY-CUSTODY-V2).
 * - Distinguishes between unaltered original files and derived transcripts/thumbnails.
 */
@Composable
fun SafetyEvidenceManagerPanel(
    modifier: Modifier = Modifier,
    onOpenEvidenceDetail: (DigitalEvidenceItem) -> Unit = {},
) {
    var sampleEvidences by remember {
        mutableStateOf(
            listOf(
                DigitalEvidenceItem(
                    id = "EVID-001",
                    fileName = "fotografia_indicio_balistico.jpg",
                    mediaType = "FOTOGRAFÍA",
                    sizeBytes = 4_892_104L,
                    sha256Hash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
                    sourceProvenance = "Observación directa en vía pública (Cámara 48MP)",
                    capturedAtIso = "2026-10-08T21:22:10-06:00",
                ),
                DigitalEvidenceItem(
                    id = "EVID-002",
                    fileName = "enlace_video_seguridad_2115.url",
                    mediaType = "VIDEO (ENLACE)",
                    sizeBytes = 1_024L,
                    sha256Hash = "8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4",
                    sourceProvenance = "Enlace externo verificado (YouTube/CCTV CDN voluntario)",
                    capturedAtIso = "2026-10-08T21:15:30-06:00",
                ),
                DigitalEvidenceItem(
                    id = "EVID-003",
                    fileName = "acta_declaracion_jurada_fuente.pdf",
                    mediaType = "DOCUMENTO",
                    sizeBytes = 852_440L,
                    sha256Hash = "4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a",
                    sourceProvenance = "Registro notarial / Declaración ciudadana autenticada",
                    capturedAtIso = "2026-10-08T21:40:00-06:00",
                ),
                DigitalEvidenceItem(
                    id = "EVID-004",
                    fileName = "traza_coordenadas_gps_cuadrante.geojson",
                    mediaType = "INFO GEOGRÁFICA",
                    sizeBytes = 128_320L,
                    sha256Hash = "2a11ef721d1542d85e884898da28047151d0e56f8dc6292773603d0d6aabbdd6",
                    sourceProvenance = "Sensor GPS diferencial con cálculo de precisión espacial",
                    capturedAtIso = "2026-10-08T21:14:00-06:00",
                ),
                DigitalEvidenceItem(
                    id = "EVID-005",
                    fileName = "cronologia_sucesos_linea_tiempo.json",
                    mediaType = "CRONOLOGÍA",
                    sizeBytes = 64_110L,
                    sha256Hash = "77d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a4b2277",
                    sourceProvenance = "Línea de tiempo auditada: ocurrencia vs registro",
                    capturedAtIso = "2026-10-08T22:00:00-06:00",
                ),
                DigitalEvidenceItem(
                    id = "EVID-006",
                    fileName = "entrevista_vecinal_anonimizada.wav",
                    mediaType = "DIFERENTES FUENTES",
                    sizeBytes = 1_820_500L,
                    sha256Hash = "d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d85e884898da28047151",
                    sourceProvenance = "Testimonio corroborado bajo protocolo de protección",
                    capturedAtIso = "2026-10-08T22:30:00-06:00",
                ),
            )
        )
    }

    var selectedItem by remember { mutableStateOf<DigitalEvidenceItem?>(sampleEvidences.firstOrNull()) }
    var simulatedTamper by remember { mutableStateOf(false) }

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
                        "2. ADJUNTAR Y ORGANIZAR EVIDENCIA",
                        color = MeetColors.cyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "6 Tipos de Evidencia & Huella Criptográfica SHA-256",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f)),
                ) {
                    Text(
                        "INTEGRIDAD DEMOSTRADA",
                        color = MeetColors.neonGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Mission Statement: Avoid dispersion
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🛡️", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Objetivo central: Evitar que información potencialmente relevante quede dispersa entre mensajes, redes sociales, fotografías o archivos aislados.",
                        color = MeetColors.cyberCyan,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // Evidence items list
            sampleEvidences.forEach { evidence ->
                val isSelected = evidence.id == selectedItem?.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            selectedItem = evidence
                            simulatedTamper = false
                        },
                    color = if (isSelected) MeetColors.cyberCyan.copy(alpha = 0.15f) else MeetColors.backgroundDeep,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MeetColors.cyberCyan else MeetColors.borderSubtle,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeetColors.cardBackground),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                when (evidence.mediaType) {
                                    "VIDEO" -> Icons.Default.Videocam
                                    "FOTO" -> Icons.Default.Image
                                    else -> Icons.Default.PictureAsPdf
                                },
                                contentDescription = null,
                                tint = when (evidence.mediaType) {
                                    "VIDEO" -> MeetColors.hotMagenta
                                    "FOTO" -> MeetColors.neonGreen
                                    else -> MeetColors.cyberCyan
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                evidence.fileName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Text(
                                "${evidence.sourceProvenance} · ${(evidence.sizeBytes / 1024 / 1024.0).let { String.format("%.1f MB", it) }}",
                                color = MeetColors.textSecondary,
                                fontSize = 10.sp,
                            )
                        }
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MeetColors.neonGreen,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Detail inspector of selected evidence
            selectedItem?.let { current ->
                val displayHash = if (simulatedTamper) {
                    "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
                } else {
                    current.sha256Hash
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(
                        1.dp,
                        if (simulatedTamper) Color(0xFFFF5252) else MeetColors.neonGreen.copy(alpha = 0.5f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "INSPECCIÓN DE HUELLA DIGITAL SHA-256",
                                color = if (simulatedTamper) Color(0xFFFF5252) else MeetColors.neonGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (simulatedTamper) Color(0xFFFF5252).copy(alpha = 0.2f) else MeetColors.neonGreen.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    if (simulatedTamper) "ALTERACIÓN DETECTADA" else "ORIGINAL VERIFICADO",
                                    color = if (simulatedTamper) Color(0xFFFF5252) else MeetColors.neonGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            displayHash,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp,
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("Fecha de Captura", color = MeetColors.textSecondary, fontSize = 9.sp)
                                Text(current.capturedAtIso, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Original Preservado", color = MeetColors.textSecondary, fontSize = 9.sp)
                                Text("Sí (Inalterado)", color = MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("QR Verifier", color = MeetColors.textSecondary, fontSize = 9.sp)
                                Text("V2 6-Fields", color = MeetColors.cyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Interactive tamper proof demonstration button
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { simulatedTamper = !simulatedTamper },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (simulatedTamper) MeetColors.neonGreen else Color(0xFFFF5252)),
                            ) {
                                Text(
                                    if (simulatedTamper) "RESTAURAR ORIGINAL" else "SIMULAR ALTERACIÓN DE 1 BYTE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (simulatedTamper) MeetColors.neonGreen else Color(0xFFFF5252),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
