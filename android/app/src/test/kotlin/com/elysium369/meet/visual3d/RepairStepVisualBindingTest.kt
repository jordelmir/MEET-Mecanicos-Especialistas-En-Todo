package com.elysium369.meet.visual3d

import com.elysium369.meet.visual3d.domain.CameraPreset
import com.elysium369.meet.visual3d.domain.RepairAnimationAction
import com.elysium369.meet.visual3d.domain.RepairStepVisualBinding
import com.elysium369.meet.visual3d.domain.RepairStepVisualValidator
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairStepVisualBindingTest {
    @Test
    fun `generic repair animation cannot invent physical travel distance`() {
        val binding = RepairStepVisualBinding(
            procedureId = "procedure:test",
            stepId = "step:remove",
            focusNodeIds = setOf("component:test"),
            contextNodeIds = setOf("assembly:test"),
            cameraPreset = CameraPreset.REPAIR_STEP,
            animationAction = RepairAnimationAction.REMOVE,
            motionAxis = null,
            motionDistanceMeters = 0.25f,
            dimensionalEvidenceId = null
        )

        assertTrue(
            RepairStepVisualValidator.validate(binding).any {
                it == "Motion distance requires dimensional evidence"
            }
        )
    }

    @Test
    fun `inspection binding keeps literal instruction outside visual contract`() {
        val binding = RepairStepVisualBinding(
            procedureId = "procedure:test",
            stepId = "step:inspect",
            focusNodeIds = setOf("component:test"),
            contextNodeIds = setOf("assembly:test"),
            cameraPreset = CameraPreset.SELECTED_COMPONENT,
            animationAction = RepairAnimationAction.INSPECT
        )

        assertTrue(RepairStepVisualValidator.validate(binding).isEmpty())
    }
}
