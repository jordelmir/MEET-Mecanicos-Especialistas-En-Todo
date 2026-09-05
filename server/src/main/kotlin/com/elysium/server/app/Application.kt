package com.elysium.server.app

import com.elysium.server.api.configureHealthRoutes
import com.elysium.server.api.configureV1BusinessRoutes
import com.elysium.server.realtime.configureRealtimeGateway
import com.elysium.server.workers.OutboxWorker
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
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
            encodeDefaults = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader("Authorization")
        allowHeader("Content-Type")
        allowHeader("Idempotency-Key")
        allowHeader("X-Correlation-Id")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(30)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val correlationId = call.request.headers["X-Correlation-Id"]
            when (cause) {
                is IllegalArgumentException -> {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "ok" to false,
                        "error" to mapOf("code" to "BAD_REQUEST", "message" to (cause.message ?: "Invalid request")),
                        "correlationId" to correlationId,
                    ))
                }
                is SecurityException -> {
                    call.respond(HttpStatusCode.Unauthorized, mapOf(
                        "ok" to false,
                        "error" to mapOf("code" to "UNAUTHORIZED", "message" to "Authentication required"),
                        "correlationId" to correlationId,
                    ))
                }
                else -> {
                    call.application.environment.log.error("Unhandled exception", cause)
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "ok" to false,
                        "error" to mapOf("code" to "INTERNAL_ERROR", "message" to "An internal error occurred"),
                        "correlationId" to correlationId,
                    ))
                }
            }
        }
    }

    val outboxWorker = OutboxWorker()
    outboxWorker.start(this)

    routing {
        configureHealthRoutes()
        configureV1BusinessRoutes()
        configureRealtimeGateway()
    }
}
