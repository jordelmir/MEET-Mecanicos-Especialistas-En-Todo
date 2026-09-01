package com.elysium369.meet.ride

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideActorOwnershipContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `ride verification is scoped to authenticated principal not physical device`() {
        val source = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt",
        ).readText()
        val dedicatedSource = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/presentation/RideViewModel.kt",
        ).readText()

        listOf(source, dedicatedSource).forEach { implementation ->
            assertTrue(implementation.contains("activePrincipalKernel.activePrincipal.flatMapLatest"))
            assertTrue(implementation.contains("rideDao.getPassengerVerificationFlow(principal.id)"))
            assertTrue(implementation.contains("rideDao.getDriverVerificationFlow(principal.id)"))
            assertFalse(implementation.contains("getPassengerVerificationFlow(localDeviceId)"))
            assertFalse(implementation.contains("getDriverVerificationFlow(localDeviceId)"))
            assertFalse(implementation.contains("passengerId = localDeviceId"))
            assertFalse(implementation.contains("driverId = localDeviceId"))
        }
    }
}
