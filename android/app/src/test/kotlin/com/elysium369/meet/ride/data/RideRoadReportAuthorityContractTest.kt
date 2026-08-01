package com.elysium369.meet.ride.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideRoadReportAuthorityContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `road reports require assigned driver and authoritative active route`() {
        val sql = projectFile(
            "supabase/migrations/20260801020000_ride_reports_route_only.sql",
        ).readText()
        val viewModel = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt",
        ).readText()

        assertTrue(sql.contains("trip_id is not null"))
        assertTrue(sql.contains("r.assigned_driver_id = (select auth.uid())"))
        assertTrue(sql.contains("r.state = 'IN_PROGRESS'"))
        assertTrue(sql.contains("v.verification_status = 'VERIFIED'"))
        assertTrue(viewModel.contains("RideRoadReportAvailabilityPolicy.evaluate("))
        assertFalse(viewModel.contains("remoteReport.copy(trip_id = null)"))
    }
}
