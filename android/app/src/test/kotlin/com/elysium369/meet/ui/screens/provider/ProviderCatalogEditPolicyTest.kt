package com.elysium369.meet.ui.screens.provider

import com.elysium369.meet.provider.domain.models.ProviderDomainCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderCatalogEditPolicyTest {
    @Test
    fun `stored category wins over role and unknown stored category requires explicit choice`() {
        assertEquals(ProviderDomainCategory.PLUMBING_WATER, ProviderCatalogEditPolicy.category("PLUMBING_WATER", "mechanic"))
        assertNull(ProviderCatalogEditPolicy.category("UNKNOWN_NEW_CATEGORY", "mechanic"))
    }

    @Test
    fun `real provider roles map only to their related technical branch`() {
        assertEquals(ProviderDomainCategory.AUTOMOTIVE_MECHANIC, ProviderCatalogEditPolicy.category(null, "WORKSHOP"))
        assertEquals(ProviderDomainCategory.TOW_TRUCK, ProviderCatalogEditPolicy.category(null, "tow_provider"))
        assertEquals(ProviderDomainCategory.LOCKSMITH_SECURITY, ProviderCatalogEditPolicy.category(null, "auto_locksmith"))
        for (role in listOf("service_provider", "parts_store", "ride_driver", "unknown")) {
            assertNull(ProviderCatalogEditPolicy.category(null, role))
        }
    }

    @Test
    fun `invalid financial inputs are rejected instead of silently converted into template prices`() {
        for (invalid in listOf("", "-1", "1.5", "Infinity", "NaN", "9223372036854775808")) {
            assertNull(ProviderCatalogEditPolicy.rates(invalid, "15000", "1200", "90", "5000"))
            assertNull(ProviderCatalogEditPolicy.rates("15000", invalid, "1200", "90", "5000"))
            assertNull(ProviderCatalogEditPolicy.rates("15000", "15000", invalid, "90", "5000"))
        }
        assertNull(ProviderCatalogEditPolicy.rates("0", "0", "0", "2147483648", "0"))
        assertNull(ProviderCatalogEditPolicy.rates("0", "0", "0", "0", "-1"))
    }

    @Test
    fun `zero labor for delivery profiles and deliberate nonnegative warranty are preserved`() {
        assertEquals(ProviderCatalogEditPolicy.Rates(0, 25000, 1500, 7, 0), ProviderCatalogEditPolicy.rates("0", "25000", "1500", "7", "0"))
        assertEquals(ProviderCatalogEditPolicy.Rates(15000, 20000, 1200, 90, 5000), ProviderCatalogEditPolicy.rates(" 15000 ", "20000", "1200", "90", "5000"))
    }
}
