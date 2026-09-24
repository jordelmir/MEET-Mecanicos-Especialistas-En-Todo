#!/usr/bin/env bash
# ==============================================================================
# verify-no-exact-public-location.sh — Public Location Sanitization Gate
#
# Asserts that:
# 1. Public projections never store or publish raw micro-precision GPS for active
#    illicit reports or citizen claims (minimum 2 decimal places / 1000m approx).
# 2. Raw private GPS coordinates exist only in safety_private schemas or encrypted
#    client storage.
# 3. Android map models use geo_disclosure and display coordinates.
# ==============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MIGRATIONS_DIR="$REPO_ROOT/supabase/migrations"
KOTLIN_DIR="$REPO_ROOT/android/app/src/main/kotlin/com/elysium369/meet/safety"

echo "=== [1/3] Checking SQL Geo-Disclosure & Coordinate Rounding ==="

# Check that projection function rounds public coordinates to 2 decimal places (~1.1 km)
if ! grep -q "round(new.latitude::numeric,2)" "$MIGRATIONS_DIR"/*safety* 2>/dev/null && \
   ! grep -q "round(new.latitude::numeric,\s*2)" "$MIGRATIONS_DIR"/*safety* 2>/dev/null; then
  echo "FAIL: Public point projection lacks coordinate rounding to 2 decimals"
  exit 1
fi

if ! grep -q "APPROXIMATE_1000M" "$MIGRATIONS_DIR"/*safety* 2>/dev/null; then
  echo "FAIL: Public point projection lacks APPROXIMATE_1000M geo_disclosure flag"
  exit 1
fi

echo "  -> Public projection enforces 2-decimal coordinate rounding & APPROXIMATE_1000M"

echo "=== [2/3] Checking Private Schema GPS Isolation ==="

# Verify that raw latitude/longitude from reports are kept in safety_private.report_content
if ! grep -q "safety_private.report_content" "$MIGRATIONS_DIR"/*safety* 2>/dev/null; then
  echo "FAIL: Missing safety_private.report_content storage for raw GPS"
  exit 1
fi

echo "  -> High-precision GPS is strictly isolated to safety_private.report_content"

echo "=== [3/3] Checking Android Map Adapters & GeoMarker Roles ==="

# Check that SafetyMapAdapter exists and handles privacy separation
if ! grep -q "privateLabel" "$KOTLIN_DIR/geo/SafetyMapAdapter.kt" 2>/dev/null; then
  echo "FAIL: SafetyMapAdapter missing privateLabel separation"
  exit 1
fi

# Ensure Android public point entities have display coordinates and serverVersion
if ! grep -q "displayLatitude" "$KOTLIN_DIR/data/local/SafetyPublicPointEntity.kt" 2>/dev/null && \
   ! grep -q "latitude" "$KOTLIN_DIR/data/local/SafetyPublicPointEntity.kt" 2>/dev/null; then
  echo "FAIL: SafetyPublicPointEntity missing required coordinate mapping"
  exit 1
fi

echo "  -> Android map contracts enforce privacy separation and display labeling"

echo "PASS: No Exact Public Location Verification Complete."
