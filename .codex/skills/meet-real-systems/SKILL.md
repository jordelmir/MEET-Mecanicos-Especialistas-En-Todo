# MEET Real Systems

## Purpose

Use this skill when working on MEET's Android app, offline knowledge base, or transport stack.

The goal is simple: no fake diagnostics in production paths, no silent simulation passed off as reality, and no shipping work without local verification.

## Rules

1. Treat OBD, BLE, WiFi, DoIP, DTC, topology, trips, and active tests as truth-sensitive features.
2. If a flow has simulated data, either:
   - remove it from production behavior, or
   - label it explicitly and keep it isolated from real records, reports, and sync.
3. Prefer real transport coverage over decorative UI claims.
4. Do not stage unrelated repository noise. Review diffs file by file.
5. Update `README.md` whenever the real capability surface changes.

## Required verification

Before closing meaningful MEET work:

1. Run Kotlin compilation:
   `cd android && ./gradlew :app:compileDebugKotlin`
2. Build debug APK when Android code changed:
   `cd android && ./gradlew :app:assembleDebug`
3. If `generate_db.py` changed, regenerate:
   `/usr/bin/python3 generate_db.py --include-graph`
4. If adb is available, install and launch:
   - `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`
   - `adb shell am start -n com.elysium369.meet/.MainActivity`
5. For transport claims, verify permissions/radios before writing the summary.

## Database guidance

- Use the hybrid SQLite model already established in MEET:
  indexed relational fields for lookup + JSON payloads for deep knowledge.
- Keep Room and packaged SQLite aligned when adding new seed content.
- Add migrations for schema changes; do not rely on destructive recreation.

## Commit discipline

- Keep commits honest and scoped.
- Do not mix product hardening with unrelated workspace churn.
- Push only after build verification passes.
