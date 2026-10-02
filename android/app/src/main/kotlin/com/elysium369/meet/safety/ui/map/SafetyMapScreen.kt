package com.elysium369.meet.safety.ui.map

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.R
import com.elysium369.meet.core.geo.MapCameraIntent
import com.elysium369.meet.core.geo.runtime.CommonMapPanel
import com.elysium369.meet.safety.data.SafetyPrivateMapPoint
import com.elysium369.meet.safety.data.local.SafetyPublicPointEntity
import com.elysium369.meet.safety.domain.SafetyReportCategory
import com.elysium369.meet.safety.domain.SourceRelation
import com.elysium369.meet.safety.domain.label
import com.elysium369.meet.safety.domain.toObservatoryBadge
import com.elysium369.meet.safety.evidence.SafetyEvidenceEntity
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
    val context = LocalContext.current
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
                            color = MeetColors.textPrimary,
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.safety_public_back), tint = MeetColors.textPrimary)
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
                SafetyMetricCard(stringResource(R.string.safety_map_public_points), state.points.size.toString(), MeetColors.textPrimary, Modifier.weight(1f))
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
                    focusedTextColor = MeetColors.textPrimary,
                    unfocusedTextColor = MeetColors.textPrimary,
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
                                        color = MeetColors.textPrimary,
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
                                    Text(point.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MeetColors.textPrimary)
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
                                Text(stringResource(R.string.safety_map_no_public_points), color = MeetColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
        val evidenceList = state.evidenceByReport[point.reportId] ?: emptyList()
        ModalBottomSheet(
            onDismissRequest = { selectedId = null },
            containerColor = MeetColors.cardBackground,
        ) {
            PrivateReportDetailSheet(
                point = point,
                evidenceList = evidenceList,
                onClose = { selectedId = null },
                onOpenEvidence = { item -> viewModel.openEvidence(context, item.evidenceId) },
                onLoadThumbnail = { evidenceId -> viewModel.loadEvidenceThumbnail(evidenceId) },
            )
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
                    Text(point.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
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
            Text(stringResource(R.string.safety_public_geo_detail, stringResource(R.string.safety_public_coarse_area), point.locationAccuracyMeters?.toString() ?: stringResource(R.string.safety_public_unknown)), color = MeetColors.textSecondary, fontSize = 12.sp)
        }
        item {
            Text(stringResource(R.string.safety_public_sources_count, point.independentSourceCount), color = MeetColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

@Composable
private fun PrivateReportDetailSheet(
    point: SafetyPrivateMapPoint,
    evidenceList: List<SafetyEvidenceEntity>,
    onClose: () -> Unit,
    onOpenEvidence: (SafetyEvidenceEntity) -> Unit,
    onLoadThumbnail: suspend (String) -> ByteArray?,
) {
    val categoryColor = SafetyCategoryIcons.colorForString(point.category)
    val categoryIcon = SafetyCategoryIcons.iconForString(point.category)
    val categoryLabel = runCatching {
        SafetyReportCategory.valueOf(point.category).label()
    }.getOrDefault(point.category.replace("_", " "))

    val sourceRelationEnum = runCatching { SourceRelation.valueOf(point.sourceRelation) }.getOrNull()
    val sourceBadge = sourceRelationEnum?.toObservatoryBadge()

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // === 1. Header with Category, Icon, and Close button ===
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(categoryColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            categoryLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MeetColors.textPrimary,
                        )
                        Text(
                            "Ocurrió el ${safetyPublicDate(point.occurredAt)}",
                            fontSize = 11.sp,
                            color = MeetColors.textMuted,
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cerrar",
                        tint = MeetColors.textSecondary,
                    )
                }
            }
        }

        // === 2. Status & Authority Badges ===
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (statusIcon, statusColor, statusLabel) = resolvePrivateState(point)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                        Text(statusLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    }
                }

                if (point.serverVersion > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeetColors.cyberCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("v${point.serverVersion} autorizada", fontSize = 11.sp, color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // === 3. Provenance / Fuente de Información Badge ===
        if (sourceBadge != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    border = BorderStroke(1.dp, Color(sourceBadge.badgeColorHex).copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(sourceBadge.emoji, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                sourceBadge.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(sourceBadge.badgeColorHex),
                            )
                            Text(
                                sourceRelationEnum?.label() ?: sourceBadge.subtitle,
                                fontSize = 11.sp,
                                color = MeetColors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        // === 4. Coordinates & Accuracy Card ===
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "Coordenadas: ${String.format(java.util.Locale.US, "%.5f, %.5f", point.latitude, point.longitude)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MeetColors.textPrimary,
                        )
                        val accuracyText = point.accuracyMeters?.let { "Precisión: ±${it.toInt()}m" } ?: "Precisión no informada"
                        val sourceText = formatLocationSource(point.locationSource)
                        Text("$accuracyText · $sourceText", fontSize = 11.sp, color = MeetColors.textMuted)
                    }
                }
            }
        }

        // === 5. Victims Demographics (if any) ===
        if (point.victimCount != null && point.victimCount > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MeetColors.error, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Víctimas reportadas: ${point.victimCount}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MeetColors.error,
                            )
                            val details = listOfNotNull(
                                point.victimFemale?.let { "$it mujeres" },
                                point.victimMale?.let { "$it hombres" },
                            ).joinToString(" · ")
                            if (details.isNotBlank()) {
                                Text(details, fontSize = 11.sp, color = MeetColors.textSecondary)
                            }
                        }
                    }
                }
            }
        }

        // === 6. NARRATIVA COMPLETA / EXTENSA ===
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "TESTIMONIO / REPORTE COMPLETO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = MeetColors.cyberCyan,
                            letterSpacing = 1.sp,
                        )
                    }
                    if (point.narrative.isNotBlank()) {
                        Text(
                            "${point.narrative.length} caracteres",
                            fontSize = 10.sp,
                            color = MeetColors.textMuted,
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Text(
                        text = if (point.narrative.isNotBlank()) point.narrative else "Sin descripción narrativa proporcionada.",
                        color = MeetColors.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // === 7. ARCHIVOS ADJUNTOS / EVIDENCIA MULTIMEDIA ===
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "ARCHIVOS ADJUNTOS (${evidenceList.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MeetColors.cyberCyan,
                        letterSpacing = 1.sp,
                    )
                }

                if (evidenceList.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    ) {
                        Text(
                            "No se adjuntaron archivos o imágenes a este reporte.",
                            fontSize = 12.sp,
                            color = MeetColors.textMuted,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
        }

        // Attached files items
        items(evidenceList, key = { it.evidenceId }) { item ->
            EvidenceFileCard(
                item = item,
                onOpen = { onOpenEvidence(item) },
                onLoadThumbnail = onLoadThumbnail,
            )
        }

        // === 8. Security & Cryptographic Transparency Notice ===
        item {
            HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)
        }
        item {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Este reporte está sellado y protegido con criptografía soberana local (ChaCha20-Poly1305 / AEAD). La ubicación exacta y archivos solo son accesibles desde tu dispositivo autorizado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeetColors.textSecondary,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun EvidenceFileCard(
    item: SafetyEvidenceEntity,
    onOpen: () -> Unit,
    onLoadThumbnail: suspend (String) -> ByteArray?,
) {
    val isImage = item.mimeType.startsWith("image/")
    val (icon, typeLabel) = when {
        isImage -> Icons.Filled.Image to "Imagen"
        item.mimeType.startsWith("video/") -> Icons.Filled.Videocam to "Video"
        item.mimeType.startsWith("audio/") -> Icons.Filled.AudioFile to "Audio"
        item.mimeType == "application/pdf" -> Icons.Filled.PictureAsPdf to "PDF"
        else -> Icons.Filled.AttachFile to "Documento"
    }

    var imageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    if (isImage) {
        LaunchedEffect(item.evidenceId) {
            val bytes = onLoadThumbnail(item.evidenceId)
            if (bytes != null && bytes.isNotEmpty()) {
                imageBitmap = runCatching {
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = "Vista previa de imagen",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MeetColors.cyberCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            typeLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MeetColors.textPrimary,
                        )
                        Text(
                            "${item.byteCount / 1024} KB · ${item.mimeType}",
                            fontSize = 10.sp,
                            color = MeetColors.textMuted,
                        )
                    }
                }

                Button(
                    onClick = onOpen,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.cyberCyan.copy(alpha = 0.15f),
                        contentColor = MeetColors.cyberCyan,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Abrir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun resolvePrivateState(point: SafetyPrivateMapPoint): Triple<ImageVector, Color, String> {
    return when {
        point.serverState == "CONFIRMED" || point.syncState == "SYNCED" ->
            Triple(Icons.Filled.CloudDone, Color(0xFF10B981), "Sincronizado mundialmente")
        point.syncState == "SYNCING" ->
            Triple(Icons.Filled.CloudSync, Color(0xFF06B6D4), "Sincronizando...")
        point.syncState == "QUEUED" ->
            Triple(Icons.Filled.CloudUpload, Color(0xFFFFB020), "En cola de sincronización")
        point.syncState == "FAILED" ->
            Triple(Icons.Filled.CloudOff, Color(0xFFEF4444), "Error de sincronización")
        else ->
            Triple(Icons.Filled.CloudUpload, Color(0xFFFFB020), "Reporte local protegido")
    }
}

private fun formatLocationSource(source: String): String = when (source) {
    "DEVICE" -> "GPS del dispositivo"
    "MAP_SELECTION" -> "Selección manual en mapa"
    "USER_DESCRIPTION" -> "Búsqueda / descripción"
    else -> "Ubicación reportada"
}
