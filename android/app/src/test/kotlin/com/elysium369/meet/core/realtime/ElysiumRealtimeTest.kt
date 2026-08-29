package com.elysium369.meet.core.realtime

import com.elysium369.meet.core.remote.RemoteResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class ElysiumRealtimeTest {

    @Test
    fun realtimeClientSubscribesAndUnsubscribesProperly() {
        val client = ElysiumRealtimeClient()
        assertEquals(0, client.getActiveSubscriptionCount())

        client.subscribe("ride/ride-123", lastCursor = 10L)
        assertEquals(1, client.getActiveSubscriptionCount())

        client.unsubscribe("ride/ride-123")
        assertEquals(0, client.getActiveSubscriptionCount())
    }

    @Test
    fun realtimeEventEnvelopeSerializationAndFlow() = runBlocking {
        val client = ElysiumRealtimeClient()
        val payloadJson = buildJsonObject {
            put("state", "IN_PROGRESS")
            put("speedKmh", 45.0)
        }

        val envelope = RealtimeEventEnvelope(
            eventId = "evt-001",
            eventType = "ride.state.changed",
            eventClass = RealtimeEventClass.DURABLE_DOMAIN,
            occurredAt = "2026-08-29T00:00:00Z",
            aggregateType = "RIDE",
            aggregateId = "ride-123",
            aggregateVersion = 2,
            payload = payloadJson,
        )

        client.publishEventForTest(envelope)
        val received = client.events.first()

        assertEquals("evt-001", received.eventId)
        assertEquals("ride.state.changed", received.eventType)
        assertEquals("RIDE", received.aggregateType)
        assertEquals(2L, received.aggregateVersion)
    }

    @Test
    fun remoteResultHierarchyDistinguishesErrorsWithoutCollapsing() {
        val success: RemoteResult<String> = RemoteResult.Success("OK")
        val notFound: RemoteResult<String> = RemoteResult.NotFound
        val offline: RemoteResult<String> = RemoteResult.Offline
        val conflict: RemoteResult<String> = RemoteResult.VersionConflict(expectedVersion = 10, actualVersion = 12)

        assertTrue(success is RemoteResult.Success)
        assertTrue(notFound is RemoteResult.NotFound)
        assertTrue(offline is RemoteResult.Offline)
        assertTrue(conflict is RemoteResult.VersionConflict)
        assertEquals(10L, (conflict as RemoteResult.VersionConflict).expectedVersion)
        assertEquals(12L, conflict.actualVersion)
    }
}
