package com.elysium.server.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.lang.management.ManagementFactory
import java.util.UUID

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

@Serializable
data class PaginatedResponse<T>(
    val ok: Boolean,
    val data: List<T> = emptyList(),
    val total: Int = 0,
    val limit: Int = 50,
    val offset: Int = 0,
    val correlationId: String? = null,
)

@Serializable
data class VehicleResponse(
    val id: String,
    val vin: String? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val plate: String? = null,
    val color: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class CreateVehicleRequest(
    val vin: String? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val plate: String? = null,
    val color: String? = null,
)

private fun ApplicationCall.principalId(): String {
    return request.headers["Authorization"]
        ?.removePrefix("Bearer ")
        ?.takeIf { it.isNotBlank() }
        ?: "anonymous"
}

private fun ApplicationCall.correlationId(): String {
    return request.headers["X-Correlation-Id"] ?: UUID.randomUUID().toString()
}

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

    get("/metrics") {
        call.respond(
            mapOf(
                "uptimeMs" to ManagementFactory.getRuntimeMXBean().uptime,
                "availableProcessors" to Runtime.getRuntime().availableProcessors(),
                "freeMemoryBytes" to Runtime.getRuntime().freeMemory(),
                "totalMemoryBytes" to Runtime.getRuntime().totalMemory(),
                "maxMemoryBytes" to Runtime.getRuntime().maxMemory(),
            )
        )
    }
}

fun Route.configureV1BusinessRoutes() {
    route("/v1") {
        get("/me") {
            val principal = call.principalId()
            call.respond(
                ApiResponse(
                    ok = true,
                    data = mapOf("userId" to principal, "service" to "Elysium Platform OS", "status" to "AUTHORITATIVE_SERVER"),
                    correlationId = call.correlationId(),
                )
            )
        }

        route("/vehicles") {
            get {
                val principal = call.principalId()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
                val offset = call.request.queryParameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0

                call.respond(
                    PaginatedResponse<VehicleResponse>(
                        ok = true,
                        data = emptyList(),
                        total = 0,
                        limit = limit,
                        offset = offset,
                        correlationId = call.correlationId(),
                    )
                )
            }

            get("/{id}") {
                val vehicleId = call.parameters["id"]
                if (vehicleId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Boolean>(ok = false, error = ApiError("BAD_REQUEST", "Vehicle ID required"), correlationId = call.correlationId()),
                    )
                    return@get
                }
                call.respond(
                    ApiResponse(
                        ok = true,
                        data = VehicleResponse(id = vehicleId),
                        correlationId = call.correlationId(),
                    )
                )
            }

            post {
                val principal = call.principalId()
                val request = call.receive<CreateVehicleRequest>()
                val vehicleId = UUID.randomUUID().toString()

                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse(
                        ok = true,
                        data = VehicleResponse(
                            id = vehicleId,
                            vin = request.vin,
                            make = request.make,
                            model = request.model,
                            year = request.year,
                            plate = request.plate,
                            color = request.color,
                        ),
                        correlationId = call.correlationId(),
                    )
                )
            }

            put("/{id}") {
                val vehicleId = call.parameters["id"]
                if (vehicleId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Boolean>(ok = false, error = ApiError("BAD_REQUEST", "Vehicle ID required"), correlationId = call.correlationId()),
                    )
                    return@put
                }
                val request = call.receive<CreateVehicleRequest>()

                call.respond(
                    ApiResponse(
                        ok = true,
                        data = VehicleResponse(
                            id = vehicleId,
                            vin = request.vin,
                            make = request.make,
                            model = request.model,
                            year = request.year,
                            plate = request.plate,
                            color = request.color,
                        ),
                        correlationId = call.correlationId(),
                    )
                )
            }

            delete("/{id}") {
                val vehicleId = call.parameters["id"]
                if (vehicleId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Boolean>(ok = false, error = ApiError("BAD_REQUEST", "Vehicle ID required"), correlationId = call.correlationId()),
                    )
                    return@delete
                }

                call.respond(
                    ApiResponse(ok = true, data = mapOf("deleted" to vehicleId), correlationId = call.correlationId())
                )
            }
        }

        route("/rides") {
            get {
                call.respond(ApiResponse(ok = true, data = mapOf("dispatchEngine" to "ACTIVE"), correlationId = call.correlationId()))
            }
        }

        route("/market") {
            get {
                call.respond(ApiResponse(ok = true, data = mapOf("reverseAuction" to "ACTIVE"), correlationId = call.correlationId()))
            }
        }

        route("/active-ops") {
            get {
                call.respond(ApiResponse(ok = true, data = mapOf("status" to "REGISTRY_ACTIVE"), correlationId = call.correlationId()))
            }
        }
    }
}
