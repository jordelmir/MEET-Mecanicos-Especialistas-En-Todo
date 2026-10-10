package com.elysium369.meet.safety.ui.observatory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyInsightsRepository
import com.elysium369.meet.safety.data.SafetyObservatoryFilters
import com.elysium369.meet.safety.data.SafetyObservatoryMetrics
import com.elysium369.meet.safety.data.SafetyPublicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SafetyObservatoryUiState(
    val stats: SafetyObservatoryMetrics? = null,
    val filters: SafetyObservatoryFilters = SafetyObservatoryFilters(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val illicitPatterns: List<com.elysium369.meet.safety.data.IllicitMarketPattern> = emptyList(),
    val patternsUnavailable: Boolean = false,
    val recentPoints: List<com.elysium369.meet.safety.data.local.SafetyPublicPointEntity> = emptyList(),
    val recentCases: List<com.elysium369.meet.safety.data.local.SafetyPublicCaseEntity> = emptyList(),
    val selectedSourceFilter: String? = null, // null = all, "JOURNALISTIC", "CIVIL", "PUBLIC_RECORD", "INSTITUTIONAL", "DOCUMENTARY"
)

@HiltViewModel
class SafetyObservatoryViewModel @Inject constructor(
    private val repository: SafetyInsightsRepository,
    private val safetyDao: com.elysium369.meet.safety.data.local.SafetyPublicDao,
    private val publicRepository: SafetyPublicRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(SafetyObservatoryUiState())
    val uiState = mutable.asStateFlow()
    private var request: Job? = null

    init {
        refresh()
    }

    fun setFilters(filters: SafetyObservatoryFilters) {
        mutable.update { it.copy(filters = filters) }
        refresh()
    }

    fun setSourceFilter(source: String?) {
        mutable.update { it.copy(selectedSourceFilter = source) }
    }

    fun refresh() {
        request?.cancel()
        request = viewModelScope.launch {
            val filters = mutable.value.filters

            // Trigger sync of public cases and points from remote in background
            launch { runCatching { publicRepository.refreshCases() } }
            launch { runCatching { publicRepository.refreshPoints() } }

            var points = runCatching { safetyDao.getPoints().take(24) }.getOrDefault(emptyList())
            var cases = runCatching { safetyDao.getCases().take(24) }.getOrDefault(emptyList())

            mutable.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    recentPoints = points,
                    recentCases = cases
                )
            }

            try {
                val queriedStats = repository.observatoryV2(filters)
                val stats = queriedStats.takeIf {
                    it.data_state != com.elysium369.meet.safety.data.SafetyObservatoryDataState.UNAVAILABLE
                }
                // Reload only previously published public records; these do not replace aggregates.
                points = runCatching { safetyDao.getPoints().take(24) }.getOrDefault(points)
                cases = runCatching { safetyDao.getCases().take(24) }.getOrDefault(cases)

                mutable.update {
                    it.copy(
                        stats = stats,
                        isLoading = false,
                        recentPoints = points,
                        recentCases = cases,
                        error = if (stats == null) {
                            "La agregación remota del Observatorio no está disponible. No se presentan conteos como cero; los registros públicos locales pueden seguir visibles."
                        } else null,
                    )
                }

                if (stats != null && (filters.category.isBlank() || filters.category == "DRUG_SALE_ACTIVITY")) {
                    try {
                        val patterns = repository.counternarcotics(filters)
                        mutable.update { it.copy(illicitPatterns = patterns.patterns) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutable.update { it.copy(patternsUnavailable = true) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Do not turn a disabled gate, query error, or missing aggregate into zeros.
                // Already-published public records can still be listed independently.
                val localPoints = runCatching { safetyDao.getPoints() }.getOrDefault(emptyList())
                val localCases = runCatching { safetyDao.getCases() }.getOrDefault(emptyList())
                val message = if (error.message?.contains("SAFETY_OBSERVATORY_DISABLED") == true) {
                    "El Observatorio no está habilitado por la configuración del servidor."
                } else {
                    "No se pudo consultar la proyección agregada del Observatorio. No se mostrarán métricas como cero; vuelve a intentarlo."
                }
                mutable.update {
                    it.copy(
                        stats = null,
                        isLoading = false,
                        recentPoints = localPoints.take(24),
                        recentCases = localCases.take(24),
                        error = message,
                    )
                }
            }
        }
    }
}

