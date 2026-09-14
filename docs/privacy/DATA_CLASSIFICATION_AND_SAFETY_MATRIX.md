# MEET / ELYSIUM — Data Classification, Privacy & Google Play Data Safety Matrix

This document defines the strict, authoritative data taxonomy, access boundaries, retention lifecycle, and Google Play Data Safety disclosures for the MEET / Elysium platform.

---

## 1. Classification Levels

| Level | Description | Handling Constraints |
|---|---|---|
| **PUBLIC** | Freely available vehicle catalog specs, general DTC standard definitions. | No access controls required. |
| **INTERNAL** | App metadata, non-PII diagnostic heuristics, rate-limiting counters, feature flags. | Authenticated system access only; no external leak. |
| **CONFIDENTIAL** | Mechanic workshop notes, quote details, anonymized aggregated metrics. | RLS-enforced per tenant/workshop. |
| **PII** | Full name, phone number, email address, physical address. | Strict RLS; encrypted in transit; redacted in logs via `SensitiveLog`. |
| **PRECISE_LOCATION** | Real-time GPS coordinates, trip pickup/destination latitude & longitude. | Ephemeral storage; discarded or degraded post-ride; requires foreground service justification. |
| **AUTH_SECRET** | User passwords, password hashes, refresh tokens, Supabase JWTs. | Never logged; never exposed to untrusted clients; handled only by identity providers. |
| **PAYMENT_SECRET** | PSP API keys, webhook HMAC secrets, bank tokens. | Kept strictly in backend environment variables/vaults; NEVER in mobile APK/AAB. |
| **FINANCIAL_LEDGER** | Double-entry journal entries, driver balances, commission calculations, payout records. | Append-only; immutable; cryptographic audit trail; retained per tax/accounting law even after account deletion. |
| **VEHICLE_IDENTIFIER** | VIN (Vehicle Identification Number), license plate. | Redacted in release logs (`SensitiveLog.vin`); tied strictly to authenticated vehicle owners. |

---

## 2. Field-by-Field Privacy & Governance Matrix

| Data Item | Classification | Collector | Purpose | Retention | Encryption | Read Access | Write Access | Deletion Behavior | Play Data Safety Disclosure |
|---|---|---|---|---|---|---|---|---|---|
| **User Phone** | PII | Client (auth) | Passenger/driver contact during active trip, 2FA | Account lifetime | TLS in transit, AES-256 at rest | Owner & active trip counterpart | User only | Deleted on account deletion | Phone number (App functionality, Account management) |
| **User Email** | PII | Client (auth) | Account identity, receipts, legal notices | Account lifetime | TLS in transit, AES-256 at rest | User, service role | User only | Deleted on account deletion | Email address (Account management) |
| **Precise GPS** | PRECISE_LOCATION | Android FGS | Matching, routing, live navigation, fare calculation | Active trip + 30 days audit | TLS in transit | Active driver & passenger | Client device via authenticated RPC | Purged after audit window; degraded to coarse location | Precise location (App functionality) |
| **VIN** | VEHICLE_IDENTIFIER | Client / OBD | Parts compatibility, certified reports, vehicle passport | Vehicle lifetime in system | TLS in transit | Vehicle owner | Authenticated vehicle owner | Pseudonymized on owner account deletion | Device or other identifiers |
| **Ride History** | CONFIDENTIAL | Platform | Trip records, dispute resolution | 7 years (tax/accounting compliance) | TLS in transit | Passenger, Driver, Admin | Server-authoritative RPCs only | Anonymized / decoupled from PII | Purchase history (Financial info) |
| **Driver Documents** | PII / CONFIDENTIAL | Client (driver onboarding) | KYC, driver licensing, safety background check | Legal requirement window | TLS in transit, encrypted storage | Driver, Verification Admin | Driver submission, Admin review | Retained as required by transport regulations | Photos / Documents (Identity verification) |
| **Ledger Entries** | FINANCIAL_LEDGER | Platform | Immutable financial accounting | Permanent (accounting law) | TLS in transit, database encryption | Owner (balance view), Service Role | Service Role only (via `post_ledger_transaction`) | Retained in ledger; decoupled from personal identity | Financial info (Accounting) |
| **Google Play Token** | CONFIDENTIAL | Client (billing) | Digital feature entitlement verification | Subscription lifetime + grace | SHA-256 hashed | Service Role only | Service Role only | Token hash retained in claims table to prevent replay | Purchase info |

---

## 3. Account Deletion Workflow & Retained Records

When a user initiates an account deletion via in-app settings or external web URL:

1. **Safety & Finance Check**: The system validates that there are zero open rides, uncaptured payment operations, or pending payouts. If pending operations exist, deletion is marked `BLOCKED` with code `ACTIVE_RIDES_PENDING`.
2. **PII Erasure**: Personal profile (name, phone, email, avatar, chat history, live positions) is completely deleted.
3. **Financial Decoupling**: Historic ledger entries and completed trip receipts are retained solely for tax/legal compliance, but are cryptographically pseudonymized and decoupled from user authentication.
4. **Non-Reversible Tombstone**: An immutable record is created in `account_deletion_tombstones` storing `subject_digest = HMAC_SHA256(userId, server_pepper)`. Zero PII is stored in the tombstone.
5. **Auth Account Deletion**: The `auth.users` record is permanently deleted.

---

## 4. Google Play Data Safety Alignment

- **Data shared**: Zero user data is sold or shared with third-party advertisers.
- **Data collected**: Name, email, phone number, precise location, purchase history, financial info (minor units), user documents (driver license for drivers), device identifiers.
- **Security practices**: Data is encrypted in transit using modern TLS; users can request deletion of their data at any time via the in-app screen or public deletion portal.
