package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Authoritative, durable Room SQLite entity for Towing & Roadside operations.
 * Preserves exact TowState, monotonic serverVersion, requiredCapabilities, and custody records
 * without state collapse across application restarts or process death.
 */
@Entity(
    tableName = "tow_jobs",
    indices = [
        Index(value = ["state"]),
        Index(value = ["customerId"]),
        Index(value = ["assignedOperatorId"]),
        Index(value = ["correlationId"])
    ]
)
@Serializable
data class TowJobEntity(
    @PrimaryKey val jobId: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val vehicleVin: String? = null,
    val vehicleSummary: String,
    val pickupLatitude: Double,
    val pickupLongitude: Double,
    val pickupAccuracyMeters: Float? = null,
    val pickupCapturedAt: Long? = null,
    val pickupAddress: String,
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null,
    val destinationAddress: String? = null,
    val state: String, // Exact TowState name (REQUESTED, EN_ROUTE, LOADED, etc.)
    val serverVersion: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val assignedProviderId: String? = null,
    val assignedOperatorId: String? = null,
    val assignedTowUnitId: String? = null,
    val assignedOperatorName: String? = null,
    val assignedOperatorPhone: String? = null,
    val assignedOperatorRating: Double? = null,
    val assignedOperatorCompletedJobs: Int? = null,
    val operatorLatitude: Double? = null,
    val operatorLongitude: Double? = null,
    val operatorFreshnessEpochMs: Long? = null,
    val requiredCapabilities: String, // Comma-separated TowCapabilities
    val assignedUnitJson: String? = null,
    val estimatedPriceMinor: Long? = null,
    val quotedPriceMinor: Long? = null,
    val authorizedPriceMinor: Long? = null,
    val finalSettlementMinor: Long? = null,
    val currency: String = "CRC",
    val quoteId: String? = null,
    val authorizationId: String? = null,
    val correlationId: String,
    val custodyRecordsJson: String = "[]",
)
