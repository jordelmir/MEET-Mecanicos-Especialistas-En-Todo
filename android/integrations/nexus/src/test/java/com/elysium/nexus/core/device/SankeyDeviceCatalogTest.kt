package com.elysium.nexus.core.device

import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.IrWaveform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SankeyDeviceCatalogTest {

    @Test
    fun testSankeyTvTemplatesExist() {
        val generic = DeviceCatalog.byId("tv-sankey-generic")
        assertNotNull("tv-sankey-generic should exist in catalog", generic)
        assertEquals("Sankey", generic?.brand)
        assertEquals(DeviceCategory.TV, generic?.category)
        assertEquals(IrProtocol.Nec, generic?.protocol)

        val smart = DeviceCatalog.byId("tv-sankey-smart")
        assertNotNull("tv-sankey-smart should exist in catalog", smart)
        assertEquals("Sankey", smart?.brand)

        val uhd = DeviceCatalog.byId("tv-sankey-uhd")
        assertNotNull("tv-sankey-uhd should exist in catalog", uhd)
        assertEquals("Sankey", uhd?.brand)

        val curved = DeviceCatalog.byId("tv-sankey-curved")
        assertNotNull("tv-sankey-curved should exist in catalog", curved)
        assertEquals("Sankey", curved?.brand)
    }

    @Test
    fun testSankeyAcTemplateExists() {
        val sankeyAc = DeviceCatalog.byId("ac-sankey-generic")
        assertNotNull("ac-sankey-generic should exist in catalog", sankeyAc)
        assertEquals("Sankey", sankeyAc?.brand)
        assertEquals(DeviceCategory.AIR_CONDITIONER, sankeyAc?.category)
        assertEquals(IrProtocol.Nec, sankeyAc?.protocol)
    }

    @Test
    fun testSankeyWaveformEncodingValid() {
        val sankeyTemplates = DeviceCatalog.all.filter { it.brand == "Sankey" }
        assertTrue("Sankey templates count should be at least 5 (4 TVs + 1 AC)", sankeyTemplates.size >= 5)

        for (template in sankeyTemplates) {
            for (button in template.buttons) {
                val waveform = IrWaveform.encodeNec(template.deviceAddress, button.commandCode)
                assertTrue(
                    "Waveform pattern for ${template.id} button ${button.id} should not be empty",
                    waveform.pattern.isNotEmpty()
                )
                // NEC waveform should have at least 36 pulse entries
                assertTrue("NEC pattern size should be at least 36 entries", waveform.pattern.size >= 36)
            }
        }
    }

    @Test
    fun testRegionalBrandsAdded() {
        val kalley = DeviceCatalog.byId("tv-kalley-generic")
        assertNotNull("tv-kalley-generic should exist", kalley)

        val challenger = DeviceCatalog.byId("tv-challenger-generic")
        assertNotNull("tv-challenger-generic should exist", challenger)

        val daewoo = DeviceCatalog.byId("tv-daewoo-generic")
        assertNotNull("tv-daewoo-generic should exist", daewoo)

        val hyundai = DeviceCatalog.byId("tv-hyundai-generic")
        assertNotNull("tv-hyundai-generic should exist", hyundai)
    }
}
