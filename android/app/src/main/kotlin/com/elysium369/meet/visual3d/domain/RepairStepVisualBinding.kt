package com.elysium369.meet.visual3d.domain

enum class RepairAnimationAction {
    INSPECT,
    DISCONNECT,
    DRAIN,
    SUPPORT,
    LOOSEN,
    REMOVE,
    MOVE_ASIDE,
    CLEAN,
    MEASURE,
    INSTALL,
    TIGHTEN,
    TORQUE,
    RECONNECT,
    FILL,
    CALIBRATE,
    VERIFY
}

data class RepairStepVisualBinding(
    val procedureId: String,
    val stepId: String,
    val focusNodeIds: Set<String>,
    val contextNodeIds: Set<String> = emptySet(),
    val ghostNodeIds: Set<String> = emptySet(),
    val hideNodeIds: Set<String> = emptySet(),
    val removeNodeIds: Set<String> = emptySet(),
    val supportNodeIds: Set<String> = emptySet(),
    val cameraPreset: CameraPreset,
    val animationAction: RepairAnimationAction,
    val motionAxis: TwinVector3? = null,
    val motionDistanceMeters: Float? = null,
    val dimensionalEvidenceId: String? = null,
    val toolOverlayIds: Set<String> = emptySet(),
    val hazardOverlayIds: Set<String> = emptySet(),
    val evidencePromptIds: Set<String> = emptySet()
)

object RepairStepVisualValidator {
    fun validate(binding: RepairStepVisualBinding): List<String> = buildList {
        if (binding.procedureId.isBlank()) add("Procedure ID is required")
        if (binding.stepId.isBlank()) add("Step ID is required")
        if (binding.focusNodeIds.isEmpty()) add("At least one focus node is required")
        if (binding.motionDistanceMeters != null && binding.dimensionalEvidenceId.isNullOrBlank()) {
            add("Motion distance requires dimensional evidence")
        }
        if (binding.motionDistanceMeters != null && binding.motionAxis == null) {
            add("Motion distance requires an axis")
        }
        if (binding.motionDistanceMeters != null && binding.motionDistanceMeters <= 0f) {
            add("Motion distance must be positive")
        }
    }
}
