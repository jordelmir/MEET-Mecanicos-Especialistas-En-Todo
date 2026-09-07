# MEET / ELYSIUM — GLOBAL PLATFORM GATES (V13)

## Multi-Market Operations, Localization & Compliance

---

## 1. Global Market Operating System (MarketOS)

The platform supports borderless operations while maintaining strict market boundaries:

### Market Code Taxonomy
Markets are identified by ISO-3166-1 alpha-2 country codes and IATA / metropolitan identifiers:
- `CR_SJO`: Costa Rica — Greater Metropolitan Area (San José / Alajuela / Heredia / Cartago)
- `MX_CDMX`: Mexico — Mexico City Metropolitan Area
- `MX_CUN`: Mexico — Cancun / Riviera Maya
- `US_MIA`: United States — Miami Metro / South Florida
- `CO_BOG`: Colombia — Bogotá D.C.

### Market Configuration Enforcements (`MarketConfig.kt`)
Each market enforces explicit operational constraints:
- **Currency Code**: ISO-4217 3-letter currency (CRC, MXN, USD, COP).
- **Distance Unit**: `KILOMETERS` vs `MILES` (metric vs imperial).
- **Tax Policy**: Jurisdiction-specific VAT/sales tax rate and statutory authority rules.
- **Provider Requirements**: Background checks, certified inspector credentials, tow-truck weight ratings.

---

## 2. Fail-Closed Activation Gate

No market can accept consumer traffic, dispatch rides, or assign mechanics until passing the fail-closed activation verification gate:
```sql
SELECT public.market_verify_activation_gate(p_market_code);
```
Verification checks in atomic sequence:
1. **Kill Switch Audit**: Verifies `platform_kill_switches` has no active `MARKET` kill switch for the target market code.
2. **Pricing Policy Invariant**: Verifies that an active, validated `mobility_pricing_policies` row exists with valid base fares, distance fares, and time fares in the market's domestic currency.
3. **Legal & Operating Status**: Verifies that the market record is marked `active = TRUE` in `mobility_markets`.

If any check fails, the gate returns:
```json
{
  "allowed": false,
  "reason": "MISSING_MANDATORY_PRICING_POLICY"
}
```
All client dispatch and request creation RPCs invoke this gate prior to state creation.

---

## 3. Platform Kill Switch Hierarchy

The platform provides fine-grained, instantaneous kill switches across 6 target types:
1. `MARKET`: Disable an entire geographical market in response to severe weather, civil emergency, or regulatory action.
2. `SERVICE_TYPE`: Disable specific verticals (e.g. `RIDE`, `TOW`, `MECHANICAL`, `INSPECTION`) independently.
3. `PROVIDER`: Suspend a specific driver, mechanic, or fleet provider across all operations.
4. `PAYMENT_PROVIDER`: Temporarily suspend a compromised or degrading PSP.
5. `ELECTRONIC_PAYMENTS`: Force failover to cash-only mode when electronic processing is unavailable.
6. `FEATURE`: Toggle individual client capabilities or experimental UI flows.

All kill switches are evaluated in $\mathcal{O}(1)$ time via SQL STABLE security definer functions (`platform_is_target_active`).

---

## 4. Regulatory & Store Compliance

- **Google Play Target SDK 35 & Billing Library 7.0.0**: All subscriptions and consumable credits flow through canonical Google Play billing with server-side validation.
- **GDPR & Privacy Right to Erasure**: Complete zero-trace purge of user records from `auth.users`, cascades through identity records, and pseudonyms historical financial transactions to preserve tax audit invariants without storing PII.
- **Forensic Hash Integrity**: Vehicle passport records, inspection reports, and repair certifications are verified via SHA-256 digests.
