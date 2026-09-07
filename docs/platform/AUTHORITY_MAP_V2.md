# MEET / ELYSIUM — CANONICAL AUTHORITY MAP V2
## GATE 0: Unified Domain Authority Matrix

> **Hostile-Client Invariant:**
> El cliente móvil (Android) y web son tratados como potencialmente hostiles y falibles. El cliente jamás decide autoritativamente: precios, tarifas, descuentos, estados de captura financiera, balances de monedero, entradas contables del ledger, derechos de suscripción (entitlements), activación de mercados o aprobación de capacidades de proveedores.

---

### 1. Matriz de Autoridad de Dominio

| Dominio | Fuente de Verdad (Source of Truth) | Escritores Autorizados (Writers) | Lectores Autorizados (Readers) | Autoridad del Cliente (Client Authority) | Autoridad del Servidor (Server Authority) | Pruebas de Verificación (Tests) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Identidad & Cuentas** | `auth.users`, `public.principals` | `auth.users`, `service_role` | Propietario (`auth.uid()`), `service_role` | Solicitud de registro, login, solicitud de eliminación | Gestión de tokens JWT, RBAC, ejecución atómica de borrado | `verify-mobility-v11-public-launch.sh` (Test 11) |
| **Catálogo de Productos** | `public.catalog_products` | `service_role` (Backoffice/DBA) | `anon`, `authenticated` | Selección de producto de interés | Definición de productos, tipos, estados (DRAFT, ACTIVE, RETIRED) | `verify-revenue-to-platform-e2e.sh` |
| **Precios por Mercado** | `public.market_product_prices` | `service_role` | `authenticated`, `anon` | Ninguna (cero inyección de precio) | Resolución autoritativa de moneda y monto menor por mercado | `verify-revenue-to-platform-e2e.sh` |
| **Entitlements & Suscripciones** | `billing_private.provider_events`, `public.user_entitlements` | `service_role` (Edge Function de verificación) | Propietario (`auth.uid()`) | Envío de purchase token para verificación | Validación contra Google Play Developer API, activación y expiración | `verify-revenue-to-platform-e2e.sh` |
| **Diagnóstico & DTCs** | `public.diagnostic_snapshots`, Room DB | Propietario del vehículo, `service_role` | Propietario, Mecánico con permiso | Lectura de dongle OBD física local | Persistencia inmutable, correlación causal con reparaciones | `ObdProtocolDecoderTest.kt`, `DtcDecoderTest.kt` |
| **Intención de Reparación (`RepairIntent`)** | `public.repair_intents` | Propietario (`auth.uid()`), `service_role` | Propietario, Proveedores en zona | Declaración de síntomas, confirmación de falla | Clasificación de verdad (`OBSERVED` vs `DERIVED`), puente a despacho | `verify-revenue-to-platform-e2e.sh` |
| **Solicitudes de Servicio / Repuestos** | `public.universal_service_requests`, `public.part_requests` | Cliente propietario (`client_id = auth.uid()`) | Proveedores calificados en mercado activo | Publicación de necesidad con intake técnico | Transición de ciclo de vida, asignación atómica de oferta | `verify-tow-fulfillment.sh`, `verify-mobility-v11-public-launch.sh` |
| **Cotización & Pricing de Movilidad** | `public.ride_quotes`, `public.ride_route_evidence` | RPC `mobility_generate_quote` (`SECURITY DEFINER`) | Pasajero solicitante | Selección de origen y destino | Cálculo de ruta de red vial, polilínea, distancia y tarifa | `verify-mobility-financial-authority-v8.sh` (Test A) |
| **Viajes de Movilidad (Trips)** | `public.trips`, `public.mobility_trip_state_transitions` | RPCs autoritativas (`service_role` context) | Pasajero y Conductor asignados | Comandos de ciclo de vida (vía RPCs con verificación de PIN) | Máquina de estados finita monótona, exclusión de DISPUTED | `verify-mobility-v12-provider-operations.sh` (Test 4) |
| **Ubicación en Vivo de Conductor** | `public.driver_presence_snapshot` | Conductor autenticado (solo su fila) | Pasajero de viaje activo vía `mobility_get_active_trip_location_v1` | Emisión periódica de coordenadas y heading | RLS denegado directo; RPC contextual restringe a viajes activos | `verify-mobility-v12-provider-operations.sh` (Test 1 & 2) |
| **Captura Financiera PSP** | `public.payment_provider_events`, `public.ride_payment_intents` | `service_role` (Trusted PSP Webhook) | Propietario (solo estado proyectado) | Ninguna (no puede auto-autorizar dinero electrónico) | Validación de firma HMAC-SHA256, tolerancia temporal y monto exacto | `verify-mobility-financial-authority-v8.sh` (Test B & E2) |
| **Liquidación & Doble Entrada** | `public.ledger_entries`, `public.ledger_transactions` | `service_role` (RPC `mobility_settle_trip_v2`) | `service_role` (lectura proyectada para usuario) | Ninguna | Doble entrada balanceada obligatoria: $\sum \text{amount\_minor} = 0$ | `verify-mobility-financial-authority-v8.sh` (Test F & H) |
| **Pasaporte del Vehículo (`VehicleEvents`)** | `public.vehicle_events` | RPC `vehicle_append_passport_event_v1` | Propietario del vehículo | Envío de lecturas para validación | Certificación de procedencia, hash SHA-256 inmutable | `verify-revenue-to-platform-e2e.sh` |
| **Operaciones de Proveedores** | `public.provider_operational_status`, `public.principal_capabilities` | RPC `provider_go_online_v1`, `provider_go_offline_v1` | Proveedor autenticado (`auth.uid()`) | Cambio de estado de disponibilidad (Standby) | Validación de capacidades aprobadas antes de permitir conexión | `verify-mobility-v12-provider-operations.sh` (Test 6) |
| **Antifraude & Riesgo** | `public.risk_decisions` | `service_role` (Motor de riesgo y confianza) | `service_role`, Auditor de cumplimiento | Ninguna | Bloqueo, desafío o suspensión basado en reglas y telemetría | `verify-revenue-to-platform-e2e.sh` |
| **Kill Switches de Plataforma** | `public.platform_kill_switches` | `service_role` (Admin / SRE Control Plane) | Todos los servicios de backend | Ninguna | Desactivación granular e instantánea de mercados, servicios o rieles | `verify-revenue-to-platform-e2e.sh` |
| **Organizaciones & Flotas (B2B)** | `public.organizations`, `public.organization_memberships` | Administrador de Organización, `service_role` | Miembros de la organización según rol | Operaciones de despacho y lectura de vehículos de flota | Aislamiento estricto multi-tenant (Tenant A no accede a Tenant B) | `verify-revenue-to-platform-e2e.sh` |

---
**Garantía:** Toda transición de estado y mutación sensible se ejecuta en transacciones atómicas con validación de concurrencia y guardas fail-closed.
