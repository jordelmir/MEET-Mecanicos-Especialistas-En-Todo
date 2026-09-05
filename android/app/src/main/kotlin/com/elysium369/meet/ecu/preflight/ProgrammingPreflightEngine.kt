package com.elysium369.meet.ecu.preflight

import com.elysium369.meet.ecu.domain.EcuIdentityProfile
import com.elysium369.meet.ecu.domain.FirmwareArtifact
import com.elysium369.meet.ecu.domain.FirmwareArtifactType
import com.elysium369.meet.ecu.domain.ProgrammingCapabilityPack
import com.elysium369.meet.ecu.domain.RecoveryTier

enum class PreflightCheckStatus {
    VERIFIED,
    FAILED,
    UNKNOWN,
}

data class PreflightCheckItem(
    val checkId: String,
    val description: String,
    val status: PreflightCheckStatus,
    val failureReason: String? = null,
)

data class PreflightEvaluationReport(
    val passed: Boolean,
    val checks: List<PreflightCheckItem>,
) {
    val blockingReasons: List<String>
        get() = checks.filter { it.status != PreflightCheckStatus.VERIFIED }.map { "${it.checkId}: ${it.failureReason ?: "Status is ${it.status}"}" }
}

data class PreflightEnvironmentContext(
    val observedVoltage: Double?,
    val isExternalStabilizerConnected: Boolean,
    val roundTripLatencyMs: Long?,
    val frameErrorRate: Double,
    val operatorConfirmed: Boolean,
    val authenticatedPrincipalId: String?,
)

object ProgrammingPreflightEngine {

    fun evaluate(
        profile: EcuIdentityProfile,
        pack: ProgrammingCapabilityPack,
        targetArtifact: FirmwareArtifact,
        originalArtifact: FirmwareArtifact?,
        environment: PreflightEnvironmentContext,
    ): PreflightEvaluationReport {
        val checks = mutableListOf<PreflightCheckItem>()

        // 1. Identity & Applicability Check
        val identityApplicable = pack.isApplicableTo(profile)
        checks.add(
            PreflightCheckItem(
                checkId = "PREFLIGHT_IDENTITY_MATCH",
                description = "ECU hardware and software match capability pack applicability",
                status = if (identityApplicable) PreflightCheckStatus.VERIFIED else PreflightCheckStatus.FAILED,
                failureReason = if (!identityApplicable) "ECU ${profile.compatibilityFingerprint} not compatible with pack ${pack.packId}" else null
            )
        )

        // 2. Power & Electrical Stability Check
        val voltage = environment.observedVoltage
        val powerStatus = when {
            voltage == null -> PreflightCheckStatus.UNKNOWN
            voltage < pack.powerPolicy.minVoltageVolts -> PreflightCheckStatus.FAILED
            voltage > pack.powerPolicy.maxVoltageVolts -> PreflightCheckStatus.FAILED
            pack.powerPolicy.requireExternalStabilizer && !environment.isExternalStabilizerConnected -> PreflightCheckStatus.FAILED
            else -> PreflightCheckStatus.VERIFIED
        }
        checks.add(
            PreflightCheckItem(
                checkId = "PREFLIGHT_ELECTRICAL_STABILITY",
                description = "Battery voltage between ${pack.powerPolicy.minVoltageVolts}V and ${pack.powerPolicy.maxVoltageVolts}V with stabilizer",
                status = powerStatus,
                failureReason = when (powerStatus) {
                    PreflightCheckStatus.UNKNOWN -> "Battery voltage could not be deterministically measured"
                    PreflightCheckStatus.FAILED -> "Observed voltage ${voltage ?: 0.0}V out of range or stabilizer missing"
                    else -> null
                }
            )
        )

        // 3. Communication Health
        val latency = environment.roundTripLatencyMs
        val commStatus = when {
            latency == null -> PreflightCheckStatus.UNKNOWN
            latency > 150L -> PreflightCheckStatus.FAILED
            environment.frameErrorRate > 0.01 -> PreflightCheckStatus.FAILED
            else -> PreflightCheckStatus.VERIFIED
        }
        checks.add(
            PreflightCheckItem(
                checkId = "PREFLIGHT_COMM_HEALTH",
                description = "Bus round-trip latency < 150ms and error rate < 1%",
                status = commStatus,
                failureReason = when (commStatus) {
                    PreflightCheckStatus.UNKNOWN -> "Communication health metrics not available"
                    PreflightCheckStatus.FAILED -> "High latency (${latency}ms) or packet drop rate (${environment.frameErrorRate * 100}%)"
                    else -> null
                }
            )
        )

        // 4. Artifact & Original Backup Integrity
        val artifactMatches = targetArtifact.ecuFingerprint.equals(profile.compatibilityFingerprint, ignoreCase = true) ||
                              targetArtifact.ecuFingerprint.startsWith(profile.ecuFamily)
        val originalPresent = originalArtifact != null && originalArtifact.artifactType == FirmwareArtifactType.ORIGINAL_READBACK
        val artifactStatus = if (artifactMatches && originalPresent) PreflightCheckStatus.VERIFIED else PreflightCheckStatus.FAILED
        checks.add(
            PreflightCheckItem(
                checkId = "PREFLIGHT_ARTIFACT_INTEGRITY",
                description = "Valid target firmware with immutable original backup present",
                status = artifactStatus,
                failureReason = when {
                    !originalPresent -> "Immutable original readback backup is missing; refusing destructive programming"
                    !artifactMatches -> "Target artifact ${targetArtifact.artifactId} is not bound to ECU fingerprint ${profile.compatibilityFingerprint}"
                    else -> null
                }
            )
        )

        // 5. Recovery Plan Feasibility
        val recoveryViable = pack.recoveryPlan.tier != RecoveryTier.UNRECOVERABLE
        checks.add(
            PreflightCheckItem(
                checkId = "PREFLIGHT_RECOVERY_FEASIBILITY",
                description = "ECU capability pack contains verified recovery strategy",
                status = if (recoveryViable) PreflightCheckStatus.VERIFIED else PreflightCheckStatus.FAILED,
                failureReason = if (!recoveryViable) "No recovery procedure available for this ECU family" else null
            )
        )

        // 6. Explicit Operator Authorization
        val authStatus = if (environment.operatorConfirmed && !environment.authenticatedPrincipalId.isNullOrBlank()) {
            PreflightCheckStatus.VERIFIED
        } else {
            PreflightCheckStatus.FAILED
        }
        checks.add(
            PreflightCheckItem(
                checkId = "PREFLIGHT_OPERATOR_AUTHORIZATION",
                description = "Authenticated technician explicitly confirmed irreversible programming risk",
                status = authStatus,
                failureReason = if (authStatus != PreflightCheckStatus.VERIFIED) "Technician signature or explicit confirmation missing" else null
            )
        )

        val passed = checks.all { it.status == PreflightCheckStatus.VERIFIED }
        return PreflightEvaluationReport(passed = passed, checks = checks)
    }
}
