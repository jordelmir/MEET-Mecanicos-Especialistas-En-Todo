# GITHUB-BRANCH-PROTECTION-REQUIRED.md

**Platform**: MEET / Elysium Vanguard  
**Target Branch**: `main`  
**Compliance Mandate**: Master Implementation Order V4 / AGENTS.md Safety Rule 5  
**Status**: PENDING REPO ADMINISTRATOR CONFIGURATION IN GITHUB SETTINGS  

---

## 1. Objective

To permanently prevent accidental force-pushes, branch deletions, and unverified merges on `main`. 
All agents (Codex, Mavis, Google Antigravity) and human contributors must merge via standard pull requests or fast-forward/non-fast-forward verified commits with green CI status.

---

## 2. Required GitHub Settings

Navigate to **Repository Settings → Branches → Branch protection rules → Add classic rule** for branch pattern `main`:

### A. Protect matching branches
- [x] **Require a pull request before merging**
  - Require approvals: `1`
  - Dismiss stale pull request approvals when new commits are pushed: `Checked`
  - Require review from Code Owners: `Checked`
- [x] **Require status checks to pass before merging**
  - [x] Require branches to be up to date before merging
  - Required checks:
    - `tests/parity/ci-verify.sh` (TS ≡ Kotlin Cross-Runtime Parity)
    - `compileDebugSources` (Android Build)
    - `testDebugUnitTest` (Fulfillment OS + Truth Guard Suites)
    - `verifyNoSecretsInSource` (Zero-secret leak check)
- [x] **Do not allow bypassing the above settings** (Enforce for administrators)
- [x] **Block force pushes** (Prevent `git push --force` and `git push --force-with-lease`)
- [x] **Block deletions** (Prevent branch deletion)

---

## 3. Local Git Safety Hook

To prevent accidental local force pushes from any workstation before GitHub branch protection is enforced by admin:

```bash
# .git/hooks/pre-push
#!/bin/bash
while read local_ref local_sha remote_ref remote_sha
do
    if [ "$remote_ref" = "refs/heads/main" ]; then
        if [ "$remote_sha" != "0000000000000000000000000000000000000000" ]; then
            # Check if this push is a non-fast-forward force push
            git merge-base --is-ancestor "$remote_sha" "$local_sha"
            if [ $? -ne 0 ]; then
                echo "[ERROR] Force-pushing to main is strictly prohibited by AGENTS.md."
                exit 1
            fi
        fi
    fi
done
exit 0
```
