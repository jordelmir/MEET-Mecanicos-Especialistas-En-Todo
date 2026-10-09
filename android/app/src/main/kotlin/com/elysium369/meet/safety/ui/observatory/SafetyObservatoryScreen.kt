package com.elysium369.meet.safety.ui.observatory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.elysium369.meet.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.safety.data.SafetyObservatoryMetrics
import com.elysium369.meet.safety.domain.DataCoverage
import com.elysium369.meet.safety.domain.DataCoveragePolicy
import com.elysium369.meet.safety.ui.common.SafetyShimmer
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyObservatoryScreen(
    onBack: () -> Unit = {},
    viewModel: SafetyObservatoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.safety_observatory_title),
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = MeetColors.textPrimary,
                        )
                        Text(
                            stringResource(R.string.safety_observatory_subtitle),
                            fontSize = 11.sp,
                            color = ObservatoryColors.accentCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.safety_back),
                            tint = MeetColors.textPrimary,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.safety_public_refresh), color = ObservatoryColors.accentCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // ── Filters Toggle ──
            item {
                OutlinedButton(
                    onClick = { showFilters = !showFilters },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ObservatoryColors.cardSurface,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ObservatoryColors.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = null,
                        tint = ObservatoryColors.accentCyan,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (showFilters) "Ocultar filtros avanzados" else "Filtros de consulta territorial y temporal",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (showFilters) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObservatoryColors.cardSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ObservatoryColors.cardBorder),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Filtros sobre ubicación pública aproximada • Fechas en UTC",
                                fontSize = 11.sp,
                                color = MeetColors.textSecondary,
                            )
                            val tfColors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MeetColors.textPrimary,
                                unfocusedTextColor = MeetColors.textPrimary,
                                focusedBorderColor = ObservatoryColors.accentCyan,
                                unfocusedBorderColor = ObservatoryColors.cardBorder,
                                focusedLabelColor = ObservatoryColors.accentCyan,
                                unfocusedLabelColor = MeetColors.textSecondary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(uiState.filters.from, { viewModel.setFilters(uiState.filters.copy(from = it)) }, label = { Text("Desde (ISO)") }, modifier = Modifier.weight(1f), colors = tfColors, singleLine = true)
                                OutlinedTextField(uiState.filters.to, { viewModel.setFilters(uiState.filters.copy(to = it)) }, label = { Text("Hasta (ISO)") }, modifier = Modifier.weight(1f), colors = tfColors, singleLine = true)
                            }
                            OutlinedTextField(uiState.filters.category, { viewModel.setFilters(uiState.filters.copy(category = it)) }, label = { Text("Categoría (ej. HOMICIDE)") }, modifier = Modifier.fillMaxWidth(), colors = tfColors, singleLine = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(uiState.filters.country, { viewModel.setFilters(uiState.filters.copy(country = it)) }, label = { Text("País") }, modifier = Modifier.weight(1f), colors = tfColors, singleLine = true)
                                OutlinedTextField(uiState.filters.admin1, { viewModel.setFilters(uiState.filters.copy(admin1 = it)) }, label = { Text("Provincia / Estado") }, modifier = Modifier.weight(1f), colors = tfColors, singleLine = true)
                            }
                            OutlinedTextField(uiState.filters.admin2, { viewModel.setFilters(uiState.filters.copy(admin2 = it)) }, label = { Text("Cantón / Municipio") }, modifier = Modifier.fillMaxWidth(), colors = tfColors, singleLine = true)

                            Button(
                                onClick = viewModel::refresh,
                                enabled = !uiState.isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = ObservatoryColors.accentCyan, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("CONSULTAR MÉTRICAS", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            // ── Loading / Error ──
            if (uiState.isLoading && uiState.stats == null) {
                item {
                    SafetyShimmer(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }

            uiState.error?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1111)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ObservatoryColors.accentRed.copy(alpha = 0.5f)),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = ObservatoryColors.accentRed)
                            Spacer(Modifier.width(10.dp))
                            Text(error, color = ObservatoryColors.accentRed, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Documented patterns are server projections; no people or guilt inference.
            if (uiState.illicitPatterns.isNotEmpty() || uiState.patternsUnavailable) item {
                Card(colors = CardDefaults.cardColors(containerColor = ObservatoryColors.cardSurface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Patrones documentados de actividad reportada", fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                        Text("Solo claims documentados con fuentes periodísticas, registros públicos o institucionales y agrupación independiente. No identifica personas ni establece culpabilidad.", fontSize = 12.sp, color = MeetColors.textSecondary)
                        if (uiState.patternsUnavailable) Text("Patrones pendientes de consulta; no equivale a ausencia de actividad.", color = MeetColors.textSecondary)
                        uiState.illicitPatterns.forEach { pattern ->
                            Text("Celda ${pattern.public_cell_id} · ${pattern.period_start.take(10)}: ${pattern.documented_claim_count} claims · ${pattern.independent_source_clusters} grupos independientes · ${pattern.institutional_response_events} respuestas documentadas · ${pattern.truth_state.publicLabel}", fontSize = 12.sp, color = MeetColors.textSecondary, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            // ── Dashboard content ──
            uiState.stats?.let { stats ->
                if (stats.privacy_suppressed) {
                    item {
                        Text("Proyección V3: semanas completas, retraso mínimo de 7 días y celdas con al menos 5 claims documentados. Los valores visibles son parciales; la ausencia de datos no significa ausencia de hechos ni inacción institucional.", color = MeetColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                    }
                }
                // KPI row 1
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KpiCard(
                            label = stringResource(R.string.safety_observatory_published_points),
                            value = stats.public_point_count.toString(),
                            accentColor = ObservatoryColors.accentCyan,
                            modifier = Modifier.weight(1f),
                        )
                        KpiCard(
                            label = stringResource(R.string.safety_observatory_homicides),
                            value = stats.homicide_count.toString(),
                            accentColor = ObservatoryColors.accentRed,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // KPI row 2
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KpiCard(
                            label = stringResource(R.string.safety_observatory_victims),
                            value = if (stats.sensitive_metrics_available) stats.total_victims_documented.toString() else "Dato no publicado",
                            accentColor = ObservatoryColors.accentAmber,
                            modifier = Modifier.weight(1f),
                        )
                        KpiCard(
                            label = stringResource(R.string.safety_observatory_sources),
                            value = if (stats.sensitive_metrics_available) stats.independent_source_count.toString() else "Dato no publicado",
                            accentColor = ObservatoryColors.accentGreen,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Coverage is meaningful only when source metrics were actually published.
                if (stats.sensitive_metrics_available) item {
                    val coverage = DataCoveragePolicy.classify(stats.public_point_count, stats.independent_source_count, 30L)
                    val coverageColor = when (coverage) {
                        DataCoverage.VERY_LOW -> ObservatoryColors.accentRed
                        DataCoverage.LOW -> ObservatoryColors.accentAmber
                        DataCoverage.MEDIUM -> ObservatoryColors.accentCyan
                        DataCoverage.HIGH -> ObservatoryColors.accentGreen
                    }
                    val coverageLabel = when (coverage) {
                        DataCoverage.VERY_LOW -> stringResource(R.string.safety_observatory_coverage_very_low)
                        DataCoverage.LOW -> stringResource(R.string.safety_observatory_coverage_low)
                        DataCoverage.MEDIUM -> stringResource(R.string.safety_observatory_coverage_medium)
                        DataCoverage.HIGH -> stringResource(R.string.safety_observatory_coverage_high)
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObservatoryColors.cardSurface),
                        border = BorderStroke(1.dp, coverageColor.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(coverageColor),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.safety_observatory_coverage_title),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = coverageColor,
                                        letterSpacing = 1.sp,
                                    )
                                }
                                Text(coverage.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(coverageLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.safety_observatory_coverage_desc),
                                fontSize = 11.sp,
                                color = MeetColors.textSecondary,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }

                // Pie chart — category
                item {
                    ObservatoryPieChart(
                        data = stats.categoryBreakdown(),
                        title = stringResource(R.string.safety_observatory_category_chart),
                    )
                }

                // Bar chart — victims by gender
                if (stats.total_victims_documented > 0) {
                    item {
                        VictimGenderChart(
                            data = stats.victimsByGender(),
                            title = stringResource(R.string.safety_observatory_victims_gender),
                        )
                    }
                }

                // Resolution time comparison
                if (stats.hasResolutionData) {
                    item {
                        ResolutionTimeChart(
                            avgAll = stats.avg_resolution_days_all,
                            avgFemale = stats.avg_resolution_days_female_victim,
                            avgMale = stats.avg_resolution_days_male_victim,
                            title = stringResource(R.string.safety_observatory_resolution_time),
                        )
                    }
                }

                // Bar chart — sources by type
                if (stats.sensitive_metrics_available) item {
                    ObservatoryBarChart(
                        data = stats.sourceBreakdown(),
                        title = stringResource(R.string.safety_observatory_sources_type),
                    )
                }

            if (stats.sensitive_metrics_available) {
                // ── Procedencia y Tipos de Fuentes de Información ──
                item {
                    val totalSources = (stats.civil_source_count + stats.journalistic_source_count +
                        stats.public_record_source_count + stats.documentary_source_count +
                        stats.institutional_source_count).coerceAtLeast(1L)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObservatoryColors.cardSurface),
                        border = BorderStroke(1.dp, ObservatoryColors.accentCyan.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Procedencia y Clasificación de la Información",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = MeetColors.textPrimary,
                            )
                            Text(
                                "El desglose por tipo de fuente solo se muestra cuando la proyección remota lo autoriza y dispone de esos datos. Los conteos de fuentes no deben inferirse a partir del número de reportes.",
                                fontSize = 12.sp,
                                color = MeetColors.textSecondary,
                                lineHeight = 16.sp,
                            )

                            // Source Provenance Items with percentages
                            SourceProvenanceDetailRow(
                                icon = "📰",
                                title = "Investigación Periodística",
                                desc = "Material clasificado como periodístico. La clasificación no autentica automáticamente la autoría ni el contenido.",
                                count = stats.journalistic_source_count,
                                total = totalSources,
                                color = Color(0xFF69F0AE)
                            )
                            SourceProvenanceDetailRow(
                                icon = "🛡️",
                                title = "Reportes Ciudadanos / Casos Públicos",
                                desc = "Reportes ciudadanos. Los estados de corroboración solo se muestran cuando la proyección autorizada los aporta.",
                                count = stats.civil_source_count,
                                total = totalSources,
                                color = Color(0xFF00E5FF)
                            )
                            SourceProvenanceDetailRow(
                                icon = "🏛️",
                                title = "Registros Públicos y Judiciales",
                                desc = "Material clasificado como registro público o judicial; verificar el documento original, la autoridad emisora y su alcance.",
                                count = stats.public_record_source_count,
                                total = totalSources,
                                color = Color(0xFFFFD700)
                            )
                            SourceProvenanceDetailRow(
                                icon = "🏢",
                                title = "Expedientes Institucionales",
                                desc = "Material declarado de origen institucional; la entidad emisora y la autenticidad requieren comprobación en la fuente.",
                                count = stats.institutional_source_count,
                                total = totalSources,
                                color = Color(0xFF82B1FF)
                            )
                            SourceProvenanceDetailRow(
                                icon = "📄",
                                title = "Evidencias Documentales y Peritajes",
                                desc = "Documentos y peritajes aportados. Una huella SHA-256 solo indica correspondencia de bytes cuando se calcula y verifica; no certifica la veracidad del contenido.",
                                count = stats.documentary_source_count,
                                total = totalSources,
                                color = Color(0xFFFF80AB)
                            )
                        }
                    }
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObservatoryColors.cardSurface),
                        border = BorderStroke(1.dp, ObservatoryColors.cardBorder),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "DESGLOSE DE FUENTES NO PUBLICADO",
                                color = ObservatoryColors.accentAmber,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp,
                            )
                            Text(
                                "La proyección actual suprime las métricas por tipo de fuente para proteger la privacidad. Elysium Safety no estima ni inventa esas cantidades; la ausencia del desglose no significa que no existan fuentes.",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }
            }

                // ── Filtro y Lista de Reportes Auditados ──
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Reportes Auditados en el Observatorio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MeetColors.textPrimary,
                    )
                    Text(
                        "Filtrar por tipología de fuente originaria:",
                        fontSize = 11.sp,
                        color = MeetColors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )

                    // Source Filter Chips Row
                    val filterOptions = listOf(
                        null to "Todos",
                        "JOURNALISTIC" to "📰 Periodístico",
                        "CIVIL" to "🛡️ Caso Público",
                        "PUBLIC_RECORD" to "🏛️ Registro Público",
                        "INSTITUTIONAL" to "🏢 Institucional",
                        "DOCUMENTARY" to "📄 Documental"
                    )

                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        items(filterOptions.size) { idx ->
                            val (key, label) = filterOptions[idx]
                            val isSelected = uiState.selectedSourceFilter == key
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setSourceFilter(key) },
                                label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ObservatoryColors.accentCyan.copy(alpha = 0.25f),
                                    selectedLabelColor = ObservatoryColors.accentCyan,
                                    containerColor = ObservatoryColors.cardSurface,
                                    labelColor = MeetColors.textSecondary
                                ),
                                border = BorderStroke(1.dp, if (isSelected) ObservatoryColors.accentCyan else ObservatoryColors.cardBorder)
                            )
                        }
                    }
                }

                // Filtered cases and points
                val selectedFilter = uiState.selectedSourceFilter
                val filteredCases = if (selectedFilter == null) {
                    uiState.recentCases
                } else {
                    uiState.recentCases.filter { c ->
                        val (label, _) = formatProvenanceForCase(c.caseType, c.title)
                        when (selectedFilter) {
                            "JOURNALISTIC" -> label.contains("Periodística", ignoreCase = true)
                            "CIVIL" -> label.contains("Ciudadano", ignoreCase = true) || label.contains("Público", ignoreCase = true)
                            "PUBLIC_RECORD" -> label.contains("Registro", ignoreCase = true) || label.contains("Judicial", ignoreCase = true)
                            "INSTITUTIONAL" -> label.contains("Institucional", ignoreCase = true)
                            "DOCUMENTARY" -> label.contains("Documental", ignoreCase = true)
                            else -> true
                        }
                    }
                }

                val filteredPoints = if (selectedFilter == null) {
                    uiState.recentPoints
                } else {
                    uiState.recentPoints.filter { p ->
                        when (selectedFilter) {
                            "JOURNALISTIC" -> p.journalisticSourceCount > 0
                            "CIVIL" -> p.civilSourceCount > 0
                            "PUBLIC_RECORD" -> p.publicRecordSourceCount > 0
                            "INSTITUTIONAL" -> p.institutionalSourceCount > 0
                            "DOCUMENTARY" -> p.documentarySourceCount > 0
                            else -> true
                        }
                    }
                }

                if (filteredCases.isNotEmpty()) {
                    items(filteredCases.size) { index ->
                        val case = filteredCases[index]
                        ObservatoryCaseProvenanceCard(case = case)
                    }
                }

                if (filteredPoints.isNotEmpty()) {
                    items(filteredPoints.size) { index ->
                        val point = filteredPoints[index]
                        ObservatoryPointProvenanceCard(point = point)
                    }
                }

                if (filteredCases.isEmpty() && filteredPoints.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = ObservatoryColors.cardSurface),
                            border = BorderStroke(1.dp, ObservatoryColors.cardBorder)
                        ) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "No se encontraron reportes con el filtro seleccionado.",
                                    fontSize = 13.sp,
                                    color = MeetColors.textSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(Modifier.height(6.dp))
                                TextButton(onClick = { viewModel.setSourceFilter(null) }) {
                                    Text("Ver todos los orígenes de información", color = ObservatoryColors.accentCyan)
                                }
                            }
                        }
                    }
                }

                // Epistemic disclosure
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObservatoryColors.cardSurface),
                        border = BorderStroke(1.dp, ObservatoryColors.cardBorder),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Balance, contentDescription = null, tint = ObservatoryColors.accentAmber, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.safety_observatory_methodology_title),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObservatoryColors.accentAmber,
                                    letterSpacing = 1.sp,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "• Los datos reflejan la proyección pública autorizada, no la totalidad de incidentes reales.\n" +
                                "• Las sumas de fuentes corresponden a fuentes por punto publicado; una fuente puede aparecer en varios puntos.\n" +
                                "• Los promedios de resolución se calculan sobre casos con accountability events documentados.\n" +
                                "• Un reporte no demuestra un hecho. La corroboración no demuestra culpabilidad.\n" +
                                "• El conteo de víctimas es reportado por las fuentes y puede no ser exacto.",
                                fontSize = 11.sp,
                                color = MeetColors.textSecondary,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }

            // Bottom spacing
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SourceProvenanceDetailRow(
    icon: String,
    title: String,
    desc: String,
    count: Long,
    total: Long,
    color: Color,
) {
    val percentage = if (total > 0) ((count.toFloat() / total.toFloat()) * 100).toInt() else 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(icon, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = color.copy(alpha = 0.18f),
                border = BorderStroke(0.8.dp, color.copy(alpha = 0.6f))
            ) {
                Text(
                    text = "$count fuentes ($percentage%)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = color,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(desc, fontSize = 11.sp, color = MeetColors.textSecondary, lineHeight = 15.sp)
    }
}

@Composable
private fun ObservatoryCaseProvenanceCard(case: com.elysium369.meet.safety.data.local.SafetyPublicCaseEntity) {
    val (sourceLabel, sourceColor) = formatProvenanceForCase(case.caseType, case.title)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ObservatoryColors.cardSurface),
        border = BorderStroke(1.dp, sourceColor.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = sourceColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, sourceColor.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = sourceLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = sourceColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = case.lifecycle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (case.lifecycle) {
                        "OPEN" -> MeetColors.cyberCyan
                        "CLOSED" -> MeetColors.neonGreen
                        else -> MeetColors.warning
                    }
                )
            }

            Text(
                case.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MeetColors.textPrimary
            )

            if (case.publicSummary.isNotBlank()) {
                Text(
                    case.publicSummary,
                    fontSize = 12.sp,
                    color = MeetColors.textSecondary,
                    maxLines = 3,
                    lineHeight = 16.sp
                )
            }

            // Provenance & Metrics row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070E1A))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tipo de Fuente: $sourceLabel",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = sourceColor
                )
                Text(
                    "Fuentes: ${case.sourceCount ?: 1} · Hechos: ${case.claimCount ?: 1}",
                    fontSize = 10.sp,
                    color = MeetColors.textSecondary
                )
            }

            Text(
                "🛡️ Datos anonimizados conforme a la Constitución de Seguridad. Identifica el canal de origen sin juicio de personas.",
                fontSize = 9.sp,
                color = MeetColors.textSecondary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ObservatoryPointProvenanceCard(point: com.elysium369.meet.safety.data.local.SafetyPublicPointEntity) {
    val (sourceLabel, sourceColor) = point.provenanceBadge()
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ObservatoryColors.cardSurface),
        border = BorderStroke(1.dp, sourceColor.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = sourceColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, sourceColor.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = sourceLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = sourceColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = point.category,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ObservatoryColors.accentCyan
                )
            }

            Text(
                point.label,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MeetColors.textPrimary
            )

            // Sources tally
            val activeSources = buildList {
                if (point.journalisticSourceCount > 0) add("📰 Periodístico (${point.journalisticSourceCount})")
                if (point.civilSourceCount > 0) add("🛡️ Ciudadano (${point.civilSourceCount})")
                if (point.publicRecordSourceCount > 0) add("🏛️ Reg. Público (${point.publicRecordSourceCount})")
                if (point.institutionalSourceCount > 0) add("🏢 Institucional (${point.institutionalSourceCount})")
                if (point.documentarySourceCount > 0) add("📄 Documental (${point.documentarySourceCount})")
            }

            if (activeSources.isNotEmpty()) {
                Text(
                    "Canales de origen: ${activeSources.joinToString(" · ")}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = sourceColor
                )
            } else {
                Text(
                    "Fuentes independientes: ${point.independentSourceCount} · Estado: ${point.claimState}",
                    fontSize = 11.sp,
                    color = MeetColors.textSecondary
                )
            }

            Text(
                "Punto geodivulgado con resolución segura • No identifica domicilios ni personas.",
                fontSize = 9.sp,
                color = MeetColors.textSecondary.copy(alpha = 0.7f)
            )
        }
    }
}

fun formatProvenanceForCase(caseType: String?, title: String = ""): Pair<String, Color> {
    val type = caseType?.uppercase() ?: ""
    val lower = title.lowercase()
    return when {
        type.contains("JOURNAL") || lower.contains("prensa") || lower.contains("periodis") || lower.contains("noticia") || lower.contains("investiga") ->
            "📰 Investigación Periodística" to Color(0xFF69F0AE)
        type.contains("INSTITUTION") || lower.contains("fiscal") || lower.contains("polic") || lower.contains("ministerio") || lower.contains("oficial") ->
            "🏢 Expediente Institucional" to Color(0xFF82B1FF)
        type.contains("RECORD") || type.contains("PUBLIC_RECORD") || lower.contains("juzgado") || lower.contains("gaceta") || lower.contains("tribunal") ->
            "🏛️ Registro Público / Judicial" to Color(0xFFFFD700)
        type.contains("DOCUMENT") || lower.contains("peritaje") || lower.contains("forense") ->
            "📄 Evidencia Documental" to Color(0xFFFF80AB)
        else ->
            "🛡️ Caso Público / Reporte Ciudadano" to Color(0xFF00E5FF)
    }
}

fun com.elysium369.meet.safety.data.local.SafetyPublicPointEntity.provenanceBadge(): Pair<String, Color> {
    return when {
        journalisticSourceCount > 0 -> "📰 Investigación Periodística" to Color(0xFF69F0AE)
        publicRecordSourceCount > 0 -> "🏛️ Registro Público" to Color(0xFFFFD700)
        institutionalSourceCount > 0 -> "🏢 Expediente Institucional" to Color(0xFF82B1FF)
        documentarySourceCount > 0 -> "📄 Evidencia Documental" to Color(0xFFFF80AB)
        civilSourceCount > 0 -> "🛡️ Reporte Ciudadano" to Color(0xFF00E5FF)
        else -> "🛡️ Caso Público" to Color(0xFF00E5FF)
    }
}
