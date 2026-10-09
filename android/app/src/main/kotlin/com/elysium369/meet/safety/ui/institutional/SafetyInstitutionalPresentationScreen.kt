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
