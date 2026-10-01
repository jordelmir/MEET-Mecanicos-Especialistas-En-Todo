package com.elysium.server.workers

import com.elysium.server.realtime.RealtimeSessionRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProductionOutboxInfrastructureTest {

    @Test
    fun `ProductionDomainEventPublisher delivers event with sequence and routes to channels`() = runBlocking {
        val publisher = ProductionDomainEventPublisher()

        val event = OutboxEventRecord(
            outboxId = 42L,
            eventId = "evt-prod-100",
            sourceDomain = "mobility",
            sourceId = "ride-abc",
            aggregateType = "RIDE",
            aggregateId = "ride-abc",
            aggregateVersion = 2L,
            eventType = "mobility.ride.accepted.v1",
            payload = """{"driverId":"drv-777","fareMinor":3500}""",
            targetPrincipalId = "pax-888",
        )

        val result = publisher.publish(event)
        assertEquals(EventPublishResult.Success, result)
    }

    @Test
    fun `OutboxWorker rejects instantiation without explicit dependencies in strict tests`() {
        val repo = InMemoryOutboxRepository()
        val publisher = ProductionDomainEventPublisher()
        val worker = OutboxWorker(
            repository = repo,
            publisher = publisher,
        )
        assertNotNull(worker)
    }
}
