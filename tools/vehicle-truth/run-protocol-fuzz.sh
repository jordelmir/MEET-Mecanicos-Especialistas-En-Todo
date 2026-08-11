#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
: "${JAZZER_BIN:?Set JAZZER_BIN to a pinned Jazzer executable}"
: "${FUZZ_CLASSPATH:?Set FUZZ_CLASSPATH to the compiled test runtime classpath}"

exec "$JAZZER_BIN" \
  --cp="$FUZZ_CLASSPATH" \
  --target_class=com.elysium369.meet.core.obd.fuzz.DiagnosticProtocolFuzzTargets \
  "$repo_root/fixtures/obd-conformance/golden-traces" \
  -max_len=65536 \
  -timeout=10 \
  "$@"
