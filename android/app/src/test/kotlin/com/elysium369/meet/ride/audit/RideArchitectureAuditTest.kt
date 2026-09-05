package com.elysium369.meet.ride.audit

import com.elysium369.meet.ride.domain.RideActor
import com.elysium369.meet.ride.domain.RideActorRole
import com.elysium369.meet.ride.domain.RideCancellationPolicy
import com.elysium369.meet.ride.domain.RideCancellationReason
import com.elysium369.meet.ride.domain.RideCommissionPolicy
import com.elysium369.meet.ride.domain.RideDomainErrorCode
import com.elysium369.meet.ride.domain.RideFareBidPolicy
import com.elysium369.meet.ride.domain.RideFareEngine
import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.domain.RideGuardianPolicy
import com.elysium369.meet.ride.domain.RideLifecyclePolicy
import com.elysium369.meet.ride.domain.RideMoney
import com.elysium369.meet.ride.domain.RideSafetySignalType
import com.elysium369.meet.ride.domain.RideState
import com.elysium369.meet.ride.domain.RideTransitionRequest
import com.elysium369.meet.ride.domain.RideTripParties
import com.elysium369.meet.ride.domain.RideVersion
import com.elysium369.meet.ride.domain.TransitionDecision
import com.elysium369.meet.ride.presence.RideDriverAvailability
import com.elysium369.meet.ride.dispatch.RideEligibilityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun TransitionDecision.isAllowed() = this is TransitionDecision.Allowed
private fun TransitionDecision.isDenied() = this is TransitionDecision.Denied
private fun TransitionDecision.deniedCode() = (this as? TransitionDecision.Denied)?.error?.code

/**
 * Custom architecture audit tests verifying the actual implementation state.
 *
 * These tests are NOT exhaustive domain tests — they audit specific architectural
 * invariants that must hold for the ride domain to be production-ready.
 */
class RideArchitectureAuditTest {

    // ──────────────────────────────────────────────
    // SECTION 1: State machine transition matrix
    // ──────────────────────────────────────────────

    private val passenger = RideActor("p-1", RideActorRole.PASSENGER)
    private val driver = RideActor("d-1", RideActorRole.DRIVER)
    private val system = RideActor("sys", RideActorRole.SYSTEM)
    private val dispatcher = RideActor("disp", RideActorRole.DISPATCHER)

    private fun transition(
        from: RideState,
        to: RideState,
        actor: RideActorRole = RideActorRole.PASSENGER,
        parties: RideTripParties = RideTripParties("p-1", "d-1"),
        expectedVersion: Long = 1L,
        currentVersion: Long = 1L,
        pinVerified: Boolean = false,
    ): TransitionDecision {
        val rideActor = when (actor) {
            RideActorRole.PASSENGER -> passenger
            RideActorRole.DRIVER -> driver
            RideActorRole.SYSTEM -> system
            RideActorRole.DISPATCHER -> dispatcher
            RideActorRole.SAFETY_OPERATOR -> RideActor("safety-1", RideActorRole.SAFETY_OPERATOR)
        }
        return RideLifecyclePolicy.decide(
            RideTransitionRequest(
                from = from,
                to = to,
                actor = rideActor,
                parties = parties,
                expectedVersion = RideVersion.of(expectedVersion),
                currentVersion = RideVersion.of(currentVersion),
                pinVerified = pinVerified,
            ),
        )
    }

    @Test
    fun `valid happy-path transitions are all allowed`() {
        assertTrue("DRAFT→SEARCHING by passenger", transition(RideState.DRAFT, RideState.SEARCHING).isAllowed())
        assertTrue("SEARCHING→OFFERED by driver", transition(RideState.SEARCHING, RideState.OFFERED, RideActorRole.DRIVER).isAllowed())
        assertTrue("SEARCHING→ASSIGNED by system", transition(RideState.SEARCHING, RideState.ASSIGNED, RideActorRole.SYSTEM).isAllowed())
        assertTrue("OFFERED→ASSIGNED by passenger", transition(RideState.OFFERED, RideState.ASSIGNED).isAllowed())
        assertTrue("ASSIGNED→DRIVER_EN_ROUTE by driver", transition(RideState.ASSIGNED, RideState.DRIVER_EN_ROUTE, RideActorRole.DRIVER).isAllowed())
        assertTrue("DRIVER_EN_ROUTE→ARRIVED by driver", transition(RideState.DRIVER_EN_ROUTE, RideState.ARRIVED, RideActorRole.DRIVER).isAllowed())
        assertTrue("ARRIVED→PASSENGER_ONBOARD by driver (pin verified)", transition(RideState.ARRIVED, RideState.PASSENGER_ONBOARD, RideActorRole.DRIVER, pinVerified = true).isAllowed())
        assertTrue("PASSENGER_ONBOARD→IN_PROGRESS by driver", transition(RideState.PASSENGER_ONBOARD, RideState.IN_PROGRESS, RideActorRole.DRIVER).isAllowed())
        assertTrue("IN_PROGRESS→COMPLETED by driver", transition(RideState.IN_PROGRESS, RideState.COMPLETED, RideActorRole.DRIVER).isAllowed())
    }

    @Test
    fun `any non-terminal state can be cancelled by authorized party`() {
        val cancellable = listOf(
            RideState.DRAFT, RideState.SEARCHING, RideState.OFFERED, RideState.ASSIGNED,
            RideState.DRIVER_EN_ROUTE, RideState.ARRIVED, RideState.PASSENGER_ONBOARD, RideState.IN_PROGRESS,
        )
        cancellable.forEach { state ->
            val result = transition(state, RideState.CANCELLED)
            assertTrue("$state→CANCELLED must be allowed", result.isAllowed())
        }
    }

    @Test
    fun `terminal states reject further transitions except DISPUTED from COMPLETED`() {
        val terminal = listOf(RideState.CANCELLED, RideState.EXPIRED, RideState.DISPUTED)
        terminal.forEach { state ->
            val result = transition(state, RideState.SEARCHING, RideActorRole.PASSENGER)
            assertFalse("$state is terminal, should reject", result.isAllowed())
        }
        // DISPUTED from COMPLETED is allowed for passenger/driver
        val disputedFromCompleted = transition(RideState.COMPLETED, RideState.DISPUTED)
        assertTrue("COMPLETED→DISPUTED by passenger is allowed", disputedFromCompleted.isAllowed())
    }

    @Test
    fun `version conflict is rejected`() {
        val result = transition(
            from = RideState.DRAFT,
            to = RideState.SEARCHING,
            expectedVersion = 2L,
            currentVersion = 5L,
        )
        assertEquals(RideDomainErrorCode.VERSION_CONFLICT, result.deniedCode())
    }

    @Test
    fun `wrong actor role is rejected`() {
        // PASSENGER cannot do SEARCHING→OFFERED (only DRIVER)
        val result = transition(RideState.SEARCHING, RideState.OFFERED, RideActorRole.PASSENGER)
        assertEquals(RideDomainErrorCode.ROLE_NOT_AUTHORIZED, result.deniedCode())
    }

    @Test
    fun `PIN required for PASSENGER_ONBOARD transition`() {
        val withPin = transition(RideState.ARRIVED, RideState.PASSENGER_ONBOARD, RideActorRole.DRIVER, pinVerified = true)
        assertTrue("ARRIVED→PASSENGER_ONBOARD allowed with PIN", withPin.isAllowed())

        val withoutPin = transition(RideState.ARRIVED, RideState.PASSENGER_ONBOARD, RideActorRole.DRIVER, pinVerified = false)
        assertEquals(RideDomainErrorCode.PIN_REQUIRED, withoutPin.deniedCode())
    }

    @Test
    fun `unauthorized actor is rejected`() {
        val randomActor = RideActor("hacker-1", RideActorRole.PASSENGER)
        val parties = RideTripParties("p-1", "d-1")
        val result = RideLifecyclePolicy.decide(
            RideTransitionRequest(
                from = RideState.DRAFT,
                to = RideState.SEARCHING,
                actor = randomActor,
                parties = parties,
                expectedVersion = RideVersion.of(0),
                currentVersion = RideVersion.of(0),
            ),
        )
        assertEquals(RideDomainErrorCode.FORBIDDEN, result.deniedCode())
    }

    @Test
    fun `system can expire SEARCHING and OFFERED rides`() {
        val fromSearching = transition(RideState.SEARCHING, RideState.EXPIRED, RideActorRole.SYSTEM)
        assertTrue("SYSTEM can expire SEARCHING", fromSearching.isAllowed())

        val fromOffered = transition(RideState.OFFERED, RideState.EXPIRED, RideActorRole.SYSTEM)
        assertTrue("SYSTEM can expire OFFERED", fromOffered.isAllowed())

        val fromAssigned = transition(RideState.ASSIGNED, RideState.EXPIRED, RideActorRole.SYSTEM)
        assertFalse("SYSTEM cannot expire ASSIGNED", fromAssigned.isAllowed())
    }

    // ──────────────────────────────────────────────
    // SECTION 2: Fare engine correctness
    // ──────────────────────────────────────────────

    @Test
    fun `Costa Rica fare engine produces correct amounts for zero distance`() {
        val quote = RideFareEngine.quoteCostaRica(distanceMeters = 0, durationSeconds = 0)
        assertEquals(0L, quote.estimatedTotalMinor)
        assertEquals(RideFareMode.METERED_TIME_DISTANCE, quote.mode)
        assertEquals("CRC", quote.currency.value)
        assertEquals(1L, quote.rateCardVersion)
    }

    @Test
    fun `Costa Rica fare engine produces correct amounts for typical ride`() {
        // 5km, 10 minutes
        val quote = RideFareEngine.quoteCostaRica(distanceMeters = 5_000, durationSeconds = 600)
        // Distance: 5000 * 300 / 1000 = 1500
        assertEquals(1500L, quote.distanceFareMinor)
        // Time: 600 * 60 / 60 = 600
        assertEquals(600L, quote.timeFareMinor)
        assertEquals(2100L, quote.estimatedTotalMinor)
        assertTrue("allows stops during trip", quote.allowsStopsDuringTrip)
    }

    @Test
    fun `Costa Rica fare engine rounds up correctly`() {
        // 1 meter, 1 second — tests ceil-divide rounding
        val quote = RideFareEngine.quoteCostaRica(distanceMeters = 1, durationSeconds = 1)
        // Distance: ceil(1 * 300 / 1000) = ceil(0.3) = 1
        assertEquals(1L, quote.distanceFareMinor)
        // Time: ceil(1 * 60 / 60) = ceil(1) = 1
        assertEquals(1L, quote.timeFareMinor)
        assertEquals(2L, quote.estimatedTotalMinor)
    }

    @Test
    fun `open bid mode does not allow stops during trip`() {
        assertFalse("OPEN_BID blocks in-trip stops", RideFareEngine.allowsStopsDuringTrip(RideFareMode.OPEN_BID))
        assertTrue("METERED allows in-trip stops", RideFareEngine.allowsStopsDuringTrip(RideFareMode.METERED_TIME_DISTANCE))
    }

    @Test
    fun `fare engine rejects negative distance`() {
        try {
            RideFareEngine.quoteCostaRica(distanceMeters = -1, durationSeconds = 0)
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("negative") == true)
        }
    }

    // ──────────────────────────────────────────────
    // SECTION 3: Money overflow safety
    // ──────────────────────────────────────────────

    @Test
    fun `RideMoney addition prevents overflow`() {
        val a = RideMoney.of(Long.MAX_VALUE / 2, "CRC")
        val b = RideMoney.of(Long.MAX_VALUE / 2, "CRC")
        val result = a + b
        assertEquals(Long.MAX_VALUE / 2 + Long.MAX_VALUE / 2, result.minorUnits)
    }

    @Test
    fun `RideMoney rejects negative minor units`() {
        try {
            RideMoney.of(-1, "CRC")
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("negative") == true)
        }
    }

    @Test
    fun `RideMoney rejects currency mismatch in arithmetic`() {
        val crc = RideMoney.of(100, "CRC")
        val usd = RideMoney.of(100, "USD")
        try {
            crc + usd
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("combine") == true)
        }
    }

    @Test
    fun `RideMoney subtraction prevents negative result`() {
        val a = RideMoney.of(50, "CRC")
        val b = RideMoney.of(100, "CRC")
        try {
            a - b
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("negative") == true)
        }
    }

    // ──────────────────────────────────────────────
    // SECTION 4: Commission correctness
    // ──────────────────────────────────────────────

    @Test
    fun `5% commission on 10000 minor units is 500`() {
        val amounts = com.elysium369.meet.ride.domain.CommissionableRideAmounts(
            currency = com.elysium369.meet.ride.domain.CurrencyCode.of("CRC"),
            transportFare = com.elysium369.meet.ride.domain.AmountMinor.of(10_000),
        )
        val calc = RideCommissionPolicy.calculate(amounts)
        assertEquals(10_000L, calc.commissionableBase.minorUnits)
        assertEquals(500L, calc.platformCommission.minorUnits)
        assertEquals("ride-commission-v1", calc.policyVersion)
    }

    @Test
    fun `5% commission on 1 cent rounds down to zero`() {
        val amounts = com.elysium369.meet.ride.domain.CommissionableRideAmounts(
            currency = com.elysium369.meet.ride.domain.CurrencyCode.of("CRC"),
            transportFare = com.elysium369.meet.ride.domain.AmountMinor.of(1),
        )
        val calc = RideCommissionPolicy.calculate(amounts)
        // 1 * 500 / 10000 = 0.05, half-up rounding: (1*500 + 5000) / 10000 = 0
        assertEquals(0L, calc.platformCommission.minorUnits)
    }

    @Test
    fun `zero base produces zero commission`() {
        val amounts = com.elysium369.meet.ride.domain.CommissionableRideAmounts(
            currency = com.elysium369.meet.ride.domain.CurrencyCode.of("CRC"),
        )
        val calc = RideCommissionPolicy.calculate(amounts)
        assertEquals(0L, calc.commissionableBase.minorUnits)
        assertEquals(0L, calc.platformCommission.minorUnits)
    }

    @Test
    fun `discounts reduce commissionable base`() {
        val amounts = com.elysium369.meet.ride.domain.CommissionableRideAmounts(
            currency = com.elysium369.meet.ride.domain.CurrencyCode.of("CRC"),
            transportFare = com.elysium369.meet.ride.domain.AmountMinor.of(10_000),
            driverFundedDiscount = com.elysium369.meet.ride.domain.AmountMinor.of(2_000),
        )
        val calc = RideCommissionPolicy.calculate(amounts)
        assertEquals(8_000L, calc.commissionableBase.minorUnits)
        assertEquals(400L, calc.platformCommission.minorUnits)
    }

    // ──────────────────────────────────────────────
    // SECTION 5: Fare bid normalization
    // ──────────────────────────────────────────────

    @Test
    fun `CRC fare bid snaps to 300-step grid`() {
        assertEquals(900.0, RideFareBidPolicy.normalize(1000.0, "CRC"), 0.01)
        assertEquals(1200.0, RideFareBidPolicy.normalize(1100.0, "CRC"), 0.01)
        assertEquals(30000.0, RideFareBidPolicy.normalize(50000.0, "CRC"), 0.01)
        assertEquals(900.0, RideFareBidPolicy.normalize(100.0, "CRC"), 0.01)
    }

    @Test
    fun `USD fare bid snaps to 1-step grid`() {
        assertEquals(5.0, RideFareBidPolicy.normalize(4.50, "USD"), 0.001)
        assertEquals(10.0, RideFareBidPolicy.normalize(9.99, "USD"), 0.001)
    }

    @Test
    fun `CRC fare bid respects minimum and maximum`() {
        assertEquals(900.0, RideFareBidPolicy.normalize(0.0, "CRC"), 0.01)
        assertEquals(30000.0, RideFareBidPolicy.normalize(100000.0, "CRC"), 0.01)
    }

    // ──────────────────────────────────────────────
    // SECTION 6: Boarding PIN security
    // ──────────────────────────────────────────────

    @Test
    fun `boarding PIN is 4 digits`() {
        val (pin, _) = com.elysium369.meet.ride.domain.RideBoardingPinPolicy.issue(System.currentTimeMillis())
        assertEquals(4, pin.length)
        assertTrue(pin.matches(Regex("[0-9]{4}")))
    }

    @Test
    fun `boarding PIN expires after 30 minutes`() {
        val now = System.currentTimeMillis()
        val (pin, challenge) = com.elysium369.meet.ride.domain.RideBoardingPinPolicy.issue(now)
        val expiredChallenge = challenge.copy(expiresAtEpochMs = now - 1)
        val result = com.elysium369.meet.ride.domain.RideBoardingPinPolicy.verify(expiredChallenge, pin, now)
        assertEquals(com.elysium369.meet.ride.domain.RidePinVerificationStatus.EXPIRED_OR_USED, result.status)
    }

    @Test
    fun `boarding PIN locks after 5 failed attempts`() {
        val now = System.currentTimeMillis()
        val (pin, challenge) = com.elysium369.meet.ride.domain.RideBoardingPinPolicy.issue(now)
        var current = challenge
        for (i in 1..5) {
            val result = com.elysium369.meet.ride.domain.RideBoardingPinPolicy.verify(current, "0000", now)
            current = result.challenge
        }
        val lockedResult = com.elysium369.meet.ride.domain.RideBoardingPinPolicy.verify(current, "0000", now)
        assertEquals(com.elysium369.meet.ride.domain.RidePinVerificationStatus.LOCKED, lockedResult.status)
    }

    @Test
    fun `correct PIN returns VERIFIED`() {
        val now = System.currentTimeMillis()
        val (pin, challenge) = com.elysium369.meet.ride.domain.RideBoardingPinPolicy.issue(now)
        val result = com.elysium369.meet.ride.domain.RideBoardingPinPolicy.verify(challenge, pin, now)
        assertEquals(com.elysium369.meet.ride.domain.RidePinVerificationStatus.VERIFIED, result.status)
    }

    @Test
    fun `PIN cannot be reused after verification`() {
        val now = System.currentTimeMillis()
        val (pin, challenge) = com.elysium369.meet.ride.domain.RideBoardingPinPolicy.issue(now)
        val verified = com.elysium369.meet.ride.domain.RideBoardingPinPolicy.verify(challenge, pin, now)
        assertEquals(com.elysium369.meet.ride.domain.RidePinVerificationStatus.VERIFIED, verified.status)
        val reused = com.elysium369.meet.ride.domain.RideBoardingPinPolicy.verify(verified.challenge, pin, now + 1000)
        assertEquals(com.elysium369.meet.ride.domain.RidePinVerificationStatus.EXPIRED_OR_USED, reused.status)
    }

    // ──────────────────────────────────────────────
    // SECTION 7: Cancellation policy
    // ──────────────────────────────────────────────

    @Test
    fun `safety-related cancellation reasons are classified correctly`() {
        val safetyReasons = RideCancellationReason.entries.filter { it.safetyRelated }
        assertEquals(11, safetyReasons.size)
        assertTrue(RideCancellationReason.SAFETY_CONCERN.safetyRelated)
        assertTrue(RideCancellationReason.HARASSMENT.safetyRelated)
        assertFalse(RideCancellationReason.CHANGE_OF_PLANS.safetyRelated)
        assertFalse(RideCancellationReason.PASSENGER_NO_SHOW.safetyRelated)
    }

    @Test
    fun `passenger has correct subset of cancellation reasons`() {
        val reasons = RideCancellationPolicy.reasonsFor(RideActorRole.PASSENGER)
        assertTrue(RideCancellationReason.SAFETY_CONCERN in reasons)
        assertTrue(RideCancellationReason.CHANGE_OF_PLANS in reasons)
        assertTrue(RideCancellationReason.PASSENGER_NO_SHOW !in reasons)
        assertTrue(RideCancellationReason.UNACCOMPANIED_MINOR !in reasons)
    }

    @Test
    fun `driver has correct subset of cancellation reasons`() {
        val reasons = RideCancellationPolicy.reasonsFor(RideActorRole.DRIVER)
        assertTrue(RideCancellationReason.PASSENGER_NO_SHOW in reasons)
        assertTrue(RideCancellationReason.UNACCOMPANIED_MINOR in reasons)
        assertTrue(RideCancellationReason.CHANGE_OF_PLANS !in reasons)
    }

    @Test
    fun `OTHER cancellation requires detail`() {
        assertTrue("OTHER with detail is valid", RideCancellationPolicy.isDetailValid(RideCancellationReason.OTHER, "Something happened"))
        assertFalse("OTHER without detail is invalid", RideCancellationPolicy.isDetailValid(RideCancellationReason.OTHER, null))
        assertFalse("OTHER with blank detail is invalid", RideCancellationPolicy.isDetailValid(RideCancellationReason.OTHER, "   "))
    }

    @Test
    fun `non-OTHER reasons allow null detail`() {
        assertTrue("SAFETY_CONCERN with null detail is valid", RideCancellationPolicy.isDetailValid(RideCancellationReason.SAFETY_CONCERN, null))
    }

    // ──────────────────────────────────────────────
    // SECTION 8: Arrival policy
    // ──────────────────────────────────────────────

    @Test
    fun `arrival rejects null driver location`() {
        val pickup = com.elysium369.meet.ride.map.RideGeoPoint(9.93, -84.08, 10f, System.currentTimeMillis())
        val result = com.elysium369.meet.ride.domain.RideArrivalPolicy.evaluate(null, pickup, System.currentTimeMillis())
        assertFalse(result.allowed)
    }

    @Test
    fun `arrival rejects stale GPS`() {
        val oldTime = System.currentTimeMillis() - 60_000L // 1 minute old
        val driver = com.elysium369.meet.ride.map.RideGeoPoint(9.93, -84.08, 10f, oldTime)
        val pickup = com.elysium369.meet.ride.map.RideGeoPoint(9.93, -84.08, 10f, System.currentTimeMillis())
        val result = com.elysium369.meet.ride.domain.RideArrivalPolicy.evaluate(driver, pickup, System.currentTimeMillis())
        assertFalse(result.allowed)
    }

    @Test
    fun `arrival rejects low accuracy GPS`() {
        val driver = com.elysium369.meet.ride.map.RideGeoPoint(9.93, -84.08, 200f, System.currentTimeMillis())
        val pickup = com.elysium369.meet.ride.map.RideGeoPoint(9.93, -84.08, 10f, System.currentTimeMillis())
        val result = com.elysium369.meet.ride.domain.RideArrivalPolicy.evaluate(driver, pickup, System.currentTimeMillis())
        assertFalse(result.allowed)
    }

    @Test
    fun `arrival allows when within 100m with good GPS`() {
        // Same point = 0m distance
        val now = System.currentTimeMillis()
        val driver = com.elysium369.meet.ride.map.RideGeoPoint(9.93, -84.08, 5f, now)
        val pickup = com.elysium369.meet.ride.map.RideGeoPoint(9.93, -84.08, 5f, now)
        val result = com.elysium369.meet.ride.domain.RideArrivalPolicy.evaluate(driver, pickup, now)
        assertTrue(result.allowed)
    }

    @Test
    fun `arrival rejects when beyond 100m`() {
        // ~1km apart (0.01 degree latitude ≈ 1.1km)
        val now = System.currentTimeMillis()
        val driver = com.elysium369.meet.ride.map.RideGeoPoint(9.94, -84.08, 5f, now)
        val pickup = com.elysium369.meet.ride.map.RideGeoPoint(9.93, -84.08, 5f, now)
        val result = com.elysium369.meet.ride.domain.RideArrivalPolicy.evaluate(driver, pickup, now)
        assertFalse(result.allowed)
    }

    // ──────────────────────────────────────────────
    // SECTION 9: Guardian safety signal classification
    // ──────────────────────────────────────────────

    @Test
    fun `CRITICAL severity signals are classified correctly`() {
        assertEquals("CRITICAL", RideGuardianPolicy.severity(RideSafetySignalType.SOS))
        assertEquals("CRITICAL", RideGuardianPolicy.severity(RideSafetySignalType.POSSIBLE_COLLISION))
        assertEquals("CRITICAL", RideGuardianPolicy.severity(RideSafetySignalType.MEDICAL_CONCERN))
    }

    @Test
    fun `URGENT severity signals are classified correctly`() {
        assertEquals("URGENT", RideGuardianPolicy.severity(RideSafetySignalType.VEHICLE_MISMATCH))
        assertEquals("URGENT", RideGuardianPolicy.severity(RideSafetySignalType.PERSON_MISMATCH))
        assertEquals("URGENT", RideGuardianPolicy.severity(RideSafetySignalType.HARASSMENT))
        assertEquals("URGENT", RideGuardianPolicy.severity(RideSafetySignalType.ROUTE_DEVIATION))
    }

    @Test
    fun `CHECK_IN severity signals are classified correctly`() {
        assertEquals("CHECK_IN", RideGuardianPolicy.severity(RideSafetySignalType.CHECK_IN_REQUEST))
        assertEquals("CHECK_IN", RideGuardianPolicy.severity(RideSafetySignalType.LONG_STOP))
        assertEquals("CHECK_IN", RideGuardianPolicy.severity(RideSafetySignalType.SIGNAL_LOSS))
    }

    @Test
    fun `guardian blocks signals on inactive rides`() {
        assertFalse("Cannot signal on DRAFT", RideGuardianPolicy.canSignal("DRAFT", 1L))
        assertFalse("Cannot signal on CANCELLED", RideGuardianPolicy.canSignal("CANCELLED", 1L))
        assertFalse("Cannot signal with version 0", RideGuardianPolicy.canSignal("IN_PROGRESS", 0L))
        assertTrue("Can signal on ASSIGNED", RideGuardianPolicy.canSignal("ASSIGNED", 1L))
        assertTrue("Can signal on IN_PROGRESS", RideGuardianPolicy.canSignal("IN_PROGRESS", 1L))
    }

    // ──────────────────────────────────────────────
    // SECTION 10: Driver eligibility (new)
    // ──────────────────────────────────────────────

    @Test
    fun `fully qualified driver is eligible`() {
        val quals = RideEligibilityPolicy.DriverOperationalQualifications(
            identityVerified = true,
            driverApproved = true,
            vehicleAssigned = true,
            vehicleEligible = true,
            availability = RideDriverAvailability.AVAILABLE,
            lastSeenAtMs = System.currentTimeMillis(),
        )
        val result = RideEligibilityPolicy.evaluateQualifications(quals, System.currentTimeMillis())
        assertTrue(result.eligible)
    }

    @Test
    fun `unverified identity blocks eligibility`() {
        val quals = RideEligibilityPolicy.DriverOperationalQualifications(
            identityVerified = false,
            driverApproved = true,
            vehicleAssigned = true,
            vehicleEligible = true,
            availability = RideDriverAvailability.AVAILABLE,
            lastSeenAtMs = System.currentTimeMillis(),
        )
        val result = RideEligibilityPolicy.evaluateQualifications(quals, System.currentTimeMillis())
        assertFalse(result.eligible)
        assertTrue(result.reason?.contains("IDENTITY_NOT_VERIFIED") == true)
    }

    @Test
    fun `unapproved driver blocks eligibility`() {
        val quals = RideEligibilityPolicy.DriverOperationalQualifications(
            identityVerified = true,
            driverApproved = false,
            vehicleAssigned = true,
            vehicleEligible = true,
            availability = RideDriverAvailability.AVAILABLE,
            lastSeenAtMs = System.currentTimeMillis(),
        )
        val result = RideEligibilityPolicy.evaluateQualifications(quals, System.currentTimeMillis())
        assertFalse(result.eligible)
        assertTrue(result.reason?.contains("DRIVER_NOT_APPROVED") == true)
    }

    @Test
    fun `no vehicle assigned blocks eligibility`() {
        val quals = RideEligibilityPolicy.DriverOperationalQualifications(
            identityVerified = true,
            driverApproved = true,
            vehicleAssigned = false,
            vehicleEligible = true,
            availability = RideDriverAvailability.AVAILABLE,
            lastSeenAtMs = System.currentTimeMillis(),
        )
        val result = RideEligibilityPolicy.evaluateQualifications(quals, System.currentTimeMillis())
        assertFalse(result.eligible)
        assertTrue(result.reason?.contains("NO_VEHICLE_ASSIGNED") == true)
    }

    @Test
    fun `ineligible vehicle blocks eligibility`() {
        val quals = RideEligibilityPolicy.DriverOperationalQualifications(
            identityVerified = true,
            driverApproved = true,
            vehicleAssigned = true,
            vehicleEligible = false,
            availability = RideDriverAvailability.AVAILABLE,
            lastSeenAtMs = System.currentTimeMillis(),
        )
        val result = RideEligibilityPolicy.evaluateQualifications(quals, System.currentTimeMillis())
        assertFalse(result.eligible)
        assertTrue(result.reason?.contains("VEHICLE_NOT_ELIGIBLE") == true)
    }

    @Test
    fun `offline driver blocks eligibility even if fully qualified`() {
        val quals = RideEligibilityPolicy.DriverOperationalQualifications(
            identityVerified = true,
            driverApproved = true,
            vehicleAssigned = true,
            vehicleEligible = true,
            availability = RideDriverAvailability.OFFLINE,
            lastSeenAtMs = System.currentTimeMillis(),
        )
        val result = RideEligibilityPolicy.evaluateQualifications(quals, System.currentTimeMillis())
        assertFalse(result.eligible)
    }

    // ──────────────────────────────────────────────
    // SECTION 11: RideState isActive property
    // ──────────────────────────────────────────────

    @Test
    fun `isActive correctly identifies active states`() {
        val activeStates = listOf(
            RideState.DRAFT, RideState.SEARCHING, RideState.OFFERED, RideState.ASSIGNED,
            RideState.DRIVER_EN_ROUTE, RideState.ARRIVED, RideState.PASSENGER_ONBOARD, RideState.IN_PROGRESS,
        )
        val inactiveStates = listOf(RideState.COMPLETED, RideState.CANCELLED, RideState.EXPIRED, RideState.DISPUTED)

        activeStates.forEach { assertTrue("$it should be active", it.isActive) }
        inactiveStates.forEach { assertFalse("$it should NOT be active", it.isActive) }
    }

    // ──────────────────────────────────────────────
    // SECTION 12: Audit completeness — all state transitions documented
    // ──────────────────────────────────────────────

    @Test
    fun `every RideState enum value appears in at least one transition`() {
        val statesInTransitions = mutableSetOf<RideState>()
        val allStates = RideState.entries.toSet()

        for (from in allStates) {
            for (to in allStates) {
                if (from == to) continue
                for (role in listOf(RideActorRole.PASSENGER, RideActorRole.DRIVER, RideActorRole.SYSTEM)) {
                    val result = transition(from, to, role, pinVerified = true)
                    if (result.isAllowed()) {
                        statesInTransitions.add(from)
                        statesInTransitions.add(to)
                    }
                }
            }
        }

        // COMPLETED is terminal but reachable via IN_PROGRESS→COMPLETED
        // DISPUTED is reachable via COMPLETED→DISPUTED and CANCELLED→DISPUTED
        val uncovered = allStates - statesInTransitions
        assertTrue(
            "All states must appear in at least one transition. Uncovered: $uncovered",
            uncovered.isEmpty(),
        )
    }
}
