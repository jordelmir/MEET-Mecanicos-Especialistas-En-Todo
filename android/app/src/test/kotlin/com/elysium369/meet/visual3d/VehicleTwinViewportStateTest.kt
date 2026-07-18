package com.elysium369.meet.visual3d

import com.elysium369.meet.visual3d.ui.TwinFocusMode
import com.elysium369.meet.visual3d.ui.VehicleTwinViewportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VehicleTwinViewportStateTest {
    @Test
    fun `entering service view freezes the camera and requests full separation`() {
        val serviceState = VehicleTwinViewportState(autoRotateEnabled = true).enterRepair()

        assertEquals(TwinFocusMode.REPAIR, serviceState.focusMode)
        assertEquals(1f, serviceState.explodedProgress, 0f)
        assertFalse(serviceState.autoRotateEnabled)
    }
}
