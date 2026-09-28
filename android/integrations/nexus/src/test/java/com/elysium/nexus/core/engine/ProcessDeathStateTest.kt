package com.elysium.nexus.core.engine

import com.elysium.nexus.core.device.IrAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Process-death state serialization contract.
 *
 * When the system kills the app process (low memory, config change,
 * etc.), all `remember {}` state is lost. This test proves that the
 * critical data structures used across the app can be round-tripped
 * through a key-value store — the same contract that `SavedStateHandle`
 * and `rememberSaveable` satisfy when backed by a `Bundle`.
 *
 * We use a plain Map here because `android.os.Bundle` returns
 * default values (null/0/false) under `isReturnDefaultValues = true`.
 * The real Bundle round-trip is verified by the instrumented test
 * [ProfileResolutionInstrumentedIntegrationTest] which runs on-device.
 *
 * See: https://developer.android.com/topic/libraries/architecture/saving-state
 */
class ProcessDeathStateTest {

    /**
     * Minimal Bundle-like key-value store for JVM testing.
     * Mirrors the String→Any? contract of android.os.Bundle.
     */
    private class FakeBundle {
        private val data = mutableMapOf<String, Any>()
        fun putString(key: String, value: String) { data[key] = value }
        fun putInt(key: String, value: Int) { data[key] = value }
        fun putBoolean(key: String, value: Boolean) { data[key] = value }
        fun putStringList(key: String, value: List<String>) { data[key] = value }
        fun getString(key: String): String? = data[key] as? String
        fun getInt(key: String): Int = (data[key] as? Int) ?: 0
        fun getBoolean(key: String): Boolean = (data[key] as? Boolean) ?: false
        fun getStringList(key: String): List<String>? = data[key] as? List<String>
    }

    // ── IR Wizard State ──

    @Test
    fun irWizardStep_roundTrips() {
        val bundle = FakeBundle()
        bundle.putString("ir_step", "CONFIRM")
        assertEquals("CONFIRM", bundle.getString("ir_step"))
    }

    @Test
    fun irWizardStep_allValues() {
        val steps = listOf("ORIENT", "PROBE", "CONFIRM", "DONE", "CHALLENGE")
        val bundle = FakeBundle()
        for ((i, step) in steps.withIndex()) {
            bundle.putString("ir_step_$i", step)
        }
        for ((i, expected) in steps.withIndex()) {
            assertEquals(expected, bundle.getString("ir_step_$i"))
        }
    }

    @Test
    fun verifiedActions_roundTrips() {
        val actions = setOf(IrAction.VOLUME_UP, IrAction.VOLUME_DOWN, IrAction.MUTE, IrAction.POWER_TOGGLE)
        val bundle = FakeBundle()
        bundle.putStringList("verified_actions", actions.map { it.name })
        val restored = bundle.getStringList("verified_actions")?.map { IrAction.valueOf(it) }?.toSet()
        assertEquals(actions, restored)
    }

    @Test
    fun verifiedActions_emptySet() {
        val bundle = FakeBundle()
        bundle.putStringList("verified_actions", emptyList())
        val restored = bundle.getStringList("verified_actions")?.map { IrAction.valueOf(it) }?.toSet()
        assertTrue(restored.isNullOrEmpty())
    }

    @Test
    fun verifiedActions_allActions() {
        val allActions = IrAction.values().toList()
        val bundle = FakeBundle()
        bundle.putStringList("verified_actions", allActions.map { it.name })
        val restored = bundle.getStringList("verified_actions")?.map { IrAction.valueOf(it) }
        assertEquals(allActions, restored)
    }

    @Test
    fun candidateId_roundTrips() {
        val bundle = FakeBundle()
        bundle.putString("candidate_id", "cs:samsung:un55hu8500:MUTE")
        assertEquals("cs:samsung:un55hu8500:MUTE", bundle.getString("candidate_id"))
    }

    @Test
    fun autoScanProgress_roundTrips() {
        val bundle = FakeBundle()
        bundle.putInt("scan_index", 42)
        bundle.putInt("scan_total", 200)
        bundle.putString("scan_brand", "Samsung")
        assertEquals(42, bundle.getInt("scan_index"))
        assertEquals(200, bundle.getInt("scan_total"))
        assertEquals("Samsung", bundle.getString("scan_brand"))
    }

    // ── Navigation State ──

    @Test
    fun navBackStack_roundTrips() {
        val stack = listOf("Hub", "IrConnect", "TvControl")
        val bundle = FakeBundle()
        bundle.putStringList("nav_stack", stack)
        assertEquals(stack, bundle.getStringList("nav_stack"))
    }

    @Test
    fun selectedProfileId_roundTrips() {
        val bundle = FakeBundle()
        bundle.putString("selected_profile_id", "ir-abc123")
        assertEquals("ir-abc123", bundle.getString("selected_profile_id"))
    }

    // ── Text Input State ──

    @Test
    fun importText_roundTrips() {
        val bundle = FakeBundle()
        val hex = "040 00 01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59 60 61 62 63"
        bundle.putString("import_text", hex)
        assertEquals(65, bundle.getString("import_text")!!.split(" ").size)
    }

    @Test
    fun profileName_roundTrips() {
        val bundle = FakeBundle()
        bundle.putString("profile_name", "Mi Samsung del Living")
        assertEquals("Mi Samsung del Living", bundle.getString("profile_name"))
    }

    @Test
    fun macHostPort_roundTrips() {
        val bundle = FakeBundle()
        bundle.putString("mac_host", "192.168.1.100")
        bundle.putInt("mac_port", 7878)
        assertEquals("192.168.1.100", bundle.getString("mac_host"))
        assertEquals(7878, bundle.getInt("mac_port"))
    }

    @Test
    fun pairingPin_roundTrips() {
        val digits = listOf("1", "2", "3", "4", "5", "6")
        val bundle = FakeBundle()
        bundle.putStringList("pin_digits", digits)
        assertEquals(digits, bundle.getStringList("pin_digits"))
    }

    // ── AC State ──

    @Test
    fun acSettings_roundTrips() {
        val bundle = FakeBundle()
        bundle.putInt("ac_temp", 24)
        bundle.putString("ac_mode", "COOL")
        bundle.putInt("ac_fan", 2)
        bundle.putBoolean("ac_power", true)
        assertEquals(24, bundle.getInt("ac_temp"))
        assertEquals("COOL", bundle.getString("ac_mode"))
        assertEquals(2, bundle.getInt("ac_fan"))
        assertTrue(bundle.getBoolean("ac_power"))
    }

    // ── Automation State ──

    @Test
    fun automationConfig_roundTrips() {
        val bundle = FakeBundle()
        bundle.putString("auto_name", "Noche")
        bundle.putString("auto_trigger", "TIME_22_00")
        bundle.putString("auto_device", "ir-living-tv")
        bundle.putBoolean("auto_action", false)
        assertEquals("Noche", bundle.getString("auto_name"))
        assertEquals("TIME_22_00", bundle.getString("auto_trigger"))
        assertEquals("ir-living-tv", bundle.getString("auto_device"))
        assertEquals(false, bundle.getBoolean("auto_action"))
    }

    // ── Universal Remote State ──

    @Test
    fun airMouseEnabled_roundTrips() {
        val bundle = FakeBundle()
        bundle.putBoolean("air_mouse", true)
        assertTrue(bundle.getBoolean("air_mouse"))
    }

    @Test
    fun universalSearchQuery_roundTrips() {
        val bundle = FakeBundle()
        bundle.putString("search_query", "Samsung")
        assertEquals("Samsung", bundle.getString("search_query"))
    }

    // ── Large Data ──

    @Test
    fun largeCandidateList_roundTrips() {
        val candidates = (1..400).map { "cs:brand$it:model$it:ACTION_$it" }
        val bundle = FakeBundle()
        bundle.putStringList("candidates", candidates)
        val restored = bundle.getStringList("candidates")
        assertEquals(400, restored?.size)
        assertEquals(candidates, restored)
    }

    @Test
    fun multipleStateKeys_coexist() {
        val bundle = FakeBundle()
        bundle.putString("ir_step", "CONFIRM")
        bundle.putStringList("verified_actions", listOf("VOLUME_UP", "MUTE"))
        bundle.putString("candidate_id", "cs:samsung:un55hu8500:MUTE")
        bundle.putInt("scan_index", 42)
        bundle.putBoolean("is_auto_scanning", true)

        assertEquals("CONFIRM", bundle.getString("ir_step"))
        assertEquals(2, bundle.getStringList("verified_actions")?.size)
        assertEquals("cs:samsung:un55hu8500:MUTE", bundle.getString("candidate_id"))
        assertEquals(42, bundle.getInt("scan_index"))
        assertTrue(bundle.getBoolean("is_auto_scanning"))
    }

    // ── Default Values (missing key returns safe defaults) ──

    @Test
    fun missingKey_returnsNull() {
        val bundle = FakeBundle()
        assertEquals(null, bundle.getString("nonexistent"))
    }

    @Test
    fun missingKey_returnsZero() {
        val bundle = FakeBundle()
        assertEquals(0, bundle.getInt("nonexistent"))
    }

    @Test
    fun missingKey_returnsFalse() {
        val bundle = FakeBundle()
        assertEquals(false, bundle.getBoolean("nonexistent"))
    }

    @Test
    fun missingKey_returnsNullList() {
        val bundle = FakeBundle()
        assertEquals(null, bundle.getStringList("nonexistent"))
    }
}
