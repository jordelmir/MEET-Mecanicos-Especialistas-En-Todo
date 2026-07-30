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
    fun `cancellation is delegated to actor bound command authority`() {
        val viewModel = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt",
        ).readText()
        val gateway = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/data/remote/RideCommandGateway.kt",
        ).readText()

        assertTrue(viewModel.contains("type = RideCommandType.CANCEL"))
        assertTrue(viewModel.contains("reasonCode = reason.name"))
        assertTrue(viewModel.contains("expectedVersion: Long = request.serverVersion"))
        assertFalse(viewModel.contains("rideDao.cancelActiveRequest("))
        assertTrue(gateway.contains("\"ride_cancel_trip_v2\""))
        assertFalse(gateway.contains("p_actor_id"))
        assertFalse(viewModel.contains("""?: "SYSTEM""""))
        assertFalse(viewModel.contains("""?: "Sistema""""))
    }
}
