#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <first-release-artifact> <second-release-artifact>" >&2
  exit 64
fi
first="$1"; second="$2"
[[ -f "$first" && -f "$second" ]] || { echo "Both artifacts are required" >&2; exit 65; }
first_sha="$(shasum -a 256 "$first" | awk '{print $1}')"
second_sha="$(shasum -a 256 "$second" | awk '{print $1}')"
jq -cn --arg first "$first_sha" --arg second "$second_sha" \
  '{schemaVersion:1,firstSha256:$first,secondSha256:$second,byteIdentical:($first == $second)}'
[[ "$first_sha" == "$second_sha" ]]
