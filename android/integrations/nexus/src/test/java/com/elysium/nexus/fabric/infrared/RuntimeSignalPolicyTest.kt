package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.fabric.infrared.database.InMemoryIrCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V0.7 Phase 5 — Commercial codec policy tests.
 *
 * The audit requirement: "No candidate loader may bypass this policy."
 * EXPERIMENTAL codecs (RC5, RC6, Kaseikyo) and unknown codecs are LAB_ONLY
 * and blocked from every runtime path: probe, saved profiles, brand lookup,
 * direct lookup, and automation.
 */
class RuntimeSignalPolicyTest {

    private fun encodedSignal(
        protocol: IrProtocol,
        codecId: String?,
        address: Int = 0,
        command: Int = 1
    ): IrSignal.Encoded = IrSignal.Encoded(
        carrierHz = protocol.carrierHz,
        protocol = protocol,
        address = address,
        command = command,
        codecId = codecId
    )

    @Test
    fun `COMMERCIAL blocks EXPERIMENTAL codecs`() {
        val experimental = listOf(
            encodedSignal(IrProtocol.Rc5, "RC5"),
            encodedSignal(IrProtocol.Rc6, "RC6"),
            encodedSignal(IrProtocol.Kaseikyo, "KASEIKYO")
        )
        experimental.forEach { signal ->
            assertTrue(
                "EXPERIMENTAL codec ${signal.codecId} must be LAB_ONLY",
                !RuntimeSignalPolicy.isExecutable(signal, RuntimePolicy.COMMERCIAL)
            )
        }
    }

    @Test
    fun `COMMERCIAL allows conformance-validated codecs`() {
        val allowed = listOf(
            encodedSignal(IrProtocol.Nec, "NEC"),
            encodedSignal(IrProtocol.Samsung, "SAMSUNG"),
            encodedSignal(IrProtocol.SonySirc, "SIRC"),
            encodedSignal(IrProtocol.Aiwa, "AIWA")
        )
        allowed.forEach { signal ->
            assertTrue(
                "Codec ${signal.codecId} must be transmittable under COMMERCIAL",
                RuntimeSignalPolicy.isExecutable(signal, RuntimePolicy.COMMERCIAL)
            )
        }
    }

    @Test
    fun `COMMERCIAL fails closed on unknown or null codecId`() {
        assertTrue(
            "Null codecId must fail closed",
            !RuntimeSignalPolicy.isExecutable(
                encodedSignal(IrProtocol.Nec, codecId = null),
                RuntimePolicy.COMMERCIAL
            )
        )
        assertTrue(
            "Unknown codecId must fail closed",
            !RuntimeSignalPolicy.isExecutable(
                encodedSignal(IrProtocol.Nec, codecId = "PIONEER_NOT_REGISTERED"),
                RuntimePolicy.COMMERCIAL
            )
        )
    }

    @Test
    fun `COMMERCIAL allows raw waveforms`() {
        val raw = IrSignal.Raw(carrierHz = 38_000, patternUs = intArrayOf(9000, 4500, 560, 560))
        assertTrue(RuntimeSignalPolicy.isExecutable(raw, RuntimePolicy.COMMERCIAL))
    }

    @Test
    fun `LAB_ONLY admits experimental signals`() {
        val rc5 = encodedSignal(IrProtocol.Rc5, "RC5")
        assertTrue(RuntimeSignalPolicy.isExecutable(rc5, RuntimePolicy.LAB_ONLY))
    }

    @Test
    fun `resolveExecutableSignal default policy blocks experimental on InMemory catalog`() = kotlinx.coroutines.test.runTest {
        val rc5 = encodedSignal(IrProtocol.Rc5, "RC5")
        val nec = encodedSignal(IrProtocol.Nec, "NEC")
        val raw = IrSignal.Raw(carrierHz = 38_000, patternUs = intArrayOf(9000, 4500, 560, 560))
        val catalog = InMemoryIrCatalog(signalMap = mapOf(
            "sig-rc5" to rc5,
            "sig-nec" to nec,
            "sig-raw" to raw
        ))

        assertNull(
            "Direct lookup must not return EXPERIMENTAL codecs under COMMERCIAL",
            catalog.resolveExecutableSignal("sig-rc5")
        )
        assertNotNull(
            "Direct lookup must return validated codecs",
            catalog.resolveExecutableSignal("sig-nec")
        )
        assertNotNull(
            "Direct lookup must return raw waveforms",
            catalog.resolveExecutableSignal("sig-raw")
        )
        assertNull("Unknown signal id must return null", catalog.resolveExecutableSignal("sig-missing"))
    }

    @Test
    fun `resolveExecutableSignal LAB_ONLY bypasses only in lab`() = kotlinx.coroutines.test.runTest {
        val rc5 = encodedSignal(IrProtocol.Rc5, "RC5")
        val catalog = InMemoryIrCatalog(signalMap = mapOf("sig-rc5" to rc5))

        assertEquals(
            "LAB_ONLY must admit experimental signals",
            rc5,
            catalog.resolveExecutableSignal("sig-rc5", RuntimePolicy.LAB_ONLY)
        )
        assertNull(
            "COMMERCIAL must block experimental signals even when asked by id",
            catalog.resolveExecutableSignal("sig-rc5", RuntimePolicy.COMMERCIAL)
        )
    }
}