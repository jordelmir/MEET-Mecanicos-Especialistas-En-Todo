-- ══════════════════════════════════════════════════════════════════════════════
-- ELYSIUM VANGUARD — P0 FOUNDATION MIGRATION
-- Creates all domain tables required before scaling.
-- Every table: UUID PK, created_at, updated_at, RLS enabled, owner-scoped policies.
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. USER PROFILES & ROLES
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.user_profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  auth_user_id UUID UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name TEXT NOT NULL DEFAULT '',
  avatar_url TEXT,
  phone_hash TEXT,
  primary_role TEXT NOT NULL DEFAULT 'driver'
    CHECK (primary_role IN (
      'driver', 'enthusiast', 'pro_user', 'mechanic', 'workshop_owner',
      'parts_store', 'tow_provider', 'ride_driver', 'fleet_manager',
      'verified_company', 'creator', 'admin', 'super_admin',
      'support_agent', 'trust_safety_reviewer'
    )),
  experience_level TEXT NOT NULL DEFAULT 'basic'
    CHECK (experience_level IN ('basic', 'plus', 'elite', 'engineering', 'professional', 'enterprise')),
  locale TEXT NOT NULL DEFAULT 'es',
  timezone TEXT NOT NULL DEFAULT 'America/Panama',
  consent_analytics BOOLEAN NOT NULL DEFAULT FALSE,
  consent_location BOOLEAN NOT NULL DEFAULT FALSE,
  consent_telemetry BOOLEAN NOT NULL DEFAULT FALSE,
  consent_marketing BOOLEAN NOT NULL DEFAULT FALSE,
  terms_accepted_at TIMESTAMPTZ,
  privacy_accepted_at TIMESTAMPTZ,
  account_status TEXT NOT NULL DEFAULT 'active'
    CHECK (account_status IN ('active', 'suspended', 'deleted', 'pending_deletion')),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_auth ON public.user_profiles(auth_user_id);

CREATE INDEX IF NOT EXISTS idx_user_profiles_role ON public.user_profiles(primary_role);

CREATE INDEX IF NOT EXISTS idx_user_profiles_status ON public.user_profiles(account_status);

CREATE TABLE IF NOT EXISTS public.user_roles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_profile_id UUID NOT NULL REFERENCES public.user_profiles(id) ON DELETE CASCADE,
  role_name TEXT NOT NULL
    CHECK (role_name IN (
      'driver', 'enthusiast', 'pro_user', 'mechanic', 'workshop_owner',
      'parts_store', 'tow_provider', 'ride_driver', 'fleet_manager',
      'verified_company', 'creator', 'admin', 'super_admin',
      'support_agent', 'trust_safety_reviewer'
    )),
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  granted_by UUID,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_profile_id, role_name)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_profile ON public.user_roles(user_profile_id, is_active);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. PROVIDER PROFILES, VERIFICATIONS & REPUTATION
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.provider_profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_profile_id UUID NOT NULL REFERENCES public.user_profiles(id) ON DELETE CASCADE,
  provider_type TEXT NOT NULL
    CHECK (provider_type IN ('mechanic', 'workshop', 'parts_store', 'tow_provider', 'ride_driver', 'creator')),
  business_name TEXT,
  description TEXT,
  phone TEXT,
  email TEXT,
  location_text TEXT,
  location_lat DOUBLE PRECISION,
  location_lng DOUBLE PRECISION,
  coverage_radius_km DOUBLE PRECISION DEFAULT 10.0,
  specializations TEXT[] DEFAULT '{}',
  certifications JSONB DEFAULT '[]'::jsonb,
  operating_hours JSONB DEFAULT '{}'::jsonb,
  is_verified BOOLEAN NOT NULL DEFAULT FALSE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'active', 'suspended', 'banned')),
  version INTEGER NOT NULL DEFAULT 1,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_profile_id, provider_type)
);

CREATE INDEX IF NOT EXISTS idx_provider_profiles_type_status ON public.provider_profiles(provider_type, status, is_verified);

CREATE INDEX IF NOT EXISTS idx_provider_profiles_user ON public.provider_profiles(user_profile_id);

CREATE INDEX IF NOT EXISTS idx_provider_profiles_location ON public.provider_profiles(location_lat, location_lng)
  WHERE location_lat IS NOT NULL AND location_lng IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.provider_verifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_profile_id UUID NOT NULL REFERENCES public.provider_profiles(id) ON DELETE CASCADE,
  verification_type TEXT NOT NULL
    CHECK (verification_type IN ('identity', 'business_license', 'insurance', 'certification', 'address', 'phone', 'email', 'website')),
  document_url TEXT,
  document_hash TEXT,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'needs_more_info', 'approved', 'rejected', 'expired', 'revoked')),
  reviewer_id UUID,
  reviewer_notes TEXT,
  submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  reviewed_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ,
  version INTEGER NOT NULL DEFAULT 1,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_provider_verifications_profile_status ON public.provider_verifications(provider_profile_id, status);

CREATE INDEX IF NOT EXISTS idx_provider_verifications_reviewer ON public.provider_verifications(reviewer_id)
  WHERE reviewer_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.provider_reputation_scores (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_profile_id UUID NOT NULL REFERENCES public.provider_profiles(id) ON DELETE CASCADE,
  -- Weighted metrics (0.0 – 1.0 scale)
  repair_success_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  comeback_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  diagnostic_accuracy DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  avg_response_time_minutes DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  avg_completion_time_minutes DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  dispute_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  warranty_claim_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  repeat_customer_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  verified_repairs_count INTEGER NOT NULL DEFAULT 0,
  documentation_quality_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  quote_accuracy DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  on_time_arrival_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  parts_return_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  -- Computed composite
  technical_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  -- Badge level
  badge_level TEXT NOT NULL DEFAULT 'technician'
    CHECK (badge_level IN ('technician', 'certified', 'pro', 'master', 'elite_workshop')),
  -- Specializations with scores
  specialization_scores JSONB NOT NULL DEFAULT '{}'::jsonb,
  -- Stats
  total_reviews INTEGER NOT NULL DEFAULT 0,
  avg_star_rating DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  total_completed_jobs INTEGER NOT NULL DEFAULT 0,
  last_calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider_profile_id)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. REPAIR NETWORK — FULL STATE MACHINE
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.repair_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_profile_id UUID NOT NULL REFERENCES public.user_profiles(id) ON DELETE CASCADE,
  vehicle_id UUID,
  vin_hash TEXT,
  vehicle_make TEXT,
  vehicle_model TEXT,
  vehicle_year INTEGER,
  vehicle_engine TEXT,
  vehicle_transmission TEXT,
  vehicle_mileage INTEGER,
  dtc_codes TEXT[] DEFAULT '{}',
  freeze_frame JSONB DEFAULT '{}'::jsonb,
  live_data_snapshot JSONB DEFAULT '{}'::jsonb,
  symptoms TEXT,
  priority TEXT NOT NULL DEFAULT 'medium'
    CHECK (priority IN ('low', 'medium', 'high', 'emergency')),
  location_text TEXT,
  location_lat DOUBLE PRECISION,
  location_lng DOUBLE PRECISION,
  desired_service_type TEXT DEFAULT 'general'
    CHECK (desired_service_type IN ('general', 'electrical', 'engine', 'transmission', 'brakes', 'ac', 'suspension', 'diagnostics', 'coding', 'pre_purchase')),
  customer_notes TEXT,
  max_budget_cents BIGINT,
  requested_datetime TIMESTAMPTZ,
  attachments JSONB DEFAULT '[]'::jsonb,
  diagnostic_confidence DOUBLE PRECISION,
  status TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN (
      'draft', 'published', 'triaged', 'waiting_offers', 'offer_received',
      'offer_accepted', 'mechanic_assigned', 'in_route', 'inspection_started',
      'diagnosis_confirmed', 'parts_required', 'waiting_parts',
      'repair_in_progress', 'repair_completed', 'validation_pending',
      'customer_confirmed', 'closed', 'cancelled', 'disputed', 'refunded'
    )),
  version INTEGER NOT NULL DEFAULT 1,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_repair_requests_customer ON public.repair_requests(customer_profile_id, status);

CREATE INDEX IF NOT EXISTS idx_repair_requests_status ON public.repair_requests(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_repair_requests_vehicle ON public.repair_requests(vehicle_id) WHERE vehicle_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_repair_requests_dtcs ON public.repair_requests USING gin(dtc_codes);

CREATE INDEX IF NOT EXISTS idx_repair_requests_location ON public.repair_requests(location_lat, location_lng)
  WHERE location_lat IS NOT NULL AND location_lng IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.repair_offers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  repair_request_id UUID NOT NULL REFERENCES public.repair_requests(id) ON DELETE CASCADE,
  mechanic_profile_id UUID NOT NULL REFERENCES public.provider_profiles(id) ON DELETE CASCADE,
  estimated_price_cents BIGINT NOT NULL CHECK (estimated_price_cents >= 0),
  estimated_time_minutes INTEGER NOT NULL CHECK (estimated_time_minutes > 0),
  labor_fee_cents BIGINT NOT NULL DEFAULT 0 CHECK (labor_fee_cents >= 0),
  travel_fee_cents BIGINT NOT NULL DEFAULT 0 CHECK (travel_fee_cents >= 0),
  required_parts JSONB DEFAULT '[]'::jsonb,
  warranty_days INTEGER NOT NULL DEFAULT 30 CHECK (warranty_days >= 0),
  diagnosis_notes TEXT,
  confidence DOUBLE PRECISION DEFAULT 0.0,
  availability_datetime TIMESTAMPTZ,
  expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '24 hours'),
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'accepted', 'rejected', 'expired', 'withdrawn')),
  version INTEGER NOT NULL DEFAULT 1,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- A mechanic can only have one active offer per request
  UNIQUE (repair_request_id, mechanic_profile_id)
);

CREATE INDEX IF NOT EXISTS idx_repair_offers_request ON public.repair_offers(repair_request_id, status);

CREATE INDEX IF NOT EXISTS idx_repair_offers_mechanic ON public.repair_offers(mechanic_profile_id, status);

CREATE INDEX IF NOT EXISTS idx_repair_offers_expiry ON public.repair_offers(expires_at) WHERE status = 'pending';

CREATE TABLE IF NOT EXISTS public.repair_work_orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  repair_request_id UUID NOT NULL REFERENCES public.repair_requests(id) ON DELETE RESTRICT,
  accepted_offer_id UUID NOT NULL REFERENCES public.repair_offers(id) ON DELETE RESTRICT,
  mechanic_profile_id UUID NOT NULL REFERENCES public.provider_profiles(id) ON DELETE RESTRICT,
  customer_profile_id UUID NOT NULL REFERENCES public.user_profiles(id) ON DELETE RESTRICT,
  status TEXT NOT NULL DEFAULT 'mechanic_assigned'
    CHECK (status IN (
      'mechanic_assigned', 'in_route', 'inspection_started',
      'diagnosis_confirmed', 'parts_required', 'waiting_parts',
      'repair_in_progress', 'repair_completed', 'validation_pending',
      'customer_confirmed', 'closed', 'cancelled', 'disputed', 'refunded'
    )),
  final_price_cents BIGINT,
  final_labor_cents BIGINT,
  final_parts_cents BIGINT,
  warranty_terms TEXT,
  warranty_expires_at TIMESTAMPTZ,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  closed_at TIMESTAMPTZ,
  before_photos_hash TEXT,
  after_photos_hash TEXT,
  report_hash TEXT,
  invoice_hash TEXT,
  customer_signature_hash TEXT,
  mechanic_signature_hash TEXT,
  final_dtc_scan_hash TEXT,
  version INTEGER NOT NULL DEFAULT 1,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (repair_request_id)
);

CREATE INDEX IF NOT EXISTS idx_repair_work_orders_mechanic ON public.repair_work_orders(mechanic_profile_id, status);

CREATE INDEX IF NOT EXISTS idx_repair_work_orders_customer ON public.repair_work_orders(customer_profile_id, status);

CREATE INDEX IF NOT EXISTS idx_repair_work_orders_status ON public.repair_work_orders(status, created_at DESC);

CREATE TABLE IF NOT EXISTS public.repair_status_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  work_order_id UUID NOT NULL REFERENCES public.repair_work_orders(id) ON DELETE CASCADE,
  from_status TEXT NOT NULL,
  to_status TEXT NOT NULL,
  actor_id UUID,
  actor_role TEXT,
  reason TEXT,
  evidence JSONB DEFAULT '{}'::jsonb,
  idempotency_key TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_repair_status_events_order ON public.repair_status_events(work_order_id, created_at);

CREATE TABLE IF NOT EXISTS public.repair_evidence (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  work_order_id UUID NOT NULL REFERENCES public.repair_work_orders(id) ON DELETE CASCADE,
  evidence_type TEXT NOT NULL
    CHECK (evidence_type IN ('photo_before', 'photo_after', 'video', 'document', 'signature', 'dtc_scan', 'report', 'invoice', 'voice_note')),
  file_url TEXT,
  file_hash TEXT NOT NULL,
  caption TEXT,
  actor_id UUID,
  actor_role TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_repair_evidence_order ON public.repair_evidence(work_order_id, evidence_type);

CREATE TABLE IF NOT EXISTS public.repair_warranties (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  work_order_id UUID NOT NULL REFERENCES public.repair_work_orders(id) ON DELETE CASCADE,
  warranty_type TEXT NOT NULL DEFAULT 'labor'
    CHECK (warranty_type IN ('labor', 'parts', 'full')),
  terms TEXT NOT NULL,
  terms_hash TEXT NOT NULL,
  starts_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  claim_status TEXT NOT NULL DEFAULT 'active'
    CHECK (claim_status IN ('active', 'claimed', 'expired', 'voided')),
  claim_notes TEXT,
  claimed_at TIMESTAMPTZ,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_repair_warranties_order ON public.repair_warranties(work_order_id);

CREATE INDEX IF NOT EXISTS idx_repair_warranties_expiry ON public.repair_warranties(expires_at) WHERE claim_status = 'active';

CREATE TABLE IF NOT EXISTS public.repair_commissions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  work_order_id UUID NOT NULL REFERENCES public.repair_work_orders(id) ON DELETE RESTRICT,
  provider_profile_id UUID NOT NULL REFERENCES public.provider_profiles(id) ON DELETE RESTRICT,
  transaction_kind TEXT NOT NULL,
  gross_amount_cents BIGINT NOT NULL CHECK (gross_amount_cents >= 0),
  commission_rate_bps INTEGER NOT NULL CHECK (commission_rate_bps BETWEEN 0 AND 10000),
  commission_amount_cents BIGINT NOT NULL CHECK (commission_amount_cents >= 0),
  net_provider_cents BIGINT NOT NULL CHECK (net_provider_cents >= 0),
  currency TEXT NOT NULL DEFAULT 'USD',
  rule_key TEXT,
  status TEXT NOT NULL DEFAULT 'calculated'
    CHECK (status IN ('calculated', 'held', 'released', 'paid_out', 'refunded', 'disputed')),
  ledger_entry_id TEXT,
  idempotency_key TEXT NOT NULL UNIQUE,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (work_order_id)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. TOW NETWORK
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.tow_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_profile_id UUID NOT NULL REFERENCES public.user_profiles(id) ON DELETE CASCADE,
  vehicle_id UUID,
  breakdown_type TEXT NOT NULL DEFAULT 'mechanical'
    CHECK (breakdown_type IN ('mechanical', 'electrical', 'flat_tire', 'accident', 'fuel', 'lockout', 'other')),
  pickup_location_text TEXT,
  pickup_lat DOUBLE PRECISION,
  pickup_lng DOUBLE PRECISION,
  destination_text TEXT,
  destination_lat DOUBLE PRECISION,
  destination_lng DOUBLE PRECISION,
  vehicle_condition TEXT DEFAULT 'unknown'
    CHECK (vehicle_condition IN ('running', 'not_running', 'damaged', 'unknown')),
  priority TEXT NOT NULL DEFAULT 'normal'
    CHECK (priority IN ('normal', 'urgent', 'emergency')),
  contact_method TEXT DEFAULT 'in_app',
  proposed_price_cents BIGINT,
  payment_method TEXT DEFAULT 'in_app',
  diagnostic_context JSONB DEFAULT '{}'::jsonb,
  photos JSONB DEFAULT '[]'::jsonb,
  assigned_provider_id UUID REFERENCES public.provider_profiles(id) ON DELETE SET NULL,
  status TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft', 'published', 'driver_assigned', 'in_route', 'arrived', 'towing', 'delivered', 'closed', 'cancelled', 'disputed')),
  version INTEGER NOT NULL DEFAULT 1,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tow_requests_customer ON public.tow_requests(customer_profile_id, status);

CREATE INDEX IF NOT EXISTS idx_tow_requests_status ON public.tow_requests(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_tow_requests_provider ON public.tow_requests(assigned_provider_id) WHERE assigned_provider_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. RIDES (AUXILIARY TRANSPORT)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.ride_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_profile_id UUID NOT NULL REFERENCES public.user_profiles(id) ON DELETE CASCADE,
  linked_tow_request_id UUID REFERENCES public.tow_requests(id) ON DELETE SET NULL,
  linked_repair_request_id UUID REFERENCES public.repair_requests(id) ON DELETE SET NULL,
  pickup_location_text TEXT,
  pickup_lat DOUBLE PRECISION,
  pickup_lng DOUBLE PRECISION,
  destination_text TEXT,
  destination_lat DOUBLE PRECISION,
  destination_lng DOUBLE PRECISION,
  passenger_count INTEGER NOT NULL DEFAULT 1 CHECK (passenger_count > 0),
  assigned_driver_id UUID REFERENCES public.provider_profiles(id) ON DELETE SET NULL,
  proposed_price_cents BIGINT,
  final_price_cents BIGINT,
  status TEXT NOT NULL DEFAULT 'requested'
    CHECK (status IN ('requested', 'searching_driver', 'offer_received', 'driver_assigned', 'driver_arrived', 'in_progress', 'completed', 'cancelled', 'disputed')),
  version INTEGER NOT NULL DEFAULT 1,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ride_requests_customer ON public.ride_requests(customer_profile_id, status);

CREATE INDEX IF NOT EXISTS idx_ride_requests_status ON public.ride_requests(status, created_at DESC);

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. VEHICLE TIMELINE
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.vehicle_timeline_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  vehicle_id UUID NOT NULL,
  event_type TEXT NOT NULL
    CHECK (event_type IN (
      'scan_completed', 'dtc_detected', 'dtc_cleared', 'maintenance_done',
      'part_replaced', 'repair_completed', 'inspection_done', 'pdf_report_created',
      'mode06_failure', 'mode06_recovered', 'battery_alert', 'overheat_alert',
      'fuel_efficiency_drop', 'seller_disclosure_created', 'ownership_transfer',
      'warranty_registered', 'recall_notice', 'tow_service', 'mileage_update'
    )),
  title TEXT NOT NULL,
  description TEXT,
  actor_id UUID,
  actor_role TEXT,
  related_entity_type TEXT,
  related_entity_id UUID,
  evidence_hash TEXT,
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  is_private BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vehicle_timeline_vehicle ON public.vehicle_timeline_events(vehicle_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_vehicle_timeline_type ON public.vehicle_timeline_events(vehicle_id, event_type);

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. VERIFIED COMPANIES (B2B)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.company_profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  company_name TEXT NOT NULL,
  legal_name TEXT,
  tax_id TEXT,
  category TEXT NOT NULL
    CHECK (category IN (
      'oem', 'manufacturer', 'dealer', 'workshop', 'parts_store',
      'towing', 'fleet', 'insurance', 'tool_vendor', 'creator',
      'university', 'government'
    )),
  description TEXT,
  website TEXT,
  email TEXT,
  phone TEXT,
  address TEXT,
  country TEXT,
  city TEXT,
  logo_url TEXT,
  social_links JSONB DEFAULT '{}'::jsonb,
  status TEXT NOT NULL DEFAULT 'unverified'
    CHECK (status IN ('unverified', 'pending', 'needs_more_info', 'verified', 'rejected', 'suspended', 'expired', 'revoked')),
  verified_at TIMESTAMPTZ,
  verification_expires_at TIMESTAMPTZ,
  version INTEGER NOT NULL DEFAULT 1,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_company_profiles_owner ON public.company_profiles(owner_user_id);

CREATE INDEX IF NOT EXISTS idx_company_profiles_category_status ON public.company_profiles(category, status);

CREATE TABLE IF NOT EXISTS public.company_verification_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_profile_id UUID NOT NULL REFERENCES public.company_profiles(id) ON DELETE CASCADE,
  requested_category TEXT NOT NULL,
  documents JSONB NOT NULL DEFAULT '[]'::jsonb,
  corporate_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
  address_verified BOOLEAN NOT NULL DEFAULT FALSE,
  website_verified BOOLEAN NOT NULL DEFAULT FALSE,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'needs_more_info', 'approved', 'rejected')),
  reviewer_id UUID,
  reviewer_notes TEXT,
  submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  reviewed_at TIMESTAMPTZ,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_company_verif_company ON public.company_verification_requests(company_profile_id, status);

CREATE TABLE IF NOT EXISTS public.verified_badges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_profile_id UUID NOT NULL REFERENCES public.company_profiles(id) ON DELETE CASCADE,
  badge_type TEXT NOT NULL,
  badge_label TEXT NOT NULL,
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  granted_by UUID,
  revoked_at TIMESTAMPTZ,
  revoked_reason TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (company_profile_id, badge_type)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. COMMUNITY CASES (ANONYMIZED REPAIR KNOWLEDGE)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.community_cases (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_work_order_id UUID REFERENCES public.repair_work_orders(id) ON DELETE SET NULL,
  -- Anonymized vehicle info (no VIN, plate, GPS, phone)
  vehicle_make TEXT,
  vehicle_model TEXT,
  vehicle_year INTEGER,
  vehicle_engine TEXT,
  dtc_codes TEXT[] DEFAULT '{}',
  symptoms TEXT,
  freeze_frame_summary JSONB DEFAULT '{}'::jsonb,
  live_data_summary JSONB DEFAULT '{}'::jsonb,
  tests_performed TEXT,
  root_cause TEXT,
  repair_applied TEXT,
  parts_used JSONB DEFAULT '[]'::jsonb,
  approximate_cost_cents BIGINT,
  repair_time_minutes INTEGER,
  outcome TEXT CHECK (outcome IN ('success', 'partial', 'failed', 'recurrence')),
  recurrence BOOLEAN NOT NULL DEFAULT FALSE,
  confidence DOUBLE PRECISION DEFAULT 0.0,
  upvotes INTEGER NOT NULL DEFAULT 0,
  downvotes INTEGER NOT NULL DEFAULT 0,
  quality_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  is_expert_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
  expert_reviewer_id UUID,
  status TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft', 'pending_review', 'published', 'featured', 'rejected', 'archived')),
  submitted_by UUID,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_community_cases_dtcs ON public.community_cases USING gin(dtc_codes);

CREATE INDEX IF NOT EXISTS idx_community_cases_vehicle ON public.community_cases(vehicle_make, vehicle_model, vehicle_year);

CREATE INDEX IF NOT EXISTS idx_community_cases_status ON public.community_cases(status, quality_score DESC);

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. AI DIAGNOSTIC RESULTS
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.ai_diagnostic_results (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_profile_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
  vehicle_id UUID,
  session_id TEXT,
  dtc_codes TEXT[] DEFAULT '{}',
  freeze_frame JSONB DEFAULT '{}'::jsonb,
  live_data JSONB DEFAULT '{}'::jsonb,
  symptoms TEXT,
  vehicle_context JSONB DEFAULT '{}'::jsonb,
  -- AI output
  probable_diagnosis TEXT,
  evidence_summary TEXT,
  contradictory_data TEXT,
  recommended_tests TEXT,
  risk_level TEXT CHECK (risk_level IN ('low', 'medium', 'high', 'critical')),
  next_step TEXT,
  confidence DOUBLE PRECISION DEFAULT 0.0,
  do_not_do TEXT,
  possible_parts JSONB DEFAULT '[]'::jsonb,
  estimated_cost_range JSONB DEFAULT '{}'::jsonb,
  sources_cited JSONB DEFAULT '[]'::jsonb,
  -- Feedback loop
  feedback_outcome TEXT CHECK (feedback_outcome IN ('correct', 'partially_correct', 'incorrect', 'pending')),
  feedback_notes TEXT,
  feedback_given_at TIMESTAMPTZ,
  model_version TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_diagnostics_user ON public.ai_diagnostic_results(user_profile_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_diagnostics_dtcs ON public.ai_diagnostic_results USING gin(dtc_codes);

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. AUDIT LOGS (APPEND-ONLY, IMMUTABLE)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.audit_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_id UUID,
  actor_role TEXT,
  action TEXT NOT NULL,
  resource_type TEXT NOT NULL,
  resource_id TEXT,
  old_state JSONB,
  new_state JSONB,
  ip_address TEXT,
  user_agent TEXT,
  context JSONB NOT NULL DEFAULT '{}'::jsonb,
  idempotency_key TEXT UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON public.audit_logs(actor_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_resource ON public.audit_logs(resource_type, resource_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON public.audit_logs(action, created_at DESC);

-- ─────────────────────────────────────────────────────────────────────────────
-- 11. FEATURE FLAGS
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.feature_flags (
  flag_key TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  description TEXT,
  is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  rollout_percentage INTEGER NOT NULL DEFAULT 0 CHECK (rollout_percentage BETWEEN 0 AND 100),
  allowed_roles TEXT[] DEFAULT '{}',
  allowed_plans TEXT[] DEFAULT '{}',
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 12. NOTIFICATIONS
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_profile_id UUID NOT NULL REFERENCES public.user_profiles(id) ON DELETE CASCADE,
  notification_type TEXT NOT NULL,
  title TEXT NOT NULL,
  body TEXT,
  action_url TEXT,
  related_entity_type TEXT,
  related_entity_id UUID,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  read_at TIMESTAMPTZ,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON public.notifications(user_profile_id, is_read, created_at DESC);

-- ═══════════════════════════════════════════════════════════════════════════════
-- ROW LEVEL SECURITY POLICIES
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.provider_profiles ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.provider_verifications ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.provider_reputation_scores ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.repair_requests ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.repair_offers ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.repair_work_orders ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.repair_status_events ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.repair_evidence ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.repair_warranties ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.repair_commissions ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.tow_requests ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.ride_requests ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.vehicle_timeline_events ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.company_profiles ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.company_verification_requests ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.verified_badges ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.community_cases ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.ai_diagnostic_results ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.feature_flags ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;

-- ── user_profiles: Owner reads own, inserts own ──
CREATE POLICY user_profiles_select_own ON public.user_profiles
  FOR SELECT TO authenticated USING (auth.uid() = auth_user_id);

CREATE POLICY user_profiles_insert_own ON public.user_profiles
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = auth_user_id);

CREATE POLICY user_profiles_update_own ON public.user_profiles
  FOR UPDATE TO authenticated USING (auth.uid() = auth_user_id) WITH CHECK (auth.uid() = auth_user_id);

-- ── user_roles: Owner reads own ──
CREATE POLICY user_roles_select_own ON public.user_roles
  FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = user_profile_id AND up.auth_user_id = auth.uid()));

-- Roles are granted by service_role only (no client insert/update)
CREATE POLICY user_roles_no_client_write ON public.user_roles
  FOR ALL TO anon, authenticated USING (FALSE) WITH CHECK (FALSE);

-- ── provider_profiles: Public read active/verified, owner writes own ──
CREATE POLICY provider_profiles_public_read ON public.provider_profiles
  FOR SELECT TO anon, authenticated
  USING (is_active = TRUE AND status = 'active');

CREATE POLICY provider_profiles_insert_own ON public.provider_profiles
  FOR INSERT TO authenticated
  WITH CHECK (EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = user_profile_id AND up.auth_user_id = auth.uid()));

CREATE POLICY provider_profiles_update_own ON public.provider_profiles
  FOR UPDATE TO authenticated
  USING (EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = user_profile_id AND up.auth_user_id = auth.uid()))
  WITH CHECK (EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = user_profile_id AND up.auth_user_id = auth.uid()));

-- ── provider_verifications: Owner reads own, no client write ──
CREATE POLICY provider_verif_select_own ON public.provider_verifications
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.provider_profiles pp
    JOIN public.user_profiles up ON up.id = pp.user_profile_id
    WHERE pp.id = provider_profile_id AND up.auth_user_id = auth.uid()
  ));

CREATE POLICY provider_verif_no_client_write ON public.provider_verifications
  FOR ALL TO anon, authenticated USING (FALSE) WITH CHECK (FALSE);

-- ── provider_reputation_scores: Public read ──
CREATE POLICY provider_rep_public_read ON public.provider_reputation_scores
  FOR SELECT TO anon, authenticated USING (TRUE);

CREATE POLICY provider_rep_no_client_write ON public.provider_reputation_scores
  FOR ALL TO anon, authenticated USING (FALSE) WITH CHECK (FALSE);

-- ── repair_requests: Customer owns, active providers can see published ──
CREATE POLICY repair_requests_customer_rw ON public.repair_requests
  FOR ALL TO authenticated
  USING (EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = customer_profile_id AND up.auth_user_id = auth.uid()))
  WITH CHECK (EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = customer_profile_id AND up.auth_user_id = auth.uid()));

CREATE POLICY repair_requests_provider_read ON public.repair_requests
  FOR SELECT TO authenticated
  USING (status IN ('published', 'waiting_offers', 'offer_received'));

-- ── repair_offers: Mechanic writes own, customer of request reads ──
CREATE POLICY repair_offers_mechanic_rw ON public.repair_offers
  FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.provider_profiles pp
    JOIN public.user_profiles up ON up.id = pp.user_profile_id
    WHERE pp.id = mechanic_profile_id AND up.auth_user_id = auth.uid()
  ))
  WITH CHECK (EXISTS (
    SELECT 1 FROM public.provider_profiles pp
    JOIN public.user_profiles up ON up.id = pp.user_profile_id
    WHERE pp.id = mechanic_profile_id AND up.auth_user_id = auth.uid()
  ));

CREATE POLICY repair_offers_customer_read ON public.repair_offers
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.repair_requests rr
    JOIN public.user_profiles up ON up.id = rr.customer_profile_id
    WHERE rr.id = repair_request_id AND up.auth_user_id = auth.uid()
  ));

-- ── repair_work_orders: Participants only ──
CREATE POLICY repair_wo_participants ON public.repair_work_orders
  FOR SELECT TO authenticated
  USING (
    EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = customer_profile_id AND up.auth_user_id = auth.uid())
    OR EXISTS (
      SELECT 1 FROM public.provider_profiles pp
      JOIN public.user_profiles up ON up.id = pp.user_profile_id
      WHERE pp.id = mechanic_profile_id AND up.auth_user_id = auth.uid()
    )
  );

CREATE POLICY repair_wo_no_client_write ON public.repair_work_orders
  FOR ALL TO anon, authenticated USING (FALSE) WITH CHECK (FALSE);

-- ── repair_status_events: Participants read, service_role writes ──
CREATE POLICY repair_status_participants ON public.repair_status_events
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.repair_work_orders wo
    LEFT JOIN public.user_profiles up_c ON up_c.id = wo.customer_profile_id
    LEFT JOIN public.provider_profiles pp ON pp.id = wo.mechanic_profile_id
    LEFT JOIN public.user_profiles up_m ON up_m.id = pp.user_profile_id
    WHERE wo.id = work_order_id AND (up_c.auth_user_id = auth.uid() OR up_m.auth_user_id = auth.uid())
  ));

CREATE POLICY repair_status_no_client_write ON public.repair_status_events
  FOR ALL TO anon, authenticated USING (FALSE) WITH CHECK (FALSE);

-- ── repair_evidence: Participants read, participants insert ──
CREATE POLICY repair_evidence_participants ON public.repair_evidence
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.repair_work_orders wo
    LEFT JOIN public.user_profiles up_c ON up_c.id = wo.customer_profile_id
    LEFT JOIN public.provider_profiles pp ON pp.id = wo.mechanic_profile_id
    LEFT JOIN public.user_profiles up_m ON up_m.id = pp.user_profile_id
    WHERE wo.id = work_order_id AND (up_c.auth_user_id = auth.uid() OR up_m.auth_user_id = auth.uid())
  ));

CREATE POLICY repair_evidence_insert_participants ON public.repair_evidence
  FOR INSERT TO authenticated
  WITH CHECK (EXISTS (
    SELECT 1 FROM public.repair_work_orders wo
    LEFT JOIN public.user_profiles up_c ON up_c.id = wo.customer_profile_id
    LEFT JOIN public.provider_profiles pp ON pp.id = wo.mechanic_profile_id
    LEFT JOIN public.user_profiles up_m ON up_m.id = pp.user_profile_id
    WHERE wo.id = work_order_id AND (up_c.auth_user_id = auth.uid() OR up_m.auth_user_id = auth.uid())
  ));

-- ── repair_warranties: Participants read ──
CREATE POLICY repair_warranties_read ON public.repair_warranties
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.repair_work_orders wo
    LEFT JOIN public.user_profiles up_c ON up_c.id = wo.customer_profile_id
    WHERE wo.id = work_order_id AND up_c.auth_user_id = auth.uid()
  ));

CREATE POLICY repair_warranties_no_write ON public.repair_warranties
  FOR ALL TO anon, authenticated USING (FALSE) WITH CHECK (FALSE);

-- ── repair_commissions: No client access ──
CREATE POLICY repair_commissions_no_access ON public.repair_commissions
  FOR ALL TO anon, authenticated USING (FALSE) WITH CHECK (FALSE);

-- ── tow_requests: Customer owns ──
CREATE POLICY tow_requests_customer_rw ON public.tow_requests
  FOR ALL TO authenticated
  USING (EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = customer_profile_id AND up.auth_user_id = auth.uid()))
  WITH CHECK (EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = customer_profile_id AND up.auth_user_id = auth.uid()));

CREATE POLICY tow_requests_provider_read ON public.tow_requests
  FOR SELECT TO authenticated
  USING (status IN ('published', 'driver_assigned'));

-- ── ride_requests: Customer owns ──
CREATE POLICY ride_requests_customer_rw ON public.ride_requests
  FOR ALL TO authenticated
  USING (EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = customer_profile_id AND up.auth_user_id = auth.uid()))
  WITH CHECK (EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = customer_profile_id AND up.auth_user_id = auth.uid()));

CREATE POLICY ride_requests_driver_read ON public.ride_requests
  FOR SELECT TO authenticated
  USING (status IN ('requested', 'searching_driver'));

-- ── vehicle_timeline_events: Vehicle owner reads ──
CREATE POLICY vehicle_timeline_public_read ON public.vehicle_timeline_events
  FOR SELECT TO authenticated USING (is_private = FALSE);

CREATE POLICY vehicle_timeline_no_client_write ON public.vehicle_timeline_events
  FOR ALL TO anon, authenticated USING (FALSE) WITH CHECK (FALSE);

-- ── company_profiles: Public read verified, owner writes ──
CREATE POLICY company_profiles_public_read ON public.company_profiles
  FOR SELECT TO anon, authenticated
  USING (status = 'verified');

CREATE POLICY company_profiles_owner_rw ON public.company_profiles
  FOR ALL TO authenticated
  USING (owner_user_id = auth.uid())
  WITH CHECK (owner_user_id = auth.uid());

-- ── company_verification_requests: Owner reads ──
CREATE POLICY company_verif_owner_read ON public.company_verification_requests
  FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM public.company_profiles cp WHERE cp.id = company_profile_id AND cp.owner_user_id = auth.uid()));

CREATE POLICY company_verif_owner_insert ON public.company_verification_requests
  FOR INSERT TO authenticated
  WITH CHECK (EXISTS (SELECT 1 FROM public.company_profiles cp WHERE cp.id = company_profile_id AND cp.owner_user_id = auth.uid()));

-- ── verified_badges: Public read active ──
CREATE POLICY verified_badges_public_read ON public.verified_badges
  FOR SELECT TO anon, authenticated USING (is_active = TRUE);

CREATE POLICY verified_badges_no_client_write ON public.verified_badges
  FOR ALL TO anon, authenticated USING (FALSE) WITH CHECK (FALSE);

-- ── community_cases: Public read published ──
CREATE POLICY community_cases_public_read ON public.community_cases
  FOR SELECT TO anon, authenticated
  USING (status IN ('published', 'featured'));

CREATE POLICY community_cases_insert ON public.community_cases
  FOR INSERT TO authenticated WITH CHECK (TRUE);

-- ── ai_diagnostic_results: Owner reads own ──
CREATE POLICY ai_diagnostics_owner_read ON public.ai_diagnostic_results
  FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = user_profile_id AND up.auth_user_id = auth.uid()));

CREATE POLICY ai_diagnostics_insert ON public.ai_diagnostic_results
  FOR INSERT TO authenticated WITH CHECK (TRUE);

-- ── audit_logs: Service role only ──
CREATE POLICY audit_logs_insert_authenticated ON public.audit_logs
  FOR INSERT TO authenticated WITH CHECK (TRUE);

CREATE POLICY audit_logs_no_read ON public.audit_logs
  FOR SELECT TO anon, authenticated USING (FALSE);

-- ── feature_flags: Public read ──
CREATE POLICY feature_flags_public_read ON public.feature_flags
  FOR SELECT TO anon, authenticated USING (TRUE);

CREATE POLICY feature_flags_no_client_write ON public.feature_flags
  FOR ALL TO anon, authenticated USING (FALSE) WITH CHECK (FALSE);

-- ── notifications: Owner reads/updates own ──
CREATE POLICY notifications_owner_rw ON public.notifications
  FOR ALL TO authenticated
  USING (EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = user_profile_id AND up.auth_user_id = auth.uid()))
  WITH CHECK (EXISTS (SELECT 1 FROM public.user_profiles up WHERE up.id = user_profile_id AND up.auth_user_id = auth.uid()));

-- ═══════════════════════════════════════════════════════════════════════════════
-- SEED: Initial Feature Flags
-- ═══════════════════════════════════════════════════════════════════════════════

INSERT INTO public.feature_flags (flag_key, display_name, description, is_enabled, rollout_percentage)
VALUES
  ('repair_network_v1', 'Repair Network V1', 'Enable full repair request/offer/work order flow', FALSE, 0),
  ('tow_network_v1', 'Tow Network V1', 'Enable tow service requests', FALSE, 0),
  ('rides_v1', 'Rides V1', 'Enable auxiliary ride requests', FALSE, 0),
  ('community_cases_v1', 'Community Cases V1', 'Enable anonymized repair case publishing', FALSE, 0),
  ('ai_diagnostics_v1', 'AI Diagnostics V1', 'Enable AI-powered diagnostic analysis', TRUE, 100),
  ('verified_companies_v1', 'Verified Companies V1', 'Enable company verification flow', FALSE, 0),
  ('vehicle_timeline_v1', 'Vehicle Timeline V1', 'Enable vehicle timeline events', TRUE, 100),
  ('provider_reputation_v1', 'Provider Reputation V1', 'Enable technical reputation scoring', FALSE, 0)
ON CONFLICT (flag_key) DO UPDATE
SET display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    updated_at = now();

-- ═══════════════════════════════════════════════════════════════════════════════
-- TRIGGER: Auto-update updated_at on mutable tables
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION public.trigger_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;
DO $$
DECLARE
  tbl TEXT;
BEGIN
  FOREACH tbl IN ARRAY ARRAY[
    'user_profiles', 'user_roles', 'provider_profiles', 'provider_verifications',
    'provider_reputation_scores', 'repair_requests', 'repair_offers',
    'repair_work_orders', 'repair_warranties', 'repair_commissions',
    'tow_requests', 'ride_requests', 'company_profiles',
    'company_verification_requests', 'verified_badges', 'community_cases',
    'feature_flags', 'notifications'
  ]
  LOOP
    EXECUTE format(
      'DROP TRIGGER IF EXISTS trg_set_updated_at_%I ON public.%I;
       CREATE TRIGGER trg_set_updated_at_%I
       BEFORE UPDATE ON public.%I
       FOR EACH ROW
       EXECUTE FUNCTION public.trigger_set_updated_at();',
      tbl, tbl, tbl, tbl
    );
  END LOOP;
END;
$$;
