package com.elysium369.meet.safety

import com.elysium369.meet.safety.crypto.SafetyPayloadAad
import org.junit.Assert.*
import org.junit.Test

class SafetyPayloadCipherTest {

    @Test
    fun `Report AAD differs from evidence AAD for same IDs`() {
        val reportAad = SafetyPayloadAad.report("principal1", "item1", "payload1")
        val evidenceAad = SafetyPayloadAad.evidence("principal1", "item1", "payload1")
        assertNotEquals(reportAad.contentToString(), evidenceAad.contentToString())
    }

    @Test
    fun `Report AAD contains 'safety-report'`() {
        val aad = SafetyPayloadAad.report("principal1", "item1", "payload1")
        val stringAad = String(aad)
        assertTrue(stringAad.contains("safety-report"))
    }

    @Test
    fun `Evidence AAD contains 'safety-evidence'`() {
        val aad = SafetyPayloadAad.evidence("principal1", "item1", "payload1")
        val stringAad = String(aad)
        assertTrue(stringAad.contains("safety-evidence"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Blank principalId throws for report`() {
        SafetyPayloadAad.report(" ", "item1", "payload1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Blank reportId throws for report`() {
        SafetyPayloadAad.report("principal1", "", "payload1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Blank evidenceId throws for evidence`() {
        SafetyPayloadAad.evidence("principal1", "  ", "payload1")
    }

    @Test
    fun `Same inputs produce identical AAD deterministically`() {
        val aad1 = SafetyPayloadAad.report("principal1", "item1", "payload1")
        val aad2 = SafetyPayloadAad.report("principal1", "item1", "payload1")
        assertArrayEquals(aad1, aad2)
    }
}
