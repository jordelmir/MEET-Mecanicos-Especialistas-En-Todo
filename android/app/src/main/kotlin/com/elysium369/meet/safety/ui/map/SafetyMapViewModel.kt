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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SafetyMapLayer(val category: String?) {
    ALL(null), HOMICIDE("HOMICIDE"), VIOLENCE("VIOLENT_INCIDENT"),
    DRUG_ACTIVITY("DRUG_SALE_ACTIVITY"), THREAT("THREAT"),
    MISSING_PERSON("MISSING_PERSON"), INSTITUTIONAL("INSTITUTIONAL_CONDUCT"),
}
enum class SafetyTimeRange(val days: Long?) { DAYS_7(7), DAYS_30(30), YEAR_1(365), ALL(null) }

fun List<SafetyPublicPointEntity>.filterFor(layer: SafetyMapLayer, range: SafetyTimeRange, now: Long): List<SafetyPublicPointEntity> {
    val cutoff = range.days?.let { now - it * 86_400_000L }
    return filter { point ->
        point.serverVersion > 0 && point.geoDisclosure in setOf("APPROXIMATE_1000M", "APPROXIMATE_500M", "STREET_SEGMENT", "EXACT_PUBLIC_PLACE") &&
            point.displayLatitude.isFinite() && point.displayLatitude in -90.0..90.0 &&
            point.displayLongitude.isFinite() && point.displayLongitude in -180.0..180.0 &&
            (layer.category == null || point.category == layer.category) &&
            (cutoff == null || point.publishedAt >= cutoff || (point.firstDocumentedAt ?: Long.MIN_VALUE) >= cutoff)
    }
}

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
)

@HiltViewModel
class SafetyMapViewModel @Inject constructor(
    private val publicRepository: SafetyPublicRepository,
    private val safetyRepository: SafetyRepository,
    private val locationProvider: FusedSafetyLocationProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()
    private val layer = savedStateHandle.getStateFlow("safetyLayer", SafetyMapLayer.ALL.name)
    private val range = savedStateHandle.getStateFlow("safetyRange", SafetyTimeRange.ALL.name)
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val search = savedStateHandle.getStateFlow("safetySearch", "")
    private val mapSources = combine(publicRepository.observePoints(), safetyRepository.observeMyPrivateMapPoints(), search) { public, private, query -> Triple(public, private, query) }
    val uiState = combine(mapSources, layer, range, loading, error) { sources, layerName, rangeName, busy, failure ->
        val (all, allPrivate, query) = sources
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
                points.map { SafetyPublicPoint(it.publicPointId, it.displayLatitude, it.displayLongitude, it.label, it.claimState, it.independentSourceCount, it.category) },
                privatePoints,
            ),
            points = points, privatePoints = privatePoints, isLoading = busy, error = failure,
            searchQuery = query,
            pointCount = points.size + privatePoints.size,
            layer = selectedLayer, range = selectedRange,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SafetyMapUiState())

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
        try { safetyRepository.reconcileMyReports(); publicRepository.refreshPoints(); error.value = null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error.value = "No se pudo actualizar. Se muestra la última información pública guardada." }
        finally { loading.value = false }
    }
}
