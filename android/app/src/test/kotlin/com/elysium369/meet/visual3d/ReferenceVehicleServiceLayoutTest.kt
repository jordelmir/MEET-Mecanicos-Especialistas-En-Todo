package com.elysium369.meet.visual3d

import com.elysium369.meet.visual3d.domain.ReferenceVehicleServiceLayout
import com.elysium369.meet.visual3d.domain.ServiceOffset
import com.elysium369.meet.visual3d.domain.ServicePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceVehicleServiceLayoutTest {
    @Test
    fun `service layout separates the reference vehicle's physical assemblies`() {
        val offsets = ReferenceVehicleServiceLayout.offsets

        assertTrue(offsets.keys.containsAll(
            setOf(
                "Engine",
                "BodyHood",
                "BodyRearPanelsColor1",
                "BodyDoorRColor1",
                "BodyDoorLColor1",
                "WheelFrontL",
                "WheelFrontR",
                "WheelRearL",
                "WheelRearR",
                "Axles"
            )
        ))
        offsets.values.forEach { offset ->
            assertNotEquals(ServiceOffset.ZERO, offset)
        }
        assertEquals(
            -offsets.getValue("BodyDoorLColor1").x,
            offsets.getValue("BodyDoorRColor1").x,
            0.0001f
        )
    }

    @Test
    fun `service offsets interpolate from assembled to exploded without drift`() {
        val assembled = ReferenceVehicleServiceLayout.offsetFor("BodyHood", progress = 0f)
        val halfway = ReferenceVehicleServiceLayout.offsetFor("BodyHood", progress = 0.5f)
        val exploded = ReferenceVehicleServiceLayout.offsetFor("BodyHood", progress = 1f)

        assertEquals(ServiceOffset.ZERO, assembled)
        assertEquals(exploded.x * 0.5f, halfway.x, 0.0001f)
        assertEquals(exploded.y * 0.5f, halfway.y, 0.0001f)
        assertEquals(exploded.z * 0.5f, halfway.z, 0.0001f)
        assertEquals(ServiceOffset.ZERO, ReferenceVehicleServiceLayout.offsetFor("Unknown", 1f))
    }

    @Test
    fun `assembled layout preserves the source gltf translation instead of resetting nodes to origin`() {
        val sourceHoodPosition = ServicePosition(
            x = -0.000000001f,
            y = -2.3793886f,
            z = 0.1760146f
        )

        val assembled = ReferenceVehicleServiceLayout.positionFor(
            nodeName = "BodyHood",
            sourcePosition = sourceHoodPosition,
            progress = 0f
        )
        val exploded = ReferenceVehicleServiceLayout.positionFor(
            nodeName = "BodyHood",
            sourcePosition = sourceHoodPosition,
            progress = 1f
        )

        assertEquals(sourceHoodPosition, assembled)
        assertEquals(sourceHoodPosition.x, exploded.x, 0.0001f)
        assertEquals(sourceHoodPosition.y - 0.28f, exploded.y, 0.0001f)
        assertEquals(sourceHoodPosition.z + 0.42f, exploded.z, 0.0001f)
    }
}
