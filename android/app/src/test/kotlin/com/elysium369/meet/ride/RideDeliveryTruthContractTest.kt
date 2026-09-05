package com.elysium369.meet.ride

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideDeliveryTruthContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path), File("app/$path"), File("android/app/$path"), File("../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `local publish waits for an authoritative projection`() {
        val viewModel = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt",
        ).readText()
        val worker = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/work/RideCommandSyncWorker.kt",
        ).readText()
        val screen = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/screens/RideServiceScreen.kt",
        ).readText()

        assertTrue(viewModel.contains("status = \"PENDING_PUBLICATION\""))
        assertTrue(viewModel.contains("serverState = \"PENDING_PUBLICATION\""))
        assertTrue(worker.contains("ExistingWorkPolicy.APPEND_OR_REPLACE"))
        assertFalse(worker.contains("ExistingWorkPolicy.KEEP"))
        assertTrue(screen.contains("Confirmando publicación"))
        assertTrue(screen.contains("serverVersion > 0L"))
    }

    @Test
    fun `trust approval requires private reviewable evidence`() {
        val gateway = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/data/remote/PlatformTrustCenterGateway.kt",
        ).readText()
        val migration = projectFile(
            "../../supabase/migrations/20260901090000_rides_evidence_and_parts_rls.sql",
        ).readText()

        assertTrue(gateway.contains("meet_submit_service_verification_v3"))
        assertTrue(gateway.contains("downloadAuthenticated"))
        assertTrue(migration.contains("REVIEWABLE_EVIDENCE_REQUIRED"))
        assertTrue(migration.contains("trust-verification-evidence"))
        assertTrue(migration.contains("meet_owner_decide_verification_v3"))
    }

    @Test
    fun `release worker delivery and trip path evidence survive navigation`() {
        val proguard = projectFile("proguard-rules.pro").readText()
        val worker = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/work/RideLocationBreadcrumbWorker.kt",
        ).readText()
        val trackingService = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/location/RideLocationTrackingService.kt",
        ).readText()
        val manifest = projectFile("src/main/AndroidManifest.xml").readText()
        val viewModel = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt",
        ).readText()
        val screen = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/screens/RideServiceScreen.kt",
        ).readText()
        val migration = projectFile(
            "../../supabase/migrations/20260902090000_ride_location_safety_observability.sql",
        ).readText()
        val adaptiveHome = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/screens/home/adaptive/HomeAdaptiveScreen.kt",
        ).readText()

        assertTrue(proguard.contains("extends androidx.work.InputMerger"))
        assertTrue(worker.contains("DeviceMessageCipher"))
        assertTrue(worker.contains("ExistingWorkPolicy.APPEND_OR_REPLACE"))
        assertTrue(viewModel.contains("combine(_rideDriverMode, _activeRideRequest)"))
        assertTrue(viewModel.contains("RideLocationTrackingService.start"))
        assertTrue(viewModel.contains("RideLocationTrackingService.stop"))
        assertTrue(trackingService.contains("START_STICKY"))
        assertTrue(trackingService.contains("FOREGROUND_SERVICE_TYPE_LOCATION"))
        assertTrue(trackingService.contains("RideLocationBreadcrumbWorker.enqueue"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_LOCATION"))
        assertTrue(manifest.contains(".ride.location.RideLocationTrackingService"))
        assertFalse(screen.contains("viewModel.recordRideSpeedObservation(ride.requestId)"))
        assertTrue(migration.contains("DRIVER_AUTHORITY_REQUIRED"))
        assertTrue(migration.contains("ACTIVE_LEGAL_HOLD_REQUIRED"))
        assertTrue(migration.contains("ride_location_disclosure_audit"))
        assertTrue(migration.contains("interval '90 days'"))
        assertTrue(adaptiveHome.contains("screenWidthDp < 600"))
        assertTrue(adaptiveHome.contains("if (compactHeader) \"\\n\" else \" \""))
    }
}
