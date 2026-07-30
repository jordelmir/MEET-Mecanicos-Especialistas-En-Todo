package com.elysium369.meet.ride.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RideSupportAuthorityContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `support cases use command authority and compensating money references`() {
        val migration = projectFile(
            "supabase/migrations/20260730060000_ride_support_cases.sql",
        ).readText()
        val gateway = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/data/remote/RideCommandGateway.kt",
        ).readText()
        val viewModel = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt",
        ).readText()

        assertTrue(migration.contains("v_user_id <> v_request.passenger_id"))
        assertTrue(migration.contains("ride_command_replay("))
        assertTrue(migration.contains("ride_record_command_receipt("))
        assertTrue(migration.contains("financial_adjustment_transaction_id"))
        assertTrue(migration.contains("support never edits ledger postings"))
        assertTrue(gateway.contains("\"ride_open_support_case_v2\""))
        assertTrue(viewModel.contains("type = RideCommandType.OPEN_SUPPORT_CASE"))
    }
}
