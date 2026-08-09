# MEET Diagnostic Evidence Engine v1

Version: Android 4.14.0 (42)

## Objective

Preserve the diagnostic claim from the physical exchange through storage,
presentation and spatial guidance:

`vehicle → session → module → service → raw exchange → finding → status → evidence → spatial projection`

The engine must never infer that a fault was repaired merely because a code was
not returned. It may record `NOT_OBSERVED` only when the same identified module
successfully completed the authoritative coverage for that finding semantic.

## Implemented truth contracts

- SAE OBD buckets and UDS status semantics use explicit, non-null coverage.
  A successful UDS Service 19 read cannot prove SAE Mode 0A permanent coverage.
- Finding identity includes vehicle, protocol namespace, canonical module,
  code and observation semantic. ECM and TCM findings with the same code remain
  independent in Room and in the UI.
- Module identity canonicalizes functional and physical CAN addressing without
  discarding the raw target/response addresses.
- ELM text is decoded as CAN/ISO-TP PDUs before UDS service interpretation.
  Service bytes are authoritative only at PDU position zero.
- UDS negative responses preserve the requested service, NRC and operational
  semantics such as response-pending, busy/retry, security-required and
  request-out-of-range.
- Completeness only evaluates demonstrated responders/required coverage.
  Discovery candidates that do not exist on the vehicle cannot force a false
  `PARTIAL` result.
- Cached confirmed topology responders are compiled before generic discovery
  candidates. Quick Scan uses functional broadcast plus confirmed responders;
  Full Vehicle Scan adds discovery candidates.
- Scan progress is emitted as real domain events: scan start, module reading,
  finding discovered, module completion, cancellation and completion.
- Safe cancellation finishes the current exchange, restores adapter state and
  retains a report containing the evidence gathered so far.
- The transport has one exclusive physical owner for topology discovery,
  diagnostic scan, oscilloscope, active test and DTC clear. Non-owner commands
  are rejected while a lease is active.
- `EngineType.UNKNOWN` exposes only universal components. It does not silently
  assert an inline-four architecture.
- DTC-to-3D routing uses the DTC family and available module evidence to select
  engine, transmission, chassis/brakes, body/electrical or network scenes.
  A historical/permanent entry is no longer injected into the active 3D list.
- DTC cards retain and display module, response address, service and namespace
  when the finding came from the current evidence report.

## Persistence migration 49 → 50

`dtc_events` adds first-class diagnostic identity fields:

- `diagnosticNamespace`
- `moduleIdentity`
- `moduleName`
- `targetAddress`
- `responseAddress`
- `sourceService`
- `statusByte`
- `observationSemantic`

The composite index
`index_dtc_events_finding_identity` supports exact finding lookup. Existing rows
remain readable; legacy adoption is allowed only when module evidence is
compatible or unambiguous.

## Verification gates

Run from `android/`:

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

Run the repository byte-contract gate from the repository root:

```bash
bash tests/parity/ci-verify.sh
```

Device proof requires install, launch, foreground/process confirmation and a
crash-focused log review. Build success alone is not device proof.

### Android device proof — 2026-08-09

The exact v4.14.0 APK published in GitHub was installed on a real HONOR
`VER-N49`. Android reported package `com.elysium369.meet`, version code `42`
and version name `4.14.0`. A cold `MainActivity` launch completed successfully
in 1666 ms, remained the top resumed/focused activity with a live process, and
produced no fatal exception or ANR in the post-launch log review. Home and DTC
screens were visually inspected; Quick/Full Scan controls rendered correctly.

This proves the Android artifact launches and navigates. It does not prove a
physical ECU exchange because no OBD adapter was connected during the check.

## Explicitly deferred capability packs

KWP2000/OEM and OBDonUDS capability packs require source-backed, vehicle/OEM-
specific addressing and service definitions. This release provides protocol
namespaces and scan-plan boundaries but does not issue speculative OEM commands.
Manufacturer snapshots/extended data are likewise not labeled as supported
until a typed decoder and real ECU fixtures exist.

These are deliberate safety gates, not simulated coverage.
