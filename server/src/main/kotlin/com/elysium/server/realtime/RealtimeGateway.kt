package com.elysium.server.realtime

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
data class ErpWelcomeMessage(
    val protocolVersion: Int = 1,
    val type: String = "WELCOME",
    val serverVersion: String = "1.0.0",
    val heartbeatIntervalMs: Long = 25000L,
)

object RealtimeSessionRegistry {
    private val sessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()
    private val channelSubscriptions = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()

    fun register(connectionId: String, session: DefaultWebSocketServerSession) {
        sessions[connectionId] = session
    }

    fun unregister(connectionId: String) {
        sessions.remove(connectionId)
        channelSubscriptions.forEach { (_, subscribers) ->
            subscribers.remove(connectionId)
        }
    }

    fun subscribe(channel: String, connectionId: String) {
        channelSubscriptions.computeIfAbsent(channel) { CopyOnWriteArrayList() }.addIfAbsent(connectionId)
    }

    fun activeConnectionCount(): Int = sessions.size
}

fun Route.configureRealtimeGateway() {
    val logger = LoggerFactory.getLogger("RealtimeGateway")

    webSocket("/v1/realtime") {
        val connectionId = java.util.UUID.randomUUID().toString()
        RealtimeSessionRegistry.register(connectionId, this)
        logger.info("New ERP/1 connection established: $connectionId")

        try {
            // Send WELCOME control frame
            val welcome = ErpWelcomeMessage()
            send(Frame.Text(Json.encodeToString(welcome)))

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    if (text.contains("PING")) {
                        send(Frame.Text("{\"type\":\"PONG\",\"timestamp\":${System.currentTimeMillis()}}"))
                    }
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.info("ERP/1 connection closed: $connectionId")
        } catch (e: Throwable) {
            logger.error("ERP/1 connection error on $connectionId: ${e.message}")
        } finally {
            RealtimeSessionRegistry.unregister(connectionId)
        }
    }
}
