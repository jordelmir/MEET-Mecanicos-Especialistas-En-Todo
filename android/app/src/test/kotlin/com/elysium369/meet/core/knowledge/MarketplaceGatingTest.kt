package com.elysium369.meet.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceGatingTest {

    @Test
    fun `offer is blocked when no tests completed`() {
        val offer = MarketplaceGating.pumpOfferExample("P0230", "Hyundai Accent 2005")
        val result = MarketplaceGating().gate(listOf(offer), emptyList())
        assertTrue(result.blockedOffers.contains(offer))
        assertFalse(result.offers.contains(offer))
    }

    @Test
    fun `offer is blocked when only some tests completed`() {
        val offer = MarketplaceGating.pumpOfferExample("P0230", "Hyundai Accent 2005")
        val result = MarketplaceGating().gate(
            listOf(offer),
            listOf("battery_check", "fuse_check")
        )
        assertTrue(result.blockedOffers.contains(offer))
    }

    @Test
    fun `offer is ready when ALL required tests completed`() {
        val offer = MarketplaceGating.pumpOfferExample("P0230", "Hyundai Accent 2005")
        val result = MarketplaceGating().gate(
            listOf(offer),
            offer.requiredTests
        )
        assertTrue(result.offers.contains(offer))
        assertFalse(result.blockedOffers.contains(offer))
    }

    @Test
    fun `disclaimer is shown`() {
        val offer = MarketplaceGating.pumpOfferExample("P0230", "Hyundai Accent 2005")
        val result = MarketplaceGating().gate(listOf(offer), emptyList())
        assertTrue(result.disclaimer.contains("Compra recomendada"))
    }

    @Test
    fun `multiple offers mix blocked and ready`() {
        val pump = MarketplaceGating.pumpOfferExample("P0230", "Hyundai Accent 2005")
        val relay = MarketplaceOffer(
            partName = "Relay de bomba",
            associatedCause = "Relay defectuoso",
            associatedComponent = "fuel_pump_relay",
            requiredTests = listOf("relay_check", "fuse_check"),
            estimatedCostRange = "$10-$35 USD"
        )
        val result = MarketplaceGating().gate(
            listOf(pump, relay),
            listOf("relay_check", "fuse_check")  // pump not ready, relay ready
        )
        assertTrue(result.offers.contains(relay))
        assertTrue(result.blockedOffers.contains(pump))
        assertEquals(1, result.offers.size)
        assertEquals(1, result.blockedOffers.size)
    }

    @Test
    fun `allBlockedReason lists pending tests for blocked offers`() {
        val offer = MarketplaceGating.pumpOfferExample("P0230", "Hyundai Accent 2005")
        val result = MarketplaceGating().gate(listOf(offer), emptyList())
        val reason = result.allBlockedReason()
        assertTrue(reason.contains("Bomba de combustible"))
        assertTrue(reason.contains("battery_check"))
    }
}
