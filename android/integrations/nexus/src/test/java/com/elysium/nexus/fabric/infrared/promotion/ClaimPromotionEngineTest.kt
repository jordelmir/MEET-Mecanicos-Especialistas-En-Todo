package com.elysium.nexus.fabric.infrared.promotion

import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence
import com.elysium.nexus.fabric.infrared.database.model.RetailerName
import com.elysium.nexus.fabric.infrared.database.model.RetailerSku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaimPromotionEngineTest {

    @Test
    fun `deriveClaimStatus returns STRUCTURAL_VALID when no evidence exists`() {
        val status = ClaimPromotionEngine.deriveClaimStatus(emptyList())
        assertEquals(ClaimPromotionEngine.DerivedClaimStatus.STRUCTURAL_VALID, status)
    }

    @Test
    fun `deriveClaimStatus returns HIL_VERIFIED when HIL evidence exists`() {
        val evidence = listOf(
            PhysicalTestEvidence(
                id = "ev-1",
                deviceModelId = "mod-samsung-1",
                actionKey = "VOLUME_UP",
                signalId = "sig-100",
                physicalSha256 = "sha256-dummy",
                measuredCarrierHz = 38000,
                transmitterHardware = "NexusBridge",
                receiverHardware = "HIL-Station-1",
                status = "HIL_VERIFIED"
            )
        )
        val status = ClaimPromotionEngine.deriveClaimStatus(evidence)
        assertEquals(ClaimPromotionEngine.DerivedClaimStatus.HIL_VERIFIED, status)
    }

    @Test
    fun `computeRetailCoverage returns false for 100 percent when pending models exist`() {
        val skus = listOf(
            RetailerSku(id = "sku-1", retailer = RetailerName.MONGE_CR, skuCode = "UN55U8000", mpn = "UN55U8000FPXPA", deviceModelId = "mod-1"),
            RetailerSku(id = "sku-2", retailer = RetailerName.MONGE_CR, skuCode = "AW55B4Q", mpn = "AW55B4Q", deviceModelId = "mod-2")
        )
        val evidenceMap = mapOf(
            "mod-1" to listOf(
                PhysicalTestEvidence(
                    id = "ev-1", deviceModelId = "mod-1", actionKey = "VOLUME_UP", signalId = "s1",
                    physicalSha256 = "sha1", measuredCarrierHz = 38000, transmitterHardware = "NexusBridge", receiverHardware = "HIL-1", status = "HIL_VERIFIED"
                )
            )
            // mod-2 has no evidence
        )

        val result = ClaimPromotionEngine.computeRetailCoverage("MONGE_CR", skus, evidenceMap)
        assertEquals(2, result.activeSkuCount)
        assertEquals(1, result.coreVerifiedCount)
        assertEquals(1, result.pendingCount)
        assertEquals(50.0, result.coveragePercentage, 0.01)
        assertFalse(result.is100PercentCoreVerified)
    }

    @Test
    fun `computeRetailCoverage returns true for 100 percent when all active SKUs are HIL verified`() {
        val skus = listOf(
            RetailerSku(id = "sku-1", retailer = RetailerName.MONGE_CR, skuCode = "UN55U8000", mpn = "UN55U8000FPXPA", deviceModelId = "mod-1")
        )
        val evidenceMap = mapOf(
            "mod-1" to listOf(
                PhysicalTestEvidence(
                    id = "ev-1", deviceModelId = "mod-1", actionKey = "VOLUME_UP", signalId = "s1",
                    physicalSha256 = "sha1", measuredCarrierHz = 38000, transmitterHardware = "NexusBridge", receiverHardware = "HIL-1", status = "HIL_VERIFIED"
                )
            )
        )

        val result = ClaimPromotionEngine.computeRetailCoverage("MONGE_CR", skus, evidenceMap)
        assertEquals(1, result.activeSkuCount)
        assertEquals(1, result.coreVerifiedCount)
        assertEquals(0, result.pendingCount)
        assertEquals(100.0, result.coveragePercentage, 0.01)
        assertTrue(result.is100PercentCoreVerified)
    }
}
