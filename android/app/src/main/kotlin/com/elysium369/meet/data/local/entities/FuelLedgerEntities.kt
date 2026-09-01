package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "fuel_transactions_local",
    indices = [
        Index(value = ["ownerPrincipalId", "occurredAtEpochMs"]),
        Index(value = ["ownerPrincipalId", "vehicleId", "occurredAtEpochMs"]),
        Index(value = ["ownerPrincipalId", "rideId"]),
        Index(value = ["ownerPrincipalId", "syncState"]),
    ],
)
data class FuelTransactionEntity(
    @androidx.room.PrimaryKey val transactionId: String,
    val ownerPrincipalId: String,
    val vehicleId: String? = null,
    val rideId: String? = null,
    val stationId: String? = null,
    val fuelType: String = "UNKNOWN",
    val odometerMeters: Long? = null,
    val distanceSincePreviousMeters: Long? = null,
    val amountMinor: Long,
    val currency: String,
    val volumeMilliLiters: Long,
    val pricePerLiterMinor: Long?,
    val source: String,
    val truthState: String,
    val receiptEvidenceId: String? = null,
    val occurredAtEpochMs: Long,
    val createdAtEpochMs: Long,
    val syncState: String,
)

@Entity(
    tableName = "fuel_reward_ledger_local",
    indices = [
        Index(value = ["ownerPrincipalId", "occurredAtEpochMs"]),
        Index(value = ["ownerPrincipalId", "transactionId"]),
        Index(value = ["ownerPrincipalId", "serverVersion"], unique = true),
    ],
)
data class FuelRewardLedgerEntryEntity(
    @androidx.room.PrimaryKey val entryId: String,
    val ownerPrincipalId: String,
    val transactionId: String?,
    val deltaUnits: Long,
    val balanceAfterUnits: Long,
    val reasonCode: String,
    val authorityState: String,
    val serverVersion: Long,
    val occurredAtEpochMs: Long,
)

@Entity(
    tableName = "fuel_station_price_observations",
    indices = [
        Index(value = ["stationId", "fuelType", "observedAtEpochMs"]),
        Index(value = ["ownerPrincipalId", "expiresAtEpochMs"]),
    ],
)
data class FuelStationPriceObservationEntity(
    @androidx.room.PrimaryKey val observationId: String,
    val ownerPrincipalId: String,
    val stationId: String,
    val fuelType: String,
    val pricePerLiterMinor: Long,
    val currency: String,
    val source: String,
    val truthState: String,
    val observedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)
