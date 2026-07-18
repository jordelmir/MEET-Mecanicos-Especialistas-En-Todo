package com.elysium369.meet.visual3d

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceVehicleAssetTest {
    private fun asset(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
        File("android/app/src/main/assets/$path")
    ).firstOrNull(File::isFile) ?: error("Missing vehicle-twin asset: $path")

    @Test
    fun `reference vehicle is a traceable GLB 2 asset`() {
        val model = asset("models/vehicle_twin/reference_vehicle.glb")
        val header = model.inputStream().use { input -> ByteArray(12).also(input::read) }
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("glTF", header.copyOfRange(0, 4).decodeToString())
        assertEquals(2, buffer.getInt(4))
        assertEquals(model.length(), buffer.getInt(8).toLong())
        assertTrue("Reference vehicle must contain usable geometry", model.length() > 1_000_000L)

        val attribution = asset("models/vehicle_twin/ATTRIBUTION.md").readText()
        assertTrue(attribution.contains("CC BY 4.0"))
        assertTrue(attribution.contains("CarConcept.glb"))
        assertTrue(attribution.contains("interaction pivots", ignoreCase = true))
        assertTrue(attribution.contains("not Hyundai OEM CAD", ignoreCase = true))
    }
}
