package com.elysium.nexus.fabric.infrared.promotion

import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence
import com.elysium.nexus.fabric.infrared.database.model.RetailerName
import com.elysium.nexus.fabric.infrared.database.model.RetailerSku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityCertificateEngineTest {

    @Test
    fun `issueCertificate returns null when core actions are incomplete`() {
        val sku = RetailerSku(id = "s1", retailer = RetailerName.MONGE_CR, skuCode = "UN55U8000", mpn = "UN55U8000FPXPA", deviceModelId = "mod-1")
        val evidenceList = listOf(
            PhysicalTestEvidence(id = "e1", deviceModelId = "mod-1", actionKey = "POWER_TOGGLE", signalId = "sig1", physicalSha256 = "sha1", measuredCarrierHz = 38000, transmitterHardware = "NexusBridge", receiverHardware = "HIL-1")
        )

        val cert = CompatibilityCertificateEngine.issueCertificate(sku, evidenceList)
        assertNull("Certificate must fail when core actions are incomplete", cert)
    }

    @Test
    fun `issueCertificate returns signed certificate when all core actions are verified`() {
        val sku = RetailerSku(id = "s1", retailer = RetailerName.MONGE_CR, skuCode = "UN55U8000", mpn = "UN55U8000FPXPA", deviceModelId = "mod-1")
        val evidenceList = listOf(
            PhysicalTestEvidence(id = "e1", deviceModelId = "mod-1", actionKey = "POWER_TOGGLE", signalId = "sig1", physicalSha256 = "sha1", measuredCarrierHz = 38000, transmitterHardware = "NexusBridge", receiverHardware = "HIL-1"),
            PhysicalTestEvidence(id = "e2", deviceModelId = "mod-1", actionKey = "VOLUME_UP", signalId = "sig2", physicalSha256 = "sha2", measuredCarrierHz = 38000, transmitterHardware = "NexusBridge", receiverHardware = "HIL-1"),
            PhysicalTestEvidence(id = "e3", deviceModelId = "mod-1", actionKey = "VOLUME_DOWN", signalId = "sig3", physicalSha256 = "sha3", measuredCarrierHz = 38000, transmitterHardware = "NexusBridge", receiverHardware = "HIL-1"),
            PhysicalTestEvidence(id = "e4", deviceModelId = "mod-1", actionKey = "MUTE", signalId = "sig4", physicalSha256 = "sha4", measuredCarrierHz = 38000, transmitterHardware = "NexusBridge", receiverHardware = "HIL-1"),
            PhysicalTestEvidence(id = "e5", deviceModelId = "mod-1", actionKey = "INPUT_SELECT", signalId = "sig5", physicalSha256 = "sha5", measuredCarrierHz = 38000, transmitterHardware = "NexusBridge", receiverHardware = "HIL-1")
        )

        val cert = CompatibilityCertificateEngine.issueCertificate(sku, evidenceList)
        assertNotNull(cert)
        assertEquals("CERT-MONGE_CR-UN55U8000", cert!!.certificateId)
        assertTrue(CompatibilityCertificateEngine.verifyCertificate(cert))
    }
}
