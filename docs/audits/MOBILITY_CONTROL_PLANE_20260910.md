# Mobility control plane — current repository inventory, 2026-09-10

Baseline inspected: `6961030c5e0b1f8872af13c27e2f86f30f832929`, branch `fix/mobility-product-hardening-20260909`, with active uncommitted hardening work. Historical annex SHA and claims of missing subsystems were not treated as evidence. This is a bounded control-plane inspection, not a deployment audit. No production credentials, infrastructure settings, banking connections or operational dashboards were inspected.

Evidence vocabulary: E1 = inspected code/specification; E2 = local unit/static test; E3 = actual isolated PostgreSQL integration; E4 = production-equivalent end-to-end proof. OBSERVED does not establish integration. A search without hits means UNKNOWN in the inspected scope, not proven absence. Scores below are conservative evidence-supported implementation scores, not percentages of production readiness; 0 means no qualifying evidence established here. No category receives E4 credit.

## Concrete inventory and findings

Paths below are repository-relative. Function and table identifiers are exact search anchors.

| Area | Current evidence | Finding / boundary |
|---|---|---|
| Risk decisions | `supabase/migrations/20260906100000_global_platform_vehicle_economy_v13.sql:879`, `risk_decisions`: constrained decision, rules_version, reason_codes, evidence; client grants revoked | OBSERVED storage, not a verified decision engine. Search for the identifier found migration declarations and no transaction-plane consumer. Enforcement on publish/claim/capture remains NOT_INTEGRATED in the paths inspected. |
| GPS risk | `android/app/src/main/kotlin/com/elysium369/meet/ride/presence/RideLocationSequenceGuard.kt`, `validate` checks sequence, time monotonicity, >83.33 m/s | OBSERVED helper; search found its declaration but no production call. Range comparisons do not reject NaN. Do not equate this with authoritative anti-spoofing. |
| Device integrity / versions | `core/realtime/ElysiumRealtimeProtocol.kt` has `protocolVersion`; `core/obd/ActiveDiagnosticSafety.kt` enforces a trust-manifest minimum version for OBD | Adjacent controls exist. A Mobility command minimum-client negotiation or Play Integrity ingestion path was not established by searches in Android/Supabase. UNKNOWN, not a claim of product-wide absence. |
| Kill switches | V13 `platform_kill_switches`, `platform_is_target_active`, `market_verify_activation_gate`; `tests/platform/verify-revenue-to-platform-e2e.sh:500` exercises helper | OBSERVED helper/table, not universal enforcement. Search found no create/dispatch/capture RPC calling the helper. `platform_is_target_active` returns true when no disabling row exists. Emergency protection cannot be claimed from helper tests alone. |
| Market activation | V13 `market_verify_activation_gate` checks market switch and pricing-policy existence | `v_has_identity`, `v_has_currency`, `v_has_legal` declared but unused. Function returns ACTIVE without those gates. It does not establish regulatory launch clearance. |
| Disputes | `supabase/migrations/20260906090000_mobility_provider_operations_v12_closure.sql:129`, `mobility_trip_disputes`, participant SELECT/INSERT RLS | Confirmed authority defect: original INSERT policy authorized participants but allowed caller-selected resolved state, notes and resolved_at. Fixed additively by `20260910020000_mobility_dispute_resolution_authority.sql`. Nonprivileged insert must be OPEN without resolution; database sets timestamps; nonprivileged updates denied. Existing records retained. Historical forged cases, if any, require separate investigation. |
| Dispute scope | `android/.../mobility/domain/models/TripDispute.kt`; `android/.../core/dispute/DisputeResolutionEngine.kt` | Mobility case model and separate repair-dispute engine exist. They are not evidence of payment reversals, chargeback adjudication or a production operator console. |
| Payouts | V12 `provider_financial_projection`, `provider_payout_rail_capabilities`, `provider_payout_methods`; `provider/data/gateway/SupabaseProviderOperationsGateway.kt` | Actual schema/projection exists; configured rails are seeded inactive. Payout intent → provider submission → bank acknowledgement → reconciliation was not established. Withdrawable projections are not bank deposits. |
| CASH | `20260906060000_mobility_provider_capture_v9_closure.sql`, `mobility_confirm_cash_collected` and `mobility_settle_trip` | Separate cash collection event checks assigned driver and state. Settlement resolves RIDER_RECEIVABLE, DRIVER_PAYABLE, PLATFORM_REVENUE, TAX_ESCROW regardless of provider. A distinct driver-owes-platform CASH receivable and external remittance reconciliation were not established; balanced entries alone do not prove correct cash economics. |
| Refund/chargeback | `20260906010000_mobility_financial_authority.sql` includes PAYMENT_REFUND reference type; V13 payment-state constraint includes REFUNDED | These declarations do not establish an authenticated refund workflow or payout-after-chargeback compensation. No such lifecycle was proven in the bounded search. |
| Backoffice | `20260908100000_driver_wallet_sinpe_topups.sql`, `ride_owner_decide_wallet_topup_v1` checks `meet_session_has_aal2()` and `meet_is_platform_owner()` | OBSERVED real step-up and owner authorization for this operation. Does not prove least-privilege roles, dual approval, refund authority or global support auditing. |
| Privacy operations | `supabase/functions/account-deletion-worker/index.ts` calls erasure RPC then auth admin deletion; V12 suite exercises deletion | Existing deletion mechanism. Legal holds, unsettled-money retention and full provider/object-store deletion must be checked separately; no assumption that GDPR compliance follows from auth deletion. |
| SLI/SLO | `docs/rides/RIDE_SLO.md`; `ride/observability/RideObservability.kt`, `record` emits logs plus MeetTelemetry | Targets and event emission exist. No measured error-budget window, production alert routing, paged on-call acknowledgement or dashboard was verified. |
| DR/runbooks | `docs/operations/disaster-recovery.md`, `backup-restore.md`; `docs/runbooks/{RESTORE_DRILL,DATABASE_INCIDENT,LEDGER_MISMATCH,MOBILITY_KILL_SWITCH}.md` | Runbooks exist. Their PITR/offsite-backup statements are specifications, not current provider configuration evidence. Primary agent owns current restore-drill execution; incorporate its actual output separately. Kill-switch runbook SQL signatures must be checked against current RPCs before use. |
| Capacity/continuity | `tests/mobility/test-100-concurrent-dispatches.sh`; map resilience/cache/provider fallback; payment/realtime/routing outage runbooks | Bounded concurrency test exists. Not proof of 100,000 online drivers, stadium surge or measured backpressure. Map fallback does not prove SMS/FCM/PSP continuity. |
| Supply chain | `.github/workflows/mobility-production-gates.yml` has SHA-pinned checkout/setup-java and DB/domain gates | Concrete CI configuration. No current signed APK→SHA→CI→public release chain verified in this inventory. |

## Bounded remediation and proof

The new dispute guard preserves participant filing and privileged review. It changes authority only, does not implement money movement, and does not silently rewrite historical evidence. New V12 PostgreSQL regressions exercise legitimate filing, four forged state values, forged resolution metadata, denied participant UPDATE and preserved privileged review. Executed successfully: `bash tests/mobility/verify-mobility-v12-provider-operations.sh`, exit 0, log `/tmp/meet-v12-control-plane.log`. The log records participant-forgery denial and ends `ALL V12 PROVIDER OPERATIONS & MOBILITY CLOSURE TESTS PASSED!` (E3). The V12 harness replays its documented Mobility subset, not the entire repository migration history. It is not a full-schema-from-zero or production RLS audit.

Priority after the bounded repair:

1. Wire market/service/provider switches into the actual mutating RPCs, with in-flight completion/cancellation drain semantics and tests that invoke the command, not only the switch helper.
2. Establish version negotiation and protocol rejection at server boundaries; test an old client against current authority.
3. Close payout/refund/chargeback and CASH receivable accounting using provider receipts and balanced compensating entries. Keep inactive rails unavailable until verified.
4. Wire risk decisions to deterministic policies and evidence ingestion; test GPS anomalies and session revocation. Do not use the currently uncalled GPS helper as a security claim.
5. Verify operational roles, case transition audit, restore, error budgets, alerts and target capacity against actual infrastructure.

## Control-plane scorecard (27 categories)

| Category | Score /10 | Evidence lower bound | Remaining gate |
|---|---:|---|---|
| Trust & Risk | 2 | E1 V13 risk_decisions | Decision producer and critical-command enforcement |
| Fraud/Abuse Resistance | 2 | E1 GPS sequence/speed helper; authoritative actor guards in cash flow | Integrated spoof/collusion/ghost-trip detection and response |
| Device/App Integrity | 0 | No Mobility-specific integrity ingestion established | Attestation/risk binding; tampered-client test |
| Account Security | 3 | E1 owner AAL2 topup decision | Sensitive session revocation and payout step-up proof |
| Disputes | 4 | E3 V12 case/RLS and direct-insert authority regression passed | Versioned adjudication, evidence bundle and operational audit |
| Refunds | 1 | E1 financial reference/state declarations | Authorized idempotent provider refund and ledger compensation |
| Chargebacks | 0 | No qualifying full lifecycle established | External dispute after payout and balanced recovery |
| Driver Payouts | 2 | E1 projection/methods/disabled rails | Submitted/unknown/paid/reversed lifecycle with bank evidence |
| Treasury Reconciliation | 1 | E1 financial projections and ledger | Provider/bank statement reconciliation and discrepancy queue |
| Cash Accounting | 3 | E1 separate cash collection and settlement RPC | Driver commission receivable, remittance and negative balance tests |
| Tax/Fiscal | 1 | E1 TAX_ESCROW ledger account | Market-specific fiscal document and tax authority verification |
| Insurance | 0 | No qualifying insurance authority inspected | Coverage policy/document expiry and claim evidence |
| Regulatory Market Controls | 2 | E1 market gate/pricing check | Identity/legal/currency gates and human launch clearance |
| Backoffice Security | 3 | E1 owner+AAL2 topup RPC | Scoped operator permissions, dual approval, audit and refund controls |
| Incident Operations | 2 | E1 outage/ledger runbooks | Staffed response, incident ownership and timed exercise |
| Data Governance | 3 | E1 account erasure worker and test path | Legal hold, unsettled-money retention, residency and export |
| Version Compatibility | 1 | E1 adjacent protocol and OBD version controls | Mobility minimum-version and incompatible-command tests |
| Kill Switches | 2 | E1 granular table/helper and helper test | Real mutation enforcement, drain and rollback drill |
| Rollout/Rollback | 2 | E1 release/CI and operational instructions | Market cohorts, compatibility window and executed rollback |
| Disaster Recovery | 1 | E1 backup/restore runbooks | Measured RPO/RTO and verified data invariants after restore |
| SLO/SLI | 2 | E1 targets and telemetry emitter | Measured service windows/error budgets |
| Alerting | 1 | E1 observability event path | Actual alert rules, receiver and acknowledgement proof |
| Runbooks | 3 | E1 named outage/DR/ledger procedures | Current RPC validation and executed operator exercises |
| Capacity | 2 | E1 100-way dispatch test script | Measured 100k-driver/stadium load and limits |
| Backpressure | 2 | E1 outbox retry/batching and map cooldown | Cross-provider/database admission control under target load |
| Provider Continuity | 3 | E1 map fallback; fail-closed payout/payment capability declarations | PSP/FCM/SMS failure and recovery E4 |
| Supply Chain | 3 | E1 pinned CI actions and authority gates | Signed artifact provenance, SBOM and installed release verification |

These scores must not be averaged into a launch claim. The decisive gates remain command-enforced controls, financial operations, risk integration, operational proof and verified recovery. GLOBAL PRODUCTION CANDIDATE = NO on this inventory's evidence. Current deployment state is UNKNOWN.
