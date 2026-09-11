# Mobility hardening — execution evidence and remaining work

This is an in-progress execution record, not a production certification.

## AUDIT PASS 0 — REAL HEAD & EXECUTION BASELINE

- Repository: https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo
- Baseline branch: main; working branch: fix/mobility-product-hardening-20260909.
- HEAD: 2f0aa0b45e9309c942266cb844a49a935ee20247, Merge Play Store production gate into main.
- Fetch completed. Sync reported ALREADY_INTEGRATED. Existing local changes in MainActivity, MeetDestinations, RideLivenessDialog, RideServiceScreen and four new history/liveness files were preserved.
- Platform: macOS; Java Homebrew 17.0.18; Node 26.7.0; Gradle wrapper 9.4.1; Android SDK /Users/jordelmirsdevhome/Library/Android/sdk.
- Baseline Android compile and RideCancellationTest passed (31 seconds). This predates hardening changes.
- Connected ADB device observed; no physical verification of this patch yet.
- Native ephemeral PostgreSQL used for local authority suites; production DB and PSP untouched.
- Additional money/domain changes appeared in the shared tree during execution. Their authorship and full compatibility are not claimed by this record.

## Actual execution and duplication map (E1 OBSERVED)

Production navigation ride_service → RideServiceScreen → ObdViewModel → RideCommandRepository → Room ride_command_outbox → RideCommandSyncWorker → RideCommandGateway → ride RPCs → PostgreSQL → snapshot/realtime reconciliation → RideDao → activeRideRequest → Compose.

The active tracking destination observes activeRideRequest but constructs a presentation projection in MainActivity. It does not yet bind a driver location stream. The patch marks location absent/old rather than claiming live tracking.

mobility.* contains command gateway, domain money/payment/ledger/pricing and separate server procedures. These are not proof that production ride.* screens call those procedures. Experimental passenger/driver routes remain separate. Full convergence into mobility.* is outstanding; this patch does not add a third stack.

## Findings and remediation order

| ID | Severity | Observed defect | Patch / residual verification |
|---|---|---|---|
| F-001 | P0 | Passenger Cancel only selected request; dialogs immediately closed trip | Cancellation dialog wired to durable command and projection; final APK test pending |
| F-002 | P0 | Version-zero request could not cancel | Atomic unsent-publication suppression and durable in-flight cancellation intent; five SQLite SQL tests pass; process-death E2E pending |
| F-003 | P0 | Missing PSP secret skipped signature verification | Fail-closed webhook, signed timestamp, explicit financial evidence; 19 request tests pass |
| F-004 | P0 | Provider capture NULL comparisons and event ownership race | New migration 20260910010000; ephemeral PostgreSQL regression suite; real PSP pending |
| F-005 | P0 | Shared HOME/WORK persisted across accounts | Owner-scoped store, ignores unowned legacy data, driver HOME namespace; Android cross-account UI pending |
| F-006 | P1 | Map cancellation swallowed; mutex held across network | Rethrow cancellation, short metadata locks, half-open probe handling; coroutine regression tests added |
| F-007 | P0 | Tracking payment/rating/PTT could simulate success | Remove simulated tracking actions; explicit nullable capabilities and state gates; real integrations still unavailable |
| F-008 | P0 | Internal vehicle ID displayed as plate | Caller now supplies unavailable plate; canonical vehicle identity still needs integration |
| F-009 | P1 | Trust/ETA/live claims lacked source evidence | Remove unverified trust/arrival estimates; timestamp/provenance based GPS freshness |
| F-010 | P1 | Top-up recipient fallback and disguised proof extension | Remote-policy-required control, JPEG/PNG/PDF header and size checks; server malware validation remains outside this patch |

Each finding above is OBSERVED E1 at baseline. A patch is not PHYSICALLY_VERIFIED. No finding is closed at E4.

Critical invariants: request != completion; PostgreSQL decides remote state; an unsent local cancellation requires atomic proof of no publication attempt; ambiguous absence remains pending; one event captures at most one payment; owner-bound personal data; old GPS is never live; missing policy cannot authorize a transfer.

Dependency graph: baseline → active-flow mapping → cancellation/outbox + PSP/ledger + account isolation → tracking truth → compile/unit/SQL → APK/process-death/cross-account → real PSP/provider → convergence/release.

## Execution order

STEP 001 — Baseline and preserve WIP. Files: repository metadata/docs. Migration: none. Tests: existing compile/cancellation. Rollback: none, read-only. Dependency: none.

STEP 002 — Authoritative cancellation. Files: RideServiceScreen, ObdViewModel, RideSafetyPanels, RideCommandRepository, RideCommandOutboxDao, RideCommandSyncWorker. Migration: none (existing Room fields). Code: atomic outbox suppression, durable cancellation intent and pending projection UI. Tests: verify-local-cancel-intent.py + Android tests. Verification: process death/reconcile still required. Rollback: revert only this patch; preserve pending commands and never reinterpret them as success. Dependency: 001.

STEP 003 — Payment ingress and atomic evidence. Files: mobility-provider-webhook handler/index, new capture migration and tests. Tests: provider-webhook.test.ts, V11 PostgreSQL suite. Verification: PSP real credentials/events still required. Rollback: forward migration; never reopen unsigned ingress. Dependency: 001.

STEP 004 — Maps/privacy/tracking/proofs. Files: map providers/store, tracking projection, driver UI, proof validator. Tests: dedicated coroutine/store/presentation/proof tests. Verification: current complete Android build and two-account APK testing. Rollback: scoped revert without restoring unsupported trust claims or cross-account reads. Dependency: 002/003.

STEP 005 — Production closure (not completed). Connect trip-scoped GPS, canonical payment/rating, secure notification routing, complete schedule execution, converge stacks, replay ALL migrations, run all 15 master scenarios, install and inspect final APK. No production readiness claim before these gates.

## Historical lead revalidation

FIXED below means source patched, not physical feature completion.

| Lead | Status | Evidence / remaining work |
|---|---|---|
| 1 Cancel selects only | FIXED | PassengerDashboard cancellationTarget/dialog |
| 2 Premature close | FIXED | AuthoritativeRideCancellationDialog observes projection |
| 3 Silent cancellation returns | PARTIALLY_FIXED | ObdViewModel feedback; typed terminal UI needs further coverage |
| 4 Version-zero cancellation | PARTIALLY_FIXED | Atomic local suppression + worker; full crash test pending |
| 5 Tracking no-op callbacks | FIXED | Required/nullable explicit capabilities |
| 6 Legacy production stack | STILL_PRESENT | ride_service still ride.* |
| 7 Collapsed states | PARTIALLY_FIXED | Tracking reads serverState; other consumers pending |
| 8 Driver GPS missing | STILL_PRESENT | Honest unavailable UI; no live binding |
| 9 Reconstructed fare | PARTIALLY_FIXED | Tracking hides synthetic breakdown; legacy model remains |
| 10 Hardcoded currency symbol | PARTIALLY_FIXED | Tracking formatter; global monetary changes need reconciliation |
| 11 Experimental driver CRC | UNABLE_TO_VERIFY | Not fully audited |
| 12 Passenger HOME/WORK ownership | PARTIALLY_FIXED | Store isolated, physical account-switch pending |
| 13 Driver HOME ownership | PARTIALLY_FIXED | Namespace/reset added, physical test pending |
| 14 Unsupported trust claims | FIXED | Tracking driver profile claims removed |
| 15 ID as plate | FIXED | MainActivity supplies null |
| 16 PAY/RATE gates | PARTIALLY_FIXED | Tracking gated, broader UI not certified |
| 17 Rating local success | PARTIALLY_FIXED | Removed from tracking; canonical callback unavailable |
| 18 Payment local success | PARTIALLY_FIXED | Removed from tracking; real PSP pending |
| 19 Hardcoded SINPE | PARTIALLY_FIXED | Tracking fake method removed; market configuration pending |
| 20 Call/chat no-op | PARTIALLY_FIXED | Tracking explicitly unavailable; canonical comms not audited |
| 21 PTT claims | PARTIALLY_FIXED | Removed local tracking transmission; other voice screens not audited |
| 22 Generic notification destination | STILL_PRESENT | RideNotificationCoordinator MainActivity intent |
| 23 Notification dedupe ownership | PARTIALLY_FIXED | Coordinator namespace now owner-bound; physical switch/token lifecycle pending |
| 24 PDF picker mismatch | FIXED | OpenDocument accepts JPEG/PNG/PDF |
| 25 False .jpg extension | FIXED | Header-derived extension and gateway validation |
| 26 Financial fallback | PARTIALLY_FIXED | Top-up destination fallback removed; full policy use audit pending |
| 27 Arbitrary active selection | UNABLE_TO_VERIFY | Full invariant audit pending |
| 28 Durable schedules | UNABLE_TO_VERIFY | Not tested with Android process dead |
| 29 Upcoming state omission | PARTIALLY_FIXED | ScheduledRideDao now includes DRIVER_MATCHED/ACTIVE; Room/device verification pending |
| 30 Cancellation trips breaker | FIXED | Cooperative cancellation rethrow |
| 31 Mutex around network | FIXED | I/O outside metadata mutex |
| 32 PSP production proof | STILL_PRESENT | Local contract only |
| 33 Geodesic routing | PARTIALLY_FIXED | Road providers observed; all fallback labels not verified |
| 34 Stack duplication | STILL_PRESENT | Convergence unfinished |

## Product comparison (FACT vs recommendation)

FACT: Uber Reserve documents upcoming-trip management and reservation-dependent cancellation conditions: https://help.uber.com/en-GB/riders/article/what-are-reservations?nodeId=71708d67-bbac-4dda-9d32-53c2509bdd1b . DiDi Australia documents SOS and live trip sharing: https://web.didiglobal.com/au/safety/ . inDrive describes choice of price/driver and its safety pact: https://indrive.com/company . These are market-specific product facts, not verified MEET capabilities. Recommendation for MEET: expose authoritative policy and evidence-based capabilities per market; do not copy their policies or claim matching infrastructure.

## Coverage / scorecard

Scores are conservative evidence-coverage lower bounds, not measured feature quality. 0 means not demonstrated in this pass, not proven absent. No category has E4 evidence.

| Category | Coverage | Score /10 | Evidence and remaining blocker |
|---|---|---:|---|
| Architecture | PARTIAL | 3 | Production flow mapped; duplicate stacks remain |
| Server Authority | PARTIAL | 6 | Local SQL gates; remote schema not inspected |
| Android Wiring | PARTIAL | 3 | Cancellation/tracking patched; final compile/device pending |
| Ride Lifecycle | PARTIAL | 4 | Command/Room path; all actors not exercised |
| Cancellation | PARTIAL | 4 | Five actual SQLite tests; process death/race E2E pending |
| Driver Dispatch | PARTIAL | 5 | Local 100-way single-winner test; Android E2E pending |
| Scheduled Rides | NOT_INSPECTED | 0 | Durable execution not demonstrated |
| Live Tracking | PARTIAL | 2 | Truth gating; GPS binding missing |
| Maps/Routing | PARTIAL | 4 | Provider coroutine fixes; physical routing pending |
| Money Model | PARTIAL | 2 | Shared monetary changes require full verification |
| Pricing | PARTIAL | 2 | Fare presentation inspected; quote chain incomplete |
| Payments | PARTIAL | 4 | 19 ingress tests; real PSP missing |
| Ledger | PARTIAL | 5 | SQL balance/atomic tests; production grants pending |
| Security | PARTIAL | 4 | Signature gap patched; broad review unfinished |
| RLS/AuthZ | PARTIAL | 5 | Local authorization gates; production grants unknown |
| Privacy | PARTIAL | 3 | Saved places patched; notifications outstanding |
| Concurrency | PARTIAL | 5 | Local assignment/capture/SQLite; full cancel-accept scenario pending |
| Idempotency | PARTIAL | 4 | Local command keys and event tests; all commands not covered |
| Offline/Recovery | PARTIAL | 2 | Durable intent code; physical crash test missing |
| Realtime/Reconciliation | PARTIAL | 2 | Snapshot worker inspected; out-of-order E2E missing |
| Notifications | PARTIAL | 1 | Generic target/global dedupe still present |
| Safety | PARTIAL | 2 | Unsupported claims removed; real emergency chain absent |
| Observability | PARTIAL | 2 | Worker events observed; operating dashboards not verified |
| Testing | PARTIAL | 4 | Targeted unit/SQL; full 15-scenario suite unfinished |
| CI/CD | PARTIAL | 1 | Local gates only, no published artifact |
| Globalization | PARTIAL | 2 | Currency presentation patch; markets/timezones not verified |
| UX/Product Truth | PARTIAL | 3 | Fake tracking actions removed; broader screens remain |

Chat and voice: PARTIAL. Room/gateways: PARTIAL. Android UI/ViewModels: PARTIAL. PostgreSQL migration subset: INSPECTED; complete from-zero replay: NOT_EXECUTED. Production deployment: NOT_EXECUTED. APK E2E: NOT_EXECUTED.

## Verdict

NO-GO. This mission is not complete. No production deployment, final APK certification or complete convergence is claimed.


## Confirmed execution results (2026-09-10)

- Commit 6961030c: signed capture ingress and atomic event ownership. Local only; no remote deployment.
- `python3 tests/mobility/verify-local-cancel-intent.py`: 5 passed, production Room SQL executed in SQLite.
- `npx vitest run tests/mobility/provider-webhook.test.ts`: 19 passed.
- `bash tests/mobility/verify-mobility-v11-public-launch.sh`: passed, including one-winner concurrent provider-event capture and unambiguous RPC overload check. This suite replays a selected dependency set, not every repository migration.
- `bash tests/mobility/verify-mobility-v12-provider-operations.sh`: passed on ephemeral PostgreSQL.
- Android compile and 21 targeted unit tests: PASS on current source. Earlier post-patch attempt failed generated Room/interface mismatch; it is not counted as a pass.
- Full TS/Kotlin parity script: PASS, identical fixture output.
- Callback inventory: 55 syntactic empty-default declarations, not 55 proven reachable defects.

- Added local backup/restore drill: pg_dump custom backup → isolated restored database → equal trip/payment/ledger counts, balanced entries, no missing transaction references, one capture event. PASS. This does not establish production PITR, RPO or RTO.
- Device reconnected as Honor VER_N49; keyguard showing=false. Installation/runtime verification pending new APK.
- New control-plane annex accepted; inventory and dispute-authority remediation in progress. No certification of risk, kill-switch wiring, chargebacks, payouts or 24/7 operations.

## Follow-up verification (2026-09-11)

- `RideLifecyclePersistenceContractTest`: PASS after making cancellation's command type explicit at the authoritative enqueue boundary.
- `RideRemoteProjectionMapperTest`: added coverage for reassignment identity clearing and null payment mapping to `UNKNOWN`; the mapper now clears stale driver/vehicle identity when assignment changes and never fabricates `CASH`.
- ADB is currently empty after the Honor Wi-Fi transport dropped; APK install had previously returned `Success`, but `am start`, foreground process, crash log and Viajes UI traversal remain unproven until the device is visible again.

## Device and regression closure (2026-09-11)

- Honor `VER_N49` reappeared over ADB. The rebuilt APK installed successfully with `adb install -r -d`.
- `am start -W -n com.elysium369.meet/.MainActivity`: `Status: ok`, `LaunchState: COLD`, `TotalTime: 1972 ms`; process `15175` was alive and `mFocusedApp` resolved to `MainActivity`.
- Recent logcat contained no `FATAL EXCEPTION` or `AndroidRuntime` crash for the launch. UI Automator exposed the real Home surface (`ELYSIUM VANGUARD`, OBD state, vehicle, actions).
- Regression command `:app:testDebugUnitTest --tests 'com.elysium369.meet.ride.*' --tests 'com.elysium369.meet.mobility.*'`: PASS, `BUILD SUCCESSFUL`.
- Viajes interaction beyond Home remains unclaimed because the current account/device state did not expose a visible Viajes control in the inspected UI tree.

## New hardening pass (2026-09-11)

- Unified Activity now formats ride minor units through the canonical `Money` value object and derives activity state from `RideState`, including `ARRIVED` and `PASSENGER_ONBOARD`.
- Notification IDs no longer collapse trips into a 4096-slot hash space.
- Foreground GPS recovery is fail-closed: a service restart requires an explicit trip intent and is no longer sticky; unauthenticated exact-location workers fail and clean up instead of retrying indefinitely.
- Local GPS trail writes now serialize concurrent mutations and publish through a temporary file rename, preventing partial-file publication and lost concurrent appends.
- Kotlin compilation after this pass: PASS. These changes still require a fresh APK install and device recheck before any release claim.

## Final device pass for this iteration (2026-09-11)

- Full debug APK assembled successfully and installed on Honor `VER_N49`.
- Cold launch succeeded again: `Status: ok`, `LaunchState: COLD`, `TotalTime: 3008 ms`, PID `21525`, foreground `MainActivity`, no fatal Android crash in the sampled logcat.
- The broad suite exposed one contract test still asserting the old sticky GPS behavior; the contract was updated to enforce the new fail-closed `START_NOT_STICKY` behavior and the focused suite now passes.
- Current APK SHA-256: `819d029fe693cefe52e268acf28026cfba8f91a85a41c5033528894f2fc284c6`.
