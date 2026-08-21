package com.elysium369.meet.core.evair

import com.elysium369.meet.core.evair.domain.ActionRisk
import com.elysium369.meet.core.evair.domain.AuthorizationResult
import com.elysium369.meet.core.evair.domain.DataQuality
import com.elysium369.meet.core.evair.domain.EventSeverity
import com.elysium369.meet.core.evair.domain.ProposedVehicleAction
import com.elysium369.meet.core.evair.domain.TelemetryPoint
import com.elysium369.meet.core.evair.domain.VehicleCommand
import com.elysium369.meet.core.evair.domain.VehicleEvent
import com.elysium369.meet.core.evair.domain.VehicleSnapshot
import com.elysium369.meet.core.evair.safety.VehicleSafetyBroker
import com.elysium369.meet.core.evair.telemetry.FeatureExtractor
import com.elysium369.meet.core.evair.telemetry.TelemetryRingBuffer
import com.elysium369.meet.core.obd.PhysicalBusOwner
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end functional proof test for EVAIR (Elysium Vanguard Automotive Intelligence Runtime).
 *
 * Verifies that the domain models, ring buffer, feature extraction, safety broker,
 * and JSON-RPC serialization contracts are 100% real, functional, and mathematically correct.
 */
class EvairEndToEndIntegrationTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `1 - Telemetry RingBuffer stores high-frequency samples and maintains chronological order`() {
        val buffer = TelemetryRingBuffer(capacity = 50)
        val now = 1_000_000L

        // Ingest 60 RPM samples (exceeds capacity of 50)
        for (i in 1..60) {
            val rpmValue = 750.0 + (i * 10.0) // 760, 770, ... 1350
            buffer.add(
                TelemetryPoint(
                    monotonicTimestampNs = (now + (i * 100)) * 1_000_000L,
                    wallClockTimestampMs = now + (i * 100),
                    pid = "010C",
                    value = rpmValue,
                    unit = "RPM",
                    quality = DataQuality.GOOD
                )
            )
        }

        // Must retain exactly the capacity (50 most recent samples)
        assertEquals(50, buffer.currentSize)
        assertTrue(buffer.isFull)

        val snapshot = buffer.snapshot()
        assertEquals(50, snapshot.size)

        // First sample in snapshot must be sample #11 (value = 860.0)
        assertEquals(860.0, snapshot.first().value, 0.001)
        // Last sample must be sample #60 (value = 1350.0)
        assertEquals(1350.0, snapshot.last().value, 0.001)
        assertEquals(1350.0, buffer.latest()?.value ?: 0.0, 0.001)
    }

    @Test
    fun `2 - Feature Extractor computes real mathematical moments, slopes and percentiles`() {
        // Generate a synthetic acceleration curve: RPM rises from 800 to 2800 over 4 seconds
        val samples = (0..20).map { step ->
            val timestamp = 1000L + (step * 200L) // every 200ms
            val rpm = 800.0 + (step * 100.0)      // 800, 900, ... 2800
            TelemetryPoint(
                monotonicTimestampNs = timestamp * 1_000_000L,
                wallClockTimestampMs = timestamp,
                pid = "010C",
                value = rpm,
                unit = "RPM",
                quality = DataQuality.GOOD
            )
        }

        val features = FeatureExtractor.extractFromPoints(
            pid = "010C",
            parameterName = "Engine RPM",
            unit = "RPM",
            samples = samples,
            windowDurationMs = 4000L
        )

        assertEquals("010C", features.pid)
        assertEquals(21, features.count)
        assertEquals(800.0, features.min, 0.001)
        assertEquals(2800.0, features.max, 0.001)
        assertEquals(1800.0, features.mean, 0.001) // Average of arithmetic progression 800..2800
        assertEquals(2000.0, features.delta, 0.001) // 2800 - 800

        // Slope = 2000 RPM delta over 4.0 seconds = 500 RPM / second
        assertEquals(500.0, features.slopePerSecond, 0.01)

        // Median (p50) must be 1800 RPM
        assertNotNull(features.p50)
        assertEquals(1800.0, features.p50!!, 5.0)

        // Quality check
        assertEquals(0.0, features.missingRate, 0.001)
        assertEquals(0.0, features.staleRate, 0.001)
    }

    @Test
    fun `3 - Safety Broker enforces strict automotive gating`() {
        val broker = VehicleSafetyBroker(getPhysicalBusOwner = { PhysicalBusOwner.IDLE })

        // A. Read PID is auto-authorized
        val readPidProposal = ProposedVehicleAction(
            actionId = "act_read_rpm",
            command = VehicleCommand.ReadPid(requestId = "req_1", pid = "010C"),
            reason = "Checking current engine speed",
            expectedObservation = "RPM value",
            risk = ActionRisk.NONE
        )
        val readResult = broker.authorize(readPidProposal)
        assertEquals(AuthorizationResult.Allowed, readResult)
        assertTrue(broker.isAutoExecutable(readPidProposal.command))

        // B. Active Test while driving (speed > 0) MUST BE HARD-DENIED
        val activeTestProposal = ProposedVehicleAction(
            actionId = "act_test_fan",
            command = VehicleCommand.RunDiagnosticTest(
                requestId = "req_2",
                testId = "RADIATOR_FAN_HIGH_SPEED"
            ),
            reason = "Verifying cooling fan operation",
            expectedObservation = "Fan activates",
            risk = ActionRisk.MEDIUM
        )

        val movingSnapshot = VehicleSnapshot(
            timestampMs = System.currentTimeMillis(),
            monotonicTimestampNs = 0L,
            vehicle = com.elysium369.meet.core.evair.domain.VehicleIdentity("VIN_TEST", "VIN_TEST", "Hyundai", "Accent", 2005, "1.6", "AT", "Accent"),
            connection = com.elysium369.meet.core.evair.domain.ConnectionSnapshot("CONNECTED", true, "CAN", "OPTIMAL", "BT", 25L),
            engine = com.elysium369.meet.core.evair.domain.EngineSnapshot(rpm = 2500.0, speedKph = 60.0),
            electrical = com.elysium369.meet.core.evair.domain.ElectricalSnapshot(14.2, 14.2),
            fuel = com.elysium369.meet.core.evair.domain.FuelSnapshot(),
            transmission = null,
            emissions = com.elysium369.meet.core.evair.domain.EmissionsSnapshot(),
            dtcs = emptyList(),
            readiness = emptyMap(),
            activeWarnings = emptyList(),
            dataSource = com.elysium369.meet.core.evair.domain.VehicleDataSource.REAL_OBD
        )

        val deniedResult = broker.authorize(activeTestProposal, movingSnapshot)
        assertTrue(deniedResult is AuthorizationResult.Denied)
        val denied = deniedResult as AuthorizationResult.Denied
        assertTrue(denied.reason.contains("movimiento"))

        // C. Active Test when stationary (speed = 0) REQUIRES CONFIRMATION
        val stoppedSnapshot = movingSnapshot.copy(
            engine = movingSnapshot.engine.copy(rpm = 750.0, speedKph = 0.0)
        )
        val confirmResult = broker.authorize(activeTestProposal, stoppedSnapshot)
        assertTrue(confirmResult is AuthorizationResult.RequiresConfirmation)

        // D. Clear DTCs ALWAYS REQUIRES CONFIRMATION
        val clearProposal = ProposedVehicleAction(
            actionId = "act_clear_codes",
            command = VehicleCommand.ClearDtcs(requestId = "req_3"),
            reason = "Clearing fixed DTCs",
            expectedObservation = "DTC list empty",
            risk = ActionRisk.HIGH
        )
        val clearResult = broker.authorize(clearProposal, stoppedSnapshot)
        assertTrue(clearResult is AuthorizationResult.RequiresConfirmation)
    }

    @Test
    fun `4 - VehicleSnapshot and VehicleEvents serialize and deserialize cleanly with JSON contract`() {
        val snapshot = VehicleSnapshot(
            timestampMs = 1771234567890L,
            monotonicTimestampNs = 55555555555L,
            vehicle = com.elysium369.meet.core.evair.domain.VehicleIdentity(
                vehicleId = "KMHCF41BP5U123456",
                vin = "KMHCF41BP5U123456",
                make = "Hyundai",
                model = "Accent Verna",
                year = 2005,
                engineType = "1.6L Alpha II G4ED DOHC",
                transmissionType = "4-Speed Automatic",
                label = "Accent Jor"
            ),
            connection = com.elysium369.meet.core.evair.domain.ConnectionSnapshot(
                phase = "CONNECTED",
                hasRealEcuLink = true,
                protocol = "ISO 15765-4 11bit 500k",
                adapterQuality = "OPTIMAL",
                transport = "BT_CLASSIC",
                latencyMs = 28L
            ),
            engine = com.elysium369.meet.core.evair.domain.EngineSnapshot(
                rpm = 820.0,
                coolantTempC = 92.5,
                intakeTempC = 34.0,
                engineLoadPct = 18.5,
                timingAdvanceDeg = 10.0,
                mapKpa = 32.0,
                throttlePct = 12.0,
                mafGps = 3.2,
                speedKph = 0.0
            ),
            electrical = com.elysium369.meet.core.evair.domain.ElectricalSnapshot(
                controlModuleVoltage = 14.15,
                batteryVoltage = 14.15
            ),
            fuel = com.elysium369.meet.core.evair.domain.FuelSnapshot(
                stftBank1Pct = 1.56,
                ltftBank1Pct = -2.34,
                fuelLevelPct = 68.0
            ),
            transmission = null,
            emissions = com.elysium369.meet.core.evair.domain.EmissionsSnapshot(
                o2B1S1Voltage = 0.65,
                o2B1S2Voltage = 0.45
            ),
            dtcs = listOf(
                com.elysium369.meet.core.evair.domain.DtcSnapshot(
                    code = "P0171",
                    category = com.elysium369.meet.core.evair.domain.DtcCategory.PENDING,
                    description = "System Too Lean (Bank 1)",
                    firstSeenMs = 1771234500000L,
                    occurrenceCount = 2
                )
            ),
            readiness = mapOf("MISFIRE" to "READY", "CATALYST" to "NOT_READY"),
            activeWarnings = emptyList(),
            dataSource = com.elysium369.meet.core.evair.domain.VehicleDataSource.REAL_OBD
        )

        // Serialize to JSON
        val jsonString = json.encodeToString(snapshot)
        assertFalse(jsonString.isEmpty())
        assertTrue(jsonString.contains("KMHCF41BP5U123456"))
        assertTrue(jsonString.contains("1.6L Alpha II G4ED DOHC"))
        assertTrue(jsonString.contains("P0171"))

        // Deserialize back
        val decoded = json.decodeFromString<VehicleSnapshot>(jsonString)
        assertEquals(snapshot.vehicle.vin, decoded.vehicle.vin)
        assertEquals(snapshot.engine.rpm, decoded.engine.rpm)
        assertEquals(snapshot.electrical.controlModuleVoltage, decoded.electrical.controlModuleVoltage)
        assertEquals(snapshot.dtcs.size, decoded.dtcs.size)
        assertEquals("P0171", decoded.dtcs.first().code)

        // Test Overheat Event Serialization
        val event: VehicleEvent = VehicleEvent.OverheatRisk(
            timestampMs = 1771234567890L,
            severity = EventSeverity.CRITICAL,
            coolantTempC = 118.5,
            risingRateCPerMinute = 8.2,
            baselineCoolantC = 92.0
        )

        val eventJson = json.encodeToString(event)
        assertTrue(eventJson.contains("118.5"))
        assertTrue(eventJson.contains("CRITICAL"))
        val decodedEvent = json.decodeFromString<VehicleEvent>(eventJson)
        assertTrue(decodedEvent is VehicleEvent.OverheatRisk)
        val overheat = decodedEvent as VehicleEvent.OverheatRisk
        assertEquals(118.5, overheat.coolantTempC, 0.001)
        assertEquals(EventSeverity.CRITICAL, overheat.severity)
    }
}
