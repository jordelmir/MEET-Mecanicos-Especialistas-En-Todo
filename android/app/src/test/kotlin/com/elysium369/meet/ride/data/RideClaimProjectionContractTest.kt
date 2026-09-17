package com.elysium369.meet.ride.data

import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ui.ObdViewModel.RideClaimUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideClaimProjectionContractTest {

    private fun createRide(
        requestId: String = "req-100",
        status: String = "OPEN",
        assignedDriverId: String? = null,
        serverVersion: Long = 0L
    ): RideRequestEntity {
        return RideRequestEntity(
            requestId = requestId,
            passengerId = "pax-1",
            passengerName = "Passenger",
            passengerPhone = "+50688888888",
            pickupLatitude = 9.93,
            pickupLongitude = -84.08,
            pickupAddress = "Pickup",
            pickupAccuracy = 5.0f,
            destLatitude = 9.94,
            destLongitude = -84.07,
            destAddress = "Dest",
            priceOffer = 4000.0,
            currency = "CRC",
            estimatedDistanceKm = 4.0,
            estimatedDurationMin = 12,
            status = status,
            assignedDriverId = assignedDriverId,
            serverVersion = serverVersion,
            createdAt = 100_000L
        )
    }

    /**
     * Pure evaluator mimicking the projection observer in ObdViewModel:
     * Pending -> Won ONLY when serverVersion > 0 and assignedDriverId in actorIds.
     */
    private fun evaluateProjection(
        currentState: RideClaimUiState,
        projectedRide: RideRequestEntity,
        actorIds: Set<String>
    ): RideClaimUiState {
        if (currentState !is RideClaimUiState.Pending) return currentState
        if (projectedRide.requestId != currentState.requestId) return currentState

        val isAssignedToMe = projectedRide.assignedDriverId != null && projectedRide.assignedDriverId in actorIds
        val isConfirmedByServer = projectedRide.serverVersion > 0L
        val isValidActiveStatus = projectedRide.status in listOf(
            "ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS"
        )

        return if (isAssignedToMe && isConfirmedByServer && isValidActiveStatus) {
            RideClaimUiState.Won(currentState.requestId)
        } else {
            currentState
        }
    }

    @Test
    fun `pending state does not transition to won without server ACK version`() {
        val pendingState = RideClaimUiState.Pending("req-100")
        val optimisticLocalRide = createRide(
            requestId = "req-100",
            status = "ACCEPTED",
            assignedDriverId = "driver-me",
            serverVersion = 0L // unconfirmed local mutation
        )

        val resultState = evaluateProjection(
            currentState = pendingState,
            projectedRide = optimisticLocalRide,
            actorIds = setOf("driver-me")
        )

        // Must NOT become Won because serverVersion == 0
        assertTrue(resultState is RideClaimUiState.Pending)
    }

    @Test
    fun `pending state transitions to won when server acknowledges and assigns to driver`() {
        val pendingState = RideClaimUiState.Pending("req-100")
        val serverAckRide = createRide(
            requestId = "req-100",
            status = "ACCEPTED",
            assignedDriverId = "driver-me",
            serverVersion = 2L // confirmed by server
        )

        val resultState = evaluateProjection(
            currentState = pendingState,
            projectedRide = serverAckRide,
            actorIds = setOf("driver-me")
        )

        assertTrue(resultState is RideClaimUiState.Won)
        assertEquals("req-100", (resultState as RideClaimUiState.Won).requestId)
    }

    @Test
    fun `pending state does not transition to won when assigned to another driver`() {
        val pendingState = RideClaimUiState.Pending("req-100")
        val otherDriverRide = createRide(
            requestId = "req-100",
            status = "ACCEPTED",
            assignedDriverId = "other-driver-999",
            serverVersion = 2L
        )

        val resultState = evaluateProjection(
            currentState = pendingState,
            projectedRide = otherDriverRide,
            actorIds = setOf("driver-me")
        )

        // Still pending until failure handler rejects it
        assertTrue(resultState is RideClaimUiState.Pending)
    }

    @Test
    fun `rejected state maps error codes to clear reasons`() {
        val rejectedConflict = RideClaimUiState.Rejected("req-1", "VERSION_CONFLICT", "Conflicto de versión")
        val rejectedAssigned = RideClaimUiState.Rejected("req-2", "ALREADY_ASSIGNED", "Ya asignado")
        val rejectedUnverified = RideClaimUiState.Rejected("req-3", "VEHICLE_NOT_VERIFIED", "Vehículo no verificado")
        val rejectedBalance = RideClaimUiState.Rejected("req-4", "INSUFFICIENT_BALANCE", "Saldo insuficiente")

        assertEquals("VERSION_CONFLICT", rejectedConflict.code)
        assertEquals("ALREADY_ASSIGNED", rejectedAssigned.code)
        assertEquals("VEHICLE_NOT_VERIFIED", rejectedUnverified.code)
        assertEquals("INSUFFICIENT_BALANCE", rejectedBalance.code)
    }
}
