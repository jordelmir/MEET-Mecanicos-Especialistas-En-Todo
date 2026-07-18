package com.elysium369.meet.core.catalog

import android.content.Context
import android.content.SharedPreferences
import java.text.Normalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val UNIVERSAL_PARTS_ASSET = "knowledge/catalog/pilot_hyundai_accent_verna_2005_front_end.json"

@Serializable
data class UniversalPartsPack(
    val schemaVersion: Int,
    val packId: String,
    val packVersion: String,
    val title: String,
    val publicationState: String,
    val autoPublishAllowed: Boolean,
    val disclaimer: String,
    val contentSha256: String,
    val vehicleScope: VehicleScope,
    val sourceDocuments: List<SourceDocument>,
    val parts: List<CatalogPart>,
    val procedures: List<RepairProcedure>,
    val statistics: CatalogStatistics
)

@Serializable
data class VehicleScope(
    val vehicleProfileId: String,
    val make: String,
    val models: List<String>,
    val year: Int,
    val engineDisplacementLiters: Double,
    val transmission: String,
    val bindingState: String
)

@Serializable
data class SourceDocument(
    val sourceFileName: String,
    val sourceSha256: String
)

@Serializable
data class CatalogStatistics(
    val partCount: Int,
    val procedureCount: Int,
    val verifiedTechnicalSpecificationCount: Int,
    val automaticallyPublishableCount: Int
)

@Serializable
data class CatalogPart(
    val id: String,
    val nameEs: String,
    val nameEn: String,
    val aliases: List<String>,
    val category: String,
    val system: String,
    val subsystem: String,
    val assembly: String,
    val subassembly: String? = null,
    val position: String,
    val description: String,
    val confidence: String,
    val publicationState: String,
    val compatibilityState: String,
    val compatibilityMessage: String,
    val requiredCompatibilityEvidence: List<String>,
    val technicalSpecifications: TechnicalSpecifications,
    val sourceRefs: List<CatalogSourceRef>,
    val threeDimensionalBinding: ThreeDimensionalBinding
)

@Serializable
data class TechnicalSpecifications(
    val oemNumber: String? = null,
    val torque: String? = null,
    val material: String? = null,
    val dimensions: String? = null,
    val pinout: Map<String, String>? = null
)

@Serializable
data class CatalogSourceRef(
    val sourceFileName: String,
    val sourceDocumentSha256: String,
    val sourceBlockId: String,
    val sourceTextHash: String,
    val sectionPath: List<String>,
    val sourceKind: String,
    val reviewStatus: String
)

@Serializable
data class ThreeDimensionalBinding(
    val sceneId: String,
    val nodeId: String,
    val visualAuthority: String,
    val isDimensionalModel: Boolean
)

@Serializable
data class RepairProcedure(
    val id: String,
    val title: String,
    val targetPartIds: List<String>,
    val publicationState: String,
    val executionPolicy: String,
    val sourceRefs: List<CatalogSourceRef>,
    val difficulty: String,
    val safetyLevel: String,
    val steps: List<RepairStep>
)

@Serializable
data class RepairStep(
    val id: String,
    val order: Int,
    val title: String,
    val instruction: String,
    val warning: String? = null,
    val tools: List<String>,
    val requiredEvidence: List<String>,
    val targetPartId: String,
    val targetNodeId: String,
    val animationAction: String,
    val completionGate: String,
    val technicalValue: String? = null,
    val technicalValueMessage: String? = null
)

object UniversalPartsCatalogParser {
    private val json = Json { ignoreUnknownKeys = false }

    fun decode(raw: String): UniversalPartsPack {
        val pack = json.decodeFromString<UniversalPartsPack>(raw)
        val errors = UniversalPartsCatalogValidator.validate(pack)
        require(errors.isEmpty()) { errors.joinToString(prefix = "Invalid universal parts pack: ", separator = "; ") }
        return pack
    }
}

object UniversalPartsCatalogValidator {
    fun validate(pack: UniversalPartsPack): List<String> = buildList {
        if (pack.schemaVersion != 1) add("unsupported schemaVersion")
        if (pack.packId != "pilot_hyundai_accent_verna_2005_front_end") add("unexpected packId")
        if (pack.publicationState != "REVIEW_REQUIRED" || pack.autoPublishAllowed) add("unsafe publication state")
        if (pack.parts.size < 50) add("at least 50 parts are required")
        if (pack.statistics.partCount != pack.parts.size) add("part count mismatch")
        if (pack.statistics.procedureCount != pack.procedures.size) add("procedure count mismatch")
        if (pack.statistics.verifiedTechnicalSpecificationCount != 0) add("pilot cannot contain verified specifications")

        val ids = pack.parts.map { it.id }
        if (ids.distinct().size != ids.size) add("duplicate part id")
        val knownIds = ids.toSet()
        pack.parts.forEach { part ->
            if (part.sourceRefs.isEmpty()) add("${part.id} has no source")
            if (part.confidence != "UNVERIFIED") add("${part.id} has unsafe confidence")
            if (part.publicationState != "REVIEW_REQUIRED") add("${part.id} has unsafe publication state")
            if (part.compatibilityState != "REQUIRES_VERIFICATION") add("${part.id} has unsafe compatibility")
            if (part.technicalSpecifications.hasAnyValue()) add("${part.id} exposes unverified specification")
            if (part.threeDimensionalBinding.nodeId != part.id) add("${part.id} has broken 3D binding")
            if (part.threeDimensionalBinding.visualAuthority != "GENERIC_SCHEMATIC") add("${part.id} overstates visual authority")
            if (part.threeDimensionalBinding.isDimensionalModel) add("${part.id} cannot be dimensional")
        }
        if (pack.procedures.size < 3) add("at least three procedures are required")
        val stepIds = mutableSetOf<String>()
        pack.procedures.forEach { procedure ->
            if (procedure.executionPolicy != "TRAINING_ONLY_REVIEW_REQUIRED") add("${procedure.id} has unsafe execution policy")
            procedure.targetPartIds.filterNot(knownIds::contains).forEach { add("${procedure.id} targets unknown part $it") }
            procedure.steps.forEach { step ->
                if (!stepIds.add(step.id)) add("duplicate step id ${step.id}")
                if (step.targetPartId !in knownIds || step.targetNodeId !in knownIds) add("${step.id} has broken target")
                if (step.completionGate == "VERIFIED_TORQUE_REQUIRED" && step.technicalValue != null) {
                    add("${step.id} exposes unverified torque")
                }
            }
        }
    }

    private fun TechnicalSpecifications.hasAnyValue(): Boolean =
        oemNumber != null || torque != null || material != null || dimensions != null || pinout != null
}

class UniversalPartsCatalogRepository(
    context: Context,
    private val assetPath: String = UNIVERSAL_PARTS_ASSET
) {
    private val appContext = context.applicationContext
    @Volatile private var cached: UniversalPartsPack? = null

    fun load(): UniversalPartsPack = cached ?: synchronized(this) {
        cached ?: appContext.assets.open(assetPath).bufferedReader().use { reader ->
            UniversalPartsCatalogParser.decode(reader.readText())
        }.also { cached = it }
    }
}

data class CompatibilityAssessment(
    val state: String,
    val message: String,
    val missingEvidence: List<String>
)

object UniversalPartsCatalogEngine {
    fun search(parts: List<CatalogPart>, query: String): List<CatalogPart> {
        val needle = query.normalized()
        if (needle.isBlank()) return parts
        return parts.filter { part ->
            listOf(part.nameEs, part.nameEn, part.category, part.system, part.subsystem, part.assembly)
                .plus(part.aliases)
                .any { it.normalized().contains(needle) }
        }
    }

    fun assessCompatibility(part: CatalogPart, evidenceIds: Set<String>): CompatibilityAssessment {
        val missing = part.requiredCompatibilityEvidence.filterNot(evidenceIds::contains)
        return CompatibilityAssessment(
            state = "REQUIRES_VERIFICATION",
            message = part.compatibilityMessage,
            missingEvidence = missing
        )
    }

    fun canCompleteStep(
        step: RepairStep,
        evidenceIds: Set<String> = emptySet(),
        hasVerifiedTechnicalClaim: Boolean = false
    ): StepGateResult {
        if (step.completionGate == "VERIFIED_TORQUE_REQUIRED" && !hasVerifiedTechnicalClaim) {
            return StepGateResult(false, "Torque no confirmado para esta variante. Adjunte una fuente tecnica verificada.")
        }
        if (step.completionGate != "MANUAL_CONFIRMATION") {
            val missing = step.requiredEvidence.filterNot(evidenceIds::contains)
            if (missing.isNotEmpty()) return StepGateResult(false, "Falta evidencia requerida: ${missing.joinToString()}")
        }
        return StepGateResult(true, null)
    }

    private fun String.normalized(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .trim()
}

data class StepGateResult(val allowed: Boolean, val reason: String?)

@Serializable
data class RepairProgress(
    val procedureId: String,
    val packVersion: String,
    val state: String = "NOT_STARTED",
    val completedStepIds: Set<String> = emptySet(),
    val blockedStepId: String? = null,
    val updatedAtEpochMillis: Long = 0L
)

data class ProgressTransition(val progress: RepairProgress, val reason: String? = null)

object RepairProgressEngine {
    fun toggleStep(
        progress: RepairProgress,
        procedure: RepairProcedure,
        stepId: String,
        evidenceIds: Set<String> = emptySet(),
        hasVerifiedTechnicalClaim: Boolean = false,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): ProgressTransition {
        val step = procedure.steps.firstOrNull { it.id == stepId }
            ?: return ProgressTransition(progress, "Paso desconocido")
        if (stepId in progress.completedStepIds) {
            val remaining = progress.completedStepIds - stepId
            return ProgressTransition(
                progress.copy(
                    state = if (remaining.isEmpty()) "NOT_STARTED" else "IN_PROGRESS",
                    completedStepIds = remaining,
                    blockedStepId = null,
                    updatedAtEpochMillis = nowEpochMillis
                )
            )
        }
        val gate = UniversalPartsCatalogEngine.canCompleteStep(step, evidenceIds, hasVerifiedTechnicalClaim)
        if (!gate.allowed) {
            return ProgressTransition(
                progress.copy(state = "BLOCKED", blockedStepId = stepId, updatedAtEpochMillis = nowEpochMillis),
                gate.reason
            )
        }
        val completed = progress.completedStepIds + stepId
        return ProgressTransition(
            progress.copy(
                state = if (completed.size == procedure.steps.size) "COMPLETED" else "IN_PROGRESS",
                completedStepIds = completed,
                blockedStepId = null,
                updatedAtEpochMillis = nowEpochMillis
            )
        )
    }
}

class RepairProgressStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences("meet_repair_progress", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(procedureId: String, packVersion: String): RepairProgress {
        val fallback = RepairProgress(procedureId = procedureId, packVersion = packVersion)
        val raw = preferences.getString(key(procedureId, packVersion), null) ?: return fallback
        return runCatching { json.decodeFromString<RepairProgress>(raw) }
            .getOrNull()
            ?.takeIf { it.procedureId == procedureId && it.packVersion == packVersion }
            ?: fallback
    }

    fun save(progress: RepairProgress) {
        preferences.edit()
            .putString(key(progress.procedureId, progress.packVersion), json.encodeToString(progress))
            .apply()
    }

    private fun key(procedureId: String, packVersion: String): String = "$packVersion:$procedureId"
}

