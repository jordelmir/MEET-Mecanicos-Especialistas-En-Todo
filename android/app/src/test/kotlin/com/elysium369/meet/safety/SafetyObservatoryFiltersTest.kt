package com.elysium369.meet.safety

import com.elysium369.meet.safety.data.SafetyObservatoryFilters
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class SafetyObservatoryFiltersTest {
    @Test fun emptyFiltersAreAbsentInsteadOfInventedDefaults() {
        assertTrue(SafetyObservatoryFilters().parameters().isEmpty())
    }
    @Test fun publicGeographyUsesNamedRpcParameters() {
        val params = SafetyObservatoryFilters(country = " CR ", admin1 = "SJ").parameters()
        assertEquals("CR", params["p_country_code"]?.jsonPrimitive?.content)
        assertEquals("SJ", params["p_admin1_code"]?.jsonPrimitive?.content)
        assertFalse(params.containsKey("latitude"))
    }
    @Test(expected = IllegalArgumentException::class) fun invertedRangeIsRejected() {
        SafetyObservatoryFilters(from = "2026-09-19T00:00:00Z", to = "2026-09-18T00:00:00Z").parameters()
    }
}
