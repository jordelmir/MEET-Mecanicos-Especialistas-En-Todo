# Project Memory — MEET

> Memoria semántica derivada del código real.
> Si este archivo contradice el código, **gana el código**.
> Actualizar vía Loop B (RAG Refresh) o Loop H (Post-Merge Learning).

## Project

- **Name**: MEET — Mecánicos Especialistas En Todo
- **Domain**: Automotive diagnostics & repair platform
- **Stack**:
  - Frontend: React 18 + TypeScript + Vite 6 + Tailwind 3
  - Backend: Supabase (Postgres + Auth + Edge Functions + Storage)
  - Mobile: Android nativo (Kotlin + Jetpack Compose + Room)
  - Data pipeline: Python (FastAPI-style ingest, SQLite intermediate, JSON for DTC database)
  - Deploy: Vercel (web) + Google Play (Android)
  - Repo: privado (`jordelmir/MEET-Mecanicos-Especialistas-En-Todo`)

## Current Architecture

### Web (`components/`, `src/`, `App.tsx`, `index.html`)

Single-page app en React con UI dirigida por componentes modulares. Páginas y vistas principales:

- `HomeScreen`, `VehicleFormScreen`, `DtcScreen`, `DtcRepairGuideScreen`
- `DashcamScreen`, `ComponentLocatorScreen`
- `GaugeMarketplaceScreen`, `GaugePreviewSheet`
- `KnowledgeOsDebugScreen`
- Componentes de monetización: `AdCampaignConsole`, `SubscriptionCheckout`, `PayoutsView`, `WorkshopCRM`, `FleetDashboard`, `PlatformCommandCenter`
- Componentes de compliance: `GDPRComplianceView`, `DataConsentModal`, `VerifiedCompanyPanel`
- `BrandModuleRegistry` (lib/vanguard/) — registro de módulos por marca de vehículo

### Android (`android/app/src/main/kotlin/com/elysium369/meet/`)

Estructura por capas:

- `core/obd/` — capa OBD (DtcDatabaseLoader, DtcWebEnrichmentStore, VerifiedKnowledgePackLoader)
- `core/billing/` — GaugeBillingManager (suscripciones)
- `core/monetization/` — MonetizationPolicy
- `core/sync/` — ElysiumCloudServices (sync con Supabase)
- `core/knowledge/` — Knowledge OS (motor de conocimiento automotriz)
- `core/vanguard/` — Vanguard Commerce
- `core/access/` — control de acceso
- `core/video/` — HudProjectionService (overlay de video telemetría)
- `data/local/` — Room (MeetDatabase, DAOs, Entities)
- `data/local/dao/` — KnowledgeGraphDao, KnowledgeOsDao, VanguardCommerceDao, VanguardTelemetryDao
- `data/local/entities/` — entidades Room
- `data/supabase/` — GaugeMarketplaceRepository (remote)
- `ui/` — Compose screens, ViewModels, components (incluye DtcUtils, MechanicalDiagrams2D, VirtualOscilloscope)
- `di/` — AppModule (inyección de dependencias)

### Backend / Supabase (`supabase/`)

- **Migraciones** (12 al momento del bootstrap):
  - `20260629133000` vanguard_commerce_events_ledger
  - `20260629143000` vanguard_access_policy_foundation
  - `20260629150000` vanguard_brand_aliases
  - `20260629170000` vanguard_p0_foundation
  - `20260629173000` vanguard_rpc_procedures
  - `20260629190000` gauge_marketplace_publish_flow
  - `20260630100000` vanguard_backend_real_hardening
  - `20260630103000` vanguard_backend_real_reconciliation
  - `20260630104500` fix_transition_repair_lock
  - `20260630112000` gauge_marketplace_trust_verification
- **Edge Functions** (`supabase/functions/`):
  - `accept-repair-offer`, `close-repair`, `transition-repair-work-order`
  - `verify-provider`, `payment-policy-router`, `stripe-webhook`
  - `sync-vanguard-outbox`, `_shared/` (utilidades comunes)
- **Schema principal**: `supabase_schema.sql`

### Data

- `dtc_database.json` (6.7 MB) — base de DTCs enriquecida
- `meet_dtc.db` (16 KB) — SQLite local con subset operativo
- Generadores Python: `generate_db.py`, `generate_elite.py`, `elite_templates.py`

### Ingest service (`meet-elite-ingest/`)

Sub-proyecto Python con Docker, Alembic migrations, pytest, FastAPI/Flask style. Ingesta datos DTC desde fuentes externas.

### Deploy

- `vercel.json` / `.vercel/` → web app
- `android/` → APK firmado vía `GUIA_FIRMA_APK.md`
- `.env`, `.env.local`, `.env.example` para configuración runtime

## Critical Domains

### 1. Telemetría automotriz

- Lectura OBD-II por BLE y WiFi
- PIDs estándar + extendidos
- Freeze frame data para ranking de causas DTC
- HUD projection sobre video

### 2. Comercio (Vanguard)

- Repair work orders con state machine (`transition-repair-work-order`)
- Outbox pattern para eventos (`sync-vanguard-outbox`, `vanguard_commerce_events_ledger`)
- Reconciliación (`vanguard_backend_real_reconciliation`)
- Pagos vía Stripe (`stripe-webhook`, `payment-policy-router`)
- Trust verification (`verify-provider`, `gauge_marketplace_trust_verification`)

### 3. Knowledge OS

- Knowledge graph de fallas y reparaciones
- Knowledge pack verified (`meet_verified_pack.json`)
- DTC enrichment web (`DtcWebEnrichmentStore`)

### 4. Marketplace (Gauges)

- Publicación de gauges con validación
- Billing de suscripciones
- Background images remotas
- Trust verification

## Safety Rules

- **VIN / GPS nunca en logs** (riesgo PII automotriz)
- **Secrets nunca en repo** — `.gitignore` cubre `.env`, keystores, etc.
- **service_role key** solo en Edge Functions, nunca en frontend
- **RLS activa** en todas las tablas multi-tenant
- **Migraciones** siempre con rollback documentado
- **Sin force-push a main** — todo por PR

## Known Constraints

- Repo privado (acceso por GitHub collaborators)
- Vercel deploy requiere env vars en dashboard
- Android requiere keystore separado (no en repo)
- Supabase project configurado en la nube (no self-hosted aún)

## Important Commands

### Build / dev

```bash
# Web
pnpm install --frozen-lockfile
pnpm dev
pnpm build
pnpm preview

# Android
cd android && ./gradlew assembleDebug
cd android && ./gradlew test

# Ingest
cd meet-elite-ingest && docker compose up
cd meet-elite-ingest && pytest

# Supabase
supabase db lint
supabase migration up
supabase functions deploy <name>
```

### Mavis loops

```bash
./scripts/mavis-loop.sh continuous
./scripts/mavis-loop.sh rag-refresh
./scripts/mavis-loop.sh security
./scripts/mavis-loop.sh test-gap
./scripts/mavis-loop.sh performance
./scripts/mavis-loop.sh docs-sync
./scripts/mavis-loop.sh post-merge
./scripts/mavis-loop.sh dependency
```

## Current Risks

> Ver `.mavis/memory/known-risks.md` para detalle.

Top 3 al bootstrap (2026-07-02):

1. **WIP sin commitear** — 42 archivos modificados + 47 untracked en `stash@{0}` (commit base previo al bootstrap).
2. **`gh` no autenticado** en la Mac local — bloquea push/PR hasta resolver.
3. **Sin tests / typecheck / lint scripts** en `package.json` — quality gate para Node no tiene comandos definidos.

## Last Updated

2026-07-02 — bootstrap inicial por Mavis (Loop A, primer pase). Sin Loop B aún aplicado.