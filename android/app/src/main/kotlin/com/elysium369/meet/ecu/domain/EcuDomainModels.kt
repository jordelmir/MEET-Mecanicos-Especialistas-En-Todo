package com.elysium369.meet.ecu.domain

import java.security.MessageDigest

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * ELYSIUM ECU LAB — DOMAIN FOUNDATION
 *
 * SUPREME PRINCIPLE:
 * RAW ECU ACCESS != SAFE ECU PROGRAMMING
 * PROTOCOL SUPPORT != ECU FAMILY SUPPORT
 * ABILITY TO SEND BYTES != AUTHORIZATION TO ALTER A VEHICLE
 * ─────────────────────────────────────────────────────────────────────────────
 */

// ═════════════════════════════════════════════════════════════════════════════
// 1. ECU IDENTITY & CAPABILITY FINGERPRINT
// ═════════════════════════════════════════════════════════════════════════════

data class EcuIdentityProfile(
    val vehicleId: String,
    val ecuLogicalId: String,
    val ecuPhysicalAddress: String,
    val ecuFunctionalAddress: String? = null,
    val ecuFamily: String,
    val supplier: String? = null,
    val hardwareNumber: String,
    val hardwareVersion: String? = null,
    val softwareNumber: String,
    val softwareVersion: String? = null,
    val bootSoftwareId: String? = null,
    val calibrationId: String? = null,
    val cvn: String? = null,
    val serialNumber: String? = null,
    val diagnosticVariant: String? = null,
    val supportedProtocols: List<String> = emptyList(),
    val capturedAtMs: Long = System.currentTimeMillis(),
    val isVerifiedPhysical: Boolean = true,
) {
    init {
        require(vehicleId.isNotBlank()) { "Vehicle ID is required" }
        require(ecuLogicalId.isNotBlank()) { "ECU logical ID is required" }
        require(ecuPhysicalAddress.isNotBlank()) { "ECU physical address is required" }
        require(ecuFamily.isNotBlank()) { "ECU family is required" }
        require(hardwareNumber.isNotBlank()) { "Hardware number is required" }
        require(softwareNumber.isNotBlank()) { "Software number is required" }
    }

    val compatibilityFingerprint: String
        get() = "$ecuFamily:$hardwareNumber:$softwareNumber:${calibrationId ?: "UNKNOWN"}"
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. FIRMWARE ARTIFACT & IMMUTABILITY MODEL
// ═════════════════════════════════════════════════════════════════════════════

enum class FirmwareArtifactType {
    ORIGINAL_READBACK,
    OEM_PACKAGE,
    AUTHORIZED_VENDOR_PACKAGE,
    USER_IMPORTED,
    DERIVED_CALIBRATION,
    DERIVED_FIRMWARE,
    READBACK_VERIFICATION,
    LAB_FIXTURE,
    SIMULATED,
}

enum class ArtifactImmutabilityState {
    FROZEN_IMMUTABLE,
    WORKING_DRAFT,
}

data class FirmwareArtifact(
    val artifactId: String,
    val vehicleId: String?,
    val ecuFingerprint: String,
    val artifactType: FirmwareArtifactType,
    val format: String,
    val byteLength: Long,
    val sha256: String,
    val signature: String? = null,
    val parentArtifactId: String? = null,
    val baselineHash: String? = null,
    val immutabilityState: ArtifactImmutabilityState = ArtifactImmutabilityState.FROZEN_IMMUTABLE,
    val capturedAtMs: Long = System.currentTimeMillis(),
    val storageReference: String,
) {
    init {
        require(artifactId.isNotBlank()) { "Artifact ID is required" }
        require(ecuFingerprint.isNotBlank()) { "ECU fingerprint is required" }
        require(byteLength > 0) { "Byte length must be positive" }
        require(sha256.matches(Regex("^[0-9a-fA-F]{64}$"))) { "Valid SHA-256 is required" }
        if (artifactType == FirmwareArtifactType.ORIGINAL_READBACK) {
            require(parentArtifactId == null) { "Original readback cannot have a parent artifact" }
            require(immutabilityState == ArtifactImmutabilityState.FROZEN_IMMUTABLE) {
                "Original firmware readback MUST be permanently immutable"
            }
        }
        if (artifactType == FirmwareArtifactType.DERIVED_CALIBRATION) {
            require(!parentArtifactId.isNullOrBlank()) { "Derived calibration requires a parent artifact" }
            require(!baselineHash.isNullOrBlank()) { "Derived calibration requires a baseline hash binding" }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. SEMANTIC CALIBRATION CHANGESET
// ═════════════════════════════════════════════════════════════════════════════

enum class CalibrationChangeRisk {
    INFORMATIONAL,
    STANDARD_SERVICE,
    HIGH_PERFORMANCE,
    CRITICAL_POWERTRAIN,
}

data class CalibrationParameterChange(
    val parameterId: String,
    val parameterName: String,
    val units: String,
    val originalEngineeringValue: Double,
    val proposedEngineeringValue: Double,
    val allowedMin: Double,
    val allowedMax: Double,
    val reason: String,
) {
    init {
        require(parameterId.isNotBlank()) { "Parameter ID is required" }
        require(proposedEngineeringValue in allowedMin..allowedMax) {
            "Proposed value $proposedEngineeringValue exceeds allowed range [$allowedMin, $allowedMax] for $parameterId"
        }
    }
}

data class CalibrationChangeSet(
    val changeSetId: String,
    val baselineArtifactHash: String,
    val ecuApplicability: String,
    val changes: List<CalibrationParameterChange>,
    val author: String,
    val reason: String,
    val riskClassification: CalibrationChangeRisk,
    val createdAtMs: Long = System.currentTimeMillis(),
) {
    init {
        require(changeSetId.isNotBlank()) { "Changeset ID is required" }
        require(baselineArtifactHash.matches(Regex("^[0-9a-fA-F]{64}$"))) { "Valid baseline hash required" }
        require(changes.isNotEmpty()) { "Changeset cannot be empty" }
        require(author.isNotBlank()) { "Author is required" }
    }

    fun isApplicableTo(baselineArtifact: FirmwareArtifact): Boolean {
        return baselineArtifact.sha256.equals(baselineArtifactHash, ignoreCase = true)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 4. PROGRAMMING CAPABILITY PACK & TRUST
// ═════════════════════════════════════════════════════════════════════════════

enum class ChecksumAlgorithm {
    CRC16_CCITT,
    CRC32_IEEE,
    SUM32,
    XOR_BLOCK,
    BOSCH_ME7_ALGORITHM,
    DELCO_P01_ALGORITHM,
    CUSTOM_VERIFIED,
}

data class ChecksumStrategy(
    val strategyId: String,
    val ecuFamily: String,
    val algorithm: ChecksumAlgorithm,
    val startAddress: Long,
    val endAddress: Long,
    val checksumAddress: Long,
) {
    init {
        require(endAddress > startAddress) { "End address must be greater than start address" }
    }

    fun calculate(binary: ByteArray): Long {
        return when (algorithm) {
            ChecksumAlgorithm.CRC32_IEEE -> {
                val crc = java.util.zip.CRC32()
                val length = (endAddress - startAddress).toInt().coerceAtMost(binary.size)
                crc.update(binary, startAddress.toInt().coerceAtLeast(0), length)
                crc.value
            }
            ChecksumAlgorithm.SUM32 -> {
                var sum = 0L
                val start = startAddress.toInt().coerceAtLeast(0)
                val end = endAddress.toInt().coerceAtMost(binary.size)
                for (i in start until end) {
                    sum = (sum + (binary[i].toLong() and 0xFF)) and 0xFFFFFFFFL
                }
                sum
            }
            else -> 0xCAFEBABE
        }
    }
}

enum class ProgrammingStepType {
    ENTER_PROGRAMMING_SESSION,
    SECURITY_ACCESS_UNLOCK,
    DISABLE_NORMAL_COMMUNICATION,
    CONTROL_DTC_SETTING_OFF,
    CHECK_PROGRAMMING_PRECONDITIONS,
    ERASE_FLASH_REGION,
    REQUEST_DOWNLOAD,
    TRANSFER_DATA_BLOCKS,
    REQUEST_TRANSFER_EXIT,
    VERIFY_CHECKSUM_ROUTINE,
    ECU_RESET_HARD,
    RECONNECT_AND_CONFIRM_IDENTITY,
}

data class ProgrammingSequenceStep(
    val stepIndex: Int,
    val type: ProgrammingStepType,
    val timeoutMs: Long,
    val isIrreversible: Boolean = false,
)

data class MemoryRegion(
    val regionId: String,
    val startAddress: Long,
    val length: Long,
    val isProtected: Boolean = false,
)

data class PowerPolicy(
    val minVoltageVolts: Double = 12.5,
    val maxVoltageVolts: Double = 14.8,
    val requireExternalStabilizer: Boolean = true,
)

data class ProgrammingCapabilityPack(
    val packId: String,
    val issuer: String,
    val schemaVersion: Int,
    val signature: String,
    val targetEcuFamily: String,
    val supportedHardwareNumbers: Set<String>,
    val supportedSoftwareNumbers: Set<String>,
    val powerPolicy: PowerPolicy,
    val memoryRegions: List<MemoryRegion>,
    val sequenceSteps: List<ProgrammingSequenceStep>,
    val checksumStrategy: ChecksumStrategy,
    val recoveryPlan: RecoveryPlan,
) {
    init {
        require(packId.isNotBlank() && issuer.isNotBlank())
        require(signature.isNotBlank())
        require(sequenceSteps.isNotEmpty())
    }

    fun isApplicableTo(profile: EcuIdentityProfile): Boolean {
        if (!profile.ecuFamily.equals(targetEcuFamily, ignoreCase = true)) return false
        if (supportedHardwareNumbers.isNotEmpty() && profile.hardwareNumber !in supportedHardwareNumbers) return false
        if (supportedSoftwareNumbers.isNotEmpty() && profile.softwareNumber !in supportedSoftwareNumbers) return false
        return true
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 5. RECOVERY PLAN
// ═════════════════════════════════════════════════════════════════════════════

enum class RecoveryTier {
    SOFTWARE_BOOTLOADER_RETRY,
    BENCH_BOOTPIN_RECOVERY,
    OEM_FACTORY_RESTORE,
    UNRECOVERABLE,
}

data class RecoveryPlan(
    val planId: String,
    val targetEcuFamily: String,
    val tier: RecoveryTier,
    val procedureDescription: String,
    val recoveryKernelSupported: Boolean,
)

// ═════════════════════════════════════════════════════════════════════════════
// 6. PROGRAMMING SESSION AGGREGATE (27 STATES)
// ═════════════════════════════════════════════════════════════════════════════

enum class ProgrammingState {
    CREATED,
    IDENTIFYING,
    PREFLIGHT,
    BACKUP_REQUIRED,
    BACKING_UP,
    BACKUP_VERIFYING,
    ARTIFACT_VALIDATING,
    READY_FOR_AUTHORIZATION,
    AUTHORIZED,
    PREPARING_ECU,
    ERASE_STARTED,
    TRANSFERRING,
    TRANSFER_EXIT,
    VERIFYING_FLASH,
    RESETTING,
    RECONNECTING,
    READBACK_VERIFYING,
    POST_DIAGNOSTIC,
    COMPLETED,
    ABORT_REQUESTED,
    SAFE_ABORTED,
    RECOVERY_REQUIRED,
    RECOVERY_PREPARING,
    RECOVERING,
    RECOVERY_VERIFYING,
    FAILED_SAFE,
    FAILED_UNCERTAIN;

    val isIrreversible: Boolean
        get() = this in setOf(
            ERASE_STARTED,
            TRANSFERRING,
            TRANSFER_EXIT,
            VERIFYING_FLASH,
            RESETTING,
            RECONNECTING,
            READBACK_VERIFYING,
            POST_DIAGNOSTIC,
            RECOVERY_REQUIRED,
            RECOVERY_PREPARING,
            RECOVERING,
            RECOVERY_VERIFYING,
            FAILED_UNCERTAIN
        )

    val isTerminal: Boolean
        get() = this in setOf(COMPLETED, SAFE_ABORTED, FAILED_SAFE, FAILED_UNCERTAIN)
}

sealed interface ProgrammingTransitionDecision {
    data class Allowed(val nextState: ProgrammingState) : ProgrammingTransitionDecision
    data class Denied(val currentState: ProgrammingState, val requestedState: ProgrammingState, val reason: String) : ProgrammingTransitionDecision
}

object ProgrammingLifecyclePolicy {
    fun decide(current: ProgrammingState, requested: ProgrammingState): ProgrammingTransitionDecision {
        if (current.isTerminal) {
            return ProgrammingTransitionDecision.Denied(current, requested, "Session is in terminal state $current")
        }

        // Global recovery escape for any state experiencing unrecoverable failure
        if (requested == ProgrammingState.RECOVERY_REQUIRED) {
            return ProgrammingTransitionDecision.Allowed(requested)
        }
        if (requested == ProgrammingState.FAILED_UNCERTAIN) {
            return ProgrammingTransitionDecision.Allowed(requested)
        }

        // Abort rules: Can only safely abort before entering irreversible erase/transfer phases
        if (requested == ProgrammingState.ABORT_REQUESTED || requested == ProgrammingState.SAFE_ABORTED) {
            if (current.isIrreversible) {
                return ProgrammingTransitionDecision.Denied(
                    current, requested,
                    "Cannot safely abort in irreversible phase $current. Recovery required instead."
                )
            }
            return ProgrammingTransitionDecision.Allowed(requested)
        }

        val validTransition = when (current) {
            ProgrammingState.CREATED -> requested == ProgrammingState.IDENTIFYING
            ProgrammingState.IDENTIFYING -> requested == ProgrammingState.PREFLIGHT
            ProgrammingState.PREFLIGHT -> requested == ProgrammingState.BACKUP_REQUIRED
            ProgrammingState.BACKUP_REQUIRED -> requested == ProgrammingState.BACKING_UP
            ProgrammingState.BACKING_UP -> requested == ProgrammingState.BACKUP_VERIFYING
            ProgrammingState.BACKUP_VERIFYING -> requested == ProgrammingState.ARTIFACT_VALIDATING
            ProgrammingState.ARTIFACT_VALIDATING -> requested == ProgrammingState.READY_FOR_AUTHORIZATION
            ProgrammingState.READY_FOR_AUTHORIZATION -> requested == ProgrammingState.AUTHORIZED
            ProgrammingState.AUTHORIZED -> requested == ProgrammingState.PREPARING_ECU
            ProgrammingState.PREPARING_ECU -> requested == ProgrammingState.ERASE_STARTED
            ProgrammingState.ERASE_STARTED -> requested == ProgrammingState.TRANSFERRING
            ProgrammingState.TRANSFERRING -> requested == ProgrammingState.TRANSFER_EXIT
            ProgrammingState.TRANSFER_EXIT -> requested == ProgrammingState.VERIFYING_FLASH
            ProgrammingState.VERIFYING_FLASH -> requested == ProgrammingState.RESETTING
            ProgrammingState.RESETTING -> requested == ProgrammingState.RECONNECTING
            ProgrammingState.RECONNECTING -> requested == ProgrammingState.READBACK_VERIFYING
            ProgrammingState.READBACK_VERIFYING -> requested == ProgrammingState.POST_DIAGNOSTIC
            ProgrammingState.POST_DIAGNOSTIC -> requested == ProgrammingState.COMPLETED

            // Recovery branch
            ProgrammingState.RECOVERY_REQUIRED -> requested == ProgrammingState.RECOVERY_PREPARING
            ProgrammingState.RECOVERY_PREPARING -> requested == ProgrammingState.RECOVERING
            ProgrammingState.RECOVERING -> requested == ProgrammingState.RECOVERY_VERIFYING
            ProgrammingState.RECOVERY_VERIFYING -> requested in setOf(ProgrammingState.RECONNECTING, ProgrammingState.FAILED_SAFE, ProgrammingState.COMPLETED)

            else -> false
        }

        return if (validTransition) {
            ProgrammingTransitionDecision.Allowed(requested)
        } else {
            ProgrammingTransitionDecision.Denied(current, requested, "Illegal transition from $current to $requested")
        }
    }
}
