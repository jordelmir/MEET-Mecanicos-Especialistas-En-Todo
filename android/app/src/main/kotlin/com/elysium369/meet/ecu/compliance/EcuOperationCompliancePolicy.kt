package com.elysium369.meet.ecu.compliance

enum class EcuOperationCategory {
    OEM_FACTORY_FLASH,
    EMISSION_SERVICE_REPAIR,
    BENCH_CALIBRATION_RESEARCH,
    EMISSION_DEFEAT_DEVICE,
    ODOMETER_VIN_MANIPULATION,
    SAFETY_SYSTEM_DISABLING,
    IMMOBILIZER_THEFT_BYPASS,
}

enum class VehicleUsageContext {
    PUBLIC_HIGHWAY,
    SANCTIONED_MOTORSPORT_OFFROAD,
    LABORATORY_BENCH,
}

enum class ComplianceDecisionState {
    ALLOWED,
    ALLOWED_WITH_REQUIREMENTS,
    REQUIRES_PROFESSIONAL_REVIEW,
    BLOCKED,
}

data class ComplianceEvaluationResult(
    val state: ComplianceDecisionState,
    val reason: String,
    val legalPrecedentRef: String? = null,
)

object EcuOperationCompliancePolicy {

    fun evaluate(
        category: EcuOperationCategory,
        vehicleUse: VehicleUsageContext,
        jurisdiction: String = "CR", // Default Costa Rica
    ): ComplianceEvaluationResult {
        // 1. Criminal Tampering / Safety Disabling is universally BLOCKED across all contexts
        if (category == EcuOperationCategory.ODOMETER_VIN_MANIPULATION) {
            return ComplianceEvaluationResult(
                state = ComplianceDecisionState.BLOCKED,
                reason = "Falsification of VIN or odometer is strictly prohibited under criminal and consumer protection statutes.",
                legalPrecedentRef = "Costa Rica Código Penal Art. 242 / Ley 9078"
            )
        }

        if (category == EcuOperationCategory.SAFETY_SYSTEM_DISABLING) {
            return ComplianceEvaluationResult(
                state = ComplianceDecisionState.BLOCKED,
                reason = "Disabling active safety systems (Airbags, ABS, ESC, Emergency Braking) is prohibited.",
                legalPrecedentRef = "Costa Rica Ley de Tránsito por Vías Públicas Terrestres y Seguridad Vial No. 9078"
            )
        }

        if (category == EcuOperationCategory.IMMOBILIZER_THEFT_BYPASS) {
            return ComplianceEvaluationResult(
                state = ComplianceDecisionState.BLOCKED,
                reason = "Generic immobilizer defeat or anti-theft bypass without certified locksmith identity is prohibited.",
                legalPrecedentRef = "Anti-Theft Security Compliance Standard"
            )
        }

        // 2. Emissions Defeat Controls
        if (category == EcuOperationCategory.EMISSION_DEFEAT_DEVICE) {
            return if (vehicleUse == VehicleUsageContext.PUBLIC_HIGHWAY) {
                ComplianceEvaluationResult(
                    state = ComplianceDecisionState.BLOCKED,
                    reason = "Emissions control deletion (DPF, EGR, SCR, AdBlue, Catalyst OFF) on public highway vehicles is illegal under emission standards.",
                    legalPrecedentRef = "Costa Rica Decreto Ejecutivo 37243-MOPT-MINAE / Dekra RTV standards"
                )
            } else {
                ComplianceEvaluationResult(
                    state = ComplianceDecisionState.REQUIRES_PROFESSIONAL_REVIEW,
                    reason = "Off-highway / sanctioned competition calibration requires closed-circuit verification and ownership affidavit.",
                    legalPrecedentRef = "Motorsport Exemption Protocol"
                )
            }
        }

        // 3. Legitimate Service & Diagnostics
        return when (category) {
            EcuOperationCategory.OEM_FACTORY_FLASH -> ComplianceEvaluationResult(
                state = ComplianceDecisionState.ALLOWED,
                reason = "Legitimate OEM factory update or calibration restore is fully permitted."
            )
            EcuOperationCategory.EMISSION_SERVICE_REPAIR -> ComplianceEvaluationResult(
                state = ComplianceDecisionState.ALLOWED,
                reason = "Service procedures to restore and validate emissions equipment are approved."
            )
            EcuOperationCategory.BENCH_CALIBRATION_RESEARCH -> if (vehicleUse == VehicleUsageContext.LABORATORY_BENCH) {
                ComplianceEvaluationResult(
                    state = ComplianceDecisionState.ALLOWED_WITH_REQUIREMENTS,
                    reason = "Bench research on isolated hardware permitted under laboratory safety protocol."
                )
            } else {
                ComplianceEvaluationResult(
                    state = ComplianceDecisionState.REQUIRES_PROFESSIONAL_REVIEW,
                    reason = "Calibration modifications require dyno and emission test verification."
                )
            }
            else -> ComplianceEvaluationResult(
                state = ComplianceDecisionState.BLOCKED,
                reason = "Unclassified ECU operation rejected fail-closed."
            )
        }
    }
}
