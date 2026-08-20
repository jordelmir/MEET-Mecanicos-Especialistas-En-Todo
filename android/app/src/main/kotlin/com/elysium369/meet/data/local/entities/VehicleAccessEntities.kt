package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "vehicle_access_credentials",
    indices = [Index(value = ["vehicleId"])]
)
@Serializable
data class VehicleAccessCredentialEntity(
    @PrimaryKey val credentialId: String,
    val vehicleId: String,
    val slotNumber: Int,
    val label: String,
    val credentialType: String,
    val authority: String,
    val status: String,
    val permissionsJson: String,
    val transponderFamily: String?,
    val remoteFrequency: String?,
    val batteryHealthPercent: Int?,
    val isPrimaryOwner: Boolean,
    val validFromEpochMs: Long,
    val validUntilEpochMs: Long?,
    val lastVerifiedAtEpochMs: Long?,
    val proofHash: String?
)

@Entity(
    tableName = "vehicle_access_grants",
    indices = [Index(value = ["vehicleId"])]
)
@Serializable
data class AccessGrantEntity(
    @PrimaryKey val grantId: String,
    val vehicleId: String,
    val recipientName: String,
    val recipientRole: String,
    val permissionsJson: String,
    val validFromEpochMs: Long,
    val validUntilEpochMs: Long,
    val isVehicleEnforced: Boolean,
    val status: String,
    val revocationReason: String?
)

@Entity(
    tableName = "vehicle_access_audit_events",
    indices = [Index(value = ["vehicleId"])]
)
@Serializable
data class AccessAuditEventEntity(
    @PrimaryKey val eventId: String,
    val vehicleId: String,
    val timestampEpochMs: Long,
    val action: String,
    val actor: String,
    val credentialType: String,
    val outcome: String,
    val evidenceHash: String
)
