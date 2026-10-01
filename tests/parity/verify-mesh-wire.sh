#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
kotlin_result="${1:-$repo_root/android/app/build/reports/parity/mesh-wire-v1.json}"
if [[ ! -s "$kotlin_result" ]]; then
  echo "Mesh Kotlin result missing. Run MeshWireParityTest; TS-only is not cross-runtime proof." >&2
  exit 2
fi
cd "$repo_root"
npx --no-install tsx tests/parity/mesh-wire-parity.ts tests/parity/fixtures/mesh-wire-v1.json "$kotlin_result"
