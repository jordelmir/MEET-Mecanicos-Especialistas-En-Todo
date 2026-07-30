# Fase 5 — Flujo vertical pasajero–conductor autoritativo

## Estado

Implementado y validado el primer flujo vertical completo sobre PostgreSQL:

`solicitud → paradas → oferta/claim → asignación → en ruta → llegada → PIN →
pasajero a bordo → inicio → finalización/cancelación`.

La UI Android dejó de adjudicar viajes, validar PIN o cambiar estados mediante
mutaciones locales. Room conserva únicamente la proyección y el outbox.

## Autoridad backend

- `ride_create_request_v2`: crea solicitud y paradas en una transacción;
- `ride_submit_offer_v2`: exige vehículo activo/verificado y versión vigente;
- `ride_accept_offer_v2`: asigna exactamente un conductor, congela tarifa y
  reserva la comisión;
- `ride_driver_transition_v2`: controla `DRIVER_EN_ROUTE`,
  `DRIVER_ARRIVED` y `START`;
- `ride_issue_boarding_pin_v2`: genera un PIN de cuatro dígitos en servidor,
  almacena sólo hash bcrypt y vence en 30 minutos;
- `ride_verify_boarding_pin_v2`: bloqueo después de cinco intentos y
  transición atómica a `PASSENGER_ONBOARD`;
- `ride_complete_trip_v2` y `ride_cancel_trip_v2`: reutilizados como cierre
  financiero y cancelación tipada;
- direct insert de solicitudes/ofertas revocado a `authenticated`;
- toda operación usa actor de `auth.uid()`, idempotency key, request hash,
  bloqueo advisory, `FOR UPDATE` y expected version.

## Android

- Room 44→45, conservando datos existentes;
- PIN autoritativo cifrado en tránsito y persistido sólo en la proyección del
  pasajero mientras está vigente;
- botones bloqueados durante `syncState=PENDING`;
- separación visual entre “comando enviado” y “resultado confirmado”;
- `CLAIM` ya no reproduce sonido de victoria antes del ACK;
- tarifa convertida a minor units antes de cruzar el boundary;
- Realtime actúa únicamente como señal de actualización;
- cada señal ejecuta catch-up PostgREST bajo RLS, por lo que un evento WebSocket
  perdido no se convierte en fuente de verdad;
- solicitudes, paradas, ofertas y vehículo agregado de la oferta se
  reconcilian entre dispositivos sin ampliar el acceso RLS al perfil;
- hasta 100 viajes, 512 paradas, 300 ofertas y 100 vehículos visibles se
  reconcilian de forma acotada.

## Evidencia

```text
bash tests/ride/verify-ride-migration.sh
bash tests/ride/verify-ride-android-authority.sh
bash tests/ride/verify-ride-command-authority-postgres.sh
./gradlew --no-daemon --no-parallel \
  :app:testDebugUnitTest --tests 'com.elysium369.meet.ride.*'
./gradlew --no-daemon --no-parallel :app:compileDebugKotlin
```

Resultados:

- flujo vertical PostgreSQL: `PASS`;
- carrera de 100 claims: `1` ganador, `99` no ganadores;
- cierre: CRC 5.100 de base, CRC 255 de comisión (500 bps);
- journals desbalanceados: `0`;
- suite Viajes Android: `76/76 PASS`;
- Kotlin/KAPT/Hilt/Room/Realtime: `BUILD SUCCESSFUL`;
- memoria libre observada después del gate: `59%`.

## Límites honestos

- la verificación remota del vehículo todavía requiere que exista un registro
  `VERIFIED`; la aprobación local de documentos no se presenta como aprobación
  remota;
- nombres/teléfonos no se exponen antes de la asignación; las tarjetas remotas
  usan etiquetas neutrales y el vehículo permitido por RLS;
- falta cerrar onboarding remoto de conductor, feedback audiovisual del
  resultado del claim, recuperación exacta del PIN tras reinstalación,
  despacho geográfico y pruebas con dos dispositivos contra staging.
