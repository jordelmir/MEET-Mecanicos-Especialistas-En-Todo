package com.elysium.server.workers

import com.elysium.server.realtime.RealtimeEnvelope
import com.elysium.server.realtime.RealtimeSessionRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

/**
 * ══════════════════════════════════════════════════════════════════════
 *  P R O D U C T I O N   D O M A I N   E V E N T   P U B L I S H E R
 *  ──────────────────────────────────────────────────────────────
 *  Production implementation of EventPublisher adhering to
 *  Master Order Genesis §6, §8, §59, §60:
 *  - Distributes committed domain events to Realtime Gateway channels
 *    (ERP/1 protocol for mobile clients, drivers, and operations).
 *  - Routes events by domain, aggregate, and target principal.
 *  - Returns typed EventPublishResult (Success, RetryableError, TerminalError).
 * ══════════════════════════════════════════════════════════════════════
 */
class ProductionDomainEventPublisher(
    private val sessionRegistry: RealtimeSessionRegistry = RealtimeSessionRegistry,
    private val externalWebhookUrl: String? = null,
) : EventPublisher {

    private val logger = LoggerFactory.getLogger(ProductionDomainEventPublisher::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun publish(event: OutboxEventRecord): EventPublishResult {
        return try {
            val parsedPayload: JsonElement? = try {
                if (event.payload.isNotBlank()) json.parseToJsonElement(event.payload) else null
            } catch (e: Exception) {
                logger.warn("Malformed JSON payload in outbox event ${event.eventId}: ${e.message}")
                null
            }

            val envelope = RealtimeEnvelope(
                protocolVersion = 1,
                eventId = event.eventId,
                eventType = event.eventType,
                occurredAt = System.currentTimeMillis(),
                aggregateType = event.aggregateType,
                aggregateId = event.aggregateId,
                aggregateVersion = event.aggregateVersion,
                sequence = sessionRegistry.nextSequence(),
                correlationId = event.correlationId,
                payload = parsedPayload,
            )

            // 1. Broadcast to domain channel (e.g., "mobility", "automotive", "safety")
            sessionRegistry.broadcast(event.sourceDomain, envelope)

            // 2. Broadcast to aggregate channel (e.g., "ride:ride-123")
            val aggregateChannel = "${event.aggregateType.lowercase()}:${event.aggregateId}"
            sessionRegistry.broadcast(aggregateChannel, envelope)

            // 3. Broadcast to targeted principal channel if directed
            event.targetPrincipalId?.let { principalId ->
                sessionRegistry.broadcast("principal:$principalId", envelope)
            }

            logger.debug(
                "Published event {} [{}] across channels: {}, {}",
                event.eventId,
                event.eventType,
                event.sourceDomain,
                aggregateChannel
            )

            EventPublishResult.Success
        } catch (e: Exception) {
            logger.error("Failed to publish event {}: {}", event.eventId, e.message, e)
            EventPublishResult.RetryableError("PUBLICATION_DISPATCH_FAILURE: ${e.message}")
        }
    }
}
