#!/usr/bin/env bash
# Run against the already isolated Safety PostgreSQL test cluster, never production.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
args=(-h "${1:?test socket required}" -p "${2:?test port required}" -d postgres -v ON_ERROR_STOP=1 -q)
run_dir="$(mktemp -d "${TMPDIR:-/tmp}/safety-intake-concurrency.XXXXXX")"
trap 'rm -rf -- "$run_dir"' EXIT
psql "${args[@]}" -f "$repo_root/tests/safety/safety-intake-concurrency-setup.sql"
psql "${args[@]}" -f "$repo_root/tests/safety/safety-intake-concurrent.sql" >"$run_dir/one.log" 2>&1 &
first_pid=$!
psql "${args[@]}" -f "$repo_root/tests/safety/safety-intake-concurrent.sql" >"$run_dir/two.log" 2>&1 &
second_pid=$!
first_status=0; wait "$first_pid" || first_status=$?
second_status=0; wait "$second_pid" || second_status=$?
if [[ "$first_status" == 0 && "$second_status" == 0 ]] || [[ "$first_status" != 0 && "$second_status" != 0 ]]; then
 cat "$run_dir/one.log" "$run_dir/two.log"
 echo 'Concurrent intake must accept exactly one command' >&2; exit 1
fi
grep -q 'SAFETY_RATE_LIMIT_EXCEEDED' "$run_dir/one.log" "$run_dir/two.log"
psql "${args[@]}" -f "$repo_root/tests/safety/safety-intake-concurrency-assert.sql"
echo 'Safety concurrency intake budget: PASS'
