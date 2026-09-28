package com.elysium.nexus.core.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceLevelTest {

    @Test
    fun `tier ordering is correct`() {
        assertTrue(EvidenceLevel.INTERNAL_UNVERIFIED.tier < EvidenceLevel.MODEL_INFERRED.tier)
        assertTrue(EvidenceLevel.MODEL_INFERRED.tier < EvidenceLevel.WIFI_IDENTITY_MATCHED.tier)
        assertTrue(EvidenceLevel.WIFI_IDENTITY_MATCHED.tier < EvidenceLevel.SESSION_VERIFIED.tier)
        assertTrue(EvidenceLevel.SESSION_VERIFIED.tier < EvidenceLevel.TV_COMPANION_VERIFIED.tier)
        assertTrue(EvidenceLevel.TV_COMPANION_VERIFIED.tier < EvidenceLevel.WIFI_ORACLE_VERIFIED.tier)
        assertTrue(EvidenceLevel.WIFI_ORACLE_VERIFIED.tier < EvidenceLevel.EXTERNAL_HIL_VERIFIED.tier)
        assertTrue(EvidenceLevel.EXTERNAL_HIL_VERIFIED.tier < EvidenceLevel.LAB_MATRIX_VERIFIED.tier)
        assertTrue(EvidenceLevel.LAB_MATRIX_VERIFIED.tier < EvidenceLevel.PRODUCTION_APPROVED.tier)
    }

    @Test
    fun `isAtLeast returns true for same or higher level`() {
        assertTrue(EvidenceLevel.SESSION_VERIFIED.isAtLeast(EvidenceLevel.SESSION_VERIFIED))
        assertTrue(EvidenceLevel.LAB_MATRIX_VERIFIED.isAtLeast(EvidenceLevel.SESSION_VERIFIED))
    }

    @Test
    fun `isAtLeast returns false for lower level`() {
        assertFalse(EvidenceLevel.INTERNAL_UNVERIFIED.isAtLeast(EvidenceLevel.SESSION_VERIFIED))
    }

    @Test
    fun `canPromoteTo returns true for next tier`() {
        assertTrue(EvidenceLevel.INTERNAL_UNVERIFIED.canPromoteTo(EvidenceLevel.MODEL_INFERRED))
        assertTrue(EvidenceLevel.SESSION_VERIFIED.canPromoteTo(EvidenceLevel.TV_COMPANION_VERIFIED))
    }

    @Test
    fun `canPromoteTo returns false for same tier`() {
        assertFalse(EvidenceLevel.SESSION_VERIFIED.canPromoteTo(EvidenceLevel.SESSION_VERIFIED))
    }

    @Test
    fun `canPromoteTo returns false for two tiers ahead`() {
        assertFalse(EvidenceLevel.INTERNAL_UNVERIFIED.canPromoteTo(EvidenceLevel.WIFI_IDENTITY_MATCHED))
    }

    @Test
    fun `PRODUCTION_MINIMUM is EXTERNAL_HIL_VERIFIED`() {
        assertEquals(EvidenceLevel.EXTERNAL_HIL_VERIFIED, EvidenceLevel.PRODUCTION_MINIMUM)
    }

    @Test
    fun `IR_PRODUCTION_MINIMUM is SESSION_VERIFIED`() {
        assertEquals(EvidenceLevel.SESSION_VERIFIED, EvidenceLevel.IR_PRODUCTION_MINIMUM)
    }

    @Test
    fun `ASCENDING is ordered by tier`() {
        val ascending = EvidenceLevel.ASCENDING
        assertEquals(9, ascending.size)
        for (i in 1 until ascending.size) {
            assertTrue(ascending[i - 1].tier < ascending[i].tier)
        }
    }

    @Test
    fun `fromTier returns correct level`() {
        assertEquals(EvidenceLevel.WIFI_IDENTITY_MATCHED, EvidenceLevel.fromTier(3))
        assertEquals(EvidenceLevel.PRODUCTION_APPROVED, EvidenceLevel.fromTier(9))
    }

    @Test
    fun `fromTier returns null for invalid tier`() {
        assertNull(EvidenceLevel.fromTier(0))
        assertNull(EvidenceLevel.fromTier(10))
        assertNull(EvidenceLevel.fromTier(-1))
    }

    @Test
    fun `fromDisplayName finds level`() {
        assertEquals(EvidenceLevel.EXTERNAL_HIL_VERIFIED, EvidenceLevel.fromDisplayName("External HIL Verified"))
        assertEquals(EvidenceLevel.LAB_MATRIX_VERIFIED, EvidenceLevel.fromDisplayName("Lab Matrix Verified"))
    }

    @Test
    fun `fromDisplayName is case insensitive`() {
        assertEquals(EvidenceLevel.PRODUCTION_APPROVED, EvidenceLevel.fromDisplayName("production approved"))
    }

    @Test
    fun `fromDisplayName returns null for unknown name`() {
        assertNull(EvidenceLevel.fromDisplayName("Nonexistent Level"))
    }

    @Test
    fun `displayName is non-blank for all levels`() {
        for (level in EvidenceLevel.entries) {
            assertTrue(level.displayName.isNotBlank())
        }
    }

    @Test
    fun `tier is positive for all levels`() {
        for (level in EvidenceLevel.entries) {
            assertTrue(level.tier > 0)
        }
    }
}
