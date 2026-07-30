# Plan de pruebas de Elysium Vanguard Viajes

## Nivel 1 — dominio puro

- dinero no negativo y monedas normalizadas;
- suma/resta solo con la misma moneda;
- 500 bps con 0, 1, 9, 10, 99, 100, 2.399, 2.400, 2.450 y `Long.MAX_VALUE`;
- inclusiones y exclusiones de base comisionable;
- devolución parcial y piso en cero;
- splits internos iguales a 500 bps;
- transiciones permitidas, actor, PIN y terminalidad.

## Nivel 2 — PostgreSQL/RPC

- idempotencia por command key y actor;
- RLS negativa entre pasajeros, conductores y tenants;
- dos conductores no pueden obtener el mismo viaje;
- completar dos veces no duplica settlement;
- journal balanceado y postings inmutables;
- cancelar libera la reserva una sola vez;
- un solo PIN activo, expiración y límite de intentos;
- transición con versión obsoleta devuelve conflicto.

## Nivel 3 — Android

- outbox sobrevive reinicio y reintenta sin duplicar;
- realtime perdido se recupera con catch-up;
- Room refleja, pero no inventa, una aceptación;
- proceso muerto durante claim/complete/release se reconcilia;
- geocoding diferencia sin resultados, red, timeout, cuota y respuesta inválida;
- sin proveedor de rutas aparece “ruta no disponible”, no línea recta;
- tracking se detiene al terminar/cancelar o retirar consentimiento.

## Nivel 4 — concurrencia y seguridad

- 100 conductores reclaman simultáneamente: exactamente uno gana;
- 100 reintentos de `complete`: un journal de captura;
- replay de commands de otro actor: todos rechazados;
- cambio de tenant: ningún read model cruza límites;
- revocación de rol durante sesión: se retiran capacidades;
- PIN brute force: bloqueo y auditoría sin revelar el PIN.

## Regresión global

En cada release:

```text
bash tests/ride/verify-ride-migration.sh
bash .codex/skills/meet-rides-improvement-loop/scripts/verify-rides.sh full
bash tests/parity/ci-verify.sh
./gradlew --no-daemon --no-parallel :app:assembleRelease
```

Los tests SQL deben correr contra PostgreSQL real en CI. La máquina local
auditada no dispone de Docker; no se reportará pgTAP local como ejecutado hasta
contar con el runner.

## Evidencia de dispositivo

Una APK no queda validada solo por compilar. La evidencia mínima es:

1. instalación exitosa;
2. lanzamiento con resultado;
3. actividad foreground y PID;
4. ausencia de crash fatal nuevo en logs;
5. recorrido pasajero y conductor con backend de pruebas;
6. captura visual únicamente cuando el dispositivo realmente esté disponible.
