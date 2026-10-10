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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.elysium369.meet.safety.drugimpunity.DrugMarketImpunityStore
import com.elysium369.meet.safety.drugimpunity.DrugMarketImpunityClockCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.elysium369.meet.safety.evidence.SafetyEvidenceStatusTone
import com.elysium369.meet.safety.evidence.safetyEvidencePresentation
import com.elysium369.meet.safety.science.data.SciClaimEntity
import com.elysium369.meet.safety.science.data.SciHypothesisEntity
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
    onNavigateToResearch: () -> Unit = {},
    viewModel: SafetyMapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val impunityStore = remember(context) { DrugMarketImpunityStore(context.applicationContext) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) viewModel.centerOnCurrentLocation() else viewModel.locationPermissionDenied()
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showList by rememberSaveable { mutableStateOf(false) }

    val matchedPrivate = remember(selectedId, state.privatePoints) {
        val target = selectedId ?: return@remember null
        state.privatePoints.firstOrNull {
            it.markerId == target ||
            it.reportId == target ||
            it.markerId.equals(target, ignoreCase = true) ||
            it.reportId.equals(target.removePrefix("private:"), ignoreCase = true)
        }
    }

    val matchedPublic = remember(selectedId, matchedPrivate, state.points) {
        if (matchedPrivate != null) null
        else {
            val target = selectedId ?: return@remember null
            state.points.firstOrNull {
                it.publicPointId == target ||
                it.publicPointId.equals(target, ignoreCase = true) ||
                it.publicPointId.equals(target.removePrefix("private:"), ignoreCase = true)
            }
        }
    }

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

            MapTruthNotice()

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

    matchedPrivate?.let { point ->
        val evidenceList = state.evidenceByReport[point.reportId]
            ?: state.evidenceByReport[point.reportId.lowercase()]
            ?: emptyList()
        val reportId = point.reportId
        val shortId = if (reportId.length >= 8) reportId.substring(0, 8) else reportId
        val pointHypotheses = remember(state.hypotheses, point.reportId) {
            state.hypotheses.filter { hyp ->
                hyp.proposition.contains(shortId, ignoreCase = true) ||
                hyp.supportingEvidenceIdsJson.contains(point.reportId, ignoreCase = true) ||
                hyp.nullHypothesis?.contains(shortId, ignoreCase = true) == true
            }
        }
        val pointClaims = remember(state.claims, point.reportId) {
            state.claims.filter { clm ->
                clm.proposition.contains(shortId, ignoreCase = true) ||
                clm.predicate.contains(shortId, ignoreCase = true)
            }
        }
        ModalBottomSheet(
            onDismissRequest = { selectedId = null },
            containerColor = MeetColors.cardBackground,
        ) {
            PrivateReportDetailSheet(
                point = point,
                evidenceList = evidenceList,
                onClose = { selectedId = null },
                onOpenEvidence = { item -> viewModel.openEvidence(context, item.evidenceId, item.encryptedPath, item.mimeType) },
                onLoadThumbnail = { evidenceId, storagePath -> viewModel.loadEvidenceThumbnail(evidenceId, storagePath) },
                store = impunityStore,
                onNavigateToResearch = onNavigateToResearch,
                hypotheses = pointHypotheses,
                claims = pointClaims,
            )
        }
    }

    matchedPublic?.let { point ->
        val regionPoints = state.points.filter { it.regionKey() == point.regionKey() }
        val evidenceList = state.evidenceByReport[point.publicPointId]
            ?: state.evidenceByReport[point.publicPointId.lowercase()]
            ?: emptyList()
        val matchingPrivate = state.privatePoints.firstOrNull { it.reportId.equals(point.publicPointId, ignoreCase = true) }
        val pointId = point.publicPointId
        val shortId = if (pointId.length >= 8) pointId.substring(0, 8) else pointId
        val pointHypotheses = remember(state.hypotheses, point.publicPointId) {
            state.hypotheses.filter { hyp ->
                hyp.proposition.contains(shortId, ignoreCase = true) ||
                hyp.supportingEvidenceIdsJson.contains(point.publicPointId, ignoreCase = true) ||
                hyp.nullHypothesis?.contains(shortId, ignoreCase = true) == true
            }
        }
        val pointClaims = remember(state.claims, point.publicPointId) {
            state.claims.filter { clm ->
                clm.proposition.contains(shortId, ignoreCase = true) ||
                clm.predicate.contains(shortId, ignoreCase = true)
            }
        }
        ModalBottomSheet(
            onDismissRequest = { selectedId = null },
            containerColor = MeetColors.cardBackground,
        ) {
            PublicPointDetail(
                point = point,
                region = point.regionLabel(),
                homicideCount = regionPoints.count { it.category == "HOMICIDE" },
                totalCount = regionPoints.size,
                store = impunityStore,
                evidenceList = evidenceList,
                matchingPrivate = matchingPrivate,
                onClose = { selectedId = null },
                onOpenEvidence = { item -> viewModel.openEvidence(context, item.evidenceId, item.encryptedPath, item.mimeType) },
                onLoadThumbnail = { evidenceId, storagePath -> viewModel.loadEvidenceThumbnail(evidenceId, storagePath) },
                onNavigateToResearch = onNavigateToResearch,
                hypotheses = pointHypotheses,
                claims = pointClaims,
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
private fun PublicPointDetail(
    point: SafetyPublicPointEntity,
    region: String,
    homicideCount: Int,
    totalCount: Int,
    store: DrugMarketImpunityStore,
    evidenceList: List<SafetyEvidenceEntity> = emptyList(),
    matchingPrivate: SafetyPrivateMapPoint? = null,
    onClose: () -> Unit = {},
    onOpenEvidence: (SafetyEvidenceEntity) -> Unit = {},
    onLoadThumbnail: suspend (String, String?) -> ByteArray? = { _, _ -> null },
    onNavigateToResearch: () -> Unit = {},
    hypotheses: List<SciHypothesisEntity> = emptyList(),
    claims: List<SciClaimEntity> = emptyList(),
) {
    val catColor = SafetyCategoryIcons.colorForString(point.category)
    val catIcon = SafetyCategoryIcons.iconForString(point.category)
    val catLabel = runCatching {
        SafetyReportCategory.valueOf(point.category).label()
    }.getOrDefault(point.category.replace("_", " "))

    val context = LocalContext.current
    // Extract video URLs if any in label or matching private report
    val urlRegex = remember { Regex("""(https?://[^\s]+)""") }
    val labelUrls = remember(point.label) {
        urlRegex.findAll(point.label).map { it.value.trimEnd('.', ',', ';', ')', ']', '>') }.toList()
    }
    val privateUrls = matchingPrivate?.videoUrls ?: emptyList()
    val privateNarrativeUrls = remember(matchingPrivate?.narrative) {
        matchingPrivate?.narrative?.let { nar ->
            urlRegex.findAll(nar).map { it.value.trimEnd('.', ',', ';', ')', ']', '>') }.toList()
        } ?: emptyList()
    }
    val videoUrls = remember(labelUrls, privateUrls, privateNarrativeUrls) {
        (labelUrls + privateUrls + privateNarrativeUrls).filter { it.isNotBlank() }.distinct()
    }
    val cleanNarrative = remember(point.label, videoUrls) {
        var text = point.label
        videoUrls.forEach { u -> text = text.replace(u, "").trim() }
        text.replace("[VIDEOS ADJUNTOS]", "")
            .replace("[VIDEOS]", "")
            .trim()
            .ifBlank { point.label }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val isDrug = point.category == "DRUG_SALE_ACTIVITY" || point.category.contains("DRUG", ignoreCase = true)
        val isMissing = point.category == "MISSING_PERSON" || point.category.contains("MISSING", ignoreCase = true)
        val isHomicide = point.category == "HOMICIDE" || point.category.contains("HOMICID", ignoreCase = true)
        if (isDrug || isMissing || isHomicide) {
            item {
                DrugMarketImpunityClockCard(
                    pointId = point.publicPointId,
                    initialReportedAt = point.firstDocumentedAt ?: point.publishedAt,
                    store = store,
                    clockType = when {
                        isMissing -> com.elysium369.meet.safety.drugimpunity.ImpunityClockType.MISSING_PERSON
                        isHomicide -> com.elysium369.meet.safety.drugimpunity.ImpunityClockType.HOMICIDE
                        else -> com.elysium369.meet.safety.drugimpunity.ImpunityClockType.DRUG_SALE
                    },
                )
            }
        }

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
                            .background(catColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = catIcon,
                            contentDescription = null,
                            tint = catColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            catLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MeetColors.textPrimary,
                        )
                        val dateMs = point.firstDocumentedAt ?: point.publishedAt
                        Text(
                            "Ocurrió el ${safetyPublicDate(dateMs)}",
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.CloudDone, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                        Text("Disponible en mapa público", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                }

                if (point.serverVersion > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeetColors.cyberCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("Versión del servidor v${point.serverVersion}", fontSize = 11.sp, color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // === 3. Provenance / Fuente de Información Badge ===
        item {
            val sourceTitle = when {
                point.journalisticSourceCount > 0 -> "Fuente Periodística"
                point.institutionalSourceCount > 0 -> "Fuente Institucional"
                point.publicRecordSourceCount > 0 -> "Registro Público"
                else -> "Fuente ciudadana"
            }
            val sourceSubtitle = when {
                point.journalisticSourceCount > 0 -> "Investigación periodística / Medios"
                point.institutionalSourceCount > 0 -> "Origen institucional clasificado; consulte el documento de respaldo"
                point.publicRecordSourceCount > 0 -> "Expediente judicial o registral"
                else -> "Reporte ciudadano; no confirma por sí solo el incidente"
            }
            val sourceEmoji = when {
                point.journalisticSourceCount > 0 -> "📰"
                point.institutionalSourceCount > 0 -> "🏛️"
                point.publicRecordSourceCount > 0 -> "📄"
                else -> "🛡️"
            }
            val sourceColor = when {
                point.journalisticSourceCount > 0 -> Color(0xFF69F0AE)
                point.institutionalSourceCount > 0 -> Color(0xFFB388FF)
                point.publicRecordSourceCount > 0 -> Color(0xFFFFD700)
                else -> MeetColors.cyberCyan
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, sourceColor.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(sourceEmoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            sourceTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = sourceColor,
                        )
                        Text(
                            sourceSubtitle,
                            fontSize = 11.sp,
                            color = MeetColors.textSecondary,
                        )
                    }
                }
            }
        }

        // === 4. Coordenadas Card ===
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Coordenadas: ${String.format(java.util.Locale.US, "%.5f, %.5f", point.displayLatitude, point.displayLongitude)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MeetColors.textPrimary,
                        )
                        val accuracyText = point.locationAccuracyMeters?.let { "Precisión: ±${it}m" } ?: "Precisión no informada"
                        Text("$accuracyText · Proyección pública de Safety", fontSize = 11.sp, color = MeetColors.textMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            openWazeNavigation(context, point.displayLatitude, point.displayLongitude)
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_waze_logo),
                            contentDescription = "Navegar con Waze",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }

        // === 5. Victims Demographics ===
        if (point.victimCountDocumented > 0) {
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
                                "Víctimas documentadas: ${point.victimCountDocumented}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MeetColors.error,
                            )
                            val details = listOfNotNull(
                                point.victimFemaleCount.takeIf { it > 0 }?.let { "$it mujeres" },
                                point.victimMaleCount.takeIf { it > 0 }?.let { "$it hombres" },
                            ).joinToString(" · ")
                            if (details.isNotBlank()) {
                                Text(details, fontSize = 11.sp, color = MeetColors.textSecondary)
                            }
                        }
                    }
                }
            }
        }

        // === 6. Testimonio / Reporte Completo ===
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
                    if (cleanNarrative.isNotBlank()) {
                        Text(
                            "${cleanNarrative.length} caracteres",
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
                        text = cleanNarrative.ifBlank { "Sin descripción narrativa proporcionada." },
                        color = MeetColors.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // === DIMENSIÓN CIENTÍFICA & ANÁLISIS FORENSE ===
        item {
            ScientificDimensionMapSection(
                hypotheses = hypotheses,
                claims = claims,
                onNavigateToResearch = onNavigateToResearch,
                categoryName = point.category,
            )
        }

        // === 7. Archivos adjuntos / Evidencia multimedia ===
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

        items(evidenceList, key = { it.evidenceId }) { item ->
            EvidenceFileCard(
                item = item,
                onOpen = { onOpenEvidence(item) },
                onLoadThumbnail = onLoadThumbnail,
            )
        }

        // === 8. Videos adjuntos por enlace (One-click launch!) ===
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "VIDEOS ADJUNTOS (${videoUrls.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MeetColors.neonGreen,
                        letterSpacing = 1.sp,
                    )
                }
                if (videoUrls.isNotEmpty()) {
                    Text(
                        "Toca el enlace para reproducir el video directamente en YouTube, TikTok, Drive o navegador:",
                        fontSize = 11.sp,
                        color = MeetColors.textSecondary,
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    ) {
                        Text(
                            "No se adjuntaron enlaces de video a este reporte.",
                            fontSize = 12.sp,
                            color = MeetColors.textMuted,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
        }

        if (videoUrls.isNotEmpty()) {
            items(videoUrls) { url ->
                VideoLinkCard(
                    url = url,
                    onOpen = {
                        try {
                            val uri = android.net.Uri.parse(url)
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val uri = android.net.Uri.parse(url)
                                val chooser = android.content.Intent.createChooser(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                    "Ver video"
                                )
                                context.startActivity(chooser)
                            } catch (e: Exception) {
                                android.util.Log.e("SafetyMap", "Error opening video link $url", e)
                            }
                        }
                    }
                )
            }
        }

        // === 9. Epistemic & Security Notice ===
        item {
            HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)
        }
        item {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "El estado de sincronización indica recepción por el servidor, no que el incidente esté confirmado ni que el reporte se haya divulgado. La verificación de bytes de cada adjunto tiene un estado separado. ${stringResource(R.string.safety_public_epistemic_notice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeetColors.textSecondary,
                    lineHeight = 16.sp,
                )
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
    SafetyMapLayer.SICOP_PROCUREMENT -> R.string.safety_public_procurement
    SafetyMapLayer.CORPORATE_STRUCTURES -> R.string.safety_public_corporate
    SafetyMapLayer.FINANCIAL_INTELLIGENCE -> R.string.safety_public_financial
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
    onLoadThumbnail: suspend (String, String?) -> ByteArray? = { _, _ -> null },
    store: DrugMarketImpunityStore,
    onNavigateToResearch: () -> Unit = {},
    hypotheses: List<SciHypothesisEntity> = emptyList(),
    claims: List<SciClaimEntity> = emptyList(),
) {
    val categoryColor = SafetyCategoryIcons.colorForString(point.category)
    val categoryIcon = SafetyCategoryIcons.iconForString(point.category)
    val categoryLabel = runCatching {
        SafetyReportCategory.valueOf(point.category).label()
    }.getOrDefault(point.category.replace("_", " "))

    val sourceRelationEnum = runCatching { SourceRelation.valueOf(point.sourceRelation) }.getOrNull()
    val sourceBadge = sourceRelationEnum?.toObservatoryBadge()
    val context = LocalContext.current

    // Extract video URLs if any in narrative or point.videoUrls
    val urlRegex = remember { Regex("""(https?://[^\s]+)""") }
    val narrativeUrls = remember(point.narrative) {
        urlRegex.findAll(point.narrative).map { it.value.trimEnd('.', ',', ';', ')', ']', '>') }.toList()
    }
    val allVideoUrls = remember(point.videoUrls, narrativeUrls) {
        (point.videoUrls + narrativeUrls).filter { it.isNotBlank() }.distinct()
    }
    val cleanNarrative = remember(point.narrative, allVideoUrls) {
        var text = point.narrative
        allVideoUrls.forEach { u -> text = text.replace(u, "").trim() }
        text.replace("[VIDEOS ADJUNTOS]", "")
            .replace("[VIDEOS]", "")
            .trim()
            .ifBlank { point.narrative }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val isDrug = point.category == "DRUG_SALE_ACTIVITY" || point.category.contains("DRUG", ignoreCase = true)
        val isMissing = point.category == "MISSING_PERSON" || point.category.contains("MISSING", ignoreCase = true)
        val isHomicide = point.category == "HOMICIDE" || point.category.contains("HOMICID", ignoreCase = true)
        if (isDrug || isMissing || isHomicide) {
            item {
                DrugMarketImpunityClockCard(
                    pointId = point.reportId,
                    initialReportedAt = point.occurredAt.takeIf { it > 0 } ?: point.createdAt,
                    store = store,
                    clockType = when {
                        isMissing -> com.elysium369.meet.safety.drugimpunity.ImpunityClockType.MISSING_PERSON
                        isHomicide -> com.elysium369.meet.safety.drugimpunity.ImpunityClockType.HOMICIDE
                        else -> com.elysium369.meet.safety.drugimpunity.ImpunityClockType.DRUG_SALE
                    },
                )
            }
        }
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
                        Text("Versión del servidor v${point.serverVersion}", fontSize = 11.sp, color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold)
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
                    Column(modifier = Modifier.weight(1f)) {
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
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            openWazeNavigation(context, point.latitude, point.longitude)
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_waze_logo),
                            contentDescription = "Navegar con Waze",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp),
                        )
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
                    if (cleanNarrative.isNotBlank()) {
                        Text(
                            "${cleanNarrative.length} caracteres",
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
                        text = if (cleanNarrative.isNotBlank()) cleanNarrative else "Sin descripción narrativa proporcionada.",
                        color = MeetColors.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // === DIMENSIÓN CIENTÍFICA & ANÁLISIS FORENSE ===
        item {
            ScientificDimensionMapSection(
                hypotheses = hypotheses,
                claims = claims,
                onNavigateToResearch = onNavigateToResearch,
                categoryName = point.category,
            )
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

        // === 8. VIDEOS ADJUNTOS POR ENLACE ===
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "VIDEOS ADJUNTOS (${allVideoUrls.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MeetColors.neonGreen,
                        letterSpacing = 1.sp,
                    )
                }
                if (allVideoUrls.isNotEmpty()) {
                    Text(
                        "Toca el enlace para reproducir el video directamente en YouTube, TikTok, Drive o navegador:",
                        fontSize = 11.sp,
                        color = MeetColors.textSecondary,
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    ) {
                        Text(
                            "No se adjuntaron enlaces de video a este reporte.",
                            fontSize = 12.sp,
                            color = MeetColors.textMuted,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
        }

        if (allVideoUrls.isNotEmpty()) {
            items(allVideoUrls) { url ->
                VideoLinkCard(
                    url = url,
                    onOpen = {
                        try {
                            val uri = android.net.Uri.parse(url)
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val uri = android.net.Uri.parse(url)
                                val chooser = android.content.Intent.createChooser(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                    "Ver video"
                                )
                                context.startActivity(chooser)
                            } catch (e: Exception) {
                                android.util.Log.e("SafetyMap", "Error opening video link $url", e)
                            }
                        }
                    }
                )
            }
        }

        // === 9. Security & Cryptographic Transparency Notice ===
        item {
            HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)
        }
        item {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Esta vista muestra una proyección pública de campos autorizados. Los adjuntos y las coordenadas exactas siguen sujetos a permisos; una huella de bytes verificada no confirma la veracidad de su contenido ni su admisibilidad judicial.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeetColors.textSecondary,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

private data class VideoPlatformInfo(val name: String, val emoji: String)

private fun resolveVideoPlatform(url: String): VideoPlatformInfo {
    val lower = url.lowercase()
    return when {
        lower.contains("youtube.com") || lower.contains("youtu.be") -> VideoPlatformInfo("YouTube", "▶️")
        lower.contains("tiktok.com") -> VideoPlatformInfo("TikTok", "🎵")
        lower.contains("drive.google.com") -> VideoPlatformInfo("Google Drive", "📁")
        lower.contains("vimeo.com") -> VideoPlatformInfo("Vimeo", "🎬")
        lower.contains("twitter.com") || lower.contains("x.com") -> VideoPlatformInfo("X / Twitter", "🐦")
        lower.contains("instagram.com") -> VideoPlatformInfo("Instagram", "📸")
        lower.contains("facebook.com") || lower.contains("fb.watch") -> VideoPlatformInfo("Facebook", "👤")
        else -> VideoPlatformInfo("Enlace de Video", "🎥")
    }
}

@Composable
private fun VideoLinkCard(url: String, onOpen: () -> Unit) {
    val platform = resolveVideoPlatform(url)
    // Platform-aware vivid colors
    val platformColor = when {
        url.contains("youtube", true) || url.contains("youtu.be", true) -> Color(0xFFFF0000)
        url.contains("tiktok", true) -> Color(0xFFEE1D52)
        url.contains("instagram", true) -> Color(0xFFE4405F)
        url.contains("facebook", true) || url.contains("fb.watch", true) -> Color(0xFF1877F2)
        url.contains("drive.google", true) -> Color(0xFF34A853)
        url.contains("vimeo", true) -> Color(0xFF1AB7EA)
        url.contains("twitter", true) || url.contains("x.com", true) -> Color(0xFF1DA1F2)
        else -> MeetColors.neonGreen
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
        border = BorderStroke(1.5.dp, platformColor.copy(alpha = 0.8f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(platformColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(platform.emoji, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        platform.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = platformColor,
                    )
                    Text(
                        url,
                        fontSize = 10.sp,
                        color = MeetColors.textSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = platformColor,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ver video", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScientificDimensionMapSection(
    hypotheses: List<SciHypothesisEntity>,
    claims: List<SciClaimEntity>,
    onNavigateToResearch: () -> Unit,
    categoryName: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text("🔬", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "DIMENSIÓN CIENTÍFICA & ANÁLISIS FORENSE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MeetColors.cyberCyan,
                        letterSpacing = 0.8.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (hypotheses.isNotEmpty()) MeetColors.neonGreen.copy(alpha = 0.15f)
                            else MeetColors.cyberCyan.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (hypotheses.isNotEmpty()) "HIPÓTESIS REGISTRADA" else "REGISTRO EMPÍRICO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hypotheses.isNotEmpty()) MeetColors.neonGreen else MeetColors.cyberCyan,
                    )
                }
            }

            if (hypotheses.isNotEmpty()) {
                hypotheses.forEach { hyp ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MeetColors.cardBackground)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "HIPÓTESIS (H₁):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = MeetColors.cyberCyan,
                        )
                        Text(
                            hyp.proposition,
                            fontSize = 12.sp,
                            color = MeetColors.textPrimary,
                            lineHeight = 17.sp,
                        )
                        hyp.nullHypothesis?.let { nullHyp ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "HIPÓTESIS NULA (H₀):",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MeetColors.textSecondary,
                            )
                            Text(
                                nullHyp,
                                fontSize = 11.sp,
                                color = MeetColors.textSecondary,
                                lineHeight = 15.sp,
                            )
                        }
                        val falsification = hyp.falsificationCriteriaJson
                            .replace("[\"", "")
                            .replace("\"]", "")
                            .replace("\\\"", "\"")
                            .trim()
                        if (falsification.isNotBlank() && falsification != "[]") {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "⚡ CRITERIO POPPERIANO DE FALSACIÓN:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MeetColors.warning,
                            )
                            Text(
                                falsification,
                                fontSize = 11.sp,
                                color = MeetColors.textPrimary,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
            }

            if (claims.isNotEmpty()) {
                claims.forEach { clm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeetColors.cardBackground)
                            .padding(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("📌", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                "AFIRMACIÓN OBSERVADA (${clm.assertionState}):",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MeetColors.neonGreen,
                            )
                            Text(
                                clm.proposition,
                                fontSize = 11.sp,
                                color = MeetColors.textPrimary,
                            )
                        }
                    }
                }
            }

            if (hypotheses.isEmpty() && claims.isEmpty()) {
                Text(
                    "Este punto territorial cuenta con atestación de custodia criptográfica. Puedes formular hipótesis explicativas y correlacionar evidencia en la plataforma de investigación.",
                    fontSize = 11.sp,
                    color = MeetColors.textSecondary,
                    lineHeight = 16.sp,
                )
            }

            Button(
                onClick = onNavigateToResearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.cyberCyan.copy(alpha = 0.2f),
                    contentColor = MeetColors.cyberCyan,
                ),
                border = BorderStroke(1.dp, MeetColors.cyberCyan),
            ) {
                Text("🔬 Abrir en Plataforma Científica & Research", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun decodeSampledBitmap(bytes: ByteArray, maxDim: Int = 1080): android.graphics.Bitmap? {
    return runCatching {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        var sampleSize = 1
        val origMax = maxOf(options.outWidth, options.outHeight)
        while (origMax / (sampleSize * 2) >= maxDim) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }.getOrNull()
}

@Composable
private fun EvidenceFileCard(
    item: SafetyEvidenceEntity,
    onOpen: () -> Unit,
    onLoadThumbnail: suspend (String, String?) -> ByteArray?,
) {
    val isImage = item.mimeType.startsWith("image/")
    val (icon, typeLabel) = when {
        isImage -> Icons.Filled.Image to "Imagen"
        item.mimeType.startsWith("audio/") -> Icons.Filled.AudioFile to "Audio"
        item.mimeType == "application/pdf" -> Icons.Filled.PictureAsPdf to "PDF"
        else -> Icons.Filled.AttachFile to "Documento"
    }

    var imageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoadingThumbnail by remember { mutableStateOf(isImage) }

    if (isImage) {
        LaunchedEffect(item.evidenceId, item.encryptedPath) {
            isLoadingThumbnail = true
            val bytes = onLoadThumbnail(item.evidenceId, item.encryptedPath)
            if (bytes != null && bytes.isNotEmpty()) {
                imageBitmap = runCatching {
                    decodeSampledBitmap(bytes, 1080)?.asImageBitmap()
                }.getOrNull()
            }
            isLoadingThumbnail = false
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
            if (isImage) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap!!,
                        contentDescription = "Vista previa de imagen",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 240.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else if (isLoadingThumbnail) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MeetColors.cardBackgroundLighter),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MeetColors.cyberCyan,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                "Cargando imagen...",
                                color = MeetColors.textMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
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
                        val evidenceStatus = safetyEvidencePresentation(item.uploadState, item.lastErrorCode)
                        val evidenceStatusColor = when (evidenceStatus.tone) {
                            SafetyEvidenceStatusTone.VERIFIED -> MeetColors.neonGreen
                            SafetyEvidenceStatusTone.IN_PROGRESS -> MeetColors.cyberCyan
                            SafetyEvidenceStatusTone.PENDING -> MeetColors.warning
                            SafetyEvidenceStatusTone.ERROR -> MeetColors.error
                            SafetyEvidenceStatusTone.NEUTRAL -> MeetColors.textMuted
                        }
                        Text(
                            evidenceStatus.label,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(evidenceStatusColor.copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = evidenceStatusColor,
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
        point.syncState == "SYNCED" ->
            Triple(Icons.Filled.CloudDone, Color(0xFF10B981), "Recibido por el servidor")
        point.serverState != null ->
            Triple(Icons.Filled.CloudUpload, MeetColors.textMuted, "Estado remoto: ${point.serverState}")
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

/**
 * Opens Waze navigation to the given coordinates.
 * Deep-links via `waze://?ll=LAT,LON&navigate=yes` so Waze starts
 * turn-by-turn navigation immediately.
 * Falls back to Google Maps geo: intent if Waze is not installed.
 */
private fun openWazeNavigation(context: android.content.Context, lat: Double, lon: Double) {
    try {
        val wazeUri = android.net.Uri.parse(
            "waze://?ll=${String.format(java.util.Locale.US, "%.6f,%.6f", lat, lon)}&navigate=yes"
        )
        val wazeIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, wazeUri).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Check if Waze is installed before launching
        if (wazeIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(wazeIntent)
        } else {
            // Fallback: open Google Maps navigation
            val mapsUri = android.net.Uri.parse(
                "geo:0,0?q=${String.format(java.util.Locale.US, "%.6f,%.6f", lat, lon)}(Reporte)"
            )
            val mapsIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, mapsUri).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(mapsIntent)
        }
    } catch (e: Exception) {
        android.util.Log.e("SafetyMap", "Error opening Waze navigation for ($lat, $lon)", e)
        // Last-resort fallback: open Waze via Play Store
        try {
            val playUri = android.net.Uri.parse("market://details?id=com.waze")
            val playIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, playUri).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playIntent)
        } catch (_: Exception) {
            // Silently fail if even Play Store is not available
        }
    }
}


@Composable
internal fun MapTruthNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    MeetColors.cyberCyan.copy(alpha = 0.65f),
                    MeetColors.electricBlue.copy(alpha = 0.45f),
                    MeetColors.hotMagenta.copy(alpha = 0.28f),
                ),
            ),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MeetColors.cyberCyan,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "LECTURA RESPONSABLE DEL MAPA",
                    color = MeetColors.cyberCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    "Cada punto representa un registro accesible con su nivel de exposición. Un marcador no confirma por sí solo un delito; una capa vacía puede reflejar cobertura incompleta.",
                    color = MeetColors.textSecondary,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}
