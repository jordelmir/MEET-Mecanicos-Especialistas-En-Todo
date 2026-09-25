package com.elysium.server.workers

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OutboxWorkerTest {

    @Test
    fun `pollAndPublishPendingEvents claims and successfully publishes events`() = runBlocking {
        val repository = InMemoryOutboxRepository()
        val publishedEvents = mutableListOf<OutboxEventRecord>()
        val publisher = EventPublisher { event ->
            publishedEvents.add(event)
            EventPublishResult.Success
        }

        val worker = OutboxWorker(
            repository = repository,
            publisher = publisher,
            batchSize = 10,
        )

        val id1 = repository.insertEvent(
            OutboxEventRecord(
                outboxId = 0L,
                sourceDomain = "mobility",
                sourceId = "ride-123",
                aggregateType = "RIDE",
                aggregateId = "ride-123",
                eventType = "mobility.ride.requested.v1",
            )
        )
        val id2 = repository.insertEvent(
            OutboxEventRecord(
                outboxId = 0L,
                sourceDomain = "emissions",
                sourceId = "diag-456",
                aggregateType = "DIAGNOSTIC",
                aggregateId = "diag-456",
                eventType = "emissions.session.completed.v1",
            )
        )

        val publishedCount = worker.pollAndPublishPendingEvents()

        assertEquals(2, publishedCount)
        assertEquals(2, publishedEvents.size)

        val record1 = repository.getEvent(id1)
        val record2 = repository.getEvent(id2)
        assertNotNull(record1?.publishedAtEpochMs)
        assertNotNull(record2?.publishedAtEpochMs)
        assertNull(record1?.leaseToken)
        assertNull(record2?.leaseToken)
    }

    @Test
    fun `pollAndPublishPendingEvents retries with backoff on retryable error`() = runBlocking {
        val repository = InMemoryOutboxRepository()
        val publisher = EventPublisher {
            EventPublishResult.RetryableError("NETWORK_TIMEOUT")
        }

        val worker = OutboxWorker(
            repository = repository,
            publisher = publisher,
            maxAttempts = 3,
        )

        val id = repository.insertEvent(
            OutboxEventRecord(
                outboxId = 0L,
                sourceDomain = "finance",
                sourceId = "pay-789",
                aggregateType = "PAYMENT",
                aggregateId = "pay-789",
                eventType = "payments.authorization.confirmed.v1",
            )
        )

        val publishedCount = worker.pollAndPublishPendingEvents()
        assertEquals(0, publishedCount)

        val record = repository.getEvent(id)
        assertNotNull(record)
        assertEquals(1, record?.publishAttempts)
        assertNull(record?.publishedAtEpochMs)
        assertNull(record?.deadLetteredAtEpochMs)
        assertTrue((record?.nextAttemptAtEpochMs ?: 0L) > System.currentTimeMillis() - 100L)
        assertEquals("NETWORK_TIMEOUT", record?.lastErrorCode)
    }

    @Test
    fun `pollAndPublishPendingEvents routes to DLQ after exceeding max attempts`() = runBlocking {
        val repository = InMemoryOutboxRepository()
        val publisher = EventPublisher {
            EventPublishResult.RetryableError("PERMANENT_DOWNSTREAM_REJECTION")
        }

        val worker = OutboxWorker(
            repository = repository,
            publisher = publisher,
            maxAttempts = 2,
        )

        val id = repository.insertEvent(
            OutboxEventRecord(
                outboxId = 0L,
                sourceDomain = "safety",
                sourceId = "report-101",
                aggregateType = "SAFETY_REPORT",
                aggregateId = "report-101",
                eventType = "safety.report.created.v1",
                publishAttempts = 1, // Already attempted once
            )
        )

        val publishedCount = worker.pollAndPublishPendingEvents()
        assertEquals(0, publishedCount)

        val record = repository.getEvent(id)
        assertNotNull(record?.deadLetteredAtEpochMs, "Event must be dead-lettered after reaching max attempts")
        assertNull(record?.publishedAtEpochMs)
        assertTrue(record?.lastErrorCode?.contains("EXCEEDED_MAX_ATTEMPTS") == true)
    }

    @Test
    fun `pollAndPublishPendingEvents respects active lease preventing duplicate processing`() = runBlocking {
        val repository = InMemoryOutboxRepository()
        val publisher = EventPublisher { EventPublishResult.Success }

        val worker1 = OutboxWorker(repository = repository, publisher = publisher, workerId = "worker-1", leaseDurationMs = 60_000L)
        val worker2 = OutboxWorker(repository = repository, publisher = publisher, workerId = "worker-2")

        val id = repository.insertEvent(
            OutboxEventRecord(
                outboxId = 0L,
                sourceDomain = "mobility",
                sourceId = "ride-999",
                aggregateType = "RIDE",
                aggregateId = "ride-999",
                eventType = "mobility.ride.accepted.v1",
            )
        )

        // Worker 1 claims with a lease token but hasn't published yet
        val claimed = repository.claimPendingEvents(
            workerId = "worker-1",
            leaseToken = "token-1",
            leaseUntilEpochMs = System.currentTimeMillis() + 60_000L,
            batchSize = 10,
        )
        assertEquals(1, claimed.size)

        // Worker 2 attempts to claim while lease is active
        val claimedByWorker2 = repository.claimPendingEvents(
            workerId = "worker-2",
            leaseToken = "token-2",
            leaseUntilEpochMs = System.currentTimeMillis() + 60_000L,
            batchSize = 10,
        )
        assertTrue(claimedByWorker2.isEmpty(), "Worker 2 must not claim an active leased event")
    }
}
