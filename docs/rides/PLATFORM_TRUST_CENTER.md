# Centro de Confianza de Plataforma

**Contrato:** cola durable V2, autoridad remota, revisión humana con MFA y
reconciliación Realtime + REST.

## Autoridad

La app nunca concede autoridad comparando correos. Android consulta
`meet_is_platform_owner()` y el servidor resuelve un grant activo de
`PLATFORM_OWNER` asociado al UUID autenticado. Una sesión ausente, un RPC
indisponible o una respuesta negativa cierran el acceso.

Las decisiones sensibles se ejecutan únicamente mediante
`meet_owner_decide_verification_v2`. El servidor exige `aal2`; la pantalla
permite enrolar TOTP, escanear el QR, validar el código vigente y recién entonces
habilita aprobar, rechazar o suspender. La implementación anterior V1 permanece
sin permiso de ejecución para clientes.

## Entrega durable y categorías

`meet_submit_service_verification_v2` hace un upsert idempotente por
`(applicant_user_id, service_type, profile_reference)`, devuelve recibo con
`correlation_id` y crea el evento de auditoría dentro de la misma transacción.
Toda solicitud queda `PENDING`; registrarse nunca concede una capacidad.

La cola admite:

- `PASSENGER` y `RIDE_DRIVER`;
- `TOW_TRUCK`, `MECHANIC`, `PARTS_STORE`, `SERVICE_PROVIDER`;
- `WORKSHOP` y `AUTO_LOCKSMITH`;
- `LAWYER`, `NOTARY`, `PROPERTY_BROKER`, `PROPERTY_SELLER`;
- `FUEL_STATION_STAFF` y `FLEET_OPERATOR`.

Los registros de proveedor y flota se conservan localmente si el transporte
falla, pero se muestran como pendientes y se reintentan al volver a una sesión
autenticada o al abrir el panel correspondiente. Pasajeros y choferes también
reconcilian expedientes pendientes. No se acepta crear una nueva solicitud sin
sesión autenticada, evitando perfiles huérfanos sin identidad remota.

## Realtime y recuperación

`service_verification_applications` forma parte de `supabase_realtime`. El
WebSocket solo despierta una nueva lectura autoritativa; el payload del evento
no decide el estado de UI ni concede permisos.

La pantalla mantiene estas defensas:

1. lectura REST inmediata bajo RLS;
2. suscripción autenticada a Postgres Changes;
3. recarga completa después de cada señal;
4. reconexión exponencial con máximo de 60 segundos;
5. reconciliación REST cada 30 segundos para reparar eventos perdidos;
6. botón manual y conservación de la última cola conocida durante cortes.

Las pestañas `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED` y `ALL` muestran
conteos calculados en la misma instantánea del servidor.

## Auditoría y observabilidad

La presentación, el recibo, cada reenvío y cada decisión comparten un
`correlation_id`. Android emite JSON estructurado sin nombre, correo, teléfono,
licencia ni evidencia con:

- operación, resultado y código de fallo controlado;
- capacidad y estado de cola de baja cardinalidad;
- cantidad, duración, estado WebSocket y ordinal de reintento;
- `traceId` y `correlationId` para unir cliente, RPC y auditoría.

El manifiesto SHA-256 identifica el expediente capturado, pero no demuestra que
el documento sea genuino. La aprobación continúa siendo una decisión humana.

## Verificación y operación

- Android: `PlatformTrustCenterDeliveryContractTest` y política de tipos.
- PostgreSQL: `tests/vanguard-convergence/trust-center-delivery-integration.sql`.
- Gate: `bash tests/vanguard-convergence/verify-vanguard-postgres.sh`.
- Prueba remota: `tests/vanguard-convergence/verify-trust-center-remote.mjs`
  con claves obtenidas en tiempo de ejecución; crea y elimina un usuario temporal.
- Realtime remoto: suscribirse como solicitante, enviar una solicitud de prueba,
  observar el cambio y después confirmar por RPC que el registro existe.
- Incidente: usar el procedimiento de `docs/operations/incident-response.md`.

No se debe declarar producción verificada únicamente por una prueba local. La
entrega exige migración remota, prueba de usuario real, WebSocket real, APK
firmada y comprobación física por ADB.
