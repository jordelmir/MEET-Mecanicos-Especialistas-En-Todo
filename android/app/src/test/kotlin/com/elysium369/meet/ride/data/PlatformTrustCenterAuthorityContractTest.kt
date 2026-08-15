package com.elysium369.meet.ride.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformTrustCenterAuthorityContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `owner authority and decisions are enforced by backend not Android email matching`() {
        val migration = projectFile(
            "supabase/migrations/20260815090000_platform_owner_trust_center.sql",
        ).readText()
        val gateway = projectFile(
            "src/main/kotlin/com/elysium369/meet/ride/data/remote/PlatformTrustCenterGateway.kt",
        ).readText()
        val home = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/screens/HomeScreen.kt",
        ).readText()

        assertTrue(migration.contains("public.meet_is_platform_owner()"))
        assertTrue(migration.contains("public.meet_owner_decide_verification_v1"))
        assertTrue(migration.contains("service_verification_audit_events"))
        assertTrue(migration.contains("email_confirmed_at is not null"))
        assertTrue(migration.contains("PLATFORM_OWNER_REQUIRED"))
        assertTrue(gateway.contains("meet_is_platform_owner"))
        assertTrue(home.contains("PlatformOwnerAccessPolicy"))
        assertFalse(gateway.contains("jordelmir@gmail.com"))
        assertFalse(home.contains("jordelmir@gmail.com"))
    }
}
