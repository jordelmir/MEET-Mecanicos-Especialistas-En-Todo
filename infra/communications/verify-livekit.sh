#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 https://livekit.example.com turn.example.com" >&2
  exit 64
fi

signal_url="$1"
turn_host="$2"

case "$signal_url" in
  https://*) ;;
  *) echo "signal endpoint must use HTTPS" >&2; exit 65 ;;
esac

if [[ -z "$turn_host" || "$turn_host" == *://* || "$turn_host" == */* ]]; then
  echo "TURN host must be a bare DNS hostname" >&2
  exit 65
fi

command -v curl >/dev/null || { echo "curl is required" >&2; exit 69; }
command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 69; }

curl --fail --silent --show-error --proto '=https' --tlsv1.2 \
  --connect-timeout 10 --max-time 20 "$signal_url" >/dev/null

certificate_output="$(openssl s_client -connect "${turn_host}:443" \
  -servername "$turn_host" -verify_return_error </dev/null 2>&1)"
if ! grep -q "Verify return code: 0 (ok)" <<<"$certificate_output"; then
  echo "TURN/TLS certificate verification failed" >&2
  exit 1
fi

echo "[communications-livekit] HTTPS and TURN/TLS certificate checks passed"
echo "[communications-livekit] media relay and two-device authorization still require physical tests"
