package com.elysium369.meet.ride.map

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideMapPinCameraOwnershipContractTest {
    @Test
    fun `live pin coordinates cannot restart initial camera zoom`() {
        val source = projectFile(
            "app/src/main/kotlin/com/elysium369/meet/ui/screens/RideMapPanel.kt",
        ).readText()

        assertFalse(
            "Live pin updates must not be a key of the one-time camera initializer",
            source.contains(
                "LaunchedEffect(pinSelectionEnabled, pinSelectionInitialPoint, latestMap, styleReady)",
            ),
        )
        assertTrue(
            "The pin camera must explicitly record one-time initialization",
            source.contains("pinCameraInitialized"),
        )
    }

    private fun projectFile(relativePath: String): File {
        var cursor = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            File(cursor, relativePath).takeIf(File::exists)?.let { return it }
            cursor = cursor.parentFile ?: return@repeat
        }
        error("Project file not found: $relativePath")
    }
}
