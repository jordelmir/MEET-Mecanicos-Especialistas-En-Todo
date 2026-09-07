package com.elysium369.meet.ride.accessibility

import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  R I D E   A C C E S S I B I L I T Y   E N G I N E
 *  ──────────────────────────────────────────────────────
 *  Inclusive mobility for EVERYONE.
 *
 *  "La dignidad de viajar no es privilegio — es derecho."
 *
 *  Features:
 *  ✅ Wheelchair-accessible vehicle filter
 *  ✅ Visual/hearing/motor impairment accommodations
 *  ✅ Companion authorization (minors, elderly)
 *  ✅ Service animal declaration
 *  ✅ Accessible meeting point instructions
 *  ✅ Communication preferences (text-only, large text)
 *  ✅ Driver accessibility training verification
 *  ✅ Vehicle equipment verification (ramp, anchor, etc.)
 *  ✅ Live trip sharing with caregiver
 * ══════════════════════════════════════════════════════════════════════
 */

// ─── Accessibility Needs ───

enum class AccessibilityNeed {
    WHEELCHAIR_STANDARD,     // Manual wheelchair
    WHEELCHAIR_ELECTRIC,     // Electric wheelchair (needs ramp)
    WALKER_OR_CANE,          // Walking assistance device
    VISUAL_IMPAIRMENT,       // Blind or low vision
    HEARING_IMPAIRMENT,      // Deaf or hard of hearing
    COGNITIVE_ASSISTANCE,    // Cognitive/developmental
    REDUCED_MOBILITY,        // Can walk but with difficulty
    SERVICE_ANIMAL,          // Service animal
    CHILD_SEAT_INFANT,       // Infant car seat (0-12 months)
    CHILD_SEAT_TODDLER,     // Toddler seat (1-4 years)
    CHILD_SEAT_BOOSTER,     // Booster seat (4-8 years)
    ELDERLY_ASSISTANCE,      // Elderly passenger (extra time/patience)
    OXYGEN_EQUIPMENT,        // Portable oxygen
}

// ─── Communication Preferences ───

enum class CommunicationPreference {
    STANDARD,           // Normal voice + text
    TEXT_ONLY,          // No voice calls, text messages only
    LARGE_TEXT,         // Larger text in all communications
    VOICE_ONLY,         // Screen reader friendly, minimal text
    SIGN_LANGUAGE,      // Driver knows sign language
    SIMPLIFIED,         // Simple language, clear instructions
}

// ─── Vehicle Equipment ───

enum class VehicleEquipment {
    WHEELCHAIR_RAMP,         // Foldable ramp
    WHEELCHAIR_LIFT,         // Hydraulic lift
    WHEELCHAIR_ANCHOR,       // Wheelchair tie-down system
    HAND_CONTROLS,           // Modified driving controls
    SWIVEL_SEAT,             // Rotating passenger seat
    GRAB_BARS,               // Assist handles
    LOWERED_FLOOR,           // Low-floor vehicle
    INFANT_SEAT_INSTALLED,   // Pre-installed infant seat
    TODDLER_SEAT_INSTALLED,  // Pre-installed toddler seat
    BOOSTER_INSTALLED,       // Pre-installed booster
    EXTRA_TRUNK_SPACE,       // For walkers/wheelchairs
}

// ─── Accessibility Profile ───

@Serializable
data class AccessibilityProfile(
    val userId: String,
    val needs: Set<AccessibilityNeed> = emptySet(),
    val communicationPref: CommunicationPreference = CommunicationPreference.STANDARD,
    val companionRequired: Boolean = false,
    val companionName: String? = null,
    val companionPhone: String? = null,
    val companionRelationship: CompanionRelationship? = null,
    val caregiverShareEnabled: Boolean = false,
    val caregiverId: String? = null,
    val meetingPointInstructions: String = "",
    val additionalNotes: String = "",
    val isActive: Boolean = true,
) {
    val needsAccessibleVehicle: Boolean
        get() = needs.any {
            it in listOf(
                AccessibilityNeed.WHEELCHAIR_STANDARD,
                AccessibilityNeed.WHEELCHAIR_ELECTRIC,
                AccessibilityNeed.OXYGEN_EQUIPMENT,
            )
        }

    val needsChildSeat: Boolean
        get() = needs.any {
            it in listOf(
                AccessibilityNeed.CHILD_SEAT_INFANT,
                AccessibilityNeed.CHILD_SEAT_TODDLER,
                AccessibilityNeed.CHILD_SEAT_BOOSTER,
            )
        }

    val hasCompanion: Boolean get() = companionRequired && companionName != null

    val summary: String
        get() = buildString {
            if (needs.isEmpty()) {
                append("Sin necesidades especiales declaradas")
                return@buildString
            }
            append("Necesidades: ")
            append(needs.joinToString(", ") { it.displayLabel })
            if (companionRequired) append(" · Acompañante: $companionName")
            if (communicationPref != CommunicationPreference.STANDARD) {
                append(" · Comunicación: ${communicationPref.displayLabel}")
            }
        }
}

enum class CompanionRelationship {
    PARENT,         // Parent of minor
    CHILD,          // Child of elderly
    CAREGIVER,      // Professional caregiver
    FAMILY,         // Other family member
    FRIEND,         // Trusted friend
}

// ─── Driver Accessibility Certification ───

@Serializable
data class DriverAccessibilityCert(
    val driverId: String,
    val trainedNeeds: Set<AccessibilityNeed>,
    val vehicleEquipment: Set<VehicleEquipment>,
    val certifiedAtEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long? = null,
    val languagesSpoken: List<String> = listOf("es"),
    val knowsSignLanguage: Boolean = false,
    val patienceRating: Double = 0.0,  // From accessibility-specific reviews
) {
    val isExpired: Boolean
        get() = expiresAtEpochMs != null && System.currentTimeMillis() > expiresAtEpochMs

    val isValid: Boolean get() = !isExpired

    fun canServe(needs: Set<AccessibilityNeed>): Boolean {
        val vehicleNeeds = needs.filter { it.requiresEquipment }
        val trainingNeeds = needs.filter { it.requiresTraining }

        val hasEquipment = vehicleNeeds.all { need ->
            need.requiredEquipment.any { it in vehicleEquipment }
        }
        val hasTrained = trainingNeeds.all { it in trainedNeeds }

        return hasEquipment && hasTrained
    }
}

// ─── Matching Result ───

data class AccessibilityMatchResult(
    val isCompatible: Boolean,
    val matchScore: Double,    // 0.0 - 1.0
    val missingEquipment: Set<VehicleEquipment>,
    val missingTraining: Set<AccessibilityNeed>,
    val driverMessage: String,
    val passengerMessage: String,
)

// ─── Engine ───

class RideAccessibilityEngine {

    private val profiles = mutableMapOf<String, AccessibilityProfile>()
    private val driverCerts = mutableMapOf<String, DriverAccessibilityCert>()

    // ─── Profiles ───

    fun createProfile(
        userId: String,
        needs: Set<AccessibilityNeed>,
        communicationPref: CommunicationPreference = CommunicationPreference.STANDARD,
        companionName: String? = null,
        companionPhone: String? = null,
        companionRelationship: CompanionRelationship? = null,
        meetingPointInstructions: String = "",
    ): AccessibilityProfile {
        val profile = AccessibilityProfile(
            userId = userId,
            needs = needs,
            communicationPref = communicationPref,
            companionRequired = companionName != null,
            companionName = companionName,
            companionPhone = companionPhone,
            companionRelationship = companionRelationship,
            meetingPointInstructions = meetingPointInstructions,
        )
        profiles[userId] = profile
        return profile
    }

    fun updateProfile(userId: String, update: (AccessibilityProfile) -> AccessibilityProfile): AccessibilityProfile? {
        val existing = profiles[userId] ?: return null
        val updated = update(existing)
        profiles[userId] = updated
        return updated
    }

    fun enableCaregiverSharing(userId: String, caregiverId: String): Boolean {
        val profile = profiles[userId] ?: return false
        profiles[userId] = profile.copy(
            caregiverShareEnabled = true,
            caregiverId = caregiverId,
        )
        return true
    }

    // ─── Driver Certification ───

    fun certifyDriver(
        driverId: String,
        trainedNeeds: Set<AccessibilityNeed>,
        vehicleEquipment: Set<VehicleEquipment>,
        knowsSignLanguage: Boolean = false,
    ): DriverAccessibilityCert {
        val cert = DriverAccessibilityCert(
            driverId = driverId,
            trainedNeeds = trainedNeeds,
            vehicleEquipment = vehicleEquipment,
            knowsSignLanguage = knowsSignLanguage,
        )
        driverCerts[driverId] = cert
        return cert
    }

    // ─── Matching ───

    fun matchDriverToPassenger(
        driverId: String,
        passengerId: String,
    ): AccessibilityMatchResult {
        val profile = profiles[passengerId]
        val cert = driverCerts[driverId]

        if (profile == null || profile.needs.isEmpty()) {
            return AccessibilityMatchResult(
                isCompatible = true, matchScore = 1.0,
                missingEquipment = emptySet(), missingTraining = emptySet(),
                driverMessage = "Pasajero sin necesidades especiales declaradas.",
                passengerMessage = "Tu conductor está listo.",
            )
        }

        if (cert == null) {
            return AccessibilityMatchResult(
                isCompatible = false, matchScore = 0.0,
                missingEquipment = emptySet(), missingTraining = profile.needs,
                driverMessage = "Este pasajero requiere asistencia especial. No tienes certificación.",
                passengerMessage = "Buscando conductor con certificación de accesibilidad...",
            )
        }

        val missingEquip = profile.needs
            .filter { it.requiresEquipment }
            .flatMap { it.requiredEquipment }
            .filter { it !in cert.vehicleEquipment }
            .toSet()

        val missingTrain = profile.needs
            .filter { it.requiresTraining && it !in cert.trainedNeeds }
            .toSet()

        val totalNeeds = profile.needs.size.toDouble()
        val metNeeds = totalNeeds - missingEquip.size - missingTrain.size
        val score = if (totalNeeds > 0) (metNeeds / totalNeeds).coerceIn(0.0, 1.0) else 1.0

        val compatible = missingEquip.isEmpty() && missingTrain.isEmpty()

        return AccessibilityMatchResult(
            isCompatible = compatible,
            matchScore = score,
            missingEquipment = missingEquip,
            missingTraining = missingTrain,
            driverMessage = if (compatible) {
                "Pasajero con necesidades especiales. ${profile.summary}"
            } else {
                "No compatible: faltan ${missingEquip.size + missingTrain.size} requisitos."
            },
            passengerMessage = if (compatible) {
                "Tu conductor está certificado para tus necesidades. ${
                    if (profile.meetingPointInstructions.isNotBlank())
                        "Instrucciones de encuentro enviadas." else ""
                }"
            } else {
                "Buscando conductor con equipo adecuado..."
            },
        )
    }

    // ─── Queries ───

    fun getProfile(userId: String): AccessibilityProfile? = profiles[userId]

    fun findCompatibleDrivers(passengerId: String): List<String> {
        val profile = profiles[passengerId] ?: return emptyList()
        return driverCerts.values
            .filter { it.isValid && it.canServe(profile.needs) }
            .map { it.driverId }
    }

    val totalProfiles: Int get() = profiles.size
    val totalCertifiedDrivers: Int get() = driverCerts.count { it.value.isValid }
}

// ─── Extension Properties ───

val AccessibilityNeed.displayLabel: String
    get() = when (this) {
        AccessibilityNeed.WHEELCHAIR_STANDARD -> "Silla de ruedas"
        AccessibilityNeed.WHEELCHAIR_ELECTRIC -> "Silla eléctrica"
        AccessibilityNeed.WALKER_OR_CANE -> "Andadera/bastón"
        AccessibilityNeed.VISUAL_IMPAIRMENT -> "Discapacidad visual"
        AccessibilityNeed.HEARING_IMPAIRMENT -> "Discapacidad auditiva"
        AccessibilityNeed.COGNITIVE_ASSISTANCE -> "Asistencia cognitiva"
        AccessibilityNeed.REDUCED_MOBILITY -> "Movilidad reducida"
        AccessibilityNeed.SERVICE_ANIMAL -> "Animal de servicio"
        AccessibilityNeed.CHILD_SEAT_INFANT -> "Silla de bebé"
        AccessibilityNeed.CHILD_SEAT_TODDLER -> "Silla de niño"
        AccessibilityNeed.CHILD_SEAT_BOOSTER -> "Silla booster"
        AccessibilityNeed.ELDERLY_ASSISTANCE -> "Asistencia adulto mayor"
        AccessibilityNeed.OXYGEN_EQUIPMENT -> "Equipo de oxígeno"
    }

val AccessibilityNeed.requiresEquipment: Boolean
    get() = this in listOf(
        AccessibilityNeed.WHEELCHAIR_STANDARD,
        AccessibilityNeed.WHEELCHAIR_ELECTRIC,
        AccessibilityNeed.CHILD_SEAT_INFANT,
        AccessibilityNeed.CHILD_SEAT_TODDLER,
        AccessibilityNeed.CHILD_SEAT_BOOSTER,
        AccessibilityNeed.OXYGEN_EQUIPMENT,
    )

val AccessibilityNeed.requiresTraining: Boolean
    get() = this in listOf(
        AccessibilityNeed.VISUAL_IMPAIRMENT,
        AccessibilityNeed.HEARING_IMPAIRMENT,
        AccessibilityNeed.COGNITIVE_ASSISTANCE,
        AccessibilityNeed.ELDERLY_ASSISTANCE,
    )

val AccessibilityNeed.requiredEquipment: Set<VehicleEquipment>
    get() = when (this) {
        AccessibilityNeed.WHEELCHAIR_STANDARD -> setOf(VehicleEquipment.WHEELCHAIR_RAMP, VehicleEquipment.WHEELCHAIR_ANCHOR)
        AccessibilityNeed.WHEELCHAIR_ELECTRIC -> setOf(VehicleEquipment.WHEELCHAIR_LIFT, VehicleEquipment.WHEELCHAIR_ANCHOR)
        AccessibilityNeed.CHILD_SEAT_INFANT -> setOf(VehicleEquipment.INFANT_SEAT_INSTALLED)
        AccessibilityNeed.CHILD_SEAT_TODDLER -> setOf(VehicleEquipment.TODDLER_SEAT_INSTALLED)
        AccessibilityNeed.CHILD_SEAT_BOOSTER -> setOf(VehicleEquipment.BOOSTER_INSTALLED)
        AccessibilityNeed.OXYGEN_EQUIPMENT -> setOf(VehicleEquipment.EXTRA_TRUNK_SPACE)
        else -> emptySet()
    }

val CommunicationPreference.displayLabel: String
    get() = when (this) {
        CommunicationPreference.STANDARD -> "Estándar"
        CommunicationPreference.TEXT_ONLY -> "Solo texto"
        CommunicationPreference.LARGE_TEXT -> "Texto grande"
        CommunicationPreference.VOICE_ONLY -> "Solo voz"
        CommunicationPreference.SIGN_LANGUAGE -> "Lengua de señas"
        CommunicationPreference.SIMPLIFIED -> "Lenguaje simple"
    }
