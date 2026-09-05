package com.elysium369.meet.ecu

import com.elysium369.meet.ecu.compliance.ComplianceDecisionState
import com.elysium369.meet.ecu.compliance.EcuOperationCategory
import com.elysium369.meet.ecu.compliance.EcuOperationCompliancePolicy
import com.elysium369.meet.ecu.compliance.VehicleUsageContext
import com.elysium369.meet.ecu.domain.CalibrationChangeRisk
import com.elysium369.meet.ecu.domain.CalibrationChangeSet
import com.elysium369.meet.ecu.domain.CalibrationParameterChange
import com.elysium369.meet.ecu.domain.ChecksumAlgorithm
import com.elysium369.meet.ecu.domain.ChecksumStrategy
import com.elysium369.meet.ecu.domain.EcuIdentityProfile
import com.elysium369.meet.ecu.domain.FirmwareArtifact
import com.elysium369.meet.ecu.domain.FirmwareArtifactType
import com.elysium369.meet.ecu.domain.PowerPolicy
import com.elysium369.meet.ecu.domain.ProgrammingCapabilityPack
import com.elysium369.meet.ecu.domain.ProgrammingSequenceStep
import com.elysium369.meet.ecu.domain.ProgrammingStepType
import com.elysium369.meet.ecu.domain.RecoveryPlan
import com.elysium369.meet.ecu.domain.RecoveryTier
import com.elysium369.meet.ecu.preflight.PreflightCheckStatus
import com.elysium369.meet.ecu.preflight.PreflightEnvironmentContext
import com.elysium369.meet.ecu.preflight.ProgrammingPreflightEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcuComplianceAndArtifactTest {

    private val sampleProfile = EcuIdentityProfile(
        vehicleId = "VEH-AUDI-01",
        ecuLogicalId = "ECU_ENGINE",
        ecuPhysicalAddress = "0x7E0",
        ecuFamily = "BOSCH_ME7",
        hardwareNumber = "0261206123",
        softwareNumber = "1037359123",
        calibrationId = "CAL_AWT_STAGE0",
    )

    private val samplePack = ProgrammingCapabilityPack(
        packId = "PACK-ME7-VERIFIED-V1",
        issuer = "Elysium Vanguard Certified Authority",
        schemaVersion = 1,
        signature = "MEQCIB8Z3t4nJ...sample_ecdsa_sig...",
        targetEcuFamily = "BOSCH_ME7",
        supportedHardwareNumbers = setOf("0261206123"),
        supportedSoftwareNumbers = setOf("1037359123"),
        powerPolicy = PowerPolicy(minVoltageVolts = 12.5, maxVoltageVolts = 14.8, requireExternalStabilizer = true),
        memoryRegions = emptyList(),
        sequenceSteps = listOf(
            ProgrammingSequenceStep(1, ProgrammingStepType.ENTER_PROGRAMMING_SESSION, 5000),
            ProgrammingSequenceStep(2, ProgrammingStepType.ERASE_FLASH_REGION, 30000, isIrreversible = true),
        ),
        checksumStrategy = ChecksumStrategy("CHK-CRC32", "BOSCH_ME7", ChecksumAlgorithm.CRC32_IEEE, 0, 512 * 1024, 0x100),
        recoveryPlan = RecoveryPlan("REC-ME7-BOOT", "BOSCH_ME7", RecoveryTier.BENCH_BOOTPIN_RECOVERY, "Pin 24 recovery", true),
    )

    private val validOriginal = FirmwareArtifact(
        artifactId = "ART-ORIG-001",
        vehicleId = "VEH-AUDI-01",
        ecuFingerprint = sampleProfile.compatibilityFingerprint,
        artifactType = FirmwareArtifactType.ORIGINAL_READBACK,
        format = "RAW_BIN",
        byteLength = 512 * 1024,
        sha256 = "a".repeat(64),
        storageReference = "vault/originals/ART-ORIG-001.bin"
    )

    private val validTarget = FirmwareArtifact(
        artifactId = "ART-MOD-001",
        vehicleId = "VEH-AUDI-01",
        ecuFingerprint = sampleProfile.compatibilityFingerprint,
        artifactType = FirmwareArtifactType.DERIVED_CALIBRATION,
        format = "RAW_BIN",
        byteLength = 512 * 1024,
        sha256 = "b".repeat(64),
        parentArtifactId = validOriginal.artifactId,
        baselineHash = validOriginal.sha256,
        storageReference = "vault/derived/ART-MOD-001.bin"
    )

    // ── SECTION 145: WRONG ARTIFACT REJECTION ────────────────────────────────

    @Test
    fun `target artifact for wrong ECU family or software is strictly rejected by preflight`() {
        val wrongEcuArtifact = validTarget.copy(
            ecuFingerprint = "SIEMENS_MS43:123456:987654:CAL_MS43",
        )

        val env = PreflightEnvironmentContext(
            observedVoltage = 13.6,
            isExternalStabilizerConnected = true,
            roundTripLatencyMs = 25,
            frameErrorRate = 0.0,
            operatorConfirmed = true,
            authenticatedPrincipalId = "tech-senior-007",
        )

        val report = ProgrammingPreflightEngine.evaluate(sampleProfile, samplePack, wrongEcuArtifact, validOriginal, env)
        assertFalse("Preflight must fail on wrong artifact", report.passed)
        assertTrue(report.blockingReasons.any { it.contains("PREFLIGHT_ARTIFACT_INTEGRITY") })
    }

    @Test
    fun `changeset bound to different baseline hash cannot be applied`() {
        val changeSet = CalibrationChangeSet(
            changeSetId = "CS-001",
            baselineArtifactHash = "c".repeat(64), // Differs from validOriginal ("a".repeat(64))
            ecuApplicability = "BOSCH_ME7",
            changes = listOf(
                CalibrationParameterChange(
                    parameterId = "KFMIRL",
                    parameterName = "Engine Load Target",
                    units = "%",
                    originalEngineeringValue = 100.0,
                    proposedEngineeringValue = 120.0,
                    allowedMin = 50.0,
                    allowedMax = 150.0,
                    reason = "Optimized throttle response",
                )
            ),
            author = "Senior Tuner",
            reason = "Stage 0.5 test",
            riskClassification = CalibrationChangeRisk.STANDARD_SERVICE,
        )

        assertFalse("Changeset must not apply to mismatched baseline", changeSet.isApplicableTo(validOriginal))
    }

    @Test
    fun `missing original immutable backup blocks preflight`() {
        val env = PreflightEnvironmentContext(
            observedVoltage = 13.6,
            isExternalStabilizerConnected = true,
            roundTripLatencyMs = 25,
            frameErrorRate = 0.0,
            operatorConfirmed = true,
            authenticatedPrincipalId = "tech-senior-007",
        )

        val report = ProgrammingPreflightEngine.evaluate(sampleProfile, samplePack, validTarget, null, env)
        assertFalse("Preflight must fail without original backup", report.passed)
        assertTrue(report.blockingReasons.any { it.contains("Immutable original readback backup is missing") })
    }

    @Test
    fun `unstable or unknown battery voltage blocks preflight`() {
        val lowVoltageEnv = PreflightEnvironmentContext(
            observedVoltage = 11.8, // Below 12.5V
            isExternalStabilizerConnected = true,
            roundTripLatencyMs = 25,
            frameErrorRate = 0.0,
            operatorConfirmed = true,
            authenticatedPrincipalId = "tech-senior-007",
        )
        val lowVoltReport = ProgrammingPreflightEngine.evaluate(sampleProfile, samplePack, validTarget, validOriginal, lowVoltageEnv)
        assertFalse("Low voltage must fail preflight", lowVoltReport.passed)

        val unknownVoltageEnv = lowVoltageEnv.copy(observedVoltage = null)
        val unknownVoltReport = ProgrammingPreflightEngine.evaluate(sampleProfile, samplePack, validTarget, validOriginal, unknownVoltageEnv)
        assertFalse("Unknown voltage must fail preflight (UNKNOWN = BLOCK)", unknownVoltReport.passed)
    }

    // ── REGULATORY COMPLIANCE POLICIES ───────────────────────────────────────

    @Test
    fun `emissions defeat device is strictly blocked on public highway`() {
        val result = EcuOperationCompliancePolicy.evaluate(
            category = EcuOperationCategory.EMISSION_DEFEAT_DEVICE,
            vehicleUse = VehicleUsageContext.PUBLIC_HIGHWAY,
        )
        assertEquals(ComplianceDecisionState.BLOCKED, result.state)
        assertTrue(result.reason.contains("illegal under emission standards"))
    }

    @Test
    fun `odometer and VIN manipulation is unconditionally blocked`() {
        val odoResult = EcuOperationCompliancePolicy.evaluate(
            category = EcuOperationCategory.ODOMETER_VIN_MANIPULATION,
            vehicleUse = VehicleUsageContext.LABORATORY_BENCH,
        )
        assertEquals(ComplianceDecisionState.BLOCKED, odoResult.state)

        val safetyResult = EcuOperationCompliancePolicy.evaluate(
            category = EcuOperationCategory.SAFETY_SYSTEM_DISABLING,
            vehicleUse = VehicleUsageContext.PUBLIC_HIGHWAY,
        )
        assertEquals(ComplianceDecisionState.BLOCKED, safetyResult.state)
    }

    @Test
    fun `legitimate OEM factory flash is allowed`() {
        val oemResult = EcuOperationCompliancePolicy.evaluate(
            category = EcuOperationCategory.OEM_FACTORY_FLASH,
            vehicleUse = VehicleUsageContext.PUBLIC_HIGHWAY,
        )
        assertEquals(ComplianceDecisionState.ALLOWED, oemResult.state)
    }
}
