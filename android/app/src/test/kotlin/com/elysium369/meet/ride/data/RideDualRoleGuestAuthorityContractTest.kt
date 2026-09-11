package com.elysium369.meet.ride.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RideDualRoleGuestAuthorityContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path), File("app/$path"), File("android/app/$path"),
        File("../$path"), File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun guestRequestIsAtomicAndOutboxCompatible() {
        val sql = projectFile(
            "supabase/migrations/20260911093000_ride_dual_role_self_assignment_guard.sql",
        ).readText()
        val gateway = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/data/remote/RideCommandGateway.kt",
        ).readText()

        assertTrue(sql.contains("function public.ride_create_guest_request_v1"))
        assertTrue(sql.contains("public.ride_create_request_v3("))
        assertTrue(sql.contains("insert into public.guest_ride_profiles"))
        assertTrue(sql.contains("p_request_id, v_actor"))
        assertTrue(sql.contains("alter column ride_request_id drop default"))
        assertTrue(sql.contains("ride_link_passenger_role_from_driver_review"))
        assertTrue(sql.contains("derived_from_application_id"))
        assertTrue(gateway.contains("\"ride_create_guest_request_v1\""))
        assertTrue(gateway.contains("p_guest_phone_e164"))
    }

    @Test
    fun sameAccountCannotOfferOrDriveItsOwnRequest() {
        val sql = projectFile(
            "supabase/migrations/20260911093000_ride_dual_role_self_assignment_guard.sql",
        ).readText()

        assertTrue(sql.contains("SELF_RIDE_ASSIGNMENT_FORBIDDEN"))
        assertTrue(sql.contains("SELF_RIDE_OFFER_FORBIDDEN"))
        assertTrue(sql.contains("request.passenger_id = new.driver_id"))
    }
}
