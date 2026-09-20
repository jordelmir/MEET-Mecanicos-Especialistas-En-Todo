package com.elysium369.meet.safety.ui.cases

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyPublicRepository
import com.elysium369.meet.safety.data.SafetyRepository
import com.elysium369.meet.safety.domain.SafetyChallengeKind
import com.elysium369.meet.safety.data.local.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SafetyCaseDetailUiState(
    val case: SafetyPublicCaseEntity? = null,
    val timeline: List<SafetyPublicTimelineEntity> = emptyList(),
    val claims: List<SafetyPublicClaimEntity> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val challengeQueued: Boolean = false,
    val challengeSubmitting: Boolean = false,
)

@HiltViewModel
class SafetyCaseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SafetyPublicRepository,
    private val safetyRepository: SafetyRepository,
) : ViewModel() {
    private val caseId: String = checkNotNull(savedStateHandle["caseId"])
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val challengeQueued = MutableStateFlow(false)
    private val challengeSubmitting = MutableStateFlow(false)
    val state = combine(repository.observeCase(caseId), repository.observeTimeline(caseId), repository.observeClaims(caseId), loading, error, challengeQueued, challengeSubmitting) { values ->
        val case = values[0] as SafetyPublicCaseEntity?
        @Suppress("UNCHECKED_CAST") val timeline = values[1] as List<SafetyPublicTimelineEntity>
        @Suppress("UNCHECKED_CAST") val claims = values[2] as List<SafetyPublicClaimEntity>
        val busy = values[3] as Boolean
        val failure = values[4] as String?
        val authoritativeCase = case?.takeIf { it.serverVersion > 0 }
        SafetyCaseDetailUiState(authoritativeCase,
            timeline.filter { authoritativeCase != null && it.serverVersion > 0 },
            claims.filter { authoritativeCase != null && it.serverVersion > 0 }, busy, failure,
            values[5] as Boolean, values[6] as Boolean)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SafetyCaseDetailUiState())
    init { viewModelScope.launch { repository.realtimeWakeUps().collect { refreshNow() } } }
    fun refresh() { viewModelScope.launch { refreshNow() } }
    fun submitChallenge(kind: SafetyChallengeKind, claimId: String?, narrative: String, sourceUrl: String?) {
        if (challengeSubmitting.value) return
        viewModelScope.launch {
            challengeSubmitting.value = true
            challengeQueued.value = false
            try {
                safetyRepository.submitCounterclaim(caseId, claimId, kind, narrative, sourceUrl)
                challengeQueued.value = true
                error.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error.value = failure.message ?: "No se pudo guardar la solicitud."
            } finally {
                challengeSubmitting.value = false
            }
        }
    }
    private suspend fun refreshNow() {
        loading.value = true
        try { repository.refreshCaseDetail(caseId); error.value = null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error.value = "No se pudo actualizar. Se muestra la última información pública guardada." }
        finally { loading.value = false }
    }
}
