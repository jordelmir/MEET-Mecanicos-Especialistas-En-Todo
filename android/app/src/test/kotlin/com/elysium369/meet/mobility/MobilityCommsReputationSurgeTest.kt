package com.elysium369.meet.mobility

import com.elysium369.meet.mobility.domain.chat.TripMessage
import com.elysium369.meet.mobility.domain.chat.TripMessageType
import com.elysium369.meet.mobility.domain.feedback.LostItemCase
import com.elysium369.meet.mobility.domain.feedback.LostItemCaseState
import com.elysium369.meet.mobility.domain.feedback.SupportCase
import com.elysium369.meet.mobility.domain.feedback.SupportCasePriority
import com.elysium369.meet.mobility.domain.feedback.SupportCaseState
import com.elysium369.meet.mobility.domain.feedback.TripRating
import com.elysium369.meet.mobility.domain.models.MarketId
import com.elysium369.meet.mobility.domain.policy.MarketPolicyConfig
import com.elysium369.meet.mobility.domain.policy.SurgeCalculation
import com.elysium369.meet.mobility.domain.pricing.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MobilityCommsReputationSurgeTest {

    @Test
    fun tripMessageValidation() {
        val tripId = UUID.randomUUID()
        val senderId = UUID.randomUUID()

        // Valid message
        val msg = TripMessage(
            messageId = UUID.randomUUID(),
            tripId = tripId,
            senderId = senderId,
            messageType = TripMessageType.TEXT,
            body = "Estoy afuera del edificio con sombrilla",
            createdAt = Instant.now()
        )
        assertEquals(TripMessageType.TEXT, msg.messageType)
        assertEquals("Estoy afuera del edificio con sombrilla", msg.body)

        // Blank body rejected
        assertThrows(IllegalArgumentException::class.java) {
            TripMessage(
                messageId = UUID.randomUUID(),
                tripId = tripId,
                senderId = senderId,
                messageType = TripMessageType.TEXT,
                body = "   ",
                createdAt = Instant.now()
            )
        }

        // Excessive length (> 1000 chars) rejected
        val longBody = "a".repeat(1001)
        assertThrows(IllegalArgumentException::class.java) {
            TripMessage(
                messageId = UUID.randomUUID(),
                tripId = tripId,
                senderId = senderId,
                messageType = TripMessageType.TEXT,
                body = longBody,
                createdAt = Instant.now()
            )
        }
    }

    @Test
    fun tripRatingValidation() {
        val tripId = UUID.randomUUID()
        val riderId = UUID.randomUUID()
        val driverId = UUID.randomUUID()

        // Valid rating
        val rating = TripRating(
            ratingId = UUID.randomUUID(),
            tripId = tripId,
            reviewerId = riderId,
            subjectId = driverId,
            rating = 5,
            comment = "Excelente viaje, muy seguro",
            createdAt = Instant.now()
        )
        assertEquals(5, rating.rating)

        // Self-rating rejected
        assertThrows(IllegalArgumentException::class.java) {
            TripRating(
                ratingId = UUID.randomUUID(),
                tripId = tripId,
                reviewerId = riderId,
                subjectId = riderId,
                rating = 5,
                createdAt = Instant.now()
            )
        }

        // Out of bounds rating (0 or 6 stars) rejected
        assertThrows(IllegalArgumentException::class.java) {
            TripRating(
                ratingId = UUID.randomUUID(),
                tripId = tripId,
                reviewerId = riderId,
                subjectId = driverId,
                rating = 0,
                createdAt = Instant.now()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TripRating(
                ratingId = UUID.randomUUID(),
                tripId = tripId,
                reviewerId = riderId,
                subjectId = driverId,
                rating = 6,
                createdAt = Instant.now()
            )
        }
    }

    @Test
    fun lostItemAndSupportCaseIntegrity() {
        val tripId = UUID.randomUUID()
        val riderId = UUID.randomUUID()
        val driverId = UUID.randomUUID()

        val lostCase = LostItemCase(
            caseId = UUID.randomUUID(),
            tripId = tripId,
            riderId = riderId,
            driverId = driverId,
            itemDescription = "Llavero con llaves de casa y control del portón",
            state = LostItemCaseState.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        assertEquals(LostItemCaseState.OPEN, lostCase.state)

        val supportCase = SupportCase(
            caseId = UUID.randomUUID(),
            userId = riderId,
            tripId = tripId,
            category = "BILLING_INQUIRY",
            priority = SupportCasePriority.HIGH,
            state = SupportCaseState.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        assertEquals(SupportCasePriority.HIGH, supportCase.priority)
        assertEquals(SupportCaseState.OPEN, supportCase.state)
    }

    @Test
    fun dynamicSurgeCalculationLogic() {
        val policy = MarketPolicyConfig(
            marketId = MarketId("CR_SJO"),
            driverLocationTtlSeconds = 30L,
            maxSearchRadiusMeters = 5000.0,
            minSurgeMultiplier = Rate(1L, 1L),
            maxSurgeMultiplier = Rate(3L, 1L),
            updatedAt = Instant.now()
        )

        val now = Instant.now()

        // 1. Ample supply (demand=5, supply=20) -> 1.0x (1/1)
        val calc1 = SurgeCalculation.calculate(5L, 20L, policy, now)
        assertEquals(1L, calc1.surgeMultiplier.numerator)
        assertEquals(1L, calc1.surgeMultiplier.denominator)

        // 2. 0 supply, demand=10 -> max surge (3/1)
        val calc2 = SurgeCalculation.calculate(10L, 0L, policy, now)
        assertEquals(3L, calc2.surgeMultiplier.numerator)
        assertEquals(1L, calc2.surgeMultiplier.denominator)

        // 3. Demand=10, supply=5 -> 2.0x (10/5)
        val calc3 = SurgeCalculation.calculate(10L, 5L, policy, now)
        assertEquals(10L, calc3.surgeMultiplier.numerator)
        assertEquals(5L, calc3.surgeMultiplier.denominator)
    }
}
