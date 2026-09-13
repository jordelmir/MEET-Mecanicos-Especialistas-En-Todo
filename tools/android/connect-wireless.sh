#!/usr/bin/env bash
# MEET Wireless ADB Connection Helper
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
/usr/bin/python3 "$DIR/wireless_adb.py" "$@"
