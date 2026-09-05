# ADR 0012: Elysium Fulfillment OS — Projection Architecture

- **Status**: Accepted & Implemented (Commit `48a4056c`)
- **Date**: 2026-09-05
- **Deciders**: Principal Software Architect, Staff Android Engineer, Security Engineer

---

## Context

MEET provides automotive mobility (Rides), vehicle recovery (Towing), roadside assistance (Battery, Tires, Fuel, Locksmith), repair (Mobile Mechanic, Workshops), and parts delivery.

Previously, user flows were either isolated in siloed screens or relied on duplicating screen implementations across services. Furthermore, there was a risk of confusing domain business logic with visual interaction patterns (e.g., trying to force all services into a `Ride` domain model or creating a "God aggregate" that conflates a ride passenger with a towed vehicle).

## Decision

We adopt the **"Same shell, different contract"** architectural paradigm:

1. **Rides = Interaction Grammar**: The request → configure → search → match → track → arrive → execute → verify → pay → review loop provides the shared mental model for users across all operational services.
2. **Each Service = Specialized Domain Authority**:
   - `Ride` maintains its own `RideState` and ride lifecycle authority.
   - `Tow` maintains its own `TowJob`, `TowCapabilities`, and `TowStateEngine` authority.
   - `Repair` maintains its own `WorkOrder` and diagnostic authority.
   - `Evidence` remains the single canonical authority for cryptographic file and photo attestations.
   - `Payments` and `Money` retain ledger and monetary integrity.
3. **Fulfillment OS is a Presentation Projection Layer**:
   - `FulfillmentProjection` is strictly a read model.
   - It is produced by domain adapters (`FulfillmentPresentationAdapter<T>`) such as `RideFulfillmentAdapter` and `TowFulfillmentAdapter`.
   - The shared UI shell (`FulfillmentScaffold`, `FulfillmentTimelineView`, `ProviderTrackingCard`) consumes projections and emits user intents (`FulfillmentUiAction`).
   - Mutations are routed through domain command gateways (`TowCommandRepository`, `RideCommandRepository`) and executed against server-authoritative state machines.

## Consequences

### Positive
- Zero duplication of UI boilerplate across services.
- Domain boundaries remain strictly isolated: vehicle custody is not confused with passenger carriage.
- High architectural scalability: new verticals (e.g., locksmith, battery, parts delivery) require only a domain model, command repository, and presentation adapter.
- Complete eradication of synthetic data: projections fail closed and honest if location or provider data is unavailable.

### Trade-offs & Mitigations
- Requires mapping between domain state machines and `FulfillmentPhase`. Handled via exhaustive `when` expressions in typed adapters.
