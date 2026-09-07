package com.elysium369.meet.core.trust

import org.junit.Assert.*
import org.junit.Test

class TrustGraphTest {

    @Test
    fun `direct trust from single verification`() {
        val graph = TrustGraph()
        graph.addTrust(TrustEdge("maria", "jose", "plumbing",
            TrustLevel.VERIFIED_OUTCOME, TrustReason.CUSTOMER_OF))
        val score = graph.trustScoreFor("jose", "plumbing")
        assertEquals(0.8, score.directTrust, 0.001)
        assertEquals(1, score.uniqueVouchers)
    }

    @Test
    fun `transitive trust decays by 50 percent`() {
        val graph = TrustGraph()
        // Carlos trusts María
        graph.addTrust(TrustEdge("carlos", "maria", "plumbing",
            TrustLevel.VERIFIED_OUTCOME, TrustReason.CUSTOMER_OF))
        // María trusts José
        graph.addTrust(TrustEdge("maria", "jose", "plumbing",
            TrustLevel.VERIFIED_OUTCOME, TrustReason.CUSTOMER_OF))
        // Carlos should have transitive trust in José
        val score = graph.trustScoreFor("jose", "plumbing")
        assertTrue(score.transitiveTrust > 0)
        assertTrue(score.transitiveTrust <= 0.5) // 50% decay
    }

    @Test
    fun `cannot trust yourself`() {
        val graph = TrustGraph()
        assertThrows(IllegalArgumentException::class.java) {
            graph.addTrust(TrustEdge("jose", "jose", "plumbing",
                TrustLevel.VOUCHED, TrustReason.PERSONAL_KNOWLEDGE))
        }
    }

    @Test
    fun `trust is domain specific`() {
        val graph = TrustGraph()
        graph.addTrust(TrustEdge("maria", "jose", "plumbing",
            TrustLevel.CERTIFIED, TrustReason.PEER_REVIEW))
        val plumbing = graph.directTrustFor("jose", "plumbing")
        val electrical = graph.directTrustFor("jose", "electrical")
        assertTrue(plumbing > 0)
        assertEquals(0.0, electrical, 0.001)
    }

    @Test
    fun `master apprentice has highest trust weight`() {
        val graph = TrustGraph()
        graph.addTrust(TrustEdge("master", "apprentice", "welding",
            TrustLevel.MASTER_APPRENTICE, TrustReason.TRAINED_BY))
        val score = graph.directTrustFor("apprentice", "welding")
        assertEquals(0.9, score, 0.001)
    }

    @Test
    fun `composite trust weighs direct 70 percent transitive 30 percent`() {
        val score = TrustScore("u1", "domain", directTrust = 1.0, transitiveTrust = 0.0)
        assertEquals(0.7, score.composite, 0.001)
        val score2 = TrustScore("u1", "domain", directTrust = 0.0, transitiveTrust = 1.0)
        assertEquals(0.3, score2.composite, 0.001)
    }

    @Test
    fun `human readable trust labels`() {
        // direct=0.9, transitive=0.0 → composite = 0.63 → "moderada"
        val moderate = TrustScore("u1", "d", directTrust = 0.9).humanReadable
        assertTrue(moderate.contains("moderada"))
        val none = TrustScore("u1", "d").humanReadable
        assertTrue(none.contains("Sin historial"))
    }

    @Test
    fun `vouchers returns all who trust a user`() {
        val graph = TrustGraph()
        graph.addTrust(TrustEdge("a", "target", "d", TrustLevel.VOUCHED, TrustReason.PERSONAL_KNOWLEDGE))
        graph.addTrust(TrustEdge("b", "target", "d", TrustLevel.WITNESSED, TrustReason.WITNESSED_WORK))
        assertEquals(2, graph.vouchersFor("target", "d").size)
    }

    @Test
    fun `all 5 trust levels exist`() {
        assertEquals(5, TrustLevel.entries.size)
    }

    @Test
    fun `all 8 trust reasons exist`() {
        assertEquals(8, TrustReason.entries.size)
    }
}
