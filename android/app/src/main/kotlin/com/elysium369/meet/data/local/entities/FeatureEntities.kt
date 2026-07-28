package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════════
// FEATURE 1 — Elysium Vanguard LiveLink PRO
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
// FEATURE 2 — Elysium Vanguard Repair Network Addons
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
// FEATURE 3 — Elysium Vanguard Marketplace
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
    val createdAt: Long,
    val escrowStatus: String? = "NONE", // "NONE", "HELD", "RELEASED", "REFUNDED"
    val paymentId: String? = null,
    // v31 — Mechanic assignment + Indriver-style fields
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val phone: String = "",
    val priceOffer: Double = 0.0,           // Indriver-style price in USD
    val assignedMechanicId: String? = null,
    val assignedMechanicName: String? = null,
    val assignedMechanicPhone: String? = null,
    val completedAt: Long? = null
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

@Entity(tableName = "parts_stores")
@Serializable
data class PartsStoreEntity(
    @PrimaryKey val storeId: String,
    val storeName: String,
    val rating: Double,
    val phone: String,
    val location: String,
    val deliveryRadiusKm: Double,
    val averageEtaMinutes: Int,
    val verified: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "part_requests",
    indices = [
        Index(value = ["status", "createdAt"]),
        Index(value = ["vehicleId"]),
        Index(value = ["serviceRequestId"])
    ]
)
@Serializable
data class PartRequestEntity(
    @PrimaryKey val requestId: String,
    val serviceRequestId: String?,
    val vehicleId: String,
    val dtcCode: String?,
    val partName: String,
    val partNumber: String?,
    val quantity: Int,
    val oemPreference: String,      // OEM, AFTERMARKET, ANY
    val deliveryLocation: String,
    val urgencyMinutes: Int,
    val customerNotes: String,
    val status: String,             // OPEN, ACCEPTED, DELIVERED, CANCELLED
    val acceptedOfferId: String?,
    val createdAt: Long,
    // v31 — Part position + GPS
    val partPosition: String = "N/A", // DELANTERA_DERECHA, DELANTERA_IZQUIERDA, TRASERA_DERECHA, TRASERA_IZQUIERDA, CENTRAL, N/A
    val phone: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

@Entity(
    tableName = "part_offers",
    indices = [
        Index(value = ["partRequestId"]),
        Index(value = ["storeId"])
    ]
)
@Serializable
data class PartOfferEntity(
    @PrimaryKey val offerId: String,
    val partRequestId: String,
    val storeId: String,
    val storeName: String,
    val brand: String,
    val partNumber: String,
    val condition: String,          // NEW, OEM, USED_TESTED, REMAN
    val price: Double,
    val deliveryFee: Double,
    val etaMinutes: Int,
    val warrantyDays: Int,
    val message: String,
    val status: String,             // PENDING, ACCEPTED, REJECTED
    val createdAt: Long
)

// ═══════════════════════════════════════════════════════════════
// FEATURE 4 — Elysium Vanguard Black Box
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
// FEATURE 5 — Elysium Vanguard Twin
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

@Entity(tableName = "tow_truck_requests")
@Serializable
data class TowTruckRequestEntity(
    @PrimaryKey val requestId: String,
    val userId: String,
    val vehicleInfo: String,        // Make, Model, Year, active DTCs, etc.
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val destinationLatitude: Double?,
    val destinationLongitude: Double?,
    val destinationName: String?,
    val phone: String,
    val status: String,            // OPEN, TAKEN, COMPLETED, CANCELLED
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val assignedDriverPhone: String? = null,
    val priceOffer: Double,        // Indriver style proposed fare
    val createdAt: Long,
    val completedAt: Long? = null
)

@Entity(tableName = "ratings")
@Serializable
data class RatingEntity(
    @PrimaryKey val ratingId: String,
    val targetType: String,        // MECHANIC, STORE, TOW_TRUCK, CLIENT
    val targetId: String,          // Rated user/business ID
    val sourceId: String,          // Reviewer ID
    val sourceName: String,
    val stars: Double,             // 1.0 to 5.0 rating value (e.g. 4.8)
    val comment: String,
    val createdAt: Long
)

// ═══════════════════════════════════════════════════════════════
// FEATURE 7 — Provider Role Registration System
// ═══════════════════════════════════════════════════════════════

@Entity(
    tableName = "provider_profiles",
    indices = [
        Index(value = ["userId", "providerType"], unique = true),
        Index(value = ["providerType", "isActive"])
    ]
)
@Serializable
data class ProviderProfileEntity(
    @PrimaryKey val profileId: String,
    val userId: String,                   // Supabase auth user ID
    val providerType: String,             // MECHANIC, TOW_TRUCK, PARTS_STORE
    val businessName: String,             // Nombre del taller / grúa / repuestera
    val ownerName: String,                // Nombre del propietario o responsable
    val phone: String,                    // Teléfono de contacto
    val location: String,                 // Ubicación textual
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val specialties: String = "",         // Ej: "Frenos, Suspensión, Motor" / "Grúa plataforma" / "Autopartes Toyota"
    val radiusKm: Double = 25.0,          // Radio de cobertura
    val licenseNumber: String = "",       // Número de patente / licencia
    val isActive: Boolean = true,         // Puede desactivar sin borrar
    val verified: Boolean = false,        // Verificado por el sistema
    val rating: Double = 0.0,             // Rating promedio
    val totalJobs: Int = 0,               // Trabajos completados
    val createdAt: Long,
    val updatedAt: Long = 0L
)

// ═══════════════════════════════════════════════════════════════
// FEATURE 8 — MEET Rides (Viajes InDriver-Style)
// ═══════════════════════════════════════════════════════════════

@Entity(
    tableName = "ride_requests",
    indices = [
        Index(value = ["passengerId"]),
        Index(value = ["assignedDriverId"]),
        Index(value = ["status"])
    ]
)
@Serializable
data class RideRequestEntity(
    @PrimaryKey val requestId: String,
    val passengerId: String,             // Supabase auth user ID o local ID
    val passengerName: String,
    val passengerPhone: String,
    val pickupLatitude: Double,
    val pickupLongitude: Double,
    val pickupAddress: String,
    val pickupAccuracy: Float,           // Precisión del GPS en metros
    val destLatitude: Double,
    val destLongitude: Double,
    val destAddress: String,
    val priceOffer: Double,              // Precio propuesto por el pasajero
    val currency: String,                // CRC o USD
    val estimatedDistanceKm: Double,     // Distancia calculada
    val estimatedDurationMin: Int,       // Duración aproximada
    val stopsJson: String = "[]",        // Ordered stop snapshots; never inferred
    val paymentMethod: String = "CASH",  // CASH or SINPE (declared, not settlement proof)
    val quoteVersion: Long = 1L,
    val fareBreakdownJson: String = "{}",
    val status: String,                  // OPEN, ACCEPTED, ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED
    val acceptedOfferId: String? = null,
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val assignedDriverPhone: String? = null,
    val assignedDriverVehicle: String? = null,
    val finalPrice: Double? = null,
    val passengerRating: Double? = null,  // Calificación dada al conductor
    val driverRating: Double? = null,     // Calificación dada al pasajero
    val createdAt: Long,
    val completedAt: Long? = null
)

@Entity(
    tableName = "ride_offers",
    indices = [
        Index(value = ["requestId"]),
        Index(value = ["driverId"])
    ]
)
@Serializable
data class RideOfferEntity(
    @PrimaryKey val offerId: String,
    val requestId: String,
    val driverId: String,
    val driverName: String,
    val driverPhone: String,
    val driverRating: Double,
    val driverTotalTrips: Int,
    val vehicleDescription: String,      // Ej: "Toyota Corolla 2018 Gris"
    val counterPrice: Double,            // Contraoferta del conductor (o el mismo precio)
    val currency: String,
    val estimatedArrivalMin: Int,        // Tiempo estimado de llegada
    val driverLatitude: Double,
    val driverLongitude: Double,
    val message: String? = null,
    val status: String,                  // PENDING, ACCEPTED, REJECTED
    val createdAt: Long
)

@Entity(
    tableName = "ride_chat_messages",
    indices = [
        Index(value = ["rideRequestId"])
    ]
)
@Serializable
data class RideChatMessageEntity(
    @PrimaryKey val messageId: String,
    val rideRequestId: String,
    val senderId: String,
    val senderName: String,
    val senderRole: String,              // PASSENGER o DRIVER
    val messageType: String,             // TEXT, AUDIO, PRESET
    val textContent: String? = null,
    val audioFilePath: String? = null,
    val audioDurationMs: Long? = null,
    val isRead: Boolean = false,
    val createdAt: Long
)

// ─── FEATURE 9 — Driver & Passenger Identity Verification (Uber-grade) ───────

/**
 * Full driver verification record. Every field is a local file path
 * pointing to a photo/document captured on-device.  Status tracks the
 * admin review lifecycle: PENDING → APPROVED | REJECTED.
 */
@Entity(tableName = "driver_verifications")
@Serializable
data class DriverVerificationEntity(
    @PrimaryKey val driverId: String,
    // ── Personal ──
    val fullName: String,
    val phone: String,
    val email: String,
    val dateOfBirth: String,                    // ISO-8601 yyyy-MM-dd
    // ── Vehicle ──
    val vehicleMake: String,                    // Marca
    val vehicleModel: String,                   // Modelo
    val vehicleYear: Int,                       // Año
    val vehicleColor: String,                   // Color
    val vehiclePlate: String,                   // Placa
    // ── Mandatory Documents (local file paths) ──
    val pathLicenciaFront: String,              // Licencia de conducir — frente
    val pathLicenciaBack: String,               // Licencia de conducir — reverso
    val pathCedulaFront: String,                // Cédula de identidad — frente
    val pathCedulaBack: String,                 // Cédula de identidad — reverso
    val pathHojaDelincuencia: String,           // Hoja de delincuencia
    val pathMarchamo: String,                   // Marchamo / Derecho de circulación
    val pathDekra: String,                      // DEKRA / RTV (Revisión Técnica)
    val pathSeguro: String,                     // Póliza de seguro vehicular
    // ── Selfie-Based Biometric Checks ──
    val pathSelfieProfile: String,              // Foto frontal de perfil
    val pathSelfieWithCedula: String,           // Selfie sosteniendo cédula al lado de la cara
    val pathSelfieWithLicencia: String,         // Selfie sosteniendo licencia al lado de la cara
    // ── Vehicle Photos ──
    val pathVehicleFront: String,               // Foto frontal del vehículo
    val pathVehicleBack: String,                // Foto trasera del vehículo
    val pathVehicleInterior: String,            // Foto del interior
    // ── Review Lifecycle ──
    val status: String,                         // PENDING, APPROVED, REJECTED
    val rejectionReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long = 0L,
    val approvedAt: Long? = null
)

/**
 * Passenger identity verification.  Lighter than driver — profile photo
 * plus front-of-ID and a selfie holding the ID.
 */
@Entity(tableName = "passenger_verifications")
@Serializable
data class PassengerVerificationEntity(
    @PrimaryKey val passengerId: String,
    val fullName: String,
    val phone: String,
    val pathProfilePhoto: String,               // Foto de perfil
    val pathCedulaFront: String,                // Cédula — frente
    val pathSelfieWithCedula: String,           // Selfie sosteniendo cédula al lado de la cara
    val status: String,                         // PENDING, APPROVED, REJECTED
    val rejectionReason: String? = null,
    val createdAt: Long,
    val approvedAt: Long? = null
)
