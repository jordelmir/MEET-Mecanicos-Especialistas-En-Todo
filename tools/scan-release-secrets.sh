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
  '(sb_secret_[A-Za-z0-9_-]{16,}|sk_live_[A-Za-z0-9]{16,}|AIzaSy[A-Za-z0-9_-]{25,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)' \
  >/tmp/meet-release-secret-scan.txt 2>/dev/null; then
  cat /tmp/meet-release-secret-scan.txt >&2
  fail "privileged credential pattern found in tracked release source"
fi

grep -q 'buildConfigField("String", "MINIMAX_API_KEY_DEBUG", "\\"\\"")' android/app/build.gradle.kts \
  || fail "release MiniMax credential is not forced empty"
grep -q 'buildConfigField("String", "CAR2DB_API_KEY", "\\"\\"")' android/app/build.gradle.kts \
  || fail "release Car2DB credential is not forced empty"

if [[ -n "${MEET_RELEASE_APK:-}" ]]; then
  [[ -f "$MEET_RELEASE_APK" ]] || fail "release APK not found: $MEET_RELEASE_APK"
  APK_SCAN_FILE="$(mktemp)"
  trap 'rm -f "$APK_SCAN_FILE"' EXIT
  while IFS= read -r entry; do
    unzip -p "$MEET_RELEASE_APK" "$entry" 2>/dev/null | strings >> "$APK_SCAN_FILE"
  done < <(unzip -Z1 "$MEET_RELEASE_APK" | grep -E '(^classes[0-9]*\.dex$|resources\.arsc$|^res/raw/|^assets/)' || true)
  if grep -aEq \
    '(sb_secret_[A-Za-z0-9_-]{16,}|sk_live_[A-Za-z0-9]{16,}|AIzaSy[A-Za-z0-9_-]{25,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)' \
    "$APK_SCAN_FILE"; then
    fail "privileged credential signature found inside release APK"
  fi
fi

echo "release-secret-scan: OK"
