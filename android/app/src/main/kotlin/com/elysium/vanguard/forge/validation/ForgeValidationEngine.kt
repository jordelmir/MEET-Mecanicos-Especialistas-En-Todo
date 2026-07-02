package com.elysium.vanguard.forge.validation

import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.ForgeVehicle
import com.elysium.vanguard.forge.domain.ForgeValidationError
import com.elysium.vanguard.forge.domain.ForgeValidationIssue
import com.elysium.vanguard.forge.domain.ForgeValidationResult
import com.elysium.vanguard.forge.domain.ForgeValidationWarning
import com.elysium.vanguard.forge.domain.ForgeManual
import com.elysium.vanguard.forge.domain.SafetyClassification
import com.elysium.vanguard.forge.domain.SimulationProfile

/**
 * ForgeValidationEngine V1.
 *
 * Validación declarativa de artefactos. No ejecuta física.
 *
 * Reglas duras:
 * - DIMENSION_MISSING si una primitiva obligatoria no tiene dimensiones.
 * - MATERIAL_MISSING si la pieza es estructural/crítica sin material.
 * - MANUAL_MISSING si pieza safety-critical sin repair/replacement.
 * - DAMAGE_UNRESOLVED si publicación con daño.
 * - SAFETY_CLASSIFICATION_MISSING si no se clasificó.
 */
class ForgeValidationEngine {

    fun validatePart(part: ForgePart): ForgeValidationResult {
        val issues = mutableListOf<ForgeValidationIssue>()

        // Dimensiones: al menos una dimensión básica debe estar presente.
        val dims = part.dimensions
        val hasAnyDimension = dims.lengthMm != null || dims.widthMm != null ||
                dims.heightMm != null || dims.diameterMm != null ||
                dims.outerDiameterMm != null || dims.thicknessMm != null
        if (!hasAnyDimension) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.DIMENSION_MISSING,
                warning = null,
                message = "Part ${part.artifact.id} has no dimensions defined"
            )
        }

        // inner vs outer diameter.
        if (dims.innerDiameterMm != null && dims.outerDiameterMm != null &&
            dims.innerDiameterMm >= dims.outerDiameterMm) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.DIMENSION_INVALID,
                warning = null,
                message = "innerDiameterMm (${dims.innerDiameterMm}) must be < outerDiameterMm (${dims.outerDiameterMm})"
            )
        }

        // Material.
        if (part.materialId.isNullOrBlank()) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.MATERIAL_MISSING,
                warning = null,
                message = "Part ${part.artifact.id} has no material assigned"
            )
        }

        // Proceso de fabricación.
        if (part.manufacturingProcessIds.isEmpty()) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.PROCESS_MISSING,
                warning = null,
                message = "Part ${part.artifact.id} has no manufacturing process assigned"
            )
        }

        // Safety classification.
        if (part.artifact.safetyClassification == SafetyClassification.EDUCATIONAL &&
            part.connectionPorts.any { it.portType == com.elysium.vanguard.forge.domain.ConnectionPortType.BOLT_HOLE }
        ) {
            // Pieza con puertos bolt que no se ha clasificado como safety-critical: warning.
            issues += ForgeValidationIssue(
                code = null,
                warning = ForgeValidationWarning.REQUIRES_PROFESSIONAL_VALIDATION,
                message = "Part ${part.artifact.id} has bolt holes but is classified as EDUCATIONAL — verify intended use"
            )
        }

        // Manuales para safety-critical.
        if (part.artifact.safetyClassification.isSafetyCritical &&
            part.repairProcedures.isEmpty() &&
            part.replacementProcedures.isEmpty()
        ) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.MANUAL_MISSING,
                warning = null,
                message = "Safety-critical part ${part.artifact.id} missing repair/replacement procedures"
            )
        }

        // Puertos de conexión.
        if (part.connectionPorts.isEmpty() &&
            part.artifact.safetyClassification.isSafetyCritical
        ) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.CONNECTION_PORT_MISSING,
                warning = null,
                message = "Safety-critical part ${part.artifact.id} has no connection ports defined"
            )
        }

        // Features no soportados.
        for (feature in part.featureTree) {
            if (!feature.type.supportedV1) {
                issues += ForgeValidationIssue(
                    code = ForgeValidationError.UNSUPPORTED_FEATURE,
                    warning = null,
                    message = "Feature '${feature.id}' of type ${feature.type} is not supported in V1 compiler",
                    relatedFeatureId = feature.id
                )
            }
        }

        return ForgeValidationResult.from(issues)
    }

    fun validateAssembly(assembly: ForgeAssembly, partsById: Map<String, ForgePart> = emptyMap()): ForgeValidationResult {
        val issues = mutableListOf<ForgeValidationIssue>()

        if (assembly.instances.isEmpty()) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.DIMENSION_MISSING,
                warning = null,
                message = "Assembly has no instances"
            )
        }

        // Instancias sin parte resuelta.
        for (instance in assembly.instances) {
            if (partsById.isNotEmpty() && !partsById.containsKey(instance.partId)) {
                issues += ForgeValidationIssue(
                    code = null,
                    warning = ForgeValidationWarning.GENERIC_DATA,
                    message = "Assembly references unknown part ${instance.partId}",
                    relatedInstanceId = instance.id
                )
            }
        }

        // Daño no resuelto.
        for (instance in assembly.instances) {
            if (instance.damageState.severity >= com.elysium.vanguard.forge.domain.DamageSeverity.HIGH) {
                issues += ForgeValidationIssue(
                    code = ForgeValidationError.DAMAGE_UNRESOLVED,
                    warning = null,
                    message = "Instance ${instance.id} has unresolved damage severity ${instance.damageState.severity}",
                    relatedInstanceId = instance.id
                )
            }
        }

        // Piezas safety-critical sin manual.
        for (instance in assembly.instances) {
            val part = partsById[instance.partId]
            if (part != null && part.artifact.safetyClassification.isSafetyCritical &&
                part.repairProcedures.isEmpty() && part.replacementProcedures.isEmpty()
            ) {
                issues += ForgeValidationIssue(
                    code = ForgeValidationError.MANUAL_MISSING,
                    warning = null,
                    message = "Instance ${instance.id} (${part.artifact.id}) is safety-critical without manual",
                    relatedInstanceId = instance.id
                )
            }
        }

        // Complejidad alta.
        if (assembly.instances.size > 100) {
            issues += ForgeValidationIssue(
                code = null,
                warning = ForgeValidationWarning.HIGH_COMPLEXITY_ARTIFACT,
                message = "Assembly has ${assembly.instances.size} instances — performance may degrade"
            )
        }

        return ForgeValidationResult.from(issues)
    }

    fun validateVehicle(vehicle: ForgeVehicle, partsById: Map<String, ForgePart> = emptyMap()): ForgeValidationResult {
        val issues = mutableListOf<ForgeValidationIssue>()

        // Powertrain.
        if (vehicle.powertrain == null) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.DIMENSION_MISSING,
                warning = null,
                message = "Vehicle ${vehicle.artifact.id} has no powertrain defined"
            )
        }

        // Safety classification obligatoria.
        if (vehicle.artifact.safetyClassification == SafetyClassification.EDUCATIONAL &&
            vehicle.systems.any { it.systemType == com.elysium.vanguard.forge.domain.VehicleSystemType.BRAKES ||
                it.systemType == com.elysium.vanguard.forge.domain.VehicleSystemType.STEERING }
        ) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.SAFETY_CLASSIFICATION_MISSING,
                warning = null,
                message = "Vehicle ${vehicle.artifact.id} has brake/steering systems — re-evaluate SafetyClassification"
            )
        }

        // Damage unresolved.
        if (vehicle.simulationScenarios.isEmpty()) {
            issues += ForgeValidationIssue(
                code = null,
                warning = ForgeValidationWarning.GENERIC_DATA,
                message = "Vehicle has no simulation scenarios defined"
            )
        }

        return ForgeValidationResult.from(issues)
    }

    fun validateSimulationScenario(
        assembly: ForgeAssembly,
        profile: SimulationProfile?
    ): ForgeValidationResult {
        val issues = mutableListOf<ForgeValidationIssue>()
        if (profile == null) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.DIMENSION_MISSING,
                warning = null,
                message = "Simulation scenario has no SimulationProfile"
            )
        } else {
            if (profile.fixedStepSec <= 0.0 || profile.fixedStepSec > 1.0) {
                issues += ForgeValidationIssue(
                    code = ForgeValidationError.PHYSICS_UNSTABLE,
                    warning = null,
                    message = "SimulationProfile.fixedStepSec ${profile.fixedStepSec} is out of safe range"
                )
            }
            if (assembly.instances.size > 50 && profile.enableCollisions) {
                issues += ForgeValidationIssue(
                    code = null,
                    warning = ForgeValidationWarning.PERFORMANCE_RISK,
                    message = "Collision detection on >50 instances may cause performance issues"
                )
            }
        }
        return ForgeValidationResult.from(issues)
    }

    fun validateManual(manual: ForgeManual): ForgeValidationResult {
        val issues = mutableListOf<ForgeValidationIssue>()
        if (manual.safetyWarnings.isEmpty()) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.SAFETY_CLASSIFICATION_MISSING,
                warning = null,
                message = "Manual ${manual.id} has no safety warnings — REQUIRED."
            )
        }
        if (manual.steps.isEmpty()) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.DIMENSION_MISSING,
                warning = null,
                message = "Manual ${manual.id} has no steps"
            )
        }
        return ForgeValidationResult.from(issues)
    }

    fun validateForPublishing(artifact: com.elysium.vanguard.forge.domain.ForgeArtifact): ForgeValidationResult {
        val issues = mutableListOf<ForgeValidationIssue>()
        // OEM data not licensed — siempre para V1.
        issues += ForgeValidationIssue(
            code = ForgeValidationError.OEM_DATA_NOT_LICENSED,
            warning = null,
            message = "V1 no incluye datos OEM licenciados — solo contenido educativo genérico."
        )
        // Safety classification required.
        if (artifact.safetyClassification == SafetyClassification.EDUCATIONAL) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.SAFETY_CLASSIFICATION_MISSING,
                warning = null,
                message = "Artifact ${artifact.id} classified as EDUCATIONAL — confirmar antes de publicar."
            )
        }
        return ForgeValidationResult.from(issues)
    }
}