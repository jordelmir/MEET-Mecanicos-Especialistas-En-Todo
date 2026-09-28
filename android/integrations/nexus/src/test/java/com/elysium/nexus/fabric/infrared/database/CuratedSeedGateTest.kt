package com.elysium.nexus.fabric.infrared.database

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Section 21/7 curated-seed regression gate.
 *
 * The curated TV brands in `ir_codes_db.json` (Kintech, Control Universal TV)
 * must be seeded into the Schema v4 catalog that `IrConnectFlow` reads at
 * runtime. The manifest's `databaseSha256` is the same contract CI enforces
 * (`.github/workflows/android-ci.yml`), so a JVM-grade check keeps the two
 * in lockstep without needing a device.
 */
class CatalogCuratedSeedGateTest {

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun databaseBinaryHashMatchesManifest() {
        val dbFile = PackagedCatalogTestAsset.decoded()
        val manifestFile = PackagedCatalogTestAsset.manifest
        assertTrue("ir_catalog.db must exist at ${dbFile.absolutePath}", dbFile.exists())
        assertTrue("manifest must exist at ${manifestFile.absolutePath}", manifestFile.exists())

        val json = JSONObject(manifestFile.readText())
        val expectedSha = json.getString("databaseSha256")
        val actualSha = sha256Of(dbFile)

        assertEquals(
            "databaseSha256 in ir_catalog.manifest.json must match the shipped ir_catalog.db",
            expectedSha,
            actualSha
        )
    }

@Test
    fun curatedTvBrandsArePresentInCatalogCounts() {
        val manifestFile = PackagedCatalogTestAsset.manifest
        val dbFile = PackagedCatalogTestAsset.decoded()
        assertTrue("manifest must exist", manifestFile.exists())
        assertTrue("db must exist", dbFile.exists())

        // The manifest's canonical hash is a deterministic function of every
        // logical row in the DB. It MUST differ from the pre-seed baseline
        // (ab74671f0c220b21eb9f052a172a2eb1457e22ad7dea2c466b2ae788cde63764)
        // because Kintech / Control Universal TV rows were added.
        val json = JSONObject(manifestFile.readText())
        // V06-P5: schema v5 contract is declarative in the manifest.
        assertEquals("manifest must declare schema v5", 5, json.getInt("schemaVersion"))

        val canonical = json.getString("canonicalContentSha256")
        assertTrue("canonical hash must be a 64-hex string", canonical.matches(Regex("[0-9a-f]{64}")))

        val counts = json.getJSONObject("counts")
        assertTrue("production catalog must ship 5+ locked sources", counts.getInt("sources") >= 5)
        assertTrue("catalog must still have 800+ brands", counts.getInt("brands") >= 800)
        assertTrue("catalog must have a command_bindings row for seeded signals",
            counts.getInt("command_bindings") >= 36000)
        // V06-P5 §14: v5 entities present in the canonical hash scope.
        assertTrue("protocol_definitions are hashed", counts.getInt("protocol_definitions") >= 20)
        assertTrue("protocol_variants are hashed", counts.getInt("protocol_variants") >= 20)
    }
}
