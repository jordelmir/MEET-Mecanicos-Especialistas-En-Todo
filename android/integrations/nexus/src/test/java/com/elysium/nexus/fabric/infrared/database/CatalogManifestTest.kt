package com.elysium.nexus.fabric.infrared.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V06-PTG-01 §2/§9 + V06.1 Phase 0.1 — Catalog manifest identity tests.
 *
 * The packaged manifest is the single authority for catalog identity. Phase
 * 0.1 (audit P0-1) replaced the proprietary JSON parser with `org.json` so the
 * REAL packaged manifest — which carries nested objects (`counts`) and the
 * `policyVersion` field — parsers to [CatalogManifest.CatalogMetadata].
 *
 * The mandatory test: read the EXACT packaged asset
 * `src/main/assets/ir/ir_catalog.manifest.json` → parse → Success.
 * No simplified fixture.
 *
 * Fail-closed contract pinned here:
 * - any missing/blank/wrong-typed required field → Failure (never partial install)
 * - nested objects (counts/stats) do not break parsing
 * - non-JSON input → Failure
 */
class CatalogManifestTest {

    /** Exact packaged asset, read from disk (unit tests run with cwd = :app module). */
    private val packagedManifestText: String by lazy {
        val asset = File("src/main/assets/ir/ir_catalog.manifest.json")
        assertTrue("packaged manifest must exist at $asset", asset.exists())
        asset.readText(Charsets.UTF_8)
    }

    private fun validManifest(policyVersion: String = "v0.6-ptg-1") = """
        {
          "catalogBuildId": "ptg-v1|5|e0bfbea28910399842e4ab0744b451f791064b6dead204bfdb1dc0f3b642a36b|aabbccdd",
          "schemaVersion": 5,
          "profile": "production",
          "databaseSha256": "00732dda3eb3bb7fd717b7fc93f503cac33426d2bedf288e83e67afdd98a09d5",
          "canonicalContentSha256": "e0bfbea28910399842e4ab0744b451f791064b6dead204bfdb1dc0f3b642a36b",
          "sourceLockSha256": "11aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          "rejectionManifestSha256": "22bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
          "licenseManifestSha256": "33cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
          "policyVersion": "$policyVersion",
          "counts": { "signals": 85392, "code_sets": 2350 }
        }
    """.trimIndent()

    @Test
    fun `parses the EXACT packaged manifest asset - Phase 0_1 mandate`() {
        val result = CatalogManifest.parse(packagedManifestText)
        assertTrue("expected Success on packaged manifest, got $result", result is CatalogManifest.ParseResult.Success)
        val metadata = (result as CatalogManifest.ParseResult.Success).metadata
        assertTrue("buildId must be non-blank", metadata.catalogBuildId.length >= 60)
        assertEquals(5, metadata.schemaVersion)
        assertTrue("hashes must be full sha256 (64 chars)", metadata.databaseSha256.length == 64)
        assertTrue("policyVersion must parse from packaged manifest", metadata.policyVersion.isNotEmpty())
        assertTrue("packaged manifest must pass the install gate", CatalogManifest.isSchemaVersionAccepted(metadata.schemaVersion))
    }

    @Test
    fun `parses complete manifest with nested counts object`() {
        val result = CatalogManifest.parse(validManifest())
        assertTrue("expected Success, got $result", result is CatalogManifest.ParseResult.Success)
        val metadata = (result as CatalogManifest.ParseResult.Success).metadata
        assertEquals("ptg-v1|5|e0bfbea28910399842e4ab0744b451f791064b6dead204bfdb1dc0f3b642a36b|aabbccdd", metadata.catalogBuildId)
        assertEquals(5, metadata.schemaVersion)
        assertEquals("00732dda3eb3bb7fd717b7fc93f503cac33426d2bedf288e83e67afdd98a09d5", metadata.databaseSha256)
        assertEquals("e0bfbea28910399842e4ab0744b451f791064b6dead204bfdb1dc0f3b642a36b", metadata.canonicalContentSha256)
        assertEquals("11aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", metadata.sourceLockSha256)
        assertEquals("22bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", metadata.rejectionManifestSha256)
        assertEquals("33cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", metadata.licenseManifestSha256)
        assertEquals("v0.6-ptg-1", metadata.policyVersion)
    }

    @Test
    fun `rejects manifest missing catalogBuildId`() {
        val text = validManifest().replace("\"catalogBuildId\": \"ptg-v1|5|e0bfbea28910399842e4ab0744b451f791064b6dead204bfdb1dc0f3b642a36b|aabbccdd\",", "")
        val result = CatalogManifest.parse(text)
        assertTrue("expected Failure, got $result", result is CatalogManifest.ParseResult.Failure)
        assertTrue((result as CatalogManifest.ParseResult.Failure).reason.contains("catalogBuildId"))
    }

    @Test
    fun `rejects manifest missing databaseSha256`() {
        val text = validManifest().replace("\"databaseSha256\": \"00732dda3eb3bb7fd717b7fc93f503cac33426d2bedf288e83e67afdd98a09d5\",", "")
        val result = CatalogManifest.parse(text)
        assertTrue("expected Failure, got $result", result is CatalogManifest.ParseResult.Failure)
        assertTrue((result as CatalogManifest.ParseResult.Failure).reason.contains("databaseSha256"))
    }

    @Test
    fun `rejects manifest missing policyVersion - Phase 0_1`() {
        val text = validManifest().replace("\"policyVersion\": \"v0.6-ptg-1\",", "")
        val result = CatalogManifest.parse(text)
        assertTrue("expected Failure, got $result", result is CatalogManifest.ParseResult.Failure)
        assertTrue((result as CatalogManifest.ParseResult.Failure).reason.contains("policyVersion"))
    }

    @Test
    fun `rejects blank sourceLockSha256`() {
        val text = validManifest().replace("\"sourceLockSha256\": \"11aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",", "\"sourceLockSha256\": \"\",")
        val result = CatalogManifest.parse(text)
        assertTrue("expected Failure, got $result", result is CatalogManifest.ParseResult.Failure)
        assertTrue((result as CatalogManifest.ParseResult.Failure).reason.contains("sourceLockSha256"))
    }

    @Test
    fun `rejects wrong-typed schemaVersion`() {
        val text = validManifest().replace("\"schemaVersion\": 5,", "\"schemaVersion\": \"five\",")
        val result = CatalogManifest.parse(text)
        assertTrue("expected Failure, got $result", result is CatalogManifest.ParseResult.Failure)
        assertTrue((result as CatalogManifest.ParseResult.Failure).reason.contains("schemaVersion"))
    }

    @Test
    fun `rejects schema version 4 manifest at install gate`() {
        val text = validManifest().replace("\"schemaVersion\": 5,", "\"schemaVersion\": 4,")
        val result = CatalogManifest.parse(text)
        assertTrue("expected Success (parse-level), got $result", result is CatalogManifest.ParseResult.Success)
        val metadata = (result as CatalogManifest.ParseResult.Success).metadata
        assertEquals(4, metadata.schemaVersion)
        assertTrue("v4 must fail the install gate", !CatalogManifest.isSchemaVersionAccepted(metadata.schemaVersion))
    }

    @Test
    fun `rejects non JSON input`() {
        val result = CatalogManifest.parse("this is not json")
        assertTrue("expected Failure, got $result", result is CatalogManifest.ParseResult.Failure)
    }

    @Test
    fun `rejects empty input`() {
        val result = CatalogManifest.parse("")
        assertTrue("expected Failure, got $result", result is CatalogManifest.ParseResult.Failure)
    }

    @Test
    fun `escaped strings decode without crashing on incomplete manifests`() {
        val text = """{"catalogBuildId": "ptg\u002Dv1", "schemaVersion": 5}"""
        val result = CatalogManifest.parse(text)
        assertTrue("expected Failure for incomplete manifest, got $result", result is CatalogManifest.ParseResult.Failure)
    }
}