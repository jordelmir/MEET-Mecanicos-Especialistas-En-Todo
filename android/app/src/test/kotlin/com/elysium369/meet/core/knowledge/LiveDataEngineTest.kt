package com.elysium369.meet.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDataEngineTest {

    @Test
    fun `readPid returns MISSING when scanner disconnected`() {
        val engine = LiveDataEngine()
        // state is DISCONNECTED by default
        val v = engine.readPid("BATTERY_VOLTAGE", "Battery Voltage", "V", 0L)
        assertEquals(DataQuality.MISSING, v.quality)
        assertEquals("SIN ENLACE", v.status())
        assertNull(v.value)
    }

    @Test
    fun `readPid returns INVALID when PID not supported`() {
        val engine = LiveDataEngine()
        engine.setState(LiveDataEngine.ConnectionState.READY)
        // Battery voltage not marked as supported
        val v = engine.readPid("BATTERY_VOLTAGE", "Battery Voltage", "V", 0L)
        assertEquals(DataQuality.INVALID, v.quality)
        assertEquals("INVALIDO", v.status())
    }

    @Test
    fun `readPid returns REAL when scanner ready and PID supported`() {
        val engine = LiveDataEngine()
        engine.setState(LiveDataEngine.ConnectionState.READY)
        engine.markPidSupported("BATTERY_VOLTAGE")
        val v = engine.readPid("BATTERY_VOLTAGE", "Battery Voltage", "V", 0L)
        // The placeholder returns 0.0; quality is REAL but value is generic.
        assertEquals(DataQuality.REAL, v.quality)
        assertEquals("OBD", v.source)
    }

    @Test
    fun `readPid returns SIMULATED when state is SIMULATED`() {
        val engine = LiveDataEngine()
        engine.setState(LiveDataEngine.ConnectionState.SIMULATED)
        engine.markPidSupported("BATTERY_VOLTAGE")
        val v = engine.readPid("BATTERY_VOLTAGE", "Battery Voltage", "V", 0L)
        assertEquals(DataQuality.SIMULATED, v.quality)
        assertEquals("SIMULADO", v.status())
        assertEquals("SIMULATED", v.source)
    }

    @Test
    fun `out of range produces FUERA DE RANGO status`() {
        val engine = LiveDataEngine()
        engine.setState(LiveDataEngine.ConnectionState.READY)
        engine.markPidSupported("BATTERY_VOLTAGE")
        val v = engine.readPid(
            "BATTERY_VOLTAGE", "Battery Voltage", "V", 0L,
            expectedRange = ExpectedRange(min = 12.4, max = 12.7, unit = "V")
        )
        // Placeholder value is 0.0; range is 12.4-12.7 → out of range.
        assertEquals("FUERA DE RANGO", v.status())
    }

    @Test
    fun `OEM_LICENSED_RANGE_FUTURE produces disclaimer`() {
        val engine = LiveDataEngine()
        val v = engine.readPid(
            "RPM", "Engine RPM", "rpm", 0L,
            expectedRange = ExpectedRange(
                min = 0.0, max = 8000.0, unit = "rpm",
                source = RangeSource.OEM_LICENSED_RANGE_FUTURE
            )
        )
        val disclaimer = v.rangeDisclaimer()
        assertNotNull(disclaimer)
        assertTrue(disclaimer!!.contains("Rango específico no disponible"))
    }

    @Test
    fun `isConnected only true for active connection states`() {
        val engine = LiveDataEngine()
        assertEquals(false, engine.isConnected())
        engine.setState(LiveDataEngine.ConnectionState.READY)
        assertEquals(true, engine.isConnected())
        engine.setState(LiveDataEngine.ConnectionState.DISCONNECTED)
        assertEquals(false, engine.isConnected())
        engine.setState(LiveDataEngine.ConnectionState.SCANNING)
        assertEquals(true, engine.isConnected())
    }
}
