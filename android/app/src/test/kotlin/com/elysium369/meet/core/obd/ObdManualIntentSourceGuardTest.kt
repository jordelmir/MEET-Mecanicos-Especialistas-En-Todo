package com.elysium369.meet.core.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ObdManualIntentSourceGuardTest {
    private val projectDir: File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .flatMap { dir -> sequenceOf(dir, File(dir, "android")) }
        .first { candidate -> File(candidate, "app/src/main/kotlin").isDirectory }

    private fun source(relative: String): String =
        File(projectDir, "app/src/main/kotlin/$relative").readText()

    @Test
    fun foregroundService_isObservationOnlyAndNotSticky() {
        val text = source("com/elysium369/meet/core/obd/ObdForegroundService.kt")
        assertFalse(Regex("(?m)^\\s*obdSession\\.connect\\(\\)").containsMatchIn(text))
        assertFalse(Regex("(?m)^\\s*obdSession\\.setTargetAddress").containsMatchIn(text))
        assertFalse(Regex("return\\s+START_STICKY\\b").containsMatchIn(text))
        assertTrue(text.contains("return START_NOT_STICKY"))
    }

    @Test
    fun transportContract_hasAbortButNoAutonomousReconnectSurface() {
        val text = source("com/elysium369/meet/core/transport/TransportInterface.kt")
        assertTrue(text.contains("fun abortConnect()"))
        assertFalse(text.contains("fun reconnect("))
    }

    @Test
    fun garageSelection_onlySelectsVehicle() {
        val text = source("com/elysium369/meet/ui/screens/GarageScreen.kt")
        assertTrue(text.contains("onSelect = { viewModel.selectVehicle(vehicle) }"))
        assertFalse(text.contains("onSelect = { viewModel.startDiagnosticSession(vehicle) }"))
    }

    @Test
    fun rememberedAdapterPreparation_neverCallsConnect() {
        val text = source("com/elysium369/meet/ui/ObdViewModel.kt")
        assertFalse(text.contains("preWarmObdConnection"))
        val function = text.substringAfter("fun prepareRememberedAdapter()")
            .substringBefore("fun retryLastAdapterByUserAction()")
        assertFalse(function.contains("connect("))
    }

    @Test
    fun cancel_vetoesAndAbortsBeforeWaitingForJob() {
        val text = source("com/elysium369/meet/ui/ObdViewModel.kt")
        val function = text.substringAfter("fun cancelConnection()")
            .substringBefore("fun forceResetConnection()")
        val vetoIndex = function.indexOf("obdSession.cancelActiveConnectionAttempt()")
        val cancelIndex = function.indexOf("connectionJob?.cancel()")
        val joinIndex = function.indexOf("jobToCancel?.join()")
        assertTrue(vetoIndex >= 0)
        assertTrue(cancelIndex > vetoIndex)
        assertTrue(joinIndex > cancelIndex)
    }

    @Test
    fun explicitDemo_remainsAvailableAndTruthfullyLabelled() {
        val sheet = source("com/elysium369/meet/ui/components/AdapterSearchSheet.kt")
        val session = source("com/elysium369/meet/core/obd/ObdSession.kt")
        assertTrue(sheet.contains("MODO DEMO / SIMULACIÓN"))
        assertTrue(sheet.contains("nunca cuenta como conexión física"))
        assertTrue(session.contains("normalizedAddress == \"SIMULATOR\""))
        assertTrue(session.contains("if (!isDemoTarget) {"))
        assertTrue(session.contains("Modo demo — datos simulados, sin vehículo físico"))
        val viewModel = source("com/elysium369/meet/ui/ObdViewModel.kt")
        val save = viewModel.substringAfter("private suspend fun saveSessionResults()")
            .substringBefore("private fun clearState()")
        assertTrue(save.contains("if (connectionTruth.value.isDemoSession)"))
        val scan = viewModel.substringAfter("suspend fun refreshDiagnostics(")
            .substringBefore("private fun addProfessionalDtcReportLogs")
        assertTrue(scan.contains("truth.isDemoSession"))
    }

    @Test
    fun physicalConnect_isOnlyReachableFromExplicitAdapterSelection() {
        val kotlinRoot = File(projectDir, "app/src/main/kotlin")
        val connectCallSites = kotlinRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    if (line.contains("obdViewModel.connect(")) "${file.relativeTo(kotlinRoot)}:${index + 1}" else null
                }
            }
            .toList()
        assertTrue(connectCallSites.toString(), connectCallSites.size == 1)
        assertTrue(connectCallSites.single().startsWith("com/elysium369/meet/MainActivity.kt:"))
    }

    @Test
    fun wifi_usesContinuousReaderAndPublishesRemoteClose() {
        val text = source("com/elysium369/meet/core/transport/WifiTransport.kt")
        assertTrue(text.contains("startReaderWorker()"))
        assertTrue(text.contains("stream.read(buffer)"))
        assertTrue(text.contains("publishRemoteClosed(\"WiFi stream EOF\")"))
        assertFalse(text.contains("stream.available() > 0"))
    }

    @Test
    fun classic_cancelAndEof_haveAuthoritativeSocketPaths() {
        val text = source("com/elysium369/meet/core/transport/BtClassicTransport.kt")
        val abort = text.substringAfter("override fun abortConnect()")
            .substringBefore("private fun startReaderWorker()")
        assertTrue(abort.contains("socket?.close()"))
        assertTrue(text.contains("catch (cancelled: CancellationException)"))
        assertTrue(text.contains("bytesRead < 0"))
        assertTrue(text.contains("TransportLinkState.RemoteClosed(\"Bluetooth stream EOF\""))
    }

    @Test
    fun ble_disconnectCallback_publishesRemoteClosedImmediately() {
        val text = source("com/elysium369/meet/core/transport/BleTransport.kt")
        val callback = text.substringAfter("newState == BluetoothProfile.STATE_DISCONNECTED")
            .substringBefore("override fun onMtuChanged")
        assertTrue(callback.contains("TransportLinkState.RemoteClosed"))
        assertTrue(callback.contains("TransportLinkEvent.RemoteClosed"))
        assertTrue(text.contains("abortRequested.set(true)"))
        assertTrue(text.contains("catch (cancelled: CancellationException)"))
    }

    @Test
    fun doip_requiresSemanticEcuProofAfterRoutingActivation() {
        val text = source("com/elysium369/meet/core/obd/ObdSession.kt")
        val init = text.substringAfter("private suspend fun initializeDoIpConnection()")
            .substringBefore("private fun wrapDoIpDiagnostics")
        assertTrue(init.contains("\"3E00\""))
        assertTrue(init.contains("startsWith(\"7E00\""))
        assertTrue(init.contains("healthCoordinator.onEcuReady()"))
    }

    @Test
    fun cancelledOrSupersededAttempt_cannotPublishTerminalError() {
        val text = source("com/elysium369/meet/core/obd/ObdSession.kt")
        val guard = text.substringAfter("Connection transaction was cancelled or superseded")
            .substringBefore("// ALL ATTEMPTS EXHAUSTED")
        assertTrue(guard.contains("activeTransport.disconnect()"))
        assertTrue(guard.contains("return"))
    }
}
