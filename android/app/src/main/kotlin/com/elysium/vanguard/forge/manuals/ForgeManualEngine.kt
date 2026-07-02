package com.elysium.vanguard.forge.manuals

import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgeManual
import com.elysium.vanguard.forge.domain.ForgeManualType
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.FailureMode
import com.elysium.vanguard.forge.domain.ProcedureDifficulty
import com.elysium.vanguard.forge.domain.ProcedureStep
import com.elysium.vanguard.forge.domain.SafetyClassification
import com.elysium.vanguard.forge.domain.ToolRequirement
import com.elysium.vanguard.forge.domain.TorqueSpec
import java.util.UUID

/**
 * ForgeManualEngine V1.
 *
 * Genera manuales genéricos (no OEM) para piezas, ensamblajes y modos de falla.
 *
 * Regla crítica de seguridad: todo manual incluye siempre al menos un safetyWarning,
 * incluso si la pieza es EDUCATIONAL.
 */
class ForgeManualEngine {

    fun createManualFromPart(part: ForgePart): ForgeManual {
        val safetyWarnings = mutableListOf(
            "ADVERTENCIA GENERAL: simulación educativa. No sustituye manuales OEM ni validación profesional.",
            "Use siempre EPP adecuado (guantes, gafas) al manipular piezas mecánicas."
        )
        if (part.artifact.safetyClassification.isSafetyCritical) {
            safetyWarnings += "PIEZA CRÍTICA DE SEGURIDAD. " +
                "Requiere validación profesional antes de uso vehicular. " +
                "Este manual NO es un manual de servicio automotriz oficial."
        }

        val steps = mutableListOf<ProcedureStep>()
        steps += ProcedureStep(
            order = 1,
            title = "Identificar pieza",
            description = "Confirme que tiene la pieza correcta para la aplicación. Verifique ID ${part.artifact.id}."
        )
        steps += ProcedureStep(
            order = 2,
            title = "Inspeccionar estado",
            description = "Inspeccione visualmente: grietas, corrosión, desgaste, deformaciones."
        )
        if (part.manufacturingProcessIds.isNotEmpty()) {
            steps += ProcedureStep(
                order = steps.size + 1,
                title = "Considerar proceso de fabricación",
                description = "Pieza típicamente fabricada con: ${part.manufacturingProcessIds.joinToString()}."
            )
        }
        steps += ProcedureStep(
            order = steps.size + 1,
            title = "Documentar dimensiones",
            description = "Registre dimensiones reales: L=${part.dimensions.lengthMm}, W=${part.dimensions.widthMm}, " +
                "H=${part.dimensions.heightMm}, D=${part.dimensions.diameterMm}, T=${part.dimensions.thicknessMm}."
        )

        return ForgeManual(
            id = "manual_${part.artifact.id}",
            artifact = ForgeArtifact(
                id = "manual_${part.artifact.id}",
                name = "Manual de ${part.artifact.name}",
                artifactType = ForgeArtifactType.MANUAL,
                safetyClassification = part.artifact.safetyClassification,
                tags = part.artifact.tags + "manual"
            ),
            manualType = ForgeManualType.FABRICATION_MANUAL,
            scope = "Manual genérico para ${part.artifact.name}.",
            tools = emptyList(),
            materials = part.manufacturingProcessIds,
            torqueSpecs = emptyList(),
            steps = steps,
            inspectionChecklist = listOf(
                "Inspección visual sin grietas",
                "Dimensiones dentro de tolerancia",
                "Sin corrosión excesiva"
            ),
            commonMistakes = listOf(
                "Confundir dimensiones en pulgadas vs mm",
                "No verificar puertos de conexión"
            ),
            safetyWarnings = safetyWarnings,
            finalValidationSteps = listOf("Verificar ajuste en ensamblaje destino"),
            relatedDtcCodes = part.relatedDtcCodes
        )
    }

    fun generateRepairManual(failureMode: FailureMode, part: ForgePart): ForgeManual {
        val safetyWarnings = mutableListOf(
            "ADVERTENCIA GENERAL: simulación educativa.",
            "Use EPP: gafas, guantes, calzado de seguridad."
        )
        if (part.artifact.safetyClassification.isSafetyCritical) {
            safetyWarnings += "PIEZA CRÍTICA. Validación profesional obligatoria."
        }
        val safetyBanner = "PELIGRO: modo de falla '${failureMode.title}' puede comprometer seguridad."

        val steps = mutableListOf<ProcedureStep>()
        steps += ProcedureStep(order = 1, title = "Aislar sistema", description = "Desconectar energía, drenar fluidos.")
        steps += ProcedureStep(order = 2, title = "Inspeccionar", description = "Confirmar modo de falla: ${failureMode.damageType}.")
        steps += ProcedureStep(order = 3, title = "Diagnosticar", description = "Documentar síntomas: ${failureMode.symptoms.joinToString().ifBlank { "N/A" }}.")
        steps += ProcedureStep(order = 4, title = "Reparar", description = "Aplicar procedimiento de reparación para ${part.artifact.name}.")
        steps += ProcedureStep(order = 5, title = "Validar", description = "Verificar恢复正常 operación.")

        val tools = part.repairProcedures.firstOrNull()?.requiredTools ?: emptyList()
        val torqueSpecs = part.repairProcedures.firstOrNull()?.torqueSpecs ?: emptyList()

        return ForgeManual(
            id = "manual_repair_${failureMode.id}",
            artifact = ForgeArtifact(
                id = "manual_repair_${failureMode.id}",
                name = "Reparación: ${failureMode.title}",
                artifactType = ForgeArtifactType.MANUAL,
                safetyClassification = part.artifact.safetyClassification,
                tags = listOf("repair", "manual") + part.artifact.tags
            ),
            manualType = ForgeManualType.REPAIR_MANUAL,
            scope = "Procedimiento de reparación para ${failureMode.title}.",
            tools = tools,
            materials = part.repairProcedures.firstOrNull()?.requiredMaterials ?: emptyList(),
            torqueSpecs = torqueSpecs,
            steps = steps,
            inspectionChecklist = listOf("Síntoma eliminado", "Sin nuevas fugas"),
            commonMistakes = listOf("No reemplazar sellos", "No reapretar al torque correcto"),
            safetyWarnings = safetyWarnings + safetyBanner,
            finalValidationSteps = listOf("Probar sistema bajo carga"),
            relatedDtcCodes = failureMode.relatedDtcCodes
        )
    }

    fun generateReplacementManual(part: ForgePart): ForgeManual {
        val safetyWarnings = mutableListOf(
            "ADVERTENCIA GENERAL: simulación educativa.",
            "Use EPP adecuado."
        )
        if (part.artifact.safetyClassification.isSafetyCritical) {
            safetyWarnings += "PIEZA CRÍTICA. Validación profesional obligatoria."
        }

        val steps = mutableListOf<ProcedureStep>()
        steps += ProcedureStep(order = 1, title = "Preparar área", description = "Asegurar espacio limpio y herramientas a mano.")
        steps += ProcedureStep(order = 2, title = "Desmontar pieza dañada", description = "Seguir orden inverso al ensamblaje.")
        steps += ProcedureStep(order = 3, title = "Inspeccionar montaje", description = "Verificar superficies de contacto, sellos, tornillos.")
        steps += ProcedureStep(order = 4, title = "Instalar pieza nueva", description = "Alinear correctamente y apretar al torque especificado.")
        steps += ProcedureStep(order = 5, title = "Validar funcionamiento", description = "Probar sistema.")

        val tools = part.replacementProcedures.firstOrNull()?.requiredTools ?: emptyList()
        val torqueSpecs = part.replacementProcedures.firstOrNull()?.torqueSpecs ?: emptyList()

        return ForgeManual(
            id = "manual_replace_${part.artifact.id}",
            artifact = ForgeArtifact(
                id = "manual_replace_${part.artifact.id}",
                name = "Reemplazo: ${part.artifact.name}",
                artifactType = ForgeArtifactType.MANUAL,
                safetyClassification = part.artifact.safetyClassification,
                tags = listOf("replacement", "manual") + part.artifact.tags
            ),
            manualType = ForgeManualType.REPLACEMENT_MANUAL,
            scope = "Procedimiento de reemplazo para ${part.artifact.name}.",
            tools = tools,
            materials = part.replacementProcedures.firstOrNull()?.requiredMaterials ?: emptyList(),
            torqueSpecs = torqueSpecs,
            steps = steps,
            inspectionChecklist = listOf("Pieza nueva sin defectos", "Torque aplicado"),
            commonMistakes = listOf("No reemplazar sellos", "Reutilizar tornillos de torque"),
            safetyWarnings = safetyWarnings,
            finalValidationSteps = listOf("Probar bajo carga"),
            relatedDtcCodes = part.relatedDtcCodes
        )
    }

    /**
     * Valida que el manual tenga contenido mínimo: al menos un safetyWarning,
     * al menos un paso, scope no vacío.
     */
    fun validateManualCompleteness(manual: ForgeManual): List<String> {
        val issues = mutableListOf<String>()
        if (manual.safetyWarnings.isEmpty()) {
            issues += "Manual sin advertencias de seguridad — debe tener al menos una."
        }
        if (manual.steps.isEmpty()) {
            issues += "Manual sin pasos — debe incluir al menos un procedimiento."
        }
        if (manual.scope.isBlank()) {
            issues += "Manual sin alcance definido."
        }
        if (manual.manualType == ForgeManualType.REPAIR_MANUAL || manual.manualType == ForgeManualType.REPLACEMENT_MANUAL) {
            if (manual.tools.isEmpty()) {
                issues += "Manual de ${manual.manualType} sin herramientas especificadas."
            }
        }
        return issues
    }
}