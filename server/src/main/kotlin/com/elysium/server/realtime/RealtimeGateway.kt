package com.elysium.server.realtime

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("RealtimeGateway")

private val clientCommandJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun Route.configureRealtimeGateway() {

    webSocket("/v1/realtime") {
        val connectionId = UUID.randomUUID().toString()
        val connection = RealtimeConnection(
            connectionId = connectionId,
            session = this,
            maxBufferSize = 1_000,
        )
        RealtimeSessionRegistry.register(connection)

        try {
            // Send WELCOME
            connection.sendControl(
                ServerControl.Welcome(
                    connectionId = connectionId,
                    heartbeatIntervalMs = 25_000L,
                    maxBufferSize = connection.maxBufferSize,
                )
            )
            logger.info("ERP/1 connection established: $connectionId")

            // Heartbeat coroutine
            val heartbeatJob = launch {
                while (true) {
                    delay(25_000)
                    try {
                        connection.sendControl(ServerControl.Pong())
                    } catch (_: Exception) {
                        break
                    }
                }
            }

            // Message loop
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val rawText = frame.readText()

                val command = try {
                    clientCommandJson.decodeFromString<ClientCommand>(rawText)
                } catch (e: Exception) {
                    connection.sendControl(
                        ServerControl.Error("INVALID_COMMAND", "Could not parse command: ${e.message}", retryable = false)
                    )
                    continue
                }

                when (command) {
                    is ClientCommand.Ping -> {
                        connection.sendControl(
                            ServerControl.Pong(
                                clientTimestamp = command.clientTimestamp,
                            )
                        )
                    }

                    is ClientCommand.Subscribe -> {
                        command.channels.forEach { channel ->
                            RealtimeSessionRegistry.subscribe(channel, connection)
                        }
                        connection.sendControl(
                            ServerControl.Subscribed(
                                channels = command.channels,
                                cursor = connection.cursor,
                            )
                        )
                        logger.info("$connectionId subscribed to ${command.channels}")
                    }

                    is ClientCommand.Unsubscribe -> {
                        command.channels.forEach { channel ->
                            RealtimeSessionRegistry.unsubscribe(channel, connectionId)
                        }
                        connection.sendControl(
                            ServerControl.Unsubscribed(channels = command.channels)
                        )
                        logger.info("$connectionId unsubscribed from ${command.channels}")
                    }

                    is ClientCommand.Resume -> {
                        val channels = command.channels ?: connection.subscriptions.toList()
                        connection.advanceCursor(command.lastCursor)

                        for (channel in channels) {
                            RealtimeSessionRegistry.replaySince(connection, command.lastCursor, channel)
                        }
                        logger.info("$connectionId resumed from cursor ${command.lastCursor} on $channels")
                    }
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.info("ERP/1 connection closed: $connectionId")
        } catch (e: Exception) {
            logger.error("ERP/1 connection error on $connectionId: ${e.message}")
        } finally {
            RealtimeSessionRegistry.unregister(connectionId)
        }
    }
}
