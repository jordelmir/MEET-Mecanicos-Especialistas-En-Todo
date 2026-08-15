#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFIER="$ROOT_DIR/tools/android/verify-physical-device.sh"
APK="${1:-$ROOT_DIR/android/app/build/outputs/apk/debug/app-debug.apk}"

[[ -f "$APK" ]] || {
  echo "Verifier self-test requires a built APK: $APK" >&2
  exit 2
}

TEST_DIR="$(mktemp -d)"
cleanup() {
  [[ -n "$TEST_DIR" && -d "$TEST_DIR" ]] && rm -rf "$TEST_DIR"
}
trap cleanup EXIT INT TERM

cat >"$TEST_DIR/adb" <<'EOF'
#!/usr/bin/env bash
set -u

mode="${MEET_FAKE_ADB_MODE:-none}"
if [[ "${1:-}" == "devices" ]]; then
  echo "List of devices attached"
  [[ "$mode" == "emulator" ]] && printf 'emulator-5554\tdevice\n'
  exit 0
fi

if [[ "${1:-}" == "-s" ]]; then
  shift 2
fi
case "${1:-}" in
  get-state)
    echo "device"
    ;;
  shell)
    shift
    if [[ "${1:-}" == "getprop" && "${2:-}" == "ro.kernel.qemu" ]]; then
      echo "1"
    fi
    ;;
  *)
    echo "Unexpected fake adb call: $*" >&2
    exit 9
    ;;
esac
EOF
chmod +x "$TEST_DIR/adb"

bash -n "$VERIFIER"
bash "$VERIFIER" --help >/dev/null

set +e
no_device_output="$(PATH="$TEST_DIR:$PATH" MEET_FAKE_ADB_MODE=none \
  bash "$VERIFIER" --apk "$APK" 2>&1)"
no_device_status=$?
set -e
[[ "$no_device_status" -eq 2 ]]
grep -Fq "Expected exactly one online ADB device; found 0." <<<"$no_device_output"

set +e
emulator_output="$(PATH="$TEST_DIR:$PATH" MEET_FAKE_ADB_MODE=emulator \
  bash "$VERIFIER" --apk "$APK" 2>&1)"
emulator_status=$?
set -e
[[ "$emulator_status" -eq 3 ]]
grep -Fq "Rejected emulator/non-physical target: emulator-5554" <<<"$emulator_output"

echo "physical-device-verifier-self-test: OK"
