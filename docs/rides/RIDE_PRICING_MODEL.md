# ELYSIUM MOBILITY OS — PRICING & FARE ENGINE SPECIFICATION
**Status**: AUTHORITATIVE SPECIFICATION V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *Never use floating point numbers as monetary authority. Integer minor units only. 5% platform commission fixed by policy.*

---

## 1. Monetary Data Integrity

1. **Exact Representation**: All money is represented in integer minor units (`Long` in Kotlin, `BIGINT` in Postgres).
   - Costa Rican Colón: `CRC` (minor unit is 1 Colón; e.g. `2500L` = ₡2,500).
   - US Dollar: `USD` (minor unit is 1 cent; e.g. `500L` = $5.00).
2. **Currency Boundary**: Cross-currency operations require explicit exchange rate contracts. Implicit conversion is strictly forbidden.
3. **Double-Entry Balance**: Every fare captured generates balanced ledger debits and credits:
   $$\sum \text{Debits} = \sum \text{Credits}$$

---

## 2. Fare Breakdown & Formula

$$\text{TotalFareMinor} = \text{BaseFare} + (\text{DistanceKm} \times \text{RatePerKm}) + (\text{DurationMin} \times \text{RatePerMin}) + \text{Tolls} + \text{Surcharges} - \text{Discounts}$$

### Fare Modes
1. **`OPEN_BID` (Negotiated)**:
   - Passenger publishes an initial offer (`priceOfferMinor`).
   - Drivers counter with bids (`counterPrice`).
   - The agreed amount becomes `finalPriceMinor` upon acceptance.
2. **`FIXED` (Pre-Quoted)**:
   - System calculates guaranteed upfront quote based on distance, duration, and demand multiplier.
   - Fixed quote is valid for 5 minutes (`quoteVersion`).
3. **`METERED` (Taximeter / Real-Time)**:
   - Active during trip execution.
   - Accumulates distance and elapsed time from verified location breadcrumbs.
   - Adjusts for approved en-route stops and waiting time (> 3 minutes at pickup).

---

## 3. Platform Commission (5% Fixed Policy)

By sovereign system policy (`docs/rides/COMMISSION_5_PERCENT.md`), Elysium Vanguard charges a **5% platform commission**:

$$\text{CommissionMinor} = \text{round}\left(\frac{\text{FareMinor} \times 500}{10000}\right)$$

### Commission Lifecycle
1. **At Offer Acceptance / Claim**:
   - The 5% commission is verified against the driver's available wallet balance:
     $$\text{DriverAvailableBalance} - \text{ActiveReservations} \ge \text{CommissionMinor}$$
   - If balance is sufficient, the commission is reserved in `public.ride_commission_reservations` with state `RESERVED`.
2. **At Trip Completion**:
   - The commission is debited from `DRIVER_RESERVED` and credited to `PLATFORM_COMMISSION` in `public.ride_wallet_ledger`.
   - Reservation status transitions to `CAPTURED`.
3. **At Cancellation / Dispute**:
   - The reservation is released back to `DRIVER_AVAILABLE` with status `RELEASED`.
   - No commission is captured on uncompleted or cancelled rides.
