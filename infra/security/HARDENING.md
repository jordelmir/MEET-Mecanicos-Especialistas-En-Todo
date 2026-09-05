# MEET Security Hardening (Wave 29)

## Security Principles

1. **No secrets in code** — All keys, tokens, passwords via env/config
2. **No fake data** — Never invent vehicle facts, sensor values, or diagnostic truth
3. **No silent edits** — All report modifications create new versions with chained hashes
4. **No full VIN in QR** — Only 6-field minimal payload
5. **No force-push to main** — Use --no-ff merges
6. **No cross-runtime parity breaks** — TS ≡ Kotlin hash contract

## Security Checklist

### Authentication
- [ ] JWT tokens with short expiry (15 min)
- [ ] Refresh token rotation
- [ ] Biometric re-auth for sensitive ops
- [ ] Device binding for platform owner

### Authorization
- [ ] RLS policies on all Supabase tables
- [ ] Per-domain access controls
- [ ] Circle membership verification
- [ ] Property ownership verification

### Data Protection
- [ ] Encrypt at rest (Supabase default)
- [ ] Encrypt in transit (TLS 1.3)
- [ ] Never log PII (VIN, plate, phone)
- [ ] QR payload: minimal fields only

### Input Validation
- [ ] Validate all API inputs
- [ ] Sanitize user-generated content
- [ ] Rate limiting on all endpoints
- [ ] CORS restrictions

### Code Security
- [ ] No hardcoded secrets
- [ ] No debug logs in production
- [ ] ProGuard/R8 minification
- [ ] Certificate pinning (future)

## Audit

Run security audit before each release:
```bash
# Android
./gradlew lintDebug
./gradlew :app:dependencies

# Server
./gradlew dependencyCheckAnalyze

# Web
npm audit
```
