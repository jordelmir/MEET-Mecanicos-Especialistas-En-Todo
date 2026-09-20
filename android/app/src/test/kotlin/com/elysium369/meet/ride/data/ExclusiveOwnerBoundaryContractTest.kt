package com.elysium369.meet.ride.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ExclusiveOwnerBoundaryContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path), File("app/$path"), File("android/app/$path"), File("../$path"), File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `sensitive routes require server granted owner access`() {
        val main = projectFile("src/main/kotlin/com/elysium369/meet/MainActivity.kt").readText()
        assertTrue(main.substringAfter("composable(\"platform_trust_center\")").substringBefore("composable(\"meet_command_center\")").contains("PlatformOwnerRouteGuard"))
        assertTrue(main.substringAfter("composable(\"meet_command_center\")").substringBefore("composable(\"driver_command_center\")").contains("PlatformOwnerRouteGuard"))
        assertTrue(main.contains("refreshPlatformOwnerAccess()"))
    }

    @Test
    fun `database owner authority is exclusive to confirmed designated account`() {
        val migration = projectFile("supabase/migrations/20260920083000_exclusive_platform_owner_boundary.sql").readText()
        assertTrue(migration.contains("lower(coalesce(u.email, '')) = 'jordelmir@gmail.com'"))
        assertTrue(migration.contains("u.email_confirmed_at is not null"))
        assertTrue(migration.contains("EXCLUSIVE_PLATFORM_OWNER_REQUIRED"))
        assertTrue(migration.contains("before insert or update of user_id, role, active"))
    }
}
