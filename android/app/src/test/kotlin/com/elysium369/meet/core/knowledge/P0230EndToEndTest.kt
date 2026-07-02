package com.elysium369.meet.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end test for P0230 on Hyundai Accent 2005 1.6 AT.
 * Verifies the full pipeline: load pack -> import -> normalize DTC
 * -> lookup profile -> rank causes.
 */
class P0230EndToEndTest {

    private val importer = KnowledgePackImporter()

    @Test
    fun `P0230 pack imports and ranks power ground relay first under low voltage`() {
        val raw = loadAsset("pack_06_dtc_P0230.json")
        val parseResult = importer.parse(raw)
        assertNotNull("Parse should succeed", parseResult.getOrNull())
        val pack = parseResult.getOrThrow()
        assertEquals("pack_06_dtc_P0230", pack.packId)
        assertEquals(10, pack.nodes.size)
        assertEquals(3, pack.edges.size)

        val importResult = importer.importPack(pack)
        assertTrue("Import should succeed", importResult is PackImportResult.Success)
        val s = importResult as PackImportResult.Success
        assertEquals(10, s.nodesAccepted)
        assertEquals(3, s.edgesAccepted)

        // Normalize the DTC
        val engine = DtcEngine()
        val norm = engine.normalize("P0230")
        assertTrue(norm is DtcEngine.NormalizeResult.Valid)
        val normCode = (norm as DtcEngine.NormalizeResult.Valid).code
        assertEquals("P0230", normCode)

        // Look up profile from the pack's profiles[]
        val p0230 = pack.profiles.firstOrNull { it.code == "P0230" }
        assertNotNull("P0230 profile should be in pack", p0230)
        val profile = p0230!!

        // Run priority engine with Hyundai Accent 2005 context.
        val ctx = PriorityEngine.DiagnosticContext(
            dtcCode = "P0230",
            dtcStatus = "ACTIVE",
            freezeFrame = mapOf(
                "BatteryVoltage" to 11.2,
                "FuelPumpCommand" to 1.0,  // ON
                "RPM" to 0.0,
                "ECT" to 87.0,
                "EngineLoad" to 15.0
            ),
            livePids = emptyMap(),
            scannerConnected = true,
            coOccurringDtcs = listOf("P0231"),
            completedTests = emptyList(),
            communityEvidence = false,
            sourceTier = "A_OWNED_CREATED"
        )

        val ranked = PriorityEngine().rank(
            profile = profile,
            rankedCauses = listOf(
                "cause_battery_ground" to 0.32,
                "cause_relay"         to 0.24,
                "cause_fuse_feed"     to 0.18,
                "cause_connector"     to 0.12,
                "cause_pump_motor"    to 0.10,
                "cause_pcm_driver"    to 0.04
            ),
            ctx = ctx
        )

        // Expect: battery_ground FIRST (low voltage boosts it), PCM LAST.
        assertEquals("cause_battery_ground", ranked.first().causeId)
        assertEquals("cause_pcm_driver", ranked.last().causeId)

        // Verify ordering: top 3 are power/ground/relay/fuse, never pump or PCM.
        val top3 = ranked.take(3).map { it.causeId }.toSet()
        assertTrue("Top 3 must be power/ground/relay, got $top3",
            top3.intersect(setOf("cause_battery_ground", "cause_relay", "cause_fuse_feed")).size >= 2)

        // Verify confidence is high because scanner connected and freeze frame present.
        assertTrue(ranked.first().confidence >= 0.7)
    }

    @Test
    fun `P0230 with scanner disconnected lowers all confidences`() {
        val raw = loadAsset("pack_06_dtc_P0230.json")
        val pack = importer.parse(raw).getOrThrow()
        val profile = pack.profiles.first { it.code == "P0230" }

        val ctx = PriorityEngine.DiagnosticContext(
            dtcCode = "P0230",
            dtcStatus = "PENDING",
            freezeFrame = emptyMap(),  // no scanner -> no freeze frame
            livePids = emptyMap(),
            scannerConnected = false,
            coOccurringDtcs = emptyList(),
            completedTests = emptyList(),
            communityEvidence = false,
            sourceTier = "A_OWNED_CREATED"
        )
        val ranked = PriorityEngine().rank(
            profile = profile,
            rankedCauses = listOf(
                "cause_battery_ground" to 0.32,
                "cause_relay"         to 0.24,
                "cause_fuse_feed"     to 0.18,
                "cause_connector"     to 0.12,
                "cause_pump_motor"    to 0.10,
                "cause_pcm_driver"    to 0.04
            ),
            ctx = ctx
        )
        // Confidence is low because no scanner.
        assertTrue("Confidence should be low without scanner, got ${ranked.first().confidence}",
            ranked.first().confidence < 0.6)
        // Battery ground is still first.
        assertEquals("cause_battery_ground", ranked.first().causeId)
    }

    private fun loadAsset(filename: String): String {
        // Read from the assets directory in the project tree for unit tests.
        val path = "../app/src/main/assets/knowledge/packs/$filename"
        val f = java.io.File(path)
        require(f.exists()) { "Asset not found at $path" }
        return f.readText()
    }
}
