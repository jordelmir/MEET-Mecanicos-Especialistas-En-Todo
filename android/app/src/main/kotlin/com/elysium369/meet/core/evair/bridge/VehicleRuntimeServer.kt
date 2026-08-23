package com.elysium369.meet.core.evair.bridge

import android.util.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VehicleRuntimeServer — High-performance, loopback-only (127.0.0.1) REST API for local AI tooling.
 *
 * Exposes typed, read-only automotive endpoints to MCP Servers, Antigravity Subagents,
 * and the Elysium CLI. Requires cryptographic token authentication via X-Elysium-Runtime-Token.
 */
@Singleton
class VehicleRuntimeServer @Inject constructor(
    private val facade: VehicleToolFacade,
) {
    private val TAG = "VehicleRuntimeServer"
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serverEngine: ApplicationEngine? = null
    private val isRunning = AtomicBoolean(false)

    val host: String = "127.0.0.1"
    // Keep the private loopback runtime distinct from the user-opt-in LiveLink port (8765).
    val port: Int = 18765

    // Ephemeral 32-byte cryptographic session token
    val sessionToken: String = generateSessionToken()

    init {
        start()
    }

    fun start() {
        if (isRunning.getAndSet(true)) return

        scope.launch {
            try {
                serverEngine = embeddedServer(CIO, port = port, host = host) {
                    routing {
                        // Public liveness probe
                        get("/v1/health") {
                            call.respondJson(HttpStatusCode.OK, """{"status":"UP","version":"1.0.0","runtime":"EVAIR"}""")
                        }

                        // Authenticated endpoints
                        get("/v1/vehicle/identity") {
                            val c = call
                            c.withAuth {
                                val identity = facade.identity()
                                c.respondJson(HttpStatusCode.OK, json.encodeToString(identity))
                            }
                        }

                        get("/v1/vehicle/snapshot") {
                            val c = call
                            c.withAuth {
                                val snapshot = facade.snapshot()
                                c.respondJson(HttpStatusCode.OK, json.encodeToString(snapshot))
                            }
                        }

                        get("/v1/diagnostics/dtcs") {
                            val c = call
                            c.withAuth {
                                val dtcs = facade.dtcs()
                                c.respondJson(HttpStatusCode.OK, json.encodeToString(dtcs))
                            }
                        }

                        get("/v1/diagnostics/freeze-frame") {
                            val c = call
                            c.withAuth {
                                val ff = facade.freezeFrame()
                                c.respondJson(HttpStatusCode.OK, json.encodeToString(ff))
                            }
                        }

                        get("/v1/diagnostics/readiness") {
                            val c = call
                            c.withAuth {
                                val readiness = facade.readiness()
                                c.respondJson(HttpStatusCode.OK, json.encodeToString(readiness))
                            }
                        }

                        get("/v1/diagnostics/mode06") {
                            val c = call
                            c.withAuth {
                                val mode06 = facade.mode06()
                                c.respondJson(HttpStatusCode.OK, json.encodeToString(mode06))
                            }
                        }

                        get("/v1/telemetry/window") {
                            val c = call
                            c.withAuth {
                                val pid = c.parameters["pid"]
                                if (pid.isNullOrBlank()) {
                                    c.respondError(HttpStatusCode.BadRequest, "Missing required query parameter: pid")
                                    return@withAuth
                                }
                                val seconds = c.parameters["seconds"]?.toIntOrNull() ?: 30
                                val window = facade.telemetryWindow(pid, seconds)
                                c.respondJson(HttpStatusCode.OK, json.encodeToString(window))
                            }
                        }

                        get("/v1/telemetry/features") {
                            val c = call
                            c.withAuth {
                                val pid = c.parameters["pid"]
                                if (pid.isNullOrBlank()) {
                                    c.respondError(HttpStatusCode.BadRequest, "Missing required query parameter: pid")
                                    return@withAuth
                                }
                                val seconds = c.parameters["seconds"]?.toIntOrNull() ?: 30
                                val features = facade.telemetryFeatures(pid, seconds)
                                c.respondJson(HttpStatusCode.OK, json.encodeToString(features))
                            }
                        }

                        get("/v1/diagnostics/anomalies") {
                            val c = call
                            c.withAuth {
                                val anomalies = facade.detectAnomalies()
                                c.respondJson(HttpStatusCode.OK, json.encodeToString(anomalies))
                            }
                        }

                        get("/v1/diagnostics/baseline") {
                            val c = call
                            c.withAuth {
                                val pid = c.parameters["pid"]
                                if (pid.isNullOrBlank()) {
                                    val baselines = facade.allBaselines()
                                    c.respondJson(HttpStatusCode.OK, json.encodeToString(baselines))
                                } else {
                                    val baseline = facade.baseline(pid)
                                    if (baseline != null) {
                                        c.respondJson(HttpStatusCode.OK, json.encodeToString(baseline))
                                    } else {
                                        c.respondError(HttpStatusCode.NotFound, "No baseline found for PID $pid")
                                    }
                                }
                            }
                        }

                        get("/v1/vehicle/health-summary") {
                            val c = call
                            c.withAuth {
                                val health = facade.healthSummary()
                                c.respondJson(HttpStatusCode.OK, json.encodeToString(health))
                            }
                        }
                    }
                }.start(wait = false)
                Log.i(TAG, "EVAIR Vehicle Runtime Server listening on http://$host:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind EVAIR runtime server to port $port: ${e.message}", e)
                isRunning.set(false)
            }
        }
    }

    fun stop() {
        if (isRunning.getAndSet(false)) {
            try {
                serverEngine?.stop(1000, 2000)
                Log.i(TAG, "EVAIR Vehicle Runtime Server stopped")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping runtime server: ${e.message}")
            }
        }
    }

    private suspend fun ApplicationCall.withAuth(block: suspend () -> Unit) {
        val authHeader = request.header("X-Elysium-Runtime-Token")
            ?: request.header("Authorization")?.removePrefix("Bearer ")

        if (authHeader == null || authHeader != sessionToken) {
            respondError(HttpStatusCode.Unauthorized, "Unauthorized: Invalid or missing X-Elysium-Runtime-Token")
            return
        }

        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request: ${e.message}", e)
            respondError(HttpStatusCode.InternalServerError, "Internal Server Error: ${e.message}")
        }
    }

    private suspend fun ApplicationCall.respondJson(status: HttpStatusCode, body: String) {
        respondText(body, ContentType.Application.Json, status)
    }

    private suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String) {
        val errorPayload = """{"error":true,"status":${status.value},"message":"${message.replace("\"", "\\\"")}"}"""
        respondText(errorPayload, ContentType.Application.Json, status)
    }

    private fun generateSessionToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
