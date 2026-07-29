package com.elysium369.meet.ride.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideOfferAcceptanceContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `offer acceptance is request-bound atomic and retry-safe`() {
        val dao = projectFile(
            "src/main/kotlin/com/elysium369/meet/data/local/dao/FeatureDaos.kt",
        ).readText()
        val viewModel = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt",
        ).readText()

        assertTrue(dao.contains("suspend fun acceptOfferAtomically("))
        assertTrue(dao.contains("if (offer.requestId != requestId)"))
        assertTrue(dao.contains("AND status = 'OPEN'"))
        assertTrue(dao.contains("AND assignedDriverId IS NULL"))
        assertTrue(dao.contains("AND acceptedOfferId IS NULL"))
        assertTrue(dao.contains("RideOfferAcceptanceOutcome.ALREADY_ACCEPTED"))
        assertTrue(viewModel.contains("rideDao.acceptOfferAtomically(requestId, offerId)"))
        assertFalse(viewModel.contains("rideDao.acceptOffer("))
    }
}
