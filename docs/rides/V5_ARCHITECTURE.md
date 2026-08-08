# Viajes V5 — Architecture

```mermaid
flowchart TD
  UI["Compose UDF"] --> UC["Use cases por bounded context"]
  UC --> OUT["Command outbox"]
  OUT --> RPC["RPC actor-bound + expected version"]
  RPC --> PG["Postgres authority"]
  PG --> EVT["Append-only trip events"]
  PG --> PROJ["RLS projection"]
  PROJ --> ROOM["Room offline projection"]
  ROOM --> UI
```

La migración desde `ObdViewModel` será incremental hacia `booking`, `pricing`, `trip`, `safety`, `sharing`, `reputation`, `history`, `communication`, `support` y `payment`. No se crea una segunda máquina de estados.

El contrato tarifario conserva tasas, versión de rate card, métricas planeadas, estimado y política de paradas en la solicitud. Los comandos son idempotentes y el servidor resuelve el actor desde `auth.uid()`.
