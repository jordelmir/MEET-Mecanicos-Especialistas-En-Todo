package com.elysium369.meet.domain.diagnostics

import com.elysium369.meet.data.local.entities.DiagnosticObservationEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FindingStateProjectorTest {
    @Test
    fun projectionIsDeterministicAfterRebuildFromAppendOnlyTimeline() {
        val timeline = listOf(
            observation("a", 100, 1, "OBSERVED"),
            observation("b", 200, 2, "NOT_OBSERVED_LAST_SCAN"),
            observation("c", 300, 3, "VERIFIED_RESOLVED"),
        )
        val before = FindingStateProjector.project(timeline)
        val rebuiltFromDifferentQueryOrder = FindingStateProjector.project(timeline.shuffled())
        assertEquals(before, rebuiltFromDifferentQueryOrder)
        assertEquals(FindingResolutionState.VERIFIED_RESOLVED, before.state)
        assertEquals(300L, before.resolvedAtMs)
    }

    private fun observation(
        id: String,
        observedAt: Long,
        sequence: Long,
        state: String,
    ) = DiagnosticObservationEntity(
        id = id,
        findingId = "finding-1",
        sessionId = "session-1",
        observedAt = observedAt,
        observationState = state,
        semantics = "SAE_ACTIVE_DTC",
        statusByte = null,
        sourceService = "03",
        exchangeId = null,
        rawPayloadHash = "hash-$id",
        sessionSequence = sequence,
        elapsedRealtimeNanos = sequence,
        previousObservationHash = "previous-$id",
        observationHash = "observation-$id",
    )
}
