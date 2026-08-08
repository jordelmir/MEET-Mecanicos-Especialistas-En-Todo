# Viajes V5 — Test Plan

## Automatizados

- Fórmula exacta ₡300/km + ₡60/min, parciales, negativos y overflow.
- Snapshot de tasa y rechazo de estimado manipulado.
- Pon tu precio bloquea paradas activas.
- Tarifa medida permite cambio solo por pasajero, estado válido y expected version.
- Replays no duplican eventos, asignación ni ledger.
- RLS entre pasajeros/conductores ajenos.

## Integración crítica

Dos dispositivos y navegador: solicitud, concurrencia de aceptación, llegada <=100 m, PIN, tracking, cambio de parada medido, Guardian, pérdida de red, completion, rating bilateral y expiración de share. Lo que no pueda probarse físicamente se reportará como no verificado.
