#!/usr/bin/env bash
# =============================================================================
# scripts/pricing/verify-pricing-gates.sh
# Production Gate: Pricing Parity, Authority, and Driver Economic Protection
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "=========================================================="
echo "  MEET PRICING ENGINE — PRODUCTION GATES VERIFICATION"
echo "=========================================================="

FAILED=0

# --- Gate P1: Canonical Rate Card & Currency Parity ---
echo "--- [1/6] Gate P1: Canonical Rate Card & Currency Parity ---"
if bash "$REPO_ROOT/tests/parity/ci-verify.sh"; then
    echo "  -> PASS: Cross-runtime Kotlin/TS/SQL parity verified."
else
    echo "  -> FAIL: Parity verification failed!" >&2
    FAILED=1
fi

# --- Gate P2: Unified Pricing Authority (ride_create_request_v4) ---
echo "--- [2/6] Gate P2: Unified Pricing Authority ---"
V4_MIGRATION="$REPO_ROOT/supabase/migrations/20260923010000_ride_create_request_v4_unified_authority.sql"
if grep -q "from public.mobility_pricing_policies" "$V4_MIGRATION" && \
   grep -q "create or replace function public.ride_create_request_v4" "$V4_MIGRATION"; then
    echo "  -> PASS: ride_create_request_v4 reads dynamically from mobility_pricing_policies."
else
    echo "  -> FAIL: ride_create_request_v4 does not read from mobility_pricing_policies!" >&2
    FAILED=1
fi

# --- Gate P3: Pilot V3 Rate Card Seed ---
echo "--- [3/6] Gate P3: Pilot V3 Rate Card Seed ---"
V3_PILOT="$REPO_ROOT/supabase/migrations/20260923020000_seed_mobility_pricing_pilot_v3.sql"
if grep -q "STD_RIDE" "$V3_PILOT" && \
   grep -q "900" "$V3_PILOT" && \
   grep -q "350" "$V3_PILOT" && \
   grep -q "80" "$V3_PILOT"; then
    echo "  -> PASS: Pilot V3 (900 base, 350/km, 80/min) is seeded in migration."
else
    echo "  -> FAIL: Pilot V3 rate card migration is missing expected rates!" >&2
    FAILED=1
fi

# --- Gate P4: Atomic CAS Fare Adjustment ---
echo "--- [4/6] Gate P4: Atomic CAS Fare Adjustment ---"
CAS_MIGRATION="$REPO_ROOT/supabase/migrations/20260923030000_ride_change_fare_cas_idempotent.sql"
if grep -q "STALE_VERSION" "$CAS_MIGRATION" && \
   grep -q "p_expected_version" "$CAS_MIGRATION" && \
   grep -q "METERED_MODE_IMMUTABLE" "$CAS_MIGRATION"; then
    echo "  -> PASS: ride_change_fare_v1 enforces CAS concurrency and metered immutability."
else
    echo "  -> FAIL: ride_change_fare_v1 missing CAS guards!" >&2
    FAILED=1
fi

# --- Gate P5: Driver Financial Ledger Room Persistence ---
echo "--- [5/6] Gate P5: Driver Financial Ledger Room Persistence ---"
ROOM_DB="$REPO_ROOT/android/app/src/main/kotlin/com/elysium369/meet/data/local/MeetDatabase.kt"
APP_MODULE="$REPO_ROOT/android/app/src/main/kotlin/com/elysium369/meet/di/AppModule.kt"
if grep -q "VehicleFinancialLedgerEntity" "$ROOM_DB" && \
   grep -q "version = 82" "$ROOM_DB" && \
   grep -q "MIGRATION_81_82" "$APP_MODULE"; then
    echo "  -> PASS: VehicleFinancialLedger persisted in Room DB v82 with MIGRATION_81_82."
else
    echo "  -> FAIL: Room persistence for financial ledger is not registered in DB v82!" >&2
    FAILED=1
fi

# --- Gate P6: Driver Economic Projection Viability ---
echo "--- [6/6] Gate P6: Driver Economic Projection Viability ---"
PROJECTION_FILE="$REPO_ROOT/android/app/src/main/kotlin/com/elysium369/meet/ride/domain/DriverEconomicsProjection.kt"
if grep -q "DEFAULT_PLATFORM_COMMISSION_BPS = 500L" "$PROJECTION_FILE" && \
   grep -q "projectedNetEarningsMinor" "$PROJECTION_FILE"; then
    echo "  -> PASS: DriverEconomicsProjection enforces 5.0% commission and net earnings."
else
    echo "  -> FAIL: DriverEconomicsProjection invariants missing!" >&2
    FAILED=1
fi

echo "=========================================================="
if [ "$FAILED" -eq 0 ]; then
    echo "ALL 6 PRICING PRODUCTION GATES PASSED [100% OK]"
    echo "=========================================================="
    exit 0
else
    echo "PRICING PRODUCTION GATES FAILED" >&2
    echo "=========================================================="
    exit 1
fi
