package com.elysium369.meet.ride.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RideArrivalAuthorityContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `arrival is enforced by an authenticated one hundred meter rpc`() {
        val sql = projectFile(
            "supabase/migrations/20260801010000_ride_arrival_driver_ops.sql",
        ).readText()
        val gateway = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/data/remote/RideCommandGateway.kt",
        ).readText()

        assertTrue(sql.contains("create or replace function public.ride_driver_arrived_v3"))
        assertTrue(sql.contains("v_distance_meters > 100"))
        assertTrue(sql.contains("p_accuracy_meters not between 0 and 75"))
        assertTrue(sql.contains("p_captured_at < now() - interval '30 seconds'"))
        assertTrue(gateway.contains("\"ride_driver_arrived_v3\""))
    }
}
