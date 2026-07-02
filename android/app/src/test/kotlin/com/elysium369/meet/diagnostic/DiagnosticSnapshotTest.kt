package com.elysium369.meet.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSnapshotTest {

    private val baseVehicleId = "vehicle_test_001"

    private fun realSnapshot(
        idSuffix: String = "1",
        vehicleId: String = baseVehicleId,
        createdAtMs: Long = 1000L,
        dtcs: List<String> = emptyList(),
        readiness: Map<String, Boolean> = emptyMap(),
        ecuVoltage: Double? = 14.2,
        rpm: Double? = 850.0,
        coolantTempC: Double? = 90.0
    ): DiagnosticSnapshot = DiagnosticSnapshot(
        id = "snap-$idSuffix",
        vehicleId = vehicleId,
        createdAtMs = createdAtMs,
        dtcsActive = dtcs,
        readiness = readiness,
        ecuVoltage = ecuVoltage,
        rpm = rpm,
        coolantTempC = coolantTempC,
        provenance = DiagnosticProvenance.Real
    )

    @Test
    fun `hash is deterministic for identical content`() {
        val a = realSnapshot(idSuffix = "a", createdAtMs = 5000L, dtcs = listOf("P0301", "P0420"))
        val b = realSnapshot(idSuffix = "b", createdAtMs = 5000L, dtcs = listOf("P0420", "P0301"))
        // Sort order shouldn't matter — same hash.
        assertEquals(a.hashSha256, b.hashSha256)
    }

    @Test
    fun `hash differs when DTCs differ`() {
        val a = realSnapshot(dtcs = listOf("P0301"))
        val b = realSnapshot(dtcs = listOf("P0420"))
        assertNotEquals(a.hashSha256, b.hashSha256)
    }

    @Test
    fun `hash differs when createdAtMs differs`() {
        val a = realSnapshot(createdAtMs = 1000L)
        val b = realSnapshot(createdAtMs = 2000L)
        assertNotEquals(a.hashSha256, b.hashSha256)
    }

    @Test
    fun `empty factory produces valid snapshot`() {
        val snap = DiagnosticSnapshot.empty(vehicleId = baseVehicleId)
        assertEquals(baseVehicleId, snap.vehicleId)
        assertNotNull(snap.id)
        assertTrue(snap.hashSha256.isNotEmpty())
    }

    @Test
    fun `summary includes DTCs and provenance`() {
        val snap = realSnapshot(dtcs = listOf("P0301", "P0420"))
        assertTrue(snap.summary.contains("active=2"))
        assertTrue(snap.summary.contains("REAL"))
    }

    @Test
    fun `blank id rejected`() {
        try {
            DiagnosticSnapshot(
                id = "",
                vehicleId = baseVehicleId,
                createdAtMs = 1L,
                provenance = DiagnosticProvenance.Real
            )
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `blank vehicleId rejected`() {
        try {
            DiagnosticSnapshot(
                id = "snap-1",
                vehicleId = "",
                createdAtMs = 1L,
                provenance = DiagnosticProvenance.Real
            )
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }
}

class BeforeAfterComparatorTest {

    private val vehicleId = "vehicle_test_001"

    private fun snap(
        suffix: String,
        createdAtMs: Long,
        dtcs: List<String> = emptyList(),
        readiness: Map<String, Boolean> = emptyMap(),
        ecuVoltage: Double? = 14.2,
        rpm: Double? = 850.0,
        coolantTempC: Double? = 90.0,
        fuelTrimStft: Double? = 0.0,
        fuelTrimLtft: Double? = 0.0,
        provenance: DiagnosticProvenance = DiagnosticProvenance.Real
    ): DiagnosticSnapshot = DiagnosticSnapshot(
        id = "snap-$suffix",
        vehicleId = vehicleId,
        createdAtMs = createdAtMs,
        dtcsActive = dtcs,
        readiness = readiness,
        ecuVoltage = ecuVoltage,
        rpm = rpm,
        coolantTempC = coolantTempC,
        fuelTrimStft = fuelTrimStft,
        fuelTrimLtft = fuelTrimLtft,
        provenance = provenance
    )

    @Test
    fun `canDeclareRepaired is FALSE by default even when DTCs cleared`() {
        val before = snap("a", 1000L, dtcs = listOf("P0301"))
        val after = snap("b", 2000L, dtcs = emptyList())
        val result = BeforeAfterComparator.compare(before, after)
        assertTrue("Cleared DTCs detected", result.clearedDtcs.contains("P0301"))
        // Even though DTC cleared, default must be FALSE (no readiness, no road test).
        assertFalse(result.canDeclareRepaired)
    }

    @Test
    fun `canDeclareRepaired only TRUE when ALL conditions met`() {
        val before = snap("a", 1000L,
            dtcs = listOf("P0301"),
            readiness = mapOf("misfire" to false, "fuel" to false))
        val after = snap("b", 2000L,
            dtcs = emptyList(),
            readiness = mapOf("misfire" to true, "fuel" to true))
        val result = BeforeAfterComparator.compare(
            before, after,
            roadTestPassed = true,
            freezeFrameConditionMet = true
        )
        assertTrue("All conditions met → should declare repaired",
            result.canDeclareRepaired)
        assertTrue(result.readinessCompleted)
    }

    @Test
    fun `regression detected when new DTC appears`() {
        val before = snap("a", 1000L, dtcs = listOf("P0301"))
        val after = snap("b", 2000L, dtcs = listOf("P0301", "P0420"))
        val result = BeforeAfterComparator.compare(before, after)
        assertEquals(ComparisonConclusion.REGRESSION, result.conclusion)
        assertTrue(result.newDtcs.contains("P0420"))
        assertFalse(result.canDeclareRepaired)
    }

    @Test
    fun `no change when same DTC remains`() {
        val before = snap("a", 1000L, dtcs = listOf("P0301"))
        val after = snap("b", 2000L, dtcs = listOf("P0301"))
        val result = BeforeAfterComparator.compare(before, after)
        assertEquals(ComparisonConclusion.NO_CHANGE, result.conclusion)
        assertFalse(result.canDeclareRepaired)
    }

    @Test
    fun `unverified when provenance not Real`() {
        val before = snap("a", 1000L, dtcs = listOf("P0301"),
            provenance = DiagnosticProvenance.SinEnlace)
        val after = snap("b", 2000L, dtcs = emptyList())
        val result = BeforeAfterComparator.compare(before, after)
        assertEquals(ComparisonConclusion.UNVERIFIED, result.conclusion)
        assertFalse(result.canDeclareRepaired)
    }

    @Test
    fun `canDeclareRepaired FALSE even with provenance if readiness not complete`() {
        val before = snap("a", 1000L, dtcs = listOf("P0301"))
        val after = snap("b", 2000L, dtcs = emptyList(),
            readiness = mapOf("misfire" to true, "fuel" to false))
        val result = BeforeAfterComparator.compare(
            before, after,
            roadTestPassed = true,
            freezeFrameConditionMet = true
        )
        assertFalse("Readiness not complete", result.canDeclareRepaired)
    }

    @Test
    fun `deltas computed correctly`() {
        val before = snap("a", 1000L, ecuVoltage = 13.8, coolantTempC = 85.0,
            fuelTrimStft = 5.0, fuelTrimLtft = 3.0)
        val after = snap("b", 2000L, ecuVoltage = 14.4, coolantTempC = 92.0,
            fuelTrimStft = -2.0, fuelTrimLtft = 1.0)
        val result = BeforeAfterComparator.compare(before, after)
        assertEquals(0.6, result.voltageDelta!!, 0.001)
        assertEquals(7.0, result.coolantTempDelta!!, 0.001)
        assertEquals(-7.0, result.stftDelta!!, 0.001)
        assertEquals(-2.0, result.ltftDelta!!, 0.001)
    }
}