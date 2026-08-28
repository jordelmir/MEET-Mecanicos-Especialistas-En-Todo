package com.elysium369.meet.legal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalVanguardV2Test {
    private val taxonomy = LegalTaxonomy("CR-2026.1", setOf("LABOR", "FAMILY", "CRIMINAL"))
    private val validTriage = LegalTriageResult(
        "LABOR", listOf("FAMILY"), .72, LegalUrgency.HUMAN_REVIEW,
        setOf("DEADLINE_REVIEW"), taxonomy.version,
    )
    private val approved = LegalExposureEligibility(true, LawyerCapabilityState.APPROVED, true, true, true)

    @Test fun aiTriageReturnsValidTaxonomyCode() = assertTrue(LegalTriagePolicy.validate(validTriage, taxonomy))

    @Test fun aiTriageCannotCreateUnknownCategory() = assertFalse(
        LegalTriagePolicy.validate(validTriage.copy(primaryCategoryCode = "INVENTED"), taxonomy),
    )

    @Test fun aiTriageIsNeverAuthoritative() = assertFalse(validTriage.isAuthoritative)

    @Test fun conflictRequiredBeforeFullDisclosure() {
        assertEquals(
            LegalDisclosureLevel.TRIAGE_ONLY,
            LegalExchangePolicy.disclosureLevel(LegalConflictDecision.PENDING, engaged = false),
        )
    }

    @Test fun conflictLawyerCannotSubmitOffer() = assertFalse(
        LegalExchangePolicy.maySubmitOffer(approved, LegalConflictDecision.CONFLICT),
    )

    @Test fun unverifiedLawyerCannotReceiveMatter() = assertFalse(
        LegalExchangePolicy.mayReceiveMatter(approved.copy(lawyerCapability = LawyerCapabilityState.UNVERIFIED)),
    )

    @Test fun ratingRequiresVerifiedTransaction() = assertFalse(
        ReputationPolicy.mayReview(true, VerifiedTransactionState.NOT_ELIGIBLE, 0),
    )

    @Test fun oneReviewPerParticipant() = assertFalse(
        ReputationPolicy.mayReview(true, VerifiedTransactionState.COMPLETED, 1),
    )

    @Test fun unknownRatingIsNotFiveStars() = assertNull(
        ReputationPolicy.bayesianRating(null, 0, 4.2, 10),
    )

    @Test fun bayesianRankingProtectsSmallSamples() {
        val small = ReputationPolicy.bayesianRating(5.0, 1, 4.0, 10)!!
        assertTrue(small < 4.2)
    }

    @Test fun metricsCannotBeClientMutated() = assertFalse(MetricsMutationPolicy.clientMayMutate())

    @Test fun onlyOneAcceptedLegalOffer() {
        val result = LegalOfferAcceptancePolicy.accept(setOf("offer-a", "offer-b", "offer-c"), "offer-b")
        assertEquals("offer-b", result.acceptedOfferId)
        assertEquals(setOf("offer-a", "offer-c"), result.rejectedOfferIds)
    }

    @Test fun acceptedOfferCreatesEngagement() {
        assertTrue(LegalOfferAcceptancePolicy.accept(setOf("offer-a"), "offer-a").engagementCreated)
    }

    @Test fun cancelledByCustomerNotAttributedToProvider() {
        assertEquals(0, PerformanceAttributionPolicy.providerCancellationIncrement(TransactionCancellationActor.CUSTOMER))
        assertEquals(1, PerformanceAttributionPolicy.providerCancellationIncrement(TransactionCancellationActor.PROVIDER))
    }

    @Test fun courtWaitNotAttributedToLawyer() {
        val hour = 3_600_000L
        val result = LegalCaseClock.professionalActionDuration(
            listOf(
                LegalCaseClockSegment(LegalCaseClockType.PROFESSIONAL_ACTION, 0, hour),
                LegalCaseClockSegment(LegalCaseClockType.COURT_WAIT, hour, hour * 50),
            ),
        )
        assertEquals(hour, result)
    }

    @Test fun professionalInactivityMetricUsesCorrectClock() {
        assertNull(LegalCaseClock.professionalInactivity(10_000, 1_000, LegalCaseClockType.COURT_WAIT))
        assertEquals(9_000L, LegalCaseClock.professionalInactivity(10_000, 1_000, null))
    }
}
