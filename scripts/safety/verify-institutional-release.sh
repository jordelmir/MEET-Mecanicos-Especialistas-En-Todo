#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
task_apk="${1:?Provide signed institutional APK path}"
task_aab="${2:?Provide signed institutional AAB path}"
task_expected_cert="${SAFETY_INSTITUTIONAL_CERT_SHA256:?Configure the approved institutional signing certificate SHA256}"
task_apksigner="${APKSIGNER_PATH:?Configure Android SDK apksigner path}"
[[ "$task_expected_cert" =~ ^[a-fA-F0-9]{64}$ ]] || { echo 'Invalid approved signing certificate'; exit 1; }
[[ -s "$task_apk" && -s "$task_aab" ]] || { echo 'Signed artifacts unavailable'; exit 1; }
task_tmp="$(mktemp -d "${TMPDIR:-/tmp}/safety-release-proof.XXXXXX")"
trap 'rm -rf -- "$task_tmp"' EXIT
"$task_apksigner" verify --verbose --print-certs "$task_apk" > "$task_tmp/apk.txt"
task_actual_cert="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$task_tmp/apk.txt" | tr '[:upper:]' '[:lower:]')"
[[ "$task_actual_cert" == "$(printf '%s' "$task_expected_cert" | tr '[:upper:]' '[:lower:]')" ]] || { echo 'APK signing certificate is not the approved institutional certificate'; exit 1; }
jarsigner -verify "$task_aab" > "$task_tmp/aab.txt" 2>&1
rg -q 'jar verified' "$task_tmp/aab.txt" || { echo 'AAB signature is not verified'; exit 1; }
keytool -printcert -jarfile "$task_aab" > "$task_tmp/aab-cert.txt"
task_aab_cert="$(sed -n 's/.*SHA256: //p' "$task_tmp/aab-cert.txt" | head -1 | tr -d ':' | tr '[:upper:]' '[:lower:]')"
[[ "$task_aab_cert" == "$task_actual_cert" ]] || { echo 'APK/AAB signing certificate mismatch'; exit 1; }
[[ -s "$task_root/android/app/build/reports/sbom/meet-release.cdx.json" ]] || { echo 'Resolved SBOM missing'; exit 1; }
python3 "$task_root/tools/vehicle-truth/validate-sbom.py" "$task_root/android/app/build/reports/sbom/meet-release.cdx.json"
echo 'Institutional artifact signatures and SBOM: PASS'
echo 'Provenance attestation, deployed SHA, physical tests and external reviews remain independently required.'
