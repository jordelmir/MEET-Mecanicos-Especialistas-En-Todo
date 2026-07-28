package com.elysium369.meet.core.catalog

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PrincipalRepairSourceContractTest {
    private fun asset(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
        File("android/app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: error("Missing asset $path")

    @Test
    fun `principal database extraction preserves source provenance and full block count`() {
        val extraction = Json { ignoreUnknownKeys = true }
            .decodeFromString<PrincipalRepairExtraction>(
                asset(PRINCIPAL_REPAIR_SOURCE_ASSET).readText(),
            )

        assertEquals(PRINCIPAL_REPAIR_SOURCE_SHA256, extraction.document.sourceSha256)
        assertEquals(PRINCIPAL_REPAIR_SOURCE_BLOCK_COUNT, extraction.blocks.size)
        assertEquals(
            extraction.blocks.size,
            extraction.blocks.map { it.blockId }.distinct().size,
        )
        extraction.blocks.take(250).forEach { block ->
            assertEquals(block.textHash, block.text.sha256())
        }
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }
}
