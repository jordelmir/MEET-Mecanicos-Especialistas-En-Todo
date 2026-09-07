package com.elysium369.meet.core.diagnostic

import org.junit.Assert.*
import org.junit.Test

class DiagnosticWisdomEngineTest {
    @Test fun `add and find solutions`() {
        val e = DiagnosticWisdomEngine()
        e.addSolution("P0301", "Misfire cyl 1", "Cambiar bujía", "Bujía defectuosa", contributorId = "m1", contributorName = "Carlos")
        val sols = e.findSolutions("P0301")
        assertEquals(1, sols.size)
        assertEquals("Cambiar bujía", sols[0].title)
    }
    @Test fun `outcome feedback updates success rate`() {
        val e = DiagnosticWisdomEngine()
        val sol = e.addSolution("P0301", "Misfire", "Fix", "Desc", contributorId = "m1", contributorName = "C")
        e.recordOutcome(sol.solutionId, "m2", "v1", "P0301", success = true, dtcClearedAfterRepair = true)
        e.recordOutcome(sol.solutionId, "m3", "v2", "P0301", success = true, dtcClearedAfterRepair = true)
        e.recordOutcome(sol.solutionId, "m4", "v3", "P0301", success = false, dtcClearedAfterRepair = false)
        val updated = e.findSolutions("P0301").first()
        assertEquals(3, updated.totalAttempts)
        assertTrue(updated.successRate > 0.6)
    }
    @Test fun `solutions ranked by relevance`() {
        val e = DiagnosticWisdomEngine()
        val s1 = e.addSolution("P0301", "Misfire", "Bad fix", "Desc", contributorId = "m1", contributorName = "A")
        val s2 = e.addSolution("P0301", "Misfire", "Good fix", "Desc", contributorId = "m2", contributorName = "B")
        repeat(5) { e.recordOutcome(s2.solutionId, "mx", "vx", "P0301", true, true) }
        repeat(5) { e.recordOutcome(s1.solutionId, "mx", "vx", "P0301", false, false) }
        val ranked = e.findSolutions("P0301")
        assertEquals("Good fix", ranked.first().title)
    }
    @Test fun `vehicle-specific filtering`() {
        val e = DiagnosticWisdomEngine()
        e.addSolution("P0301", "Misfire", "Toyota fix", "Desc", contributorId = "m1", contributorName = "C", vehicleBrand = "Toyota", vehicleModel = "Corolla")
        e.addSolution("P0301", "Misfire", "Honda fix", "Desc", contributorId = "m2", contributorName = "D", vehicleBrand = "Honda", vehicleModel = "Civic")
        val toyota = e.findSolutionsForVehicle("P0301", brand = "Toyota", model = "Corolla")
        assertEquals(1, toyota.size)
        assertEquals("Toyota fix", toyota[0].title)
    }
    @Test fun `upvote and downvote`() {
        val e = DiagnosticWisdomEngine()
        val sol = e.addSolution("P0301", "Misfire", "Fix", "Desc", contributorId = "m1", contributorName = "C")
        assertTrue(e.upvote(sol.solutionId))
        assertTrue(e.upvote(sol.solutionId))
        assertTrue(e.downvote(sol.solutionId))
        val updated = e.findSolutions("P0301").first()
        assertEquals(2, updated.upvotes)
        assertEquals(1, updated.downvotes)
    }
    @Test fun `contributor rank progression`() {
        val e = DiagnosticWisdomEngine()
        repeat(6) { e.addSolution("P030$it", "DTC", "Fix $it", "Desc", contributorId = "m1", contributorName = "C") }
        val stats = e.getContributorStats("m1")
        assertEquals(ContributorRank.PRACTITIONER, stats.rank)
    }
    @Test fun `difficulty labels in Spanish`() {
        assertEquals("Fácil (< 30 min)", Difficulty.EASY.displayLabel)
        assertEquals("Experto (equipo especializado)", Difficulty.EXPERT.displayLabel)
    }
}
