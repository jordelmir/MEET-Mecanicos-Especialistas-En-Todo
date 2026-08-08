package com.elysium369.meet.ride.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RideMeteredFareAuthorityContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `metered fares are server checked and rate card is snapshotted`() {
        val sql = projectFile(
            "supabase/migrations/20260808010000_ride_v5_metered_fares.sql",
        ).readText()
        val gateway = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/data/remote/RideCommandGateway.kt",
        ).readText()

        assertTrue(sql.contains("create or replace function public.ride_create_request_v3"))
        assertTrue(sql.contains("p_distance_rate_minor_per_km <> 300"))
        assertTrue(sql.contains("p_time_rate_minor_per_minute <> 60"))
        assertTrue(sql.contains("FARE_ESTIMATE_MISMATCH"))
        assertTrue(sql.contains("fare_rate_card_version"))
        assertTrue(gateway.contains("\"ride_create_request_v3\""))
    }

    @Test
    fun `only passenger metered trips can replace active stops`() {
        val sql = projectFile(
            "supabase/migrations/20260808010000_ride_v5_metered_fares.sql",
        ).readText()

        assertTrue(sql.contains("create or replace function public.ride_replace_stops_v3"))
        assertTrue(sql.contains("v_request.passenger_id <> v_user_id"))
        assertTrue(sql.contains("v_request.fare_mode <> 'METERED_TIME_DISTANCE'"))
        assertTrue(sql.contains("p_expected_version"))
        assertTrue(sql.contains("ROUTE_STOPS_CHANGED"))
    }
}
