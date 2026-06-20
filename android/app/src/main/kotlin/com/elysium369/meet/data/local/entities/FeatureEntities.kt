package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════════
// FEATURE 1 — MEET LiveLink PRO
// ═══════════════════════════════════════════════════════════════

@Entity(tableName = "live_sessions")
@Serializable
data class LiveSessionEntity(
    @PrimaryKey val sessionId: String,
    val vehicleId: String,
    val ownerId: String,
    val mechanicId: String?,
    val status: String,            // PENDING, ACTIVE, COMPLETED, EXPIRED
    val startedAt: Long,
    val endedAt: Long?,
    val permissions: String,       // READ_ONLY, FULL
    val sessionCode: String,       // 6-digit pin code
    val shareUrl: String,
    val durationMinutes: Int,
    val videoCallUrl: String? = null
)

@Entity(tableName = "live_snapshots")
@Serializable
data class LiveSnapshotEntity(
    @PrimaryKey val snapshotId: String,
    val sessionId: String,
    val timestamp: Long,
    val pidValues: String,         // JSON representation of current telemetry values
    val notes: String
)

@Entity(tableName = "mechanic_notes")
@Serializable
data class MechanicNoteEntity(
    @PrimaryKey val noteId: String,
    val sessionId: String,
    val authorId: String,
    val content: String,
    val createdAt: Long
)

// ═══════════════════════════════════════════════════════════════
// FEATURE 2 — MEET Repair Network Addons
// ═══════════════════════════════════════════════════════════════

@Entity(tableName = "repair_photos")
@Serializable
data class RepairPhotoEntity(
    @PrimaryKey val photoId: String,
    val caseId: String,
    val photoPath: String,
    val caption: String?,
    val createdAt: Long
)

@Entity(tableName = "repair_parts")
@Serializable
data class RepairPartEntity(
    @PrimaryKey val partId: String,
    val caseId: String,
    val partNumber: String,
    val partName: String,
    val price: Double,
    val brand: String
)

@Entity(tableName = "repair_votes")
@Serializable
data class RepairVoteEntity(
    @PrimaryKey val id: String,     // compound identifier: caseId_userId
    val caseId: String,
    val userId: String,
    val voteType: String           // UP, DOWN
)

@Entity(tableName = "repair_comments")
@Serializable
data class RepairCommentEntity(
    @PrimaryKey val commentId: String,
    val caseId: String,
    val userId: String,
    val userName: String,
    val userReputation: String,    // Usuario normal, Contribuidor, Experto, Mecánico certificado, Master
    val content: String,
    val createdAt: Long
)

@Entity(tableName = "repair_verifications")
@Serializable
data class RepairVerificationEntity(
    @PrimaryKey val verificationId: String,
    val caseId: String,
    val verifierId: String,
    val verifierName: String,
    val verifierCredential: String, // Master, certified mechanic, etc.
    val verifiedAt: Long
)

// ═══════════════════════════════════════════════════════════════
// FEATURE 3 — MEET Marketplace
// ═══════════════════════════════════════════════════════════════

@Entity(tableName = "service_requests")
@Serializable
data class ServiceRequestEntity(
    @PrimaryKey val requestId: String,
    val vehicleId: String,
    val problem: String,           // pastillas desgastadas, alternador inestable, etc.
    val priority: String,          // HIGH, MEDIUM, LOW
    val description: String,
    val location: String,          // Lat,Lon or text
    val radiusKm: Double,
    val status: String,            // OPEN, ACCEPTED, COMPLETED, CANCELLED
    val autoDtcCode: String?,
    val createdAt: Long
)

@Entity(tableName = "service_bids")
@Serializable
data class ServiceBidEntity(
    @PrimaryKey val bidId: String,
    val requestId: String,
    val shopId: String,
    val shopName: String,
    val shopRating: Double,
    val price: Double,
    val estimatedHours: Double,
    val warrantyDays: Int,
    val message: String,
    val status: String,            // PENDING, ACCEPTED, REJECTED
    val createdAt: Long
)

// ═══════════════════════════════════════════════════════════════
// FEATURE 4 — MEET Black Box
// ═══════════════════════════════════════════════════════════════

@Entity(tableName = "evidence_packages")
@Serializable
data class EvidencePackageEntity(
    @PrimaryKey val packageId: String,
    val vehicleId: String,
    val eventType: String,         // IMPACT, HARSH_BRAKING, SOS, CRITICAL_TEMP, DTC_SEVERE, MANUAL
    val timestamp: Long,
    val gpsLocation: String,
    val videoPath: String,
    val audioPath: String,
    val pidSnapshot: String,       // JSON snapshot of telemetry parameters
    val dtcs: String,              // JSON list of active/pending DTC codes
    val hashSha256: String,
    val signatureVersion: String,
    val createdAt: Long
)

// ═══════════════════════════════════════════════════════════════
// FEATURE 5 — MEET Twin
// ═══════════════════════════════════════════════════════════════

@Entity(tableName = "vehicle_twin_profiles")
@Serializable
data class VehicleTwinProfileEntity(
    @PrimaryKey val profileId: String,
    val vehicleId: String,
    val baselineJson: String,      // expected base values of parameters
    val varianceJson: String,      // variance/dev values
    val confidence: Double,        // 0.0 to 100.0%
    val lastTrainingDate: Long,
    val anomalyCount: Int,
    val healthScore: Int
)

@Entity(tableName = "twin_anomalies")
@Serializable
data class TwinAnomalyEntity(
    @PrimaryKey val anomalyId: String,
    val vehicleId: String,
    val parameter: String,         // Coolant Temperature, Battery Voltage, etc.
    val expectedValue: Float,
    val actualValue: Float,
    val deviation: Float,
    val severity: String,          // LOW, MEDIUM, HIGH
    val confidence: Double,        // 0.0 to 100.0%
    val timestamp: Long
)
