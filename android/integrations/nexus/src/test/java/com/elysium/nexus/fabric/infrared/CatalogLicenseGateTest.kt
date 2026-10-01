package com.elysium.nexus.fabric.infrared

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import com.elysium.nexus.fabric.infrared.database.PackagedCatalogTestAsset

class CatalogLicenseGateTest {

    @Test
    fun packagedCatalogIncludesLicenseEvidence() {
        val json = JSONObject(PackagedCatalogTestAsset.manifest.readText())
        assertTrue(json.getString("sourceLockSha256").matches(Regex("[0-9a-f]{64}")))
        assertTrue(json.getString("licenseManifestSha256").matches(Regex("[0-9a-f]{64}")))
        assertTrue(File("src/main/assets/ir/THIRD_PARTY_IR_DATA_NOTICES.md").length() > 0L)
    }

    @Test
    fun productionManifest_containsValidSha256AndCounts() {
        val manifestFile = PackagedCatalogTestAsset.manifest
        assertTrue("Manifest must exist at ${manifestFile.absolutePath}", manifestFile.exists())

        val json = JSONObject(manifestFile.readText())
        val sha256 = json.getString("databaseSha256")
        val profile = json.getString("profile")

        assertEquals("production", profile)
        assertEquals(64, sha256.length)
        assertNotNull(json.getJSONObject("counts"))
    }
}
