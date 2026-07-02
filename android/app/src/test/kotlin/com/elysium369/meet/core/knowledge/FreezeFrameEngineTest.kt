package com.elysium369.meet.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeFrameEngineTest {

    @Test
    fun `P0230 freeze frame 11_2V is reported as BAJO with low-voltage impact`() {
        val engine = FreezeFrameEngine()
        val snap = FreezeFrameSnapshot(
            dtcCode = "P0230",
            capturedAt = 0L,
            pids = mapOf("BATTERY_VOLTAGE" to 11.2)
        )
        val c = engine.batteryComparison(snap)
        assertEquals("BATTERY_VOLTAGE", c.pid)
        assertEquals("BAJO", c.status)
        assertEquals(11.2, c.actual!!, 0.001)
        assertTrue(c.impact.contains("falsos DTC electricos"))
    }

    @Test
    fun `P0230 freeze frame 12_5V is reported as OK`() {
        val engine = FreezeFrameEngine()
        val snap = FreezeFrameSnapshot(
            dtcCode = "P0230",
            capturedAt = 0L,
            pids = mapOf("BATTERY_VOLTAGE" to 12.5)
        )
        val c = engine.batteryComparison(snap)
        assertEquals("OK", c.status)
    }

    @Test
    fun `data quality MISSING produces SIN ENLACE REAL status`() {
        val engine = FreezeFrameEngine()
        val snap = FreezeFrameSnapshot(
            dtcCode = "P0230",
            capturedAt = 0L,
            pids = mapOf("BATTERY_VOLTAGE" to 0.0),
            dataQuality = DataQuality.MISSING
        )
        val c = engine.batteryComparison(snap)
        assertEquals("SIN ENLACE REAL", c.status)
    }

    @Test
    fun `OEM_LICENSED_RANGE_FUTURE produces VALIDAR CON OEM status`() {
        val engine = FreezeFrameEngine()
        val snap = FreezeFrameSnapshot(
            dtcCode = "P0230",
            capturedAt = 0L,
            pids = mapOf("BATTERY_VOLTAGE" to 12.5)
        )
        val c = engine.buildComparison(
            snap,
            "BATTERY_VOLTAGE",
            "Battery Voltage",
            ExpectedRange(
                min = 12.0, max = 13.0, unit = "V",
                source = RangeSource.OEM_LICENSED_RANGE_FUTURE
            )
        )
        assertEquals("VALIDAR CON OEM", c.status)
    }

    @Test
    fun `ECT high produces SOBRE CALENTAMIENTO impact`() {
        val engine = FreezeFrameEngine()
        val snap = FreezeFrameSnapshot(
            dtcCode = "P0217",
            capturedAt = 0L,
            pids = mapOf("ECT" to 118.0)
        )
        val c = engine.buildComparison(
            snap,
            "ECT",
            "Engine Coolant Temp",
            ExpectedRange(min = 80.0, max = 105.0, unit = "C")
        )
        assertEquals("ALTO", c.status)
        assertTrue(c.impact.contains("Sobrecalentamiento"))
    }
}
