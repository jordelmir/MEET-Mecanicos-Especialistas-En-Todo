package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * Durable, owner-scoped pointer to the vehicle the user explicitly activated.
 *
 * The vehicle itself remains authoritative in [VehicleEntity]. This row only
 * records intent, so garage refreshes, ordering changes and OBD lifecycle
 * events cannot silently replace the active vehicle.
 */
@Entity(
    tableName = "active_vehicle_selections",
    primaryKeys = ["ownerPrincipalId"],
    indices = [Index(value = ["vehicleId"], name = "index_active_vehicle_selections_vehicleId")],
)
data class ActiveVehicleSelectionEntity(
    val ownerPrincipalId: String,
    val vehicleId: String,
    val reason: String,
    val updatedAtEpochMs: Long,
)
