package com.elysium369.meet.education.data

import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.education.domain.ConceptKnowledgeState
import com.elysium369.meet.education.domain.CurriculumConcept
import com.elysium369.meet.education.domain.CurriculumPrerequisite
import com.elysium369.meet.education.domain.EpistemicTruthState
import com.elysium369.meet.education.domain.FrontierConcept
import com.elysium369.meet.education.domain.LearningEvidenceRecord
import com.elysium369.meet.education.domain.PersonalLearningFrontier
import com.elysium369.meet.education.economic.SkillToServiceBridge
import com.elysium369.meet.education.economic.SkillToServiceMapping
import com.elysium369.meet.education.engine.ElysiumMasteryEngine
import com.elysium369.meet.education.forge.ForgeEducationBridge
import com.elysium369.meet.identity.ActivePrincipalKernel
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class ElysiumLearningRepository(
    private val principalProvider: com.elysium369.meet.identity.ActivePrincipalProvider?,
) {
    @Inject
    constructor(activePrincipalKernel: ActivePrincipalKernel) : this(principalProvider = activePrincipalKernel)

    constructor() : this(null)

    private val json = Json { ignoreUnknownKeys = true }
    private val localConceptStates = mutableMapOf<String, ConceptKnowledgeState>()

    private val principalId: String
        get() = principalProvider?.current()?.id ?: "local_student_cr_001"

    private val canSyncToCloud: Boolean
        get() = principalProvider?.current()?.canSyncToCloud ?: false

    // Canonical Prerequisites DAG
    private val canonicalPrerequisites = listOf(
        CurriculumPrerequisite(
            conceptId = "cr_mat1_c_addition_sub",
            prerequisiteConceptId = "cr_mat1_c_counting_100",
            relationshipType = "STRICT_PREREQUISITE",
        ),
        CurriculumPrerequisite(
            conceptId = "cr_mat1_c_currency_crc",
            prerequisiteConceptId = "cr_mat1_c_counting_100",
            relationshipType = "STRICT_PREREQUISITE",
        ),
    )

    fun getCurriculumUnits(track: CurriculumTrack): List<CourseUnitData> {
        return when (track) {
            CurriculumTrack.MATEMATICA_1 -> CourseZeroCurriculumSeed.MATEMATICA_1_UNITS
            CurriculumTrack.FONTANERIA_7 -> CourseZeroCurriculumSeed.FONTANERIA_7_UNITS
        }
    }

    fun getConceptById(conceptId: String): CurriculumConceptData? {
        val allUnits = CourseZeroCurriculumSeed.MATEMATICA_1_UNITS + CourseZeroCurriculumSeed.FONTANERIA_7_UNITS
        return allUnits.flatMap { it.concepts }.firstOrNull { it.id == conceptId }
    }

    fun getTaskById(taskId: String): InteractiveTaskData? {
        val allUnits = CourseZeroCurriculumSeed.MATEMATICA_1_UNITS + CourseZeroCurriculumSeed.FONTANERIA_7_UNITS
        return allUnits.flatMap { it.concepts }.flatMap { it.tasks }.firstOrNull { it.id == taskId }
    }

    fun getLocalConceptState(conceptId: String): ConceptKnowledgeState {
        val pId = principalId
        return localConceptStates.getOrPut(conceptId) {
            ConceptKnowledgeState(
                learnerId = pId,
                conceptId = conceptId,
                masteryEstimate = 0.0,
                confidence = 0.20,
                evidenceCount = 0,
            )
        }
    }

    fun getEconomicBridgeMappings(): List<SkillToServiceMapping> {
        return SkillToServiceBridge.CANONICAL_PLUMBING_MAPPINGS
    }

    /**
     * Resolves the Personal Learning Frontier.
     * Queries Supabase RPC `learning_get_personal_frontier_v1` when authenticated and online.
     * Seamlessly falls back to local deterministic calculation via ElysiumMasteryEngine when offline.
     */
    suspend fun getPersonalFrontier(
        subject: String = "MATEMATICA",
        grade: Int = 1,
    ): Result<PersonalLearningFrontier> = withContext(Dispatchers.IO) {
        val pId = principalId

        // If authenticated and can sync, try remote Supabase RPC
        if (canSyncToCloud) {
            val remoteResult = runCatching {
                val client = SupabaseModule.client
                val params = buildJsonObject {
                    put("p_subject", subject)
                    put("p_grade", grade)
                }

                val response = client.postgrest.rpc("learning_get_personal_frontier_v1", params)
                val rootObj = json.parseToJsonElement(response.data).jsonObject

                val success = rootObj["success"]?.jsonPrimitive?.booleanOrNull ?: false
                if (!success) {
                    throw IllegalStateException("Supabase returned unsuccessful response: ${response.data}")
                }

                val frontierArray = rootObj["frontier_concepts"]?.jsonArray ?: emptyList()
                val parsedConcepts = frontierArray.map { item ->
                    val obj = item.jsonObject
                    FrontierConcept(
                        conceptId = obj["concept_id"]?.jsonPrimitive?.content ?: "",
                        conceptCode = obj["concept_code"]?.jsonPrimitive?.content ?: "",
                        title = obj["title"]?.jsonPrimitive?.content ?: "",
                        unitTitle = obj["unit_title"]?.jsonPrimitive?.content ?: "",
                        targetMonth = obj["target_month"]?.jsonPrimitive?.intOrNull ?: 1,
                        currentMastery = obj["current_mastery"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        confidence = obj["confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.2,
                        evidenceCount = obj["evidence_count"]?.jsonPrimitive?.intOrNull ?: 0,
                        needsTransfer = obj["needs_transfer"]?.jsonPrimitive?.booleanOrNull ?: false,
                    )
                }

                PersonalLearningFrontier(
                    learnerId = pId,
                    subject = subject,
                    grade = grade,
                    frontierConcepts = parsedConcepts,
                )
            }

            if (remoteResult.isSuccess) {
                return@withContext remoteResult
            }
        }

        // Deterministic local offline fallback
        Result.success(computeLocalFrontier(pId, subject, grade))
    }

    /**
     * Records educational evidence cryptographically and updates mastery state.
     * Enforces the invariant: Answering without transfer caps at 0.750 max.
     */
    suspend fun recordEvidence(
        conceptId: String,
        taskId: String,
        isCorrect: Boolean,
        isTransferTask: Boolean,
        misconceptionCode: String? = null,
        responseLatencyMs: Int? = null,
        environmentContext: String = "FORGE_3D_ROOM",
    ): Result<LearningEvidenceRecord> = withContext(Dispatchers.IO) {
        val pId = principalId
        val todayEpochDay = LocalDate.now().toEpochDay()
        val currentState = getLocalConceptState(conceptId)

        // Always update local state immediately for instant feedback
        val updatedState = ElysiumMasteryEngine.recordEvidence(
            current = currentState,
            isCorrect = isCorrect,
            isTransferTask = isTransferTask,
            misconceptionCode = misconceptionCode,
            todayEpochDay = todayEpochDay,
        )
        localConceptStates[conceptId] = updatedState

        // Build local cryptographic evidence packet
        val localEvidence = ForgeEducationBridge.createEvidencePacket(
            learnerId = pId,
            conceptId = conceptId,
            taskId = taskId,
            environmentContext = environmentContext,
            isCorrect = isCorrect,
            isTransferTask = isTransferTask,
            misconceptionCode = misconceptionCode,
            latencyMs = responseLatencyMs ?: 1200,
        )

        val evidenceRecord = LearningEvidenceRecord(
            id = "evi_${System.currentTimeMillis()}_${conceptId.takeLast(6)}",
            learnerId = pId,
            conceptId = conceptId,
            taskId = taskId,
            isCorrect = isCorrect,
            isTransferTask = isTransferTask,
            misconceptionCode = misconceptionCode,
            responseLatencyMs = responseLatencyMs,
            environmentContext = environmentContext,
            rawEvidenceHash = localEvidence.rawEvidenceHash,
            timestampEpochMs = System.currentTimeMillis(),
        )

        // If authenticated and can sync, push to Supabase RPC asynchronously
        if (canSyncToCloud) {
            runCatching {
                val client = SupabaseModule.client
                val params = buildJsonObject {
                    put("p_concept_id", conceptId)
                    put("p_task_id", taskId)
                    put("p_is_correct", isCorrect)
                    put("p_is_transfer_task", isTransferTask)
                    misconceptionCode?.let { put("p_misconception_code", it) }
                    responseLatencyMs?.let { put("p_response_latency_ms", it) }
                    put("p_environment_context", environmentContext)
                }
                client.postgrest.rpc("learning_record_evidence_v1", params)
                Unit
            }
        }

        Result.success(evidenceRecord)
    }

    private fun computeLocalFrontier(
        learnerId: String,
        subject: String,
        grade: Int,
    ): PersonalLearningFrontier {
        val units = if (grade == 7) {
            CourseZeroCurriculumSeed.FONTANERIA_7_UNITS
        } else {
            CourseZeroCurriculumSeed.MATEMATICA_1_UNITS
        }

        val concepts = units.flatMap { unit ->
            unit.concepts.map { c ->
                CurriculumConcept(
                    id = c.id,
                    unitId = unit.id,
                    conceptCode = c.conceptCode,
                    title = c.title,
                    description = c.description,
                    truthState = c.truthState,
                    isOfficial = true,
                    sourceAnchor = unit.id,
                )
            }
        }

        val conceptMonthMap = units.flatMap { unit ->
            unit.concepts.map { c -> c.id to unit.targetMonth }
        }.toMap()

        val frontierConcepts = ElysiumMasteryEngine.computeLearningFrontier(
            targetConcepts = concepts,
            prerequisites = canonicalPrerequisites,
            states = localConceptStates,
            conceptMonthMap = conceptMonthMap,
            maxFrontierSize = 5,
        )

        return PersonalLearningFrontier(
            learnerId = learnerId,
            subject = subject,
            grade = grade,
            frontierConcepts = frontierConcepts,
        )
    }
}
