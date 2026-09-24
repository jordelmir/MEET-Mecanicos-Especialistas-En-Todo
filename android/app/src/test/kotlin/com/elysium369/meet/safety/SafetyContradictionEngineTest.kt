package com.elysium369.meet.safety

import com.elysium369.meet.safety.domain.ContradictionInput
import com.elysium369.meet.safety.domain.ContradictionType
import com.elysium369.meet.safety.domain.SafetyContradictionEngine
import org.junit.Assert.*
import org.junit.Test

class SafetyContradictionEngineTest {

    @Test
    fun `Two claims for same subject at same time 100km apart gives TEMPORAL_IMPOSSIBILITY`() {
        val claim1 = ContradictionInput(
            claimId = "c1",
            subjectRef = "sub1",
            occurredAtEpochMs = 1000L,
            endedAtEpochMs = 2000L,
            latitude = 0.0,
            longitude = 0.0
        )
        val claim2 = ContradictionInput(
            claimId = "c2",
            subjectRef = "sub1",
            occurredAtEpochMs = 1000L,
            endedAtEpochMs = 2000L,
            latitude = 1.0,
            longitude = 0.0
        )
        val results = SafetyContradictionEngine.detect(listOf(claim1, claim2))
        assertEquals(1, results.size)
        assertEquals(ContradictionType.TEMPORAL_IMPOSSIBILITY, results.first().type)
    }

    @Test
    fun `Two claims for same subject at same time same location gives no contradiction`() {
        val claim1 = ContradictionInput(
            claimId = "c1",
            subjectRef = "sub1",
            occurredAtEpochMs = 1000L,
            endedAtEpochMs = 2000L,
            latitude = 0.0,
            longitude = 0.0
        )
        val claim2 = ContradictionInput(
            claimId = "c2",
            subjectRef = "sub1",
            occurredAtEpochMs = 1000L,
            endedAtEpochMs = 2000L,
            latitude = 0.0,
            longitude = 0.0
        )
        val results = SafetyContradictionEngine.detect(listOf(claim1, claim2))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `Two claims for same subject 1 hour apart 2000km apart gives LOCATION_IMPOSSIBILITY`() {
        val claim1 = ContradictionInput(
            claimId = "c1",
            subjectRef = "sub1",
            occurredAtEpochMs = 1000L,
            endedAtEpochMs = 1000L,
            latitude = 0.0,
            longitude = 0.0
        )
        val claim2 = ContradictionInput(
            claimId = "c2",
            subjectRef = "sub1",
            occurredAtEpochMs = 3601000L,
            endedAtEpochMs = 3601000L,
            latitude = 20.0,
            longitude = 0.0
        )
        val results = SafetyContradictionEngine.detect(listOf(claim1, claim2))
        assertEquals(1, results.size)
        assertEquals(ContradictionType.LOCATION_IMPOSSIBILITY, results.first().type)
    }

    @Test
    fun `Two claims for different subjects gives no contradiction`() {
        val claim1 = ContradictionInput(
            claimId = "c1",
            subjectRef = "sub1",
            occurredAtEpochMs = 1000L,
            endedAtEpochMs = 2000L,
            latitude = 0.0,
            longitude = 0.0
        )
        val claim2 = ContradictionInput(
            claimId = "c2",
            subjectRef = "sub2",
            occurredAtEpochMs = 1000L,
            endedAtEpochMs = 2000L,
            latitude = 20.0,
            longitude = 0.0
        )
        val results = SafetyContradictionEngine.detect(listOf(claim1, claim2))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `Claims without location gives no location contradiction`() {
        val claim1 = ContradictionInput(
            claimId = "c1",
            subjectRef = "sub1",
            occurredAtEpochMs = 1000L,
            endedAtEpochMs = 2000L,
            latitude = null,
            longitude = null
        )
        val claim2 = ContradictionInput(
            claimId = "c2",
            subjectRef = "sub1",
            occurredAtEpochMs = 1000L,
            endedAtEpochMs = 2000L,
            latitude = null,
            longitude = null
        )
        val results = SafetyContradictionEngine.detect(listOf(claim1, claim2))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `Claims without time gives no temporal contradiction`() {
        val claim1 = ContradictionInput(
            claimId = "c1",
            subjectRef = "sub1",
            occurredAtEpochMs = null,
            endedAtEpochMs = null,
            latitude = 0.0,
            longitude = 0.0
        )
        val claim2 = ContradictionInput(
            claimId = "c2",
            subjectRef = "sub1",
            occurredAtEpochMs = null,
            endedAtEpochMs = null,
            latitude = 10.0,
            longitude = 0.0
        )
        val results = SafetyContradictionEngine.detect(listOf(claim1, claim2))
        assertTrue(results.isEmpty())
    }
}
