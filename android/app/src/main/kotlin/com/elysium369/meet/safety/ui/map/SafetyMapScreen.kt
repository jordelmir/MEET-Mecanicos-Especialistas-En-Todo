package com.elysium369.meet.safety.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.R
import com.elysium369.meet.core.geo.MapCameraIntent
import com.elysium369.meet.core.geo.runtime.CommonMapPanel
import com.elysium369.meet.safety.data.local.SafetyPublicPointEntity
import com.elysium369.meet.safety.ui.cases.safetyPublicDate
import com.elysium369.meet.safety.ui.common.SafetyCategoryIcons
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyMapScreen(
    onBack: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onSearchLocation: () -> Unit = onNavigateToReport,
    onSelectLocationOnMap: () -> Unit = onNavigateToReport,
    viewModel: SafetyMapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) viewModel.centerOnCurrentLocation() else viewModel.locationPermissionDenied()
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showList by rememberSaveable { mutableStateOf(false) }
    val selected = state.points.firstOrNull { it.publicPointId == selectedId }
    val selectedPrivate = state.privatePoints.firstOrNull { it.markerId == selectedId }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.safety_map_vanguard_title),
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                        Text(
                            stringResource(R.string.safety_map_vanguard_subtitle),
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.safety_public_back), tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.safety_public_refresh), color = MeetColors.cyberCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Metrics row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SafetyMetricCard(stringResource(R.string.safety_map_visible_points), state.pointCount.toString(), MeetColors.cyberCyan, Modifier.weight(1f))
                SafetyMetricCard(stringResource(R.string.safety_map_my_reports), state.privatePoints.size.toString(), MeetColors.neonGreen, Modifier.weight(1f))
                SafetyMetricCard(stringResource(R.string.safety_map_public_points), state.points.size.toString(), Color.White, Modifier.weight(1f))
            }

            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearch,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar en mapa histórico por zona o categoría...", fontSize = 12.sp, color = MeetColors.textSecondary) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.updateSearch("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpiar", tint = MeetColors.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeetColors.cyberCyan,
                    unfocusedBorderColor = MeetColors.borderSubtle,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )

            // Layer filter chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SafetyMapLayer.entries) { layer ->
                    val isSelected = state.layer == layer
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectLayer(layer) },
                        label = { Text(stringResource(layer.labelResource()), fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MeetColors.cyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = MeetColors.cyberCyan,
                        ),
                    )
                }
            }

            // Time range filter chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SafetyTimeRange.entries) { range ->
                    val isSelected = state.range == range
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectRange(range) },
                        label = { Text(stringResource(range.labelResource()), fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = MeetColors.neonGreen,
                        ),
                    )
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)), color = MeetColors.cyberCyan)
            }

            state.error?.let {
                Text(it, color = MeetColors.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.safety_public_map_disclosure),
                    color = MeetColors.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showList = !showList }) {
                    Text(
                        stringResource(if (showList) R.string.safety_public_show_map else R.string.safety_public_show_list, state.pointCount),
                        color = MeetColors.cyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (showList) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.privatePoints, key = { it.markerId }) { point ->
                        Card(
                            onClick = { selectedId = point.markerId },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                            border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MeetColors.neonGreen))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        stringResource(R.string.safety_private_report_status, point.serverState ?: point.syncState),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                    )
                                    Text("Reporte personal cifrado", fontSize = 11.sp, color = MeetColors.textSecondary)
                                }
                            }
                        }
                    }
                    items(state.points, key = { it.publicPointId }) { point ->
                        val catColor = SafetyCategoryIcons.colorForString(point.category)
                        val catIcon = SafetyCategoryIcons.iconForString(point.category)
                        Card(
                            onClick = { selectedId = point.publicPointId },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                            border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(catColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(catIcon, contentDescription = null, tint = catColor, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(point.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text(point.category.replace("_", " "), fontSize = 11.sp, color = catColor)
                                }
                                Text("${point.independentSourceCount} fuentes", fontSize = 11.sp, color = MeetColors.cyberCyan)
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.65f)),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        val visibleMapState = state.mapState.copy(
                            cameraIntent = currentLocation?.let { MapCameraIntent.CenterOn(it, 14.5) } ?: state.mapState.cameraIntent,
                            showRecenterButton = true,
                        )
                        CommonMapPanel(
                            visibleMapState,
                            Modifier.fillMaxSize(),
                            userLocation = currentLocation,
                            onRecenterRequested = {
                                locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            },
                            onMarkerClick = { selectedId = it },
                        )
                        if (state.pointCount == 0 && !state.isLoading) {
                            Column(
                                Modifier.align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .background(MeetColors.backgroundDeep.copy(alpha = 0.92f))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Top,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(stringResource(R.string.safety_map_no_public_points), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { point ->
        val regionPoints = state.points.filter { it.regionKey() == point.regionKey() }
        ModalBottomSheet(
            onDismissRequest = { selectedId = null },
            containerColor = MeetColors.cardBackground,
        ) {
            PublicPointDetail(point, point.regionLabel(), regionPoints.count { it.category == "HOMICIDE" }, regionPoints.size)
        }
    }

    selectedPrivate?.let { point ->
        ModalBottomSheet(
            onDismissRequest = { selectedId = null },
            containerColor = MeetColors.cardBackground,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.safety_private_report_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(stringResource(R.string.safety_private_report_status, point.serverState ?: point.syncState), color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.safety_private_report_date, safetyPublicDate(point.occurredAt)), color = MeetColors.textSecondary)
                Text(stringResource(R.string.safety_private_report_notice), style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
            }
        }
    }
}

@Composable
private fun SafetyMetricCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, color = valueColor, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.height(2.dp))
            Text(label, color = MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PublicPointDetail(point: SafetyPublicPointEntity, region: String, homicideCount: Int, totalCount: Int) {
    val catColor = SafetyCategoryIcons.colorForString(point.category)
    val catIcon = SafetyCategoryIcons.iconForString(point.category)

    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(catColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(catIcon, contentDescription = null, tint = catColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(point.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(point.category.replace("_", " "), fontSize = 12.sp, color = catColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.safety_map_region_summary, region, homicideCount, totalCount), color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.safety_public_claim_state, point.claimState), fontSize = 12.sp, color = MeetColors.neonGreen)
                }
            }
        }

        item {
            Text(stringResource(R.string.safety_public_geo_detail, point.geoDisclosure, point.locationAccuracyMeters?.toString() ?: stringResource(R.string.safety_public_unknown)), color = MeetColors.textSecondary, fontSize = 12.sp)
        }
        item {
            Text(stringResource(R.string.safety_public_sources_count, point.independentSourceCount), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        item {
            Text(
                stringResource(point.sourceMixStringRes(), point.civilSourceCount, point.journalisticSourceCount, point.publicRecordSourceCount, point.documentarySourceCount, point.institutionalSourceCount),
                color = MeetColors.cyberCyan,
                fontSize = 12.sp,
            )
        }
        item {
            Text(stringResource(R.string.safety_public_reviewed, safetyPublicDate(point.lastReviewedAt)), color = MeetColors.textSecondary, fontSize = 11.sp)
        }
        item {
            HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)
        }
        item {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Balance, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.safety_public_epistemic_notice), style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary, lineHeight = 16.sp)
            }
        }
    }
}

private fun SafetyPublicPointEntity.sourceMixStringRes() = R.string.safety_public_source_mix
private fun SafetyPublicPointEntity.regionKey(): String = admin2Code ?: admin1Code ?: countryCode ?: publicH3Cell ?: publicPointId
private fun SafetyPublicPointEntity.regionLabel(): String = admin2Code ?: admin1Code ?: countryCode ?: label
private fun SafetyMapLayer.labelResource() = when (this) {
    SafetyMapLayer.ALL -> R.string.safety_public_all
    SafetyMapLayer.HOMICIDE -> R.string.safety_public_homicide
    SafetyMapLayer.VIOLENCE -> R.string.safety_public_violence
    SafetyMapLayer.DRUG_ACTIVITY -> R.string.safety_public_drugs
    SafetyMapLayer.THREAT -> R.string.safety_public_threat
    SafetyMapLayer.MISSING_PERSON -> R.string.safety_public_missing
    SafetyMapLayer.INSTITUTIONAL -> R.string.safety_public_institutional
}
private fun SafetyTimeRange.labelResource() = when (this) {
    SafetyTimeRange.ALL -> R.string.safety_public_all_dates
    SafetyTimeRange.DAYS_7 -> R.string.safety_public_days7
    SafetyTimeRange.DAYS_30 -> R.string.safety_public_days30
    SafetyTimeRange.YEAR_1 -> R.string.safety_public_year
}
