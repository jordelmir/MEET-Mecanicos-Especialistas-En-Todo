package com.elysium369.meet.ride

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRideRoleWiringContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `onboarding exposes both ride roles and authenticated sync`() {
        val onboarding = projectFile(
            "src/main/kotlin/com/elysium369/meet/ui/screens/OnboardingScreen.kt",
        ).readText()
        val activity = projectFile(
            "src/main/kotlin/com/elysium369/meet/MainActivity.kt",
        ).readText()
        val profiles = projectFile(
            "src/main/kotlin/com/elysium369/meet/identity/OnboardingUsageProfile.kt",
        ).readText()

        assertTrue(onboarding.contains("OnboardingUsageProfile.entries"))
        assertTrue(profiles.contains("Usuario de viajes"))
        assertTrue(profiles.contains("Conductor"))
        assertTrue(profiles.contains("ride_passenger"))
        assertTrue(profiles.contains("ride_driver"))
        assertTrue(activity.contains("syncSelectedUsageProfile"))
    }

    @Test
    fun `supabase activation is actor bound least privilege and verification honest`() {
        val migration = projectFile(
            "supabase/migrations/20260816010000_authenticated_usage_roles.sql",
        ).readText()

        assertTrue(migration.contains("function public.meet_activate_usage_profile_v1"))
        assertTrue(migration.contains("v_user_id uuid := (select auth.uid())"))
        assertTrue(migration.contains("'ride_passenger'"))
        assertTrue(migration.contains("'ride_driver'"))
        assertTrue(migration.contains("verification_required"))
        assertTrue(migration.contains("revoke all on function public.meet_activate_usage_profile_v1"))
        assertTrue(migration.contains("grant execute on function public.meet_activate_usage_profile_v1"))
        assertTrue(migration.contains("to authenticated"))
    }
}
