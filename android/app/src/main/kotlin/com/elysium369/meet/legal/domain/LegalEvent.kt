package com.elysium369.meet.legal.domain

import com.elysium369.meet.presence.PresenceLocation
import java.util.UUID

/**
 * LegalEvent — Full legal event model per Master Order section 76.
 *
 * Laws:
 * - Unknown remains UNKNOWN
 * - AI helps organize, never invents evidence
 * - Original evidence is immutable
 * - Chain of custody preserved
 */
enum class LegalEventType {
    INCIDENT,
    ACCIDENT,
    THEFT,
    VANDALISM,
    FRAUD,
    MECHANICAL_FAILURE,
    ROAD_HAZARD,
    WEATHER,
    PERSONAL_INJURY,
    PROPERTY_DAMAGE,
    POLICE_INTERACTION,
    INSURANCE_CLAIM,
    LEGAL_PROCEEDING,
    SERVICE_DISPUTE,
    PAYMENT_DISPUTE,
    CONTRACT_BREACH,
    WITNESS_OBSERVATION,
    EVIDENCE_COLLECTION,
    OTHER
}

enum class LegalEventTruthState {
    USER_STATED,
    WITNESS_CONFIRMED,
    DOCUMENT_OBSERVED,
    PHOTO_OBSERVED,
    VIDEO_OBSERVED,
    AUDIO_OBSERVED,
    OBD_OBSERVED,
    GPS_OBSERVED,
    AI_INFERRED,
    PROFESSIONAL_VERIFIED,
    COURT_CERTIFIED,
    UNKNOWN
}

enum class LegalPersonRole {
    WITNESS,
    VICTIM,
    SUSPECT,
    OFFICER,
    MECHANIC,
    INSURANCE_AGENT,
    LAWYER,
    JUDGE,
    PASSENGER,
    DRIVER,
    OWNER,
    THIRD_PARTY,
    OTHER
}

data class LegalEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val caseId: String? = null,
    val title: String,
    val description: String,
    val eventDate: String, // ISO date
    val startTime: String? = null, // ISO time
    val endTime: String? = null,
    val timezone: String = "America/Costa_Rica",
    val recordedAtEpochMs: Long = System.currentTimeMillis(),
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val location: PresenceLocation? = null,
    val eventType: LegalEventType,
    val people: List<LegalPerson> = emptyList(),
    val organizations: List<LegalOrganization> = emptyList(),
    val vehicleIds: List<String> = emptyList(),
    val assets: List<String> = emptyList(),
    val witnesses: List<LegalWitness> = emptyList(),
    val cameras: List<CameraObservation> = emptyList(),
    val documents: List<String> = emptyList(),
    val photos: List<String> = emptyList(),
    val video: List<String> = emptyList(),
    val audio: List<String> = emptyList(),
    val messages: List<String> = emptyList(),
    val calls: List<String> = emptyList(),
    val transactions: List<String> = emptyList(),
    val expenses: List<LegalExpense> = emptyList(),
    val damages: List<LegalDamage> = emptyList(),
    val relatedEventIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val source: String = "USER_INPUT",
    val truthState: LegalEventTruthState = LegalEventTruthState.USER_STATED,
    val author: String,
) {
    val isTerminal: Boolean get() = truthState in listOf(
        LegalEventTruthState.COURT_CERTIFIED,
        LegalEventTruthState.PROFESSIONAL_VERIFIED
    )
}

data class LegalPerson(
    val personId: String = UUID.randomUUID().toString(),
    val name: String,
    val role: LegalPersonRole,
    val contactPoints: List<LegalContactPoint> = emptyList(),
    val notes: String? = null,
)

data class LegalContactPoint(
    val type: String, // phone, email, address
    val value: String,
    val isPrimary: Boolean = false,
    val verifiedAtEpochMs: Long? = null,
    val source: String = "USER_INPUT",
)

data class LegalOrganization(
    val orgId: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String, // insurance, police, workshop, government, other
    val contactPoints: List<LegalContactPoint> = emptyList(),
)

data class LegalWitness(
    val witnessId: String = UUID.randomUUID().toString(),
    val name: String?,
    val contactInfo: String?,
    val statement: String?,
    val observedAtEpochMs: Long,
    val location: PresenceLocation? = null,
)

data class LegalExpense(
    val expenseId: String = UUID.randomUUID().toString(),
    val description: String,
    val amountMinor: Long,
    val currency: String = "CRC",
    val paidAtEpochMs: Long,
    val paidBy: String? = null,
    val category: String? = null,
    val receiptEvidenceId: String? = null,
)

data class LegalDamage(
    val damageId: String = UUID.randomUUID().toString(),
    val description: String,
    val estimatedCostMinor: Long? = null,
    val currency: String = "CRC",
    val severity: String, // minor, moderate, major, total_loss
    val photos: List<String> = emptyList(),
)

data class CameraObservation(
    val observationId: String = UUID.randomUUID().toString(),
    val location: com.elysium369.meet.presence.PresenceLocation?,
    val ownerOrg: String? = null,
    val orientation: String? = null,
    val coverageApprox: String? = null,
    val observedAtEpochMs: Long,
    val stillPresent: Boolean = true,
    val footageContact: String? = null,
    val notes: String? = null,
    val photoRef: String? = null,
)

data class LegalCase(
    val caseId: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val eventIds: List<String> = emptyList(),
    val personIds: List<String> = emptyList(),
    val organizationIds: List<String> = emptyList(),
    val vehicleIds: List<String> = emptyList(),
    val evidenceIds: List<String> = emptyList(),
    val status: LegalCaseStatus = LegalCaseStatus.OPEN,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

enum class LegalCaseStatus {
    OPEN, ACTIVE, RESOLVED, CLOSED, ARCHIVED
}
