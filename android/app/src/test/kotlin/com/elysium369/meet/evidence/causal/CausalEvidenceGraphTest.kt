package com.elysium369.meet.evidence.causal

import com.elysium369.meet.authority.VerificationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CausalEvidenceGraphTest {

    private lateinit var graph: CausalEvidenceGraph

    @Before
    fun setUp() {
        graph = CausalEvidenceGraph()
    }

    @Test
    fun `appendNode creates cryptographically verified causal chain from observation to clearance`() {
        val vehicleId = "vehicle_corolla_2019"

        // 1. Observation: DTC P0171 (System Too Lean Bank 1)
        val observationNode = graph.appendNode(
            entityId = vehicleId,
            nodeType = CausalNodeType.OBSERVATION,
            payloadJson = """{"dtc":"P0171","stft":18.5,"ltft":24.2}""",
            verificationLevel = VerificationLevel.PHYSICALLY_VERIFIED,
            timestampEpochMs = 1000L,
        )

        // 2. Hypothesis: AI / Tech suspects intake manifold gasket vacuum leak
        val hypothesisNode = graph.appendNode(
            entityId = vehicleId,
            nodeType = CausalNodeType.HYPOTHESIS,
            payloadJson = """{"suspectedDefect":"INTAKE_GASKET_LEAK","confidence":0.88}""",
            relationToParent = CausalRelationType.OBSERVATION_CAUSED_HYPOTHESIS,
            parentIds = listOf(observationNode.nodeId),
            verificationLevel = VerificationLevel.MODEL_DECLARED,
            timestampEpochMs = 2000L,
        )

        // 3. Test: Smoke test performed on intake system
        val testNode = graph.appendNode(
            entityId = vehicleId,
            nodeType = CausalNodeType.DIAGNOSTIC_TEST,
            payloadJson = """{"testMethod":"SMOKE_INJECTION","pressurePsi":1.0}""",
            relationToParent = CausalRelationType.HYPOTHESIS_LED_TO_TEST,
            parentIds = listOf(hypothesisNode.nodeId),
            verificationLevel = VerificationLevel.PHYSICALLY_VERIFIED,
            timestampEpochMs = 3000L,
        )

        // 4. Measurement: Smoke escape detected at cylinder 3 intake runner
        val measurementNode = graph.appendNode(
            entityId = vehicleId,
            nodeType = CausalNodeType.PHYSICAL_MEASUREMENT,
            payloadJson = """{"leakConfirmed":true,"location":"CYLINDER_3_RUNNER"}""",
            relationToParent = CausalRelationType.TEST_PRODUCED_MEASUREMENT,
            parentIds = listOf(testNode.nodeId),
            verificationLevel = VerificationLevel.PHYSICALLY_VERIFIED,
            timestampEpochMs = 4000L,
        )

        // 5. Repair: Replaced intake manifold gasket with OEM part
        val repairNode = graph.appendNode(
            entityId = vehicleId,
            nodeType = CausalNodeType.REPAIR_INTERVENTION,
            payloadJson = """{"partNumber":"OEM-17171-0T040","action":"REPLACED_GASKET"}""",
            relationToParent = CausalRelationType.MEASUREMENT_JUSTIFIED_REPAIR,
            parentIds = listOf(measurementNode.nodeId),
            verificationLevel = VerificationLevel.PHYSICALLY_VERIFIED,
            timestampEpochMs = 5000L,
        )

        // 6. Post Verification: Live engine test, LTFT returned to +1.5%
        val postVerificationNode = graph.appendNode(
            entityId = vehicleId,
            nodeType = CausalNodeType.POST_VERIFICATION,
            payloadJson = """{"stft":0.8,"ltft":1.5,"readinessPassed":true}""",
            relationToParent = CausalRelationType.REPAIR_TRIGGERED_POST_VERIFICATION,
            parentIds = listOf(repairNode.nodeId),
            verificationLevel = VerificationLevel.PHYSICALLY_VERIFIED,
            timestampEpochMs = 6000L,
        )

        // 7. Clearance: Certified Forensic Certificate issued
        val clearanceNode = graph.appendNode(
            entityId = vehicleId,
            nodeType = CausalNodeType.CLEARANCE_CERTIFICATE,
            payloadJson = """{"certifiedBy":"DEKRA_VERIFIED","certId":"CERT-2026-9901"}""",
            relationToParent = CausalRelationType.POST_VERIFICATION_CERTIFIED_CLEARANCE,
            parentIds = listOf(postVerificationNode.nodeId),
            verificationLevel = VerificationLevel.FORENSICALLY_CERTIFIED,
            timestampEpochMs = 7000L,
        )

        // 1. Verify that the entire chain is cryptographically intact
        assertTrue(graph.verifyChainIntegrity(clearanceNode.nodeId))

        // 2. Trace root causes backwards from clearance
        val causalTrace = graph.traceRootCauses(clearanceNode.nodeId)
        assertEquals(7, causalTrace.size)
        assertEquals(CausalNodeType.CLEARANCE_CERTIFICATE, causalTrace[0].nodeType)
        assertEquals(CausalNodeType.OBSERVATION, causalTrace.last().nodeType)
        assertTrue(causalTrace.last().payloadJson.contains("P0171"))
    }

    @Test
    fun `verifyChainIntegrity detects tampering if any ancestor node is corrupted`() {
        val vehicleId = "vehicle_test_tamper"

        val node1 = graph.appendNode(
            entityId = vehicleId,
            nodeType = CausalNodeType.OBSERVATION,
            payloadJson = """{"dtc":"P0300"}""",
            timestampEpochMs = 1000L,
        )

        val node2 = graph.appendNode(
            entityId = vehicleId,
            nodeType = CausalNodeType.REPAIR_INTERVENTION,
            payloadJson = """{"sparkPlugsReplaced":true}""",
            parentIds = listOf(node1.nodeId),
            timestampEpochMs = 2000L,
        )

        assertTrue(graph.verifyChainIntegrity(node2.nodeId))

        // Simulate attacker tampering with node1's proof hash
        val tamperedNode1 = node1.copy(proofHash = "0000000000000000000000000000000000000000000000000000000000000000")
        val field = CausalEvidenceGraph::class.java.getDeclaredField("nodes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val internalNodes = field.get(graph) as java.util.concurrent.ConcurrentHashMap<String, CausalEvidenceNode>
        internalNodes[node1.nodeId] = tamperedNode1

        // Verification must now fail!
        assertFalse(graph.verifyChainIntegrity(node2.nodeId))
    }
}
