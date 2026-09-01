package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index

/** Owner-scoped pointer to the ride the user explicitly opened. */
@Entity(
    tableName = "active_ride_selections",
    primaryKeys = ["ownerPrincipalId"],
    indices = [Index(value = ["rideRequestId"])],
)
data class ActiveRideSelectionEntity(
    val ownerPrincipalId: String,
    val rideRequestId: String,
    val updatedAtEpochMs: Long,
)
