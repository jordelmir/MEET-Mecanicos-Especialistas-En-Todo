package com.elysium369.meet.core.trust.engine

import com.elysium369.meet.core.identity.ActorCapability
import com.elysium369.meet.core.identity.TrustScope
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * ══════════════════════════════════════════════════════════════════════
 *  M E E T   T R U S T   C E N T E R   E N G I N E
 *  ──────────────────────────────────────────────────────────────
 *  Answers authoritative question: "¿Puedo confiar y permitir operar a este actor?"
 *
 *  - Identity, Licensure, Inspection (Dekra/RTV), and Insurance compliance.
 *  - Transparent risk scoring (never an opaque magic number): includes
 *    reason_codes, evidence, timestamp and appeal_status.
 *  - Authoritative capability gating: blocks DRIVE_RIDE if license is expired.
 *  - Deep-link integration with Command Center.
 * ══════════════════════════════════════════════════════════════════════
 */

@Serializable
enum class ComplianceStatus {
    VALID,
    EXPIRING_SOON,
    EXPIRED,
    REVOKED,
    PENDING_REVIEW,
}

@Serializable
data class DocumentRecord(
    val docId: String = UUID.randomUUID().toString(),
    val title: String,
    val type: String, // LICENSE, INSURANCE, INSPECTION_RTV, CRIMINAL_RECORD
    val status: ComplianceStatus,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val issuer: String,
    val verificationEvidenceHash: String? = null,
) {
    val daysUntilExpiration: Long
        get() {
            val diffMs = expiresAtEpochMs - System.currentTimeMillis()
            return (diffMs / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
        }

    val requiresAction: Boolean
        get() = status == ComplianceStatus.EXPIRED || status == ComplianceStatus.EXPIRING_SOON
}

@Serializable
data class TransparentRiskScore(
    val score: Int, // 0 - 100
    val reasonCodes: List<String>,
    val evidenceList: List<String>,
    val ruleVersion: String = "v2.4",
    val calculatedAtEpochMs: Long = System.currentTimeMillis(),
    val appealStatus: String? = null,
)

@Serializable
sealed interface CapabilityDecision {
    data object Granted : CapabilityDecision
    data class Denied(val reason: String, val requiredDocumentType: String? = null) : CapabilityDecision
}

@Serializable
data class ActorTrustProfile(
    val principalId: String,
    val organizationId: String? = null,
    val identityVerified: Boolean,
    val phoneVerified: Boolean,
    val emailVerified: Boolean,
    val documents: List<DocumentRecord>,
    val riskScore: TransparentRiskScore,
    val openIncidentsCount: Int = 0,
    val financialRiskFlags: List<String> = emptyList(),
) {
    fun canOperateCapability(capability: ActorCapability): CapabilityDecision {
        if (!identityVerified) {
            return CapabilityDecision.Denied("Identidad no verificada en Centro de Confianza")
        }

        if (capability == ActorCapability.DRIVE_RIDE) {
            val license = documents.firstOrNull { it.type == "LICENSE" }
            if (license == null || license.status == ComplianceStatus.EXPIRED || license.status == ComplianceStatus.REVOKED) {
                return CapabilityDecision.Denied("Licencia de conducir vencida o no registrada", "LICENSE")
            }
            val insurance = documents.firstOrNull { it.type == "INSURANCE" }
            if (insurance != null && insurance.status == ComplianceStatus.EXPIRED) {
                return CapabilityDecision.Denied("Póliza de seguro vehicular vencida", "INSURANCE")
            }
        }

        if (capability == ActorCapability.PROVIDE_REPAIR) {
            val certification = documents.firstOrNull { it.type == "CERTIFICATION" }
            if (certification != null && certification.status == ComplianceStatus.EXPIRED) {
                return CapabilityDecision.Denied("Certificación técnica profesional vencida", "CERTIFICATION")
            }
        }

        if (capability == ActorCapability.PROVIDE_TOW) {
            val towPermit = documents.firstOrNull { it.type == "TOW_PERMIT" || it.type == "INSPECTION_RTV" }
            if (towPermit != null && towPermit.status == ComplianceStatus.EXPIRED) {
                return CapabilityDecision.Denied("Permiso o inspección técnica de grúa vencida", "TOW_PERMIT")
            }
        }

        return CapabilityDecision.Granted
    }
}

class TrustCenterEngine {

    /**
     * Retrieves an actor's authoritative trust profile, enforcing TrustScope boundaries.
     */
    fun getProfile(scope: TrustScope, targetPrincipalId: String, targetOrgId: String?): ActorTrustProfile {
        require(scope.canInspect(targetPrincipalId, targetOrgId)) {
            "Unauthorized access to trust profile for principal $targetPrincipalId"
        }

        val sampleDocs = listOf(
            DocumentRecord(
                title = "Licencia de Conducir B1/B2",
                type = "LICENSE",
                status = ComplianceStatus.VALID,
                issuedAtEpochMs = System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 180,
                expiresAtEpochMs = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 320,
                issuer = "COSEVI - MOPT",
                verificationEvidenceHash = "sha256-cosevi-88910a",
            ),
            DocumentRecord(
                title = "Inspección Técnica Vehicular",
                type = "INSPECTION_RTV",
                status = ComplianceStatus.VALID,
                issuedAtEpochMs = System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 60,
                expiresAtEpochMs = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 300,
                issuer = "Dekra Costa Rica",
                verificationEvidenceHash = "sha256-dekra-2026-b",
            ),
            DocumentRecord(
                title = "Póliza de Seguro de Responsabilidad",
                type = "INSURANCE",
                status = ComplianceStatus.EXPIRING_SOON,
                issuedAtEpochMs = System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 340,
                expiresAtEpochMs = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 25, // 25 days left
                issuer = "INS - Instituto Nacional de Seguros",
                verificationEvidenceHash = "sha256-ins-policy-77a",
            ),
        )

        return ActorTrustProfile(
            principalId = targetPrincipalId,
            organizationId = targetOrgId,
            identityVerified = true,
            phoneVerified = true,
            emailVerified = true,
            documents = sampleDocs,
            riskScore = TransparentRiskScore(
                score = 94,
                reasonCodes = listOf("IDENTITY_BIOMETRIC_MATCH", "VALID_GOVERNMENT_DOCUMENTS", "CLEAN_INCIDENT_HISTORY"),
                evidenceList = listOf("MOPT DB Match 2026", "Zero safety incident flags", "184 trips completed with 4.96 rating"),
            ),
            openIncidentsCount = 0,
            financialRiskFlags = emptyList(),
        )
    }
}
