# Architecture Map — MEET

> Mapa vivo de componentes y dependencias.
> Mantener sincronizado con código (Loop B) y post-merge (Loop H).

## High-level diagram (text)

```
┌─────────────────────────────────────────────────────────────┐
│                       Mobile (Android)                       │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐    │
│  │  UI/Compose │←→│  ViewModels  │←→│   Repositories  │    │
│  └─────────────┘  └──────────────┘  └─────────────────┘    │
│         ↑                ↑                  ↑               │
│         ↓                ↓                  ↓               │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐    │
│  │  Components │  │   UseCases   │  │   Data Sources  │    │
│  │ (DtcUtils,  │  │  (Domain)    │  │ Room + Supabase │    │
│  │  Diagrams)  │  │              │  │   + OBD/BLE     │    │
│  └─────────────┘  └──────────────┘  └─────────────────┘    │
│         ↑                                  ↑               │
│         └──────────── DI (AppModule) ──────┘                │
└─────────────────────────────────────────────────────────────┘
                ↑                            ↑
                │ sync (ElysiumCloud)       │ OBD / BLE
                ↓                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    Supabase (Backend)                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐ │
│  │   Postgres   │  │ Auth + RLS   │  │ Edge Functions   │ │
│  │  + RLS       │  │              │  │ (vanguard,       │ │
│  │  + Migrations│  │              │  │  marketplace,    │ │
│  │              │  │              │  │  stripe, etc.)   │ │
│  └──────────────┘  └──────────────┘  └──────────────────┘ │
│         ↑                                     ↑             │
│         │                                     │             │
│  ┌──────────────┐                    ┌──────────────────┐  │
│  │   Storage    │                    │  Outbox Events   │  │
│  │ (background  │                    │  (reconciliation)│  │
│  │  images, etc)│                    │                  │  │
│  └──────────────┘                    └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                ↑                              ↑
                │                              │
┌─────────────────────────────────────────────────────────────┐
│                          Web (React)                         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  App.tsx → components/* → services/* → Supabase JS   │   │
│  │  (Vanguard UI, Marketplace, GDPR, CRM, Fleet, etc.) │   │
│  └──────────────────────────────────────────────────────┘   │
│                          ↓                                  │
│                    Vercel (deploy)                            │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                  Ingest service (Python)                     │
│  FastAPI / Flask + Alembic + Docker + pytest                 │
│  → Enriqeuce DTC database (dtc_database.json)                │
│  → Sincroniza con Supabase (migrations, content)             │
└─────────────────────────────────────────────────────────────┘
```

## Module map

### Android packages

```
com.elysium369.meet
├── core
│   ├── access            # Control de acceso
│   ├── billing           # GaugeBillingManager (subs)
│   ├── dtc               # Knowledge OS / DTC engine
│   ├── knowledge         # Knowledge graph + pack verified
│   ├── monetization      # MonetizationPolicy
│   ├── obd               # OBD stack (BLE/WiFi, DTC loader)
│   │   ├── DtcDatabaseLoader
│   │   ├── DtcWebEnrichmentStore
│   │   └── VerifiedKnowledgePackLoader
│   ├── share             # Sharing utilities
│   ├── sync              # ElysiumCloudServices
│   ├── vanguard          # Vanguard commerce logic
│   └── video             # HudProjectionService (overlay)
├── data
│   ├── local             # Room (MeetDatabase + DAOs + Entities)
│   └── supabase          # Remote repos (GaugeMarketplaceRepository)
├── di
│   └── AppModule         # Hilt/Koin DI
├── ui
│   ├── components        # Compose reusable (DtcUtils, Diagrams, Oscilloscope)
│   ├── screens           # Compose screens (Home, Dtc, Dashcam, etc.)
│   ├── GaugeMarketplaceViewModel
│   ├── KnowledgeOsDebugViewModel
│   └── ObdViewModel
└── MeetApplication       # App entry
```

### Supabase edge functions

```
supabase/functions/
├── _shared/                              # utilities
├── accept-repair-offer/                  # POST: aceptar oferta
├── close-repair/                         # POST: cerrar repair
├── payment-policy-router/                # routing de pagos
├── stripe-webhook/                       # webhook Stripe
├── sync-vanguard-outbox/                 # outbox processor
├── transition-repair-work-order/         # state machine
└── verify-provider/                      # verificación proveedor
```

### React components

```
components/
├── AdCampaignConsole         # Campañas publicitarias
├── AppErrorBoundary          # Error boundary global
├── ContextualSuggestions     # Sugerencias contextuales
├── DataConsentModal          # GDPR consent
├── FleetDashboard            # Dashboard flota
├── GDPRComplianceView        # Vista compliance
├── OfferForm                 # Form oferta
├── PartsRequestForm          # Form pedido partes
├── PayoutsView               # Vista payouts
├── PlatformCommandCenter     # Centro de comando
├── ProviderProfilePanel      # Perfil proveedor
├── ProviderWorkTray          # Bandeja proveedor
├── RepairRequestForm         # Form solicitud repair
├── ReportPreview             # Preview de reporte
├── SubscriptionCheckout      # Checkout suscripción
├── VehicleTimelineView       # Timeline vehículo
├── VerifiedCompanyPanel      # Panel empresa verificada
└── WorkshopCRM               # CRM taller
```

## Dependency boundaries

- **Mobile ↔ Supabase**: siempre vía Supabase JS client o Edge Functions (nunca acceso directo a DB desde móvil fuera de la API)
- **Web ↔ Supabase**: misma regla
- **Mobile → OBD**: transporte BLE o WiFi, parsers en `core/obd/`
- **Ingest → Supabase**: solo vía service role (nunca desde frontend)
- **Web → Android**: distribución vía Play Store, no comunicación directa runtime

## Data flow crítico: Repair lifecycle

```
1. User creates RepairRequest (web)
   → POST supabase
   → RLS check
   → INSERT into repair_work_orders (status=open)
2. Providers receive in ProviderWorkTray (web/mobile)
   → accept-repair-offer edge function
   → INSERT into vanguard_commerce_events_ledger
3. State transition: open → in_progress → done
   → transition-repair-work-order edge function
   → RLS check + lock
4. Close: close-repair
   → triggers payout via payment-policy-router
   → Stripe webhook updates ledger
5. Outbox sync: sync-vanguard-outbox
   → reconciliación con stripe + ledger
```

## Out of scope (no mapear aquí)

- Generadores Python de DTC DB (data prep, no runtime)
- Scripts de build / deploy
- Documentación histórica (ver `docs/`)