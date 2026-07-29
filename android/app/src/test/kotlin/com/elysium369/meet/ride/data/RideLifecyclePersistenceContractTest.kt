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

        assertTrue(dao.contains("suspend fun transitionRequestStatus("))
        assertTrue(dao.contains("AND status = :expectedStatus"))
        assertTrue(dao.contains("suspend fun cancelActiveRequest("))
        assertFalse(viewModel.contains("rideDao.updateRequestStatus("))
        assertFalse(viewModel.contains("rideDao.updateRequestStatusAndCompletedAt("))
        assertFalse(screen.contains("""updateRideStatus(ride.requestId, "CANCELLED")"""))
    }
}
