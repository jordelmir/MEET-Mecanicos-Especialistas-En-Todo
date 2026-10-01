package com.elysium.nexus.fabric.infrared.gate

import com.elysium.nexus.fabric.infrared.CodecVerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetailReleaseGateTest {

    @Test
    fun `checkBrandQuery fails when deviceType is blank`() {
        val result = RetailReleaseGate.checkBrandQuery("")
        assertTrue(result is RetailReleaseGate.GateResult.Fail)
        assertEquals("ERR_GATE_BRAND_QUERY_UNFILTERED", (result as RetailReleaseGate.GateResult.Fail).code)
    }

    @Test
    fun `checkBrandQuery passes when deviceType is TV`() {
        val result = RetailReleaseGate.checkBrandQuery("TV")
        assertTrue(result is RetailReleaseGate.GateResult.Pass)
    }

    @Test
    fun `checkCodecEligibility blocks experimental codecs`() {
        val result = RetailReleaseGate.checkCodecEligibility(CodecVerificationStatus.EXPERIMENTAL)
        assertTrue(result is RetailReleaseGate.GateResult.Fail)
        assertEquals("ERR_GATE_CODEC_EXPERIMENTAL_BLOCKED", (result as RetailReleaseGate.GateResult.Fail).code)
    }

    @Test
    fun `checkCodecEligibility passes unit shape validated codecs`() {
        val result = RetailReleaseGate.checkCodecEligibility(CodecVerificationStatus.UNIT_SHAPE_VALIDATED)
        assertTrue(result is RetailReleaseGate.GateResult.Pass)
    }

    @Test
    fun `checkCommercialClaimEligibility blocks zero physical evidence`() {
        val result = RetailReleaseGate.checkCommercialClaimEligibility(0)
        assertTrue(result is RetailReleaseGate.GateResult.Fail)
        assertEquals("ERR_GATE_ZERO_PHYSICAL_EVIDENCE", (result as RetailReleaseGate.GateResult.Fail).code)
    }

    @Test
    fun `checkReleaseSigningCredentials blocks hardcoded password`() {
        val result = RetailReleaseGate.checkReleaseSigningCredentials("Elysium2026!", "Elysium2026!")
        assertTrue(result is RetailReleaseGate.GateResult.Fail)
        assertEquals("ERR_GATE_HARDCODED_RELEASE_PASSWORD", (result as RetailReleaseGate.GateResult.Fail).code)
    }

    @Test
    fun `checkReleaseSigningCredentials passes clean env credentials`() {
        val result = RetailReleaseGate.checkReleaseSigningCredentials("secure_store_pass_9921", "secure_key_pass_7731")
        assertTrue(result is RetailReleaseGate.GateResult.Pass)
    }
}
