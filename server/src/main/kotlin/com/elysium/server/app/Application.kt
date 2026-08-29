package com.elysium.server.app

import com.elysium.server.api.configureHealthRoutes
import com.elysium.server.api.configureV1BusinessRoutes
import com.elysium.server.realtime.configureRealtimeGateway
import com.elysium.server.workers.OutboxWorker
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import java.time.Duration

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader("Authorization")
        allowHeader("Content-Type")
        allowHeader("Idempotency-Key")
        allowHeader("X-Correlation-Id")
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(30)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val outboxWorker = OutboxWorker()
    outboxWorker.start(this)

    routing {
        configureHealthRoutes()
        configureV1BusinessRoutes()
        configureRealtimeGateway()
    }
}
