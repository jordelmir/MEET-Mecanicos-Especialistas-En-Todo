package com.elysium369.meet.mobility

import com.elysium369.meet.mobility.domain.guest.E164PhoneNumber
import com.elysium369.meet.mobility.domain.guest.GuestRideProfile
import com.elysium369.meet.mobility.domain.guest.MaskedCommunicationSession
import com.elysium369.meet.mobility.domain.models.MarketId
import com.elysium369.meet.mobility.domain.reserve.ScheduledReservationState
import com.elysium369.meet.mobility.domain.reserve.ScheduledRidePolicy
import com.elysium369.meet.mobility.domain.reserve.ScheduledRideReservation
import com.elysium369.meet.mobility.domain.routing.GeoCoordinate
import com.elysium369.meet.mobility.domain.safety.EmergencyContact
import com.elysium369.meet.mobility.domain.safety.EmergencySosEvent
import com.elysium369.meet.mobility.domain.safety.EmergencySosState
import com.elysium369.meet.mobility.domain.safety.EmergencySosType
import com.elysium369.meet.mobility.domain.safety.RiskSeverity
import com.elysium369.meet.mobility.domain.safety.RouteDeviationRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class MobilitySafetyAndReserveTest {

    private val policy = ScheduledRidePolicy(
        marketId = MarketId("CR_SJO"),
        minLeadTimeMinutes = 30L,
        maxLeadTimeDays = 30L,
        dispatchLeadTimeMinutes = 25L,
        cancellationFreeWindowMinutes = 60L
    )

    @Test
    fun policyLeadTimeValidation() {
        val now = Instant.parse("2026-09-06T12:00:00Z")

        // 1. Too soon (10 minutes in advance) -> not eligible
        val tooSoon = now.plus(Duration.ofMinutes(10))
        assertFalse(policy.isEligibleBookingTime(now, tooSoon))

        // 2. Exactly at minimum boundary (30 minutes in advance) -> eligible
        val exactMin = now.plus(Duration.ofMinutes(30))
        assertTrue(policy.isEligibleBookingTime(now, exactMin))

        // 3. Normal booking (2 hours in advance) -> eligible
        val normal = now.plus(Duration.ofHours(2))
        assertTrue(policy.isEligibleBookingTime(now, normal))

        // 4. Too far in advance (31 days in advance) -> not eligible
        val tooFar = now.plus(Duration.ofDays(31))
        assertFalse(policy.isEligibleBookingTime(now, tooFar))
    }

    @Test
    fun policyDispatchTimeAndCancellationLogic() {
        val now = Instant.parse("2026-09-06T12:00:00Z")
        val pickup = now.plus(Duration.ofHours(2)) // 14:00

        // Dispatch time should be 25 minutes prior to pickup (13:35)
        val expectedDispatch = pickup.minus(Duration.ofMinutes(25))
        assertEquals(expectedDispatch, policy.calculateDispatchTime(pickup))

        // Cancellation 90 minutes before pickup (> 60 min threshold) -> Free cancellation
        val cancelEarly = pickup.minus(Duration.ofMinutes(90))
        assertTrue(policy.isFreeCancellation(cancelEarly, pickup))

        // Cancellation 30 minutes before pickup (< 60 min threshold) -> Fee applies
        val cancelLate = pickup.minus(Duration.ofMinutes(30))
        assertFalse(policy.isFreeCancellation(cancelLate, pickup))
    }

    @Test
    fun e164PhoneNumberValidation() {
        // Valid E.164 formats
        val phoneCr = E164PhoneNumber.of("+50688889999")
        assertEquals("+50688889999", phoneCr.value)

        val phoneUs = E164PhoneNumber.of("+14155552671")
        assertEquals("+14155552671", phoneUs.value)

        val phoneMx = E164PhoneNumber.of("+5215512345678")
        assertEquals("+5215512345678", phoneMx.value)

        // Invalid formats rejected
        assertThrows(IllegalArgumentException::class.java) {
            E164PhoneNumber.of("88889999") // Missing '+'
        }
        assertThrows(IllegalArgumentException::class.java) {
            E164PhoneNumber.of("+0123456") // Starting with 0
        }
        assertThrows(IllegalArgumentException::class.java) {
            E164PhoneNumber.of("+123456") // Too short (< 8 digits)
        }
        assertThrows(IllegalArgumentException::class.java) {
            E164PhoneNumber.of("+1234567890123456") // Too long (> 15 digits)
        }
        assertThrows(IllegalArgumentException::class.java) {
            E164PhoneNumber.of("+506-8888-9999") // Contains dashes
        }
        assertThrows(IllegalArgumentException::class.java) {
            E164PhoneNumber.of("+506 8888 9999") // Contains spaces
        }
    }

    @Test
    fun guestRideProfileAndMaskedChannelIntegrity() {
        val guestPhone = E164PhoneNumber.of("+50687654321")
        val proxyPhone = E164PhoneNumber.of("+50640008888")

        val guestProfile = GuestRideProfile(
            guestRideId = UUID.randomUUID(),
            rideRequestId = UUID.randomUUID(),
            requestedByRiderId = UUID.randomUUID(),
            guestName = "Don Roberto Soto",
            guestPhone = guestPhone,
            smsNotificationsEnabled = true,
            trackingToken = "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0",
            createdAt = Instant.now()
        )

        assertEquals("Don Roberto Soto", guestProfile.guestName)
        assertEquals("+50687654321", guestProfile.guestPhone.value)
        assertEquals(64, guestProfile.trackingToken.length)

        val maskedSession = MaskedCommunicationSession(
            sessionId = UUID.randomUUID(),
            tripId = UUID.randomUUID(),
            riderId = UUID.randomUUID(),
            driverId = UUID.randomUUID(),
            virtualProxyNumber = proxyPhone.value,
            isActive = true,
            expiresAt = Instant.now().plus(Duration.ofHours(3)),
            createdAt = Instant.now()
        )

        assertTrue(maskedSession.isActive)
        assertEquals("+50640008888", maskedSession.virtualProxyNumber)
    }

    @Test
    fun emergencyContactAndSosEventLogic() {
        val contact = EmergencyContact(
            contactId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            name = "Hermano Mayor",
            phone = E164PhoneNumber.of("+50683332211"),
            notifyOnSos = true,
            notifyOnNightTrips = false
        )

        assertTrue(contact.notifyOnSos)
        assertFalse(contact.notifyOnNightTrips)

        val coord = GeoCoordinate(9.9333, -84.0833)
        val sosEvent = EmergencySosEvent(
            eventId = UUID.randomUUID(),
            tripId = UUID.randomUUID(),
            triggeredBy = UUID.randomUUID(),
            eventType = EmergencySosType.SOS_BUTTON,
            coordinate = coord,
            speedMps = 14.2f,
            state = EmergencySosState.TRIGGERED,
            createdAt = Instant.now()
        )

        assertEquals(EmergencySosType.SOS_BUTTON, sosEvent.eventType)
        assertEquals(EmergencySosState.TRIGGERED, sosEvent.state)
        assertEquals(9.9333, sosEvent.coordinate.latitude, 0.0001)
    }

    @Test
    fun routeDeviationAnomalyDetection() {
        val tripId = UUID.randomUUID()
        val coord = GeoCoordinate(9.9333, -84.0833)

        // 1. Normal route position (100m away, threshold 500m)
        val normalLog = RouteDeviationRecord(
            logId = UUID.randomUUID(),
            tripId = tripId,
            distanceFromRouteMeters = 100.0,
            thresholdMeters = 500.0,
            currentCoordinate = coord,
            recordedAt = Instant.now()
        )
        assertFalse(normalLog.isAnomalous)
        assertFalse(normalLog.isSevereAnomaly)

        // 2. Minor deviation (650m away, threshold 500m)
        val minorDev = RouteDeviationRecord(
            logId = UUID.randomUUID(),
            tripId = tripId,
            distanceFromRouteMeters = 650.0,
            thresholdMeters = 500.0,
            currentCoordinate = coord,
            recordedAt = Instant.now()
        )
        assertTrue(minorDev.isAnomalous)
        assertFalse(minorDev.isSevereAnomaly)

        // 3. Severe deviation (1800m away, threshold 500m, severe > 1500m)
        val severeDev = RouteDeviationRecord(
            logId = UUID.randomUUID(),
            tripId = tripId,
            distanceFromRouteMeters = 1800.0,
            thresholdMeters = 500.0,
            currentCoordinate = coord,
            recordedAt = Instant.now()
        )
        assertTrue(severeDev.isAnomalous)
        assertTrue(severeDev.isSevereAnomaly)
    }
}
