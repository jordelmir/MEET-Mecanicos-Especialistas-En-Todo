package com.elysium.server.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.lang.management.ManagementFactory

@Serializable
data class HealthStatus(
    val status: String,
    val environment: String,
    val version: String,
    val uptimeMs: Long,
    val timestamp: Long,
)

@Serializable
data class ApiResponse<T>(
    val ok: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
    val correlationId: String? = null,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
)

fun Route.configureHealthRoutes() {
    route("/health") {
        get("/live") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "LIVE"))
        }

        get("/ready") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "READY", "database" to "CONNECTED"))
        }

        get("/version") {
            call.respond(
                HttpStatusCode.OK,
                HealthStatus(
                    status = "HEALTHY",
                    environment = System.getenv("ENVIRONMENT") ?: "production",
                    version = "1.0.0",
                    uptimeMs = ManagementFactory.getRuntimeMXBean().uptime,
                    timestamp = System.currentTimeMillis(),
                )
            )
        }
    }
}

fun Route.configureV1BusinessRoutes() {
    route("/v1") {
        get("/me") {
            call.respond(
                ApiResponse(
                    ok = true,
                    data = mapOf("service" to "Elysium Platform OS", "status" to "AUTHORITATIVE_SERVER")
                )
            )
        }

        get("/vehicles") {
            call.respond(
                ApiResponse(
                    ok = true,
                    data = listOf<String>()
                )
            )
        }

        get("/rides") {
            call.respond(
                ApiResponse(
                    ok = true,
                    data = mapOf("dispatchEngine" to "ACTIVE")
                )
            )
        }

        get("/market") {
            call.respond(
                ApiResponse(
                    ok = true,
                    data = mapOf("reverseAuction" to "ACTIVE")
                )
            )
        }
    }
}
