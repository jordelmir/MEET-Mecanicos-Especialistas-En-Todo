# Cross-Runtime Parity — TS ↔ Kotlin

**Why this matters:** the per-vehicle integrity chain of the certified-
report pipeline crosses web (TypeScript) and Android (Kotlin). If the
two sides disagree on the SHA-256 of the same snapshot, the chain
breaks and the verifier can't authenticate reports. We pin the
contract with a small but rigorous cross-runtime test.

---

## Strategy

1. A JSON fixture lives in `tests/parity/fixtures/snapshot-p0230.json`.
   It contains the exact input fields and a hard-coded `expectedHash`.
2. A Node script (`tests/parity/hash-parity.ts`) reads the fixture and
   runs the TypeScript `canonicalSnapshotString` + SHA-256 on it. It
   asserts the output matches `expectedHash`.
3. A Kotlin test (`HashEngineParityTest`) does the same in the APK
   build, and writes the canonical string + hash to
   `app/build/reports/parity/snapshot-p0230.txt`.
4. A bash wrapper (`tests/parity/ci-verify.sh`) runs both, captures
   output, and asserts they match byte-for-byte.
5. A GitHub Actions workflow
   (`.github/workflows/cross-runtime-parity.yml`) runs the TS step on
   every push / PR. The Kotlin step is wired up but tolerates a missing
   gradle environment so dev containers without the Android SDK still
   pass.

---

## How to verify locally

```bash
# 1. Install TS deps
npm install

# 2. Run the TS verifier
npx tsx tests/parity/hash-parity.ts tests/parity/fixtures/snapshot-p0230.json

# Expected output:
# [OK] P0230 fuel-pump request (Hyundai Accent Verna 2005)
#   expected: 9548d33d0b7a38561b5b66dc1ee17c66280621a90cd539ed14f5ec4085c25089
#   actual:   9548d33d0b7a38561b5b66dc1ee17c66280621a90cd539ed14f5ec4085c25089
```

```bash
# 3. Run the Kotlin verifier
cd android
./gradlew :app:testDebugUnitTest --tests 'com.elysium369.meet.core.reports.HashEngineParityTest'
cat app/build/reports/parity/snapshot-p0230.txt
```

```bash
# 4. Run the full cross-runtime check
./tests/parity/ci-verify.sh
```

If the TS and Kotlin outputs differ, **the chain is broken**. The fix
is in one of the canonicalization layers — not in the test. The
fixtures and the parity script are the source of truth.

---

## Adding a new fixture

1. Drop a JSON file in `tests/parity/fixtures/`.
2. Compute the expected hash with a small TS one-liner:
   ```bash
   npx tsx -e 'import("ts-node/register"); const f=require("./tests/parity/fixtures/YOUR.json"); /* ... */'
   ```
3. Or, easier: temporarily set `expectedHash` to `"PLACEHOLDER"`, run
   the verifier, and copy the printed `actual:` value back into the
   JSON.
4. Add a Kotlin test in
   `android/app/src/test/kotlin/com/elysium369/meet/core/reports/HashEngineParityTest.kt`
   that writes the same input's canonical string + hash to
   `app/build/reports/parity/YOUR.txt`.

---

## What this catches

* Any change to `canonicalSnapshotString` in TS that does not match
  the Kotlin `computeHash`.
* Any change to `kotlinDoubleToString` that drifts from the Kotlin
  `Double.toString()`.
* Any change to the `,`/`;` separator inside `freezeFramePidValues`
  or `readiness` blocks.
* Any change to the order of fields in the canonical string.

If the verifier ever fails, **fix the canonicalization, not the test**.
The fixture is the contract.
