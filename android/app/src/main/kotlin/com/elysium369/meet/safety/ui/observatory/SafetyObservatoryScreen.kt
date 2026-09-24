package com.elysium369.meet.safety.ui.observatory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
                            color = Color.White,
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
                            tint = Color.White,
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
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
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

            // ── Dashboard content ──
            uiState.stats?.let { stats ->
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
                            value = stats.total_victims_documented.toString(),
                            accentColor = ObservatoryColors.accentAmber,
                            modifier = Modifier.weight(1f),
                        )
                        KpiCard(
                            label = stringResource(R.string.safety_observatory_sources),
                            value = stats.independent_source_count.toString(),
                            accentColor = ObservatoryColors.accentGreen,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Data Coverage banner (§31 of Execution Protocol)
                item {
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
                            Text(coverageLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                item {
                    ObservatoryBarChart(
                        data = stats.sourceBreakdown(),
                        title = stringResource(R.string.safety_observatory_sources_type),
                    )
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
