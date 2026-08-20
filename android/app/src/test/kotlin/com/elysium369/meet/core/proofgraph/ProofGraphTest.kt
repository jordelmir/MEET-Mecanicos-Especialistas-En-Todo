package com.elysium369.meet.core.proofgraph

import com.elysium369.meet.core.domain.EntityRef
import com.elysium369.meet.core.domain.SourceAuthority
import org.junit.Assert.*
import org.junit.Test

class ProofGraphTest {

    @Test
    fun testProofGraphAncestorsAndDescendants() {
        val dtcNode = ProofNode(
            nodeId = "NODE_DTC_P0301",
            entityRef = EntityRef.FindingRef("P0301"),
            label = "Misfire Cilindro 1",
            authority = SourceAuthority.VEHICLE_ECU,
            timestampUtc = 1000L
        )

        val repairNode = ProofNode(
            nodeId = "NODE_REPAIR_COIL",
            entityRef = EntityRef.RepairRef("REP_001"),
            label = "Reemplazo de Bobina 1",
            authority = SourceAuthority.SERVICE_PROVIDER,
            timestampUtc = 2000L
        )

        val edge = ProofEdge(
            edgeId = "EDGE_001",
            fromNodeId = "NODE_DTC_P0301",
            toNodeId = "NODE_REPAIR_COIL",
            relationType = ProofRelationType.RESOLVES
        )

        val graph = ProofGraph(
            vehicleId = "V-001",
            nodes = mapOf(dtcNode.nodeId to dtcNode, repairNode.nodeId to repairNode),
            edges = listOf(edge)
        )

        val descendants = graph.getDescendants("NODE_DTC_P0301")
        assertEquals(1, descendants.size)
        assertEquals("NODE_REPAIR_COIL", descendants.first().nodeId)

        val ancestors = graph.getAncestors("NODE_REPAIR_COIL")
        assertEquals(1, ancestors.size)
        assertEquals("NODE_DTC_P0301", ancestors.first().nodeId)
    }
}
