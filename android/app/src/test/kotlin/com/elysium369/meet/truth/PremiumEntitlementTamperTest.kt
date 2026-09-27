package com.elysium369.meet.truth

import com.elysium369.meet.core.agent.capability.AgentExecutionContext
import com.elysium369.meet.core.agent.capability.AgentResult
import com.elysium369.meet.core.agent.capability.mechanic.*
import com.elysium369.meet.core.agent.policy.AgentPolicyEngine
import com.elysium369.meet.core.agent.policy.PolicyDecision
import com.elysium369.meet.core.agentstore.data.AgentEntitlementRepository
import com.elysium369.meet.core.agentstore.domain.AgentEntitlementGateway
import com.elysium369.meet.core.agentstore.domain.EntitlementSnapshot
import com.elysium369.meet.domain.diagnostics.DeterministicVehicleEvidenceGraphRepository
import com.elysium369.meet.domain.diagnostics.VehicleEvidenceGraph
import com.elysium369.meet.domain.diagnostics.VehicleEvidenceGraphProjectionInput
import com.elysium369.meet.domain.diagnostics.VehicleEvidenceProjectionProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ══════════════════════════════════════════════════════════════════════
 *  P R E M I U M   E N T I T L E M E N T   T A M P E R   T E S T
 *  ──────────────────────────────────────────────────────────────
 *  Governed by: ELYSIUM MASTER IMPLEMENTATION ORDER — ASCENSION MAXIMA §2, §6, Test Prioritario
 *
 *  MANDATORY SECURITY INVARIANT:
 *  "Parchear todo el estado local para afirmar que agent.master_mechanic está
 *   comprado y verificar que una capability premium respaldada por servidor
 *   sigue devolviendo ENTITLEMENT_REQUIRED."
 * ══════════════════════════════════════════════════════════════════════
 */
class PremiumEntitlementTamperTest {

    private class TestAuthoritativeGateway : AgentEntitlementGateway {
            override fun currentPrincipalId(): String = "user_driver_001"
        var serverEntitlements: Set<String> = emptySet()
        var simulateNetworkFailure: Boolean = false

        override suspend fun fetchAuthoritativeEntitlements(): Result<EntitlementSnapshot> {
            if (simulateNetworkFailure) {
                return Result.failure(IllegalStateException("Server unreachable (HTTP 503)"))
            }
            return Result.success(
                EntitlementSnapshot(
                    principalId = "user_driver_001",
                    entitlements = serverEntitlements,
                    asOfEpochMs = System.currentTimeMillis(),
                    revision = 1L,
                )
            )
        }
    }

    private lateinit var gateway: TestAuthoritativeGateway
    private lateinit var entitlementRepository: AgentEntitlementRepository
    private lateinit var policyEngine: AgentPolicyEngine
    private lateinit var capability: MasterMechanicCapability

    @Before
    fun setUp() {
        gateway = TestAuthoritativeGateway()
        entitlementRepository = AgentEntitlementRepository(gateway)
        policyEngine = AgentPolicyEngine(entitlementRepository)

        val evidenceProvider = object : DiagnosticEvidenceProvider {
            override suspend fun capture(vehicleId: String): Result<DiagnosticEvidenceSnapshot> {
                return Result.success(
                    DiagnosticEvidenceSnapshot(
                        vehicleId = vehicleId,
                        bindingId = "binding_v1",
                        dtcs = listOf(ObservedDtc("P0420", "ACTIVE", "Catalyst Efficiency")),
                    )
                )
            }
        }

        val projectionProvider = object : VehicleEvidenceProjectionProvider {
            override suspend fun load(vehicleId: String, bindingId: String): VehicleEvidenceGraphProjectionInput {
                return VehicleEvidenceGraphProjectionInput(
                    vehicleId = vehicleId,
                    vehicleBindingId = bindingId,
                    findings = emptyList(),
                    observations = emptyList(),
                )
            }
        }

        val reasoningEngine = object : DiagnosticReasoningEngine {
            override fun rank(
                evidence: DiagnosticEvidenceSnapshot,
                graph: VehicleEvidenceGraph,
            ): List<DiagnosticHypothesis> {
                return listOf(
                    DiagnosticHypothesis(
                        id = "hyp_cat",
                        title = "Catalytic Converter Degradation",
                        probability = 0.85,
                        supportingEvidenceIds = setOf("P0420"),
                        contradictingEvidenceIds = emptySet(),
                        nextDiscriminatingTest = null,
                    )
                )
            }
        }

        capability = MasterMechanicCapability(
            evidenceProvider = evidenceProvider,
            graphRepository = DeterministicVehicleEvidenceGraphRepository,
            projectionProvider = projectionProvider,
            reasoning = reasoningEngine,
            entitlementRepository = entitlementRepository,
        )
    }

    @Test
    fun `tampered local context claiming entitlement is denied by MasterMechanicCapability`() = runBlocking {
        // Initial state: Server has NOT granted agent.master_mechanic
        entitlementRepository.refresh()
        assertFalse(entitlementRepository.hasEntitlement("agent.master_mechanic"))

        // Adversary crafts a tampered local execution context asserting they have the entitlement
        val tamperedContext = AgentExecutionContext(
            principalId = "user_driver_001",
            activeVehicleId = "veh_toyota_2022",
            userEntitlements = setOf("agent.master_mechanic"), // <-- LOCAL FORGERY
            userConfirmed = true,
        )

        // Attempt direct execution of premium capability
        val result = capability.execute(
            input = MechanicDiagnosisInput(specificDtcQuery = "P0420"),
            context = tamperedContext,
        )

        // Must FAIL CLOSED: local spoofing is ignored; authoritative server state prevails
        assertTrue("Expected EntitlementRequired but got $result", result is AgentResult.EntitlementRequired)
        val entitlementResult = result as AgentResult.EntitlementRequired
        assertEquals("agent.master_mechanic", entitlementResult.requiredEntitlement)
    }

    @Test
    fun `tampered local context claiming entitlement is denied by AgentPolicyEngine`() {
        // Initial state: Server has NOT granted agent.master_mechanic
        runBlocking { entitlementRepository.refresh() }

        val tamperedContext = AgentExecutionContext(
            principalId = "user_driver_001",
            activeVehicleId = "veh_toyota_2022",
            userEntitlements = setOf("agent.master_mechanic"), // <-- LOCAL FORGERY
        )

        val decision = policyEngine.evaluate(
            capability = capability,
            planContextGeneration = 1L,
            currentContext = tamperedContext,
        )

        assertTrue("Expected PolicyDecision.EntitlementRequired but got $decision", decision is PolicyDecision.EntitlementRequired)
        val required = decision as PolicyDecision.EntitlementRequired
        assertEquals("agent.master_mechanic", required.entitlement)
    }

    @Test
    fun `server network failure fails closed and denies premium access`() = runBlocking {
        // Server is down / unreachable
        gateway.simulateNetworkFailure = true
        entitlementRepository.refresh()

        // Even with no local tamper, or with tamper, it MUST fail closed
        val context = AgentExecutionContext(
            principalId = "user_driver_001",
            activeVehicleId = "veh_toyota_2022",
            userEntitlements = emptySet(),
        )

        val result = capability.execute(
            input = MechanicDiagnosisInput(specificDtcQuery = "P0420"),
            context = context,
        )

        assertTrue(result is AgentResult.EntitlementRequired)
    }

    @Test
    fun `authentic server verification grants access and executes diagnostic pipeline`() = runBlocking {
        // Authoritative server issues entitlement
        gateway.serverEntitlements = setOf("agent.master_mechanic")
        gateway.simulateNetworkFailure = false
        entitlementRepository.refresh()

        assertTrue(entitlementRepository.hasEntitlement("agent.master_mechanic"))

        val legitimateContext = AgentExecutionContext(
            principalId = "user_driver_001",
            activeVehicleId = "veh_toyota_2022",
            userEntitlements = setOf("agent.master_mechanic"),
            userConfirmed = true,
        )

        val result = capability.execute(
            input = MechanicDiagnosisInput(specificDtcQuery = "P0420"),
            context = legitimateContext,
        )

        assertTrue("Expected Success but got $result", result is AgentResult.Success)
        val success = result as AgentResult.Success
        assertEquals(1, success.data.findings.size)
        assertEquals("P0420", success.data.findings.first().code)
        assertTrue(success.data.hypotheses.isNotEmpty())
    }
}
