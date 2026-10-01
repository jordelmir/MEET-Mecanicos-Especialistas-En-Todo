package com.elysium.nexus.fabric.infrared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V0.7 Phase 9 — Carrier policy tests.
 *
 * The audit requirement: no blanket global ±2000 Hz carrier fallback.
 * STRICT (commercial default) must fail closed on unsupported carriers;
 * LAB_TOLERANCE may shift within ±2000 Hz and only for lab tooling.
 */
class CarrierPolicyTest {

    @Test
    fun `STRICT uses requested carrier when supported`() {
        val selection = CarrierPolicy.selectCarrier(
            requestedHz = 38_000,
            supportedRanges = listOf(36_000..40_000),
            mode = CarrierPolicyMode.STRICT
        )
        assertEquals(CarrierSelection.Use(38_000), selection)
    }

    @Test
    fun `STRICT fails closed on unsupported carrier - zero silent shift`() {
        val selection = CarrierPolicy.selectCarrier(
            requestedHz = 37_000,
            supportedRanges = listOf(36_000..36_000),
            mode = CarrierPolicyMode.STRICT
        )
        assertTrue(
            "STRICT must never shift the carrier silently",
            selection is CarrierSelection.Unsupported
        )
    }

    @Test
    fun `STRICT fails even within 2kHz when hardware does not support it`() {
        // 1000 Hz away from the only supported carrier — STRICT still rejects.
        val selection = CarrierPolicy.selectCarrier(
            requestedHz = 39_000,
            supportedRanges = listOf(38_000..38_000),
            mode = CarrierPolicyMode.STRICT
        )
        assertTrue(selection is CarrierSelection.Unsupported)
    }

    @Test
    fun `LAB_TOLERANCE shifts to nearest supported carrier within 2kHz`() {
        val selection = CarrierPolicy.selectCarrier(
            requestedHz = 37_000,
            supportedRanges = listOf(36_000..36_000),
            mode = CarrierPolicyMode.LAB_TOLERANCE
        )
        assertEquals(CarrierSelection.Use(36_000), selection)
    }

    @Test
    fun `LAB_TOLERANCE still fails closed beyond 2kHz`() {
        val selection = CarrierPolicy.selectCarrier(
            requestedHz = 33_000,
            supportedRanges = listOf(36_000..36_000),
            mode = CarrierPolicyMode.LAB_TOLERANCE
        )
        assertTrue(
            "LAB_TOLERANCE must never exceed the ±2000 Hz bound",
            selection is CarrierSelection.Unsupported
        )
    }

    @Test
    fun `unknown hardware ranges allow the requested carrier`() {
        val selection = CarrierPolicy.selectCarrier(
            requestedHz = 37_000,
            supportedRanges = emptyList(),
            mode = CarrierPolicyMode.STRICT
        )
        assertEquals(CarrierSelection.Use(37_000), selection)
    }

    @Test
    fun `default policy is STRICT`() {
        val selection = CarrierPolicy.selectCarrier(
            requestedHz = 39_000,
            supportedRanges = listOf(38_000..38_000)
        )
        assertTrue(
            "The default carrier policy must be STRICT (commercial, no silent shift)",
            selection is CarrierSelection.Unsupported
        )
    }
}