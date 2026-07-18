package com.elysium369.meet.automotive.parts

import kotlinx.serialization.Serializable

@Serializable
enum class AutomotiveSystem {
    SUSPENSION,
    STEERING,
    BRAKES,
    ENGINE_MECHANICAL,
    ENGINE_ELECTRICAL,
    FUEL,
    IGNITION,
    COOLING,
    TRANSMISSION,
    EXHAUST_EMISSIONS,
    ELECTRICAL_POWER,
    SENSORS,
    HVAC,
    BODY,
    INTERIOR,
    SAFETY_SRS,
    TIRES_WHEELS,
    FLUIDS,
    HYBRID_EV
}

@Serializable
enum class SafetyLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

@Serializable
data class PartAlias(
    val term: String,
    val locale: String,
    val canonicalId: String
)

@Serializable
data class PartToolRequirement(
    val name: String,
    val specification: String = "",
    val isMandatory: Boolean = true
)

@Serializable
data class PartTorqueSpec(
    val description: String,
    val torqueNm: Double,
    val torqueFtLbs: Double = 0.0,
    val angleDegrees: Int? = null
)

@Serializable
data class PartFitment(
    val make: String,
    val model: String,
    val yearStart: Int,
    val yearEnd: Int,
    val engine: String = "",
    val transmission: String = ""
)

@Serializable
data class PartDiagnosticRule(
    val symptom: String,
    val testDescription: String,
    val expectedOutcome: String,
    val actionIfFailed: String
)

@Serializable
data class PartProcedure(
    val id: String,
    val title: String,
    val difficulty: String,
    val estimatedTimeMinutes: Int,
    val requiresLift: Boolean,
    val requiresAlignment: Boolean,
    val beforeStart: List<String>,
    val steps: List<String>,
    val torqueSpecs: List<PartTorqueSpec>,
    val commonMistakes: List<String>,
    val postRepairValidation: List<String>,
    val whenToStopWarning: String,
    val customerExplanation: String
)

@Serializable
data class AutomotivePart(
    val id: String,
    val canonicalNameEs: String,
    val canonicalNameEn: String,
    val system: AutomotiveSystem,
    val subsystem: String,
    val aliases: List<String>,
    val description: String,
    val symptoms: List<String>,
    val relatedDtcs: List<String>,
    val requiredTools: List<String>,
    val safetyLevel: SafetyLevel,
    val procedures: List<PartProcedure>,
    val diagnosticRules: List<PartDiagnosticRule> = emptyList(),
    val notes: List<String> = emptyList()
)
