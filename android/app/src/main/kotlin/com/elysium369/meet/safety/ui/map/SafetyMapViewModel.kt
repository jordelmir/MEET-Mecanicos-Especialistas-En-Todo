package com.elysium369.meet.safety.ui.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyPublicRepository
import com.elysium369.meet.safety.data.SafetyRepository
import com.elysium369.meet.safety.data.SafetyPrivateMapPoint
import com.elysium369.meet.safety.data.local.SafetyPublicPointEntity
import com.elysium369.meet.safety.geo.SafetyMapAdapter
import com.elysium369.meet.safety.geo.SafetyPublicPoint
import com.elysium369.meet.core.geo.CommonMapState
import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.safety.location.FusedSafetyLocationProvider
import android.content.Context
import com.elysium369.meet.safety.evidence.SafetyEvidenceEntity
import com.elysium369.meet.safety.evidence.SafetyEvidenceRepository
import com.elysium369.meet.safety.science.data.SafetyScienceDao
import com.elysium369.meet.safety.science.data.SciClaimEntity
import com.elysium369.meet.safety.science.data.SciHypothesisEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SafetyMapLayer(val category: String?) {
    ALL(null), HOMICIDE("HOMICIDE"), VIOLENCE("VIOLENT_INCIDENT"),
    DRUG_ACTIVITY("DRUG_SALE_ACTIVITY"), THREAT("THREAT"),
    MISSING_PERSON("MISSING_PERSON"), INSTITUTIONAL("INSTITUTIONAL_CONDUCT"),
    SICOP_PROCUREMENT("CORRUPTION_PUBLIC_PROCUREMENT"),
    CORPORATE_STRUCTURES("CORPORATE_OPACITY_CONFLICT"),
    FINANCIAL_INTELLIGENCE("FINANCIAL_FRAUD"),
}
enum class SafetyTimeRange(val days: Long?) { DAYS_7(7), DAYS_30(30), YEAR_1(365), ALL(null) }

fun List<SafetyPublicPointEntity>.filterFor(layer: SafetyMapLayer, range: SafetyTimeRange, now: Long): List<SafetyPublicPointEntity> {
    val cutoff = range.days?.let { now - it * 86_400_000L }
    return filter { point ->
        val validDisclosure = when (point.geoDisclosure) {
            "COARSE_GRID_25KM_PLUS" -> (point.locationAccuracyMeters ?: 0) >= 25_000
            "EXACT_GEOLOCATED" -> (point.locationAccuracyMeters ?: 0) > 0
            else -> false
        }
        point.serverVersion > 0 && validDisclosure &&
            point.displayLatitude.isFinite() && point.displayLatitude in -90.0..90.0 &&
            point.displayLongitude.isFinite() && point.displayLongitude in -180.0..180.0 &&
            (layer.category == null || point.category == layer.category) &&
            (cutoff == null || point.publishedAt >= cutoff || (point.firstDocumentedAt ?: Long.MIN_VALUE) >= cutoff)
    }
}

data class SafetyMapSources(
    val public: List<SafetyPublicPointEntity>,
    val private: List<SafetyPrivateMapPoint>,
    val query: String,
    val evidenceByReport: Map<String, List<SafetyEvidenceEntity>>,
)

data class SafetyMapUiState(
    val mapState: CommonMapState = SafetyMapAdapter.build(emptyList()),
    val points: List<SafetyPublicPointEntity> = emptyList(),
    val privatePoints: List<SafetyPrivateMapPoint> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val pointCount: Int = 0,
    val layer: SafetyMapLayer = SafetyMapLayer.ALL,
    val range: SafetyTimeRange = SafetyTimeRange.ALL,
    val evidenceByReport: Map<String, List<SafetyEvidenceEntity>> = emptyMap(),
    val hypotheses: List<SciHypothesisEntity> = emptyList(),
    val claims: List<SciClaimEntity> = emptyList(),
)

private data class MapStatusAndScience(
    val busy: Boolean,
    val failure: String?,
    val hypotheses: List<SciHypothesisEntity>,
    val claims: List<SciClaimEntity>,
)

@HiltViewModel
class SafetyMapViewModel @Inject constructor(
    private val publicRepository: SafetyPublicRepository,
    private val safetyRepository: SafetyRepository,
    private val locationProvider: FusedSafetyLocationProvider,
    private val evidenceRepository: SafetyEvidenceRepository,
    private val safetyScienceDao: SafetyScienceDao,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()
    private val layer = savedStateHandle.getStateFlow("safetyLayer", SafetyMapLayer.ALL.name)
    private val range = savedStateHandle.getStateFlow("safetyRange", SafetyTimeRange.ALL.name)
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val search = savedStateHandle.getStateFlow("safetySearch", "")
    private val mapSources = combine(
        publicRepository.observePoints(),
        safetyRepository.observeMyPrivateMapPoints(),
        search,
        evidenceRepository.observeOwner(),
        publicRepository.publicEvidence,
    ) { public, private, query, localEvidenceList, publicEvidenceMap ->
        val mergedEvidence = mutableMapOf<String, MutableList<SafetyEvidenceEntity>>()

        publicEvidenceMap.forEach { (reportId, list) ->
            mergedEvidence.getOrPut(reportId) { mutableListOf() }.addAll(list)
            mergedEvidence.getOrPut(reportId.lowercase()) { mutableListOf() }.apply {
                val existingIds = map { it.evidenceId }.toSet()
                addAll(list.filter { it.evidenceId !in existingIds })
            }
        }

        localEvidenceList.groupBy { it.reportId }.forEach { (reportId, list) ->
            val existing = mergedEvidence.getOrPut(reportId) { mutableListOf() }
            val localIds = list.map { it.evidenceId }.toSet()
            existing.removeAll { it.evidenceId in localIds }
            existing.addAll(list)

            val existingLower = mergedEvidence.getOrPut(reportId.lowercase()) { mutableListOf() }
            existingLower.removeAll { it.evidenceId in localIds }
            existingLower.addAll(list)
        }

        SafetyMapSources(public, private, query, mergedEvidence)
    }
    private val scienceStatus = combine(
        loading,
        error,
        safetyScienceDao.observeHypotheses(),
        safetyScienceDao.observeRecentClaims(200),
    ) { busy, failure, hyps, clms ->
        MapStatusAndScience(busy, failure, hyps, clms)
    }

    val uiState = combine(mapSources, layer, range, scienceStatus) { sources, layerName, rangeName, sci ->
        val (all, allPrivate, query, evidenceGrouped) = sources
        val selectedLayer = SafetyMapLayer.valueOf(layerName)
        val selectedRange = SafetyTimeRange.valueOf(rangeName)
        val now = System.currentTimeMillis()
        val normalizedQuery = query.trim().lowercase()
        val points = all.filterFor(selectedLayer, selectedRange, now).filter { point ->
            normalizedQuery.isBlank() || listOfNotNull(point.label, point.category, point.countryCode, point.admin1Code, point.admin2Code, point.publicH3Cell)
                .any { it.lowercase().contains(normalizedQuery) }
        }
        val cutoff = selectedRange.days?.let { now - it * 86_400_000L }
        val privatePoints = allPrivate.filter { point ->
            (selectedLayer.category == null || point.category == selectedLayer.category) &&
                (cutoff == null || point.occurredAt >= cutoff)
        }
        SafetyMapUiState(
            mapState = SafetyMapAdapter.build(
                points.map { SafetyPublicPoint(it.publicPointId, it.displayLatitude, it.displayLongitude, it.label, it.claimState, it.independentSourceCount, it.category, it.geoDisclosure, it.locationAccuracyMeters) },
                privatePoints,
            ),
            points = points, privatePoints = privatePoints, isLoading = sci.busy, error = sci.failure,
            searchQuery = query,
            pointCount = points.size + privatePoints.size,
            layer = selectedLayer, range = selectedRange,
            evidenceByReport = evidenceGrouped,
            hypotheses = sci.hypotheses,
            claims = sci.claims,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SafetyMapUiState())

    fun openEvidence(context: Context, evidenceId: String, remoteStoragePath: String? = null, remoteMimeType: String? = null) {
        val path = remoteStoragePath ?: uiState.value.evidenceByReport.values.flatten()
            .find { it.evidenceId.equals(evidenceId, ignoreCase = true) }?.encryptedPath
        val mime = remoteMimeType ?: uiState.value.evidenceByReport.values.flatten()
            .find { it.evidenceId.equals(evidenceId, ignoreCase = true) }?.mimeType
        evidenceRepository.openEvidence(context, evidenceId, path, mime)
    }

    suspend fun loadEvidenceThumbnail(evidenceId: String, storagePath: String? = null): ByteArray? {
        val local = evidenceRepository.getDecryptedBytes(evidenceId)
        if (local != null) return local
        val effectivePath = storagePath ?: uiState.value.evidenceByReport.values.flatten()
            .find { it.evidenceId.equals(evidenceId, ignoreCase = true) }?.encryptedPath
        if (!effectivePath.isNullOrBlank() && !effectivePath.endsWith(".aead")) {
            return evidenceRepository.getRemotePublicBytes(evidenceId, effectivePath)
        }
        return null
    }

    init { viewModelScope.launch { publicRepository.realtimeWakeUps().collect { refreshNow() } } }
    fun selectLayer(value: SafetyMapLayer) { savedStateHandle["safetyLayer"] = value.name }
    fun selectRange(value: SafetyTimeRange) { savedStateHandle["safetyRange"] = value.name }
    fun updateSearch(value: String) { savedStateHandle["safetySearch"] = value.take(120) }
    fun refresh() { viewModelScope.launch { refreshNow() } }
    fun centerOnCurrentLocation() {
        viewModelScope.launch {
            try {
                val sample = locationProvider.currentLocation()
                _currentLocation.value = GeoPoint(sample.latitude, sample.longitude, sample.accuracyMeters)
                error.value = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "No se pudo obtener la ubicación actual. Revisa el GPS y los permisos." }
        }
    }
    fun locationPermissionDenied() { error.value = "Permiso de ubicación no concedido." }
    private suspend fun refreshNow() {
        loading.value = true
        try {
            safetyRepository.reconcileMyReports()
            publicRepository.refreshPoints()
            publicRepository.refreshEvidence()
            error.value = null
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error.value = "No se pudo actualizar. Se muestra la última información pública guardada." }
        finally { loading.value = false }
    }
}
