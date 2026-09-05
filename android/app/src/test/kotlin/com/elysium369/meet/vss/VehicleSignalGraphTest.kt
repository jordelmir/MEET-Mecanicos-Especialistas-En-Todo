package com.elysium369.meet.vss

import com.elysium369.meet.authority.VerificationLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VehicleSignalGraphTest {

    private lateinit var graph: VehicleSignalGraph

    @Before
    fun setUp() {
        graph = VehicleSignalGraph()
    }

    @Test
    fun `ingestObdPid correctly maps PID 010D to Vehicle_Speed in km_h with full provenance`() {
        val snapshot = graph.ingestObdPid(
            pid = "010D",
            numericValue = 85.5,
            rawHex = "410D55",
            ecuAddress = "7E0",
            timestampEpochMs = 123456789L,
        )

        assertNotNull(snapshot)
        assertEquals(VssStandardPaths.VEHICLE_SPEED, snapshot!!.path)
        assertEquals(85.5f, snapshot.asFloat()!!, 0.01f)
        assertEquals("km/h", snapshot.metadata.unit)
        assertEquals(SignalProtocol.OBD2_STANDARD, snapshot.provenance.protocol)
        assertEquals("010D", snapshot.provenance.rawIdentifier)
        assertEquals("410D55", snapshot.provenance.rawPayloadHex)
        assertEquals("7E0", snapshot.provenance.ecuAddress)
        assertEquals(VerificationLevel.PHYSICALLY_VERIFIED, snapshot.verificationLevel)

        // Read through VISS get API
        val read = graph.get(VssStandardPaths.VEHICLE_SPEED)
        assertNotNull(read)
        assertEquals(85.5f, read!!.asFloat()!!, 0.01f)
    }

    @Test
    fun `ingestObdPid maps RPM, ECT, MAP, and Voltage to correct VSS paths`() {
        graph.ingestObdPid("010C", 2450.0) // RPM
        graph.ingestObdPid("0105", 92.0)   // Coolant Temp
        graph.ingestObdPid("010B", 42.0)   // MAP kPa
        graph.ingestObdPid("0142", 14.2)   // Battery Net Voltage

        assertEquals(2450.0f, graph.get(VssStandardPaths.ENGINE_SPEED)?.asFloat()!!, 0.01f)
        assertEquals("rpm", graph.get(VssStandardPaths.ENGINE_SPEED)?.metadata?.unit)

        assertEquals(92.0f, graph.get(VssStandardPaths.ENGINE_ECT)?.asFloat()!!, 0.01f)
        assertEquals("celsius", graph.get(VssStandardPaths.ENGINE_ECT)?.metadata?.unit)

        assertEquals(42.0f, graph.get(VssStandardPaths.ENGINE_MAP)?.asFloat()!!, 0.01f)
        assertEquals("kPa", graph.get(VssStandardPaths.ENGINE_MAP)?.metadata?.unit)

        assertEquals(14.2f, graph.get(VssStandardPaths.BATTERY_VOLTAGE)?.asFloat()!!, 0.01f)
        assertEquals("V", graph.get(VssStandardPaths.BATTERY_VOLTAGE)?.metadata?.unit)
    }

    @Test
    fun `ingestDtcs stores active trouble codes in Vehicle_OBD_DTCList`() {
        val dtcs = listOf("P0300", "P0171")
        val snapshot = graph.ingestDtcs(dtcs = dtcs, rawHex = "430203000171")

        assertEquals(VssStandardPaths.OBD_DTC_LIST, snapshot.path)
        val list = snapshot.asStringList()
        assertNotNull(list)
        assertEquals(2, list!!.size)
        assertEquals("P0300", list[0])
        assertEquals("P0171", list[1])
    }

    @Test
    fun `ingestLocation maps GPS coordinates to standardized VSS location paths`() {
        graph.ingestLocation(
            latitude = 19.4326,
            longitude = -99.1332,
            heading = 180.5f,
            altitude = 2240.0,
        )

        assertEquals(19.4326, graph.get(VssStandardPaths.LOCATION_LATITUDE)?.asDouble()!!, 0.0001)
        assertEquals(-99.1332, graph.get(VssStandardPaths.LOCATION_LONGITUDE)?.asDouble()!!, 0.0001)
        assertEquals(180.5f, graph.get(VssStandardPaths.LOCATION_HEADING)?.asFloat()!!, 0.01f)
        assertEquals(2240.0, graph.get(VssStandardPaths.LOCATION_ALTITUDE)?.asDouble()!!, 0.1)
    }

    @Test
    fun `subscribe receives realtime VISS stream updates`() = runTest {
        val received = mutableListOf<Float>()

        val job = launch {
            graph.subscribe(VssStandardPaths.VEHICLE_SPEED).collect { snapshot ->
                snapshot.asFloat()?.let { received.add(it) }
            }
        }

        graph.ingestObdPid("010D", 10.0)
        graph.ingestObdPid("010D", 20.0)
        graph.ingestObdPid("010D", 30.0)

        assertEquals(3, received.size)
        assertEquals(listOf(10.0f, 20.0f, 30.0f), received)

        job.cancel()
    }

    @Test
    fun `set denies unauthorized physical signal mutation and allows authorized mutation`() {
        // Unauthorized attempt to command throttle position
        val unauthorizedResult = graph.set(
            path = VssStandardPaths.ENGINE_TPS,
            value = 50.0f,
            isAuthorizedAction = false,
        )
        assertTrue(unauthorizedResult is VssSetResult.Unauthorized)

        // Authorized diagnostic actuator test
        val authorizedResult = graph.set(
            path = VssStandardPaths.ENGINE_TPS,
            value = 50.0f,
            isAuthorizedAction = true,
        )
        assertTrue(authorizedResult is VssSetResult.Success)
        assertEquals(50.0f, graph.get(VssStandardPaths.ENGINE_TPS)?.asFloat()!!, 0.01f)
    }
}
