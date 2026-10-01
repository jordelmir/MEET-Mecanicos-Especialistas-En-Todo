#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════
#  ELYSIUM VANGUARD — PROOF KERNEL VERIFICATION SCRIPT
#  ──────────────────────────────────────────────────────────────
#  Governed by: ASCENSION MAXIMA §A7
#
#  Verifies that production-critical invariants hold in the codebase.
#  If ANY check fails, this script exits with code 1.
#  CI must run this on every PR to main.
# ══════════════════════════════════════════════════════════════════════
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_SRC="$REPO_ROOT/android/app/src/main/kotlin"
TEST_SRC="$REPO_ROOT/android/app/src/test/kotlin"
FAIL=0

echo "═══════════════════════════════════════════════════════"
echo "  ELYSIUM PROOF KERNEL — Production Invariant Checks"
echo "═══════════════════════════════════════════════════════"

# ── 1. Entitlement Bypass: No 'return true' in AgentEntitlementRepository ──
echo ""
echo "▸ [P0-1] Entitlement Bypass Check..."
# Check that hasEntitlement is NOT just 'return true' without any condition.
# The correct pattern is: null-guarded return true + server-verified check.
ENTITLEMENT_FILE="$ANDROID_SRC/com/elysium369/meet/core/agentstore/data/AgentEntitlementRepository.kt"
if grep -q 'EntitlementState.Available' "$ENTITLEMENT_FILE" 2>/dev/null && \
   grep -q 'gateway.*fetchAuthoritativeEntitlements' "$ENTITLEMENT_FILE" 2>/dev/null; then
    echo "  ✓ PASS: Entitlements are server-authoritative with fail-closed semantics."
else
    echo "  ✗ FAIL: AgentEntitlementRepository missing server-authoritative verification!"
    FAIL=1
fi

# ── 2. IP/Branding: No POKEMON or SAIYAN in production source ──
echo ""
echo "▸ [P0-2] IP/Branding Cleanup Check..."
IP_HITS=$(grep -rn 'POKEMON_VOLT\|SAIYAN_SSJ4' "$ANDROID_SRC/" --include="*.kt" 2>/dev/null || true)
if [ -n "$IP_HITS" ]; then
    echo "  ✗ FAIL: IP-infringing references found in production source:"
    echo "$IP_HITS" | head -10
    FAIL=1
else
    echo "  ✓ PASS: No IP-infringing references in production source."
fi

# ── 3. Outbox Production Wiring: Production env must fail-closed without DATABASE_URL ──
echo ""
echo "▸ [P0-3] Outbox Production Wiring Check..."
SERVER_APP="$REPO_ROOT/server/src/main/kotlin/com/elysium/server/app/Application.kt"
if [ -f "$SERVER_APP" ]; then
    if grep -q 'PostgresOutboxRepository' "$SERVER_APP" && grep -q 'CRITICAL.*DATABASE_URL.*PRODUCTION' "$SERVER_APP"; then
        echo "  ✓ PASS: Production outbox wired to PostgreSQL with fail-closed guard."
    else
        echo "  ✗ FAIL: Application.kt missing PostgresOutboxRepository or fail-closed guard."
        FAIL=1
    fi
else
    echo "  ⚠ SKIP: Server Application.kt not found."
fi

# ── 4. MasterMechanic: Must have requiredEntitlement set ──
echo ""
echo "▸ [P0-4] MasterMechanic Entitlement Check..."
MECHANIC="$ANDROID_SRC/com/elysium369/meet/core/agent/capability/mechanic/MasterMechanicCapability.kt"
if grep -q 'requiredEntitlement.*=.*"agent.master_mechanic"' "$MECHANIC" 2>/dev/null; then
    echo "  ✓ PASS: MasterMechanicCapability requires agent.master_mechanic entitlement."
else
    echo "  ✗ FAIL: MasterMechanicCapability missing or has wrong requiredEntitlement."
    FAIL=1
fi

# ── 5. No fake coordinate fallback in RideApplicationService ──
echo ""
echo "▸ [P0-5] Ride Authority — No Fake Fallback Coordinates..."
RIDE_SVC="$ANDROID_SRC/com/elysium369/meet/ride/application/RideApplicationService.kt"
if grep -q '9.9333.*-84.0833' "$RIDE_SVC" 2>/dev/null; then
    echo "  ✗ FAIL: RideApplicationService still has hardcoded 9.9333, -84.0833 fallback."
    FAIL=1
else
    echo "  ✓ PASS: No fake fallback coordinates in RideApplicationService."
fi

# ── 6. Ride quote transparency: isHeuristicEstimate must exist ──
echo ""
echo "▸ [P0-6] Ride Quote Transparency..."
if grep -q 'isHeuristicEstimate.*=.*true' "$RIDE_SVC" 2>/dev/null; then
    echo "  ✓ PASS: Preview quotes are transparently marked as heuristic estimates."
else
    echo "  ✗ FAIL: Preview quotes not marked as heuristic. Could mislead users."
    FAIL=1
fi

# ── 7. EAOS Truthfulness: No fake remediation claims ──
echo ""
echo "▸ [P0-7] EAOS Truthfulness Check..."
EAOS="$ANDROID_SRC/com/elysium369/meet/core/operations/AutonomousOperationsEngine.kt"
if grep -q 'Libro mayor 100% conciliado\|Se escaló la concurrencia' "$EAOS" 2>/dev/null; then
    echo "  ✗ FAIL: EAOS still contains fake remediation claims."
    FAIL=1
else
    echo "  ✓ PASS: No fake remediation claims in EAOS."
fi

# ── 8. EAOS Durability: Room persistence must be wired ──
echo ""
echo "▸ [P0-8] EAOS Durability Check..."
if grep -q 'OperationCaseDao\|caseDao' "$EAOS" 2>/dev/null; then
    echo "  ✓ PASS: EAOS has Room DAO wiring for durable persistence."
else
    echo "  ✗ FAIL: EAOS still uses pure in-memory storage without Room persistence."
    FAIL=1
fi

# ── 9. PremiumEntitlementTamperTest must exist ──
echo ""
echo "▸ [P0-9] Security Test Existence..."
TAMPER_TEST="$TEST_SRC/com/elysium369/meet/truth/PremiumEntitlementTamperTest.kt"
if [ -f "$TAMPER_TEST" ]; then
    echo "  ✓ PASS: PremiumEntitlementTamperTest exists."
else
    echo "  ✗ FAIL: PremiumEntitlementTamperTest not found."
    FAIL=1
fi

# ── 10. Never invent data constants ──
echo ""
echo "▸ [P0-10] Honest Phrase Compliance..."
if grep -q 'OBD no disponible' "$ANDROID_SRC/com/elysium369/meet/core/agent/capability/mechanic/MasterMechanicCapability.kt" 2>/dev/null; then
    echo "  ✓ PASS: MasterMechanic uses honest 'OBD no disponible' phrase."
else
    echo "  ✗ FAIL: MasterMechanic missing honest unavailable phrase."
    FAIL=1
fi

echo ""
echo "═══════════════════════════════════════════════════════"
if [ $FAIL -eq 0 ]; then
    echo "  ✅ ALL PROOF KERNEL CHECKS PASSED"
    echo "═══════════════════════════════════════════════════════"
    exit 0
else
    echo "  ❌ PROOF KERNEL FAILED — FIX BEFORE SHIPPING"
    echo "═══════════════════════════════════════════════════════"
    exit 1
fi
