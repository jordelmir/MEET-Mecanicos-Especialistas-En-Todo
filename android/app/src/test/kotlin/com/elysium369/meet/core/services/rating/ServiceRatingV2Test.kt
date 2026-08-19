package com.elysium369.meet.core.services.rating

import com.elysium369.meet.core.services.kernel.ServiceRole
import com.elysium369.meet.core.services.kernel.ServiceVertical
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ServiceRatingV2Test {

    @Test
    fun testWeightedScoreCalculation() {
        val submission = ServiceRatingSubmission(
            transactionId = UUID.randomUUID(),
            workOrderId = UUID.randomUUID(),
            raterProfileId = UUID.randomUUID(),
            ratedProviderId = UUID.randomUUID(),
            raterRole = ServiceRole.CUSTOMER,
            serviceVertical = ServiceVertical.MOBILE_MECHANIC,
            dimensionalScores = mapOf(
                RatingDimension.TECHNICAL_QUALITY to 5,
                RatingDimension.COMEBACK_AVOIDANCE to 5,
                RatingDimension.QUOTE_ACCURACY to 4,
                RatingDimension.TIME_COMPLIANCE to 5,
                RatingDimension.COMMUNICATION to 4,
                RatingDimension.DOCUMENTATION_QUALITY to 5,
            ),
            comment = "Excelente servicio técnico y diagnóstico impecable",
            isVerifiedTransaction = true,
        )

        assertTrue(submission.weightedScore >= 4.7)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectRatingForUnverifiedTransaction() {
        ServiceRatingSubmission(
            transactionId = UUID.randomUUID(),
            workOrderId = null,
            raterProfileId = UUID.randomUUID(),
            ratedProviderId = UUID.randomUUID(),
            raterRole = ServiceRole.CUSTOMER,
            serviceVertical = ServiceVertical.WORKSHOP,
            dimensionalScores = mapOf(RatingDimension.TECHNICAL_QUALITY to 5),
            comment = "Falla",
            isVerifiedTransaction = false,
        )
    }
}
