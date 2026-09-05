# TOW AUTHORITY CONVERGENCE & TRUTH MAPPING

**Date**: 2026-09-05
**Author**: Antigravity (World-Class Engine)
**Status**: ACTIVE / CANONICAL
**Context**: Resolution of competing Tow authorities between legacy `TowTruckDao` (`tow_truck_requests` in Room) and `TowJob` domain aggregate under `Elysium Fulfillment OS`.

---

## 1. Executive Summary

Prior to this convergence, two competing towing mechanisms coexisted in the codebase:
1. **Legacy Room Persistence**: `TowTruckDao` operating on `TowTruckRequestEntity` in the `tow_truck_requests` SQLite table, used by `ObdViewModel.createTowTruckRequest`, `takeTowTruckRequest`, `completeTowTruckRequest`, and `cancelTowTruckRequest`.
2. **Fulfillment Presentation Aggregate**: `TowCommandRepository` maintaining in-memory `TowJob` instances with `TowStateEngine` finite state transitions.

Per MEET Operating Charter ("More capability, fewer competing authorities; Todo en uno. Siempre a más, nunca a menos"), this divergence is resolved by **converging them into ONE authoritative aggregate**:
- `TowJob` remains the rich domain aggregate enforcing state machine transitions, custody record chains, and cryptographic evidence hashes.
- `TowCommandRepository` is backed directly by `TowTruckDao` (Room SQLite) for durable persistence across app lifecycles and process recreation.
- `ObdViewModel` delegates towing state and actions directly to `TowCommandRepository`.

---

## 2. Bidirectional Mapping Specification

### 2.1 `TowTruckRequestEntity` ↔ `TowJob`

| Field in `TowJob` | Source in `TowTruckRequestEntity` | Invariant / Conversion Rule |
|---|---|---|
| `jobId: UUID` | `requestId: String` | `runCatching { UUID.fromString(requestId) }.getOrElse { UUID.nameUUIDFromBytes(requestId.toByteArray()) }` |
| `customerId: UUID` | `userId: String` | `runCatching { UUID.fromString(userId) }.getOrElse { UUID.nameUUIDFromBytes(userId.toByteArray()) }` |
| `customerName: String` | Extracted or `"Cliente MEET"` | Fail-safe non-blank customer identity |
| `customerPhone: String` | `phone: String` | Exact string preserved |
| `vehicleVin: String?` | Extracted or null | Parsed from `vehicleInfo` if present |
| `vehicleSummary: String` | `vehicleInfo: String` | Brand, model, year description |
| `pickupLocation: GeoPoint` | `latitude: Double`, `longitude: Double` | Exact GPS coordinates |
| `pickupAddress: String` | `locationName: String` | Exact address string |
| `destinationLocation: GeoPoint?` | `destinationLatitude`, `destinationLongitude` | Nullable if destination was not specified |
| `destinationAddress: String?` | `destinationName: String?` | Exact address string or null |
| `state: TowState` | `status: String` | Mapping: `OPEN` → `REQUESTED`, `TAKEN` → `ASSIGNED`, `COMPLETED` → `COMPLETED`, `CANCELLED` → `CANCELLED`, `DISPUTED` → `DISPUTED` |
| `assignedOperatorName: String?`| `assignedDriverName: String?` | Preserved |
| `assignedOperatorPhone: String?`| `assignedDriverPhone: String?` | Preserved |
| `estimatedPrice: Money?` | `priceOffer: Double` | `if (priceOffer > 0) Money.colones(priceOffer.toLong()) else null` |
| `serverVersion: Long` | 1L (or incremented in Room) | Enforces CAS optimistic concurrency control |
| `createdAtEpochMs: Long` | `createdAt: Long` | Preserved epoch ms |
| `updatedAtEpochMs: Long` | `completedAt ?: createdAt` | Preserved epoch ms |

---

## 3. Concurrency Control & Evidence Invariants

1. **Compare-And-Swap (CAS)**:
   Any state transition command (`executeAction`) accepts `expectedServerVersion: Long? = null`. If the existing job's `serverVersion` does not match, a `TowCommandResult.ConcurrencyConflict` is returned.
2. **Cryptographic Custody Integrity**:
   Milestones `ConfirmLoaded` and `ConfirmDelivered` require valid non-empty evidence hashes (`secureEvidenceHash`, `deliveryEvidenceHash`). Synthetic or placeholder hashes (e.g. `SHA256:TOW-...`) are strictly forbidden.
3. **No Plausible Fabrication**:
   If an operator has not been assigned, `assignedUnit`, `assignedOperatorName`, `assignedOperatorRating`, and `operatorLocation` must strictly remain `null`.
