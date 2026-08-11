#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# Scan every reachable blob without printing matching secret material. Generic
# words such as "service_role" are intentionally excluded because guard code
# must be able to name forbidden credential classes.
SECRET_PATTERN='(sk_live_[A-Za-z0-9]{16,}|AIzaSy[A-Za-z0-9_-]{25,}|sb_secret_[A-Za-z0-9_-]{16,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)'
found=0

while IFS= read -r object_id; do
  if git cat-file blob "$object_id" 2>/dev/null | LC_ALL=C grep -aEq "$SECRET_PATTERN"; then
    found=1
    break
  fi
done < <(git rev-list --objects --all | awk '{print $1}' | git cat-file --batch-check='%(objectname) %(objecttype)' | awk '$2 == "blob" {print $1}')

if [[ "$found" -ne 0 ]]; then
  echo "git-history-secret-scan: forbidden credential signature found in reachable history" >&2
  exit 1
fi

echo "git-history-secret-scan: OK"
