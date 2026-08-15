#!/usr/bin/env bash
set -Eeuo pipefail

PACKAGE="com.elysium369.meet"
ACTIVITY=".MainActivity"
EXPECTED_ONLINE_HOST="kluumjhzncitjayvvwtj.supabase.co"
APK="android/app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_ROOT="artifacts/physical-device"
SERIAL=""
SETTLE_SECONDS=15
APK_ONLY=false

usage() {
  cat <<'EOF'
Usage: bash tools/android/verify-physical-device.sh [options]

Installs and verifies MEET on one real Android device. Emulators are rejected.

Options:
  --serial SERIAL       ADB serial. Required when more than one device is online.
  --apk PATH            APK to install (default: debug APK).
  --output-root PATH    Ignored evidence directory.
  --settle-seconds N    Seconds to observe the launched process (default: 15).
  --apk-only            Verify the APK online contract without using ADB.
  --help                Show this help.
EOF
}

while (($#)); do
  case "$1" in
    --serial) SERIAL="${2:?missing value for --serial}"; shift 2 ;;
    --apk) APK="${2:?missing value for --apk}"; shift 2 ;;
    --output-root) OUTPUT_ROOT="${2:?missing value for --output-root}"; shift 2 ;;
    --settle-seconds) SETTLE_SECONDS="${2:?missing value for --settle-seconds}"; shift 2 ;;
    --apk-only) APK_ONLY=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "$SETTLE_SECONDS" =~ ^[1-9][0-9]*$ ]] || {
  echo "--settle-seconds must be a positive integer" >&2
  exit 2
}

for command_name in awk date find grep sed shasum sleep sort tail tee tr unzip; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "Required command not found: $command_name" >&2
    exit 2
  }
done
if [[ "$APK_ONLY" == false ]]; then
  command -v adb >/dev/null 2>&1 || {
    echo "Required command not found: adb" >&2
    exit 2
  }
fi

[[ -f "$APK" ]] || {
  echo "APK not found: $APK" >&2
  echo "Build it first with: (cd android && ./gradlew :app:assembleDebug)" >&2
  exit 2
}

if [[ "$APK_ONLY" == false ]]; then
  if [[ -z "$SERIAL" ]]; then
    ONLINE_DEVICE_LIST="$(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')"
    ONLINE_DEVICE_COUNT="$(awk 'NF {count++} END {print count + 0}' <<<"$ONLINE_DEVICE_LIST")"
    if [[ "$ONLINE_DEVICE_COUNT" -ne 1 ]]; then
      echo "Expected exactly one online ADB device; found $ONLINE_DEVICE_COUNT." >&2
      echo "Pass --serial when more than one physical device is connected." >&2
      exit 2
    fi
    SERIAL="$ONLINE_DEVICE_LIST"
  fi

  ADB=(adb -s "$SERIAL")
  [[ "$("${ADB[@]}" get-state 2>/dev/null)" == "device" ]] || {
    echo "ADB device is not online: $SERIAL" >&2
    exit 2
  }

  KERNEL_QEMU="$("${ADB[@]}" shell getprop ro.kernel.qemu | tr -d '\r')"
  HARDWARE="$("${ADB[@]}" shell getprop ro.hardware | tr -d '\r')"
  PRODUCT="$("${ADB[@]}" shell getprop ro.product.name | tr -d '\r')"
  if [[ "$KERNEL_QEMU" == "1" || "$SERIAL" == emulator-* || "$HARDWARE" == *ranchu* || "$PRODUCT" == sdk_* ]]; then
    echo "Rejected emulator/non-physical target: $SERIAL" >&2
    exit 3
  fi
fi

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
AAPT2="$(find "$SDK_ROOT/build-tools" -mindepth 2 -maxdepth 2 -type f -name aapt2 2>/dev/null | sort -V | tail -1)"
[[ -x "$AAPT2" ]] || {
  echo "aapt2 not found below $SDK_ROOT/build-tools" >&2
  exit 2
}

BADGING="$($AAPT2 dump badging "$APK")"
APK_PACKAGE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$BADGING")"
TARGET_SDK="$(sed -n "s/^targetSdkVersion:'\([^']*\)'.*/\1/p" <<<"$BADGING")"
VERSION_CODE="$(sed -n "s/^package: .*versionCode='\([^']*\)'.*/\1/p" <<<"$BADGING")"
VERSION_NAME="$(sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p" <<<"$BADGING")"

[[ "$APK_PACKAGE" == "$PACKAGE" ]] || {
  echo "Unexpected APK package: $APK_PACKAGE (expected $PACKAGE)" >&2
  exit 3
}
[[ "$TARGET_SDK" =~ ^[0-9]+$ && "$TARGET_SDK" -ge 36 ]] || {
  echo "APK does not satisfy Google Play target API 36: targetSdk=$TARGET_SDK" >&2
  exit 3
}
grep -Fq "uses-permission: name='android.permission.INTERNET'" <<<"$BADGING" || {
  echo "APK does not declare android.permission.INTERNET" >&2
  exit 3
}
unzip -p "$APK" 'classes*.dex' | LC_ALL=C grep -aF "$EXPECTED_ONLINE_HOST" >/dev/null || {
  echo "APK does not contain the expected protected Supabase production host" >&2
  exit 3
}
if unzip -p "$APK" 'classes*.dex' | LC_ALL=C grep -aE 'localhost:3000|127\.0\.0\.1:3000' >/dev/null; then
  echo "APK contains a forbidden localhost backend reference" >&2
  exit 3
fi
if [[ "$APK_ONLY" == true ]]; then
  echo "package=$APK_PACKAGE"
  echo "version_name=$VERSION_NAME"
  echo "version_code=$VERSION_CODE"
  echo "target_sdk=$TARGET_SDK"
  echo "internet_permission=present"
  echo "production_backend_host=present"
  echo "localhost_backend_reference=absent"
  echo "apk_preflight=PASS"
  exit 0
fi

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
EVIDENCE_DIR="$OUTPUT_ROOT/$TIMESTAMP-$SERIAL"
mkdir -p "$EVIDENCE_DIR"
SUMMARY="$EVIDENCE_DIR/summary.txt"
GLOBAL_LOG="$EVIDENCE_DIR/logcat-launch-window.txt"
exec > >(tee "$SUMMARY") 2>&1

LOGGER_PID=""
cleanup() {
  if [[ -n "$LOGGER_PID" ]]; then
    kill "$LOGGER_PID" 2>/dev/null || true
    wait "$LOGGER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

MANUFACTURER="$("${ADB[@]}" shell getprop ro.product.manufacturer | tr -d '\r')"
MODEL="$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
ANDROID_VERSION="$("${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r')"
DEVICE_SDK="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
SECURITY_PATCH="$("${ADB[@]}" shell getprop ro.build.version.security_patch | tr -d '\r')"
APK_SHA256="$(shasum -a 256 "$APK" | awk '{print $1}')"

echo "MEET physical-device verification"
echo "timestamp_utc=$TIMESTAMP"
echo "serial=$SERIAL"
echo "device=$MANUFACTURER $MODEL"
echo "android=$ANDROID_VERSION"
echo "device_sdk=$DEVICE_SDK"
echo "security_patch=$SECURITY_PATCH"
echo "apk=$APK"
echo "apk_sha256=$APK_SHA256"
echo "package=$APK_PACKAGE"
echo "version_name=$VERSION_NAME"
echo "version_code=$VERSION_CODE"
echo "target_sdk=$TARGET_SDK"
echo "internet_permission=present"
echo "production_backend_host=present"
echo "localhost_backend_reference=absent"

"${ADB[@]}" logcat -v threadtime -T 1 >"$GLOBAL_LOG" 2>&1 &
LOGGER_PID=$!

echo "install_begin=true"
"${ADB[@]}" install -r -d -g "$APK"
echo "install_success=true"

"${ADB[@]}" shell am force-stop "$PACKAGE"
START_OUTPUT="$("${ADB[@]}" shell am start -W -n "$PACKAGE/$ACTIVITY")"
printf '%s\n' "$START_OUTPUT" | tee "$EVIDENCE_DIR/am-start.txt"
grep -Fq "Status: ok" <<<"$START_OUTPUT" || {
  echo "launch_success=false"
  exit 4
}
echo "launch_success=true"

sleep "$SETTLE_SECONDS"
PID="$("${ADB[@]}" shell pidof "$PACKAGE" | tr -d '\r' | awk '{print $1}')"
[[ -n "$PID" ]] || {
  echo "process_alive=false"
  exit 4
}
echo "process_alive=true"
echo "pid=$PID"

ACTIVITY_STATE="$("${ADB[@]}" shell dumpsys activity activities)"
printf '%s\n' "$ACTIVITY_STATE" >"$EVIDENCE_DIR/activity.txt"
grep -E "topResumedActivity=.*${PACKAGE}/.*MainActivity|mResumedActivity:.*${PACKAGE}/.*MainActivity" <<<"$ACTIVITY_STATE" >/dev/null || {
  echo "main_activity_foreground=false"
  exit 4
}
echo "main_activity_foreground=true"

PACKAGE_STATE="$("${ADB[@]}" shell dumpsys package "$PACKAGE")"
printf '%s\n' "$PACKAGE_STATE" >"$EVIDENCE_DIR/package.txt"

CONNECTIVITY_STATE="$("${ADB[@]}" shell dumpsys connectivity)"
printf '%s\n' "$CONNECTIVITY_STATE" >"$EVIDENCE_DIR/connectivity.txt"
grep -q "NET_CAPABILITY_VALIDATED" <<<"$CONNECTIVITY_STATE" || {
  echo "validated_internet_network=false"
  exit 5
}
echo "validated_internet_network=true"

"${ADB[@]}" logcat -d -v threadtime --pid="$PID" >"$EVIDENCE_DIR/logcat-process.txt" 2>&1 || true
cleanup
LOGGER_PID=""

CRASH_PATTERN="FATAL EXCEPTION|AndroidRuntime|ANR in ${PACKAGE}|am_crash.*${PACKAGE}|am_anr.*${PACKAGE}|Process: ${PACKAGE}"
if grep -E "$CRASH_PATTERN" "$EVIDENCE_DIR/logcat-process.txt" "$GLOBAL_LOG" >/dev/null; then
  echo "fatal_or_anr_detected=true"
  grep -En "$CRASH_PATTERN" "$EVIDENCE_DIR/logcat-process.txt" "$GLOBAL_LOG" \
    >"$EVIDENCE_DIR/fatal-or-anr.txt" || true
  exit 6
fi
echo "fatal_or_anr_detected=false"
echo "result=PASS"
echo "evidence_dir=$EVIDENCE_DIR"
