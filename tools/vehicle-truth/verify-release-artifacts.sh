#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
budgets="$repo_root/tools/vehicle-truth/release-size-budgets.json"
manifest="${1:-$repo_root/android/app/build/outputs/vehicle-truth-release-manifest.json}"
sbom="$repo_root/android/app/build/reports/sbom/meet-release.cdx.json"
mkdir -p "$(dirname "$manifest")"
[[ -f "$sbom" ]] || { echo "Missing resolved release SBOM: $sbom" >&2; exit 30; }
python3 "$repo_root/tools/vehicle-truth/validate-sbom.py" "$sbom"

artifacts=(
  "debugApk|$repo_root/android/app/build/outputs/apk/debug/app-debug.apk"
  "releaseApk|$repo_root/android/app/build/outputs/apk/release/app-release.apk"
  "releaseAab|$repo_root/android/app/build/outputs/bundle/release/app-release.aab"
)
rows=()
for item in "${artifacts[@]}"; do
  kind="${item%%|*}"; path="${item#*|}"
  [[ -f "$path" ]] || { echo "Missing release artifact: $path" >&2; exit 31; }
  bytes="$(stat -c %s "$path" 2>/dev/null || stat -f %z "$path")"
  maximum="$(jq -r ".artifacts.$kind.maximumBytes" "$budgets")"
  warning="$(jq -r ".artifacts.$kind.warningBytes" "$budgets")"
  (( bytes <= maximum )) || { echo "$kind exceeds maximum size budget" >&2; exit 32; }
  state="OK"; (( bytes <= warning )) || state="WARNING_REVIEW_REQUIRED"
  sha="$(shasum -a 256 "$path" | awk '{print $1}')"
  rows+=("$(jq -cn --arg kind "$kind" --arg path "${path#$repo_root/}" --arg sha "$sha" --arg state "$state" --argjson bytes "$bytes" '{kind:$kind,path:$path,bytes:$bytes,sha256:$sha,sizeState:$state}')")
done

sbom_sha="$(shasum -a 256 "$sbom" | awk '{print $1}')"
gradle_distribution="$(sed -n 's/^distributionUrl=//p' "$repo_root/android/gradle/wrapper/gradle-wrapper.properties")"
gradle_distribution_sha="$(sed -n 's/^distributionSha256Sum=//p' "$repo_root/android/gradle/wrapper/gradle-wrapper.properties")"
agp_version="$(sed -nE 's/.*com\.android\.application"\) version "([^"]+)".*/\1/p' "$repo_root/android/build.gradle.kts" | head -1)"
java_version="$(java -version 2>&1 | head -1)"
printf '%s\n' "${rows[@]}" | jq -s \
  --arg commit "$(git -C "$repo_root" rev-parse HEAD)" \
  --arg sbomSha "$sbom_sha" \
  --arg gradleDistribution "$gradle_distribution" \
  --arg gradleDistributionSha256 "$gradle_distribution_sha" \
  --arg agpVersion "$agp_version" \
  --arg javaVersion "$java_version" \
  '{schemaVersion:2,commit:$commit,reproducibility:"SOURCE_AND_TOOLCHAIN_PIN_REQUIRED",toolchain:{gradleDistribution:$gradleDistribution,gradleDistributionSha256:$gradleDistributionSha256,androidGradlePlugin:$agpVersion,java:$javaVersion},sbom:{path:"android/app/build/reports/sbom/meet-release.cdx.json",sha256:$sbomSha},artifacts:.}' > "$manifest"

unzip -tq "$repo_root/android/app/build/outputs/bundle/release/app-release.aab" >/dev/null
jarsigner -verify "$repo_root/android/app/build/outputs/bundle/release/app-release.aab" >/dev/null
