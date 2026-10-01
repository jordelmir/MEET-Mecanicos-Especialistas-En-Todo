# Provider financial authority — review candidate, not deployed

Migration: `20260927212925_provider_global_financial_authority.sql`, after root-owned `20260927130000_commission_integer_authority.sql`. Definitions inspected from live `kluumjhzncitjayvvwtj` on 2026-09-27. No production mutation or financial fixture was executed.

This candidate reuses `ride_wallets`, `ride_wallet_ledger`, and their existing balanced `ride_ledger_transactions` / `ride_ledger_postings` mirror. It broadens the wallet principal foreign key to Auth users, rather than manufacturing driver roles for other providers. Existing account-code names remain compatibility identifiers.

## Implemented

* CRC 5000 promotional starter entitlement under one principal primary key and principal advisory lock. Verified active provider evidence comes from `provider_profiles` joined to authenticated `user_profiles`; no self-attestation creates verification. Every historical ride promotional grant or service welcome bonus consumes entitlement without changing amounts or rows.
* Existing arbitrary service topup, arbitrary driver wallet credit, immediate service fee debit, and cross-wallet transfer endpoints retire fail-closed. Actual reconciled ride topups remain separate existing authority; selecting SINPE does not create money.
* Universal service acceptance freezes price from the selected persisted offer, customer/provider, 500bps commission, payment method and policy in a uniquely keyed contract. It reserves against the existing wallet before assignment. Client completion captures once; participant cancellation releases once. CASH/SINPE customers require no wallet. In-progress cancellation does not invent a refund of external payment.
* Actual live ride ACCEPT and SUBMIT overrides remove both missing-vehicle INSERT and automatic VERIFIED UPDATE. ACCEPT requires the exact selected offer; it removes the historical repeated `greatest(commission*20,100000)` grant branch. Wallet shortfall fails before assignment. Ride cancellation releases its reservation; undocumented driver cancellation commission penalties are retired prospectively.
* Cross-vertical reserved totals serialize on the same wallet row. Compatibility posted balances exclude both reservation debits and release credits; release cannot fabricate spendable credit. Debit guard checks all outstanding reservations. Promotional available credit is consumed before funded credit in the commission projection.
* Starter/contract/capability tables have RLS and owner/participant reads; clients cannot mutate ledger/accounts/contracts. Private SECURITY DEFINER helpers have explicit revoked execution. New rails P2P, stored value, cashout, Commerce payments and automatic SINPE confirmation remain OFF.

## Local evidence

Run `python3 tests/payments/run_provider_financial_contracts.py` with local PostgreSQL `/tmp` socket. The runner creates and deletes only a newly generated local test database; it does not accept a remote URL or production credentials. The live-shaped fixture reproduces column names/types/defaults but is not a full Supabase clone. Existing Auth receipt infrastructure is represented by test stubs; these tests do not prove production replay infrastructure or trust administration.

Passed: SQL creation and migration replay; 100 truly concurrent grants across two verified roles give exactly 1 ledger credit of CRC5000; historical CRC15000 stays intact and receives no extra gift; customer with CASH/SINPE and no wallet; reserve/capture/release projections; 20 completion retries give one capture; balanced journal postings; two simultaneous services compete for one commission capacity with one winner; arbitrary SINPE credit denied; missing/unverified vehicles rejected; 20 acceptance retries with distinct keys cannot mint extra grants or assign an unaffordable ride; direct client/helper privileges denied.

## Explicit remaining gates

1. Independent migration review and live metadata compatibility validation before deployment. Preserve prior migrations; do not replay vulnerable historical Commerce migration merely because it exists locally.
2. Historical service-provider wallet balances stay untouched and are **not automatically imported into canonical spendable balance**: those old ledgers accepted arbitrary client-reported SINPE credits, so provenance cannot be presumed. A reconciler must identify actual funded value versus promotional grants, freeze suspicious entries, and append audited transfer journals without altering history. Providers with only a historical service gift retain consumed starter entitlement and may need confirmed funding; this is intentionally honest, but a migration/product transition decision is still required.
3. Historical services already charged at ACCEPT may complete without a second debit. Cancelling those charged services requires reconciliation; this migration does not fabricate a cash refund or delete the old charge.
4. Existing ride COMPLETE quote amendments must be reviewed against physical customer consent and delta reservation. The debit guard prevents aggregate negative balance, but these tests do not establish quote-consent authority or all existing RPC entry points.
5. Live Commerce stores/products/orders and Commerce RPCs were absent. The root handles APK fake-escrow removal; online catalog/order/courier authority is not implemented by this candidate. Mobility settlement normalization is root-owned; unified reservation integration for that legacy path remains a separate gate.
6. Real SINPE owner AAL2/reconciliation configuration and settlement evidence must be validated. No automatic-confirmation rail is enabled here. No production money test, environment reset, historical ledger rewrite, Kotlin edit, version change, build or commit was performed.
7. Capability toggles cover wallet/grants and unavailable future rails; independent reserve/capture/owner operational kill-switch audit is still required. Provider eligibility currently follows any active verified provider profile, so catalog capabilities should be constrained when the authoritative provider-type catalog is ready.

## Rollback

Do not remove posted records, starter entitlement, or contracts. Disable provider wallet/grants capability to stop new economic acceptance, preserve captured/released history, and use a reviewed follow-up migration. Do not restore arbitrary credit/fake verification RPCs. In-flight reservations require reconciliation and explicit settlement handling; rolling back functions blindly is unsafe.
