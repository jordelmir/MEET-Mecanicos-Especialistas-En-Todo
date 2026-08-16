package com.elysium369.meet.core.knowledge.graph

import java.util.Locale

enum class KnowledgeConstraintState {
    CONFIRMED, PROBABLE, CONDITIONAL, GENERIC, UNKNOWN, NOT_APPLICABLE, CONFLICTED,
}

data class KnowledgeApplicabilityContext(
    val vehicle: ActiveVehicleIdentity?,
    val findingNamespace: String? = null,
    val findingRawIdentity: String? = null,
    val findingFailureType: Int? = null,
    val ecuName: String? = null,
    val ecuAddress: String? = null,
    val hardwareNumber: String? = null,
    val softwareNumber: String? = null,
    val calibrationId: String? = null,
    val powertrainFacts: Map<String, String> = emptyMap(),
    val verifiedEvidence: List<VehicleEvidence> = emptyList(),
)

data class KnowledgeConstraintDecision(
    val state: KnowledgeConstraintState,
    val matchedConstraints: List<String>,
    val missingConstraints: List<String>,
    val conflictingConstraints: List<String>,
    val reason: String,
) {
    @Deprecated("Use KnowledgeUsePolicy.authorize with an explicit purpose")
    val diagnosticUseAllowed: Boolean get() = false
}

/**
 * Evaluates machine-readable graph constraints without inferring absent vehicle facts.
 * Supported syntax is `key=value`, `key!=value` and `year=min..max`. Unknown keys fail closed.
 */
object KnowledgeApplicabilityEngine {
    fun evaluate(
        constraints: List<String>,
        context: KnowledgeApplicabilityContext?,
    ): KnowledgeConstraintDecision {
        val normalized = constraints.map(String::trim).filter(String::isNotBlank).distinct()
        if (normalized.isEmpty()) {
            return KnowledgeConstraintDecision(
                KnowledgeConstraintState.GENERIC, emptyList(), emptyList(), emptyList(),
                "La arista no declara restricciones vehiculares; uso genérico sujeto a verificación.",
            )
        }
        val activeContext = context
        val activeVehicle = activeContext?.vehicle
        if (activeContext == null || activeVehicle == null || activeVehicle.educationalMode) {
            return KnowledgeConstraintDecision(
                KnowledgeConstraintState.UNKNOWN, emptyList(), normalized, emptyList(),
                "No existe identidad vehicular activa para resolver las restricciones.",
            )
        }
        val matched = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        normalized.forEach { constraint ->
            val operator = if ("!=" in constraint) "!=" else "="
            val pieces = constraint.split(operator, limit = 2)
            if (pieces.size != 2) {
                missing += constraint
                return@forEach
            }
            val key = pieces[0].trim().lowercase(Locale.ROOT)
            val expected = pieces[1].trim()
            val actual = actualValue(key, activeContext)
            if (actual == null) {
                missing += constraint
            } else if (matches(key, actual, expected) == (operator == "=")) {
                matched += constraint
            } else {
                conflicts += constraint
            }
        }
        val state = when {
            conflicts.isNotEmpty() && matched.isNotEmpty() -> KnowledgeConstraintState.CONFLICTED
            conflicts.isNotEmpty() -> KnowledgeConstraintState.NOT_APPLICABLE
            missing.isNotEmpty() -> KnowledgeConstraintState.CONDITIONAL
            else -> KnowledgeConstraintState.CONFIRMED
        }
        return KnowledgeConstraintDecision(
            state, matched.sorted(), missing.sorted(), conflicts.sorted(),
            when (state) {
                KnowledgeConstraintState.CONFIRMED -> "Todas las restricciones declaradas coinciden con evidencia vehicular activa."
                KnowledgeConstraintState.CONDITIONAL -> "Falta evidencia para cerrar una o más restricciones."
                KnowledgeConstraintState.NOT_APPLICABLE -> "La identidad activa contradice las restricciones declaradas."
                KnowledgeConstraintState.CONFLICTED -> "La evidencia coincide con unas restricciones y contradice otras."
                KnowledgeConstraintState.UNKNOWN -> "Aplicabilidad desconocida."
                KnowledgeConstraintState.PROBABLE -> "Aplicabilidad genérica; requiere confirmación física."
                KnowledgeConstraintState.GENERIC -> "Conocimiento genérico; no constituye autoridad específica del vehículo."
            },
        )
    }

    private fun actualValue(key: String, context: KnowledgeApplicabilityContext): String? = when (key) {
        "profile", "profileid" -> context.vehicle?.selectedProfileId
        "make", "brand", "marca" -> context.vehicle?.make
        "model", "modelo" -> context.vehicle?.model
        "year", "año" -> context.vehicle?.year?.toString()
        "engine", "motor" -> context.vehicle?.engine
        "enginecode", "engine_code" -> context.vehicle?.engineCode
        "transmission", "transmision", "transmisión" -> context.vehicle?.transmission
        "market", "mercado" -> context.vehicle?.market
        "ecu", "ecuname" -> context.ecuName
        "ecuaddress", "ecu_address" -> context.ecuAddress
        "hardware", "hardwarenumber" -> context.hardwareNumber
        "software", "softwarenumber" -> context.softwareNumber
        "calibration", "calibrationid" -> context.calibrationId
        "namespace", "finding.namespace" -> context.findingNamespace
        "rawdtcidentity", "finding.rawidentity" -> context.findingRawIdentity
        "failuretype", "finding.failuretype" -> context.findingFailureType?.toString()
        else -> when {
            key.startsWith("powertrain.") -> context.powertrainFacts[key.removePrefix("powertrain.")]
            key.startsWith("evidence.") -> context.verifiedEvidence
                .firstOrNull { evidence ->
                    evidence.status == VehicleEvidenceStatus.VERIFIED &&
                        evidence.requirementKey.equals(key.removePrefix("evidence."), ignoreCase = true)
                }?.let { it.value ?: it.assertion.name }
            else -> null
        }
    }?.trim()?.takeIf(String::isNotBlank)

    private fun matches(key: String, actual: String, expected: String): Boolean {
        if (key in setOf("year", "año") && ".." in expected) {
            val range = expected.split("..", limit = 2).mapNotNull { it.trim().toIntOrNull() }
            val actualYear = actual.toIntOrNull() ?: return false
            return range.size == 2 && actualYear in range[0]..range[1]
        }
        val alternatives = expected.split('|').map(String::trim).filter(String::isNotBlank)
        return alternatives.any { it.equals(actual, ignoreCase = true) }
    }
}
