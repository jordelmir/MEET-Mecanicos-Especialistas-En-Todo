package com.elysium369.meet.safety.ui.institutional.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.safety.domain.SafetyReportCategory
import com.elysium369.meet.safety.ui.common.SafetyCategoryIcons
import com.elysium369.meet.ui.theme.MeetColors

data class IncidentTypologyDetail(
    val category: SafetyReportCategory,
    val title: String,
    val subtitle: String,
    val scopeDescription: String,
    val exampleOccurrence: String,
    val exampleLocation: String,
    val supportingEvidenceTypes: String,
    val initialEpistemicState: String = "OBSERVED (Reporte ciudadano estructurado)",
    val sampleSha256: String,
)

/**
 * Interactive Typology Explorer for Parliamentary Presentation.
 *
 * Implements Pillar 1:
 * Reportar incidentes de seguridad en 8 categorías tipificadas:
 * - Asaltos
 * - Homicidios
 * - Desapariciones
 * - Situaciones sospechosas
 * - Violencia
 * - Narcotráfico y actividades relacionadas
 * - Emergencias
 * - Incidentes de zona territorial
 *
 * Muestra a los diputados cómo se estructura la información de ocurrencia vs registro,
 * ubicación georreferenciada y material probatorio de respaldo.
 */
@Composable
fun SafetyIncidentTypologyExplorer(
    modifier: Modifier = Modifier,
    onNavigateToReport: () -> Unit = {},
    onNavigateToReportCategory: (SafetyReportCategory) -> Unit = { onNavigateToReport() },
) {
    val typologies = remember {
        listOf(
            IncidentTypologyDetail(
                category = SafetyReportCategory.ASSAULT_ROBBERY,
                title = "Asaltos y Robos",
                subtitle = "Sustracción violenta o desapoderamiento ilícito",
                scopeDescription = "Acontecimientos donde medie violencia o intimidación para sustraer bienes a peatones, locales o vehículos.",
                exampleOccurrence = "Ocurrido: 2026-10-08 19:42 · Registrado: 2026-10-08 19:55",
                exampleLocation = "San José Central · Distrito Merced (Av. Segunda)",
                supportingEvidenceTypes = "Fotografías de daños, metadatos de cámaras de seguridad, descripción de autores sin señalamiento judicial prematuro.",
                sampleSha256 = "6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b",
            ),
            IncidentTypologyDetail(
                category = SafetyReportCategory.HOMICIDE,
                title = "Homicidios",
                subtitle = "Hechos con pérdida violenta de vidas humanas",
                scopeDescription = "Eventos críticos documentados. En la plataforma, todo reporte ciudadano de homicidio inicia en estado OBSERVED hasta validación oficial por OIJ o Medicatura Forense.",
                exampleOccurrence = "Ocurrido: 2026-10-08 21:15 · Registrado: 2026-10-08 21:28",
                exampleLocation = "Limón Central · Barrio Cieneguita",
                supportingEvidenceTypes = "Indicios periciales balísticos fotografiados, actas de confirmación de Fuerza Pública, cronología de llegada de unidades.",
                sampleSha256 = "d4735e3a265e16eee03f59718b9b5d03019c07d8b6c51f90da3a666eec13ab35",
            ),
            IncidentTypologyDetail(
                category = SafetyReportCategory.MISSING_PERSON,
                title = "Desapariciones",
                subtitle = "Personas no localizadas y búsqueda activa",
                scopeDescription = "Casos urgentes donde un ciudadano no ha sido ubicado tras pérdida repentina de contacto. Permite adjuntar señas particulares y última ubicación verificada.",
                exampleOccurrence = "Último contacto: 2026-10-07 16:30 · Registrado: 2026-10-08 08:15",
                exampleLocation = "Puntarenas · Barranca",
                supportingEvidenceTypes = "Fotografía reciente, vestimenta, última celda telefónica o testimonio presencial.",
                sampleSha256 = "4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce",
            ),
            IncidentTypologyDetail(
                category = SafetyReportCategory.SUSPICIOUS_SITUATION,
                title = "Situaciones Sospechosas",
                subtitle = "Conductas atípicas o preparatorias en el espacio público",
                scopeDescription = "Observaciones de vigilancia no justificada sobre vecindarios, vehículos sin matrícula rondando o marcas en accesos habitacionales.",
                exampleOccurrence = "Ocurrido: 2026-10-08 14:10 · Registrado: 2026-10-08 14:20",
                exampleLocation = "Desamparados · San Miguel",
                supportingEvidenceTypes = "Grabación de video doméstico, descripción de modelo/color de vehículo, bitácora de horarios.",
                sampleSha256 = "4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a",
            ),
            IncidentTypologyDetail(
                category = SafetyReportCategory.VIOLENT_INCIDENT,
                title = "Incidentes Violentos",
                subtitle = "Enfrentamientos, agresiones físicas o disputas armadas",
                scopeDescription = "Riñas graves, disparos al aire, agresiones en vía pública o intimidaciones que alteran el orden territorial.",
                exampleOccurrence = "Ocurrido: 2026-10-08 22:05 · Registrado: 2026-10-08 22:12",
                exampleLocation = "Alajuela Central · El Carmen",
                supportingEvidenceTypes = "Grabaciones de audio ambiental con decibelios, reportes de vecinos cruzados, fotos de daños colaterales.",
                sampleSha256 = "ef2d127de37b942baad06145e54b0c619a1f22327b2ebbcfbec78f5564afe39d",
            ),
            IncidentTypologyDetail(
                category = SafetyReportCategory.DRUG_SALE_ACTIVITY,
                title = "Narcotráfico y Puntos de Venta",
                subtitle = "Actividades ligadas a microtráfico y redes delictivas",
                scopeDescription = "Documentación de puntos clandestinos de distribución, flujo repetitivo de compradores o puntos de acopio reportados por la comunidad con blindaje de identidad.",
                exampleOccurrence = "Patrón reiterado: 18:00 - 02:00 diaria · Registrado: 2026-10-08 17:00",
                exampleLocation = "San José · Pavas (Área aproximada con protección de 25 km)",
                supportingEvidenceTypes = "Bitácora cronológica, fotos de flujo peatonal sin rostros de vecinos, datos cruzados anónimos.",
                sampleSha256 = "e7f6c011776e8db7cd330b54174fd76f7d0216b612387a5ffcfb81e6f0919683",
            ),
            IncidentTypologyDetail(
                category = SafetyReportCategory.EMERGENCY,
                title = "Emergencias y Auxilio Inmediato",
                subtitle = "Situaciones de riesgo inminente a la integridad física",
                scopeDescription = "Eventos en desarrollo que requieren intervención urgente de cuerpos de socorro o Fuerza Pública.",
                exampleOccurrence = "En curso: 2026-10-08 23:30 · Registrado: Tiempo real",
                exampleLocation = "Cartago · El Tejar",
                supportingEvidenceTypes = "Coordenadas GPS vivas, audio de auxilio de 10 segundos, alerta prioritaria en despacho.",
                sampleSha256 = "7902699be42c8a8e46fbbb4501726517e86b22c56a189f7625a6da49081b2451",
            ),
            IncidentTypologyDetail(
                category = SafetyReportCategory.ZONE_INCIDENT,
                title = "Incidentes de Zona Territorial",
                subtitle = "Patrones contextuales o afectación a infraestructura comunitaria",
                scopeDescription = "Averías provocadas, luminarias saboteadas para favorecer delincuencia, o afectación a centros educativos y parques recreativos.",
                exampleOccurrence = "Observado: 2026-10-08 06:00 · Registrado: 2026-10-08 09:30",
                exampleLocation = "Heredia · Guararí",
                supportingEvidenceTypes = "Fotografías de alumbrado vandalizado, actas de comité vecinal, solicitudes municipales de reparación.",
                sampleSha256 = "2c624232cdd221771294dfbb310aca000a0df6ec8b6602f7470f5e1f0e4d0818",
            ),
            IncidentTypologyDetail(
                category = SafetyReportCategory.CORRUPTION_PUBLIC_PROCUREMENT,
                title = "Contratación Pública y SICOP",
                subtitle = "Licitaciones del Estado, obras públicas y erario",
                scopeDescription = "Auditoría ciudadana y documental sobre compras públicas (Ley N.° 9986). Registra número de procedimiento licitatorio, adjudicatario, sobreprecios y actas municipales.",
                exampleOccurrence = "Adjudicado: 2026-09-15 · Expediente SICOP: 2024LN-000015-0005900001",
                exampleLocation = "San José · Sede Institucional Central",
                supportingEvidenceTypes = "Pliegos de condiciones de SICOP, informes de auditoría interna de la CGR, facturas proforma y contratos firmados.",
                sampleSha256 = "9f83a45c612b7a8c4390021e1d3e89fb2301ca1029384756abcdef0123456789",
            ),
            IncidentTypologyDetail(
                category = SafetyReportCategory.CORPORATE_OPACITY_CONFLICT,
                title = "Estructuras Corporativas y Conflicto",
                subtitle = "Mallas societarias, empresas de papel y beneficiarios finales",
                scopeDescription = "Relacionamiento entre sociedades mercantiles, apoderados comunes y empresas pantalla vinculadas a adjudicaciones públicas o desvío de capitales.",
                exampleOccurrence = "Constitución: 2023-04-10 · Modificación registral: 2026-05-18",
                exampleLocation = "Registro Nacional · San José",
                supportingEvidenceTypes = "Certificaciones de personería jurídica, actas de asambleas de accionistas, trazabilidad de apoderados y cédulas jurídicas.",
                sampleSha256 = "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0",
            ),
            IncidentTypologyDetail(
                category = SafetyReportCategory.FINANCIAL_FRAUD,
                title = "Fraude Financiero y Desvío de Fondos",
                subtitle = "Lavado de activos, esquemas fraudulentos y desfalco",
                scopeDescription = "Patrones de transacciones irregulares, triangulación bancaria o captación ilegal de fondos con soporte documental legítimo.",
                exampleOccurrence = "Transferencias: 2026-08-01 a 2026-09-30 · Registrado: 2026-10-01",
                exampleLocation = "Sistema Bancario Nacional / Flujo Transfronterizo",
                supportingEvidenceTypes = "Comprobantes de transferencia, extractos contables anonimizados, comunicaciones contractuales y denuncias judiciales radicadas.",
                sampleSha256 = "b2c3d4e5f6a17890123456789abcdef0123456789abcdef0123456789abcdef1",
            ),
        )
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    val active = typologies[selectedIndex]
    val visual = SafetyCategoryIcons.of(active.category)

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
                        "1. TIPOLOGÍA DE INCIDENTES & INTEGRIDAD PÚBLICA",
                        color = MeetColors.cyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Estructuración de datos en 11 categorías oficiales",
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
                        "11 CATEGORÍAS",
                        color = MeetColors.neonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Selector horizontal de categorías
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(typologies.size) { index ->
                    val item = typologies[index]
                    val isSelected = index == selectedIndex
                    val itemVisual = SafetyCategoryIcons.of(item.category)

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedIndex = index },
                        color = if (isSelected) itemVisual.color.copy(alpha = 0.2f) else MeetColors.backgroundDeep,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) itemVisual.color else MeetColors.borderSubtle,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(itemVisual.emoji, fontSize = 12.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                item.title,
                                color = if (isSelected) Color.White else MeetColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Detalle de la tipología seleccionada
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, visual.color.copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(visual.color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                visual.icon,
                                contentDescription = null,
                                tint = visual.color,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                active.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                active.subtitle,
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        active.scopeDescription,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))

                    // Metadatos estructurados
                    DataRow(label = "Cronología:", value = active.exampleOccurrence, accent = MeetColors.neonGreen)
                    DataRow(label = "Ubicación:", value = active.exampleLocation, accent = MeetColors.cyberCyan)
                    DataRow(label = "Evidencia:", value = active.supportingEvidenceTypes, accent = Color.White)
                    DataRow(label = "Certeza:", value = active.initialEpistemicState, accent = Color(0xFFFFB300))

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "SHA-256: ",
                            color = MeetColors.textSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            active.sampleSha256.take(24) + "...",
                            color = MeetColors.cyberCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = { onNavigateToReportCategory(active.category) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = visual.color),
            ) {
                Icon(Icons.Default.AddAlert, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "CREAR REPORTE DE ${active.title.uppercase()} AHORA",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

@Composable
private fun DataRow(
    label: String,
    value: String,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            label,
            color = MeetColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(90.dp),
        )
        Text(
            value,
            color = accent,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
