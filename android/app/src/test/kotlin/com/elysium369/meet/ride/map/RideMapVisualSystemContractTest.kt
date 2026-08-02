package com.elysium369.meet.ride.map

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RideMapVisualSystemContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `route is rendered with shadow animated glow gradient core and road hierarchy`() {
        val panel = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/screens/RideMapPanel.kt",
        ).readText()

        assertTrue(panel.contains("RoutePulseController"))
        assertTrue(panel.contains("addNeonRouteSegments"))
        assertTrue(panel.contains("applyVanguardRoadHierarchy"))
        assertTrue(panel.contains(".width(17f)"))
        assertTrue(panel.contains("VANGUARD NAV  •  RUTA ACTIVA"))
    }

    @Test
    fun `utility markers use dimensional pin geometry instead of a flat circle`() {
        val renderer = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/map/RideMapAvatarRenderer.kt",
        ).readText()

        assertTrue(renderer.contains("RadialGradient"))
        assertTrue(renderer.contains("val pinBody = Path()"))
        assertTrue(renderer.contains("drawAvatarHalo"))
        assertTrue(renderer.contains("canvas.drawPath(pinBody, body)"))
    }
}
