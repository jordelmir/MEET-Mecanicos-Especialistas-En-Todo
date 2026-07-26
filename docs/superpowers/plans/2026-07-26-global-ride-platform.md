# MEET Global Ride Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. The owner prohibited subagents. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a production-shaped Costa Rica pilot vertical for MEET Viajes with worldwide money contracts, safe ride transitions, consented mechanical sharing, secure backend schema, live map foundations, wallet visibility, and cancellation safety.

**Architecture:** Pure Kotlin domain rules are shared by UI and persistence. Supabase/Postgres is the future authoritative state and ledger, while the existing Room flow remains the offline projection during migration. MapLibre Native renders provider-independent maps; wallet funding is abstracted and Play Billing stays policy-gated.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose, Room, Supabase/Postgres/RLS, MapLibre Native Android, Google Fused Location, JUnit 4, Gradle.

---

## File map

- `android/app/src/main/kotlin/com/elysium369/meet/ride/domain/RideMoney.kt`:
  ISO‑4217 minor-unit money and 5 % commission arithmetic.
- `android/app/src/main/kotlin/com/elysium369/meet/ride/domain/RideLifecycle.kt`:
  ride state machine and actor-authorized transitions.
- `android/app/src/main/kotlin/com/elysium369/meet/ride/domain/RidePrivacy.kt`:
  consent categories, expiry and shareable mechanical snapshots.
- `android/app/src/main/kotlin/com/elysium369/meet/ride/domain/RideCancellation.kt`:
  normalized cancellation reasons and safety routing.
- `android/app/src/main/kotlin/com/elysium369/meet/ride/wallet/RideWalletModels.kt`:
  immutable ledger projection, promotional grant and funding policy.
- `android/app/src/main/kotlin/com/elysium369/meet/ride/map/RideMapModels.kt`:
  typed passenger, pickup, destination, driver and route markers.
- `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideMapPanel.kt`:
  MapLibre lifecycle bridge and honest unavailable-state fallback.
- `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideSafetyPanels.kt`:
  wallet, consent and cancellation Compose surfaces.
- `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideServiceScreen.kt`:
  integrates the new panels without deleting existing negotiation/chat.
- `android/app/build.gradle.kts`:
  MapLibre dependency and policy build flag.
- `supabase/migrations/20260726010000_ride_platform_foundation.sql`:
  authoritative ride, consent, wallet, ledger and cancellation schema.
- `docs/ride/RIDE_PLATFORM_RUNBOOK.md`:
  setup, map attribution, feature gates and verification.

## Task 1: Money and commission contracts

**Files:**

- Create: `android/app/src/main/kotlin/com/elysium369/meet/ride/domain/RideMoney.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/ride/domain/RideMoneyTest.kt`

- [ ] **Step 1: Write failing tests for exact commission**

Test CRC, USD and KWD values, reject mixed currencies and reject negative
fares. Assert `Money.minor(100_000, "CRC").commission()` equals `5_000 CRC`
and that repeated calculations are deterministic.

- [ ] **Step 2: Run the focused tests**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests '*RideMoneyTest'
```

Expected: compilation failure because `RideMoney` does not exist.

- [ ] **Step 3: Implement integer arithmetic**

Create:

```kotlin
@JvmInline
value class CurrencyCode private constructor(val value: String) {
    companion object {
        fun of(raw: String): CurrencyCode {
            val normalized = raw.trim().uppercase()
            require(normalized.matches(Regex("[A-Z]{3}")))
            return CurrencyCode(normalized)
        }
    }
}

data class RideMoney(val minorUnits: Long, val currency: CurrencyCode) {
    init { require(minorUnits >= 0) }

    fun commission(basisPoints: Int = 500): RideMoney {
        require(basisPoints in 0..10_000)
        val whole = Math.multiplyExact(minorUnits, basisPoints.toLong())
        return copy(minorUnits = Math.addExact(whole, 5_000L) / 10_000L)
    }
}
```

Add safe same-currency addition/subtraction and `CostaRicaRidePolicy` with a
`100_000 CRC` promotional grant.

- [ ] **Step 4: Run focused tests**

Expected: all `RideMoneyTest` tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/elysium369/meet/ride/domain/RideMoney.kt \
  android/app/src/test/kotlin/com/elysium369/meet/ride/domain/RideMoneyTest.kt
git commit -m "feat(ride): add exact global money contracts"
```

## Task 2: Lifecycle, consent and cancellation policy

**Files:**

- Create: `android/app/src/main/kotlin/com/elysium369/meet/ride/domain/RideLifecycle.kt`
- Create: `android/app/src/main/kotlin/com/elysium369/meet/ride/domain/RidePrivacy.kt`
- Create: `android/app/src/main/kotlin/com/elysium369/meet/ride/domain/RideCancellation.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/ride/domain/RideLifecycleTest.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/ride/domain/RidePrivacyTest.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/ride/domain/RideCancellationTest.kt`

- [ ] **Step 1: Write state-machine denial tests**

Cover passenger creation, driver assignment, arrival, PIN-confirmed boarding,
start, completion, cancellation and invalid skipped transitions. Assert an
unrelated actor cannot mutate a trip.

- [ ] **Step 2: Write privacy tests**

Assert mechanical categories are off by default, location authorization expires
at trip end, revoked consent denies a snapshot, and stale telemetry is labelled
stale rather than live.

- [ ] **Step 3: Write cancellation tests**

Assert safety, unaccompanied minor, child-seat, identity mismatch, harassment,
dangerous pickup, medical emergency and vehicle fault all require review and
never create an automatic fee.

- [ ] **Step 4: Run focused tests and observe failure**

```bash
cd android
./gradlew testDebugUnitTest --tests '*RideLifecycleTest' \
  --tests '*RidePrivacyTest' --tests '*RideCancellationTest'
```

- [ ] **Step 5: Implement the policies**

Use explicit enums and a pure decision function:

```kotlin
data class RideTransitionRequest(
    val from: RideState,
    val to: RideState,
    val actor: RideActor,
    val pinVerified: Boolean = false
)

sealed interface TransitionDecision {
    data object Allowed : TransitionDecision
    data class Denied(val reason: String) : TransitionDecision
}
```

Consent must contain `tripId`, `driverId`, `category`, `grantedAt`,
`expiresAt`, and `revokedAt`. `canShare(now)` returns true only inside that
window. Mechanical samples contain source and capture time.

- [ ] **Step 6: Run focused tests**

Expected: lifecycle, privacy and cancellation tests pass.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/elysium369/meet/ride/domain \
  android/app/src/test/kotlin/com/elysium369/meet/ride/domain
git commit -m "feat(ride): enforce lifecycle privacy and cancellation rules"
```

## Task 3: Wallet ledger and policy-gated funding

**Files:**

- Create: `android/app/src/main/kotlin/com/elysium369/meet/ride/wallet/RideWalletModels.kt`
- Create: `android/app/src/main/kotlin/com/elysium369/meet/ride/wallet/WalletFundingProvider.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/ride/wallet/RideWalletModelsTest.kt`
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: Write ledger projection tests**

Test one promotional grant, reservation without deduction, capture on complete,
release on cancel, duplicate idempotency keys and insufficient available
balance.

- [ ] **Step 2: Run and confirm failure**

```bash
cd android
./gradlew testDebugUnitTest --tests '*RideWalletModelsTest'
```

- [ ] **Step 3: Implement immutable entries**

Use:

```kotlin
enum class LedgerEntryType {
    PROMOTIONAL_GRANT, TOP_UP_PENDING, TOP_UP_CONFIRMED,
    COMMISSION_RESERVED, COMMISSION_CAPTURED, COMMISSION_RELEASED,
    REFUND, ADJUSTMENT
}

data class RideLedgerEntry(
    val id: String,
    val idempotencyKey: String,
    val type: LedgerEntryType,
    val amount: RideMoney,
    val tripId: String?,
    val createdAtEpochMs: Long
)
```

Project `posted`, `reserved` and `available`. Reject duplicate keys and mixed
currencies. Define `WalletFundingProvider` with `catalog`, `launchPurchase`,
`confirmPurchase` and `restore`.

- [ ] **Step 4: Add policy build flag**

Add a `PLAY_BILLING_POLICY_APPROVED` `BuildConfig` boolean loaded from
`local.properties`, defaulting to `false`. No production flow may launch ride
wallet Play Billing while false.

- [ ] **Step 5: Run focused tests**

Expected: wallet tests pass.

- [ ] **Step 6: Commit**

```bash
git add android/app/build.gradle.kts \
  android/app/src/main/kotlin/com/elysium369/meet/ride/wallet \
  android/app/src/test/kotlin/com/elysium369/meet/ride/wallet
git commit -m "feat(ride): add auditable driver wallet ledger"
```

## Task 4: Secure Supabase authority

**Files:**

- Create: `supabase/migrations/20260726010000_ride_platform_foundation.sql`
- Create: `tests/ride/verify-ride-migration.sh`

- [ ] **Step 1: Write migration contract verifier**

The script must assert the migration contains RLS for every exposed ride table,
revokes direct client changes to ledger/event tables, defines unique
idempotency keys, uses `security definer set search_path = ''`, and contains RPC
functions for grant, accept, cancel and complete.

- [ ] **Step 2: Run verifier and confirm failure**

```bash
bash tests/ride/verify-ride-migration.sh
```

Expected: failure because the migration does not exist.

- [ ] **Step 3: Create schema**

Create typed tables for profiles, vehicles, requests, offers, trip events,
consents, latest positions, vehicle questions, wallets, ledger entries,
commission reservations and cancellations. Store money in `bigint` minor units
plus three-letter currency.

- [ ] **Step 4: Add authoritative RPC**

Implement:

- `ride_grant_promotional_balance()`;
- `ride_accept_offer(request_id, offer_id, idempotency_key)`;
- `ride_cancel_trip(trip_id, reason_code, detail, idempotency_key)`;
- `ride_complete_trip(trip_id, final_fare_minor, idempotency_key)`.

Each locks the relevant rows, verifies `auth.uid()`, checks state/version,
appends immutable events and returns the existing result for a repeated key.

- [ ] **Step 5: Add RLS and grants**

Participants can see only their ride. Exact position and consented mechanical
data are available only to the assigned pair during an active trip. Ledger
inserts and updates are forbidden to clients. Operational overrides require a
server role and append an adjustment event.

- [ ] **Step 6: Verify migration contract**

Expected: `ride migration contract: PASS`.

- [ ] **Step 7: Commit**

```bash
git add supabase/migrations/20260726010000_ride_platform_foundation.sql \
  tests/ride/verify-ride-migration.sh
git commit -m "feat(ride): add secure realtime mobility schema"
```

## Task 5: Map models and MapLibre panel

**Files:**

- Create: `android/app/src/main/kotlin/com/elysium369/meet/ride/map/RideMapModels.kt`
- Create: `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideMapPanel.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/ride/map/RideMapModelsTest.kt`
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Test marker separation and freshness**

Assert GPS passenger, selected pickup, destination and driver retain distinct
roles even with equal coordinates. Assert positions older than the configured
threshold become `STALE`.

- [ ] **Step 2: Add MapLibre dependency**

Add `implementation("org.maplibre.gl:android-sdk:13.0.2")` and retain OSM
attribution in every style.

- [ ] **Step 3: Implement typed map state**

```kotlin
enum class RideMarkerRole { PASSENGER_GPS, PICKUP, DESTINATION, DRIVER }

data class RideGeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val capturedAtEpochMs: Long
)
```

Validate latitude/longitude and expose freshness without pretending accuracy.

- [ ] **Step 4: Implement the Compose bridge**

Wrap `MapView` in `AndroidView`, forward lifecycle, load an injectable style
URL, draw role-specific GeoJSON sources/layers and route polyline, and show a
textual fallback if style loading fails.

- [ ] **Step 5: Run unit tests and compile**

```bash
cd android
./gradlew testDebugUnitTest --tests '*RideMapModelsTest'
./gradlew compileDebugKotlin
```

- [ ] **Step 6: Commit**

```bash
git add android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml \
  android/app/src/main/kotlin/com/elysium369/meet/ride/map \
  android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideMapPanel.kt \
  android/app/src/test/kotlin/com/elysium369/meet/ride/map
git commit -m "feat(ride): add provider-independent live trip map"
```

## Task 6: Passenger/driver safety and trust UI

**Files:**

- Create: `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideSafetyPanels.kt`
- Modify: `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideServiceScreen.kt`
- Modify: `android/app/src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt`

- [ ] **Step 1: Add map state to existing flows**

Build map state from passenger GPS, chosen pickup, destination and assigned
driver coordinates. Never collapse passenger GPS into pickup.

- [ ] **Step 2: Add wallet card**

Show promotional, recargado, reservado, disponible, 5 % commission and an
explicit policy-disabled recharge explanation. Do not show a fake successful
purchase.

- [ ] **Step 3: Add sharing center**

Provide individual switches for telemetry, active DTC, DTC history,
maintenance, installed parts and certified reports. Show source and freshness
preview. Default all mechanical switches off.

- [ ] **Step 4: Add cancellation sheet**

Render all normalized reasons, highlight safety reasons, collect bounded detail
for `OTHER`, and show the consequence before confirmation.

- [ ] **Step 5: Remove production auto-approval and fake IDs**

Delete `AUTO-APROBAR (DEV)` from Ride. Replace fallback identities with a
blocking identity-required state. Keep debug-only seed helpers outside release
UI.

- [ ] **Step 6: Compile and run focused UI smoke**

```bash
cd android
./gradlew compileDebugKotlin
```

Expected: build succeeds with no unresolved Compose or MapLibre references.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideSafetyPanels.kt \
  android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideServiceScreen.kt \
  android/app/src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt
git commit -m "feat(ride): integrate map wallet consent and safety flows"
```

## Task 7: Documentation and full verification

**Files:**

- Create: `docs/ride/RIDE_PLATFORM_RUNBOOK.md`
- Modify: `docs/GOOGLE_PLAY_BILLING.md`
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: Document operation**

Document the Costa Rica grant, worldwide currency config, 5 % lifecycle,
MapLibre attribution, map/routing providers, Supabase migration, consent,
retention, cancellation handling and policy gate.

- [ ] **Step 2: Increment Android version**

Set `versionCode = 19` and `versionName = "4.2.0"`.

- [ ] **Step 3: Run complete verification**

```bash
bash tests/ride/verify-ride-migration.sh
bash tests/parity/ci-verify.sh
cd android
./gradlew testDebugUnitTest assembleDebug
```

Expected: migration contract and parity pass; all Android tests pass; debug APK
is produced.

- [ ] **Step 4: Inspect APK**

```bash
apkanalyzer manifest application-id android/app/build/outputs/apk/debug/app-debug.apk
shasum -a 256 android/app/build/outputs/apk/debug/app-debug.apk
```

Expected application ID: `com.elysium369.meet`.

- [ ] **Step 5: Defer ADB safely when unavailable**

If no device is connected, record the exact unverified device checks. If a
device is present, first read battery temperature; do not run an intensive
session at unsafe temperature. Then install, launch, inspect foreground/process
and crash logs.

- [ ] **Step 6: Commit release evidence**

```bash
git add docs/ride/RIDE_PLATFORM_RUNBOOK.md docs/GOOGLE_PLAY_BILLING.md \
  android/app/build.gradle.kts
git commit -m "docs(release): prepare Android 4.2.0 ride foundation"
```

## Completion boundary

This vertical is complete only when domain and migration contract tests pass,
the APK builds and the UI does not claim cloud dispatch, commercial recharge or
live mechanical sharing without their real configured dependencies. Production
launch still requires operating/legal review per country, a deployed Supabase
migration, map/routing infrastructure, a compliant funding provider and a
second authenticated device for end-to-end dispatch proof.
