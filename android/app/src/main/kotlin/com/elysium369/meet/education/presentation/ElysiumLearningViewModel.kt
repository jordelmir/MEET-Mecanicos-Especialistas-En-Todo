package com.elysium369.meet.education.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.education.ai.SocraticDialogueMessage
import com.elysium369.meet.education.ai.SocraticMode
import com.elysium369.meet.education.ai.SocraticTutorEngine
import com.elysium369.meet.education.data.ConceptDeepKnowledge
import com.elysium369.meet.education.data.CourseUnitData
import com.elysium369.meet.education.data.CurriculumTrack
import com.elysium369.meet.education.data.ElysiumLearningRepository
import com.elysium369.meet.education.data.InteractiveTaskData
import com.elysium369.meet.education.data.NationalCurriculumDeepKnowledge
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
    val selectedCycle: String = "Todos",
    val activeGrade: Int = 1,
    val activeSubject: String = "1.º Matemática (I Ciclo)",
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
    val isSocraticSheetVisible: Boolean = false,
    val socraticDialogue: List<SocraticDialogueMessage> = emptyList(),
    val isSocraticLoading: Boolean = false,
    val socraticHintTierCount: Int = 1,
    val currentDeepKnowledge: ConceptDeepKnowledge? = null,
    val activeDiploma: com.elysium369.meet.education.domain.CertifiedCompetencyDiploma? = null,
    val isDiplomaDialogVisible: Boolean = false,
    val isGeometrySandboxVisible: Boolean = false,
    val isElectricalSandboxVisible: Boolean = false,
    val linkedDtcBridge: com.elysium369.meet.education.domain.DtcEducationalBridge? = null,
    val pisaSelectedDomain: com.elysium369.meet.education.domain.PisaDomain = com.elysium369.meet.education.domain.PisaDomain.MATHEMATICAL_LITERACY,
    val pisaReport: com.elysium369.meet.education.engine.PisaEvaluationReport? = null,
    val pisaProgressionSpec: com.elysium369.meet.education.engine.SubjectPisaProgressionSpec? = null,
    val isPisaExpanded: Boolean = true,
    val isSystemExplainerVisible: Boolean = false,
    val isCourseIndexVisible: Boolean = false,
    val currentTaskIndex: Int = 0,
    val totalTasksInCurrentConcept: Int = 1,
    val isLessonExplanationVisible: Boolean = true,
)

@HiltViewModel
class ElysiumLearningViewModel(
    private val repository: ElysiumLearningRepository,
    private val socraticTutorEngine: SocraticTutorEngine,
    private val externalScope: kotlinx.coroutines.CoroutineScope?,
) : ViewModel() {

    @Inject
    constructor(
        repository: ElysiumLearningRepository,
        socraticTutorEngine: SocraticTutorEngine,
    ) : this(repository, socraticTutorEngine, null)

    constructor(
        repository: ElysiumLearningRepository,
        externalScope: kotlinx.coroutines.CoroutineScope?,
    ) : this(repository, SocraticTutorEngine(), externalScope)

    constructor(repository: ElysiumLearningRepository) : this(repository, SocraticTutorEngine(), null)

    private val scope: kotlinx.coroutines.CoroutineScope
        get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(ElysiumLearningUiState())
    val uiState: StateFlow<ElysiumLearningUiState> = _uiState.asStateFlow()

    private var taskStartTimeMs: Long = System.currentTimeMillis()

    init {
        val lastTrack = repository.getLastActiveTrack() ?: CurriculumTrack.MATEMATICA_1
        loadTrack(lastTrack, restoreSaved = true)
    }

    private fun persistCurrentProgress() {
        val state = _uiState.value
        val completed = if (state.feedbackSuccess == true) {
            state.activeTask?.id?.let { setOf(it) } ?: emptySet()
        } else emptySet()
        repository.saveTrackProgress(
            track = state.track,
            selectedUnitId = state.selectedUnitId,
            selectedConceptId = state.selectedConceptId,
            currentTaskIndex = state.currentTaskIndex,
            completedTaskIds = completed,
            accumulatedColones = state.accumulatedColones,
        )
    }

    fun selectCycle(cycle: String) {
        _uiState.update { it.copy(selectedCycle = cycle) }
    }

    fun selectGrade(grade: Int) {
        val cycle = when (grade) {
            in 1..3 -> "I Ciclo"
            in 4..6 -> "II Ciclo"
            in 7..9 -> "III Ciclo"
            else -> "Diversificada"
        }
        val targetTrack = CurriculumTrack.values().firstOrNull { it.gradeNumber == grade }
            ?: CurriculumTrack.MATEMATICA_1
        _uiState.update { it.copy(selectedCycle = cycle, activeGrade = grade) }
        loadTrack(targetTrack, restoreSaved = true)
    }

    fun selectTrack(track: CurriculumTrack) {
        if (_uiState.value.track == track) return
        loadTrack(track, restoreSaved = true)
    }

    private fun loadTrack(track: CurriculumTrack, restoreSaved: Boolean = false) {
        val units = repository.getCurriculumUnits(track)
        val saved = if (restoreSaved) repository.getTrackProgress(track) else null

        val initialUnit = if (saved?.selectedUnitId != null) {
            units.firstOrNull { it.id == saved.selectedUnitId }
                ?: units.firstOrNull { it.concepts.isNotEmpty() }
                ?: units.firstOrNull()
        } else {
            units.firstOrNull { it.concepts.isNotEmpty() } ?: units.firstOrNull()
        }

        val initialConcept = if (saved?.selectedConceptId != null) {
            initialUnit?.concepts?.firstOrNull { it.id == saved.selectedConceptId }
                ?: initialUnit?.concepts?.firstOrNull()
        } else {
            initialUnit?.concepts?.firstOrNull()
        }

        val initialTaskIndex = if (saved != null && initialConcept != null && initialConcept.tasks.isNotEmpty()) {
            saved.currentTaskIndex.coerceIn(0, initialConcept.tasks.size - 1)
        } else {
            0
        }

        val initialTask = initialConcept?.tasks?.getOrNull(initialTaskIndex) ?: initialConcept?.tasks?.firstOrNull()

        val grade = track.gradeNumber
        val subject = "${track.displayName} (${track.cycleName})"
        val deepKnowledge = initialConcept?.let { NationalCurriculumDeepKnowledge.getKnowledgeForConcept(it) }

        _uiState.update { current ->
            current.copy(
                track = track,
                activeGrade = grade,
                activeSubject = subject,
                units = units,
                selectedUnitId = initialUnit?.id,
                selectedConceptId = initialConcept?.id,
                activeTask = initialTask,
                currentTaskIndex = initialTaskIndex,
                totalTasksInCurrentConcept = initialConcept?.tasks?.size ?: 0,
                selectedOptionIndex = null,
                accumulatedColones = saved?.accumulatedColones ?: 0,
                feedbackMessage = null,
                misconceptionDetected = null,
                isEvidenceModalVisible = false,
                isSocraticSheetVisible = false,
                socraticDialogue = emptyList(),
                socraticHintTierCount = 1,
                currentDeepKnowledge = deepKnowledge,
            )
        }

        taskStartTimeMs = System.currentTimeMillis()
        refreshFrontier()
        val pisaDomain = when {
            track.subjectName.contains("CIENCIA", ignoreCase = true) || track.subjectName.contains("FISICA", ignoreCase = true) || track.subjectName.contains("QUIMICA", ignoreCase = true) ->
                com.elysium369.meet.education.domain.PisaDomain.SCIENTIFIC_LITERACY
            track.subjectName.contains("ESPANO", ignoreCase = true) || track.subjectName.contains("INGLES", ignoreCase = true) ->
                com.elysium369.meet.education.domain.PisaDomain.READING_LITERACY
            else ->
                com.elysium369.meet.education.domain.PisaDomain.MATHEMATICAL_LITERACY
        }
        selectPisaDomain(pisaDomain)
        initialConcept?.id?.let { updateConceptMasteryPreview(it) }
        persistCurrentProgress()
    }

    fun selectUnit(unitId: String) {
        val unit = _uiState.value.units.firstOrNull { it.id == unitId } ?: return
        val concept = unit.concepts.firstOrNull()
        val task = concept?.tasks?.firstOrNull()
        val deepKnowledge = concept?.let { NationalCurriculumDeepKnowledge.getKnowledgeForConcept(it) }

        _uiState.update {
            it.copy(
                selectedUnitId = unit.id,
                selectedConceptId = concept?.id,
                activeTask = task,
                currentTaskIndex = 0,
                totalTasksInCurrentConcept = concept?.tasks?.size ?: 0,
                selectedOptionIndex = null,
                accumulatedColones = 0,
                feedbackMessage = null,
                misconceptionDetected = null,
                socraticDialogue = emptyList(),
                socraticHintTierCount = 1,
                currentDeepKnowledge = deepKnowledge,
            )
        }
        taskStartTimeMs = System.currentTimeMillis()
        updateConceptMasteryPreview(concept?.id)
        persistCurrentProgress()
    }

    fun selectConcept(conceptId: String) {
        val currentState = _uiState.value
        val parentUnit = currentState.units.firstOrNull { u -> u.concepts.any { it.id == conceptId } }
        val concept = repository.getConceptById(conceptId) ?: return
        val task = concept.tasks.firstOrNull()
        val deepKnowledge = NationalCurriculumDeepKnowledge.getKnowledgeForConcept(concept)

        _uiState.update {
            it.copy(
                selectedUnitId = parentUnit?.id ?: it.selectedUnitId,
                selectedConceptId = concept.id,
                activeTask = task,
                currentTaskIndex = 0,
                totalTasksInCurrentConcept = concept.tasks.size,
                selectedOptionIndex = null,
                accumulatedColones = 0,
                feedbackMessage = null,
                misconceptionDetected = null,
                socraticDialogue = emptyList(),
                socraticHintTierCount = 1,
                currentDeepKnowledge = deepKnowledge,
                isCourseIndexVisible = false,
            )
        }
        taskStartTimeMs = System.currentTimeMillis()
        updateConceptMasteryPreview(concept.id)
        persistCurrentProgress()
    }

    fun selectSection(unitId: String, conceptId: String) {
        val unit = _uiState.value.units.firstOrNull { it.id == unitId } ?: return
        val concept = unit.concepts.firstOrNull { it.id == conceptId } ?: return
        val task = concept.tasks.firstOrNull()
        val deepKnowledge = NationalCurriculumDeepKnowledge.getKnowledgeForConcept(concept)

        _uiState.update {
            it.copy(
                selectedUnitId = unit.id,
                selectedConceptId = concept.id,
                activeTask = task,
                currentTaskIndex = 0,
                totalTasksInCurrentConcept = concept.tasks.size,
                selectedOptionIndex = null,
                accumulatedColones = 0,
                feedbackMessage = null,
                misconceptionDetected = null,
                socraticDialogue = emptyList(),
                socraticHintTierCount = 1,
                currentDeepKnowledge = deepKnowledge,
                isCourseIndexVisible = false,
            )
        }
        taskStartTimeMs = System.currentTimeMillis()
        updateConceptMasteryPreview(concept.id)
        persistCurrentProgress()
    }

    fun advanceToNextConcept() {
        val currentState = _uiState.value
        val currentUnitId = currentState.selectedUnitId ?: return
        val currentUnit = currentState.units.firstOrNull { it.id == currentUnitId } ?: return
        val currentConceptId = currentState.selectedConceptId ?: return
        val conceptIdx = currentUnit.concepts.indexOfFirst { it.id == currentConceptId }

        if (conceptIdx != -1 && conceptIdx + 1 < currentUnit.concepts.size) {
            selectConcept(currentUnit.concepts[conceptIdx + 1].id)
        } else {
            val currentUnitIdx = currentState.units.indexOfFirst { it.id == currentUnit.id }
            val nextUnit = currentState.units.drop(currentUnitIdx + 1).firstOrNull { it.concepts.isNotEmpty() }
            if (nextUnit != null) {
                selectUnit(nextUnit.id)
            }
        }
    }

    fun previousConcept() {
        val currentState = _uiState.value
        val currentUnitId = currentState.selectedUnitId ?: return
        val currentUnit = currentState.units.firstOrNull { it.id == currentUnitId } ?: return
        val currentConceptId = currentState.selectedConceptId ?: return
        val conceptIdx = currentUnit.concepts.indexOfFirst { it.id == currentConceptId }

        if (conceptIdx > 0) {
            selectConcept(currentUnit.concepts[conceptIdx - 1].id)
        } else {
            val currentUnitIdx = currentState.units.indexOfFirst { it.id == currentUnit.id }
            val prevUnit = currentState.units.take(currentUnitIdx).lastOrNull { it.concepts.isNotEmpty() }
            if (prevUnit != null && prevUnit.concepts.isNotEmpty()) {
                val lastConcept = prevUnit.concepts.last()
                selectSection(prevUnit.id, lastConcept.id)
            }
        }
    }

    fun advanceToNextUnit() {
        val currentState = _uiState.value
        val currentUnitId = currentState.selectedUnitId ?: return
        val currentUnitIdx = currentState.units.indexOfFirst { it.id == currentUnitId }
        val nextUnit = currentState.units.drop(currentUnitIdx + 1).firstOrNull { it.concepts.isNotEmpty() }
        if (nextUnit != null) {
            selectUnit(nextUnit.id)
        }
    }

    fun previousUnit() {
        val currentState = _uiState.value
        val currentUnitId = currentState.selectedUnitId ?: return
        val currentUnitIdx = currentState.units.indexOfFirst { it.id == currentUnitId }
        val prevUnit = currentState.units.take(currentUnitIdx).lastOrNull { it.concepts.isNotEmpty() }
        if (prevUnit != null) {
            selectUnit(prevUnit.id)
        }
    }

    fun toggleCourseIndexDialog() {
        _uiState.update { it.copy(isCourseIndexVisible = !it.isCourseIndexVisible) }
    }

    fun selectTask(taskId: String) {
        val task = repository.getTaskById(taskId) ?: return
        val currentConcept = _uiState.value.selectedConceptId?.let { repository.getConceptById(it) }
        val taskIndex = currentConcept?.tasks?.indexOfFirst { it.id == taskId }?.takeIf { it != -1 } ?: 0
        _uiState.update {
            it.copy(
                activeTask = task,
                currentTaskIndex = taskIndex,
                totalTasksInCurrentConcept = currentConcept?.tasks?.size ?: 1,
                selectedOptionIndex = null,
                accumulatedColones = 0,
                feedbackMessage = null,
                misconceptionDetected = null,
                socraticDialogue = emptyList(),
                socraticHintTierCount = 1,
            )
        }
        taskStartTimeMs = System.currentTimeMillis()
        persistCurrentProgress()
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

    fun toggleLessonExplanation() {
        _uiState.update { it.copy(isLessonExplanationVisible = !it.isLessonExplanationVisible) }
    }

    fun retryCurrentTask() {
        _uiState.update {
            it.copy(
                selectedOptionIndex = null,
                accumulatedColones = 0,
                feedbackMessage = null,
                feedbackSuccess = null,
                misconceptionDetected = null,
                isEvidenceModalVisible = false,
            )
        }
        taskStartTimeMs = System.currentTimeMillis()
    }

    fun advanceToNextTaskOrConcept() {
        val currentState = _uiState.value
        val currentConceptId = currentState.selectedConceptId ?: return
        val currentConcept = repository.getConceptById(currentConceptId) ?: return
        val currentUnitId = currentState.selectedUnitId ?: return
        val currentUnit = currentState.units.firstOrNull { it.id == currentUnitId } ?: return

        val nextTaskIndex = currentState.currentTaskIndex + 1

        if (nextTaskIndex < currentConcept.tasks.size) {
            // Advance to next task in current concept
            val nextTask = currentConcept.tasks[nextTaskIndex]
            _uiState.update {
                it.copy(
                    activeTask = nextTask,
                    currentTaskIndex = nextTaskIndex,
                    totalTasksInCurrentConcept = currentConcept.tasks.size,
                    selectedOptionIndex = null,
                    accumulatedColones = 0,
                    feedbackMessage = null,
                    feedbackSuccess = null,
                    misconceptionDetected = null,
                    isEvidenceModalVisible = false,
                    socraticDialogue = emptyList(),
                    socraticHintTierCount = 1,
                )
            }
            taskStartTimeMs = System.currentTimeMillis()
        } else {
            // Current concept tasks completed! Advance to next concept in unit
            val currentConceptIdx = currentUnit.concepts.indexOfFirst { it.id == currentConcept.id }
            if (currentConceptIdx != -1 && currentConceptIdx + 1 < currentUnit.concepts.size) {
                val nextConcept = currentUnit.concepts[currentConceptIdx + 1]
                val nextTask = nextConcept.tasks.firstOrNull()
                val deepKnowledge = NationalCurriculumDeepKnowledge.getKnowledgeForConcept(nextConcept)
                _uiState.update {
                    it.copy(
                        selectedConceptId = nextConcept.id,
                        activeTask = nextTask,
                        currentTaskIndex = 0,
                        totalTasksInCurrentConcept = nextConcept.tasks.size,
                        selectedOptionIndex = null,
                        accumulatedColones = 0,
                        feedbackMessage = null,
                        feedbackSuccess = null,
                        misconceptionDetected = null,
                        isEvidenceModalVisible = false,
                        socraticDialogue = emptyList(),
                        socraticHintTierCount = 1,
                        currentDeepKnowledge = deepKnowledge,
                        isLessonExplanationVisible = true,
                    )
                }
                taskStartTimeMs = System.currentTimeMillis()
                updateConceptMasteryPreview(nextConcept.id)
            } else {
                // Current unit concepts completed! Advance to next unit in track
                val currentUnitIdx = currentState.units.indexOfFirst { it.id == currentUnit.id }
                val nextUnit = currentState.units.drop(currentUnitIdx + 1).firstOrNull { it.concepts.isNotEmpty() }

                if (nextUnit != null) {
                    val nextConcept = nextUnit.concepts.firstOrNull()
                    val nextTask = nextConcept?.tasks?.firstOrNull()
                    val deepKnowledge = nextConcept?.let { NationalCurriculumDeepKnowledge.getKnowledgeForConcept(it) }
                    _uiState.update {
                        it.copy(
                            selectedUnitId = nextUnit.id,
                            selectedConceptId = nextConcept?.id,
                            activeTask = nextTask,
                            currentTaskIndex = 0,
                            totalTasksInCurrentConcept = nextConcept?.tasks?.size ?: 0,
                            selectedOptionIndex = null,
                            accumulatedColones = 0,
                            feedbackMessage = null,
                            feedbackSuccess = null,
                            misconceptionDetected = null,
                            isEvidenceModalVisible = false,
                            socraticDialogue = emptyList(),
                            socraticHintTierCount = 1,
                            currentDeepKnowledge = deepKnowledge,
                            isLessonExplanationVisible = true,
                        )
                    }
                    taskStartTimeMs = System.currentTimeMillis()
                    updateConceptMasteryPreview(nextConcept?.id)
                } else {
                    // Entire subject track completed!
                    _uiState.update { it.copy(isEvidenceModalVisible = false) }
                    openDiplomaDialog()
                }
            }
        }
        persistCurrentProgress()
    }

    fun previousTaskOrConcept() {
        val currentState = _uiState.value
        val currentConceptId = currentState.selectedConceptId ?: return
        val currentConcept = repository.getConceptById(currentConceptId) ?: return
        val currentUnitId = currentState.selectedUnitId ?: return
        val currentUnit = currentState.units.firstOrNull { it.id == currentUnitId } ?: return

        if (currentState.currentTaskIndex > 0) {
            val prevTaskIndex = currentState.currentTaskIndex - 1
            val prevTask = currentConcept.tasks[prevTaskIndex]
            _uiState.update {
                it.copy(
                    activeTask = prevTask,
                    currentTaskIndex = prevTaskIndex,
                    totalTasksInCurrentConcept = currentConcept.tasks.size,
                    selectedOptionIndex = null,
                    accumulatedColones = 0,
                    feedbackMessage = null,
                    feedbackSuccess = null,
                    misconceptionDetected = null,
                    isEvidenceModalVisible = false,
                )
            }
            taskStartTimeMs = System.currentTimeMillis()
        } else {
            val currentConceptIdx = currentUnit.concepts.indexOfFirst { it.id == currentConcept.id }
            if (currentConceptIdx > 0) {
                val prevConcept = currentUnit.concepts[currentConceptIdx - 1]
                val prevTaskIndex = (prevConcept.tasks.size - 1).coerceAtLeast(0)
                val prevTask = prevConcept.tasks.getOrNull(prevTaskIndex)
                val deepKnowledge = NationalCurriculumDeepKnowledge.getKnowledgeForConcept(prevConcept)
                _uiState.update {
                    it.copy(
                        selectedConceptId = prevConcept.id,
                        activeTask = prevTask,
                        currentTaskIndex = prevTaskIndex,
                        totalTasksInCurrentConcept = prevConcept.tasks.size,
                        selectedOptionIndex = null,
                        accumulatedColones = 0,
                        feedbackMessage = null,
                        feedbackSuccess = null,
                        misconceptionDetected = null,
                        isEvidenceModalVisible = false,
                        currentDeepKnowledge = deepKnowledge,
                    )
                }
                taskStartTimeMs = System.currentTimeMillis()
                updateConceptMasteryPreview(prevConcept.id)
            } else {
                val currentUnitIdx = currentState.units.indexOfFirst { it.id == currentUnit.id }
                val prevUnit = currentState.units.take(currentUnitIdx).lastOrNull { it.concepts.isNotEmpty() }
                if (prevUnit != null) {
                    val prevConcept = prevUnit.concepts.lastOrNull()
                    val prevTaskIndex = ((prevConcept?.tasks?.size ?: 1) - 1).coerceAtLeast(0)
                    val prevTask = prevConcept?.tasks?.getOrNull(prevTaskIndex)
                    val deepKnowledge = prevConcept?.let { NationalCurriculumDeepKnowledge.getKnowledgeForConcept(it) }
                    _uiState.update {
                        it.copy(
                            selectedUnitId = prevUnit.id,
                            selectedConceptId = prevConcept?.id,
                            activeTask = prevTask,
                            currentTaskIndex = prevTaskIndex,
                            totalTasksInCurrentConcept = prevConcept?.tasks?.size ?: 0,
                            selectedOptionIndex = null,
                            accumulatedColones = 0,
                            feedbackMessage = null,
                            feedbackSuccess = null,
                            misconceptionDetected = null,
                            isEvidenceModalVisible = false,
                            currentDeepKnowledge = deepKnowledge,
                        )
                    }
                    taskStartTimeMs = System.currentTimeMillis()
                    updateConceptMasteryPreview(prevConcept?.id)
                }
            }
        }
        persistCurrentProgress()
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
            val subject = currentState.track.subjectName
            refreshFrontierSync(currentState.activeGrade, subject)
            persistCurrentProgress()
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
        val subject = current.track.subjectName
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

    // ── TUTOR SOCRÁTICO DE IA ───────────────────────────────────────────────
    fun openSocraticTutor() {
        _uiState.update { it.copy(isSocraticSheetVisible = true) }
    }

    fun dismissSocraticTutor() {
        _uiState.update { it.copy(isSocraticSheetVisible = false) }
    }

    fun requestSocraticHint() {
        scope.launch { requestSocraticHintSync() }
    }

    suspend fun requestSocraticHintSync() {
        val current = _uiState.value
        val task = current.activeTask ?: return
        val concept = current.selectedConceptId?.let { repository.getConceptById(it) } ?: return
        val tier = current.socraticHintTierCount

        _uiState.update {
            it.copy(
                isSocraticLoading = true,
                isSocraticSheetVisible = true,
                socraticDialogue = it.socraticDialogue + SocraticDialogueMessage(
                    id = "user_${System.currentTimeMillis()}",
                    isUser = true,
                    text = "💡 Dame una pista socrática (Nivel $tier)",
                    mode = SocraticMode.HINT,
                )
            )
        }

        val tutorReply = socraticTutorEngine.consultTutor(
            concept = concept,
            task = task,
            mode = SocraticMode.HINT,
            hintTier = tier
        )

        _uiState.update {
            it.copy(
                isSocraticLoading = false,
                socraticHintTierCount = (tier % 3) + 1,
                socraticDialogue = it.socraticDialogue + tutorReply
            )
        }
    }

    fun requestRealWorldAnalogy() {
        scope.launch { requestRealWorldAnalogySync() }
    }

    suspend fun requestRealWorldAnalogySync() {
        val current = _uiState.value
        val task = current.activeTask ?: return
        val concept = current.selectedConceptId?.let { repository.getConceptById(it) } ?: return

        _uiState.update {
            it.copy(
                isSocraticLoading = true,
                isSocraticSheetVisible = true,
                socraticDialogue = it.socraticDialogue + SocraticDialogueMessage(
                    id = "user_${System.currentTimeMillis()}",
                    isUser = true,
                    text = "🔧 Explícamelo con una analogía práctica / taller",
                    mode = SocraticMode.REAL_WORLD_ANALOGY,
                )
            )
        }

        val tutorReply = socraticTutorEngine.consultTutor(
            concept = concept,
            task = task,
            mode = SocraticMode.REAL_WORLD_ANALOGY
        )

        _uiState.update {
            it.copy(
                isSocraticLoading = false,
                socraticDialogue = it.socraticDialogue + tutorReply
            )
        }
    }

    fun requestMisconceptionHelp() {
        scope.launch { requestMisconceptionHelpSync() }
    }

    suspend fun requestMisconceptionHelpSync() {
        val current = _uiState.value
        val task = current.activeTask ?: return
        val concept = current.selectedConceptId?.let { repository.getConceptById(it) } ?: return
        val code = current.misconceptionDetected

        _uiState.update {
            it.copy(
                isSocraticLoading = true,
                isSocraticSheetVisible = true,
                socraticDialogue = it.socraticDialogue + SocraticDialogueMessage(
                    id = "user_${System.currentTimeMillis()}",
                    isUser = true,
                    text = "🔍 ¿Por qué me equivoqué? Analiza mi error",
                    mode = SocraticMode.MISCONCEPTION_HELP,
                )
            )
        }

        val tutorReply = socraticTutorEngine.consultTutor(
            concept = concept,
            task = task,
            mode = SocraticMode.MISCONCEPTION_HELP,
            misconceptionCode = code
        )

        _uiState.update {
            it.copy(
                isSocraticLoading = false,
                socraticDialogue = it.socraticDialogue + tutorReply
            )
        }
    }

    fun requestStepByStep() {
        scope.launch { requestStepByStepSync() }
    }

    suspend fun requestStepByStepSync() {
        val current = _uiState.value
        val task = current.activeTask ?: return
        val concept = current.selectedConceptId?.let { repository.getConceptById(it) } ?: return

        _uiState.update {
            it.copy(
                isSocraticLoading = true,
                isSocraticSheetVisible = true,
                socraticDialogue = it.socraticDialogue + SocraticDialogueMessage(
                    id = "user_${System.currentTimeMillis()}",
                    isUser = true,
                    text = "🧩 Divide el problema en pasos simples",
                    mode = SocraticMode.STEP_BY_STEP,
                )
            )
        }

        val tutorReply = socraticTutorEngine.consultTutor(
            concept = concept,
            task = task,
            mode = SocraticMode.STEP_BY_STEP
        )

        _uiState.update {
            it.copy(
                isSocraticLoading = false,
                socraticDialogue = it.socraticDialogue + tutorReply
            )
        }
    }

    fun sendSocraticQuery(query: String) {
        scope.launch { sendSocraticQuerySync(query) }
    }

    suspend fun sendSocraticQuerySync(query: String) {
        if (query.isBlank()) return
        val current = _uiState.value
        val task = current.activeTask ?: return
        val concept = current.selectedConceptId?.let { repository.getConceptById(it) } ?: return

        _uiState.update {
            it.copy(
                isSocraticLoading = true,
                isSocraticSheetVisible = true,
                socraticDialogue = it.socraticDialogue + SocraticDialogueMessage(
                    id = "user_${System.currentTimeMillis()}",
                    isUser = true,
                    text = query.trim(),
                    mode = SocraticMode.FREE_INQUIRY,
                )
            )
        }

        val tutorReply = socraticTutorEngine.consultTutor(
            concept = concept,
            task = task,
            mode = SocraticMode.FREE_INQUIRY,
            userQuery = query.trim()
        )

        _uiState.update {
            it.copy(
                isSocraticLoading = false,
                socraticDialogue = it.socraticDialogue + tutorReply
            )
        }
    }

    // ── DIPLOMAS CRIPTOGRÁFICOS Y SIMULADORES ──────────────────────────────
    fun openDiplomaDialog() {
        if (_uiState.value.activeDiploma == null) {
            mintDiplomaForCurrentTrack()
        }
        _uiState.update { it.copy(isDiplomaDialogVisible = true) }
    }

    fun dismissDiplomaDialog() {
        _uiState.update { it.copy(isDiplomaDialogVisible = false) }
    }

    fun mintDiplomaForCurrentTrack() {
        val current = _uiState.value
        val track = current.track
        val evidenceHashes = repository.getRecentEvidenceHashesForTrack(track)
            .ifEmpty { listOf(current.lastEvidenceHash ?: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855") }

        val diploma = com.elysium369.meet.education.domain.ElysiumDiplomaService.mintDiploma(
            principalId = current.learnerId.ifBlank { "elysium_learner_me" },
            trackTitle = track.displayName,
            trackCode = track.name,
            academicCycle = track.cycleName,
            mastery = current.currentMasteryEstimate.coerceAtLeast(0.85),
            confidence = current.currentConfidence.coerceAtLeast(0.90),
            evidenceHashes = evidenceHashes
        )
        _uiState.update { it.copy(activeDiploma = diploma, isDiplomaDialogVisible = true) }
    }

    fun toggleGeometrySandbox() {
        _uiState.update { it.copy(isGeometrySandboxVisible = !it.isGeometrySandboxVisible) }
    }

    fun toggleElectricalSandbox() {
        _uiState.update { it.copy(isElectricalSandboxVisible = !it.isElectricalSandboxVisible) }
    }

    fun connectDtcToCurriculum(dtcCode: String) {
        val bridge = com.elysium369.meet.education.domain.DtcCurriculumBridge.getBridgeForDtc(dtcCode)
        _uiState.update {
            it.copy(
                linkedDtcBridge = bridge,
                isElectricalSandboxVisible = bridge?.sandboxType == "ELECTRICAL",
                isGeometrySandboxVisible = bridge?.sandboxType == "GEOMETRY"
            )
        }
    }

    // ── CALIBRACIÓN INTERNACIONAL PISA OCDE ────────────────────────────────
    fun selectPisaDomain(domain: com.elysium369.meet.education.domain.PisaDomain) {
        val current = _uiState.value
        val fundamental = current.currentMasteryEstimate.coerceAtLeast(0.70)
        val transfers = if (current.isTransferUnlocked || fundamental >= 0.75) 5 else 2

        val activeProcesses = when (domain) {
            com.elysium369.meet.education.domain.PisaDomain.MATHEMATICAL_LITERACY -> setOf(
                com.elysium369.meet.education.domain.PisaCognitiveProcess.MATH_FORMULATE,
                com.elysium369.meet.education.domain.PisaCognitiveProcess.MATH_EMPLOY,
                com.elysium369.meet.education.domain.PisaCognitiveProcess.MATH_INTERPRET_EVALUATE,
                com.elysium369.meet.education.domain.PisaCognitiveProcess.MATH_REASON,
            )
            com.elysium369.meet.education.domain.PisaDomain.READING_LITERACY -> setOf(
                com.elysium369.meet.education.domain.PisaCognitiveProcess.READING_LOCATE_INFORMATION,
                com.elysium369.meet.education.domain.PisaCognitiveProcess.READING_UNDERSTAND_INTEGRATE,
                com.elysium369.meet.education.domain.PisaCognitiveProcess.READING_EVALUATE_REFLECT,
            )
            com.elysium369.meet.education.domain.PisaDomain.SCIENTIFIC_LITERACY -> setOf(
                com.elysium369.meet.education.domain.PisaCognitiveProcess.SCIENCE_EXPLAIN_PHENOMENA,
                com.elysium369.meet.education.domain.PisaCognitiveProcess.SCIENCE_EVALUATE_DESIGN_ENQUIRY,
                com.elysium369.meet.education.domain.PisaCognitiveProcess.SCIENCE_INTERPRET_DATA_EVIDENCE,
            )
            else -> setOf(
                com.elysium369.meet.education.domain.PisaCognitiveProcess.MATH_REASON,
                com.elysium369.meet.education.domain.PisaCognitiveProcess.READING_EVALUATE_REFLECT,
            )
        }

        val progressionSpec = com.elysium369.meet.education.engine.PisaLevel6MasteryEngine.evaluateSubjectProgression(
            domain = domain,
            fundamentalMastery = fundamental,
            transferDemonstrations = transfers,
            activeProcesses = activeProcesses,
        )

        val report = com.elysium369.meet.education.engine.PisaAssessmentEngine.evaluatePisaProfile(
            domain = domain,
            processDemonstrations = mapOf(
                com.elysium369.meet.education.domain.PisaCognitiveProcess.MATH_EMPLOY to (9 to 10),
                com.elysium369.meet.education.domain.PisaCognitiveProcess.MATH_FORMULATE to (8 to 10),
                com.elysium369.meet.education.domain.PisaCognitiveProcess.MATH_INTERPRET_EVALUATE to (8 to 10),
                com.elysium369.meet.education.domain.PisaCognitiveProcess.MATH_REASON to (8 to 10),
            )
        )

        _uiState.update {
            it.copy(
                pisaSelectedDomain = domain,
                pisaProgressionSpec = progressionSpec,
                pisaReport = report,
            )
        }
    }

    fun toggleSystemExplainer() {
        _uiState.update { it.copy(isSystemExplainerVisible = !it.isSystemExplainerVisible) }
    }

    fun togglePisaExpanded() {
        _uiState.update { it.copy(isPisaExpanded = !it.isPisaExpanded) }
    }

    fun openSocraticTutorForPisa(domain: com.elysium369.meet.education.domain.PisaDomain) {
        val topicName = when (domain) {
            com.elysium369.meet.education.domain.PisaDomain.MATHEMATICAL_LITERACY -> "Desafío PISA Nivel 6: Modelado y Formulación Matemática"
            com.elysium369.meet.education.domain.PisaDomain.READING_LITERACY -> "Desafío PISA Nivel 6: Evaluación Crítica de Fuentes Discrepantes"
            com.elysium369.meet.education.domain.PisaDomain.SCIENTIFIC_LITERACY -> "Desafío PISA Nivel 6: Diseño Experimental y Razonamiento Científico"
            com.elysium369.meet.education.domain.PisaDomain.CREATIVE_THINKING -> "Desafío PISA Nivel 6: Pensamiento Creativo y Solución No Convencional"
            com.elysium369.meet.education.domain.PisaDomain.LEARNING_IN_DIGITAL_WORLD -> "Desafío PISA Nivel 6: Aprendizaje y Evaluación en Entornos Digitales"
        }
        val prompt = "¡Bienvenido al Desafío Internacional PISA Nivel 6 ($topicName)! Te acompañaré con el método socrático. No te daré la respuesta directa; te guiaré para que formules el modelo, identifiques supuestos y justifiques tu razonamiento. ¿Listo para analizar el primer problema inédito del mundo real?"

        _uiState.update {
            it.copy(
                isSocraticSheetVisible = true,
                socraticDialogue = listOf(
                    SocraticDialogueMessage(
                        id = "pisa_welcome_${System.currentTimeMillis()}",
                        isUser = false,
                        text = prompt,
                        mode = SocraticMode.FREE_INQUIRY,
                    )
                )
            )
        }
    }
}
