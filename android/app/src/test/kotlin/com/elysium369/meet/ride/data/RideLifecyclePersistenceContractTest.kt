package com.elysium369.meet.ride.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideLifecyclePersistenceContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `persisted ride lifecycle uses compare and set transitions`() {
        val dao = projectFile(
            "src/main/kotlin/com/elysium369/meet/data/local/dao/FeatureDaos.kt",
        ).readText()
        val viewModel = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt",
        ).readText()
        val screen = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/screens/RideServiceScreen.kt",
        ).readText()

        assertTrue(dao.contains("suspend fun transitionRequestStatusAsDriver("))
        assertTrue(dao.contains("AND status = :expectedStatus"))
        assertTrue(dao.contains("AND assignedDriverId = :driverId"))
        assertTrue(dao.contains("suspend fun cancelActiveRequest("))
        assertFalse(dao.contains("suspend fun transitionRequestStatus("))
        assertFalse(viewModel.contains("rideDao.updateRequestStatus("))
        assertFalse(viewModel.contains("rideDao.updateRequestStatusAndCompletedAt("))
        assertFalse(screen.contains("""updateRideStatus(ride.requestId, "CANCELLED")"""))
    }

    @Test
    fun `cancellation is bound to an authenticated trip party`() {
        val dao = projectFile(
            "src/main/kotlin/com/elysium369/meet/data/local/dao/FeatureDaos.kt",
        ).readText()
        val viewModel = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt",
        ).readText()

        assertTrue(dao.contains("(:actorRole = 'PASSENGER' AND passengerId = :actorId)"))
        assertTrue(dao.contains("(:actorRole = 'DRIVER' AND assignedDriverId = :actorId)"))
        assertTrue(viewModel.contains("actorId = actorId"))
        assertTrue(viewModel.contains("actorRole = role.name"))
        assertFalse(viewModel.contains("""?: "SYSTEM""""))
        assertFalse(viewModel.contains("""?: "Sistema""""))
    }
}
