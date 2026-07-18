package com.elysium369.meet.visual3d

import com.elysium369.meet.visual3d.domain.GenericInlineFourAssetContract
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericInlineFourAssetTest {
    private fun asset(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
        File("android/app/src/main/assets/$path")
    ).firstOrNull(File::isFile) ?: error("Missing generic engine asset: $path")

    @Test
    fun `generic inline four is a bounded traceable GLB 2 asset`() {
        val model = asset(GenericInlineFourAssetContract.ASSET_PATH)
        val bytes = model.readBytes()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("glTF", bytes.copyOfRange(0, 4).decodeToString())
        assertEquals(2, header.getInt(4))
        assertEquals(model.length(), header.getInt(8).toLong())
        assertTrue("Detailed engine asset should contain real mesh data", model.length() > 250_000L)
        assertTrue("Core engine GLB must remain mobile-sized", model.length() < 8_000_000L)

        val jsonLength = header.getInt(12)
        assertEquals(0x4E4F534A, header.getInt(16))
        val gltfJson = bytes.copyOfRange(20, 20 + jsonLength).decodeToString()
        GenericInlineFourAssetContract.requiredMeshKeys.forEach { meshKey ->
            assertTrue(
                "Missing stable renderable node family: $meshKey",
                gltfJson.contains("asset_mesh__${meshKey}__")
            )
        }

        val manifest = asset(GenericInlineFourAssetContract.MANIFEST_PATH).readText()
        val expectedHash = Regex("\\\"sha256\\\"\\s*:\\s*\\\"([a-f0-9]{64})\\\"")
            .find(manifest)?.groupValues?.get(1)
            ?: error("Asset manifest is missing sha256")
        val actualHash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        assertEquals(expectedHash, actualHash)
        assertTrue(manifest.contains("L2_GENERIC_ASSEMBLY"))
        assertTrue(manifest.contains("ILLUSTRATIVE_PROPORTIONS_ONLY"))
        assertTrue(manifest.contains("\"oemClaim\": false"))
        assertTrue(manifest.contains("\"vehicleSpecificClaim\": false"))
    }
}
