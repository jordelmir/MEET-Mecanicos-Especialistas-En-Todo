package com.elysium369.meet.safety.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.elysium369.meet.ui.theme.MeetColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.R
import com.elysium369.meet.core.geo.runtime.CommonMapPanel
import com.elysium369.meet.core.geo.MapCameraIntent
import com.elysium369.meet.safety.data.local.SafetyPublicPointEntity
import com.elysium369.meet.safety.ui.cases.safetyPublicDate

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
        TopAppBar(title = {
            Column {
                Text(stringResource(R.string.safety_map_vanguard_title), fontWeight = FontWeight.Black, color = Color.White)
                Text(stringResource(R.string.safety_map_vanguard_subtitle), style = MaterialTheme.typography.labelSmall, color = MeetColors.cyberCyan)
            }
        }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.safety_public_back)) }
        }, actions = { TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.safety_public_refresh)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.safety_map_general_title), color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.safety_map_general_body), color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SafetyMetricCard(stringResource(R.string.safety_map_visible_points), state.pointCount.toString(), Modifier.weight(1f))
                SafetyMetricCard(stringResource(R.string.safety_map_my_reports), state.privatePoints.size.toString(), Modifier.weight(1f))
                SafetyMetricCard(stringResource(R.string.safety_map_public_points), state.points.size.toString(), Modifier.weight(1f))
            }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearch,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar reportes históricos") },
                placeholder = { Text("Provincia, cantón, distrito, barrio, calle o categoría") },
                singleLine = true,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(SafetyMapLayer.entries) { layer ->
                    FilterChip(selected = state.layer == layer, onClick = { viewModel.selectLayer(layer) }, label = { Text(stringResource(layer.labelResource())) })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(SafetyTimeRange.entries) { range ->
                    FilterChip(selected = state.range == range, onClick = { viewModel.selectRange(range) }, label = { Text(stringResource(range.labelResource())) })
                }
            }
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
            Text(stringResource(R.string.safety_public_map_disclosure), color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            if (state.privatePoints.isNotEmpty()) Text(stringResource(R.string.safety_private_map_disclosure), Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showList = !showList }) { Text(stringResource(if (showList) R.string.safety_public_show_map else R.string.safety_public_show_list, state.pointCount)) }
            if (showList) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.privatePoints, key = { it.markerId }) { point ->
                        TextButton(onClick = { selectedId = point.markerId }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.safety_private_report_status, point.serverState ?: point.syncState))
                        }
                    }
                    items(state.points, key = { it.publicPointId }) { point ->
                        TextButton(onClick = { selectedId = point.publicPointId }, modifier = Modifier.fillMaxWidth()) { Text(point.label) }
                    }
                }
            } else Card(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = .65f)),
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
                            .background(MeetColors.backgroundDeep.copy(alpha = .92f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.safety_map_no_public_points), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    }
                }
            }
        }
    }
    selected?.let { point ->
        val regionPoints = state.points.filter { it.regionKey() == point.regionKey() }
        ModalBottomSheet(onDismissRequest = { selectedId = null }) {
            PublicPointDetail(point, point.regionLabel(), regionPoints.count { it.category == "HOMICIDE" }, regionPoints.size)
        }
    }
    selectedPrivate?.let { point -> ModalBottomSheet(onDismissRequest = { selectedId = null }) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.safety_private_report_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.safety_private_report_status, point.serverState ?: point.syncState))
            Text(stringResource(R.string.safety_private_report_date, safetyPublicDate(point.occurredAt)))
            Text(stringResource(R.string.safety_private_report_notice), style = MaterialTheme.typography.bodySmall)
        }
    } }
}

@Composable
private fun SafetyMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground), border = BorderStroke(1.dp, MeetColors.borderSubtle)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, color = MeetColors.cyberCyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text(label, color = MeetColors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PublicPointDetail(point: SafetyPublicPointEntity, region: String, homicideCount: Int, totalCount: Int) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(point.label, style = MaterialTheme.typography.titleLarge) }
        item { Text(stringResource(R.string.safety_map_region_summary, region, homicideCount, totalCount), color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold) }
        item { Text(stringResource(R.string.safety_public_claim_state, point.claimState)) }
        item { Text(stringResource(R.string.safety_public_geo_detail, point.geoDisclosure, point.locationAccuracyMeters?.toString() ?: stringResource(R.string.safety_public_unknown))) }
        item { Text(stringResource(R.string.safety_public_sources_count, point.independentSourceCount)) }
        item { Text(stringResource(R.string.safety_public_source_mix, point.civilSourceCount, point.journalisticSourceCount, point.publicRecordSourceCount, point.documentarySourceCount, point.institutionalSourceCount)) }
        item { Text(stringResource(R.string.safety_public_reviewed, safetyPublicDate(point.lastReviewedAt))) }
        item { Text(stringResource(R.string.safety_public_epistemic_notice), style = MaterialTheme.typography.bodySmall) }
    }
}

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
