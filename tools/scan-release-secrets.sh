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
  # Stream selected ZIP entries with bounded memory. The previous
  # unzip|strings aggregation produced a multi-gigabyte temporary file for the
  # large offline APK; macOS could kill grep and the shell then misreported OK.
  # This scanner retains overlap between chunks so signatures split at a read
  # boundary are still detected, and never prints credential material.
  if ! python3 - "$MEET_RELEASE_APK" <<'PY'
import re
import sys
import zipfile

apk_path = sys.argv[1]
entry_pattern = re.compile(r"(?:^classes[0-9]*\.dex$|^resources\.arsc$|^res/raw/|^assets/)")
secret_pattern = re.compile(
    rb"(?:sb_secret_[A-Za-z0-9_-]{16,}|sk_live_[A-Za-z0-9]{16,}|"
    rb"AIzaSy[A-Za-z0-9_-]{25,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)"
)
overlap_size = 256

with zipfile.ZipFile(apk_path) as archive:
    for info in archive.infolist():
        if info.is_dir() or not entry_pattern.search(info.filename):
            continue
        overlap = b""
        with archive.open(info) as source:
            while True:
                chunk = source.read(1024 * 1024)
                if not chunk:
                    break
                candidate = overlap + chunk
                if secret_pattern.search(candidate):
                    print(f"release-secret-scan: credential signature in APK entry {info.filename}", file=sys.stderr)
                    raise SystemExit(1)
                overlap = candidate[-overlap_size:]
PY
  then
    fail "privileged credential signature found or APK scan failed"
  fi
fi

echo "release-secret-scan: OK"
