#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
corpus="$repo_root/fixtures/obd-conformance"
manifests=()
while IFS= read -r -d '' file; do manifests+=("$file"); done < <(find "$corpus/hardware-runs" "$corpus/golden-traces" -type f -name '*.manifest.json' -print0)

if [[ ${#manifests[@]} -eq 0 ]]; then
  echo "PENDING_EXTERNAL_FIXTURE: no physical conformance manifest is available" >&2
  exit 20
fi

for manifest in "${manifests[@]}"; do
  jq -e '
    .schemaVersion == 1 and
    (.runId|length) >= 16 and
    (.files|length) > 0 and
    (.artifacts|length) > 0 and
    .review.state == "REVIEWED" and
    .state == "CERTIFIED" and
    .fixtureType != "REPLAY_ONLY" and
    (.referenceAuthority.signature|length) > 0 and
    (.expected.ecu|length) > 0
  ' "$manifest" >/dev/null || {
    echo "Manifest is not a certified physical authority: $manifest" >&2
    exit 23
  }
  base="$(dirname "$manifest")"
  while IFS=$'\t' read -r relative expected; do
    target="$base/$relative"
    [[ -f "$target" ]] || { echo "Missing evidence file: $target" >&2; exit 21; }
    actual="$(shasum -a 256 "$target" | awk '{print $1}')"
    [[ "$actual" == "$expected" ]] || { echo "Hash mismatch: $target" >&2; exit 22; }
  done < <(jq -r '.files[] | [.path,.sha256] | @tsv' "$manifest")
done
