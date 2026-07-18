package com.elysium369.meet.core.knowledge

import kotlinx.serialization.Serializable

@Serializable
enum class ApplicabilityStatus {
    PRESENT_DOCUMENTED,
    PRESENT_CONDITIONAL,
    PRESENT_USER_VERIFIED,
    VERIFY_PHYSICALLY,
    ABSENT_DOCUMENTED,
    NOT_APPLICABLE_ARCHITECTURE,
    UNKNOWN_INSUFFICIENT_EVIDENCE,
    AFTERMARKET_INSTALLED,
    AFTERMARKET_POSSIBLE,
    REFERENCE_VEHICLE_ONLY
}

@Serializable
enum class KnowledgeScopeType {
    TARGET_VEHICLE,
    TARGET_VARIANT,
    VEHICLE_FAMILY,
    GENERIC_TECHNOLOGY,
    REFERENCE_VEHICLE,
    AFTERMARKET_RETROFIT
}

@Serializable
enum class ConfidenceLevel {
    VERIFIED,
    HIGH,
    MEDIUM,
    LOW,
    UNVERIFIED,
    CONFLICTED
}

@Serializable
enum class SourceAuthority {
    OEM_SERVICE_MANUAL,
    OEM_BODY_REPAIR_MANUAL,
    OEM_ELECTRICAL_DIAGRAM,
    OEM_PARTS_CATALOG,
    OEM_OWNER_MANUAL,
    OEM_TSB_RECALL,
    REGULATORY_STANDARD,
    OEM_SUPPLIER_DOCUMENTATION,
    TRUSTED_TECHNICAL_DATABASE,
    TRUSTED_SECONDARY_SOURCE,
    PHYSICAL_VEHICLE_OBSERVATION,
    USER_OBSERVATION,
    ENGINEERING_INFERENCE,
    UNKNOWN
}

@Serializable
enum class MeasurementVerificationStatus {
    VERIFIED,
    PENDING_DOCUMENT_REVIEW,
    REJECTED,
    CONFLICTED
}

@Serializable
data class SourceCitation(
    val sourceId: String,
    val sourceAuthority: SourceAuthority,
    val title: String,
    val publisher: String = "",
    val documentIdentifier: String = "",
    val edition: String = "",
    val publicationDate: String = "",
    val pageOrSection: String = "",
    val marketScope: String = "",
    val vehicleScope: String = "",
    val urlOrLocalReference: String = "",
    val contentHash: String,
    val licenseStatus: String,
    val retrievedAt: String,
    val reviewedBy: String = "",
    val reviewedAt: String = ""
)

@Serializable
data class VehicleKnowledgeProfile(
    val profileId: String,
    val make: String,
    val models: List<String>,
    val yearStart: Int,
    val yearEnd: Int,
    val engine: String,
    val transmission: String,
    val markets: List<String> = emptyList(),
    val applicability: ApplicabilityStatus,
    val confidence: ConfidenceLevel,
    val sourceCitationIds: List<String> = emptyList(),
    val requiresVinConfirmation: Boolean = true
)

@Serializable
data class TechnicalClaim(
    val claimId: String,
    val subjectId: String,
    val predicate: String,
    val value: String,
    val vehicleScopeId: String,
    val scopeType: KnowledgeScopeType,
    val applicability: ApplicabilityStatus,
    val confidence: ConfidenceLevel,
    val sourceCitationId: String? = null,
    val evidenceRequired: List<String> = emptyList(),
    val evidenceIds: List<String> = emptyList(),
    val requiresVinConfirmation: Boolean = false,
    val requiresOemConfirmation: Boolean = false,
    val requiresVisualConfirmation: Boolean = false,
    val referenceVehicle: String? = null,
    val doNotTransferSpecsFromReference: Boolean = true
)

@Serializable
data class MeasurementSpecification(
    val measurementId: String,
    val quantityType: String,
    val minimumValue: Double? = null,
    val nominalValue: Double? = null,
    val maximumValue: Double? = null,
    val unitCode: String,
    val measurementCondition: String,
    val temperatureCondition: String = "",
    val engineState: String = "",
    val ignitionState: String = "",
    val connectorState: String = "",
    val measurementPoints: List<String> = emptyList(),
    val requiredInstrument: String,
    val tolerance: String,
    val sourceClaimId: String? = null,
    val verificationStatus: MeasurementVerificationStatus
) {
    fun hasNumericValue(): Boolean =
        minimumValue != null || nominalValue != null || maximumValue != null
}

@Serializable
enum class KnowledgeConflictStatus {
    OPEN,
    UNDER_REVIEW,
    RESOLVED,
    REJECTED
}

@Serializable
data class KnowledgeConflict(
    val conflictId: String,
    val claimIds: List<String>,
    val reason: String,
    val status: KnowledgeConflictStatus = KnowledgeConflictStatus.OPEN,
    val resolution: String = ""
)

data class ApplicabilityEvidence(
    val vinConfirmed: Boolean = false,
    val oemConfirmed: Boolean = false,
    val visualConfirmed: Boolean = false,
    val physicalEvidenceIds: Set<String> = emptySet()
)

data class ApplicabilityResolution(
    val effectiveStatus: ApplicabilityStatus,
    val canUseAsVehicleFact: Boolean,
    val missingEvidence: List<String>,
    val explanation: String
)

enum class KnowledgeIssueSeverity {
    INFO,
    WARNING,
    BLOCKING
}

data class KnowledgeValidationIssue(
    val code: String,
    val message: String,
    val severity: KnowledgeIssueSeverity,
    val recordId: String
)
