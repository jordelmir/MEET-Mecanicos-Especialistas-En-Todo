package com.elysium.server.realtime

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-connection state for the ERP/1 realtime protocol.
 * Tracks cursor, channel subscriptions, and a bounded event buffer for replay.
 */
class RealtimeConnection(
    val connectionId: String,
    val session: DefaultWebSocketServerSession,
    val maxBufferSize: Int = 1_000,
) {
    @Volatile
    var cursor: Long = 0L
        private set

    val subscriptions = ConcurrentHashMap.newKeySet<String>()

    private val buffer = ArrayDeque<RealtimeEnvelope>(maxBufferSize)
    private val bufferMutex = Mutex()

    suspend fun advanceCursor(newCursor: Long) {
        cursor = newCursor
    }

    suspend fun bufferEvent(envelope: RealtimeEnvelope) {
        bufferMutex.withLock {
            if (buffer.size >= maxBufferSize) {
                buffer.removeFirst()
            }
            buffer.addLast(envelope)
        }
    }

    suspend fun getEventsSince(fromCursor: Long): List<RealtimeEnvelope> {
        return bufferMutex.withLock {
            buffer.filter { it.sequence > fromCursor }
        }
    }

    suspend fun getEventsInRange(fromCursor: Long, toCursor: Long): List<RealtimeEnvelope> {
        return bufferMutex.withLock {
            buffer.filter { it.sequence in (fromCursor + 1)..toCursor }
        }
    }

    suspend fun sendEnvelope(envelope: RealtimeEnvelope) {
        try {
            val json = Json.encodeToString(envelope)
            session.send(Frame.Text(json))
        } catch (e: Exception) {
            LoggerFactory.getLogger("RealtimeConnection").warn("Failed to send to $connectionId: ${e.message}")
        }
    }

    suspend fun sendControl(control: ServerControl) {
        try {
            val json = Json.encodeToString(control)
            session.send(Frame.Text(json))
        } catch (e: Exception) {
            LoggerFactory.getLogger("RealtimeConnection").warn("Failed to send control to $connectionId: ${e.message}")
        }
    }
}

/**
 * Global session registry. Manages all active WebSocket connections,
 * channel subscriptions, and sequence numbering.
 */
object RealtimeSessionRegistry {
    private val logger = LoggerFactory.getLogger("RealtimeSessionRegistry")

    private val connections = ConcurrentHashMap<String, RealtimeConnection>()
    private val channelConnections = ConcurrentHashMap<String, ConcurrentHashMap<String, RealtimeConnection>>()

    private val sequenceCounter = AtomicLong(0L)

    fun nextSequence(): Long = sequenceCounter.incrementAndGet()

    fun register(connection: RealtimeConnection) {
        connections[connection.connectionId] = connection
        logger.info("Registered connection: ${connection.connectionId} (total: ${connections.size})")
    }

    fun unregister(connectionId: String) {
        val conn = connections.remove(connectionId) ?: return
        conn.subscriptions.forEach { channel ->
            channelConnections[channel]?.remove(connectionId)
        }
        logger.info("Unregistered connection: $connectionId (total: ${connections.size})")
    }

    fun subscribe(channel: String, connection: RealtimeConnection) {
        connection.subscriptions.add(channel)
        channelConnections.computeIfAbsent(channel) { ConcurrentHashMap() }[connection.connectionId] = connection
        logger.debug("Connection ${connection.connectionId} subscribed to $channel")
    }

    fun unsubscribe(channel: String, connectionId: String) {
        val conn = connections[connectionId] ?: return
        conn.subscriptions.remove(channel)
        channelConnections[channel]?.remove(connectionId)
        logger.debug("Connection $connectionId unsubscribed from $channel")
    }

    fun getChannelSubscribers(channel: String): List<RealtimeConnection> {
        return channelConnections[channel]?.values?.toList() ?: emptyList()
    }

    fun getConnection(connectionId: String): RealtimeConnection? = connections[connectionId]

    fun activeConnectionCount(): Int = connections.size

    fun activeChannelCount(): Int = channelConnections.size

    /**
     * Broadcast an event to all subscribers of the given channel.
     * Buffer the event in each subscriber's replay buffer.
     * Detect gaps if the subscriber's cursor is behind.
     */
    suspend fun broadcast(channel: String, envelope: RealtimeEnvelope) {
        val subscribers = getChannelSubscribers(channel)
        if (subscribers.isEmpty()) return

        for (conn in subscribers) {
            // Gap detection: if client cursor is behind the event sequence - buffer size
            val gapThreshold = maxOf(0L, envelope.sequence - conn.maxBufferSize.toLong())
            if (conn.cursor > 0 && conn.cursor < gapThreshold) {
                conn.sendControl(
                    ServerControl.GapDetected(
                        expectedCursor = conn.cursor,
                        receivedCursor = envelope.sequence,
                        channel = channel,
                    )
                )
            }

            conn.bufferEvent(envelope)
            conn.sendControl(ServerControl.Event(envelope))
        }
    }

    /**
     * Replay events to a connection since a given cursor.
     */
    suspend fun replaySince(connection: RealtimeConnection, fromCursor: Long, channel: String) {
        val events = connection.getEventsSince(fromCursor)
        if (events.isEmpty()) return

        val toCursor = events.last().sequence
        connection.sendControl(ServerControl.ReplayStart(fromCursor, toCursor, channel))

        for (event in events) {
            connection.sendControl(ServerControl.Event(event))
        }

        connection.sendControl(ServerControl.ReplayEnd(fromCursor, toCursor, channel, events.size))
    }
}
