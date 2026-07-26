package com.elysium369.meet.visual3d.domain

import com.elysium369.meet.core.knowledge.graph.RepairKnowledgeBundle
import com.elysium369.meet.core.knowledge.graph.RepairVisualAuthority
import com.elysium369.meet.core.knowledge.graph.VehicleApplicabilityState
import com.elysium369.meet.domain.visualdiagnostics.BomSystem
import com.elysium369.meet.domain.visualdiagnostics.VisualBomAtlas
import com.elysium369.meet.visual3d.ui.TwinFocusMode
import com.elysium369.meet.visual3d.ui.VehicleTwinViewportState

enum class RepairVisualDisposition {
    FOCUSABLE,
    EDUCATIONAL_ONLY,
    UNAVAILABLE
}

enum class RepairVisualAssetClass {
    PROCEDURAL_DIAGNOSTIC,
    GENERIC_SERVICE_ASSET,
    UNAVAILABLE
}

data class RepairVisualNavigationTarget(
    val semanticNodeId: String,
    val componentCanonicalKey: String,
    val label: String,
    val meshKey: String?,
    val componentIds: List<String>,
    val cameraPreset: CameraPreset,
    val disposition: RepairVisualDisposition,
    val assetClass: RepairVisualAssetClass,
    val visualAuthority: VisualAuthority,
    val isDimensionalModel: Boolean,
    val exactnessDisclaimer: String,
    val reason: String,
    val citationIds: List<String>
) {
    val canFocus: Boolean
        get() = disposition == RepairVisualDisposition.FOCUSABLE && meshKey != null
}

data class RepairVisualNavigationPlan(
    val targets: List<RepairVisualNavigationTarget>,
    val primaryTarget: RepairVisualNavigationTarget?,
    val warnings: List<String>
) {
    val canNavigate: Boolean
        get() = primaryTarget?.canFocus == true
}

/**
 * Converts evidence-gated graph targets into existing MEET 3D semantic mesh identifiers.
 *
 * This bridge never consumes the legacy DTC probability table. A DTC does not select geometry;
 * only a target emitted by [RepairKnowledgeBundle] can do so. All currently generated assets are
 * kept at generic/procedural authority and explicitly non-dimensional.
 */
object RepairKnowledgeVisualNavigator {
    fun plan(bundle: RepairKnowledgeBundle): RepairVisualNavigationPlan {
        val candidates = bundle.candidates.associateBy { it.canonicalKey }
        val warnings = linkedSetOf<String>()
        val targets = bundle.visualTargets.map { target ->
            val bom = VisualBomAtlas.find(target.componentCanonicalKey)
            val applicability = candidates[target.componentCanonicalKey]?.applicability?.state
            val restricted = applicability in RESTRICTED_APPLICABILITY
            val disposition = when {
                bom == null -> RepairVisualDisposition.UNAVAILABLE
                restricted -> RepairVisualDisposition.EDUCATIONAL_ONLY
                else -> RepairVisualDisposition.FOCUSABLE
            }
            if (bom == null) {
                warnings +=
                    "${target.componentCanonicalKey}: no existe un enlace semántico 3D validado."
            }
            if (restricted) {
                warnings +=
                    "${target.componentCanonicalKey}: visualización educativa; aplicabilidad no confirmada."
            }
            val assetClass = when {
                bom == null -> RepairVisualAssetClass.UNAVAILABLE
                target.authority == RepairVisualAuthority.PROCEDURAL_SCHEMATIC ->
                    RepairVisualAssetClass.PROCEDURAL_DIAGNOSTIC
                else -> RepairVisualAssetClass.GENERIC_SERVICE_ASSET
            }
            RepairVisualNavigationTarget(
                semanticNodeId = target.semanticNodeId,
                componentCanonicalKey = target.componentCanonicalKey,
                label = target.label,
                meshKey = bom?.meshKey,
                componentIds = bom?.componentIds.orEmpty().distinct().sorted(),
                cameraPreset = bom?.system?.cameraPreset() ?: CameraPreset.SELECTED_COMPONENT,
                disposition = disposition,
                assetClass = assetClass,
                visualAuthority = VisualAuthority.GENERIC_SCHEMATIC,
                isDimensionalModel = false,
                exactnessDisclaimer = bom?.exactnessDisclaimer
                    ?: "Visual 3D no disponible; consulte diagrama OEM y verifique físicamente.",
                reason = target.reason,
                citationIds = target.citationIds.distinct().sorted()
            )
        }.distinctBy { "${it.semanticNodeId}|${it.componentCanonicalKey}" }
            .sortedWith(
                compareBy<RepairVisualNavigationTarget> { it.disposition.ordinal }
                    .thenBy(RepairVisualNavigationTarget::componentCanonicalKey)
            )
        return RepairVisualNavigationPlan(
            targets = targets,
            primaryTarget = targets.firstOrNull { it.canFocus },
            warnings = warnings.toList().sorted()
        )
    }

    private fun BomSystem.cameraPreset(): CameraPreset = when (this) {
        BomSystem.ENGINE,
        BomSystem.ELECTRICAL,
        BomSystem.MODULES_CONTROLLERS,
        BomSystem.SENSORS,
        BomSystem.ACTUATORS,
        BomSystem.FLUIDS_CONSUMABLES,
        BomSystem.FASTENERS_HARDWARE -> CameraPreset.ENGINE_BAY
        BomSystem.TRANSMISSION_DRIVELINE,
        BomSystem.SUSPENSION,
        BomSystem.STEERING,
        BomSystem.BRAKES,
        BomSystem.WHEELS_TIRES,
        BomSystem.HYBRID_EV -> CameraPreset.UNDERBODY_FRONT
        BomSystem.HVAC,
        BomSystem.PASSIVE_SAFETY,
        BomSystem.INTERIOR -> CameraPreset.CABIN_FRONT
        BomSystem.BODY,
        BomSystem.LIGHTING,
        BomSystem.ADAS,
        BomSystem.ACCESS_IMMOBILIZER,
        BomSystem.VEHICLE_CORE -> CameraPreset.EXTERIOR_FRONT_3Q
    }

    private val RESTRICTED_APPLICABILITY = setOf(
        VehicleApplicabilityState.NOT_DOCUMENTED,
        VehicleApplicabilityState.NOT_APPLICABLE,
        VehicleApplicabilityState.CONFLICTED
    )
}

fun VehicleTwinViewportState.applyRepairKnowledge(
    plan: RepairVisualNavigationPlan
): VehicleTwinViewportState = if (plan.canNavigate) {
    copy(
        focusMode = TwinFocusMode.COMPONENT,
        xRayEnabled = true,
        autoRotateEnabled = false,
        cameraResetNonce = cameraResetNonce + 1
    )
} else {
    this
}
