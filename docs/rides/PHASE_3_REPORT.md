# Fase 3 — Autoridad transaccional de comandos

## Estado

Implementada y verificada localmente contra PostgreSQL real. La activación en
producción permanece pendiente de CI, Supabase staging, integración Android y
observabilidad operativa.

## Frontera de autoridad

Las mutaciones comerciales autenticadas quedan concentradas en:

- `ride_claim_request_v2`;
- `ride_cancel_trip_v2`;
- `ride_complete_trip_v2`.

Las RPC obtienen el actor de `auth.uid()`, exigen versión esperada y una clave
de idempotencia válida, bloquean la fila del viaje y devuelven errores JSON con
códigos estables. Las RPC comerciales anteriores pierden permiso de ejecución
para `authenticated`.

## Idempotencia y concurrencia

- receipt inmutable por actor + idempotency key;
- SHA-256 del contenido del comando;
- replay byte-equivalente de la respuesta persistida;
- conflicto explícito si la clave llega con otro payload;
- advisory lock transaccional por actor + clave;
- `FOR UPDATE` y CAS por `version`;
- un `correlation_id` UUID estable por comando aceptado;
- errores inesperados no se convierten en falsos éxitos.

El test de concurrencia crea 100 conductores verificados y con saldo, ejecuta
100 reclamos con 12 clientes PostgreSQL en paralelo y exige:

- 1 `CLAIMED`;
- 99 `ALREADY_ASSIGNED`;
- 1 asignación;
- 1 reserva;
- 1 cálculo estimado;
- 1 evento `DRIVER_CLAIMED`.

## Tarifas y liquidación

`ride_fare_quotes` conserva una tarifa aceptada, versionada e inmutable por
componentes. Al completar:

```text
base comisionable =
  transporte + espera + paradas + recargos + cancelación cobrada
  - descuento financiado por conductor - devolución comisionable

total cliente =
  base bruta + propina + peajes + impuestos
  - descuentos/devoluciones - promoción de plataforma
```

La comisión es exactamente 500 bps con half-up. Propinas, peajes, impuestos y
promoción de plataforma quedan registrados como exclusiones. La reserva se
ajusta, captura o libera de forma atómica; la finalización física no se bloquea
por falta posterior de saldo.

## Seguridad y auditoría

- command receipts, fare quotes y holds son inmutables;
- RLS está activa y no hay escritura directa para `anon`/`authenticated`;
- cancelaciones de seguridad crean un hold auditable;
- `SAFETY_HOLD` deja de ser estado de ciclo de vida;
- el estado anterior de una cancelación se preserva en el event log;
- el espejo de wallet propaga versión, bps, base y redondeo al journal;
- el journal conserva igualdad exacta entre débitos y créditos.

## Evidencia ejecutada

```text
bash tests/ride/verify-ride-migration.sh
bash tests/ride/verify-ride-command-authority-postgres.sh
```

Resultados:

- contrato estático: `PASS`;
- cuatro migraciones aplicadas en PostgreSQL 18 efímero: `PASS`;
- fixture ₡4.600 → comisión ₡230: `PASS`;
- fixture final por componentes: base ₡5.300, comisión ₡265,
  total cliente ₡5.800: `PASS`;
- replay, conflicto de clave, versión obsoleta y actor prohibido: `PASS`;
- cancelación, liberación, hold e inmutabilidad: `PASS`;
- journals balanceados: `PASS`;
- concurrencia 100→1: `PASS`.

## Pendiente antes de producción

1. repetir el runner en CI y Supabase staging;
2. integrar Android con estas RPC mediante outbox y read model;
3. probar RLS negativa usando tokens Supabase reales, no superusuario local;
4. añadir carga sostenida, timeouts, métricas y alertas;
5. desplegar detrás de feature flag con rollback documentado.
