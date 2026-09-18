#!/usr/bin/env python3
"""
Installer and Synchronizer for meet-apk-operator skill and CLI harness.
Synchronizes SKILL.md and meet_operator.py across all AI agent skill directories.
"""

import os
import shutil
import subprocess

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKILL_SOURCE = os.path.join(PROJECT_ROOT, "docs", "skills", "meet-apk-operator", "SKILL.md")
OPERATOR_SOURCE = os.path.join(PROJECT_ROOT, "scripts", "meet_operator.py")

DEST_DIRS = [
    os.path.expanduser("~/.agents/skills/meet-apk-operator"),
    os.path.expanduser("~/.gemini/config/skills/meet-apk-operator"),
    os.path.expanduser("~/.gemini/skills/meet-apk-operator"),
    os.path.expanduser("~/.config/opencode/skills/meet-apk-operator"),
    os.path.expanduser("~/.claude/skills/meet-apk-operator"),
]

def main():
    if not os.path.exists(SKILL_SOURCE):
        print(f"[-] Skill source not found: {SKILL_SOURCE}")
        return

    print("[*] Synchronizing meet-apk-operator skill...")
    for d in DEST_DIRS:
        try:
            os.makedirs(d, exist_ok=True)
            target = os.path.join(d, "SKILL.md")
            shutil.copy2(SKILL_SOURCE, target)
            print(f"[✓] Synced SKILL.md -> {target}")
        except Exception as e:
            print(f"[-] Could not sync to {d}: {e}")

    # Ensure meet_operator.py is executable
    os.chmod(OPERATOR_SOURCE, 0o755)
    print(f"[✓] Verified executable: {OPERATOR_SOURCE}")

    print("\n[+] Verification of active target devices:")
    subprocess.run(["python3", OPERATOR_SOURCE, "devices"])

if __name__ == "__main__":
    main()
