package com.elysium.vanguard.forge.diagnostics

import com.elysium.vanguard.forge.domain.DamageSeverity
import com.elysium.vanguard.forge.domain.DamageType
import com.elysium.vanguard.forge.domain.DiagnosticReport
import com.elysium.vanguard.forge.domain.DiagnosticSignal
import com.elysium.vanguard.forge.domain.DiagnosticSignalType
import com.elysium.vanguard.forge.domain.EngineRuntimeSnapshot
import com.elysium.vanguard.forge.domain.FailureMode
import com.elysium.vanguard.forge.domain.FailureModeMatch
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.PowertrainDefinition
import com.elysium.vanguard.forge.domain.RecommendedAction
import com.elysium.vanguard.forge.domain.RepairRecommendation
import com.elysium.vanguard.forge.domain.SafetyClassification
import com.elysium.vanguard.forge.domain.VehicleSystemType
import java.util.UUID

/**
 * ForgeDiagnosticEngine V1.
 *
 * Mapea DamageState + señales observadas a un DiagnosticReport.
 * Reglas:
 * - Nunca afirma certeza absoluta (siempre "probable").
 * - Si hay pocos datos, marca INSUFFICIENT_DATA.
 * - Piezas safety-critical requieren validación profesional.
 */
class ForgeDiagnosticEngine {

    fun diagnoseAssembly(
        assembly: ForgeAssembly,
        partsById: Map<String, ForgePart>
    ): DiagnosticReport {
        val damaged = assembly.instances.filter { it.damageState.severity >= DamageSeverity.MEDIUM }
        if (damaged.isEmpty()) {
            return emptyReport()
        }
        val target = damaged.maxBy { it.damageState.severity }
        val part = partsById[target.partId]
        val failureMode = part?.let { findFailureModeForInstance(target, it) }
        return buildReport(target, part, failureMode, assembly, null)
    }

    fun diagnoseEngine(
        powertrain: PowertrainDefinition,
        snapshot: EngineRuntimeSnapshot,
        assembly: ForgeAssembly,
        partsById: Map<String, ForgePart>
    ): DiagnosticReport {
        val detectedFailures = snapshot.detectedFailures
        if (detectedFailures.isEmpty() && snapshot.state.warnings.isEmpty()) {
            return emptyReport()
        }
        // Buscar la pieza más dañada relacionada.
        val candidates = assembly.instances.filter { inst ->
            inst.damageState.severity >= DamageSeverity.MEDIUM &&
            (
                inst.id in powertrain.ignitionComponentIds ||
                inst.id in powertrain.fuelComponentIds ||
                inst.id in powertrain.coolingComponentIds ||
                inst.id in listOfNotNull(powertrain.crankshaftInstanceId) ||
                inst.id in powertrain.pistonInstanceIds
            )
        }
        val target = candidates.maxByOrNull { it.damageState.severity }
            ?: assembly.instances.maxByOrNull { it.damageState.severity }
            ?: return emptyReport()
        val part = partsById[target.partId]
        val failureMode = part?.let { findFailureModeForInstance(target, it) }
        return buildReport(
            instance = target,
            part = part,
            failureMode = failureMode,
            assembly = assembly,
            engineSnapshot = snapshot
        )
    }

    fun mapSymptomsToFailureModes(symptoms: List<DiagnosticSignal>): List<FailureModeMatch> {
        // V1: matching heurístico por signalType + observedValue vs expectedRange.
        val matches = mutableListOf<FailureModeMatch>()
        for (signal in symptoms) {
            val score = when (signal.signalType) {
                DiagnosticSignalType.TEMPERATURE_HIGH -> 0.85
                DiagnosticSignalType.PRESSURE_LOW -> 0.75
                DiagnosticSignalType.RPM_VARIATION -> 0.65
                DiagnosticSignalType.VIBRATION_HIGH -> 0.7
                DiagnosticSignalType.TORQUE_DROP -> 0.8
                DiagnosticSignalType.ELECTRICAL_FAULT -> 0.6
                DiagnosticSignalType.VISUAL_DAMAGE -> 0.95
                DiagnosticSignalType.LEAK_DETECTED -> 0.9
                DiagnosticSignalType.NOISE -> 0.5
                DiagnosticSignalType.DTC_CODE -> 0.7
                DiagnosticSignalType.USER_SYMPTOM -> 0.4
            }
            matches += FailureModeMatch(
                failureMode = FailureMode(
                    id = "fm_${signal.id}",
                    partId = signal.id,
                    title = signal.name,
                    damageType = DamageType.WEAR,
                    symptoms = listOf(signal.name),
                    causes = listOf("Análisis basado en señal ${signal.signalType}"),
                    consequences = listOf("Revisar manualmente"),
                    severity = DamageSeverity.MEDIUM
                ),
                confidence = score.coerceAtMost(1.0) * signal.confidence,
                instanceId = signal.id
            )
        }
        return matches
    }

    fun mapDtcToForgeParts(dtcCode: String): List<String> {
        // Mapping genérico inicial. No inventar compatibilidad OEM.
        return when (dtcCode.uppercase()) {
            "P0300", "P0301", "P0302", "P0303", "P0304" -> listOf("spark_plug", "injector", "ignition_coil", "piston", "wiring")
            "P0128" -> listOf("thermostat", "coolant_temp_sensor", "water_pump", "radiator")
            "P0171", "P0174" -> listOf("vacuum_leak", "maf_sensor", "fuel_pressure")
            "P0420", "P0430" -> listOf("catalytic_converter", "oxygen_sensor", "exhaust_leak")
            "P0500" -> listOf("wheel_speed_sensor", "vss")
            "P0700" -> listOf("transmission_control", "valve_body")
            else -> emptyList()
        }
    }

    fun generateRepairRecommendation(report: DiagnosticReport): RepairRecommendation {
        val requiresProfessional = report.severity >= DamageSeverity.HIGH ||
            report.relatedDtcCodes.isNotEmpty()
        val mustReplace = report.severity == DamageSeverity.CRITICAL
        val canRepair = !mustReplace && report.repairProcedure != null
        return when {
            mustReplace -> RepairRecommendation(
                action = RecommendedAction.REPLACE,
                replacement = report.replacementProcedure,
                rationale = "Severidad crítica — pieza no reparable, reemplazar.",
                requiresProfessional = true
            )
            canRepair -> RepairRecommendation(
                action = RecommendedAction.REPAIR,
                procedure = report.repairProcedure,
                rationale = "Severidad permite reparación.",
                requiresProfessional = requiresProfessional
            )
            report.observedSymptoms.isEmpty() -> RepairRecommendation(
                action = RecommendedAction.INSUFFICIENT_DATA,
                rationale = "Datos insuficientes. Realizar inspección visual.",
                requiresProfessional = false
            )
            else -> RepairRecommendation(
                action = RecommendedAction.INSPECT_ONLY,
                rationale = "Sin daño confirmado. Inspeccionar en próximo servicio.",
                requiresProfessional = false
            )
        }
    }

    // --- Internos ---

    private fun findFailureModeForInstance(instance: com.elysium.vanguard.forge.domain.PartInstance, part: ForgePart): FailureMode? {
        val ds = instance.damageState
        if (ds.damageTypes.isEmpty()) return null
        val primaryType = ds.damageTypes.first()
        return FailureMode(
            id = "fm_${instance.id}_${primaryType.name}",
            partId = part.artifact.id,
            title = "${part.artifact.name} — ${primaryType.name.lowercase()}",
            damageType = primaryType,
            symptoms = part.damageModel?.notes?.let { listOf(it) } ?: emptyList(),
            causes = listOf("Daño observado: ${primaryType.name}"),
            consequences = listOf("Revisar sistema afectado"),
            severity = ds.severity,
            repairProcedureId = part.repairProcedures.firstOrNull()?.id,
            replacementProcedureId = part.replacementProcedures.firstOrNull()?.id
        )
    }

    private fun buildReport(
        instance: com.elysium.vanguard.forge.domain.PartInstance,
        part: ForgePart?,
        failureMode: FailureMode?,
        assembly: ForgeAssembly,
        engineSnapshot: EngineRuntimeSnapshot?
    ): DiagnosticReport {
        val repair = part?.repairProcedures?.firstOrNull()
        val replacement = part?.replacementProcedures?.firstOrNull()
        val mustReplace = instance.damageState.severity == DamageSeverity.CRITICAL ||
            (instance.damageState.damageTypes.contains(DamageType.BROKEN) && replacement != null)
        val canRepair = !mustReplace && repair != null

        val confidence = when {
            failureMode == null -> 0.3
            instance.damageState.severity == DamageSeverity.CRITICAL -> 0.9
            instance.damageState.severity == DamageSeverity.HIGH -> 0.75
            instance.damageState.severity == DamageSeverity.MEDIUM -> 0.6
            else -> 0.45
        }

        // canRepair / mustReplace se usan internamente en generateRepairRecommendation;
        // se exponen como parte de la explicación educativa.

        val safetyBanner = part?.artifact?.safetyClassification?.isSafetyCritical == true

        return DiagnosticReport(
            id = UUID.randomUUID().toString(),
            affectedSystem = inferSystem(instance),
            affectedPartInstanceId = instance.id,
            affectedPartName = part?.artifact?.name ?: instance.partId,
            probableFailure = failureMode?.title ?: "Daño detectado en ${instance.partId}",
            confidence = confidence,
            severity = instance.damageState.severity,
            observedSymptoms = failureMode?.symptoms ?: instance.damageState.notes?.let { listOf(it) } ?: emptyList(),
            likelyCauses = failureMode?.causes ?: emptyList(),
            consequences = failureMode?.consequences ?: emptyList(),
            repairProcedure = repair,
            replacementProcedure = replacement,
            toolsRequired = repair?.requiredTools ?: replacement?.requiredTools ?: emptyList(),
            safetyWarnings = (repair?.safetyWarnings ?: replacement?.safetyWarnings ?: emptyList()) +
                if (safetyBanner) listOf(
                    "ADVERTENCIA: pieza crítica de seguridad. " +
                    "Simulación educativa aproximada. Requiere validación profesional antes de uso vehicular."
                ) else emptyList(),
            relatedDtcCodes = part?.relatedDtcCodes ?: emptyList(),
            educationalExplanation = buildExplanation(instance, failureMode, engineSnapshot)
        )
    }

    private fun inferSystem(instance: com.elysium.vanguard.forge.domain.PartInstance): VehicleSystemType? {
        val id = instance.id.lowercase()
        val part = instance.partId.lowercase()
        return when {
            "engine" in id || "engine" in part || "piston" in part || "crank" in part -> VehicleSystemType.ENGINE
            "brake" in id || "brake" in part -> VehicleSystemType.BRAKES
            "spring" in part || "susp" in id || "bushing" in part -> VehicleSystemType.SUSPENSION
            "water_pump" in part -> VehicleSystemType.COOLING
            "spark" in part -> VehicleSystemType.ENGINE
            else -> null
        }
    }

    private fun buildExplanation(
        instance: com.elysium.vanguard.forge.domain.PartInstance,
        failureMode: FailureMode?,
        engineSnapshot: EngineRuntimeSnapshot?
    ): String {
        val sb = StringBuilder()
        sb.append("Pieza dañada: ").append(instance.partId).append(". ")
        if (failureMode != null) {
            sb.append("Síntoma: ").append(failureMode.symptoms.joinToString().ifBlank { "Daño visible" }).append(". ")
            sb.append("Causa probable: ").append(failureMode.causes.joinToString().ifBlank { "Inspección manual recomendada" }).append(". ")
        }
        if (engineSnapshot != null) {
            sb.append("Estado del motor: ${engineSnapshot.lifecycle}, RPM=${"%.0f".format(engineSnapshot.state.rpm)}, ")
            sb.append("Temp=${"%.1f".format(engineSnapshot.state.coolantTempC)}°C.")
        }
        return sb.toString()
    }

    private fun emptyReport(): DiagnosticReport {
        return DiagnosticReport(
            id = UUID.randomUUID().toString(),
            affectedSystem = null,
            affectedPartInstanceId = null,
            affectedPartName = "Sin daño detectado",
            probableFailure = "Sin daño significativo detectado.",
            confidence = 0.0,
            severity = DamageSeverity.NONE,
            observedSymptoms = emptyList(),
            likelyCauses = emptyList(),
            consequences = emptyList(),
            educationalExplanation = "Todas las piezas dentro de parámetros normales."
        )
    }
}