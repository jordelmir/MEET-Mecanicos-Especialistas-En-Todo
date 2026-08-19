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
        val raterId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val txId = UUID.randomUUID()
        val workOrderId = UUID.randomUUID()

        val proof = VerifiedTransactionProof(
            transactionId = txId,
            workOrderId = workOrderId,
            raterProfileId = raterId,
            ratedProviderId = providerId,
            completedAtEpochMs = System.currentTimeMillis() - 10000L,
            serverSignature = "sig_hmac_sha256_verified_12345",
        )

        val submission = ServiceRatingSubmission(
            transactionProof = proof,
            raterProfileId = raterId,
            ratedProviderId = providerId,
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
        )

        assertTrue(submission.weightedScore >= 4.7)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectRatingWhenRaterMismatchesProof() {
        val raterId = UUID.randomUUID()
        val imposterId = UUID.randomUUID()
        val providerId = UUID.randomUUID()

        val proof = VerifiedTransactionProof(
            transactionId = UUID.randomUUID(),
            workOrderId = UUID.randomUUID(),
            raterProfileId = raterId,
            ratedProviderId = providerId,
            completedAtEpochMs = System.currentTimeMillis(),
            serverSignature = "sig_valid",
        )

        ServiceRatingSubmission(
            transactionProof = proof,
            raterProfileId = imposterId, // Mismatch!
            ratedProviderId = providerId,
            raterRole = ServiceRole.CUSTOMER,
            serviceVertical = ServiceVertical.WORKSHOP,
            dimensionalScores = mapOf(RatingDimension.TECHNICAL_QUALITY to 5),
            comment = "Intento no autorizado",
        )
    }
}
