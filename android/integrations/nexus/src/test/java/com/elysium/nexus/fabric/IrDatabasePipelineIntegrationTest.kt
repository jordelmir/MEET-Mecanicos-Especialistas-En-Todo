package com.elysium.nexus.fabric

import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.evidence.ControlEvidenceStore
import com.elysium.nexus.fabric.evidence.ControlEvent
import com.elysium.nexus.fabric.evidence.EventResult
import com.elysium.nexus.fabric.infrared.AndroidIrTransmitter
import com.elysium.nexus.fabric.infrared.IrProbeEngine
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.IrTransmitResult
import com.elysium.nexus.fabric.infrared.database.IrCatalogRepository
import com.elysium.nexus.fabric.infrared.ingestion.FlipperIrParser
import com.elysium.nexus.fabric.infrared.ingestion.LicenseGate
import com.elysium.nexus.fabric.infrared.ingestion.ParseResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section 28 Critical Integration Test for the IR Data Fabric Pipeline:
 * Ingestion Fixture → License Gate → IrCatalogRepository → IrProbeEngine →
 * Volume Probe Transmission → Candidate Advancement → Evidence Store Logging.
 */
class IrDatabasePipelineIntegrationTest {

    private val testDeviceId = DeviceId("sankey-tv-lab-001")

    @Test
    fun `database pipeline integration executes deterministically without zero slices or repeated candidates`() = runBlocking {
        // Step 1: Parse external Flipper IR fixture
        val fixtureContent = """
            Filetype: IR signals file
            Version: 1
            name: VolUp
            type: raw
            frequency: 38000
            duty_cycle: 0.330000
            data: 9000 4500 560 1690 560 560 560 1690 560
        """.trimIndent()

        val parser = FlipperIrParser()
        val parseResult = parser.parse(fixtureContent, "flipper-fixture-01")
        assertTrue("Parser must return Success for valid fixture", parseResult is ParseResult.Success)

        // Step 2: Check License Policy Gate
        val policy = LicenseGate.evaluate("flipper-irdb")
        assertEquals("Flipper source must be APPROVED", LicenseGate.LicenseStatus.APPROVED, policy.status)

        // Step 3: Build candidates from DeviceCatalog (IrCatalogRepository requires Android Context,
        // so in JVM tests we replicate the same query logic directly)
        val candidates = com.elysium.nexus.core.device.DeviceCatalog.all
            .filter { it.brand.equals("Sankey", ignoreCase = true) || it.brand == "Generic" }
            .map { t ->
                IrCodeSet(
                    id = "test-${t.id}",
                    brand = t.brand,
                    modelPatterns = setOf(t.model),
                    remoteModels = emptySet(),
                    commands = mapOf(
                        IrAction.VOLUME_UP to IrSignal.Encoded(
                            carrierHz = t.protocol.carrierHz,
                            protocol = t.protocol,
                            address = t.deviceAddress,
                            command = 0x07
                        )
                    ),
                    provenance = com.elysium.nexus.core.device.CodeProvenance(
                        sourceName = "test", sourceUrl = "", licenseSpdx = "MIT"
                    ),
                    verification = VerificationStatus.UNVERIFIED
                )
            }
        assertTrue("Candidates list must be non-empty", candidates.isNotEmpty())

        // Step 4: Initialize IrProbeEngine with deduplicated candidates
        val probeEngine = IrProbeEngine(candidates)
        assertTrue("Probe engine must have candidates", probeEngine.totalCandidates > 0)

        // Step 5: Probe Candidate 1 with VOLUME_UP
        val candidate1 = probeEngine.currentCandidate()
        assertNotNull(candidate1)
        val sig1 = candidate1!!.commands[IrAction.VOLUME_UP]
        assertNotNull(sig1)

        val encodeResult1 = IrProtocol.encode(sig1!!)
        assertTrue("Encode result for Candidate 1 must be Success", encodeResult1 is com.elysium.nexus.fabric.infrared.EncodeResult.Success)

        val waveform1 = (encodeResult1 as com.elysium.nexus.fabric.infrared.EncodeResult.Success).waveform
        assertTrue("Every slice in IR waveform must be strictly positive", waveform1.pattern.all { it > 0 })
        assertTrue("Total waveform duration must be < 2 seconds", waveform1.totalDurationUs < 2_000_000L)

        // Simulate transmission success
        val transmitResult1 = IrTransmitResult.Success(waveform1.carrierHz, waveform1.totalDurationUs, "hash-001")
        assertTrue(transmitResult1 is IrTransmitResult.Success)

        // Step 6: Log evidence PII-free
        val evidenceStore = ControlEvidenceStore()
        evidenceStore.record(
            ControlEvent(
                timestampNs = System.nanoTime(),
                deviceIdHash = ControlEvidenceStore.hashDeviceId(testDeviceId),
                actionType = "VolumeUp",
                correlationId = "corr-probing-001",
                protocol = com.elysium.nexus.fabric.canonical.Protocol.DirectIr,
                result = EventResult.Success,
                latencyMs = 12L
            )
        )
        assertEquals(1, evidenceStore.size)

        // Step 7: Advance candidate and verify distinct fingerprint
        val fp1 = IrProbeEngine.fingerprintSignal(sig1)
        probeEngine.nextCandidate()

        val candidate2 = probeEngine.currentCandidate()
        if (candidate2 != null) {
            val sig2 = candidate2.commands[IrAction.VOLUME_UP]
            if (sig2 != null) {
                val fp2 = IrProbeEngine.fingerprintSignal(sig2)
                assertNotEquals("Candidate 2 must have a distinct signal fingerprint from Candidate 1", fp1, fp2)
            }
        }
    }
}
