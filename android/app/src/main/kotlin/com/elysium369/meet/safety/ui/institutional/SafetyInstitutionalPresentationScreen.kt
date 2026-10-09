package com.elysium369.meet.safety.ui.institutional

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Visible institutional overview for Elysium Safety.
 *
 * Existing destinations open the application's current Safety screens. SICOP is
 * intentionally presented as NOT INTEGRATED: the current adapter is a domain
 * normalizer, not a live SICOP fetcher or a populated procurement dashboard.
 * No synthetic case, procurement record, or investigative finding is displayed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyInstitutionalPresentationScreen(
    onBack: () -> Unit,
    onNavigateToReport: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToMyReports: () -> Unit,
    onNavigateToCases: () -> Unit,
    onNavigateToTimelines: () -> Unit,
    onNavigateToObservatory: () -> Unit,
    onNavigateToResearch: () -> Unit,
) {
    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ELYSIUM SAFETY",
                            color = MeetColors.textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                        )
                        Text(
                            text = "PRESENTACIÓN INSTITUCIONAL",
                            color = MeetColors.cyberCyan,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al centro de seguridad",
                            tint = MeetColors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeetColors.backgroundDeep,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MeetColors.cardBackground,
                    ),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.65f)),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            text = "SEGURIDAD · EVIDENCIA · TERRITORIO",
                            color = MeetColors.cyberCyan,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            letterSpacing = 1.1.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Información trazable para decisiones responsables",
                            color = MeetColors.textPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Elysium Safety ayuda a organizar reportes, evidencia y cronologías para que la información pueda revisarse con contexto y procedencia. Un reporte no es una prueba de culpabilidad y una anomalía no demuestra un delito.",
                            color = MeetColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MeetColors.backgroundDeep,
                        ) {
                            Text(
                                text = "La aplicación no sustituye a la Fuerza Pública, el OIJ, el Ministerio Público ni al Poder Judicial.",
                                modifier = Modifier.padding(12.dp),
                                color = MeetColors.textPrimary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onNavigateToReport,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 13.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ReportProblem,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text("Crear reporte")
                    }
                    OutlinedButton(
                        onClick = onNavigateToMap,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 13.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Map,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text("Abrir mapa")
                    }
                }
            }

            item {
                Text(
                    text = "GUÍA DE USO · RECORRIDO RECOMENDADO",
                    color = MeetColors.cyberCyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.0.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                GuideStepCard(
                    number = "01",
                    title = "Registra el acontecimiento",
                    body = "Describe qué ocurrió, cuándo y dónde. Separa lo que observaste directamente de lo que otra persona te contó. Si no sabes un dato, déjalo como desconocido en lugar de adivinar.",
                    accent = MeetColors.neonGreen,
                )
            }
            item {
                GuideStepCard(
                    number = "02",
                    title = "Conserva el material original",
                    body = "Adjunta material que tengas derecho a compartir. No edites el original para hacerlo parecer concluyente; añade contexto por separado y evita exponer a personas vulnerables o ubicaciones residenciales.",
                    accent = MeetColors.cyberCyan,
                )
            }
            item {
                GuideStepCard(
                    number = "03",
                    title = "Contextualiza el tiempo y el territorio",
                    body = "Consulta el mapa y la cronología para ordenar los hechos. Una ubicación aproximada, un hueco de datos o una coincidencia temporal no prueban causalidad ni identifican automáticamente a una persona.",
                    accent = MeetColors.electricBlue,
                )
            }
            item {
                GuideStepCard(
                    number = "04",
                    title = "Contrasta antes de concluir",
                    body = "Relaciona cada afirmación con las fuentes que realmente la respaldan. Distingue documentos originales de copias o republicaciones y registra también contradicciones y explicaciones alternativas.",
                    accent = MeetColors.hotMagenta,
                )
            }
            item {
                GuideStepCard(
                    number = "05",
                    title = "Solicita revisión autorizada",
                    body = "Comparte solo con destinatarios habilitados y mediante un procedimiento aprobado. El sistema organiza información; no determina culpabilidad ni sustituye una investigación formal.",
                    accent = MeetColors.warning,
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "QUÉ SIGNIFICA CADA ESTADO",
                            color = MeetColors.textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        StatusLegendRow("REGISTRADO", "Alguien lo reportó; aún no implica confirmación.", MeetColors.electricBlue)
                        StatusLegendRow("CORROBORADO", "Existen fuentes adicionales cuya independencia debe verificarse.", MeetColors.cyberCyan)
                        StatusLegendRow("PENDIENTE", "Falta revisión, datos o una integración autorizada.", MeetColors.warning)
                        StatusLegendRow("REFUTADO / CORREGIDO", "La contradicción queda registrada; no debe borrarse silenciosamente.", MeetColors.hotMagenta)
                    }
                }
            }

            item {
                Text(
                    text = "PANTALLAS DISPONIBLES EN ESTA APLICACIÓN",
                    color = MeetColors.cyberCyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                InstitutionalActionCard(
                    title = "Reportar un incidente",
                    description = "Abrir el formulario de Safety para documentar un acontecimiento.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.ReportProblem,
                    iconColor = MeetColors.neonGreen,
                    onClick = onNavigateToReport,
                )
            }
            item {
                InstitutionalActionCard(
                    title = "Mapa territorial",
                    description = "Consultar la pantalla de mapa y sus capas disponibles según los datos y permisos configurados.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.Map,
                    iconColor = MeetColors.cyberCyan,
                    onClick = onNavigateToMap,
                )
            }
            item {
                InstitutionalActionCard(
                    title = "Mis reportes",
                    description = "Consultar los reportes accesibles para la sesión actual.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.Assignment,
                    iconColor = MeetColors.electricBlue,
                    onClick = onNavigateToMyReports,
                )
            }
            item {
                InstitutionalActionCard(
                    title = "Expedientes de seguridad",
                    description = "Abrir la vista de casos y revisar los registros que estén disponibles para la cuenta.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.FolderOpen,
                    iconColor = MeetColors.cyberCyan,
                    onClick = onNavigateToCases,
                )
            }
            item {
                InstitutionalActionCard(
                    title = "Cronología de acontecimientos",
                    description = "Abrir la vista temporal de Safety.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.Timeline,
                    iconColor = MeetColors.hotMagenta,
                    onClick = onNavigateToTimelines,
                )
            }
            item {
                InstitutionalActionCard(
                    title = "Observatorio de seguridad",
                    description = "Consultar los indicadores disponibles sin interpretar ausencia de datos como ausencia de incidentes.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.Analytics,
                    iconColor = MeetColors.electricBlue,
                    onClick = onNavigateToObservatory,
                )
            }
            item {
                InstitutionalActionCard(
                    title = "Investigación científica y cadena de evidencia",
                    description = "Abrir las herramientas de entidades, afirmaciones, hipótesis, contradicciones y procedencia.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.Analytics,
                    iconColor = MeetColors.hotMagenta,
                    onClick = onNavigateToResearch,
                )
            }

            item {
                Text(
                    text = "CAPACIDADES EN DESARROLLO / INTEGRACIÓN PENDIENTE",
                    color = MeetColors.warning,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.0.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item {
                StatusNoticeCard(
                    title = "Contratación pública y SICOP",
                    status = "INGESTA REAL NO INTEGRADA",
                    body = "El adaptador de normalización y la regla de concentración existen como lógica de dominio. Esta pantalla no consulta SICOP, no tiene un catálogo de adjudicaciones conectado y no produce hallazgos reales. Para activarlo faltan un adaptador de captura verificable, procedencia de los documentos, cobertura de datos, permisos y pruebas de integración.",
                )
            }
            item {
                StatusNoticeCard(
                    title = "Intercambio con instituciones",
                    status = "PENDIENTE DE PILOTO AUTORIZADO",
                    body = "No se afirma que exista una conexión operativa con Fuerza Pública, OIJ, Ministerio Público o Poder Judicial. El intercambio debe acordarse con cada institución, con base jurídica, control de acceso, auditoría y pruebas de extremo a extremo.",
                )
            }
            item {
                StatusNoticeCard(
                    title = "Regla de integridad",
                    status = "EVIDENCIA ≠ CULPABILIDAD",
                    body = "Una huella criptográfica puede ayudar a detectar cambios en determinados bytes; no demuestra por sí sola que el contenido sea verdadero, que la fuente sea independiente ni que haya ocurrido un delito. Las decisiones sensibles requieren revisión humana autorizada.",
                )
            }
        }
    }
}

@Composable
private fun GuideStepCard(
    number: String,
    title: String,
    body: String,
    accent: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.14f),
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = number,
                        color = accent,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(body, color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatusLegendRow(
    label: String,
    explanation: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = color.copy(alpha = 0.14f),
        ) {
            Text(
                text = label,
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = explanation,
            color = MeetColors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InstitutionalActionCard(
    title: String,
    description: String,
    status: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = iconColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MeetColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    color = MeetColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = status,
                    color = MeetColors.cyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 0.7.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Abrir $title",
                tint = MeetColors.cyberCyan,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StatusNoticeCard(
    title: String,
    status: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = MeetColors.textPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = status,
                color = MeetColors.warning,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                letterSpacing = 0.65.sp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = body,
                color = MeetColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
