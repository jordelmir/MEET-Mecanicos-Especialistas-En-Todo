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
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `offer acceptance is request-bound atomic and retry-safe`() {
        val viewModel = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt",
        ).readText()
        val gateway = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/data/remote/RideCommandGateway.kt",
        ).readText()
        val migration = projectFile(
            "supabase/migrations/20260730030000_ride_passenger_driver_commands.sql",
        ).readText()

        assertTrue(viewModel.contains("type = RideCommandType.ACCEPT_OFFER"))
        assertTrue(viewModel.contains("RideCommandPayload(offerId = offerId)"))
        assertFalse(viewModel.contains("rideDao.acceptOfferAtomically(requestId, offerId)"))
        assertFalse(viewModel.contains("rideDao.acceptOffer("))
        assertTrue(gateway.contains("\"ride_accept_offer_v2\""))
        assertTrue(migration.contains("for update;"))
        assertTrue(migration.contains("version = p_expected_version"))
        assertTrue(migration.contains("ride_record_command_receipt("))
    }
}
