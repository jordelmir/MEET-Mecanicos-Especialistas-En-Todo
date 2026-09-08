package com.elysium369.meet.ride

import com.elysium369.meet.ui.navigation.MeetDestinations
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideNavigationConvergenceTest {
    private val projectDir: File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .flatMap { directory -> sequenceOf(directory, File(directory, "android")) }
        .first { candidate -> File(candidate, "app/src/main/kotlin").isDirectory }

    private fun source(relative: String): String =
        File(projectDir, "app/src/main/kotlin/$relative").readText()

    @Test
    fun `all production ride entry points resolve to the wired product`() {
        assertEquals(MeetDestinations.RIDE_HOME, MeetDestinations.RIDE_PASSENGER_REQUEST)
        assertEquals(MeetDestinations.RIDE_HOME, MeetDestinations.RIDE_DRIVER_MODE)
        assertNotEquals(MeetDestinations.RIDE_HOME, MeetDestinations.RIDE_PASSENGER_EXPERIMENT)
        assertNotEquals(MeetDestinations.RIDE_HOME, MeetDestinations.RIDE_DRIVER_EXPERIMENT)

        val adaptiveHome = source(
            "com/elysium369/meet/ui/screens/home/adaptive/HomeAdaptiveScreen.kt",
        )
        assertTrue(adaptiveHome.contains("MeetDestinations.RIDE_PASSENGER_REQUEST"))
        assertTrue(adaptiveHome.contains("MeetDestinations.RIDE_DRIVER_MODE"))
        assertFalse(adaptiveHome.contains("safeNavigate(\"ride_passenger_request\")"))
        assertFalse(adaptiveHome.contains("safeNavigate(\"ride_driver_cockpit\")"))
    }

    @Test
    fun `canonical and quarantined ride destinations are all declared exactly once`() {
        val activity = source("com/elysium369/meet/MainActivity.kt")

        listOf(
            "composable(MeetDestinations.RIDE_HOME)",
            "composable(MeetDestinations.RIDE_ACTIVE_TRACKING)",
            "composable(MeetDestinations.RIDE_PASSENGER_EXPERIMENT)",
            "composable(MeetDestinations.RIDE_DRIVER_EXPERIMENT)",
        ).forEach { declaration ->
            assertEquals(1, activity.windowed(declaration.length).count { it == declaration })
        }
    }
}
