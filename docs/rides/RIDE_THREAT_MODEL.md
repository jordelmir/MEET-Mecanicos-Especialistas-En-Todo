# ELYSIUM MOBILITY OS — THREAT MODEL SPECIFICATION (STRIDE)
**Status**: AUTHORITATIVE SPECIFICATION V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *The mobile client is an untrusted, potentially compromised actor. Defend every boundary.*

---

## 1. STRIDE Threat Vector Matrix

| Threat Category | Specific Attack Vector | Impact | Mitigation Strategy |
|---|---|---|---|
| **Spoofing** | Driver GPS spoofing via Mock Locations / Frida hooking | Driver appears nearby; claims rides dishonestly | Android `location.isFromMockProvider` check + server-side maximum velocity/jump physics filter + cell tower correlation |
| **Spoofing** | Principal identity spoofing (`userId: "victim"`) in JSON payload | Attacker acts on behalf of another user | Server rejects all client-supplied user IDs; derives identity strictly from validated JWT `auth.uid()` |
| **Tampering** | Parameter tampering on offered fare (`offeredFare: 1`) | Passenger pays ₡1 for a ₡10,000 ride | Server recalculates minimum legal fare based on distance/duration rate card; rejects bids below cost |
| **Repudiation** | Driver claims cash was never paid or passenger claims they boarded | Dispute between actors | 4-digit boarding PIN proves physical presence; driver signature & cash acknowledgment receipts |
| **Information Disclosure** | IDOR / BOLA on ride documents or routes (`/trip/{uuid}`) | Unauthorized party tracks real-time location of passenger | PostgreSQL Row-Level Security (`RLS`) restricts SELECT to authenticated `passenger_id` or `assigned_driver_id` only |
| **Denial of Service** | Driver submits flood of duplicate claims | Database lock contention, server exhaustion | Rate limiting per principal + PostgreSQL advisory transaction locks on `idempotency_key` |
| **Elevation of Privilege** | Normal driver calls admin override RPCs | Unauthorized approval of suspended accounts | RPCs check `has_role('RIDE_ADMIN')` backed by signed server claims; fail closed on missing privilege |

---

## 2. Specific Attack Scenarios & Mitigations

### 1. Payment Webhook Forgery
- **Attack**: Adversary sends fake `payment_success` webhooks to credit driver wallets.
- **Defense**: Server validates HMAC-SHA256 signature using a shared secret with constant-time comparison (`MessageDigest.isEqual`). Deduplicates on `provider_event_id`.

### 2. Multi-Device Simultaneous Acceptance
- **Attack**: Colluding drivers using script automation to accept the same trip from multiple accounts at the exact millisecond.
- **Defense**: PostgreSQL row-level exclusive lock (`SELECT ... FOR UPDATE`) guarantees sequential evaluation. Only the first commit wins; all concurrent transactions encounter `assigned_driver_id IS NOT NULL` and return `ALREADY_ASSIGNED`.

### 3. Document URL Harvest
- **Attack**: Scraper enumerates UUIDs to view driver identification and police background records.
- **Defense**: Storage bucket `trust-verification-evidence` is strictly PRIVATE with no public read access. Only authenticated Trust Center reviewers can generate short-lived (15-minute) signed URLs.
