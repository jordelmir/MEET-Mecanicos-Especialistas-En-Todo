package com.elysium.server.app

import com.elysium.server.api.configureHealthRoutes
import com.elysium.server.api.configureV1BusinessRoutes
import com.elysium.server.database.DatabaseFactory
import com.elysium.server.realtime.configureRealtimeGateway
import com.elysium.server.workers.*
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

    val isProduction = System.getenv("ELYSIUM_ENV") == "production" || System.getenv("ENV") == "prod"
    val isTestEnv = System.getenv("ELYSIUM_ENV") == "test" || System.getProperty("elysium.env") == "test"

    install(CORS) {
        if (isProduction) {
            val allowedOrigins = System.getenv("ALLOWED_ORIGINS")?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
            if (!allowedOrigins.isNullOrEmpty()) {
                allowedOrigins.forEach { allowHost(it) }
            } else {
                allowHost("elysium.app", schemes = listOf("https"))
                allowHost("api.elysium.app", schemes = listOf("https"))
            }
        } else {
            anyHost()
        }
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
        // Directive 49: Bound max frame size to 1MB to prevent memory exhaustion DoS
        maxFrameSize = 1L * 1024L * 1024L
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

    // ── OUTBOX WORKER PRODUCTION WIRING (Master Order Directives 47 & 48) ──
    val (outboxRepo, eventPublisher) = if (isTestEnv) {
        environment.log.info("Running in explicit TEST environment: using InMemoryOutboxRepository")
        Pair(InMemoryOutboxRepository(), ProductionDomainEventPublisher())
    } else {
        val dbUrl = System.getenv("DATABASE_URL")
            ?: System.getenv("POSTGRES_URL")
            ?: System.getenv("SUPABASE_DB_URL")
            ?: if (isProduction) "" else "jdbc:postgresql://localhost:5432/elysium_db"

        if (isProduction && dbUrl.isBlank()) {
            throw IllegalStateException("CRITICAL: DATABASE_URL not provided in PRODUCTION environment. Fail-closed law halts initialization.")
        }

        val config = DatabaseFactory.DatabaseConfig(
            jdbcUrl = dbUrl,
            user = System.getenv("DATABASE_USER") ?: System.getenv("POSTGRES_USER") ?: "postgres",
            pass = System.getenv("DATABASE_PASSWORD") ?: System.getenv("POSTGRES_PASSWORD") ?: "postgres",
            maxPoolSize = System.getenv("DB_POOL_MAX_SIZE")?.toIntOrNull() ?: 10,
            minIdle = 2,
            connectionTimeoutMs = 5000L,
            validationTimeoutMs = 2500L,
            idleTimeoutMs = 60000L,
            maxLifetimeMs = 1800000L,
        )

        try {
            val dataSource = DatabaseFactory.createDataSource(config)
            environment.log.info("Production Outbox initialized with PostgreSQL connection pool.")
            Pair(PostgresOutboxRepository(dataSource), ProductionDomainEventPublisher())
        } catch (e: Exception) {
            if (isProduction) {
                environment.log.error("FATAL: PostgreSQL pool connection failed in PRODUCTION: ${e.message}", e)
                throw IllegalStateException("CRITICAL: Authoritative PostgreSQL database failed to initialize in PRODUCTION environment. Fail-closed law prevents starting with volatile in-memory outbox.", e)
            } else {
                environment.log.warn("Database connection pool failed to start in non-production, falling back to local quarantine: ${e.message}")
                Pair(InMemoryOutboxRepository(), ProductionDomainEventPublisher())
            }
        }
    }

    val outboxWorker = OutboxWorker(
        repository = outboxRepo,
        publisher = eventPublisher,
    )
    outboxWorker.start(this)

    routing {
        configureHealthRoutes()
        configureV1BusinessRoutes()
        configureRealtimeGateway()
    }
}
