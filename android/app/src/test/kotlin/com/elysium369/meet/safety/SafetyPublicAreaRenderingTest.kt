package com.elysium369.meet.safety

import com.elysium369.meet.core.geo.MapCameraIntent
import com.elysium369.meet.safety.geo.SafetyMapAdapter
import com.elysium369.meet.safety.geo.SafetyPublicPoint
import org.junit.Assert.*
import org.junit.Test

class SafetyPublicAreaRenderingTest {
    private fun point() = SafetyPublicPoint(
        "public-area", 10.0, -84.0, "Área documentada", "DOCUMENTED", 2,
        "HOMICIDE", "COARSE_GRID_25KM_PLUS", 25_000,
    )

    @Test fun publicLocationIsRenderedAsUncertaintyAreaWithoutPrecisePin() {
        val state = SafetyMapAdapter.build(listOf(point()))
        assertTrue(state.markers.isEmpty())
        assertEquals(1, state.areas.size)
        val area = state.areas.single()
        assertEquals(25_000, area.uncertaintyMeters)
        assertEquals(4, area.boundary.size)
        assertTrue(area.boundary.all { it.latitude != 10.0 && it.longitude != -84.0 })
        assertTrue(state.cameraIntent is MapCameraIntent.FitBounds)
    }

    @Test fun legacyOrUnknownDisclosureCannotBecomeAnAreaOrAnIncidentPin() {
        val state = SafetyMapAdapter.build(listOf(
            point().copy(geoDisclosure = "EXACT_PUBLIC_PLACE"),
            point().copy(uncertaintyMeters = 1_000),
        ))
        assertTrue(state.markers.isEmpty())
        assertTrue(state.areas.isEmpty())
    }
}
