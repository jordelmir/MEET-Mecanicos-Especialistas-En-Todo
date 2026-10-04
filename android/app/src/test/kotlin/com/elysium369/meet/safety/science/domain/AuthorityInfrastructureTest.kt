package com.elysium369.meet.safety.science.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 7 — Temporal integrity tests.
 * Phase 8 — Source lineage independence tests.
 * Phase 15 — Publication authority tests.
 */
class AuthorityInfrastructureTest {

    // ── Phase 7: Temporal Integrity ─────────────────────────────

    @Test
    fun `consistent timestamps within tolerance`() {
        val result = TemporalIntegrityAnalyzer.analyze(
            deviceCapturedAt = 1000000L,
            serverReceivedAt = 1000500L,
        )
        assertEquals(TemporalState.CONSISTENT, result.temporalState)
        assertEquals(-500L, result.clockSkewMs)
    }

    @Test
    fun `missing device time is flagged`() {
        val result = TemporalIntegrityAnalyzer.analyze(
            deviceCapturedAt = null,
            serverReceivedAt = 1000000L,
        )
        assertEquals(TemporalState.MISSING_DEVICE_TIME, result.temporalState)
        assertNull(result.clockSkewMs)
    }

    @Test
    fun `future device time is flagged`() {
        val serverTime = 1000000L
        val futureDevice = serverTime + (10 * 60 * 1000L) // 10 min ahead
        val result = TemporalIntegrityAnalyzer.analyze(
            deviceCapturedAt = futureDevice,
            serverReceivedAt = serverTime,
        )
        assertEquals(TemporalState.FUTURE_DEVICE_TIME, result.temporalState)
    }

    @Test
    fun `clock skew beyond tolerance is flagged`() {
        val serverTime = 1000000L
        val pastDevice = serverTime - (10 * 60 * 1000L) // 10 min behind
        val result = TemporalIntegrityAnalyzer.analyze(
            deviceCapturedAt = pastDevice,
            serverReceivedAt = serverTime,
        )
        assertEquals(TemporalState.CLOCK_SKEW, result.temporalState)
    }

    @Test
    fun `exactly at tolerance boundary is consistent`() {
        val serverTime = 1000000L
        val result = TemporalIntegrityAnalyzer.analyze(
            deviceCapturedAt = serverTime + TemporalIntegrityAnalyzer.MAX_ACCEPTABLE_SKEW_MS,
            serverReceivedAt = serverTime,
        )
        assertEquals(TemporalState.CONSISTENT, result.temporalState)
    }

    // ── Phase 8: Source Lineage Independence ────────────────────

    @Test
    fun `ten copies of one source equals one independent source`() {
        val sources = (1..10).map { i ->
            SourceWithLineage(
                sourceId = "instance-$i",
                sourceLineageId = "reuters-article-42",
                derivationType = SourceDerivationType.REPUBLICATION,
            )
        }
        val result = SourceLineageAnalyzer.countIndependentSources(sources)
        assertEquals(
            "10 copies of Reuters article = 1 independent source",
            1, result.independentSources,
        )
        assertEquals(10, result.totalInstances)
        assertEquals(1, result.duplicateGroupCount)
    }

    @Test
    fun `three independent acquisitions count as three sources`() {
        val sources = listOf(
            SourceWithLineage("s1", "lineage-A", SourceDerivationType.INDEPENDENT_ACQUISITION),
            SourceWithLineage("s2", "lineage-B", SourceDerivationType.INDEPENDENT_ACQUISITION),
            SourceWithLineage("s3", "lineage-C", SourceDerivationType.INDEPENDENT_ACQUISITION),
        )
        val result = SourceLineageAnalyzer.countIndependentSources(sources)
        assertEquals(3, result.independentSources)
        assertEquals(0, result.duplicateGroupCount)
    }

    @Test
    fun `mixed independent and derived sources counted correctly`() {
        val sources = listOf(
            SourceWithLineage("s1", "lineage-A", SourceDerivationType.ORIGINAL),
            SourceWithLineage("s2", "lineage-A", SourceDerivationType.REPUBLICATION),
            SourceWithLineage("s3", "lineage-A", SourceDerivationType.DERIVED),
            SourceWithLineage("s4", "lineage-B", SourceDerivationType.INDEPENDENT_ACQUISITION),
            SourceWithLineage("s5", "lineage-C", SourceDerivationType.ORIGINAL),
        )
        val result = SourceLineageAnalyzer.countIndependentSources(sources)
        assertEquals(3, result.independentSources) // A, B, C
        assertEquals(5, result.totalInstances)
        assertEquals(1, result.duplicateGroupCount) // only lineage-A has dups
    }

    @Test
    fun `empty sources gives zero`() {
        val result = SourceLineageAnalyzer.countIndependentSources(emptyList())
        assertEquals(0, result.independentSources)
        assertEquals(0, result.totalInstances)
    }

    // ── Phase 15: Publication Authority ─────────────────────────

    @Test
    fun `valid publication flow transitions`() {
        assertNull(PublicationAuthorityValidator.validateTransition(
            PublicationState.DRAFT, PublicationState.UNDER_REVIEW, null, null, null,
        ))
        assertNull(PublicationAuthorityValidator.validateTransition(
            PublicationState.UNDER_REVIEW, PublicationState.PEER_REVIEWED, "r1", null, null,
        ))
        assertNull(PublicationAuthorityValidator.validateTransition(
            PublicationState.PEER_REVIEWED, PublicationState.PUBLICATION_ELIGIBLE, null, null, null,
        ))
        assertNull(PublicationAuthorityValidator.validateTransition(
            PublicationState.PUBLICATION_ELIGIBLE, PublicationState.PUBLISHED, null, "p1", null,
        ))
    }

    @Test
    fun `retracted is terminal`() {
        val error = PublicationAuthorityValidator.validateTransition(
            PublicationState.RETRACTED, PublicationState.PUBLISHED, null, null, null,
        )
        assertNotNull("Retracted must be terminal", error)
        assertTrue(error!!.contains("ILLEGAL_PUBLICATION_TRANSITION"))
    }

    @Test
    fun `same reviewer is blocked`() {
        val error = PublicationAuthorityValidator.validateTransition(
            PublicationState.UNDER_REVIEW,
            PublicationState.PEER_REVIEWED,
            reviewerId = "reviewer-1",
            publisherId = null,
            firstReviewerId = "reviewer-1",
        )
        assertNotNull("Same reviewer must be blocked", error)
        assertTrue(error!!.contains("SAME_REVIEWER"))
    }

    @Test
    fun `different reviewers are allowed`() {
        val error = PublicationAuthorityValidator.validateTransition(
            PublicationState.UNDER_REVIEW,
            PublicationState.PEER_REVIEWED,
            reviewerId = "reviewer-2",
            publisherId = null,
            firstReviewerId = "reviewer-1",
        )
        assertNull(error)
    }

    @Test
    fun `publisher cannot be reviewer`() {
        val error = PublicationAuthorityValidator.validateTransition(
            PublicationState.PUBLICATION_ELIGIBLE,
            PublicationState.PUBLISHED,
            reviewerId = "person-A",
            publisherId = "person-A",
            firstReviewerId = null,
        )
        assertNotNull("Publisher must differ from reviewer", error)
        assertTrue(error!!.contains("PUBLISHER_IS_REVIEWER"))
    }

    @Test
    fun `publisher different from reviewer is allowed`() {
        val error = PublicationAuthorityValidator.validateTransition(
            PublicationState.PUBLICATION_ELIGIBLE,
            PublicationState.PUBLISHED,
            reviewerId = "person-A",
            publisherId = "person-B",
            firstReviewerId = null,
        )
        assertNull(error)
    }

    @Test
    fun `cannot skip from DRAFT to PUBLISHED`() {
        val error = PublicationAuthorityValidator.validateTransition(
            PublicationState.DRAFT, PublicationState.PUBLISHED, null, null, null,
        )
        assertNotNull(error)
        assertTrue(error!!.contains("ILLEGAL_PUBLICATION_TRANSITION"))
    }

    @Test
    fun `correction is allowed from PUBLISHED`() {
        assertNull(PublicationAuthorityValidator.validateTransition(
            PublicationState.PUBLISHED, PublicationState.CORRECTED, null, null, null,
        ))
    }

    @Test
    fun `retraction is allowed from PUBLISHED`() {
        assertNull(PublicationAuthorityValidator.validateTransition(
            PublicationState.PUBLISHED, PublicationState.RETRACTED, null, null, null,
        ))
    }
}
