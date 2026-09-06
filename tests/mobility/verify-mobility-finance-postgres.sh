#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# ELYSIUM GLOBAL MOBILITY OS — FINANCIAL AUTHORITY V8 TEST SUITE ENTRYPOINT
# Mandate: ORDEN MAESTRA V8 (Canonical Financial Verification)
# ─────────────────────────────────────────────────────────────────────────────

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec bash "$script_dir/verify-mobility-financial-authority-v8.sh" "$@"
