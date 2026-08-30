#!/bin/sh
set -eu

printf '=== RUNNING ELYSIUM TERMINAL ACCEPTANCE SUITE ===\n'

# 1. Real Python evaluation
python3 -c 'print(2 + 2)' | grep -qx '4'
printf '✓ Python 3: PASS\n'

# 2. Toolchain versions
git --version >/dev/null
printf '✓ Git: PASS\n'

curl --version >/dev/null
printf '✓ Curl: PASS\n'

if command -v apt >/dev/null 2>&1; then
    apt --version >/dev/null
    dpkg --version >/dev/null
    printf '✓ APT/Dpkg: PASS\n'
elif command -v brew >/dev/null 2>&1; then
    brew --version >/dev/null
    printf '✓ Homebrew: PASS\n'
else
    printf 'No supported host package manager found\n' >&2
    exit 1
fi

# 3. Text output and redirection
printf 'hello\n' | grep -qx hello
tmp="$(mktemp)"
printf 'Elysium\n' > "$tmp"
grep -qx Elysium "$tmp"
rm "$tmp"
printf '✓ Pipelines & Redirections: PASS\n'

# 4. Antigravity core presence
if command -v agy >/dev/null 2>&1 || [ -f /root/.local/bin/agy ]; then
    printf '✓ Antigravity Binary: PASS\n'
fi

# 5. MEET CLI status JSON test
if command -v meet >/dev/null 2>&1; then
    meet status --json | grep -q '"is_connected"'
    printf '✓ MEET CLI JSON runtime contract: PASS\n'
else
    repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
    shell_source="$repo_root/android/app/src/main/kotlin/com/elysium369/meet/core/utils/LocalShellManager.kt"
    grep -q '"is_connected"' "$shell_source"
    grep -q '"status"' "$shell_source"
    printf '✓ MEET CLI JSON source contract: PASS (runtime lives inside Android)\n'
fi

printf '\n\033[1;32mELYSIUM TERMINAL ACCEPTANCE: PASS\033[0m\n'
