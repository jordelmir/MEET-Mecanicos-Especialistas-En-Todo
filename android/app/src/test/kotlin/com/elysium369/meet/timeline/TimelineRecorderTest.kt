package com.elysium369.meet.timeline

import com.elysium369.meet.diagnostic.DiagnosticProvenance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineRecorderTest {

    private val vehicleId = "vehicle_test_001"

    @Test
    fun `record creates append-only event`() = runBlocking {
        val recorder = TimelineRecorder()
        val event = recorder.record(
            vehicleId = vehicleId,
            eventType = TimelineEventType.DTC_DETECTED,
            title = "P0301 detectado",
            source = TimelineSource.OBD,
            provenance = DiagnosticProvenance.Real
        )
        assertNotNull(event.id)
        assertEquals(vehicleId, event.vehicleId)
        assertEquals(1, recorder.totalFor(vehicleId))
    }

    @Test
    fun `events from different vehicles are isolated`() = runBlocking {
        val recorder = TimelineRecorder()
        recorder.record(vehicleId = "v1", eventType = TimelineEventType.DTC_DETECTED,
            title = "t1", source = TimelineSource.OBD, provenance = DiagnosticProvenance.Real)
        recorder.record(vehicleId = "v2", eventType = TimelineEventType.DTC_DETECTED,
            title = "t2", source = TimelineSource.OBD, provenance = DiagnosticProvenance.Real)
        assertEquals(1, recorder.totalFor("v1"))
        assertEquals(1, recorder.totalFor("v2"))
    }

    @Test
    fun `query filters by type`() = runBlocking {
        val recorder = TimelineRecorder()
        recorder.record(vehicleId, TimelineEventType.DTC_DETECTED, "P0301",
            TimelineSource.OBD, DiagnosticProvenance.Real)
        recorder.record(vehicleId, TimelineEventType.DTC_DETECTED, "P0420",
            TimelineSource.OBD, DiagnosticProvenance.Real)
        recorder.record(vehicleId, TimelineEventType.SERVICE_RESET, "Aceite",
            TimelineSource.USER, DiagnosticProvenance.Offline)

        val dtcsOnly = recorder.query(vehicleId, types = setOf(TimelineEventType.DTC_DETECTED))
        assertEquals(2, dtcsOnly.size)
        assertTrue(dtcsOnly.all { it.eventType == TimelineEventType.DTC_DETECTED })
    }

    @Test
    fun `query filters by time range`() = runBlocking {
        val recorder = TimelineRecorder()
        recorder.record(vehicleId, TimelineEventType.DTC_DETECTED, "early",
            TimelineSource.OBD, DiagnosticProvenance.Real, createdAtMs = 1000L)
        recorder.record(vehicleId, TimelineEventType.DTC_DETECTED, "mid",
            TimelineSource.OBD, DiagnosticProvenance.Real, createdAtMs = 5000L)
        recorder.record(vehicleId, TimelineEventType.DTC_DETECTED, "late",
            TimelineSource.OBD, DiagnosticProvenance.Real, createdAtMs = 9000L)

        val mid = recorder.query(vehicleId, sinceMs = 2000L, untilMs = 7000L)
        assertEquals(1, mid.size)
        assertEquals("mid", mid[0].title)
    }

    @Test
    fun `countByType aggregates per vehicle`() = runBlocking {
        val recorder = TimelineRecorder()
        recorder.record(vehicleId, TimelineEventType.DTC_DETECTED, "a",
            TimelineSource.OBD, DiagnosticProvenance.Real)
        recorder.record(vehicleId, TimelineEventType.DTC_DETECTED, "b",
            TimelineSource.OBD, DiagnosticProvenance.Real)
        recorder.record(vehicleId, TimelineEventType.SERVICE_RESET, "c",
            TimelineSource.USER, DiagnosticProvenance.Offline)
        assertEquals(2, recorder.countByType(vehicleId, TimelineEventType.DTC_DETECTED))
        assertEquals(1, recorder.countByType(vehicleId, TimelineEventType.SERVICE_RESET))
    }

    @Test
    fun `events are append-only - never overwritten`() = runBlocking {
        val recorder = TimelineRecorder()
        val e1 = recorder.record(vehicleId, TimelineEventType.DTC_DETECTED, "P0301",
            TimelineSource.OBD, DiagnosticProvenance.Real)
        // Re-record same data → different id.
        val e2 = recorder.record(vehicleId, TimelineEventType.DTC_DETECTED, "P0301",
            TimelineSource.OBD, DiagnosticProvenance.Real)
        assertFalse("Events should have distinct ids", e1.id == e2.id)
        assertEquals(2, recorder.totalFor(vehicleId))
    }

    @Test
    fun `query respects limit`() = runBlocking {
        val recorder = TimelineRecorder()
        repeat(20) { i ->
            recorder.record(vehicleId, TimelineEventType.DTC_DETECTED, "event-$i",
                TimelineSource.OBD, DiagnosticProvenance.Real, createdAtMs = (i + 1).toLong())
        }
        val limited = recorder.query(vehicleId, limit = 5)
        assertEquals(5, limited.size)
    }

    @Test
    fun `clearForVehicle removes only that vehicle`() = runBlocking {
        val recorder = TimelineRecorder()
        recorder.record("v1", TimelineEventType.DTC_DETECTED, "x",
            TimelineSource.OBD, DiagnosticProvenance.Real)
        recorder.record("v2", TimelineEventType.DTC_DETECTED, "y",
            TimelineSource.OBD, DiagnosticProvenance.Real)
        recorder.clearForVehicle("v1")
        assertEquals(0, recorder.totalFor("v1"))
        assertEquals(1, recorder.totalFor("v2"))
    }

    @Test
    fun `append pre-constructed event preserves identity`() = runBlocking {
        val recorder = TimelineRecorder()
        val event = VehicleTimelineEvent(
            id = "manual-id-123",
            vehicleId = vehicleId,
            eventType = TimelineEventType.DIAGNOSIS_GENERATED,
            title = "Diagnosis generated",
            source = TimelineSource.AI,
            provenance = DiagnosticProvenance.Inferred("rules", 0.8),
            createdAtMs = 12345L
        )
        recorder.append(event)
        val retrieved = recorder.query(vehicleId).first()
        assertEquals("manual-id-123", retrieved.id)
        assertEquals("Diagnosis generated", retrieved.title)
    }

    @Test
    fun `provenance is preserved on events`() = runBlocking {
        val recorder = TimelineRecorder()
        recorder.record(vehicleId, TimelineEventType.DTC_DETECTED, "manual entry",
            TimelineSource.USER, DiagnosticProvenance.ManualEntry("user-1"))
        val events = recorder.query(vehicleId)
        assertTrue(events.first().provenance is DiagnosticProvenance.ManualEntry)
    }

    @Test
    fun `blank title rejected`() {
        try {
            VehicleTimelineEvent(
                id = "x",
                vehicleId = "v",
                eventType = TimelineEventType.DTC_DETECTED,
                title = "",
                source = TimelineSource.OBD,
                provenance = DiagnosticProvenance.Real,
                createdAtMs = 1L
            )
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }
}