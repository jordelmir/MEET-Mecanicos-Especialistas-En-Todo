package com.elysium369.meet.ride.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RideTenantBoundaryContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `tenant boundary covers dispatch and financial aggregates`() {
        val migration = projectFile(
            "supabase/migrations/20260730070000_ride_tenant_boundary.sql",
        ).readText()

        listOf(
            "alter table public.ride_driver_vehicles",
            "alter table public.ride_requests",
            "alter table public.ride_offers",
            "alter table public.ride_command_receipts",
            "alter table public.ride_commission_reservations",
            "alter table public.ride_ledger_transactions",
            "alter table public.ride_safety_events",
            "alter table public.ride_support_cases",
        ).forEach { required ->
            assertTrue("Missing tenant boundary for $required", migration.contains(required))
        }
        assertTrue(migration.contains("message = 'RIDE_TENANT_MISMATCH'"))
        assertTrue(migration.contains("v.tenant_id = ride_requests.tenant_id"))
        assertTrue(migration.contains("revoke insert, update, delete on public.ride_offers"))
    }
}
