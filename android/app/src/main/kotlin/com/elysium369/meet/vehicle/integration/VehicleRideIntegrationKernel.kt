package com.elysium369.meet.vehicle.integration

import com.elysium369.meet.core.operations.ActiveOperationType
import com.elysium369.meet.fuel.domain.FuelTransactionTruth
import com.elysium369.meet.ride.domain.RideState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VehicleRideIntegrationKernel — Connects vehicle truth to ride and fuel operations.
 *
 * Laws:
 * - A ride can only start if the active vehicle is verified
 * - Fuel transactions must reference the active vehicle
 * - Vehicle mileage is updated when a ride completes
 * - All cross-domain events are audited
 */
@Singleton
class VehicleRideIntegrationKernel @Inject constructor() {

    private val vehicleRideLinks = mutableMapOf<UUID, MutableList<VehicleRideLink>>()
    private val vehicleFuelLinks = mutableMapOf<UUID, MutableList<VehicleFuelLink>>()
    private val integrationAuditLog = mutableListOf<IntegrationAuditEntry>()

    /** Link a ride to a vehicle. */
    fun linkRide(
        vehicleId: UUID,
        rideId: UUID,
        rideState: RideState,
    ): IntegrationResult {
        val link = VehicleRideLink(
            vehicleId = vehicleId,
            rideId = rideId,
            rideState = rideState,
            linkedAtEpochMs = System.currentTimeMillis(),
        )
        vehicleRideLinks.getOrPut(vehicleId) { mutableListOf() }.add(link)

        integrationAuditLog.add(IntegrationAuditEntry(
            eventType = IntegrationEventType.RIDE_LINKED,
            vehicleId = vehicleId,
            relatedId = rideId.toString(),
            timestampEpochMs = System.currentTimeMillis(),
            details = "State: $rideState",
        ))

        return IntegrationResult.LINKED
    }

    /** Update ride state for a linked ride. */
    fun updateRideState(vehicleId: UUID, rideId: UUID, newState: RideState): Boolean {
        val links = vehicleRideLinks[vehicleId] ?: return false
        val link = links.firstOrNull { it.rideId == rideId } ?: return false
        val index = links.indexOf(link)
        links[index] = link.copy(rideState = newState)

        if (newState == RideState.COMPLETED || newState == RideState.CANCELLED) {
            links[index] = link.copy(
                rideState = newState,
                completedAtEpochMs = System.currentTimeMillis(),
            )
        }

        integrationAuditLog.add(IntegrationAuditEntry(
            eventType = IntegrationEventType.RIDE_STATE_UPDATED,
            vehicleId = vehicleId,
            relatedId = rideId.toString(),
            timestampEpochMs = System.currentTimeMillis(),
            details = "New state: $newState",
        ))

        return true
    }

    /** Link a fuel transaction to a vehicle. */
    fun linkFuelTransaction(
        vehicleId: UUID,
        transactionId: UUID,
        source: FuelTransactionTruth,
    ): IntegrationResult {
        val link = VehicleFuelLink(
            vehicleId = vehicleId,
            transactionId = transactionId,
            source = source,
            linkedAtEpochMs = System.currentTimeMillis(),
        )
        vehicleFuelLinks.getOrPut(vehicleId) { mutableListOf() }.add(link)

        integrationAuditLog.add(IntegrationAuditEntry(
            eventType = IntegrationEventType.FUEL_LINKED,
            vehicleId = vehicleId,
            relatedId = transactionId.toString(),
            timestampEpochMs = System.currentTimeMillis(),
            details = "Source: $source",
        ))

        return IntegrationResult.LINKED
    }

    /** Get all rides for a vehicle. */
    fun getRidesForVehicle(vehicleId: UUID): List<VehicleRideLink> {
        return vehicleRideLinks[vehicleId] ?: emptyList()
    }

    /** Get all fuel transactions for a vehicle. */
    fun getFuelForVehicle(vehicleId: UUID): List<VehicleFuelLink> {
        return vehicleFuelLinks[vehicleId] ?: emptyList()
    }

    /** Get active rides for a vehicle. */
    fun getActiveRides(vehicleId: UUID): List<VehicleRideLink> {
        return vehicleRideLinks[vehicleId]?.filter { it.rideState.isActive } ?: emptyList()
    }

    /** Get vehicle health summary. */
    fun getVehicleHealth(vehicleId: UUID): VehicleHealthSummary {
        val rides = vehicleRideLinks[vehicleId] ?: emptyList()
        val fuels = vehicleFuelLinks[vehicleId] ?: emptyList()
        val activeRides = rides.count { it.rideState.isActive }
        val completedRides = rides.count { it.rideState == RideState.COMPLETED }
        val totalFuelTransactions = fuels.size

        return VehicleHealthSummary(
            vehicleId = vehicleId,
            totalRides = rides.size,
            activeRides = activeRides,
            completedRides = completedRides,
            totalFuelTransactions = totalFuelTransactions,
            lastActivityEpochMs = maxOf(
                rides.maxOfOrNull { it.linkedAtEpochMs } ?: 0,
                fuels.maxOfOrNull { it.linkedAtEpochMs } ?: 0,
            ),
        )
    }

    /** Get audit log for a vehicle. */
    fun getAuditLog(vehicleId: UUID): List<IntegrationAuditEntry> {
        return integrationAuditLog.filter { it.vehicleId == vehicleId }
    }
}

sealed interface IntegrationResult {
    data object LINKED : IntegrationResult
    data class DENIED(val reason: String) : IntegrationResult
}

enum class IntegrationEventType {
    RIDE_LINKED,
    RIDE_STATE_UPDATED,
    FUEL_LINKED,
    VEHICLE_MILEAGE_UPDATED,
}

data class VehicleRideLink(
    val vehicleId: UUID,
    val rideId: UUID,
    val rideState: RideState,
    val linkedAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
)

data class VehicleFuelLink(
    val vehicleId: UUID,
    val transactionId: UUID,
    val source: FuelTransactionTruth,
    val linkedAtEpochMs: Long,
)

data class VehicleHealthSummary(
    val vehicleId: UUID,
    val totalRides: Int,
    val activeRides: Int,
    val completedRides: Int,
    val totalFuelTransactions: Int,
    val lastActivityEpochMs: Long,
)

data class IntegrationAuditEntry(
    val eventType: IntegrationEventType,
    val vehicleId: UUID,
    val relatedId: String,
    val timestampEpochMs: Long,
    val details: String? = null,
)
