package com.elysium369.meet.ride.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideGuardianAuthorityContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `guardian is actor bound idempotent and does not claim authority contact`() {
        val migration = projectFile(
            "supabase/migrations/20260730050000_ride_guardian_safety.sql",
        ).readText()
        val gateway = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/data/remote/RideCommandGateway.kt",
        ).readText()
        val screen = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/screens/RideServiceScreen.kt",
        ).readText()
        val outbox = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/data/local/RideCommandOutboxDao.kt",
        ).readText()

        assertTrue(migration.contains("v_user_id <> v_request.passenger_id"))
        assertTrue(migration.contains("ride_command_replay("))
        assertTrue(migration.contains("authorities_contacted = false"))
        assertTrue(migration.contains("ride_record_command_receipt("))
        assertTrue(gateway.contains("\"ride_signal_safety_v2\""))
        assertFalse(screen.contains("Uri.parse(\"tel:"))
        assertFalse(screen.contains("Teléfono: \${ride.passengerPhone}"))
        assertTrue(outbox.contains("""payloadJson = '{"redacted_after_ack":true}'"""))
    }
}
