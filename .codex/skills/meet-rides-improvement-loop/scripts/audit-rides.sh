#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

ride_files="$(
  {
    find android/app/src/main/kotlin/com/elysium369/meet/ride -type f -name '*.kt' 2>/dev/null
    find android/app/src/main/kotlin/com/elysium369/meet/ui/screens -maxdepth 1 -type f \
      \( -iname '*ride*.kt' -o -iname '*trip*.kt' \) 2>/dev/null
  } | sort -u
)"

if [[ -z "$ride_files" ]]; then
  echo "ERROR: no ride source files found" >&2
  exit 1
fi

echo "MEET_RIDES_AUDIT_V1"
echo "branch=$(git branch --show-current)"
echo "source_files=$(printf '%s\n' "$ride_files" | wc -l | tr -d ' ')"
echo "source_loc=$(xargs wc -l <<<"$ride_files" | tail -1 | awk '{print $1}')"
echo "test_files=$(find android/app/src/test/kotlin/com/elysium369/meet/ride -type f -name '*.kt' 2>/dev/null | wc -l | tr -d ' ')"
echo "largest_sources:"
xargs wc -l <<<"$ride_files" | sort -nr | sed -n '2,11p'
echo "recent_churn:"
git log --since='90 days ago' --format= --name-only -- $ride_files \
  | sed '/^$/d' | sort | uniq -c | sort -nr | head -10
echo "risk_markers:"
rg -n --no-heading \
  'TODO|FIXME|NotImplementedError|error\\(|check\\(|require\\(|GlobalScope|!!|delay\\(|Random\\(' \
  $ride_files 2>/dev/null | head -40 || true
echo "brand_leaks:"
rg -ni --no-heading 'uber|didi|indriver' $ride_files 2>/dev/null | head -20 || true
echo "known_ride_lint_debt:"
if [[ -f android/app/lint-baseline.xml ]]; then
  rg -B6 \
    'file="src/main/kotlin/com/elysium369/meet/(ride/|ui/screens/(Ride|Trip))' \
    android/app/lint-baseline.xml 2>/dev/null \
    | rg 'id=|file=' \
    | head -60 || true
else
  echo "lint baseline unavailable"
fi
