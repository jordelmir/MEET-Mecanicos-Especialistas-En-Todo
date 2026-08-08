# Viajes V5 — Security Model

- Zero trust para IDs, roles, estado, tarifa, rating y evidencia enviados por APK.
- `auth.uid()`, expected version, advisory lock e idempotency receipt en mutaciones críticas.
- Una sola asignación y una sola liquidación.
- RLS en datos sensibles; service-role nunca en APK.
- PIN temporal, rate limited y no persistido plaintext de forma permanente.
- Safety event inmutable; el caso operativo vive separado.
- Evidencia sensible requiere Keystore, AES-256-GCM, nonce único, hash, TTL y storage restringido antes de habilitarse.

Las heurísticas producen `possible_*` o check-in; nunca una acusación o emergencia automática.
