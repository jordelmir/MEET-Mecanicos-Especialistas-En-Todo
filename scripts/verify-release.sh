#!/usr/bin/env bash

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

HEAD="$(git rev-parse HEAD)"

echo "=== MEET Production Release Gate Verification ==="
echo "Verifying commit HEAD=$HEAD"

cd android

echo "--> Running lintRelease, testDebugUnitTest, and bundleRelease..."
./gradlew \
  :app:lintRelease \
  :app:testDebugUnitTest \
  :app:bundleRelease

cd "$ROOT"

AAB="android/app/build/outputs/bundle/release/app-release.aab"

if [[ ! -s "$AAB" ]]; then
  echo "FATAL: release AAB missing at $AAB" >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "--> Validating AAB bundle contents and security properties..."

if command -v bundletool >/dev/null 2>&1; then
  echo "Using bundletool to validate and dump manifest..."
  bundletool validate --bundle="$AAB"
  bundletool dump manifest --bundle="$AAB" --module=base > "$TMP/manifest.xml"
else
  echo "bundletool not in PATH; using python zip/proto bundle analyzer..."
  python3 -c "
import zipfile, sys

aab_path = '$AAB'
with zipfile.ZipFile(aab_path, 'r') as z:
    names = z.namelist()
    for name in names:
        if 'AiAutomationReceiver' in name:
            print(f'FATAL: Found {name} inside AAB bundle', file=sys.stderr)
            sys.exit(1)
        if name.startswith('base/dex/') and name.endswith('.dex'):
            dex_data = z.read(name)
            if b'AiAutomationReceiver' in dex_data:
                print(f'FATAL: AiAutomationReceiver found in {name}', file=sys.stderr)
                sys.exit(1)
    if 'base/manifest/AndroidManifest.xml' in names:
        raw_manifest = z.read('base/manifest/AndroidManifest.xml')
        if b'AiAutomationReceiver' in raw_manifest:
            print('FATAL: AiAutomationReceiver declared in base/manifest/AndroidManifest.xml', file=sys.stderr)
            sys.exit(1)
        if b'debuggable\x08\x01' in raw_manifest or b'debuggable=\"true\"' in raw_manifest:
            print('FATAL: AAB marked debuggable=true', file=sys.stderr)
            sys.exit(1)
        if b'usesCleartextTraffic\x08\x01' in raw_manifest or b'usesCleartextTraffic=\"true\"' in raw_manifest:
            print('FATAL: AAB allows cleartext traffic', file=sys.stderr)
            sys.exit(1)
"
  # Also check intermediate merged release manifest
  MERGED_MANIFEST="android/app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
  if [[ -f "$MERGED_MANIFEST" ]]; then
    cp "$MERGED_MANIFEST" "$TMP/manifest.xml"
  else
    # Check packaged manifest
    find android/app/build -name "AndroidManifest.xml" -path "*release*" -exec cp {} "$TMP/manifest.xml" \; 2>/dev/null || touch "$TMP/manifest.xml"
  fi
fi

if [[ -f "$TMP/manifest.xml" && -s "$TMP/manifest.xml" ]]; then
  if grep -qi 'AiAutomationReceiver' "$TMP/manifest.xml"; then
    echo "FATAL: automation receiver leaked into release manifest" >&2
    exit 1
  fi

  if grep -q 'android:debuggable="true"' "$TMP/manifest.xml"; then
    echo "FATAL: release is debuggable" >&2
    exit 1
  fi

  if grep -q 'android:usesCleartextTraffic="true"' "$TMP/manifest.xml"; then
    echo "FATAL: cleartext traffic enabled" >&2
    exit 1
  fi
fi

echo "--> Generating cryptographic hashes and provenance markers..."
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$AAB" | tee "$AAB.sha256"
else
  shasum -a 256 "$AAB" | tee "$AAB.sha256"
fi

printf '%s\n' "$HEAD" | tee "$AAB.gitsha"

echo "=== Release policy verification PASSED ==="
