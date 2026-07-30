# Fase 4 — Gateway Android, read model y outbox

## Estado

Fundación Android implementada y compilada. Los comandos `CLAIM`, `CANCEL` y
`COMPLETE` ya tienen gateway remoto y entrega persistente; la pantalla actual
todavía conserva rutas locales legacy que se sustituirán flujo por flujo en
las fases 5 y 6. No se afirma autoridad remota completa de la UI todavía.

## Cambios

- Room 43→44, sin migración destructiva;
- columnas canónicas `priceOfferMinor`, `finalPriceMinor`, estado/versión de
  servidor, sync state y correlation ID;
- backfill CRC a unidad entera y otras monedas a centésimas;
- `ride_command_outbox` con idempotency key primaria;
- binding al usuario de sesión para impedir replay bajo otra cuenta;
- leases recuperables, estados explícitos y máximo de ocho intentos;
- WorkManager inmediato y periódico, condicionado a red;
- backoff exponencial con jitter estable y límite de 15 minutos;
- gateway Supabase actor-bound sin `actor_id` enviado por Android;
- DTO de error estable y mensajes saneados;
- snapshot remoto bajo RLS para reconciliar Room después de ACK o conflicto;
- conflicto terminal sin reintentar una versión obsoleta;
- dead-letter para payload local inválido o agotamiento;
- DI Hilt para DAO, gateway y worker.

## Invariantes

1. Room es proyección; el ACK de PostgreSQL decide el resultado.
2. Un comando no puede cambiar de contenido conservando la misma clave.
3. Un worker no entrega comandos creados por otra sesión.
4. `IN_FLIGHT` interrumpido vuelve a `RETRYABLE` tras 15 minutos.
5. `VERSION_CONFLICT` actualiza snapshot y requiere un nuevo comando.
6. Ningún importe del boundary nuevo usa `Double` o `Float`.
7. La UI legacy no se elimina hasta contar con la RPC equivalente.

## Evidencia

```text
bash tests/ride/verify-ride-android-authority.sh
./gradlew --no-daemon --no-parallel \
  :app:testDebugUnitTest --tests 'com.elysium369.meet.ride.*'
```

- compilación Kotlin/KAPT/Hilt/Room: `BUILD SUCCESSFUL`;
- suite de Viajes: `BUILD SUCCESSFUL`;
- retry determinista, creciente y acotado: `PASS`;
- contrato estático de autoridad Android: `PASS`.

## Pendiente

- RPC de crear/publicar/ofertar/aceptar/llegar/PIN/iniciar;
- reemplazar cada mutación local de `ObdViewModel`;
- pruebas instrumentadas de migración 43→44 y process death;
- realtime y catch-up por cursor;
- Supabase staging con dos dispositivos y tokens reales.
