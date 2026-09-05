# ADR 0013: Pricing Truth Model (Estimate vs. Quote vs. Authorization vs. Settlement)

- **Status**: Accepted & Implemented (Commit `48a4056c`)
- **Date**: 2026-09-05
- **Deciders**: Principal Software Architect, Staff Android Engineer, Financial Systems Architect

---

## Context

Mobile marketplaces often conflate distinct monetary lifecycle stages into a single ambiguous `price` or `fare` field. In physical automotive services (such as towing, diagnostic repair, or parts replacement), unexpected conditions (e.g., locked wheels requiring a winch, additional labor, or diagnostic discovery) frequently require scope adjustments.

Allowing providers to mutate prices unilaterally or presenting estimates as guaranteed totals causes customer mistrust and dispute risk.

## Decision

We establish an immutable four-stage pricing truth model:

```text
ESTIMATE != QUOTE
QUOTE != AUTHORIZED AMOUNT
AUTHORIZED AMOUNT != FINAL SETTLEMENT
```

1. **EstimatedRange (`FulfillmentPricing.EstimatedRange`)**:
   - Algorithmic range based on historical distance, time, and service type.
   - Distinctly labeled as "Estimación inicial".
2. **Quote (`FulfillmentPricing.Quote`)**:
   - Firm commercial proposal issued by the provider. Contains itemized `PricingItem` list, expiration timestamp, and currency.
3. **AuthorizedAmount (`FulfillmentPricing.AuthorizedAmount`)**:
   - Explicit customer acceptance of a specific quote version.
   - Any additional work requires a supplemental authorization proposal. No silent price escalations are permitted.
4. **FinalSettlement (`FulfillmentPricing.FinalSettlement`)**:
   - Captured funds upon verified service delivery.
   - Invariant: `settlementAmount <= totalAuthorizedAmount` (unless mediated via formal dispute).
5. **Value Object Integrity**:
   - All monetary amounts are stored in minor integer units (`Long`) via `Money` and typed `CurrencyCode`.
   - Floating-point representations (`Double`, `Float`) are strictly prohibited in financial persistence and calculations.

## Consequences

- Full auditability and antifraud protection.
- Dispute rate reduction through transparent pre-authorization.
- Deterministic cross-runtime compatibility between backend ledgers and mobile client presentation.
