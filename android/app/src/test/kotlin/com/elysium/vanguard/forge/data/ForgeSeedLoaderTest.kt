package com.elysium.vanguard.forge.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeSeedLoaderTest {

    // Tests usan Context = null (parseBundleText no necesita Context).
    private val loader = ForgeSeedLoader(context = null, maxBytes = 1_000_000)

    @Test
    fun `parseBundleText parses valid bundle`() {
        val text = """
        {
          "bundleId": "test",
          "schemaVersion": 1,
          "documents": []
        }
        """.trimIndent()
        val result = runBlocking { loader.parseBundleText(text) }
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `parseBundleText handles malformed JSON safely`() {
        val result = runBlocking { loader.parseBundleText("{ invalid json ") }
        assertTrue(result.isFailure)
    }

    @Test
    fun `parseBundleText rejects empty bundle`() {
        val result = runBlocking { loader.parseBundleText("") }
        assertTrue(result.isFailure)
    }

    @Test
    fun `parseBundleText rejects oversized bundle`() {
        val huge = "x".repeat(2_000_000)
        val result = runBlocking { loader.parseBundleText(huge) }
        assertTrue(result.isFailure)
    }
}