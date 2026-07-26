#!/usr/bin/env bash
# Cross-runtime parity CI workflow.
#
# Runs the TypeScript parity verifier AND the Kotlin parity test, then
# compares both against the same JSON fixture. Either side failing is a
# build break.
#
# The Kotlin side is invoked through gradle's `test` task on the
# `HashEngineParityTest` class which produces the canonical string +
# SHA-256 of the same input and writes it to a known location
# (build/reports/parity/snapshot-p0230.txt). The bash script reads
# both and asserts equality.
#
# Local invocation:
#   ./tests/parity/ci-verify.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FIXTURE="$REPO_ROOT/tests/parity/fixtures/snapshot-p0230.json"
TS_RESULT="$(mktemp)"
TS_STDERR="$(mktemp)"
KOTLIN_RESULT="$(mktemp)"
trap 'rm -f "$TS_RESULT" "$TS_STDERR" "$KOTLIN_RESULT"' EXIT

echo "=== TS parity ==="
( cd "$REPO_ROOT" && npm_config_update_notifier=false npx tsx tests/parity/hash-parity.ts "$FIXTURE" > "$TS_RESULT" 2> "$TS_STDERR" ) || {
  cat "$TS_RESULT"
  cat "$TS_STDERR" >&2
  echo "TS parity FAILED"
  exit 1
}
cat "$TS_RESULT"

echo
echo "=== Kotlin parity ==="
if [[ -f "$REPO_ROOT/android/gradlew" ]]; then
  ( cd "$REPO_ROOT/android" && ./gradlew --no-parallel :app:testDebugUnitTest --tests 'com.elysium369.meet.core.reports.HashEngineParityTest' --info --quiet ) || {
    echo "Kotlin parity test failed. Cross-runtime verification is mandatory."
    exit 1
  }
  KOTLIN_HASH_FILE="$REPO_ROOT/android/app/build/reports/parity/snapshot-p0230.txt"
  if [[ ! -f "$KOTLIN_HASH_FILE" ]]; then
    echo "Kotlin parity did not produce the expected output file."
    exit 1
  fi
  cat "$KOTLIN_HASH_FILE" > "$KOTLIN_RESULT"
else
  echo "android/gradlew not found; skipping Kotlin parity."
  exit 0
fi

echo
echo "=== Compare ==="
if diff -q "$TS_RESULT" "$KOTLIN_RESULT" > /dev/null; then
  echo "TS and Kotlin produced identical output. Cross-runtime parity OK."
else
  echo "TS and Kotlin DIVERGED. See diff:"
  diff "$TS_RESULT" "$KOTLIN_RESULT" || true
  exit 1
fi
