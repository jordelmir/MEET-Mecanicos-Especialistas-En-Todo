#!/usr/bin/env bash
set -euo pipefail

apk_path="${1:-app/build/outputs/apk/release/app-release.apk}"
test -f "$apk_path"
unzip -tq "$apk_path" >/dev/null

sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_dir" && -f local.properties ]]; then
  sdk_dir="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
fi
apksigner_bin="$(find "$sdk_dir/build-tools" -maxdepth 2 -type f -name apksigner 2>/dev/null | sort -V | tail -1)"
aapt_bin="$(find "$sdk_dir/build-tools" -maxdepth 2 -type f -name aapt 2>/dev/null | sort -V | tail -1)"
test -x "$apksigner_bin"
test -x "$aapt_bin"

for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  # Let grep consume the whole listing. With pipefail, grep -q can close the
  # pipe early and turn a valid unzip into the false exit code 141 (SIGPIPE).
  unzip -l "$apk_path" "lib/$abi/libmaplibre.so" | grep "libmaplibre.so" >/dev/null
done

"$apksigner_bin" verify --verbose "$apk_path" | grep "Verified using v2 scheme (APK Signature Scheme v2): true" >/dev/null
badging="$("$aapt_bin" dump badging "$apk_path" 2>/dev/null || true)"
package_line="$(printf '%s\n' "$badging" | sed -n '1p')"
[[ "$package_line" == package:* ]]
printf '%s\n' "$package_line"
shasum -a 256 "$apk_path"
