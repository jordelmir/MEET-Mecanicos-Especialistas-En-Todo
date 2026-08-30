# Incident Response & On-Call Playbook

## 1. Severity Levels
- **SEV-1 (Critical):** Outage of physical diagnostic acquisition or complete cloud authentication failure.
- **SEV-2 (High):** Realtime WebSocket degraded; falling back to REST polling.
- **SEV-3 (Medium):** Minor background sync latency or non-blocking UI discrepancy.

## 2. Rollback Policy
If an API or Realtime regression occurs, feature flags toggle transport back to `LEGACY` mode instantly without requiring an APK release.

## 3. Trust Center: cola vacía o WebSocket degradado

1. Confirmar que `meet_submit_service_verification_v2` devuelve un recibo y
   conservar únicamente su `correlation_id`; no copiar datos personales a logs.
2. Consultar `meet_own_verification_applications_v1` con el solicitante. Si el
   recibo existe, la entrega está confirmada aunque el socket no haya avisado.
3. Consultar `meet_owner_verification_queue_v2('ALL', 100)` con una sesión de
   autoridad. Una cola diferente indica problema de RLS/autoridad, no de UI.
4. Confirmar que `service_verification_applications` pertenece a la publicación
   `supabase_realtime` y revisar los eventos `trust.realtime.state`.
5. La app reintenta el socket con backoff y hace catch-up REST cada 30 segundos.
   No aprobar basándose en un payload WebSocket ni desactivar MFA para recuperar
   operación.
6. Si una decisión falla con `AAL2_REQUIRED`, validar el TOTP desde la pantalla;
   jamás conceder EXECUTE sobre la RPC V1 como atajo.
