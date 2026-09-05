# ELYSIUM MOBILITY OS — PAYMENT & LEDGER SPECIFICATION
**Status**: AUTHORITATIVE SPECIFICATION V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *TripCompleted != PaymentCaptured. Immutable double-entry ledger entries represent all financial truth. Webhooks require cryptographic signature verification and deduplication.*

---

## 1. Supported Payment Rails

1. **`CASH` (Efectivo)**:
   - Cash expected at passenger drop-off.
   - Driver reports cash collected via `CASH_REPORTED`.
   - Discrepancy triggers support review (`CASH_DISCREPANCY`).
2. **`SINPE_MOVIL` (Costa Rica Real-Time Interbank Transfer)**:
   - Peer-to-peer transfer to driver's registered phone number.
   - Passenger provides transfer reference number / screenshot evidence.
   - Verified via driver confirmation or bank notification webhook.
3. **`CARD` (Debit / Credit via Processor)**:
   - Pre-authorization at ride matching.
   - Capture on trip completion.
   - Webhook signature verified before ledger posting.
4. **`WALLET_CREDITS` (Elysium In-App Balance)**:
   - Instant transfer from passenger wallet balance to driver wallet.

---

## 2. Double-Entry Accounting Ledger Structure

Defined in `public.ride_wallet_ledger` and `RideDoubleEntryLedger`:

### Chart of Accounts
- **`DRIVER_AVAILABLE`**: Funds available for driver payout or new commission reservations.
- **`DRIVER_RESERVED`**: Funds temporarily held to guarantee 5% commission on active rides.
- **`PLATFORM_COMMISSION`**: Sovereign platform revenue (5%).
- **`TENANT_FEE`**: Operating fee for fleet / taxi concession holders.
- **`COOPERATIVE_FUND`**: Driver association or cooperative mutual aid fund.

### Canonical Journal Entry Example: Trip Completion (₡5,000 Fare, 5% = ₡250)
```text
Transaction ID: tx-comp-841
Business Ref:   trip-9812
Idempotency:    trip-9812:completion:settlement

Posting 1: DEBIT  DRIVER_RESERVED       ₡250
Posting 2: CREDIT PLATFORM_COMMISSION   ₡250

Result: SUM(DEBITS) == ₡250, SUM(CREDITS) == ₡250 (BALANCED)
```

---

## 3. Webhook Attestation Pipeline

```text
External Webhook Received
         │
         ▼
 ┌───────────────────────────┐
 │ SIGNATURE VERIFICATION   │ (HMAC-SHA256 signature against webhook secret)
 └───────────┬───────────────┘
             ▼
 ┌───────────────────────────┐
 │ DEDUPLICATION CHECK       │ (Check provider_event_id in webhook_receipts)
 └───────────┬───────────────┘
             ▼
 ┌───────────────────────────┐
 │ TRANSACTIONAL COMMIT      │ (Update payment state, post ledger journal, outbox)
 └───────────────────────────┘
```

**Duplicate Webhook Rule**: A retransmitted webhook with identical `provider_event_id` returns HTTP 200 immediately without creating duplicate ledger entries or re-executing business side effects.
