# MEET / ELYSIUM — CANONICAL CAPABILITY REGISTRY V2
## GATE -1: Zero-Loss Complete Product Capability Inventory & Economic Graph

> **Doctrine #0 (AGENTS.md):**
> *"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad."*
> **Regla Inviolable:** Ninguna capacidad existente puede ser eliminada, sustituida, degradada o desconectada. Toda capacidad alimenta al menos uno de los 11 motores económicos de la plataforma del vehículo.

---

### 1. Definición del Registro Canónico (27 Campos)
Cada registro documenta el estado real, la procedencia del código, las dependencias y el rol económico dentro del sistema operativo del vehículo.

---

### 2. Catálogo Exhaustivo de Capacidades

```yaml
CAPABILITY_ID: obd-core-scanner
NAME: Scanner OBD-II y Telemetría en Tiempo Real
DOMAIN: Automotive
ENTRY_POINT: Route "scanner", Route "connect"
USER_ROLE: Consumer, Mechanic, Fleet_Manager
CURRENT_STATUS: PRODUCTION
UI_SURFACE: ScannerScreen, AdapterSearchSheet
ANDROID_IMPLEMENTATION: com.elysium369.meet.ui.screens.ScannerScreen, com.elysium369.meet.core.obd.*
WEB_IMPLEMENTATION: NONE (Hardware BLE/Classic dependency)
BACKEND_IMPLEMENTATION: public.telemetry_snapshots (Sync opcional)
DATABASE_AUTHORITY: public.diagnostic_snapshots
EXTERNAL_PROVIDER: ELM327 / STN / OBDLink Bluetooth Adapter
OFFLINE_SUPPORT: FULL_OFFLINE
ONLINE_REQUIREMENT: NONE
SECURITY_BOUNDARY: BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION
DATA_PRODUCED: Real-time PIDs (RPM, Speed, Coolant Temp, MAF, Fuel Trim)
DATA_CONSUMED: Raw ELM327 AT/ST response bytes
MONETIZATION_ROLE: RETENTION_FREE (Core value gateway)
RETENTION_ROLE: HIGH_FREQUENCY_UTILITY
MARKETPLACE_ROLE: DEMAND_GENERATOR
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: com.elysium369.meet.core.obd.ObdConnectionManager
KNOWN_FAILURES: Incompatibilidad con clones ELM327 chinos v2.1 de bajo costo
TEST_COVERAGE: ObdProtocolDecoderTest.kt
OBSERVABILITY: obd_connected, scan_started, scan_completed
TRUTH_STATE: OBSERVED
ACTION: PRESERVE

---

CAPABILITY_ID: dtc-fault-intelligence
NAME: Detección y Análisis de Códigos de Falla (DTC)
DOMAIN: Diagnostics
ENTRY_POINT: Route "dtc", Route "dtcs"
USER_ROLE: Consumer, Mechanic
CURRENT_STATUS: PRODUCTION
UI_SURFACE: DtcScreen
ANDROID_IMPLEMENTATION: com.elysium369.meet.ui.screens.DtcScreen, com.elysium369.meet.core.diagnostics.*
WEB_IMPLEMENTATION: NONE
BACKEND_IMPLEMENTATION: public.diagnostic_snapshots
DATABASE_AUTHORITY: public.diagnostic_snapshots, public.repair_intents
EXTERNAL_PROVIDER: OBD Mode 03 / Mode 07 / Mode 0A
OFFLINE_SUPPORT: FULL_OFFLINE
ONLINE_REQUIREMENT: RECONCILIATION_ONLY
SECURITY_BOUNDARY: Local memory, isolated Room DB
DATA_PRODUCED: Confirmed DTCs, Pending DTCs, Permanent DTCs
DATA_CONSUMED: Mode 03 hex response strings
MONETIZATION_ROLE: SUBSCRIPTION (MEET Pro unlock for Mode 06 & deep clearing)
RETENTION_ROLE: EMERGENCY_RELIANCE
MARKETPLACE_ROLE: DEMAND_GENERATOR (Dispara solicitudes de servicio)
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: obd-core-scanner
KNOWN_FAILURES: Fabricantes con códigos propietarios no estándar
TEST_COVERAGE: DtcDecoderTest.kt
OBSERVABILITY: dtc_detected, dtc_cleared
TRUTH_STATE: OBSERVED
ACTION: MONETIZE

---

CAPABILITY_ID: visual-3d-engine
NAME: Motor 3D Interactivo y Localizador de Componentes
DOMAIN: Automotive
ENTRY_POINT: Route "engine_3d", Route "component_locator"
USER_ROLE: Consumer, Mechanic, Student
CURRENT_STATUS: PRODUCTION
UI_SURFACE: ComponentLocatorScreen, Filament/OpenGL View
ANDROID_IMPLEMENTATION: com.elysium369.meet.ui.screens.ComponentLocatorScreen, com.elysium369.meet.visual3d.*
WEB_IMPLEMENTATION: WebGL Component Viewer
BACKEND_IMPLEMENTATION: public.canonical_parts_catalog
DATABASE_AUTHORITY: public.part_requests, public.parts_catalog
EXTERNAL_PROVIDER: Filament Android 3D Engine
OFFLINE_SUPPORT: FULL_OFFLINE (Meshes empaquetados localmente)
ONLINE_REQUIREMENT: NONE
SECURITY_BOUNDARY: GPU Surface, Memory limits
DATA_PRODUCED: Selected part identifier, highlighted subsystem
DATA_CONSUMED: 3D GLTF/GLB models, DTC association metadata
MONETIZATION_ROLE: VALUE (Diferenciador visual premium)
RETENTION_ROLE: HIGH_FREQUENCY_UTILITY
MARKETPLACE_ROLE: DEMAND_GENERATOR (Conecta pieza 3D -> Cotización de repuesto)
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: none
KNOWN_FAILURES: Caída de framerate en dispositivos con menos de 3GB RAM
TEST_COVERAGE: Visual3dModelTest.kt
OBSERVABILITY: visual3d_part_inspected
TRUTH_STATE: DERIVED
ACTION: CONNECT

---

CAPABILITY_ID: vehicle-garage-management
NAME: Garage Digital y Expediente del Vehículo
DOMAIN: Automotive
ENTRY_POINT: Route "garage", Route "vehicle_detail/{vehicleId}", Route "vehicle_form"
USER_ROLE: Consumer, Fleet_Manager, Workshop_Admin
CURRENT_STATUS: PRODUCTION
UI_SURFACE: GarageScreen, VehicleDetailScreen, VehicleFormScreen
ANDROID_IMPLEMENTATION: com.elysium369.meet.ui.screens.GarageScreen, com.elysium369.meet.vehiclelife.*
WEB_IMPLEMENTATION: /garage (React client)
BACKEND_IMPLEMENTATION: public.vehicles table
DATABASE_AUTHORITY: public.vehicles, public.vehicle_events
EXTERNAL_PROVIDER: VIN Decoder NHTSA API
OFFLINE_SUPPORT: FULL_OFFLINE (Sincronizado vía Room)
ONLINE_REQUIREMENT: RECONCILIATION_ONLY
SECURITY_BOUNDARY: RLS owner isolation (auth.uid() = user_id)
DATA_PRODUCED: Vehicle entities (VIN, Make, Model, Year, Engine, Plates)
DATA_CONSUMED: User input, OBD VIN read Mode 09
MONETIZATION_ROLE: RETENTION_FREE (Hasta 2 vehículos; Pro para flotas)
RETENTION_ROLE: MOAT_HISTORY
MARKETPLACE_ROLE: SUPPLY_QUALIFIER (Garantiza compatibilidad vehicular)
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: none
KNOWN_FAILURES: None
TEST_COVERAGE: VehicleRepositoryTest.kt
OBSERVABILITY: vehicle_added, vehicle_updated, vehicle_selected
TRUTH_STATE: PHYSICALLY_VERIFIED
ACTION: PRESERVE

---

CAPABILITY_ID: vehicle-passport-history
NAME: Pasaporte Inmutable del Vehículo (Vehicle Passport)
DOMAIN: Automotive
ENTRY_POINT: Route "vehicle_history/{vehicleId}", Route "meet_dna", Route "vanguard_dna"
USER_ROLE: Consumer, Buyer, Inspector, Insurance_Partner
CURRENT_STATUS: PRODUCTION
UI_SURFACE: VehicleHistoryScreen, MeetDnaScreen
ANDROID_IMPLEMENTATION: com.elysium369.meet.ui.screens.VehicleHistoryScreen, com.elysium369.meet.vehiclelife.passport.*
WEB_IMPLEMENTATION: /passport/:vehicleId (Safe projection viewer)
BACKEND_IMPLEMENTATION: public.vehicle_events, public.certified_reports
DATABASE_AUTHORITY: public.vehicle_events, public.certified_reports
EXTERNAL_PROVIDER: SHA-256 Hashing Engine
OFFLINE_SUPPORT: CACHED
ONLINE_REQUIREMENT: RECONCILIATION_ONLY
SECURITY_BOUNDARY: Safe Projection RLS (oculta PII y datos privados de pagos)
DATA_PRODUCED: Immutable timeline events (Maintenance, DTCs, Repairs, Mileage, Inspections)
DATA_CONSUMED: Sensor readings, certified PDF reports, service completions
MONETIZATION_ROLE: SUBSCRIPTION (MEET Pro + B2B Pre-Purchase Inspection report)
RETENTION_ROLE: MOAT_HISTORY
MARKETPLACE_ROLE: TRUST
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: vehicle-garage-management
KNOWN_FAILURES: None
TEST_COVERAGE: VehiclePassportTest.kt, HashEngineParityTest.kt
OBSERVABILITY: passport_viewed, passport_event_appended
TRUTH_STATE: OBSERVED
ACTION: EXPAND

---

CAPABILITY_ID: ai-multi-provider-diagnostic
NAME: Asistente Diagnóstico IA Multi-Proveedor (Elysium EVAIR)
DOMAIN: Diagnostics
ENTRY_POINT: Route "ai/{dtcCode}", Route "elysium_ai", Route "evair", Route "ai_settings"
USER_ROLE: Consumer, Mechanic
CURRENT_STATUS: PRODUCTION
UI_SURFACE: AiDiagnosticScreen, ElysiumAiScreen, AiSettingsScreen
ANDROID_IMPLEMENTATION: com.elysium369.meet.ui.screens.AiDiagnosticScreen, com.elysium369.meet.ai.*
WEB_IMPLEMENTATION: NONE
BACKEND_IMPLEMENTATION: public.diagnostic_ai_predictions
DATABASE_AUTHORITY: public.repair_intents
EXTERNAL_PROVIDER: Google Gemini API, OpenAI API, Anthropic Claude API, Local Fallback
OFFLINE_SUPPORT: CACHED (Reglas expertas locales cuando no hay red)
ONLINE_REQUIREMENT: OPTIONAL (Cloud enrichment)
SECURITY_BOUNDARY: Encrypted API KeyStore (Android Keystore System)
DATA_PRODUCED: Candidate root causes, recommended action class, confidence scores
DATA_CONSUMED: DTC code, Freeze Frame data, vehicle symptoms
MONETIZATION_ROLE: SUBSCRIPTION (MEET Pro: tokens ilimitados / modelos frontera)
RETENTION_ROLE: VALUE
MARKETPLACE_ROLE: DEMAND_GENERATOR (Sugiere acción mecánica -> Crea RepairIntent)
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: dtc-fault-intelligence
KNOWN_FAILURES: Latencia elevada en conexiones 3G
TEST_COVERAGE: AiRouterTest.kt
OBSERVABILITY: ai_diagnosis_requested, ai_tokens_consumed
TRUTH_STATE: DERIVED
ACTION: MONETIZE

---

CAPABILITY_ID: certified-pdf-reports
NAME: Generador de Reportes PDF Certificados con Hash SHA-256 & QR
DOMAIN: Diagnostics
ENTRY_POINT: Route "reports", Route "inspection_session/{vehicleId}", Route "meet_perito"
USER_ROLE: Mechanic, Inspector, Consumer, Dealer
CURRENT_STATUS: PRODUCTION
UI_SURFACE: ReportScreen, InspectionSessionScreen, MeetPeritoScreen
ANDROID_IMPLEMENTATION: com.elysium369.meet.core.reports.*, com.elysium369.meet.ui.screens.ReportScreen
WEB_IMPLEMENTATION: /verify/:reportId (Verificador forense público)
BACKEND_IMPLEMENTATION: public.certified_reports, public.report_signatures
DATABASE_AUTHORITY: public.certified_reports
EXTERNAL_PROVIDER: Android PdfDocument / Forensic Verifier URL
OFFLINE_SUPPORT: FULL_OFFLINE (Generación local determinista)
ONLINE_REQUIREMENT: RECONCILIATION_ONLY (Sincronización del hash para verificación)
SECURITY_BOUNDARY: Ed25519 / SHA-256 integridad inmutable; inmutabilidad post-firma
DATA_PRODUCED: Certified PDF byte arrays, SHA-256 root digest, 6-field minimal QR
DATA_CONSUMED: Pre/Post scan snapshots, repair actions, mechanic signature
MONETIZATION_ROLE: DIRECT_PAYMENT / SUBSCRIPTION (Paquetes de reportes o Pro)
RETENTION_ROLE: MOAT_HISTORY
MARKETPLACE_ROLE: TRUST
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: vehicle-garage-management, dtc-fault-intelligence
KNOWN_FAILURES: None (Paridad cruzada TS ≡ Kotlin verificada)
TEST_COVERAGE: HashEngineParityTest.kt, ci-verify.sh
OBSERVABILITY: report_generated, report_verified
TRUTH_STATE: PHYSICALLY_VERIFIED
ACTION: MONETIZE

---

CAPABILITY_ID: parts-marketplace-auction
NAME: Marketplace y Subasta de Repuestos con Compatibilidad Exacta
DOMAIN: Commerce
ENTRY_POINT: Route "marketplace", Route "parts_store", Route "part_request"
USER_ROLE: Consumer, Mechanic, Auto_Parts_Store
CURRENT_STATUS: PRODUCTION
UI_SURFACE: MarketplaceScreen, PartRequestScreen
ANDROID_IMPLEMENTATION: com.elysium369.meet.ui.screens.MarketplaceScreen, com.elysium369.meet.automotive.parts.*
WEB_IMPLEMENTATION: /parts (B2B Parts Store Dashboard)
BACKEND_IMPLEMENTATION: public.part_requests, public.part_offers, public.parts_stores
DATABASE_AUTHORITY: public.part_requests, public.part_offers
EXTERNAL_PROVIDER: Supabase Realtime
OFFLINE_SUPPORT: CACHED
ONLINE_REQUIREMENT: REALTIME_STRICT (Ofertas en vivo de repuesteras)
SECURITY_BOUNDARY: RLS Store Isolation, No client-side price mutation
DATA_PRODUCED: Part requests (VIN, OEM, system, photo), Store bids (Price, Warranty, Delivery)
DATA_CONSUMED: Vehicle VIN, Catalog OEM numbers, Store catalog
MONETIZATION_ROLE: TAKE_RATE (Comisión transaccional por venta de repuesto)
RETENTION_ROLE: VALUE
MARKETPLACE_ROLE: TRANSACTION_CORE
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: vehicle-garage-management
KNOWN_FAILURES: Requiere verificación visual obligatoria cuando no hay OEM exacto
TEST_COVERAGE: PartsAuctionTest.kt
OBSERVABILITY: part_requested, part_bid_received, part_order_settled
TRUTH_STATE: PHYSICALLY_VERIFIED
ACTION: CONNECT

---

CAPABILITY_ID: tow-truck-roadside-dispatch
NAME: Asistencia Vial y Despacho de Grúas (Tow Truck Service)
DOMAIN: Mobility
ENTRY_POINT: Route "tow_truck_service", Route "tow_truck", Route "tow_active_tracking"
USER_ROLE: Passenger, Tow_Driver, Fleet_Manager
CURRENT_STATUS: PRODUCTION
UI_SURFACE: TowTruckServiceScreen, TowFulfillmentScreen
ANDROID_IMPLEMENTATION: com.elysium369.meet.ui.screens.TowTruckServiceScreen, com.elysium369.meet.ui.screens.tow.*
WEB_IMPLEMENTATION: /tow/dispatch (Consola de grúas)
BACKEND_IMPLEMENTATION: public.tow_requests, public.tow_offers, public.tow_trips
DATABASE_AUTHORITY: public.tow_requests, public.tow_trips, public.ledger_entries
EXTERNAL_PROVIDER: OSRM / Google Maps Road Routing
OFFLINE_SUPPORT: NONE
ONLINE_REQUIREMENT: REALTIME_STRICT
SECURITY_BOUNDARY: Contextual Location RPC, Monotonic Sequence Guard
DATA_PRODUCED: Tow job requests (Broken vehicle info, Location, Destination, Tow type)
DATA_CONSUMED: GPS position, Flatbed availability, Road routing evidence
MONETIZATION_ROLE: TAKE_RATE (Comisión por remolque completado)
RETENTION_ROLE: EMERGENCY_RELIANCE
MARKETPLACE_ROLE: TRANSACTION_CORE
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: vehicle-garage-management, mobility-algorithmic-dispatch
KNOWN_FAILURES: None
TEST_COVERAGE: verify-tow-fulfillment.sh
OBSERVABILITY: tow_requested, tow_claimed, tow_completed
TRUTH_STATE: PHYSICALLY_VERIFIED
ACTION: HARDEN

---

CAPABILITY_ID: mobility-global-rides
NAME: Movilidad Urbana de Pasajeros estilo Uber/DiDi/inDrive
DOMAIN: Mobility
ENTRY_POINT: Route "ride_service", Route "ride_passenger_request", Route "ride_driver_cockpit", Route "ride_active_tracking"
USER_ROLE: Passenger, Driver
CURRENT_STATUS: PRODUCTION
UI_SURFACE: PassengerRideRequestScreen, DriverAppScreen, ActiveRideTrackingScreen
ANDROID_IMPLEMENTATION: com.elysium369.meet.mobility.*, com.elysium369.meet.ride.*
WEB_IMPLEMENTATION: /rides (Admin fleet view)
BACKEND_IMPLEMENTATION: public.ride_requests, public.trips, RPC mobility_*
DATABASE_AUTHORITY: public.ride_requests, public.trips, public.ledger_entries
EXTERNAL_PROVIDER: MapLibre, Road Network Routing Engine
OFFLINE_SUPPORT: NONE
ONLINE_REQUIREMENT: REALTIME_STRICT
SECURITY_BOUNDARY: 6-Digit Bcrypt PIN, Safe Trip Sharing Projection, Monotonic GPS Guard
DATA_PRODUCED: Trip state machine events, Authoritative Route Evidence, Zero-sum Ledger Entries
DATA_CONSUMED: Driver presence (post-lockdown via contextual RPC), Fare quotes
MONETIZATION_ROLE: TAKE_RATE (Margen de plataforma deducido en settlement)
RETENTION_ROLE: HIGH_FREQUENCY_UTILITY
MARKETPLACE_ROLE: TRANSACTION_CORE
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: double-entry-financial-ledger
KNOWN_FAILURES: Resuelto en V12 (Fuga de presencia cerrada; decodificadores fail-closed)
TEST_COVERAGE: verify-mobility-v11-public-launch.sh, verify-mobility-v12-provider-operations.sh
OBSERVABILITY: ride_requested, ride_matched, trip_started, trip_completed, ledger_settled
TRUTH_STATE: PHYSICALLY_VERIFIED
ACTION: HARDEN

---

CAPABILITY_ID: universal-provider-operations-os
NAME: Sistema Operativo para Proveedores (Mecánicos, Choferes, Grúas)
DOMAIN: Platform
ENTRY_POINT: Route "provider_registration", Route "workshop_dashboard", Route "ride_driver_cockpit"
USER_ROLE: Driver, Mechanic, Workshop_Admin, Tow_Operator
CURRENT_STATUS: PRODUCTION
UI_SURFACE: ProviderRegistrationScreen, WorkshopDashboardScreen, DriverAppScreen
ANDROID_IMPLEMENTATION: com.elysium369.meet.provider.*
WEB_IMPLEMENTATION: /provider/console (Web Provider Console)
BACKEND_IMPLEMENTATION: public.provider_operational_status, RPC provider_*
DATABASE_AUTHORITY: public.provider_operational_status, public.principal_capabilities
EXTERNAL_PROVIDER: None (Rieles externos fail-closed)
OFFLINE_SUPPORT: CACHED
ONLINE_REQUIREMENT: REALTIME_STRICT
SECURITY_BOUNDARY: Provider Capability Verification, Fail-closed Payout Rails
DATA_PRODUCED: Online/Offline status, Active work queue, Performance metrics, Financial balance
DATA_CONSUMED: Approved vertical capabilities, Marketplace requests
MONETIZATION_ROLE: SUBSCRIPTION / LEAD_FEE (Suscripción taller / Créditos de leads)
RETENTION_ROLE: HIGH_FREQUENCY_UTILITY
MARKETPLACE_ROLE: SUPPLY_QUALIFIER
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: double-entry-financial-ledger
KNOWN_FAILURES: None (Aprobado en V12)
TEST_COVERAGE: ProviderDomainModelsTest.kt, verify-mobility-v12-provider-operations.sh
OBSERVABILITY: provider_online, provider_offline, job_dispatched
TRUTH_STATE: PHYSICALLY_VERIFIED
ACTION: HARDEN

---

CAPABILITY_ID: double-entry-financial-ledger
NAME: Motor Financiero de Doble Entrada y Balanza Cero-Suma
DOMAIN: Platform
ENTRY_POINT: Background authority / RPC settlement
USER_ROLE: System, Financial_Auditor
CURRENT_STATUS: PRODUCTION
UI_SURFACE: None (Core Backend Ledger)
ANDROID_IMPLEMENTATION: com.elysium369.meet.core.money.Money
WEB_IMPLEMENTATION: /admin/finance (Audit reports)
BACKEND_IMPLEMENTATION: public.ledger_transactions, public.ledger_entries, RPC mobility_settle_trip_v2
DATABASE_AUTHORITY: public.ledger_entries
EXTERNAL_PROVIDER: PSP Authoritative Confirmation (Fail-closed)
OFFLINE_SUPPORT: NONE
ONLINE_REQUIREMENT: REALTIME_STRICT
SECURITY_BOUNDARY: service_role only mutation, strictly revoked from authenticated
DATA_PRODUCED: Balanced zero-sum double-entry accounting records (Gross, Fee, Net)
DATA_CONSUMED: Captured payment receipts, Authoritative route quotes
MONETIZATION_ROLE: FINANCIAL_AUTHORITY
RETENTION_ROLE: TRUST
MARKETPLACE_ROLE: TRANSACTION_CORE
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: none
KNOWN_FAILURES: None (Invariante sum(amount_minor) = 0 verificado bajo 100-way concurrency)
TEST_COVERAGE: verify-mobility-financial-authority-v8.sh, test-100-concurrent-dispatches.sh
OBSERVABILITY: ledger_transaction_created, ledger_imbalance_detected
TRUTH_STATE: PHYSICALLY_VERIFIED
ACTION: PRESERVE

---

CAPABILITY_ID: dvir-commercial-inspections
NAME: Inspección Comercial Vehicular DVIR (Driver Vehicle Inspection Report)
DOMAIN: Fleet
ENTRY_POINT: Route "dvir"
USER_ROLE: Driver, Fleet_Manager, Inspector
CURRENT_STATUS: PRODUCTION
UI_SURFACE: DvirScreen
ANDROID_IMPLEMENTATION: com.elysium369.meet.ui.screens.DvirScreen, com.elysium369.meet.inspections.*
WEB_IMPLEMENTATION: /fleet/dvir (Fleet compliance viewer)
BACKEND_IMPLEMENTATION: public.dvir_reports
DATABASE_AUTHORITY: public.dvir_reports, public.vehicle_events
EXTERNAL_PROVIDER: None
OFFLINE_SUPPORT: FULL_OFFLINE
ONLINE_REQUIREMENT: RECONCILIATION_ONLY
SECURITY_BOUNDARY: Inspector Signature & Device Geolocation stamp
DATA_PRODUCED: Pre-trip & Post-trip inspection reports, Defect classifications
DATA_CONSUMED: Vehicle systems checklist, OBD connection status
MONETIZATION_ROLE: ENTERPRISE (SaaS Flotas & Camiones)
RETENTION_ROLE: HIGH_FREQUENCY_UTILITY
MARKETPLACE_ROLE: SUPPLY_QUALIFIER (Garantiza estado mecánico antes de operar)
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: vehicle-garage-management
KNOWN_FAILURES: None
TEST_COVERAGE: DvirReportTest.kt
OBSERVABILITY: dvir_completed, dvir_defect_flagged
TRUTH_STATE: PHYSICALLY_VERIFIED
ACTION: MONETIZE

---

CAPABILITY_ID: camera-hud-dashcam
NAME: Head-Up Display (HUD) con Realidad Aumentada & Dashcam de Seguridad
DOMAIN: Safety
ENTRY_POINT: Route "hud", Route "dashcam"
USER_ROLE: Consumer, Driver
CURRENT_STATUS: PRODUCTION
UI_SURFACE: HudScreen, DashcamScreen, Camera Preview Overlay
ANDROID_IMPLEMENTATION: com.elysium369.meet.ui.screens.HudScreen, com.elysium369.meet.safejourney.*
WEB_IMPLEMENTATION: NONE
BACKEND_IMPLEMENTATION: Local encrypted storage / Incident upload on crash
DATABASE_AUTHORITY: public.ride_safety_signals
EXTERNAL_PROVIDER: CameraX Android API, Device Accelerometer / Gyroscope
OFFLINE_SUPPORT: FULL_OFFLINE
ONLINE_REQUIREMENT: NONE
SECURITY_BOUNDARY: CAMERA, RECORD_AUDIO, Storage Sandbox
DATA_PRODUCED: Rolling video loop, Telemetry overlay, G-force impact logs
DATA_CONSUMED: Speed PID, GPS Coordinates, Camera Feed
MONETIZATION_ROLE: VALUE (Función de seguridad activa en carretera)
RETENTION_ROLE: HIGH_FREQUENCY_UTILITY
MARKETPLACE_ROLE: TRUST (Evidencia visual incontrovertible en caso de choque)
GLOBAL_PLATFORM_ROLE: CORE_PRIMITIVE
DEPENDENCIES: obd-core-scanner
KNOWN_FAILURES: Consumo de batería elevado si no está conectado al cargador del auto
TEST_COVERAGE: SafeJourneyTelemetryTest.kt
OBSERVABILITY: hud_engaged, collision_detected
TRUTH_STATE: OBSERVED
ACTION: HARDEN
```

---

### 3. Matriz de Síntesis Estratégica

| Capacidad | Dominio | Motor Económico Principal | Estado | Acción Canónica |
| :--- | :--- | :--- | :--- | :--- |
| **OBD Core Scanner** | Automotive | ACTIVATION / VALUE | Producción | `PRESERVE` |
| **DTC Intelligence** | Diagnostics | SUBSCRIPTION / VALUE | Producción | `MONETIZE` |
| **Visual 3D Engine** | Automotive | VALUE / MARKETPLACE | Producción | `CONNECT` |
| **Garage OS** | Automotive | RETENTION / MOAT | Producción | `PRESERVE` |
| **Vehicle Passport** | Automotive | DATA MOAT / TRUST | Producción | `EXPAND` |
| **Diagnóstico IA (EVAIR)** | Diagnostics | SUBSCRIPTION / VALUE | Producción | `MONETIZE` |
| **Reportes Certificados** | Diagnostics | TRUST / SUBSCRIPTION | Producción | `MONETIZE` |
| **Subasta de Repuestos** | Commerce | MARKETPLACE / TAKE_RATE | Producción | `CONNECT` |
| **Asistencia Vial & Grúas** | Mobility | TRANSACTION / TAKE_RATE | Producción | `HARDEN` |
| **Movilidad & Viajes** | Mobility | TRANSACTION / TAKE_RATE | Producción | `HARDEN` |
| **Provider Operations OS** | Platform | PLATFORM / SUBSCRIPTION | Producción | `HARDEN` |
| **Doble-Entry Ledger** | Platform | TRUST / FINANCIAL AUTH | Producción | `PRESERVE` |
| **DVIR Flotas** | Fleet | ENTERPRISE | Producción | `MONETIZE` |
| **HUD & Dashcam** | Safety | VALUE / TRUST | Producción | `HARDEN` |

---
**Aprobación:** Este registro canónico gobierna toda evolución en la arquitectura MEET / ELYSIUM. Cero degradación, cero borrado.
