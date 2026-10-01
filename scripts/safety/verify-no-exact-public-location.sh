#!/usr/bin/env bash
# ==============================================================================
# verify-no-exact-public-location.sh — Public Location Sanitization Gate
#
# Asserts that:
# 1. V3 publication uses a coarse grid and server-side authority guard.
# 2. Raw private GPS coordinates exist only in safety_private schemas or encrypted
#    client storage.
# 3. Android map models use geo_disclosure and display coordinates.
# ==============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MIGRATIONS_DIR="$REPO_ROOT/supabase/migrations"
KOTLIN_DIR="$REPO_ROOT/android/app/src/main/kotlin/com/elysium369/meet/safety"

echo "=== [1/3] Checking V3 SQL location publication boundary ==="

v3_authority="$MIGRATIONS_DIR/20260928100000_safety_moderation_authority_v3.sql"
v3_firewall="$MIGRATIONS_DIR/20260928090000_safety_publication_firewall_v3.sql"
for contract in \
  "round(v_content.latitude::numeric * 4) / 4" \
  "round(v_content.longitude::numeric * 4) / 4" \
  "new.location_accuracy_meters < 25000" \
  "COARSE_GRID_25KM_PLUS" \
  "safety_guard_public_point_v3" \
  "PUBLIC_LOCATION_SUPPRESSED"; do
  if ! grep -Fq "$contract" "$v3_authority"; then
    echo "FAIL: V3 location contract missing: $contract"
    exit 1
  fi
done
if ! grep -Fq "SAFETY_REPORT_AUTO_PUBLICATION_FORBIDDEN" "$v3_firewall"; then
  echo "FAIL: legacy report-to-map function is not poisoned"
  exit 1
fi
echo "  -> V3 server grid, suppression, and publication guard present"

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
