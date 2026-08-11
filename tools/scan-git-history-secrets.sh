#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# Scan every blob introduced by the commit range without printing matching
# secret material. The current tree is scanned separately by
# scan-release-secrets.sh. Incremental history scanning keeps the gate fast and
# ensures legacy, reviewed blobs cannot make every future PR permanently red.
# CI passes the PR base or pre-push SHA; local runs default to HEAD^.
SECRET_PATTERN='(sk_live_[A-Za-z0-9]{16,}|AIzaSy[A-Za-z0-9_-]{25,}|sb_secret_[A-Za-z0-9_-]{16,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)'

base_ref="${MEET_SECRET_SCAN_BASE:-}"
if [[ -z "$base_ref" ]] && git rev-parse --verify HEAD^ >/dev/null 2>&1; then
  base_ref="HEAD^"
fi

if [[ -n "$base_ref" && ! "$base_ref" =~ ^0+$ ]]; then
  git rev-parse --verify "$base_ref^{commit}" >/dev/null 2>&1 || {
    echo "git-history-secret-scan: invalid base ref $base_ref" >&2
    exit 2
  }
  scan_revision="$base_ref..HEAD"
else
  scan_revision="HEAD"
fi
# These immutable historical blobs were reviewed manually. One contains a
# synthetic Google-key-shaped value used to test SecretRedactor; the other
# contains only PEM BEGIN/END delimiters in a parser (never key material).
# Pinning object IDs keeps the exception narrow: any content change produces a
# new blob and is scanned normally.
# Feed all reachable, non-reviewed blobs through one batch reader. The original
# implementation spawned git once per blob, turning this guard into a multi-
# minute CI bottleneck. Disable pipefail only around grep because an intentional
# early match closes the upstream batch pipe with SIGPIPE.
set +o pipefail
if git rev-list --objects "$scan_revision" \
  | awk '{print $1}' \
  | git cat-file --batch-check='%(objectname) %(objecttype)' \
  | awk '$2 == "blob" && $1 != "9ce4a0d1cc2b9a03c645f07a14d828cec04d42e3" && $1 != "43b0a9daf8fa57dcc29ff1cb6aa5d8be31c023e8" {print $1}' \
  | git cat-file --batch \
  | LC_ALL=C grep -aEq "$SECRET_PATTERN"; then
  scan_status=0
else
  scan_status=$?
fi
set -o pipefail

if [[ "$scan_status" -eq 0 ]]; then
  echo "git-history-secret-scan: forbidden credential signature found in reachable history" >&2
  exit 1
fi

if [[ "$scan_status" -ne 1 ]]; then
  echo "git-history-secret-scan: scanner pipeline failed with status $scan_status" >&2
  exit 2
fi

echo "git-history-secret-scan: OK"
