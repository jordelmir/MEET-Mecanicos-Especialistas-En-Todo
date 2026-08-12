#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GUARD="$ROOT_DIR/tools/verify-production-guards.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

fail() {
  echo "production-guard-self-test: $1" >&2
  exit 1
}

make_fixture() {
  local target="$1"
  mkdir -p "$target"
  git -C "$ROOT_DIR" archive --format=tar HEAD | tar -xf - -C "$target"
}

expect_failure() {
  local name="$1"
  local fixture="$TMP_DIR/$name"
  shift
  make_fixture "$fixture"
  "$@" "$fixture"
  if MEET_GUARD_ROOT="$fixture" bash "$fixture/tools/verify-production-guards.sh" >/dev/null 2>&1; then
    fail "$name did not make the guard fail"
  fi
}

inject_vanguard_stub() {
  mkdir -p "$1/android/app/src/main/kotlin/com/elysium369/meet/core/vanguard"
  : > "$1/android/app/src/main/kotlin/com/elysium369/meet/core/vanguard/VanguardCoreStubs.kt"
}

inject_stub_marker() {
  printf '%s\n' '// vanguard-stub-forbidden' >> "$1/android/app/src/main/kotlin/com/elysium369/meet/core/vanguard/VanguardCore.kt"
}

inject_direct_dtc() {
  printf '%s\n' 'package guard.fixture' 'fun forbidden() = DtcRecord("P0001")' \
    > "$1/android/app/src/main/kotlin/GuardDirectDtc.kt"
}

inject_null_zero() {
  printf '%s\n' 'val forbiddenGuardFixture = nullableValue ?: 0f' \
    >> "$1/android/app/src/main/kotlin/com/elysium369/meet/ui/screens/DtcRepairGuideScreen.kt"
}

inject_active_command() {
  printf '%s\n' 'fun forbiddenGuardFixture() = sendRawCommand("3101000D")' \
    > "$1/android/app/src/main/kotlin/com/elysium369/meet/ui/GuardActiveCommand.kt"
}

inject_unsafe_uds() {
  printf '%s\n' 'fun forbiddenGuardFixture() = sendRawCommand("2F0101")' \
    > "$1/android/app/src/main/kotlin/com/elysium369/meet/core/obd/GuardUnsafeUds.kt"
}

# Prove both directions: a clean committed tree succeeds, every adversarial
# mutation is rejected. A guard without negative proof is not a release gate.
clean="$TMP_DIR/clean"
make_fixture "$clean"
MEET_GUARD_ROOT="$clean" bash "$clean/tools/verify-production-guards.sh" >/dev/null || \
  fail "clean tree was rejected"

expect_failure vanguard-stub inject_vanguard_stub
expect_failure stub-marker inject_stub_marker
expect_failure direct-dtc-record inject_direct_dtc
expect_failure null-as-zero inject_null_zero
expect_failure hardcoded-active-command inject_active_command
expect_failure unsafe-uds-call inject_unsafe_uds

echo "production-guard-self-test: OK"
