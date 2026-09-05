# ELYSIUM MOBILITY OS — OPEN SOURCE DUE DILIGENCE & SUPPLY CHAIN
**Status**: AUTHORITATIVE AUDIT V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *Open-source is acceleration, not architecture authority. No viral licenses (GPL/AGPL) inside the mobile client binary.*

---

## 1. Third-Party Dependency Inventory

| Component / Library | Version | License | Provenance | Integration Role |
|---|---|---|---|---|
| **MapLibre Native Android** | `11.5.1` | BSD-3-Clause | `org.maplibre.gl:android-sdk` | Vector map rendering & tile display |
| **Supabase Kotlin SDK** | `2.5.4` | Apache-2.0 | `io.github.jan-tennert.supabase:*` | Auth, PostgREST RPCs, Realtime channels |
| **Android Jetpack Room** | `2.6.1` | Apache-2.0 | `androidx.room:*` | Local SQLite ORM and reactive flows |
| **Android WorkManager** | `2.9.0` | Apache-2.0 | `androidx.work:*` | Guaranteed background outbox synchronization |
| **Google Dagger / Hilt** | `2.51.1` | Apache-2.0 | `com.google.dagger:hilt-android` | Dependency injection framework |
| **Google Play Services Location** | `21.3.0` | Android Software SDK | `com.google.android.gms:play-services-location` | Fused GPS provider for background tracking |
| **Kotlinx Coroutines / Serialization**| `1.8.1` / `1.6.3` | Apache-2.0 | `org.jetbrains.kotlinx:*` | Concurrency and JSON serialization |

---

## 2. License Compliance & Viral Guardrails

1. **Permissive Only**: All Android client dependencies are licensed under Apache 2.0, MIT, or BSD-3-Clause.
2. **GPL / AGPL Strict Exclusion**: No code or library licensed under GPL, LGPL, or AGPL is included in the Android application binary.
3. **Backend Services Isolation**: Any AGPL software (e.g. self-hosted routing engines or databases) runs strictly over network boundaries (HTTP/JSON RPC), completely isolated from proprietary client source code.

---

## 3. Supply Chain Hardening

- **Version Pinning**: All Gradle dependencies use exact semantic versions in `build.gradle.kts` and `libs.versions.toml`. No floating `+` or dynamic version declarations.
- **Repository Integrity**: Dependencies are resolved exclusively from Google Maven (`maven.google.com`) and Maven Central (`repo.maven.apache.org`).
- **Secret Scanning**: Pre-build gate `:app:verifyNoSecretsInSource` executes on every build to block accidental commits of API keys or credentials.
