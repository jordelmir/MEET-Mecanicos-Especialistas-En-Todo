package com.elysium369.meet.core.obd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdRuntimeCoreTest {

    @Test
    fun `parser handles SEARCHING prefix and decodes RPM`() {
        val parsed = ObdResponseParser.parsePid(
            command = "010C",
            rawResponse = "SEARCHING... 41 0C 1A F8",
            latencyMs = 82,
            timestampMonotonicMs = 1000,
        )

        assertEquals(TelemetryQuality.VALID, parsed.sample.quality)
        assertEquals(1726.0, parsed.sample.value!!, 0.001)
        assertEquals("rpm", parsed.sample.unit)
        assertEquals(ObdDataSource.REAL_OBD, parsed.sample.source)
    }

    @Test
    fun `parser handles compact OBD payload`() {
        val parsed = ObdResponseParser.parsePid("010C", "410C1AF8", 20, 1000)

        assertEquals(TelemetryQuality.VALID, parsed.sample.quality)
        assertEquals(1726.0, parsed.sample.value!!, 0.001)
    }

    @Test
    fun `parser handles CAN header and PCI byte`() {
        val parsed = ObdResponseParser.parsePid("010D", "7E8 03 41 0D 28", 45, 1000)

        assertEquals(TelemetryQuality.VALID, parsed.sample.quality)
        assertEquals(40.0, parsed.sample.value!!, 0.001)
        assertEquals("km/h", parsed.sample.unit)
    }

    @Test
    fun `parser maps NO DATA to unsupported without fake value`() {
        val parsed = ObdResponseParser.parsePid("010C", "NO DATA", 500, 1000)

        assertEquals(TelemetryQuality.UNSUPPORTED, parsed.sample.quality)
        assertEquals(null, parsed.sample.value)
        assertEquals(ObdDataSource.NO_REAL_OBD, parsed.sample.source)
        assertEquals("No soportado", parsed.sample.displayText())
    }

    @Test
    fun `parser maps unknown command and corrupt bytes to parse errors`() {
        val unknown = ObdResponseParser.parsePid("010C", "?", 20, 1000)
        val corrupt = ObdResponseParser.parsePid("010C", "garbage bytes", 20, 1000)

        assertEquals(TelemetryQuality.PARSE_ERROR, unknown.sample.quality)
        assertEquals(TelemetryQuality.PARSE_ERROR, corrupt.sample.quality)
        assertEquals(null, unknown.sample.value)
        assertEquals(null, corrupt.sample.value)
    }

    @Test
    fun `adapter voltage is real OBD only when ATRV parses`() {
        val parsed = ObdResponseParser.parsePid("ATRV", "12.6V", 30, 1000)

        assertEquals(TelemetryQuality.VALID, parsed.sample.quality)
        assertEquals(12.6, parsed.sample.value!!, 0.001)
        assertEquals(ObdDataSource.REAL_OBD, parsed.sample.source)
    }

    @Test
    fun `supported PID discovery decodes bitmap and excludes unsupported loop spam`() {
        val block = SupportedPidDiscovery.decodeSupportedPids("0100", "41 00 18 00 00 00")

        assertEquals(TelemetryQuality.VALID, block.quality)
        assertTrue("0104" in block.supportedPids)
        assertTrue("0105" in block.supportedPids)
        assertFalse("010C" in block.supportedPids)
    }

    @Test
    fun `DTC engine separates confirmed pending and permanent codes`() {
        val result = ObdDiagnosticEngine.combine(
            confirmedRaw = "43 02 30 17 09",
            pendingRaw = "47 02 30",
            permanentRaw = "4A 17 09",
        )

        assertEquals(listOf("P0230", "P1709"), result.confirmed)
        assertEquals(listOf("P0230"), result.pending)
        assertEquals(listOf("P1709"), result.permanent)
        assertTrue(ObdDiagnosticEngine.CLEAR_DTC_WARNING.contains("freeze frame"))
    }

    @Test
    fun `scheduler excludes unsupported pids and degrades on latency`() {
        val fast = TelemetryScheduler.buildPlan(
            supportedPids = setOf("010C", "010D"),
            avgLatencyMs = 100,
            timeoutRate = 0.0,
        )
        val slow = TelemetryScheduler.buildPlan(
            supportedPids = setOf("010C", "010D"),
            avgLatencyMs = 1800,
            timeoutRate = 0.4,
        )

        assertTrue(fast.any { it.command == "010C" })
        assertFalse(fast.any { it.command == "0105" })
        assertTrue(slow.first { it.command == "010C" }.intervalMs > fast.first { it.command == "010C" }.intervalMs)
    }

    @Test
    fun `snapshot without real ECU link is explicitly NO_REAL_OBD`() {
        val snapshot = ObdSnapshotEngine.emptyNoRealObd(nowMs = 1234, reason = "Scanner desconectado")

        assertEquals(ObdDataSource.NO_REAL_OBD, snapshot.evidenceFlag)
        assertFalse(snapshot.hasRealObdEvidence)
        assertEquals("Sin evidencia OBD real", snapshot.freezeFrame?.reason)
        assertNotEquals("", snapshot.rawHash)
    }

    @Test
    fun `snapshot masks VIN and keeps valid sample as real evidence`() {
        val state = ObdConnectionState(
            phase = ObdConnectionPhase.LIVE_STREAMING,
            technicalReason = "ECU conectada",
            timestampMonotonicMs = 1000,
        )
        val sample = ObdResponseParser.parsePid("010D", "41 0D 28", 20, 1000).sample
        val snapshot = ObdSnapshotEngine.capture(
            connectionState = state,
            adapterQuality = AdapterQuality.GOOD,
            protocol = "AUTO",
            vin = "KMHCN46C18U123456",
            dtcReadResult = DtcReadResult(confirmed = listOf("P0230")),
            freezeFrame = null,
            samples = listOf(sample),
            rawEvents = listOf(
                ObdEvent(1000, ObdConnectionPhase.LIVE_STREAMING, "010D", "41 0D 28", "40 km/h", 20, null, "ELM327", "AUTO")
            ),
            capturedAtMonotonicMs = 1001,
        )

        assertEquals(ObdDataSource.REAL_OBD, snapshot.evidenceFlag)
        assertTrue(snapshot.hasRealObdEvidence)
        assertEquals("VIN-****123456", snapshot.vinMasked)
        assertEquals(listOf("P0230"), snapshot.confirmedDtcs)
    }

    @Test
    fun `adapter classifier degrades clones and unstable links`() {
        assertEquals(
            AdapterQuality.CLONE_LIMITED,
            AdapterQualityClassifier.classify(
                AdapterQualityMetrics(ati = "ELM327 v2.1", supportedBasicCommands = 3, avgLatencyMs = 300, protocolDetected = true)
            )
        )
        assertEquals(
            AdapterQuality.UNSTABLE,
            AdapterQualityClassifier.classify(
                AdapterQualityMetrics(ati = "ELM327", supportedBasicCommands = 6, avgLatencyMs = 1800, timeoutRate = 0.6, protocolDetected = true)
            )
        )
    }

    @Test
    fun `command queue retries transport errors and returns the valid response`() = runBlocking {
        val transport = FakeObdTransport(
            "010C" to mutableListOf("NO DATA", "41 0C 1A F8")
        )
        val queue = RobustObdCommandQueue(
            transport = transport,
            nowMs = transport::now,
            sleepMs = { transport.advance(it) },
        )

        val result = queue.execute(QueuedObdCommand("010C", timeoutMs = 1000, retries = 1))

        assertTrue(result.success)
        assertEquals(2, result.attempts)
        assertEquals("41 0C 1A F8", result.rawResponse)
    }

    private class FakeObdTransport(
        vararg scripted: Pair<String, MutableList<String>>,
    ) : ObdTransport {
        private val responses = scripted.toMap().toMutableMap()
        private var clock = 0L

        fun now(): Long = clock
        fun advance(ms: Long) {
            clock += ms
        }

        override suspend fun send(command: String): String {
            clock += 10
            val list = responses[command] ?: return "?"
            return if (list.isEmpty()) "NO DATA" else list.removeAt(0)
        }
    }
}
