package com.elysium369.meet.operations

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationOperationOwnershipGuardTest {
    private val projectDir: File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .flatMap { dir -> sequenceOf(dir, File(dir, "android")) }
        .first { candidate -> File(candidate, "app/src/main/kotlin").isDirectory }

    private fun source(relative: String) =
        File(projectDir, "app/src/main/kotlin/$relative").readText()

    @Test
    fun `ride realtime is not stopped by leaving ride destination`() {
        val screen = source("com/elysium369/meet/ui/screens/RideServiceScreen.kt")
        assertTrue(screen.contains("viewModel.startRideProjectionSync()"))
        assertFalse(screen.contains("viewModel.stopRideProjectionSync()"))
    }

    @Test
    fun `oscilloscope has explicit stop and navigation does not own it`() {
        val screen = source("com/elysium369/meet/ui/screens/OscilloscopeScreen.kt")
        val back = screen.substringAfter("onBackClick = {").substringBefore("},")
        assertFalse(back.contains("stopOscilloscope"))
        assertFalse(back.contains("stopUsbOscilloscopeStream"))
        val tabs = screen.substringAfter("TabRow(").substringBefore("Spacer(Modifier.height(14.dp))")
        assertFalse(tabs.contains("stopOscilloscope"))
        assertFalse(tabs.contains("stopUsbOscilloscopeStream"))
        assertTrue(screen.contains("if (obdIsRunning) viewModel.stopOscilloscope()"))
    }

    @Test
    fun `publishing Dekra request is not reset by composition disposal`() {
        val screen = source("com/elysium369/meet/ui/screens/DekraConciergeScreen.kt")
        assertFalse(screen.contains("onDispose { viewModel.resetDekraConciergeSubmission()"))
    }

    @Test
    fun `ride selection is owner scoped and restored instead of inferred from refresh`() {
        val viewModel = source("com/elysium369/meet/ui/ObdViewModel.kt")
        assertTrue(viewModel.contains("rideDao.upsertActiveRideSelection"))
        assertTrue(viewModel.contains("rideDao.getActiveRideSelection(ownerId)"))
        assertTrue(viewModel.contains("Active ride unavailable locally; durable pointer retained"))
        val restoreBlock = viewModel.substringAfter("rideDao.getActiveRideSelection(ownerId)")
            .substringBefore("@Serializable")
        assertFalse(restoreBlock.contains("firstOrNull"))
    }
}
