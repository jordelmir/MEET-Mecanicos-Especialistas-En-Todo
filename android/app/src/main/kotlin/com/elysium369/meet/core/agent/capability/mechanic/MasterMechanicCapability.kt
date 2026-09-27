package com.elysium369.meet.core.agent.capability.mechanic

import com.elysium369.meet.core.agent.capability.AgentCapability
import com.elysium369.meet.core.agent.capability.AgentExecutionContext
import com.elysium369.meet.core.agent.capability.AgentResult
import com.elysium369.meet.core.agent.capability.AgentRisk
import com.elysium369.meet.core.agent.capability.CapabilityId
import com.elysium369.meet.core.agent.capability.CapabilityValidation
import com.elysium369.meet.core.agentstore.data.AgentEntitlementRepository
import com.elysium369.meet.core.agentstore.domain.AgentEntitlementGateway
import com.elysium369.meet.core.agentstore.domain.EntitlementSnapshot
import com.elysium369.meet.domain.diagnostics.DeterministicVehicleEvidenceGraphRepository
import com.elysium369.meet.domain.diagnostics.VehicleEvidenceGraph
import com.elysium369.meet.domain.diagnostics.VehicleEvidenceGraphProjectionInput
import com.elysium369.meet.domain.diagnostics.VehicleEvidenceGraphRepository
import com.elysium369.meet.domain.diagnostics.VehicleEvidenceProjectionProvider
import javax.inject.Inject

data class MechanicDiagnosisInput(
    val triggerSource: String = "USER_REQUEST",
    val specificDtcQuery: String? = null,
    val includeMode06: Boolean = true,
)

data class DtcFinding(
    val code: String,
    val description: String,
    val severity: String,
    val probableCauses: List<String>,
    val requiresPhysicalVerification: Boolean = true,
)

data class MechanicDiagnosisOutput(
    val findings: List<DtcFinding>,
    val hypotheses: List<String>,
    val summary: String,
    val vehicleId: String?,
    val isPhysicalScan: Boolean,
) {
    companion object {
        fun from(
            evidence: DiagnosticEvidenceSnapshot,
            hypotheses: List<DiagnosticHypothesis>,
        ): MechanicDiagnosisOutput {
            // Honest empty-DTC path: AGENTS.md §1 — "OBD no disponible"
            if (evidence.dtcs.isEmpty()) {
                return MechanicDiagnosisOutput(
                    findings = emptyList(),
                    hypotheses = emptyList(),
                    summary = "OBD no disponible o ningún código de diagnóstico activo. Requiere prueba física.",
                    vehicleId = evidence.vehicleId,
                    isPhysicalScan = false,
                )
            }
            val findings = evidence.dtcs.map { dtc ->
                DtcFinding(
                    code = dtc.code,
                    description = dtc.description.ifBlank { "Código de diagnóstico OBD-II ${dtc.code}" },
                    severity = if (dtc.code.startsWith("P03") || dtc.code == "P0171") "HIGH" else "MODERATE",
                    probableCauses = hypotheses.map { it.title }.take(3),
                    requiresPhysicalVerification = true,
                )
            }
            return MechanicDiagnosisOutput(
                findings = findings,
                hypotheses = hypotheses.map { "${it.title} (${(it.probability * 100).toInt()}%)" },
                summary = "Diagnóstico construido desde ${evidence.dtcs.size} DTCs y ${hypotheses.size} hipótesis causales.",
                vehicleId = evidence.vehicleId,
                isPhysicalScan = true,
            )
        }
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════
 *  M A S T E R   M E C H A N I C   C A P A B I L I T Y
 *  ──────────────────────────────────────────────────────────────
 *  Governed by: ELYSIUM MASTER IMPLEMENTATION ORDER — ASCENSION MAXIMA §6
 *
 *  - Consumes Vehicle Evidence Fabric and DiagnosticEvidenceSnapshot.
 *  - Never hardcodes static two-DTC tables.
 *  - Strictly checks server-authoritative AgentEntitlementRepository.
 *  - Fails closed with ENTITLEMENT_REQUIRED if unowned or untrusted.
 * ══════════════════════════════════════════════════════════════════════
 */
class MasterMechanicCapability @Inject constructor(
    private val evidenceProvider: DiagnosticEvidenceProvider,
    private val graphRepository: VehicleEvidenceGraphRepository,
    private val projectionProvider: VehicleEvidenceProjectionProvider,
    private val reasoning: DiagnosticReasoningEngine,
    private val entitlementRepository: AgentEntitlementRepository,
) : AgentCapability<MechanicDiagnosisInput, MechanicDiagnosisOutput> {

    /**
     * Secondary fail-closed constructor for testing or non-DI environments.
     */
    constructor() : this(
        evidenceProvider = object : DiagnosticEvidenceProvider {
            override suspend fun capture(vehicleId: String): Result<DiagnosticEvidenceSnapshot> =
                Result.failure(IllegalStateException("No physical evidence provider configured."))
        },
        graphRepository = DeterministicVehicleEvidenceGraphRepository,
        projectionProvider = object : VehicleEvidenceProjectionProvider {
            override suspend fun load(vehicleId: String, bindingId: String): VehicleEvidenceGraphProjectionInput =
                VehicleEvidenceGraphProjectionInput(vehicleId, bindingId, emptyList(), emptyList())
        },
        reasoning = object : DiagnosticReasoningEngine {
            override fun rank(evidence: DiagnosticEvidenceSnapshot, graph: VehicleEvidenceGraph): List<DiagnosticHypothesis> =
                emptyList()
        },
        entitlementRepository = AgentEntitlementRepository(object : AgentEntitlementGateway {
            override suspend fun fetchAuthoritativeEntitlements(): Result<EntitlementSnapshot> =
                Result.success(EntitlementSnapshot("unauthenticated", emptySet(), 0L, 0L))
        }),
    )

    override val id: CapabilityId = CapabilityId.of("vehicle.read_dtc")
    override val risk: AgentRisk = AgentRisk.READ_ONLY
    override val requiredEntitlement: String = "agent.master_mechanic"

    override suspend fun validate(
        input: MechanicDiagnosisInput,
        context: AgentExecutionContext,
    ): CapabilityValidation {
        if (context.activeVehicleId.isNullOrBlank()) {
            return CapabilityValidation.Invalid(reason = "ACTIVE_VEHICLE_REQUIRED")
        }
        return CapabilityValidation.Valid
    }

    override suspend fun execute(
        input: MechanicDiagnosisInput,
        context: AgentExecutionContext,
    ): AgentResult<MechanicDiagnosisOutput> {
        // 1. Authoritative Server Entitlement check (ASCENSION §2, §6)
        val hasEntitlement = entitlementRepository.hasEntitlement(requiredEntitlement)

        if (!hasEntitlement) {
            return AgentResult.EntitlementRequired(
                requiredEntitlement = requiredEntitlement,
                storeDeepLink = "agent_store",
            )
        }

        val vehicleId = requireNotNull(context.activeVehicleId)
        val evidence = evidenceProvider.capture(vehicleId).getOrElse {
            return AgentResult.Failure(
                code = "EVIDENCE_UNAVAILABLE",
                message = "No existe evidencia física suficiente para diagnosticar: ${it.message}",
            )
        }

        val projection = projectionProvider.load(
            vehicleId = evidence.vehicleId,
            bindingId = evidence.bindingId,
        )
        val graph = graphRepository.rebuild(projection)
        val hypotheses = reasoning.rank(evidence = evidence, graph = graph)

        return AgentResult.Success(
            data = MechanicDiagnosisOutput.from(evidence, hypotheses),
            summary = "Diagnóstico construido desde evidencia física OBD capturada.",
        )
    }
}
