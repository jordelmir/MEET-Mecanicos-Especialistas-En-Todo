package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "vehicle_identity_observations",
    primaryKeys = ["ownerPrincipalId", "identityObservationId"],
    indices = [
        Index(value = ["ownerPrincipalId", "diagnosticSessionId"]),
        Index(value = ["ownerPrincipalId", "vehicleBindingId"]),
    ],
)
data class VehicleIdentityObservationEntity(
    val ownerPrincipalId: String,
    val identityObservationId: String,
    val diagnosticSessionId: String,
    val vehicleBindingId: String?,
    val strategy: String,
    val transport: String,
    val protocol: String,
    val requestAddress: String?,
    val responseAddress: String?,
    val ecuIdentity: String?,
    val startedMonotonicMs: Long,
    val completedMonotonicMs: Long,
    val responseOutcome: String,
    val rawResponseHash: String,
    val parserVersion: String,
    val vinHash: String?,
    val vinLength: Int?,
    val validationResult: String,
)

@Entity(
    tableName = "diagnostic_scan_manifests",
    primaryKeys = ["ownerPrincipalId", "scanId"],
    indices = [
        Index(value = ["ownerPrincipalId", "sessionId"]),
        Index(value = ["ownerPrincipalId", "vehicleBindingId", "startedAtEpochMs"]),
    ],
)
data class DiagnosticScanManifestEntity(
    val ownerPrincipalId: String,
    val scanId: String,
    val sessionId: String,
    val vehicleBindingId: String?,
    val scanType: String,
    val appVersion: String,
    val buildSha: String,
    val parserVersion: String,
    val capabilityPackVersion: String?,
    val adapterFingerprintHash: String?,
    val transport: String,
    val protocol: String,
    val coverageJson: String,
    val findingsCount: Int,
    val warningCodesJson: String,
    val failureDomainsJson: String,
    val rawEvidenceMerkleRoot: String?,
    val completeness: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
)

@Entity(
    tableName = "telemetry_upload_queue",
    indices = [Index(value = ["status", "nextAttemptAtEpochMs"])],
)
data class TelemetryUploadQueueEntity(
    @androidx.room.PrimaryKey val signalId: String,
    val filteredPayloadJson: String,
    val status: String,
    val attemptCount: Int,
    val nextAttemptAtEpochMs: Long,
    val lastFailureCode: String?,
    val createdAtEpochMs: Long,
)
