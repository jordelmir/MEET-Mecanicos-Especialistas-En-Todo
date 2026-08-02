package com.elysium369.meet.ride

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RideFirstAccessContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `unregistered rider sees role registration before operational dashboards`() {
        val screen = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/screens/RideServiceScreen.kt",
        ).readText()
        val activity = projectFile(
            "src/main/kotlin/com/elysium369/meet/MainActivity.kt",
        ).readText()

        assertTrue(screen.contains("passengerVerification == null"))
        assertTrue(screen.contains("driverVerification == null"))
        assertTrue(screen.contains("RideFirstAccessGateway("))
        assertTrue(screen.contains("REGISTRARME PARA VIAJAR"))
        assertTrue(screen.contains("REGISTRARME COMO CHOFER"))
        assertTrue(screen.contains("COMPLETAR REGISTRO DE CHOFER"))
        assertTrue(activity.contains("navController.navigate(\"provider_registration\")"))
    }
}
