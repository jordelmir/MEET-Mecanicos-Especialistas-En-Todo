package com.elysium369.meet.core.humanity

import com.elysium369.meet.core.humanity.parity.HumanityParityEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class HumanityParityEngineTest {

    @Test
    fun `canonical evidence hashing produces stable sha256`() {
        val evidence = EvidenceItem(
            id = "evi_101",
            userId = "usr_42",
            skillId = "automotive.measure_voltage",
            missionId = "mission.battery_test_multimeter",
            evidenceType = EvidenceType.SIMULATION,
            executionTruth = ExecutionTruthState.SIMULATED,
            evidencePayloadHash = "abcdef0123456789",
        )

        val canonical = HumanityParityEngine.canonicalEvidenceString(evidence)
        val hash = HumanityParityEngine.sha256Hex(canonical)

        assertEquals("EVIDENCE_V1|usr_42|automotive.measure_voltage|mission.battery_test_multimeter|SIMULATION|SIMULATED|abcdef0123456789", canonical)
        assertEquals(64, hash.length)
    }

    @Test
    fun `canonical capability record hashing produces stable sha256`() {
        val record = CapabilityRecord(
            userId = "usr_42",
            skillId = "automotive.scan_dtc",
            currentLevel = CapabilityLevel.L5_DEMONSTRATED,
            demonstratedEvidenceCount = 5,
            lastDemonstratedEpochMs = 1700000000000L,
            verifiedByExpert = true,
        )

        val canonical = HumanityParityEngine.canonicalCapabilityString(record)
        val hash = HumanityParityEngine.sha256Hex(canonical)

        assertEquals("CAPABILITY_V1|usr_42|automotive.scan_dtc|L5_DEMONSTRATED|5|1", canonical)
        assertEquals(64, hash.length)
    }
}
