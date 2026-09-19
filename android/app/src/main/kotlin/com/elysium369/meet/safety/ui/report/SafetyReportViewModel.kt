package com.elysium369.meet.safety.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyRepository
import com.elysium369.meet.safety.domain.CreateSafetyReportPayload
import com.elysium369.meet.safety.domain.LocationSource
import com.elysium369.meet.safety.domain.SafetyReportCategory
import com.elysium369.meet.safety.domain.SourceRelation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SafetyDraftLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val source: LocationSource = LocationSource.DEVICE,
)

data class SafetyReportUiState(
    val step: Int = 0,
    val totalSteps: Int = 5,
    val category: SafetyReportCategory? = null,
    val sourceRelation: SourceRelation? = null,
    val narrative: String = "",
    val occurredAtIso: String? = null,
    val location: SafetyDraftLocation? = null,
    val submitting: Boolean = false,
    val createdReportId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SafetyReportViewModel @Inject constructor(
    private val repository: SafetyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SafetyReportUiState())
    val state: StateFlow<SafetyReportUiState> = _state.asStateFlow()

    fun selectCategory(category: SafetyReportCategory) {
        _state.update { it.copy(category = category, error = null) }
    }

    fun selectSourceRelation(relation: SourceRelation) {
        _state.update { it.copy(sourceRelation = relation, error = null) }
    }

    fun updateNarrative(text: String) {
        _state.update { it.copy(narrative = text, error = null) }
    }

    fun updateOccurredAt(iso: String?) {
        _state.update { it.copy(occurredAtIso = iso, error = null) }
    }

    fun updateLocation(location: SafetyDraftLocation?) {
        _state.update { it.copy(location = location, error = null) }
    }

    fun nextStep() {
        val s = _state.value
        when {
            s.step == 0 && s.category == null -> return
            s.step == 1 && s.sourceRelation == null -> return
            s.step == 2 && s.narrative.trim().length < 10 -> return
            s.step < s.totalSteps -> _state.update { it.copy(step = s.step + 1) }
        }
    }

    fun previousStep() {
        val s = _state.value
        if (s.step > 0) _state.update { it.copy(step = s.step - 1) }
    }

    fun goToStep(step: Int) {
        if (step in 0.._state.value.totalSteps) {
            _state.update { it.copy(step = step) }
        }
    }

    fun submit() {
        val snapshot = _state.value
        if (snapshot.submitting) return
        if (snapshot.category == null || snapshot.sourceRelation == null) return
        if (snapshot.narrative.trim().length < 10) return

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }

            try {
                val reportId = repository.createReport(
                    payload = CreateSafetyReportPayload(
                        category = snapshot.category,
                        narrative = snapshot.narrative.trim(),
                        sourceRelation = snapshot.sourceRelation,
                        occurredAtIso = snapshot.occurredAtIso,
                        latitude = snapshot.location?.latitude,
                        longitude = snapshot.location?.longitude,
                        accuracyMeters = snapshot.location?.accuracyMeters,
                        locationSource = snapshot.location?.source ?: LocationSource.NONE,
                    ),
                )

                _state.update {
                    it.copy(
                        submitting = false,
                        createdReportId = reportId,
                        step = it.totalSteps,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        submitting = false,
                        error = error.message ?: "No se pudo guardar el reporte",
                    )
                }
            }
        }
    }
}
