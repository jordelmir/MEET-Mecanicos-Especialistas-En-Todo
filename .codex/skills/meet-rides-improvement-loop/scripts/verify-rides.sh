#!/usr/bin/env bash
set -euo pipefail

mode="${1:-fast}"
repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

if [[ "$mode" != "fast" && "$mode" != "full" ]]; then
  echo "usage: $0 [fast|full]" >&2
  exit 2
fi

echo "== ride domain tests =="
(
  cd android
  ./gradlew :app:testDebugUnitTest --tests 'com.elysium369.meet.ride.*'
)

if [[ "$mode" == "full" ]]; then
  echo "== android full gates =="
  (
    cd android
    ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
  )
  echo "== cross-runtime parity =="
  bash tests/parity/ci-verify.sh
fi

echo "MEET rides verification ($mode): PASS"
