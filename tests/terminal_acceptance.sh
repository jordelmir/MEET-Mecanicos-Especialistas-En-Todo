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

apt --version >/dev/null
printf '✓ APT: PASS\n'

dpkg --version >/dev/null
printf '✓ Dpkg: PASS\n'

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
meet status --json | grep -q '"is_connected"'
printf '✓ MEET CLI JSON contract: PASS\n'

printf '\n\033[1;32mELYSIUM TERMINAL ACCEPTANCE: PASS\033[0m\n'
