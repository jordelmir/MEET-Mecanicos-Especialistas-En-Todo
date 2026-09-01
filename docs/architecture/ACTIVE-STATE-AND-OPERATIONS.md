# Active state and operation ownership

MEET separates durable user intent from work that is currently executing.
Neither screen composition nor navigation is an authority for either one.

## Active vehicle

`ActiveVehicleKernel` is the single application-scoped authority. The selected
vehicle pointer is stored in Room per owner principal and resolved against the
owner-scoped garage. Garage refresh, cloud failure, list reordering, route
changes, activity recreation and OBD disconnect do not change it.

Every transition carries one controlled reason. User selection and creation,
verified physical ECU binding, deletion, access revocation, owner boundary,
restore and legacy migration are distinguishable in structured telemetry. A
temporarily absent local vehicle retains its durable pointer; MEET does not
silently choose `firstOrNull()` as a replacement.

## Operation ownership matrix

| Owner | Route change | Configuration | Background | Process death | Reboot |
| --- | --- | --- | --- | --- | --- |
| `SCREEN_SCOPED` | stops | stops/recreates | stops | lost | lost |
| `SESSION_SCOPED` | survives | survives | policy-dependent | lost | lost |
| `VEHICLE_SCOPED` | survives | survives | survives while vehicle session exists | recover only from durable evidence | recover only when explicitly scheduled |
| `APPLICATION_SCOPED` | survives | survives | survives while process lives | lost unless separately persisted | lost unless separately persisted |
| `FOREGROUND_SERVICE_SCOPED` | survives | survives | survives with visible notification | Android/service restart policy | no implicit restart |
| `PERSISTENT_WORK_SCOPED` | survives | survives | survives | WorkManager recovery | WorkManager recovery |

`ActiveOperationsRegistry` exposes operation id, type, vehicle, start time,
state, progress, owner, recoverability, heartbeat and controlled error code.
Only the owning runtime may complete an operation. A composable `onDispose`,
back action or destination change must never do so.

## OBD authority path

```text
physical transport
  -> ELM / protocol negotiation
  -> singleton ObdSession
  -> telemetry and diagnostic evidence
  -> owner-scoped Room projections and StateFlow
  -> Scanner / Gauges / DTC / HUD / AI observers
```

The screens observe the session; they do not own the physical bus. The OBD
foreground service is manual-intent, observation-only and `START_NOT_STICKY`.
It survives navigation and activity recreation, but MEET does not claim that a
physical socket survives process death or reboot.

## Fuel Rewards lifecycle

Fuel Rewards can render wallet, sync and empty/offline states without loading
the optional Google scanner. The scanner client is created only after the user
presses the QR action. Initialization failure degrades that action and emits a
controlled error; it cannot terminate the Fuel Rewards destination.
