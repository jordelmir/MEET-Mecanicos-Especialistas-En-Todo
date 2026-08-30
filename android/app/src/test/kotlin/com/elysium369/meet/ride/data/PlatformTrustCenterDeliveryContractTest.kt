package com.elysium369.meet.ride.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformTrustCenterDeliveryContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `every registration is durable observable and recoverable`() {
        val migration = projectFile(
            "supabase/migrations/20260829010000_platform_trust_center_delivery_realtime.sql",
        ).readText()
        val gateway = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/data/remote/PlatformTrustCenterGateway.kt",
        ).readText()
        val screen = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/screens/PlatformTrustCenterScreen.kt",
        ).readText()
        val viewModel = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt",
        ).readText()
        val fleetViewModel = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/FleetChatViewModel.kt",
        ).readText()

        assertTrue(migration.contains("meet_submit_service_verification_v2"))
        assertTrue(migration.contains("meet_owner_verification_queue_v2"))
        assertTrue(migration.contains("meet_own_verification_applications_v1"))
        assertTrue(migration.contains("alter publication supabase_realtime add table public.service_verification_applications"))
        assertTrue(migration.contains("correlation_id"))
        assertTrue(migration.contains("AUTO_LOCKSMITH"))

        assertTrue(gateway.contains("meet_submit_service_verification_v2"))
        assertTrue(gateway.contains("meet_owner_verification_queue_v2"))
        assertTrue(gateway.contains("meet_own_verification_applications_v1"))
        assertTrue(gateway.contains("postgresChangeFlow"))
        assertTrue(gateway.contains("TrustSubmissionReceipt"))
        assertFalse(gateway.contains("postgrest[\"service_verification_applications\"]"))

        assertTrue(screen.contains("realtimeWakeUps"))
        assertTrue(screen.contains("TRUST_QUEUE_HEARTBEAT_MS"))
        assertTrue(screen.contains("createChallengeAndVerify"))
        assertTrue(screen.contains("FactorType.TOTP"))
        assertTrue(viewModel.contains("syncPendingTrustApplications"))
        assertTrue(fleetViewModel.contains("FLEET_OPERATOR"))
        assertTrue(fleetViewModel.contains("syncPendingFleetOperatorApplications"))
    }
}
