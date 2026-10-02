package com.elysium369.meet.safety.ui.timelines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyPublicRepository
import com.elysium369.meet.safety.data.local.SafetyPublicCaseEntity
import com.elysium369.meet.safety.data.local.SafetyPublicTimelineEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineEntry(
    val caseId: String,
    val caseTitle: String,
    val caseType: String,
    val milestoneId: String,
    val eventType: String,
    val publicSummary: String,
    val occurredAt: Long?,
    val recordedAt: Long?,
    val sourceCount: Int,
    val evidenceCount: Int,
)

data class SafetyTimelinesUiState(
    val entries: List<TimelineEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class SafetyTimelinesViewModel @Inject constructor(
    private val publicRepository: SafetyPublicRepository,
) : ViewModel() {

    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)

    // Build a chronological timeline by combining cases with their timeline milestones
    val uiState: StateFlow<SafetyTimelinesUiState> =
        combine(publicRepository.observeCases(), loading, error) { cases, busy, failure ->
            val entries = mutableListOf<TimelineEntry>()
            for (case in cases.filter { it.serverVersion > 0 }) {
                // Each case becomes a timeline entry with its own data
                entries.add(
                    TimelineEntry(
                        caseId = case.caseId,
                        caseTitle = case.title,
                        caseType = case.caseType,
                        milestoneId = case.caseId,
                        eventType = "REPORT_FILED",
                        publicSummary = case.publicSummary.ifBlank { case.title },
                        occurredAt = case.publishedAt,
                        recordedAt = case.lastUpdatedAt,
                        sourceCount = case.sourceCount ?: 1,
                        evidenceCount = case.evidenceCount ?: 1,
                    )
                )
            }
            // Sort chronologically descending (newest first)
            entries.sortByDescending { it.occurredAt ?: it.recordedAt ?: 0L }
            SafetyTimelinesUiState(
                entries = entries,
                isLoading = busy,
                error = failure,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SafetyTimelinesUiState())

    init {
        viewModelScope.launch { publicRepository.realtimeWakeUps().collect { refreshNow() } }
        refreshNow()
    }

    fun refresh() = refreshNow()

    private fun refreshNow() {
        viewModelScope.launch {
            loading.value = true
            try {
                publicRepository.refreshCases()
                error.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error.value = "No se pudo actualizar la línea de tiempo."
            } finally {
                loading.value = false
            }
        }
    }
}
