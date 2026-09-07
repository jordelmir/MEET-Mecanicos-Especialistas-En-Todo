package com.elysium369.meet.education.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.education.data.CourseUnitData
import com.elysium369.meet.education.data.CurriculumTrack
import com.elysium369.meet.education.data.ElysiumLearningRepository
import com.elysium369.meet.education.data.InteractiveTaskData
import com.elysium369.meet.education.data.TaskType
import com.elysium369.meet.education.domain.FrontierConcept
import com.elysium369.meet.education.domain.LearnerPrivacyLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ElysiumLearningUiState(
    val track: CurriculumTrack = CurriculumTrack.MATEMATICA_1,
    val activeGrade: Int = 1,
    val activeSubject: String = "Matemática 1.º Año (MEP 2026)",
    val learnerId: String = "",
    val isMinor: Boolean = true,
    val privacyLevel: LearnerPrivacyLevel = LearnerPrivacyLevel.PROTECTED_STUDENT,
    val units: List<CourseUnitData> = emptyList(),
    val selectedUnitId: String? = null,
    val frontierConcepts: List<FrontierConcept> = emptyList(),
    val selectedConceptId: String? = null,
    val activeTask: InteractiveTaskData? = null,
    val selectedOptionIndex: Int? = null,
    val accumulatedColones: Int = 0,
    val lastEvidenceHash: String? = null,
    val feedbackMessage: String? = null,
    val feedbackSuccess: Boolean? = null,
    val misconceptionDetected: String? = null,
    val currentMasteryEstimate: Double = 0.0,
    val currentConfidence: Double = 0.20,
    val isTransferUnlocked: Boolean = false,
    val isEvidenceModalVisible: Boolean = false,
    val isLoading: Boolean = false,
)

@HiltViewModel
class ElysiumLearningViewModel(
    private val repository: ElysiumLearningRepository,
    private val externalScope: kotlinx.coroutines.CoroutineScope?,
) : ViewModel() {

    @Inject
    constructor(repository: ElysiumLearningRepository) : this(repository, null)

    private val scope: kotlinx.coroutines.CoroutineScope
        get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(ElysiumLearningUiState())
    val uiState: StateFlow<ElysiumLearningUiState> = _uiState.asStateFlow()

    private var taskStartTimeMs: Long = System.currentTimeMillis()

    init {
        loadTrack(CurriculumTrack.MATEMATICA_1)
    }

    fun selectTrack(track: CurriculumTrack) {
        if (_uiState.value.track == track) return
        loadTrack(track)
    }

    private fun loadTrack(track: CurriculumTrack) {
        val units = repository.getCurriculumUnits(track)
        val initialUnit = units.firstOrNull { it.concepts.isNotEmpty() } ?: units.firstOrNull()
        val initialConcept = initialUnit?.concepts?.firstOrNull()
        val initialTask = initialConcept?.tasks?.firstOrNull()

        val grade = if (track == CurriculumTrack.FONTANERIA_7) 7 else 1
        val subject = if (track == CurriculumTrack.FONTANERIA_7) {
            "7.º Artes Industriales Fontanería"
        } else {
            "Matemática 1.º Año (MEP 2026)"
        }

        _uiState.update { current ->
            current.copy(
                track = track,
                activeGrade = grade,
                activeSubject = subject,
                units = units,
                selectedUnitId = initialUnit?.id,
                selectedConceptId = initialConcept?.id,
                activeTask = initialTask,
                selectedOptionIndex = null,
                accumulatedColones = 0,
                feedbackMessage = null,
                misconceptionDetected = null,
                isEvidenceModalVisible = false,
            )
        }

        taskStartTimeMs = System.currentTimeMillis()
        refreshFrontier()
    }

    fun selectUnit(unitId: String) {
        val unit = _uiState.value.units.firstOrNull { it.id == unitId } ?: return
        val concept = unit.concepts.firstOrNull()
        val task = concept?.tasks?.firstOrNull()

        _uiState.update {
            it.copy(
                selectedUnitId = unit.id,
                selectedConceptId = concept?.id,
                activeTask = task,
                selectedOptionIndex = null,
                accumulatedColones = 0,
                feedbackMessage = null,
                misconceptionDetected = null,
            )
        }
        taskStartTimeMs = System.currentTimeMillis()
        updateConceptMasteryPreview(concept?.id)
    }

    fun selectConcept(conceptId: String) {
        val concept = repository.getConceptById(conceptId) ?: return
        val task = concept.tasks.firstOrNull()

        _uiState.update {
            it.copy(
                selectedConceptId = concept.id,
                activeTask = task,
                selectedOptionIndex = null,
                accumulatedColones = 0,
                feedbackMessage = null,
                misconceptionDetected = null,
            )
        }
        taskStartTimeMs = System.currentTimeMillis()
        updateConceptMasteryPreview(concept.id)
    }

    fun selectTask(taskId: String) {
        val task = repository.getTaskById(taskId) ?: return
        _uiState.update {
            it.copy(
                activeTask = task,
                selectedOptionIndex = null,
                accumulatedColones = 0,
                feedbackMessage = null,
                misconceptionDetected = null,
            )
        }
        taskStartTimeMs = System.currentTimeMillis()
    }

    fun selectOption(index: Int) {
        _uiState.update { it.copy(selectedOptionIndex = index) }
    }

    fun addColones(amount: Int) {
        _uiState.update { it.copy(accumulatedColones = it.accumulatedColones + amount) }
    }

    fun resetColones() {
        _uiState.update { it.copy(accumulatedColones = 0) }
    }

    fun submitAnswer() {
        scope.launch {
            submitAnswerSync()
        }
    }

    suspend fun submitAnswerSync(): Result<com.elysium369.meet.education.domain.LearningEvidenceRecord>? {
        val currentState = _uiState.value
        val task = currentState.activeTask ?: return null

        val isCorrect = when (task.type) {
            TaskType.CURRENCY_CALCULATOR -> currentState.accumulatedColones == task.targetAmountCrc
            TaskType.SPATIAL_PLACEMENT,
            TaskType.MULTIPLE_CHOICE,
            TaskType.TECHNICAL_SEQUENCE -> currentState.selectedOptionIndex == task.correctOptionIndex
        }

        val misconceptionCode = if (!isCorrect) {
            when (task.type) {
                TaskType.CURRENCY_CALCULATOR -> "MISCONCEPTION_CURRENCY_AMOUNT_MISMATCH"
                else -> currentState.selectedOptionIndex?.let { task.misconceptionCodes[it] }
                    ?: "MISCONCEPTION_INCORRECT_ALTERNATIVE"
            }
        } else null

        val latencyMs = (System.currentTimeMillis() - taskStartTimeMs).toInt().coerceAtLeast(400)

        _uiState.update { it.copy(isLoading = true) }

        val recordResult = repository.recordEvidence(
            conceptId = task.conceptId,
            taskId = task.id,
            isCorrect = isCorrect,
            isTransferTask = task.isTransferTask,
            misconceptionCode = misconceptionCode,
            responseLatencyMs = latencyMs,
        )

        recordResult.onSuccess { record ->
            val conceptState = repository.getLocalConceptState(task.conceptId)
            val successText = if (isCorrect) {
                if (task.isTransferTask) {
                    "¡Extraordinario! Has demostrado transferencia contextual. Tu dominio se eleva a ${(conceptState.masteryEstimate * 100).toInt()}%."
                } else {
                    "¡Respuesta Correcta! Evidencia criptográfica firmada. ${task.explanation}"
                }
            } else {
                "Respuesta incorrecta detectada. Diagnóstico pedagógico: ${misconceptionCode ?: "Revisa el concepto"}. ${task.explanation}"
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    lastEvidenceHash = record.rawEvidenceHash,
                    feedbackMessage = successText,
                    feedbackSuccess = isCorrect,
                    misconceptionDetected = misconceptionCode,
                    currentMasteryEstimate = conceptState.masteryEstimate,
                    currentConfidence = conceptState.confidence,
                    isTransferUnlocked = conceptState.isMastered,
                    isEvidenceModalVisible = true,
                )
            }

            // Refresh personal frontier
            val subject = if (currentState.track == CurriculumTrack.FONTANERIA_7) "ARTES_INDUSTRIALES" else "MATEMATICA"
            refreshFrontierSync(currentState.activeGrade, subject)
        }.onFailure { err ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    feedbackMessage = "Error registrando evidencia: ${err.message}",
                    feedbackSuccess = false,
                    isEvidenceModalVisible = true,
                )
            }
        }

        return recordResult
    }

    fun dismissFeedback() {
        _uiState.update { it.copy(isEvidenceModalVisible = false) }
    }

    fun refreshFrontier() {
        val current = _uiState.value
        val subject = if (current.track == CurriculumTrack.FONTANERIA_7) "ARTES_INDUSTRIALES" else "MATEMATICA"
        scope.launch {
            refreshFrontierSync(current.activeGrade, subject)
        }
    }

    suspend fun refreshFrontierSync(grade: Int, subject: String) {
        repository.getPersonalFrontier(subject = subject, grade = grade)
            .onSuccess { frontier ->
                _uiState.update { it.copy(frontierConcepts = frontier.frontierConcepts) }
            }
    }

    private fun updateConceptMasteryPreview(conceptId: String?) {
        if (conceptId == null) return
        val state = repository.getLocalConceptState(conceptId)
        _uiState.update {
            it.copy(
                currentMasteryEstimate = state.masteryEstimate,
                currentConfidence = state.confidence,
                isTransferUnlocked = state.isMastered,
            )
        }
    }
}
