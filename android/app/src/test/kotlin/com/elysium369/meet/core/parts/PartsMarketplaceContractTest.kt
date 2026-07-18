package com.elysium369.meet.core.parts

import org.junit.Assert.assertEquals
import org.junit.Test

class PartsMarketplaceContractTest {

    @Test
    fun `request v2 statuses normalize to Android legacy statuses`() {
        assertEquals("OPEN", PartsMarketplaceContract.requestStatusToLegacy("RECEIVING_QUOTES"))
        assertEquals("ACCEPTED", PartsMarketplaceContract.requestStatusToLegacy("QUOTE_ACCEPTED"))
        assertEquals("ACCEPTED", PartsMarketplaceContract.requestStatusToLegacy("READY_FOR_PICKUP"))
        assertEquals("DELIVERED", PartsMarketplaceContract.requestStatusToLegacy("DELIVERED"))
        assertEquals("CANCELLED", PartsMarketplaceContract.requestStatusToLegacy("DISPUTED"))
    }

    @Test
    fun `quote v2 statuses normalize to Android offer statuses`() {
        assertEquals("PENDING", PartsMarketplaceContract.quoteStatusToLegacy("SENT"))
        assertEquals("ACCEPTED", PartsMarketplaceContract.quoteStatusToLegacy("ACCEPTED"))
        assertEquals("REJECTED", PartsMarketplaceContract.quoteStatusToLegacy("EXPIRED"))
        assertEquals("REJECTED", PartsMarketplaceContract.quoteStatusToLegacy("CANCELLED"))
    }

    @Test
    fun `web part positions normalize to existing APK positions`() {
        assertEquals("DELANTERA_DERECHA", PartsMarketplaceContract.positionToLegacy("FRONT_RIGHT"))
        assertEquals("TRASERA_IZQUIERDA", PartsMarketplaceContract.positionToLegacy("REAR_LEFT"))
        assertEquals("CENTRAL", PartsMarketplaceContract.positionToLegacy("FUSE_BOX"))
        assertEquals("N/A", PartsMarketplaceContract.positionToLegacy("NOT_APPLICABLE"))
    }

    @Test
    fun `web preference and condition enums normalize to current APK values`() {
        assertEquals("OEM", PartsMarketplaceContract.preferenceToLegacy("OEM"))
        assertEquals("AFTERMARKET", PartsMarketplaceContract.preferenceToLegacy("AFTERMARKET"))
        assertEquals("ANY", PartsMarketplaceContract.preferenceToLegacy("REFURBISHED"))
        assertEquals("OEM", PartsMarketplaceContract.conditionToLegacy("NEW_OEM"))
        assertEquals("NEW", PartsMarketplaceContract.conditionToLegacy("NEW_AFTERMARKET"))
        assertEquals("USED_TESTED", PartsMarketplaceContract.conditionToLegacy("USED"))
        assertEquals("REMAN", PartsMarketplaceContract.conditionToLegacy("REBUILT"))
    }

    @Test
    fun `Android legacy values export as v2 marketplace enums`() {
        assertEquals("QUOTE_ACCEPTED", PartsMarketplaceContract.requestStatusToV2("ACCEPTED"))
        assertEquals("SENT", PartsMarketplaceContract.quoteStatusToV2("PENDING"))
        assertEquals("CENTER", PartsMarketplaceContract.positionToV2("CENTRAL"))
        assertEquals("FRONT_LEFT", PartsMarketplaceContract.positionToV2("DELANTERA_IZQUIERDA"))
        assertEquals("REFURBISHED", PartsMarketplaceContract.conditionToV2("REMAN"))
        assertEquals("NEW_AFTERMARKET", PartsMarketplaceContract.conditionToV2("NEW"))
    }
}
