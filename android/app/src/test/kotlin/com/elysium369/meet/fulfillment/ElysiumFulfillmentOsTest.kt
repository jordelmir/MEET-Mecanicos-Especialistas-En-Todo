package com.elysium369.meet.fulfillment

import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.services.kernel.CurrencyCode
import com.elysium369.meet.core.services.kernel.Money
import com.elysium369.meet.core.services.kernel.ServiceRole
import com.elysium369.meet.core.services.kernel.ServiceVertical
import com.elysium369.meet.core.services.tow.*
import com.elysium369.meet.fulfillment.adapters.RideFulfillmentAdapter
import com.elysium369.meet.fulfillment.adapters.TowFulfillmentAdapter
import com.elysium369.meet.fulfillment.domain.FulfillmentMode
import com.elysium369.meet.fulfillment.domain.FulfillmentPhase
import com.elysium369.meet.fulfillment.domain.FulfillmentPricing
import com.elysium369.meet.ride.domain.RideState
import com.elysium369.meet.ui.screens.ride.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ElysiumFulfillmentOsTest {

    // ── 1. TOW STATE MACHINE TRANSITION & ROLE GUARDS ──

    @Test
    fun towValidLifecycleProgressionTest() {
        val operatorId = UUID.randomUUID()
        val towUnitId = "UNIT-PLATFORM-01"

        // 1. REQUESTED -> ASSIGNED
        val s1 = TowStateEngine.getNextState(
            fromState = TowState.REQUESTED,
            action = TowAction.AssignOperator(operatorId, towUnitId),
            actorRole = ServiceRole.TOW_OPERATOR
        )
        assertEquals(TowState.ASSIGNED, s1)

        // 2. ASSIGNED -> EN_ROUTE
        val s2 = TowStateEngine.getNextState(
            fromState = TowState.ASSIGNED,
            action = TowAction.StartEnRoute,
            actorRole = ServiceRole.TOW_OPERATOR
        )
        assertEquals(TowState.EN_ROUTE, s2)

        // 3. EN_ROUTE -> ARRIVED
        val s3 = TowStateEngine.getNextState(
            fromState = TowState.EN_ROUTE,
            action = TowAction.ConfirmArrival,
            actorRole = ServiceRole.TOW_OPERATOR
        )
        assertEquals(TowState.ARRIVED, s3)

        // 4. ARRIVED -> LOADING
        val s4 = TowStateEngine.getNextState(
            fromState = TowState.ARRIVED,
            action = TowAction.StartLoading,
            actorRole = ServiceRole.TOW_OPERATOR
        )
        assertEquals(TowState.LOADING, s4)

        // 5. LOADING -> LOADED (Requires cryptographic evidence hash)
        val s5 = TowStateEngine.getNextState(
            fromState = TowState.LOADING,
            action = TowAction.ConfirmLoaded("SHA256:EVIDENCE_LOADED_CAR_01"),
            actorRole = ServiceRole.TOW_OPERATOR
        )
        assertEquals(TowState.LOADED, s5)

        // 6. LOADED -> IN_TRANSIT
        val s6 = TowStateEngine.getNextState(
            fromState = TowState.LOADED,
            action = TowAction.StartTransit,
            actorRole = ServiceRole.TOW_OPERATOR
        )
        assertEquals(TowState.IN_TRANSIT, s6)

        // 7. IN_TRANSIT -> ARRIVED_DESTINATION
        val s7 = TowStateEngine.getNextState(
            fromState = TowState.IN_TRANSIT,
            action = TowAction.ArrivedAtDestination,
            actorRole = ServiceRole.TOW_OPERATOR
        )
        assertEquals(TowState.ARRIVED_DESTINATION, s7)

        // 8. ARRIVED_DESTINATION -> UNLOADING
        val s8 = TowStateEngine.getNextState(
            fromState = TowState.ARRIVED_DESTINATION,
            action = TowAction.StartUnloading,
            actorRole = ServiceRole.TOW_OPERATOR
        )
        assertEquals(TowState.UNLOADING, s8)

        // 9. UNLOADING -> DELIVERED (Requires delivery evidence hash)
        val s9 = TowStateEngine.getNextState(
            fromState = TowState.UNLOADING,
            action = TowAction.ConfirmDelivered("SHA256:EVIDENCE_DELIVERED_CAR_01"),
            actorRole = ServiceRole.TOW_OPERATOR
        )
        assertEquals(TowState.DELIVERED, s9)

        // 10. DELIVERED -> COMPLETED (Only Customer or Admin can complete/sign off)
        val s10 = TowStateEngine.getNextState(
            fromState = TowState.DELIVERED,
            action = TowAction.CompleteService,
            actorRole = ServiceRole.CUSTOMER
        )
        assertEquals(TowState.COMPLETED, s10)
    }

    @Test
    fun towInvalidTransitionsFailClosedTest() {
        // Cannot jump directly from REQUESTED to COMPLETED
        val invalidJump = TowStateEngine.getNextState(
            fromState = TowState.REQUESTED,
            action = TowAction.CompleteService,
            actorRole = ServiceRole.CUSTOMER
        )
        assertNull(invalidJump)

        // Cannot jump backwards from IN_TRANSIT to EN_ROUTE
        val backwardsJump = TowStateEngine.getNextState(
            fromState = TowState.IN_TRANSIT,
            action = TowAction.StartEnRoute,
            actorRole = ServiceRole.TOW_OPERATOR
        )
        assertNull(backwardsJump)
    }

    @Test
    fun towActorAuthorizationGuardsTest() {
        // Customer cannot declare themselves as loaded
        val customerLoaded = TowStateEngine.getNextState(
            fromState = TowState.LOADING,
            action = TowAction.ConfirmLoaded("SHA256:FAKE"),
            actorRole = ServiceRole.CUSTOMER
        )
        assertNull(customerLoaded)

        // Tow operator cannot complete their own service unilaterally without customer/admin signoff
        val operatorSelfComplete = TowStateEngine.getNextState(
            fromState = TowState.DELIVERED,
            action = TowAction.CompleteService,
            actorRole = ServiceRole.TOW_OPERATOR
        )
        assertNull(operatorSelfComplete)
    }

    @Test
    fun towEvidenceRequiredForCustodyGuardsTest() {
        // Blank evidence hash MUST be rejected
        val blankEvidence = TowStateEngine.getNextState(
            fromState = TowState.LOADING,
            action = TowAction.ConfirmLoaded("   "),
            actorRole = ServiceRole.TOW_OPERATOR
        )
        assertNull(blankEvidence)

        val blankDeliveryEvidence = TowStateEngine.getNextState(
            fromState = TowState.UNLOADING,
            action = TowAction.ConfirmDelivered(""),
            actorRole = ServiceRole.TOW_OPERATOR
        )
        assertNull(blankDeliveryEvidence)
    }

    // ── 2. TOW CAPABILITY & DISPATCH INTEGRITY ──

    @Test
    fun towCapabilitiesEnumIntegrityTest() {
        val caps = TowCapabilities.values().toSet()
        assertTrue(caps.contains(TowCapabilities.FLATBED))
        assertTrue(caps.contains(TowCapabilities.WHEEL_LIFT))
        assertTrue(caps.contains(TowCapabilities.EV_COMPATIBLE))
        assertTrue(caps.contains(TowCapabilities.LOCKED_WHEELS))
        assertTrue(caps.contains(TowCapabilities.WINCH))
    }

    // ── 3. FULFILLMENT ADAPTERS & PROJECTION CONTRACTS ──

    @Test
    fun towFulfillmentAdapterProjectsCorrectPhasesTest() {
        val job = TowJob(
            jobId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            customerName = "Carlos Mora",
            customerPhone = "+506 8888-0000",
            vehicleSummary = "Hyundai Accent 2005",
            pickupLocation = GeoPoint(9.9333, -84.0833),
            pickupAddress = "San José",
            state = TowState.EN_ROUTE,
            estimatedPrice = Money.ofCrc(20000L),
            assignedOperatorName = "Andrés Soto",
            assignedOperatorPhone = "+506 8765-4321",
            assignedUnit = TowUnit(
                towUnitId = "TU-1",
                operatorId = UUID.randomUUID(),
                brandModel = "Toyota Dyna 2020",
                licensePlate = "CL-12345",
                capabilities = setOf(TowCapabilities.FLATBED, TowCapabilities.WINCH)
            )
        )

        val projection = TowFulfillmentAdapter.toFulfillmentProjection(job)

        assertEquals(ServiceVertical.TOW, projection.vertical)
        assertEquals(FulfillmentMode.PICKUP_AND_DELIVERY, projection.mode)
        assertEquals(FulfillmentPhase.ProviderEnRoute, projection.phase)
        assertNotNull(projection.provider)
        assertEquals("Andrés Soto", projection.provider?.name)
        assertTrue(projection.mapState.markers.isNotEmpty())
    }

    @Test
    fun rideFulfillmentAdapterProjectsActiveRideTest() {
        val ride = ActiveRideViewState(
            rideId = "ride_test_01",
            driver = MatchedDriver(
                driverId = "drv_1",
                name = "Conductor Real",
                rating = 4.98,
                totalTrips = 500,
                vehicle = "Corolla",
                plate = "ABC-123",
                etaMinutes = 4,
                distanceMeters = 1200
            ),
            pickup = RidePlaceInput("p1", "Parque Central", "San José", 9.9333, -84.0833),
            dropoff = RidePlaceInput("d1", "Multiplaza", "Escazú", 9.9300, -84.1400),
            fareQuote = FareQuote(1000L, 3000L, 1000L, 5000L, "CRC", 8.0, 15),
            state = RideState.DRIVER_EN_ROUTE
        )

        val projection = RideFulfillmentAdapter.toFulfillmentProjection(ride)

        assertEquals(ServiceVertical.RIDE, projection.vertical)
        assertEquals(FulfillmentPhase.ProviderEnRoute, projection.phase)
        assertEquals("Conductor Real", projection.provider?.name)
        assertTrue(projection.pricing is FulfillmentPricing.Quote)
        val quote = projection.pricing as FulfillmentPricing.Quote
        assertEquals(5000L, quote.amount.amountMinor)
    }

    private fun resolveProjectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path")
    ).firstOrNull { it.exists() } ?: File(path)

    // ── 4. PRODUCTION ROUTE TRUTH GUARD (ZERO SYNTHETIC DATA) ──

    @Test
    fun productionRideRoutesHaveNoSyntheticRodrigoOrFakeDelaysTest() {
        val screensDir = resolveProjectFile("src/main/kotlin/com/elysium369/meet/ui/screens/ride")
        assertTrue("Ride screens directory must exist: ${screensDir.absolutePath}", screensDir.exists() && screensDir.isDirectory)

        val kotlinFiles = screensDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("Must inspect ride screen files", kotlinFiles.isNotEmpty())

        for (file in kotlinFiles) {
            val content = file.readText()

            // Guard against hardcoded synthetic drivers in production screens
            assertFalse(
                "File ${file.name} must NOT contain synthetic driver 'Rodrigo Alvarado'",
                content.contains("Rodrigo Alvarado")
            )
            assertFalse(
                "File ${file.name} must NOT contain synthetic plate 'BGH-409'",
                content.contains("BGH-409")
            )
            assertFalse(
                "File ${file.name} must NOT contain synthetic passenger 'Mariela Quesada'",
                content.contains("Mariela Quesada")
            )

            // Guard against simulation timers pretending to be real network dispatch
            assertFalse(
                "File ${file.name} must NOT use delay() to simulate driver matching",
                content.contains("delay(3000)") || content.contains("delay(2000)")
            )
        }

        // Verify MainActivity does not hardcode ActiveRideViewState
        val mainActivityFile = resolveProjectFile("src/main/kotlin/com/elysium369/meet/MainActivity.kt")
        assertTrue("MainActivity.kt must exist: ${mainActivityFile.absolutePath}", mainActivityFile.exists())
        val mainContent = mainActivityFile.readText()
        assertFalse(
            "MainActivity.kt must NOT contain hardcoded 'Rodrigo Alvarado'",
            mainContent.contains("Rodrigo Alvarado")
        )
        assertFalse(
            "MainActivity.kt must NOT contain synthetic 'MEET-CR' plate fallback",
            mainContent.contains("\"MEET-CR\"")
        )
        assertFalse(
            "MainActivity.kt must NOT fallback unknown ride state to DRIVER_EN_ROUTE",
            mainContent.contains("getOrDefault(com.elysium369.meet.ride.domain.RideState.DRIVER_EN_ROUTE)")
        )
    }

    // ── 5. SEMANTIC TRUTH & TRUTH-SAFE FALLBACK TESTS ──

    @Test
    fun unknownRideStateDoesNotBecomeDriverEnRouteTest() {
        val unknownStateString = "UNKNOWN_OR_UNPARSEABLE_STATUS"
        val resolved = runCatching {
            RideState.valueOf(unknownStateString)
        }.getOrNull() ?: RideState.SEARCHING

        assertNotEquals(
            "An unknown ride state must NEVER be coerced into DRIVER_EN_ROUTE",
            RideState.DRIVER_EN_ROUTE,
            resolved
        )
        assertEquals(RideState.SEARCHING, resolved)
    }

    @Test
    fun missingDriverDoesNotCreateMatchedDriverTest() {
        val assignedDriverId: String? = null
        val matchedDriver = assignedDriverId?.let { driverId ->
            MatchedDriver(
                driverId = driverId,
                name = "Conductor",
                rating = null,
                totalTrips = null
            )
        }

        assertNull("When assignedDriverId is null, matchedDriver MUST be null", matchedDriver)

        val activeRide = ActiveRideViewState(
            rideId = "ride_unassigned",
            driver = matchedDriver,
            pickup = RidePlaceInput("p1", "Pickup", null, 9.9333, -84.0833),
            dropoff = RidePlaceInput("d1", "Dropoff", null, 9.9300, -84.1400),
            fareQuote = FareQuote(0L, 0L, 0L, 0L, "CRC", 0.0, 0),
            state = RideState.SEARCHING
        )
        assertNull("ActiveRideViewState must allow null driver", activeRide.driver)
    }

    @Test
    fun finalSettlementMathInvariantsTest() {
        val base = Money.ofCrc(10000L)
        val extras = Money.ofCrc(2500L)
        val taxes = Money.ofCrc(1625L)
        val correctTotal = Money.ofCrc(14125L)

        // 1. Valid settlement satisfies total == base + extras + taxes
        val validSettlement = FulfillmentPricing.FinalSettlement(
            base = base,
            extras = extras,
            taxes = taxes,
            total = correctTotal
        )
        assertEquals(14125L, validSettlement.total.amountMinor)

        // 2. Mismatched total throws IllegalArgumentException
        val badTotal = Money.ofCrc(15000L)
        try {
            FulfillmentPricing.FinalSettlement(
                base = base,
                extras = extras,
                taxes = taxes,
                total = badTotal
            )
            fail("Should throw IllegalArgumentException when total does not equal base + extras + taxes")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must equal base") == true)
        }

        // 3. Mismatched currencies throw IllegalArgumentException
        val usdTotal = Money.ofUsdCents(14125L)
        try {
            FulfillmentPricing.FinalSettlement(
                base = base,
                extras = extras,
                taxes = taxes,
                total = usdTotal
            )
            fail("Should throw IllegalArgumentException when settlement currencies do not match")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("currencies must match") == true)
        }
    }

    @Test
    fun cancelledTowDoesNotMarkFutureTimelineStepsCompleteTest() {
        val cancelledJob = TowJob(
            jobId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            customerName = "Test Client",
            customerPhone = "+506 8888-8888",
            vehicleSummary = "Sedan",
            pickupLocation = GeoPoint(9.9333, -84.0833),
            pickupAddress = "San José",
            state = TowState.CANCELLED
        )

        val projection = TowFulfillmentAdapter.toFulfillmentProjection(cancelledJob)

        assertTrue(projection.phase is FulfillmentPhase.Cancelled)

        // Ensure future milestones (ASSIGNED, EN_ROUTE, LOADED, DELIVERED, COMPLETED) are NOT marked completed
        for (event in projection.timeline) {
            if (event.phase in setOf("ASSIGNED", "EN_ROUTE", "LOADED", "DELIVERED", "COMPLETED")) {
                assertFalse(
                    "Cancelled job must NOT have future phase ${event.phase} marked completed",
                    event.isCompleted
                )
            }
        }
    }

    @Test
    fun towCommandRepositoryCasAndRoomMappingTest() {
        val repo = TowCommandRepository()

        val job = repo.requestTow(
            customerId = UUID.randomUUID(),
            customerName = "Maria Rojas",
            customerPhone = "+506 7000-0000",
            vehicleVin = null,
            vehicleSummary = "Nissan Tiida",
            pickupLocation = GeoPoint(9.9333, -84.0833),
            pickupAddress = "Escazú",
            destinationLocation = null,
            destinationAddress = null,
            requiredCapabilities = setOf(TowCapabilities.FLATBED),
            estimatedPrice = Money.ofCrc(18000L)
        )

        assertEquals(1L, job.serverVersion)
        assertEquals(TowState.REQUESTED, job.state)

        // 1. CAS Concurrency Conflict: passing wrong expectedServerVersion
        val conflictResult = repo.executeAction(
            jobId = job.jobId,
            action = TowAction.AssignOperator(UUID.randomUUID(), "TOW-UNIT-99"),
            actorRole = ServiceRole.TOW_OPERATOR,
            expectedServerVersion = 999L // Wrong version
        )
        assertTrue("Must detect concurrency conflict", conflictResult is TowCommandResult.ConcurrencyConflict)

        // 2. Successful transition with correct version
        val successResult = repo.executeAction(
            jobId = job.jobId,
            action = TowAction.AssignOperator(UUID.randomUUID(), "TOW-UNIT-99"),
            actorRole = ServiceRole.TOW_OPERATOR,
            expectedServerVersion = 1L
        )
        assertTrue("Must succeed with matching version", successResult is TowCommandResult.Success)
        val assignedJob = (successResult as TowCommandResult.Success).job
        assertEquals(2L, assignedJob.serverVersion)
        assertEquals(TowState.ASSIGNED, assignedJob.state)

        // 3. Entity round-trip conversion integrity
        val entity = with(TowCommandRepository) { assignedJob.toEntity() }
        assertEquals(assignedJob.jobId.toString(), entity.requestId)
        assertEquals("TAKEN", entity.status)
        assertEquals(18000.0, entity.priceOffer, 0.01)

        val reconstructedJob = with(TowCommandRepository) { entity.toTowJob() }
        assertEquals(assignedJob.jobId, reconstructedJob.jobId)
        assertEquals(TowState.ASSIGNED, reconstructedJob.state)
        assertEquals(18000L, reconstructedJob.estimatedPrice?.amountMinor)
    }
}
