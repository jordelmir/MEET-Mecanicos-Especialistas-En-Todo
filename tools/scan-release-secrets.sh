#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

fail() {
  echo "release-secret-scan: $1" >&2
  exit 1
}

tracked_source_files() {
  git ls-files \
    'android/app/src/main/**' \
    'android/app/build.gradle.kts' \
    '*.properties' \
    ':!:android/app/src/main/assets/**' \
    ':!:**/build/**'
}

SOURCE_FILES="$(tracked_source_files)"
[[ -n "$SOURCE_FILES" ]] || fail "no tracked release sources found"

if printf '%s\n' "$SOURCE_FILES" | xargs grep -nE \
  '(service[_-]?role|SUPABASE_SERVICE|sk_live_[A-Za-z0-9]|AIzaSy[A-Za-z0-9_-]{25,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)' \
  >/tmp/meet-release-secret-scan.txt 2>/dev/null; then
  cat /tmp/meet-release-secret-scan.txt >&2
  fail "privileged credential pattern found in tracked release source"
fi

grep -q 'buildConfigField("String", "MINIMAX_API_KEY_DEBUG", "\\"\\"")' android/app/build.gradle.kts \
  || fail "release MiniMax credential is not forced empty"
grep -q 'buildConfigField("String", "CAR2DB_API_KEY", "\\"\\"")' android/app/build.gradle.kts \
  || fail "release Car2DB credential is not forced empty"

echo "release-secret-scan: OK"
