package com.elysium369.meet.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiContextBuilderTest {

    @Test
    fun `hashVin produces 16 hex chars`() {
        val h = AiContextBuilder.hashVin("1HGCM82633A123456")
        assertEquals(16, h.length)
        assertTrue(h.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `hashVin is deterministic`() {
        val a = AiContextBuilder.hashVin("1HGCM82633A123456")
        val b = AiContextBuilder.hashVin("1HGCM82633A123456")
        assertEquals(a, b)
    }

    @Test
    fun `hashVin differs for different VINs`() {
        val a = AiContextBuilder.hashVin("1HGCM82633A123456")
        val b = AiContextBuilder.hashVin("1HGCM82633A999999")
        assertNotEquals(a, b)
    }

    @Test
    fun `build requires consent`() {
        val builder = AiContextBuilder()
        val raw = DiagnosticContext(
            dtcCode = "P0230",
            dtcStatus = "ACTIVE",
            consentGranted = false
        )
        assertThrows(IllegalStateException::class.java) {
            builder.build(raw)
        }
    }

    @Test
    fun `build accepts valid hashed VIN`() {
        val builder = AiContextBuilder()
        val vinHash = AiContextBuilder.hashVin("1HGCM82633A123456")
        val raw = DiagnosticContext(
            dtcCode = "P0230",
            dtcStatus = "ACTIVE",
            vinHash = vinHash,
            consentGranted = true
        )
        val built = builder.build(raw)
        assertEquals(vinHash, built.vinHash)
    }

    @Test
    fun `build rejects too-short vinHash`() {
        val builder = AiContextBuilder()
        val raw = DiagnosticContext(
            dtcCode = "P0230",
            dtcStatus = "ACTIVE",
            vinHash = "abc123",  // 6 chars, too short
            consentGranted = true
        )
        assertThrows(IllegalArgumentException::class.java) {
            builder.build(raw)
        }
    }

    @Test
    fun `P0230 full context is built with consent and hashed VIN`() {
        val builder = AiContextBuilder()
        val vinHash = AiContextBuilder.hashVin("KMHCN46C18U123456")
        val raw = DiagnosticContext(
            dtcCode = "P0230",
            dtcStatus = "ACTIVE",
            freezeFrame = mapOf("BatteryVoltage" to 11.2, "RPM" to 0.0),
            livePids = mapOf("ECT" to 87.0),
            vehicleMake = "Hyundai",
            vehicleModel = "Accent",
            vehicleYear = 2005,
            engine = "1.6L DOHC",
            transmission = "automatic",
            odometerKm = 120000.0,
            vinHash = vinHash,
            history = listOf("P0230 cleared 8 months ago"),
            relatedDtcs = listOf("P0231"),
            completedTests = emptyList(),
            rankedCauses = listOf("battery/ground", "relay", "fuse"),
            scannerCapabilities = "GENERIC_ELM327",
            consentGranted = true
        )
        val built = builder.build(raw)
        assertNotNull(built)
        // The context is the same shape; no full VIN ever leaves the device.
        assertEquals(vinHash, built.vinHash)
        assertTrue(built.history.isNotEmpty())
    }
}
