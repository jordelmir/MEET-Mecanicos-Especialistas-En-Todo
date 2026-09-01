package com.elysium369.meet.fuel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelRewardsLifecycleGuardTest {
    private val projectDir: File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .flatMap { dir -> sequenceOf(dir, File(dir, "android")) }
        .first { candidate -> File(candidate, "app/src/main/kotlin").isDirectory }

    @Test
    fun `opening Fuel Rewards never initializes optional scanner dependency`() {
        val text = File(
            projectDir,
            "app/src/main/kotlin/com/elysium369/meet/ui/screens/marketos/MarketOsHubs.kt",
        ).readText()
        val hub = text.substringAfter("fun FuelRewardsHub(").substringBefore("@OptIn(ExperimentalMaterial3Api::class)")
        val beforeExplicitAction = hub.substringBefore("PrimaryAction(\"ESCANEAR QR SIN PERMISO DE CÁMARA\"")
        val explicitAction = hub.substringAfter("PrimaryAction(\"ESCANEAR QR SIN PERMISO DE CÁMARA\"")

        assertFalse(beforeExplicitAction.contains("GmsBarcodeScanning.getClient"))
        assertTrue(explicitAction.contains("GmsBarcodeScanning.getClient"))
        assertTrue(explicitAction.contains("SCANNER NO DISPONIBLE · FUEL REWARDS SIGUE ACTIVO"))
    }
}
