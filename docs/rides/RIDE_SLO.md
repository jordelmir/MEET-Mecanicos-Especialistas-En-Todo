# ELYSIUM MOBILITY OS — SERVICE LEVEL OBJECTIVES (SLO) & OBSERVABILITY
**Status**: AUTHORITATIVE SPECIFICATION V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *No "Uber scale" claim without measurement. Metrics must never leak PII or high-cardinality identifiers.*

---

## 1. Latency & Performance Budgets

| Operation Path | p50 Target | p95 Target | p99 Target | Max Error Rate |
|---|---|---|---|---|
| **Fare Quote Generation** (`ride.quote`) | $\le 80 \text{ ms}$ | $\le 250 \text{ ms}$ | $\le 500 \text{ ms}$ | $< 0.1\%$ |
| **Spatial Driver Discovery** (`ride.discovery`) | $\le 45 \text{ ms}$ | $\le 120 \text{ ms}$ | $\le 300 \text{ ms}$ | $< 0.05\%$ |
| **Offer Submission** (`ride.offer`) | $\le 60 \text{ ms}$ | $\le 180 \text{ ms}$ | $\le 400 \text{ ms}$ | $< 0.1\%$ |
| **Concurrent Accept / Claim** (`ride.accept`) | $\le 50 \text{ ms}$ | $\le 150 \text{ ms}$ | $\le 350 \text{ ms}$ | $< 0.01\%$ |
| **Boarding PIN Verification** (`ride.pin_verify`) | $\le 40 \text{ ms}$ | $\le 100 \text{ ms}$ | $\le 250 \text{ ms}$ | $< 0.01\%$ |
| **Location Telemetry Ingestion** (`ride.location`) | $\le 30 \text{ ms}$ | $\le 80 \text{ ms}$ | $\le 200 \text{ ms}$ | $< 0.01\%$ |
| **Payment Preauth / Capture** (`payment.capture`) | $\le 350 \text{ ms}$ | $\le 900 \text{ ms}$ | $\le 2000 \text{ ms}$ | $< 0.5\%$ |

---

## 2. Distributed Tracing Standards

All network envelopes and logs MUST propagate the W3C trace context header tuple:
1. `traceId`: Globally unique request identifier (UUIDv4).
2. `correlationId`: Identifier linking all operations across the entire ride lifecycle (equal to `tripId`).
3. `causationId`: The specific command or event that caused the current execution.

---

## 3. Metric Cardinality & PII Restrictions

### FORBIDDEN Metric Labels (Never in Prometheus / Datadog / OpenTelemetry tags)
- User IDs (`passengerId`, `driverId`)
- Phone numbers or emails
- Vehicle identification numbers (VIN) or license plates
- Precise GPS coordinates (latitude/longitude)
- Trip UUIDs (must be in trace attributes, NEVER in metric dimension labels)

### ALLOWED Metric Dimensions (Low Cardinality)
- `jurisdiction` (`CR`, `CR_GAM`, `CR_ALAJUELA`)
- `status_code` (`200`, `400`, `409`, `500`)
- `error_code` (`ALREADY_ASSIGNED`, `VERSION_CONFLICT`, `NO_ROUTE`)
- `fare_mode` (`OPEN_BID`, `METERED`, `FIXED`)
- `network_type` (`WIFI`, `CELLULAR_4G`, `CELLULAR_5G`, `OFFLINE`)
