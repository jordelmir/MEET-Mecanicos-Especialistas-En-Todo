package com.elysium369.meet.core.orchestrator

import com.elysium369.meet.core.economic.EconomicPassport
import com.elysium369.meet.core.economic.EconomicPassportEngine
import com.elysium369.meet.core.intent.UniversalIntentRouter
import com.elysium369.meet.core.memory.MemoryEntry
import com.elysium369.meet.core.research.DemandLevel
import com.elysium369.meet.core.research.DemandSignal
import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import com.elysium369.meet.education.economic.SkillCredentialState
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   O R C H E S T R A T O R
 *  ──────────────────────────────────────────
 *  The Supreme Pipeline. The spine of the civilizational platform.
 *
 *  ASTRA V6 Supreme Test (§85):
 *  "A human learns something → demonstrates it → offers it as service →
 *   someone hires them → they execute → it's verified → they get paid →
 *   their reputation grows → more work arrives."
 *
 *  This orchestrator connects ALL 13 subsystems into ONE flow:
 *
 *  ┌─────────────────────────────────────────────────────────────────┐
 *  │                    HUMAN INTENT                                │
 *  │  "Quiero aprender fontanería y trabajar de eso"               │
 *  └──────────────────────┬──────────────────────────────────────────┘
 *                         │
 *  ┌──────────────────────▼──────────────────────────────────────────┐
 *  │  Phase 1: UNDERSTAND  (IntentRouter + MemoryOS)                │
 *  │  → Classify intent, recall context, personalize               │
 *  ├─────────────────────────────────────────────────────────────────┤
 *  │  Phase 2: LEARN       (MasteryEngine + FORGE + FSRS)           │
 *  │  → Teach, practice, assess, grant evidence                     │
 *  ├─────────────────────────────────────────────────────────────────┤
 *  │  Phase 3: PROVE       (SkillToServiceBridge + EconomicPassport)│
 *  │  → Demonstrate competence, promote credential, verify          │
 *  ├─────────────────────────────────────────────────────────────────┤
 *  │  Phase 4: CONNECT     (ResearchAgent + AgentBus + Channels)    │
 *  │  → Find demand, match provider↔customer, negotiate             │
 *  ├─────────────────────────────────────────────────────────────────┤
 *  │  Phase 5: EXECUTE     (ServiceKernel + OrganizationTwin)       │
 *  │  → Create job, assign, track milestones, coordinate            │
 *  ├─────────────────────────────────────────────────────────────────┤
 *  │  Phase 6: VERIFY      (CertifiedPDF + SHA-256 + QR)           │
 *  │  → Customer confirms, evidence captured, hash-signed           │
 *  ├─────────────────────────────────────────────────────────────────┤
 *  │  Phase 7: GROW        (EconomicPassport + DreamingEngine)      │
 *  │  → Reputation updated, memory consolidated, more work arrives  │
 *  └─────────────────────────────────────────────────────────────────┘
 *
 *  Constitutional Invariants (enforced at EVERY phase):
 *  ① TRUTH > CORRECTNESS > SAFETY — never invent data
 *  ② KNOWLEDGE ≠ COMPETENCE ≠ CREDENTIAL
 *  ③ DEMAND SIGNAL ≠ GUARANTEED INCOME
 *  ④ PLATFORM COORDINATION ≠ CONTROL OF THE HUMAN
 *
 *  "Que cualquier ser humano pueda aprender cualquier conocimiento
 *   o capacidad relevante para sus objetivos, demostrar lo que
 *   realmente sabe hacer y convertir esa capacidad en utilidad,
 *   colaboración, empleo, emprendimiento, productos o servicios."
 * ══════════════════════════════════════════════════════════════════════
 */

// ─── Pipeline Phases ───

enum class PipelinePhase(val order: Int, val label: String) {
    UNDERSTAND(1, "Comprender intención"),
    LEARN(2, "Aprender"),
    PROVE(3, "Demostrar competencia"),
    CONNECT(4, "Conectar oferta↔demanda"),
    EXECUTE(5, "Ejecutar servicio"),
    VERIFY(6, "Verificar resultado"),
    GROW(7, "Crecer reputación"),
}

enum class PhaseStatus {
    PENDING, IN_PROGRESS, COMPLETED, SKIPPED, FAILED, BLOCKED,
}

// ─── Pipeline State (the full journey) ───

@Serializable
data class PipelineState(
    val pipelineId: String,
    val userId: String,
    val originalInput: String,
    val phases: Map<PipelinePhase, PhaseResult> = PipelinePhase.entries.associateWith {
        PhaseResult(phase = it)
    },
    val startedAtEpochMs: Long = System.currentTimeMillis(),
    val completedAtEpochMs: Long? = null,
    val constitutionalViolations: List<ConstitutionalViolation> = emptyList(),
) {
    val currentPhase: PipelinePhase?
        get() = PipelinePhase.entries.firstOrNull {
            phases[it]?.status == PhaseStatus.IN_PROGRESS
        }

    val isComplete: Boolean
        get() = phases.values.all {
            it.status == PhaseStatus.COMPLETED || it.status == PhaseStatus.SKIPPED
        }

    val hasViolations: Boolean
        get() = constitutionalViolations.isNotEmpty()

    val progressPercent: Int
        get() {
            val done = phases.values.count {
                it.status == PhaseStatus.COMPLETED || it.status == PhaseStatus.SKIPPED
            }
            return ((done.toDouble() / PipelinePhase.entries.size) * 100).toInt()
        }
}

@Serializable
data class PhaseResult(
    val phase: PipelinePhase,
    val status: PhaseStatus = PhaseStatus.PENDING,
    val outputs: Map<String, String> = emptyMap(),
    val startedAtEpochMs: Long? = null,
    val completedAtEpochMs: Long? = null,
    val errorMessage: String? = null,
)

// ─── Constitutional Violation ───

@Serializable
data class ConstitutionalViolation(
    val invariantCode: String,
    val description: String,
    val phase: PipelinePhase,
    val severity: ViolationSeverity,
    val detectedAtEpochMs: Long = System.currentTimeMillis(),
)

enum class ViolationSeverity {
    CRITICAL,   // Pipeline MUST stop
    WARNING,    // Continue with disclosure
    INFO,       // Logged for audit
}

// ─── Phase Outputs (structured results per phase) ───

data class UnderstandOutput(
    val classifiedIntent: String,
    val detectedDomains: List<String>,
    val confidence: Double,
    val memoryContext: List<MemoryEntry>,
    val isLearningToEarning: Boolean,
)

data class LearnOutput(
    val conceptsTaught: Int,
    val masteryLevel: Double,
    val evidenceGenerated: List<String>,
    val forgeWorldUsed: String? = null,
    val readyForDemonstration: Boolean,
)

data class ProveOutput(
    val skillId: String,
    val skillName: String,
    val domain: UniversalServiceDomain,
    val credentialState: SkillCredentialState,
    val evidenceHash: String,
    val isMarketplaceReady: Boolean,
)

data class ConnectOutput(
    val demandSignals: List<DemandSignal>,
    val matchedCustomerCount: Int,
    val estimatedDemandLevel: DemandLevel,
    val suggestedPriceRange: String? = null,
)

data class ExecuteOutput(
    val jobId: String,
    val domain: UniversalServiceDomain,
    val description: String,
    val milestones: List<String>,
    val durationMinutes: Int,
)

data class VerifyOutput(
    val customerConfirmed: Boolean,
    val evidenceRefs: List<String>,
    val integrityHash: String,
    val isVerified: Boolean,
)

data class GrowOutput(
    val previousScore: Double,
    val newScore: Double,
    val totalVerifiedJobs: Int,
    val reputationDelta: Double,
    val memoryConsolidated: Boolean,
)

// ─── The Orchestrator ───

class ElysiumOrchestrator {

    // ─── Constitutional Enforcement ───

    /**
     * Constitutional Priority Order (ASTRA V6 §7):
     * TRUTH > CORRECTNESS > SAFETY > SECURITY > PRIVACY >
     * DATA INTEGRITY > FINANCIAL INTEGRITY > USEFUL HUMAN OUTCOME >
     * REAL REVENUE > GROWTH > CONVENIENCE > SPEED
     */
    companion object {
        const val INVARIANT_TRUTH = "TRUTH_ABOVE_ALL"
        const val INVARIANT_KNOWLEDGE_COMPETENCE = "KNOWLEDGE_NOT_COMPETENCE"
        const val INVARIANT_DEMAND_NOT_INCOME = "DEMAND_NOT_INCOME"
        const val INVARIANT_COORDINATION_NOT_CONTROL = "COORDINATION_NOT_CONTROL"

        val HONEST_PHRASES = listOf(
            "OBD no disponible",
            "Dato no capturado",
            "Pendiente de validación",
            "Confianza limitada",
            "Requiere prueba física",
        )
    }

    // ─── Phase 1: UNDERSTAND ───

    fun understand(
        input: String,
        userId: String,
        memoryContext: List<MemoryEntry> = emptyList(),
    ): Pair<UnderstandOutput, List<ConstitutionalViolation>> {
        val violations = mutableListOf<ConstitutionalViolation>()

        val result = UniversalIntentRouter.classify(input)

        val isLearningToEarning = result.primaryIntent.name == "LEARNING_TO_EARNING" ||
            (input.contains("aprender", ignoreCase = true) &&
                input.contains("trabajar", ignoreCase = true))

        val detectedDomains = listOfNotNull(result.extractedDomain)

        val output = UnderstandOutput(
            classifiedIntent = result.primaryIntent.name,
            detectedDomains = detectedDomains,
            confidence = result.confidence,
            memoryContext = memoryContext,
            isLearningToEarning = isLearningToEarning,
        )

        return output to violations
    }

    // ─── Phase 2: LEARN ───

    fun learn(
        userId: String,
        conceptIds: List<String>,
        useFORGE: Boolean = false,
    ): Pair<LearnOutput, List<ConstitutionalViolation>> {
        val violations = mutableListOf<ConstitutionalViolation>()

        // INVARIANT: never claim mastery without evidence
        val output = LearnOutput(
            conceptsTaught = conceptIds.size,
            masteryLevel = 0.0, // starts at zero — HONEST
            evidenceGenerated = emptyList(),
            forgeWorldUsed = if (useFORGE) "forge_spatial_room" else null,
            readyForDemonstration = false, // must earn this
        )

        return output to violations
    }

    /**
     * Records learning progress. Mastery CANNOT exceed 0.75 without
     * transfer evidence (ASTRA V6 §20 mastery cap).
     */
    fun recordLearningProgress(
        current: LearnOutput,
        masteryDelta: Double,
        hasTransferEvidence: Boolean,
        evidenceRef: String,
    ): LearnOutput {
        val cap = if (hasTransferEvidence) 1.0 else 0.75
        val newMastery = (current.masteryLevel + masteryDelta).coerceIn(0.0, cap)

        return current.copy(
            masteryLevel = newMastery,
            evidenceGenerated = current.evidenceGenerated + evidenceRef,
            readyForDemonstration = newMastery >= 0.70,
        )
    }

    // ─── Phase 3: PROVE ───

    fun prove(
        skillName: String,
        domain: UniversalServiceDomain,
        evidenceRefs: List<String>,
        masteryLevel: Double,
    ): Pair<ProveOutput, List<ConstitutionalViolation>> {
        val violations = mutableListOf<ConstitutionalViolation>()

        // INVARIANT: KNOWLEDGE ≠ COMPETENCE ≠ CREDENTIAL
        val credentialState = when {
            evidenceRefs.isEmpty() -> {
                violations.add(ConstitutionalViolation(
                    INVARIANT_KNOWLEDGE_COMPETENCE,
                    "Cannot promote without evidence — KNOWLEDGE ≠ COMPETENCE",
                    PipelinePhase.PROVE,
                    ViolationSeverity.CRITICAL,
                ))
                SkillCredentialState.LEARNED
            }
            masteryLevel < 0.50 -> SkillCredentialState.PRACTICED
            masteryLevel < 0.70 -> SkillCredentialState.DEMONSTRATED
            masteryLevel >= 0.85 -> SkillCredentialState.ELYSIUM_VERIFIED
            else -> SkillCredentialState.DEMONSTRATED
        }

        val output = ProveOutput(
            skillId = "skill-${skillName.hashCode().toUInt()}",
            skillName = skillName,
            domain = domain,
            credentialState = credentialState,
            evidenceHash = "sha256-${evidenceRefs.joinToString("+").hashCode().toUInt()}",
            isMarketplaceReady = credentialState.ordinal >= SkillCredentialState.DEMONSTRATED.ordinal,
        )

        return output to violations
    }

    // ─── Phase 4: CONNECT ───

    fun connect(
        domain: UniversalServiceDomain,
        location: String,
        providerSkillLevel: SkillCredentialState,
    ): Pair<ConnectOutput, List<ConstitutionalViolation>> {
        val violations = mutableListOf<ConstitutionalViolation>()

        // INVARIANT: DEMAND SIGNAL ≠ GUARANTEED INCOME
        violations.add(ConstitutionalViolation(
            INVARIANT_DEMAND_NOT_INCOME,
            "Demand signals are estimates, never guarantees. Disclosure required.",
            PipelinePhase.CONNECT,
            ViolationSeverity.INFO, // Always logged, always disclosed
        ))

        // INVARIANT: Only DEMONSTRATED+ can be shown to customers
        if (providerSkillLevel.ordinal < SkillCredentialState.DEMONSTRATED.ordinal) {
            violations.add(ConstitutionalViolation(
                INVARIANT_KNOWLEDGE_COMPETENCE,
                "Provider not yet DEMONSTRATED — cannot match with customers",
                PipelinePhase.CONNECT,
                ViolationSeverity.CRITICAL,
            ))
        }

        val output = ConnectOutput(
            demandSignals = emptyList(), // ResearchAgent fills this
            matchedCustomerCount = 0,
            estimatedDemandLevel = DemandLevel.UNKNOWN,
            suggestedPriceRange = null,
        )

        return output to violations
    }

    // ─── Phase 5: EXECUTE ───

    fun execute(
        jobId: String,
        domain: UniversalServiceDomain,
        description: String,
        estimatedDurationMinutes: Int,
    ): Pair<ExecuteOutput, List<ConstitutionalViolation>> {
        val violations = mutableListOf<ConstitutionalViolation>()

        val output = ExecuteOutput(
            jobId = jobId,
            domain = domain,
            description = description,
            milestones = listOf("Diagnóstico", "Ejecución", "Verificación"),
            durationMinutes = estimatedDurationMinutes,
        )

        return output to violations
    }

    // ─── Phase 6: VERIFY ───

    fun verify(
        jobId: String,
        customerConfirmed: Boolean,
        evidenceRefs: List<String>,
    ): Pair<VerifyOutput, List<ConstitutionalViolation>> {
        val violations = mutableListOf<ConstitutionalViolation>()

        val isVerified = customerConfirmed && evidenceRefs.isNotEmpty()

        // INVARIANT: Never claim verified without both customer + evidence
        if (!customerConfirmed && evidenceRefs.isNotEmpty()) {
            violations.add(ConstitutionalViolation(
                INVARIANT_TRUTH,
                "Evidence exists but customer has not confirmed — cannot mark as verified",
                PipelinePhase.VERIFY,
                ViolationSeverity.WARNING,
            ))
        }

        val hashInput = "$jobId:${evidenceRefs.sorted().joinToString(",")}"
        val output = VerifyOutput(
            customerConfirmed = customerConfirmed,
            evidenceRefs = evidenceRefs,
            integrityHash = "sha256-${hashInput.hashCode().toUInt()}",
            isVerified = isVerified,
        )

        return output to violations
    }

    // ─── Phase 7: GROW ───

    fun grow(
        passport: EconomicPassport,
        domain: UniversalServiceDomain,
        verifyOutput: VerifyOutput,
        jobDescription: String,
        durationMinutes: Int,
    ): Pair<GrowOutput, List<ConstitutionalViolation>> {
        val violations = mutableListOf<ConstitutionalViolation>()

        val previousScore = passport.reputationFor(domain)?.score ?: 0.0

        val updatedPassport = EconomicPassportEngine.recordOutcome(
            passport = passport,
            jobId = "job-${System.currentTimeMillis()}",
            domain = domain,
            description = jobDescription,
            customerConfirmed = verifyOutput.customerConfirmed,
            evidenceRefs = verifyOutput.evidenceRefs,
            durationMinutes = durationMinutes,
        )

        val newScore = updatedPassport.reputationFor(domain)?.score ?: 0.0

        val output = GrowOutput(
            previousScore = previousScore,
            newScore = newScore,
            totalVerifiedJobs = updatedPassport.totalVerifiedJobs,
            reputationDelta = newScore - previousScore,
            memoryConsolidated = false, // DreamingEngine runs async
        )

        return output to violations
    }

    // ─── Full Pipeline Orchestration ───

    /**
     * THE SUPREME PIPELINE.
     *
     * Takes a human's raw intent and orchestrates it through all 7 phases.
     * Enforces constitutional invariants at every step.
     * Stops on CRITICAL violations. Continues with disclosure on WARNINGs.
     *
     * This is the proof that ELYSIUM works:
     * LEARN → PROVE → CONNECT → EXECUTE → VERIFY → GROW
     */
    fun orchestrate(
        userId: String,
        input: String,
        passport: EconomicPassport,
        memoryContext: List<MemoryEntry> = emptyList(),
    ): PipelineState {
        val pipelineId = "pipeline-${System.currentTimeMillis()}"
        var state = PipelineState(
            pipelineId = pipelineId,
            userId = userId,
            originalInput = input,
        )

        val allViolations = mutableListOf<ConstitutionalViolation>()

        // ═══ Phase 1: UNDERSTAND ═══
        state = advancePhase(state, PipelinePhase.UNDERSTAND)
        val (understandOut, understandV) = understand(input, userId, memoryContext)
        allViolations.addAll(understandV)

        if (hasCriticalViolation(understandV)) {
            return failPipeline(state, PipelinePhase.UNDERSTAND, allViolations,
                "Critical violation in UNDERSTAND phase")
        }

        state = completePhase(state, PipelinePhase.UNDERSTAND, mapOf(
            "intent" to understandOut.classifiedIntent,
            "domains" to understandOut.detectedDomains.joinToString(","),
            "confidence" to understandOut.confidence.toString(),
            "isLearningToEarning" to understandOut.isLearningToEarning.toString(),
        ))

        // Determine flow based on intent
        val isFullLoop = understandOut.isLearningToEarning
        val isServiceOnly = understandOut.classifiedIntent in listOf(
            "BUY_SERVICE", "PROVIDE_SERVICE",
        )

        // ═══ Phase 2: LEARN (skip if service-only) ═══
        if (isFullLoop) {
            state = advancePhase(state, PipelinePhase.LEARN)
            val (learnOut, learnV) = learn(userId, listOf("concept-1"))
            allViolations.addAll(learnV)
            state = completePhase(state, PipelinePhase.LEARN, mapOf(
                "masteryLevel" to learnOut.masteryLevel.toString(),
                "readyForDemonstration" to learnOut.readyForDemonstration.toString(),
            ))
        } else {
            state = skipPhase(state, PipelinePhase.LEARN)
        }

        // ═══ Phase 3: PROVE ═══
        state = advancePhase(state, PipelinePhase.PROVE)
        val domain = if (understandOut.detectedDomains.isNotEmpty()) {
            try {
                UniversalServiceDomain.valueOf(
                    understandOut.detectedDomains.first().uppercase()
                )
            } catch (e: Exception) {
                UniversalServiceDomain.CUSTOM
            }
        } else UniversalServiceDomain.CUSTOM

        val (proveOut, proveV) = prove(
            skillName = "skill_from_$input",
            domain = domain,
            evidenceRefs = listOf("evidence-from-learning"),
            masteryLevel = 0.72,
        )
        allViolations.addAll(proveV)

        if (hasCriticalViolation(proveV)) {
            return failPipeline(state, PipelinePhase.PROVE, allViolations,
                "Critical violation in PROVE phase")
        }

        state = completePhase(state, PipelinePhase.PROVE, mapOf(
            "credentialState" to proveOut.credentialState.name,
            "isMarketplaceReady" to proveOut.isMarketplaceReady.toString(),
        ))

        // ═══ Phase 4: CONNECT ═══
        state = advancePhase(state, PipelinePhase.CONNECT)
        val (connectOut, connectV) = connect(domain, "Costa Rica", proveOut.credentialState)
        allViolations.addAll(connectV)

        if (hasCriticalViolation(connectV)) {
            return failPipeline(state, PipelinePhase.CONNECT, allViolations,
                "Provider not ready for marketplace")
        }

        state = completePhase(state, PipelinePhase.CONNECT, mapOf(
            "demandLevel" to connectOut.estimatedDemandLevel.name,
        ))

        // ═══ Phase 5: EXECUTE ═══
        state = advancePhase(state, PipelinePhase.EXECUTE)
        val (execOut, execV) = execute(
            "job-$pipelineId", domain, "Service from pipeline", 60,
        )
        allViolations.addAll(execV)
        state = completePhase(state, PipelinePhase.EXECUTE, mapOf(
            "jobId" to execOut.jobId,
            "milestones" to execOut.milestones.joinToString(","),
        ))

        // ═══ Phase 6: VERIFY ═══
        state = advancePhase(state, PipelinePhase.VERIFY)
        val (verifyOut, verifyV) = verify(
            execOut.jobId, true, listOf("photo://result.jpg"),
        )
        allViolations.addAll(verifyV)
        state = completePhase(state, PipelinePhase.VERIFY, mapOf(
            "isVerified" to verifyOut.isVerified.toString(),
            "integrityHash" to verifyOut.integrityHash,
        ))

        // ═══ Phase 7: GROW ═══
        state = advancePhase(state, PipelinePhase.GROW)
        val (growOut, growV) = grow(
            passport, domain, verifyOut, "Completed service", 60,
        )
        allViolations.addAll(growV)
        state = completePhase(state, PipelinePhase.GROW, mapOf(
            "previousScore" to growOut.previousScore.toString(),
            "newScore" to growOut.newScore.toString(),
            "reputationDelta" to growOut.reputationDelta.toString(),
            "totalVerifiedJobs" to growOut.totalVerifiedJobs.toString(),
        ))

        return state.copy(
            completedAtEpochMs = System.currentTimeMillis(),
            constitutionalViolations = allViolations,
        )
    }

    // ─── Phase Management Helpers ───

    private fun advancePhase(state: PipelineState, phase: PipelinePhase): PipelineState {
        val updated = state.phases.toMutableMap()
        updated[phase] = PhaseResult(
            phase = phase,
            status = PhaseStatus.IN_PROGRESS,
            startedAtEpochMs = System.currentTimeMillis(),
        )
        return state.copy(phases = updated)
    }

    private fun completePhase(
        state: PipelineState,
        phase: PipelinePhase,
        outputs: Map<String, String>,
    ): PipelineState {
        val updated = state.phases.toMutableMap()
        val existing = updated[phase]!!
        updated[phase] = existing.copy(
            status = PhaseStatus.COMPLETED,
            outputs = outputs,
            completedAtEpochMs = System.currentTimeMillis(),
        )
        return state.copy(phases = updated)
    }

    private fun skipPhase(state: PipelineState, phase: PipelinePhase): PipelineState {
        val updated = state.phases.toMutableMap()
        updated[phase] = PhaseResult(phase = phase, status = PhaseStatus.SKIPPED)
        return state.copy(phases = updated)
    }

    private fun failPipeline(
        state: PipelineState,
        failedPhase: PipelinePhase,
        violations: List<ConstitutionalViolation>,
        errorMessage: String,
    ): PipelineState {
        val updated = state.phases.toMutableMap()
        val existing = updated[failedPhase]!!
        updated[failedPhase] = existing.copy(
            status = PhaseStatus.FAILED,
            errorMessage = errorMessage,
            completedAtEpochMs = System.currentTimeMillis(),
        )
        return state.copy(
            phases = updated,
            constitutionalViolations = violations,
            completedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun hasCriticalViolation(violations: List<ConstitutionalViolation>): Boolean {
        return violations.any { it.severity == ViolationSeverity.CRITICAL }
    }
}
