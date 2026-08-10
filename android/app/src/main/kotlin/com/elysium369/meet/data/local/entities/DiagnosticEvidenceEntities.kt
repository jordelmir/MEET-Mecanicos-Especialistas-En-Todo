package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Long-lived identity. Temporal status belongs in DiagnosticObservationEntity. */
@Entity(
    tableName = "diagnostic_findings",
    indices = [
        Index(
            value = ["vehicleId", "ecuEndpointId", "diagnosticNamespace", "rawDtcIdentity"],
            unique = true,
            name = "index_diagnostic_findings_stable_identity",
        ),
    ],
)
data class DiagnosticFindingEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val ecuEndpointId: String,
    val diagnosticNamespace: String,
    val rawDtcIdentity: String,
    val displayCode: String,
    val createdAtMs: Long,
    val resolutionState: String = "OPEN",
    val resolvedAtMs: Long? = null,
)

/** Append-only physical/protocol evidence. Raw request and response are never updated. */
@Entity(
    tableName = "diagnostic_exchanges",
    indices = [
        Index(value = ["sessionId", "timestampMs"]),
        Index(value = ["responseAddress", "service"]),
    ],
)
data class DiagnosticExchangeEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestampMs: Long,
    val transport: String,
    val applicationProtocol: String,
    val requestScope: String,
    val requestAddress: String?,
    val responseAddress: String?,
    val service: String,
    val rawRequest: String,
    val rawResponse: String,
    val decodedOutcome: String,
    val latencyMs: Long?,
    val retryCount: Int,
    val negativeResponseCode: Int?,
    val adapterConfiguration: String,
    val parserVersion: String,
)

/** Temporal state of one stable finding. Insert only; never rewrite past observations. */
@Entity(
    tableName = "diagnostic_observations",
    indices = [
        Index(value = ["findingId", "observedAt"]),
        Index(value = ["sessionId", "observedAt"]),
        Index(value = ["exchangeId"]),
    ],
)
data class DiagnosticObservationEntity(
    @PrimaryKey val id: String,
    val findingId: String,
    val sessionId: String,
    val observedAt: Long,
    val observationState: String,
    val semantics: String,
    val statusByte: Int?,
    val sourceService: String,
    val exchangeId: String?,
    val rawPayloadHash: String,
)

@Entity(
    tableName = "finding_diagnostic_snapshots",
    indices = [Index(value = ["findingId", "capturedAtMs"])],
)
data class FindingDiagnosticSnapshotEntity(
    @PrimaryKey val id: String,
    val findingId: String,
    val moduleIdentity: String,
    val capturedAtMs: Long,
    val source: String,
    val parametersJson: String,
    val rawExchangeIdsJson: String,
)
