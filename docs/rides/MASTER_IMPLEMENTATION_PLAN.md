# Elysium Vanguard Viajes — Plan maestro de implementación

## Identidad de la entrega

- Orden fuente: `pasted-text.txt`, SHA-256
  `ff4b0b17de360265d53607682936ad743815917cbef2014fbecdae22fecef015`.
- Base auditada: `1c80d4a53abb30385cd1aefa1bc32ac6fdf64b6c`.
- Fecha de la base: 2026-07-29T18:07:48-06:00.
- Versión Android encontrada: `4.6.6` (`versionCode 31`).
- Rama de trabajo: `codex/rides-5-percent-mobility-os`.
- Modalidad: misión finita por fases; no existe un proceso recurrente ni autónomo.

## Resultado objetivo

Una sola APK y un solo `applicationId`, con shells internos por rol, que usa
PostgreSQL/Supabase como autoridad comercial y Room como caché, read model y
outbox. La plataforma cobra exactamente 500 basis points sobre una base
comisionable explícita, con operaciones financieras atómicas, idempotentes,
auditables y compensables.

El trabajo es aditivo: Scanner, OBD, DTC, Garage, IA, Motor 3D, taller,
repuestos, reportes y contratos de paridad permanecen intactos.

## Auditoría inicial

| Capacidad | Implementación actual | Fuente de verdad efectiva | Frontera de seguridad | Fallo principal | Estado | Acción |
|---|---|---|---|---|---|---|
| Viajes | Room + `ObdViewModel` + pantalla monolítica | Android local | Proceso del dispositivo | Dos dispositivos pueden divergir | No producción | Commands remotos y read model local |
| Solicitudes/ofertas | Entidades Room y tablas Supabase | Room en la APK | Parámetros de UI | Autoridad duplicada | Parcial | RPC idempotentes + repositorio remoto |
| Aceptación | `ride_accept_offer`/`ride_claim_request` usan bloqueo | Supabase en SQL; no consumido por APK | `auth.uid()` + `FOR UPDATE` | Android evita el límite remoto | Backend sólido, integración ausente | Conectar casos de uso al RPC |
| Ciclo de vida | Política Kotlin y eventos SQL | Mixta | Actor local y `auth.uid()` remoto | Estados y nombres no son completamente iguales | Parcial | Estado canónico y CAS remoto |
| PIN | Challenge bcrypt y límite de intentos en SQL; PIN local en UI | Local en flujo actual | Memoria del dispositivo | PIN no autoritativo en APK | Parcial | Consumir RPC y eliminar PIN local como autoridad |
| Dinero | `RideMoney(Long)` en dominio; `Double` en Room/UI | Mixta | Conversión local | Redondeo y serialización ambiguos | No conforme | `AmountMinor`, migración INTEGER y DTOs |
| Comisión | 500 bps con reserva/captura SQL | Supabase | RPC | Base = tarifa ofrecida; no hay componentes | Parcial | Política canónica versionada |
| Wallet | Saldo y ledger simple | Supabase | RPC | No es doble entrada | Parcial | Journal + postings balanceados |
| Cancelación | RPC libera reserva | Supabase si se usa | RPC | Flujo local puede omitir liberación | Parcial | Command remoto y reconciliación |
| Realtime | No hay integración Android de Viajes | Ninguna | N/A | UI no recibe cambios distribuidos | Ausente | Canal autenticado + catch-up |
| Offline | Room existe; worker de outbox es no-op | Android | Dispositivo | Mutaciones críticas sin entrega garantizada | Ausente | Outbox explícito e idempotency key |
| Geocoding | Photon llamado desde Compose | Proveedor externo | Red pública | Errores se convierten en lista vacía | Prototipo | Gateway tipado, debounce, caché y fallback |
| Routing | MapLibre y línea directa de fallback | Cliente | Dispositivo | Una línea recta aparenta ruta real | No conforme | Proveedor vial real o estado no disponible |
| Tarifación | Propuesta local y contraoferta | Android | UI | No auditable ni regulable | Prototipo | Motor server-side versionado por modalidad |
| Multitenancy | No es frontera completa | Parcial | RLS incompleta para tenant | Fuga o mezcla entre centrales | Ausente | `tenant_id`, memberships y RLS probada |
| Notificaciones | Infra automotriz existente | Android | Sistema operativo | Sin pipeline de eventos de viaje | Ausente | Push por evento y deduplicación |
| Observabilidad | Logs generales | Mixta | Aplicación | Sin correlación viaje-command-ledger | Parcial | correlation IDs, métricas y auditoría |
| UI pasajero/conductor | 3.961 LOC en una pantalla | Compose local | N/A | God screen, difícil de probar | Prototipo avanzado | Flujos y componentes por rol |
| Shell contextual | Inicio automotriz global | Aplicación | Rol visual | Viajes carga recursos irrelevantes | Ausente | Resolver backend de rol/capacidad |
| SQL tests | Migraciones presentes | N/A | N/A | Docker no disponible localmente | Bloqueado local | CI PostgreSQL/pgTAP obligatorio |

## Evidencia del baseline

- Migración de Viajes: `bash tests/ride/verify-ride-migration.sh` — pasó.
- Suite completa de Viajes, Android unit tests, lint, debug y paridad — pasó.
- Release Android — pasó.
- APK release base: 248.961.592 bytes.
- SHA-256 APK base:
  `aff8ef9e2cbc8c7b7a1296885f4c52b805300ebb247e9f20fcf766f8e85a36c8`.
- CI de la base: paridad cross-runtime verde.
- Limitación reproducible: no hay Docker local; los tests SQL ejecutables se
  trasladan a CI PostgreSQL y a un proyecto Supabase de pruebas.

## Riesgos y mitigaciones

| Riesgo | Severidad | Mitigación verificable |
|---|---:|---|
| Dinero histórico almacenado como `REAL` | Crítica | Columna paralela INTEGER, backfill, reconciliación, lectura dual y corte con feature flag |
| Autoridad comercial local | Crítica | Bloquear mutaciones directas; command API actor-bound |
| Doble asignación o liquidación | Crítica | Unicidad, locks, versión, idempotency key y prueba concurrente |
| Ledger no balanceado | Crítica | Journal inmutable, postings cuya suma sea cero y constraint diferido |
| Falta de cumplimiento regulatorio comprobado | Alta | Modalidad por jurisdicción y texto “pendiente de validación” |
| Proveedor de mapas/geocoding no disponible | Alta | Errores tipados; nunca mostrar línea recta como ruta |
| Migración grande de pantalla | Alta | Extraer por flujo detrás de flags, sin reescritura total |
| RLS incompleta | Crítica | Matriz actor/recurso y tests negativos multiusuario |
| Pérdida de comandos offline | Alta | Outbox persistente, reintentos acotados y reconciliación |
| Regresión automotriz | Alta | Suite Android, paridad, navegación y smoke automotriz en cada release |

## Migraciones sin destrucción

1. Añadir columnas `*_minor` INTEGER y `currency_code`; no eliminar `REAL`.
2. Backfill determinista con registro de filas ambiguas.
3. Reconciliar conteos, totales y monedas antes de activar lectura nueva.
4. Dual-read temporal; cualquier discrepancia genera telemetría y bloquea el
   corte financiero, no la seguridad ni la finalización física del viaje.
5. Activar escritura canónica mediante feature flag.
6. Dejar columnas antiguas en solo lectura durante una versión completa.
7. Retirarlas únicamente en una migración posterior con evidencia.

## Fases finitas, dependencias y salidas

| Fase | Entrega medible | Dependencia | Gate |
|---:|---|---|---|
| 0 | Auditoría, trazabilidad, plan, baseline y flags | Ninguna | Evidencia registrada |
| 1 | Dinero entero, base comisionable, 500 bps, errores y estado canónico | F0 | Tests de borde y overflow |
| 2 | Ledger doble entrada, reservas, captura, reversos y split ≤500 bps | F1 | Balance cero y fixtures |
| 3 | Command API actor-bound, idempotencia, CAS, RLS y SQL tests | F2 | Concurrencia y autorización |
| 4 | Repositorio Android remoto, Room read model y outbox | F3 | Offline/replay/reconcile |
| 5 | Pasajero: búsqueda, señas, paradas, tarifa, PIN y recibo | F4 | Flujo E2E pasajero |
| 6 | Conductor: disponibilidad, oferta, llegada, PIN, viaje e ingresos | F4 | Flujo E2E conductor |
| 7 | Realtime, tracking consentido, notificaciones y lifecycle | F5/F6 | Reconexión y battery checks |
| 8 | Routing vial, ETA, geocoding tipado, pricing regulable y dispatch | F7 | Sin rutas falsas |
| 9 | Tenants, centrales, federación, dispatcher y corporativo | F3/F8 | Aislamiento entre tenants |
| 10 | Safety, soporte, disputas, privacidad, accesibilidad y fraude | F7/F9 | Pruebas negativas y UX |
| 11 | Observabilidad, SLO, operación, rollout CR, APK y evidencia | Todas | Release checklist completo |

## Trazabilidad de las 51 secciones

| Orden | Capacidad | Fase | Identificador |
|---:|---|---:|---|
| 0 | Misión y una sola APK | 0 | RID-000 |
| 1 | Principios inmutables | Todas | RID-001 |
| 2 | Auditoría inicial | 0 | RID-002 |
| 3 | Arquitectura interna | 0–4 | RID-003 |
| 4 | Shells contextuales | 4 | RID-004 |
| 5 | Dominio canónico | 1 | RID-005 |
| 6 | Command API | 3 | RID-006 |
| 7 | Dinero | 1 | RID-007 |
| 8 | Comisión 5% | 1–2 | RID-008 |
| 9 | Ciclo financiero | 2–3 | RID-009 |
| 10 | Ledger doble entrada | 2 | RID-010 |
| 11 | Reparto del 5% | 2 | RID-011 |
| 12 | Métodos de cobro | 2–3 | RID-012 |
| 13 | Multitenancy | 3/9 | RID-013 |
| 14 | Federación | 9 | RID-014 |
| 15 | Dispatch | 8–9 | RID-015 |
| 16 | Pasajero | 5 | RID-016 |
| 17 | Señas CR | 5 | RID-017 |
| 18 | Geocoding | 8 | RID-018 |
| 19 | Routing | 8 | RID-019 |
| 20 | Motor tarifario | 8 | RID-020 |
| 21 | Conductor | 6 | RID-021 |
| 22 | Verificación | 6/10 | RID-022 |
| 23 | Vehicle Trust | 6/10 | RID-023 |
| 24 | Seguridad | 10 | RID-024 |
| 25 | Comunicación | 7/10 | RID-025 |
| 26 | Programados | 9 | RID-026 |
| 27 | Familia/invitados/grupos | 9 | RID-027 |
| 28 | Accesibilidad | 10 | RID-028 |
| 29 | Aeropuerto/turismo | 9 | RID-029 |
| 30 | Corporativo | 9 | RID-030 |
| 31 | Despachador | 9 | RID-031 |
| 32 | Offline/outbox | 4 | RID-032 |
| 33 | Realtime | 7 | RID-033 |
| 34 | Notificaciones | 7 | RID-034 |
| 35 | Privacidad | 10 | RID-035 |
| 36 | RLS | 3 | RID-036 |
| 37 | Antifraude | 10 | RID-037 |
| 38 | Soporte/disputas | 10 | RID-038 |
| 39 | UX | 5–10 | RID-039 |
| 40 | Observabilidad | 11 | RID-040 |
| 41 | SLO/rendimiento | 11 | RID-041 |
| 42 | Tests financieros | 1–3 | RID-042 |
| 43 | 100 conductores | 3 | RID-043 |
| 44 | Fixture de comisión | 1–2 | RID-044 |
| 45 | Cancelación | 2–3 | RID-045 |
| 46 | Migraciones | 1–11 | RID-046 |
| 47 | Fases | 0 | RID-047 |
| 48 | Quality gates | Todas | RID-048 |
| 49 | Documentación | Todas | RID-049 |
| 50 | Definición de terminado | 11 | RID-050 |
| 51 | Evidencia de respuesta | Todas | RID-051 |

## Definition of Done global

Ninguna fase se considera terminada por tener UI o compilar. Debe incluir:

1. invariantes codificadas;
2. pruebas en el nivel de riesgo correspondiente;
3. migración y rollback documentados cuando aplique;
4. estados de error honestos;
5. observabilidad suficiente para reconstruir la operación;
6. ausencia de regresiones automotrices;
7. documentación actualizada;
8. commit auditable con evidencia reproducible.
