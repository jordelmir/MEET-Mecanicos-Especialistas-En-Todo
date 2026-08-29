# Elysium Server Architecture

## 1. Overview & Monolithic Modularity
`elysium-server` is designed as a **Modular Monolith** in Kotlin/Ktor (JVM 21) running against PostgreSQL (Supabase managed data plane) and OpenTelemetry.

```
server/
├── app/            -> Ktor Application Entrypoint, DI, Server Routing, Lifecycle
├── domain/         -> Pure Business Rules & State Machines (Auth, Vehicle, Ride, Market, etc.)
├── application/    -> Use Case Interactors, Command/Query Handlers, Outbox Services
├── infrastructure/ -> Adapters for PostgreSQL, Supabase Auth, LiveKit, OTLP
├── api/            -> REST v1 Controllers, Error Taxonomy, Idempotency Middleware
├── realtime/       -> ERP/1 WebSocket Gateway, Channel Subscriptions, Resume Engine
├── workers/        -> Outbox Publisher, Dead-letter Monitor, Async Jobs
├── contracts/      -> OpenAPI v1 & ERP/1 JSON Schemas
└── observability/  -> Prometheus/OTLP Metrics, Structured JSON Tracing
```

## 2. API Endpoints
- Health: `GET /health/live`, `GET /health/ready`, `GET /health/version`, `GET /metrics`
- Business Surfaces:
  - `/v1/me`, `/v1/devices`
  - `/v1/vehicles`, `/v1/diagnostic-sessions`, `/v1/telemetry`
  - `/v1/communications`, `/v1/rides`, `/v1/market`, `/v1/service`, `/v1/work-orders`
  - `/v1/repair`, `/v1/parts`, `/v1/legal`, `/v1/trust`, `/v1/reputation`, `/v1/payments`, `/v1/files`
  - `/v1/realtime` (WebSocket Gateway endpoint for ERP/1)
