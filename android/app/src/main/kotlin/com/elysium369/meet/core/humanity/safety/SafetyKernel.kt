package com.elysium369.meet.core.humanity.safety

import com.elysium369.meet.core.humanity.CapabilityLevel
import com.elysium369.meet.core.humanity.SafetyLevel

data class SafetyClearanceDecision(
    val isAllowed: Boolean,
    val requiresSupervision: Boolean,
    val effectiveSafetyLevel: SafetyLevel,
    val reason: String,
)

object SafetyKernel {

    private val prohibitedKeywords = setOf(
        "airbag", "srs", "pretensioner", "pyrotechnic",
        "high voltage", "traction battery", "inverter", "orange cable", "700v", "400v",
        "fuel tank weld", "acetylene",
    )

    private val supervisedKeywords = setOf(
        "compressor", "refrigerant r134a", "r1234yf", "spring compressor",
        "strut removal", "brake line flare", "flywheel torque",
    )

    fun evaluateActionSafety(
        actionDescription: String,
        nominalSafetyLevel: SafetyLevel,
        userLevel: CapabilityLevel,
    ): SafetyClearanceDecision {
        val lower = actionDescription.lowercase()

        // 1. Prohibited / Strict Exclusion Check
        if (prohibitedKeywords.any { lower.contains(it) }) {
            return SafetyClearanceDecision(
                isAllowed = false,
                requiresSupervision = true,
                effectiveSafetyLevel = SafetyLevel.PROHIBITED_UNSUPERVISED,
                reason = "Peligro Crítico: La acción involucra sistemas pirotécnicos o alto voltaje que requieren certificación profesional y supervisión presencial estricta.",
            )
        }

        // 2. High Risk / Supervised Check
        if (supervisedKeywords.any { lower.contains(it) } || nominalSafetyLevel == SafetyLevel.SUPERVISED_REQUIRED) {
            val hasHighCompetency = userLevel.levelIndex >= CapabilityLevel.L6_INDEPENDENT.levelIndex
            return SafetyClearanceDecision(
                isAllowed = hasHighCompetency,
                requiresSupervision = !hasHighCompetency,
                effectiveSafetyLevel = SafetyLevel.SUPERVISED_REQUIRED,
                reason = if (hasHighCompetency) {
                    "Operación de riesgo moderado autorizada bajo nivel autónomo L6+."
                } else {
                    "Esta operación requiere supervisión técnica presencial o certificación de nivel L6+."
                },
            )
        }

        // 3. Low Risk Practice / Simulation Safe
        return SafetyClearanceDecision(
            isAllowed = true,
            requiresSupervision = false,
            effectiveSafetyLevel = nominalSafetyLevel,
            reason = "Operación evaluada como segura para práctica formativa con precauciones estándar de taller.",
        )
    }
}
