# Mobility tracking truth hardening — 2026-09-09

## Observed active flow (E1)

MainActivity RIDE_ACTIVE_TRACKING observes ObdViewModel.activeRideRequest, constructs ActiveRideViewState, then renders ActiveRideTrackingScreen. The projection is request-based, and the baseline caller supplied no driverLocation. That is not a verified live tracking pipeline.

Baseline findings: critical callbacks defaulted to empty bodies; PAY opened a locally confirmed SINPE dialog and advanced to a local rating sheet; rating closed the route without persistence; local PTT gestures displayed transmission without a transport; safety actions had empty handlers; driver identity panel asserted biometric and vehicle inspection verification without evidence; fare formatting always used CRC symbol irrespective of currency; MainActivity assigned serverAssignedVehicleId to plate and used total journey duration as driver arrival ETA.

## Implemented changes

- Required cancellation/back callbacks, explicit nullable contact/payment/rating capabilities. Unavailable contact controls are disabled. Pay and rating are exposed only for COMPLETED plus payment/settlement capability evidence. No local success dialog changes payment or rating state.
- Existing Money/CurrencyCode formatting handles minor-unit semantics and prefixes the explicit currency code. Unknown currency and invalid amount degrade to unavailable. Both fare totals and breakdown rows use the same formatter. This deliberately preserves the repository's CRC/COP exponent=0 convention; any ISO exponent migration requires cross-runtime/backend migration rather than silently changing amounts.
- GPS requires source, capture/reception timestamps, nonnegative sequence, valid coordinates and useful accuracy. A five-second UI clock ages data without waiting for another server event. <=15s is LIVE, <=60s RECENT, older STALE; missing/malformed/future provenance UNKNOWN. Only LIVE renders a provider-live marker. No request point becomes GPS just because the UI was created now.
- Removed unsupported local PTT transmission from active tracking. Safety dialog explicitly identifies unavailable Guardian/live sharing and can open the phone dialer; it does not claim to notify responders or transmit ride data.
- Driver profile no longer asserts biometric or inspection verification without evidence.

## Verification (E2)

`RideTrackingTruthPolicyTest`: 4 tests passed using installed Kotlin/JVM compiler and JUnit 4.13.2. The run compiles production Money, CurrencyCode, RideTrackingTruthPolicy and the unmodified RideState enum extracted from RideLifecycle.kt. Cases cover USD cents, CRC compatibility, unknown/negative fare, all non-completed states, settlement/capability gates, stale expiry without server updates, delayed delivery, missing provenance, future timestamps, and poor GPS accuracy. `git diff --check` passed at agent handoff. Android Compose compilation and physical APK interaction are delegated to primary verification and are NOT claimed by this isolated JVM run.

## Remaining blockers

- Actual driver presence/location binding, trip-scoped location stream and server sequence CAS remain unverified. This patch tells the truth when absent; it does not create a location provider.
- Payment/rating canonical persistence and settlement projection must be connected before capabilities become available. A nullable callback is explicit availability, not server authorization.
- MainActivity plate, incorrect ETA, immediate cancellation navigation and explicit capability arguments require primary-agent integration (coordinated).
- Legacy experimental request/payment/safety/navigation/driver-earnings screens retain separate prototype behavior and require quarantine or full authoritative integration. This patch does not claim those screens production safe.
- FareQuote component values remain legacy Long fields. Tracking displays estimate total only and does not promote a synthetic base-fare decomposition into authoritative pricing.

Verdict for tracking/payment completeness: NO-GO until real infrastructure and physical flow verification establish E3/E4 evidence.
