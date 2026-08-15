-- Vanguard role, entitlement, payment rail, and commission policy foundation.
-- Catalog tables are readable by clients; assignments and audit rows stay owner/service scoped.

CREATE TABLE IF NOT EXISTS public.vanguard_plan_catalog (
  plan_key TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  audience TEXT NOT NULL,
  billing_source TEXT NOT NULL CHECK (billing_source IN ('google_play', 'external', 'invoice', 'manual')),
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.vanguard_feature_catalog (
  feature_key TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  feature_group TEXT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.vanguard_plan_entitlements (
  plan_key TEXT NOT NULL REFERENCES public.vanguard_plan_catalog(plan_key) ON DELETE CASCADE,
  feature_key TEXT NOT NULL REFERENCES public.vanguard_feature_catalog(feature_key) ON DELETE CASCADE,
  usage_limit INTEGER,
  requires_provider_verification BOOLEAN NOT NULL DEFAULT FALSE,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (plan_key, feature_key)
);

CREATE TABLE IF NOT EXISTS public.provider_plan_assignments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  local_provider_user_id TEXT,
  provider_profile_id TEXT,
  provider_type TEXT NOT NULL,
  plan_key TEXT NOT NULL REFERENCES public.vanguard_plan_catalog(plan_key),
  status TEXT NOT NULL CHECK (status IN ('active', 'trialing', 'past_due', 'paused', 'cancelled', 'revoked')),
  source TEXT NOT NULL CHECK (source IN ('google_play', 'stripe', 'cash', 'invoice', 'admin', 'migration')),
  starts_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT provider_plan_identity_check CHECK (user_id IS NOT NULL OR local_provider_user_id IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS public.vanguard_payment_policy_rules (
  transaction_kind TEXT PRIMARY KEY,
  payment_rail TEXT NOT NULL,
  requires_google_play BOOLEAN NOT NULL DEFAULT FALSE,
  external_settlement_allowed BOOLEAN NOT NULL DEFAULT FALSE,
  requires_escrow BOOLEAN NOT NULL DEFAULT FALSE,
  commission_eligible BOOLEAN NOT NULL DEFAULT FALSE,
  policy_code TEXT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.vanguard_commission_rules (
  rule_key TEXT PRIMARY KEY,
  transaction_kind TEXT NOT NULL REFERENCES public.vanguard_payment_policy_rules(transaction_kind) ON DELETE CASCADE,
  plan_key TEXT NULL REFERENCES public.vanguard_plan_catalog(plan_key) ON DELETE CASCADE,
  provider_type TEXT,
  rate_bps INTEGER NOT NULL CHECK (rate_bps BETWEEN 0 AND 10000),
  priority INTEGER NOT NULL DEFAULT 100,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.vanguard_access_policy_audit_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id UUID NULL REFERENCES auth.users(id) ON DELETE SET NULL,
  local_actor_id TEXT,
  feature_key TEXT,
  transaction_kind TEXT,
  decision TEXT NOT NULL CHECK (decision IN ('allowed', 'denied')),
  reason TEXT NOT NULL,
  policy_code TEXT,
  context JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT access_audit_actor_check CHECK (actor_user_id IS NOT NULL OR local_actor_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_provider_plan_assignments_user_status
ON public.provider_plan_assignments(user_id, status);

CREATE INDEX IF NOT EXISTS idx_provider_plan_assignments_local_status
ON public.provider_plan_assignments(local_provider_user_id, status);

CREATE INDEX IF NOT EXISTS idx_provider_plan_assignments_provider
ON public.provider_plan_assignments(provider_type, status);

CREATE INDEX IF NOT EXISTS idx_vanguard_commission_rules_kind_priority
ON public.vanguard_commission_rules(transaction_kind, priority, is_active);

CREATE INDEX IF NOT EXISTS idx_vanguard_access_audit_actor_created
ON public.vanguard_access_policy_audit_logs(actor_user_id, created_at DESC);

INSERT INTO public.vanguard_plan_catalog (plan_key, display_name, audience, billing_source)
VALUES
  ('USER_FREE', 'Usuario Free', 'driver', 'manual'),
  ('VANGUARD_PLUS', 'Vanguard Plus', 'driver', 'google_play'),
  ('VANGUARD_ELITE', 'Vanguard Elite', 'driver', 'google_play'),
  ('MECHANIC_SOLO', 'Mecanico Solo', 'provider', 'external'),
  ('MECHANIC_PRO', 'Mecanico Pro', 'provider', 'external'),
  ('WORKSHOP_ELITE', 'Workshop Elite', 'provider', 'external'),
  ('PARTS_STORE_PRO', 'Repuestera Pro', 'provider', 'external'),
  ('TOW_PRO', 'Grua Pro', 'provider', 'external'),
  ('RIDE_PRO', 'Ride Pro', 'provider', 'external'),
  ('FLEET', 'Fleet', 'fleet', 'invoice'),
  ('ENTERPRISE_API', 'Enterprise API', 'enterprise', 'invoice'),
  ('VERIFIED_COMPANY', 'Verified Company', 'business', 'invoice')
ON CONFLICT (plan_key) DO UPDATE
SET display_name = EXCLUDED.display_name,
    audience = EXCLUDED.audience,
    billing_source = EXCLUDED.billing_source,
    updated_at = now();

INSERT INTO public.vanguard_feature_catalog (feature_key, display_name, feature_group)
VALUES
  ('BASIC_SCAN', 'Basic OBD scan', 'diagnostics'),
  ('AI_DTC_DIAGNOSIS', 'AI DTC diagnosis', 'diagnostics'),
  ('FULL_REPAIR_GUIDES', 'Full repair guides', 'diagnostics'),
  ('PROFESSIONAL_PDF_REPORTS', 'Professional PDF reports', 'evidence'),
  ('MODE_06', 'Mode 06 diagnostics', 'diagnostics'),
  ('OEM_PIDS', 'OEM PID access', 'diagnostics'),
  ('CREATE_REPAIR_REQUEST', 'Create repair request', 'marketplace'),
  ('PLACE_MECHANIC_BID', 'Place mechanic bid', 'marketplace'),
  ('UNLIMITED_MECHANIC_BIDS', 'Unlimited mechanic bids', 'marketplace'),
  ('TAKE_MECHANIC_REQUEST', 'Take mechanic request', 'marketplace'),
  ('COMPLETE_REPAIR_WITH_EVIDENCE', 'Complete repair with evidence', 'evidence'),
  ('CREATE_PART_REQUEST', 'Create part request', 'parts'),
  ('PLACE_PART_OFFER', 'Place part offer', 'parts'),
  ('ACCEPT_PART_OFFER', 'Accept part offer', 'parts'),
  ('TOW_REQUESTS', 'Create tow request', 'mobility'),
  ('TOW_PROVIDER_OFFERS', 'Tow provider offers', 'mobility'),
  ('RIDE_REQUESTS', 'Create ride request', 'mobility'),
  ('RIDE_PROVIDER_OFFERS', 'Ride provider offers', 'mobility'),
  ('GAUGE_MARKETPLACE_BUY', 'Buy gauge', 'digital_marketplace'),
  ('GAUGE_MARKETPLACE_SELL', 'Sell gauge', 'digital_marketplace'),
  ('VERIFIED_CAMPAIGNS', 'Verified company campaigns', 'business'),
  ('ENTERPRISE_ANALYTICS', 'Enterprise analytics', 'enterprise'),
  ('PROFESSIONAL_COMMANDS', 'Professional OBD commands', 'diagnostics'),
  ('ADMIN_CONSOLE', 'Admin console', 'operations'),
  ('TRUST_SAFETY_QUEUE', 'Trust and safety queue', 'operations')
ON CONFLICT (feature_key) DO UPDATE
SET display_name = EXCLUDED.display_name,
    feature_group = EXCLUDED.feature_group,
    updated_at = now();

INSERT INTO public.vanguard_plan_entitlements (plan_key, feature_key, usage_limit, requires_provider_verification)
VALUES
  ('USER_FREE', 'BASIC_SCAN', NULL, FALSE),
  ('USER_FREE', 'AI_DTC_DIAGNOSIS', NULL, FALSE),
  ('USER_FREE', 'CREATE_REPAIR_REQUEST', NULL, FALSE),
  ('USER_FREE', 'CREATE_PART_REQUEST', NULL, FALSE),
  ('VANGUARD_PLUS', 'FULL_REPAIR_GUIDES', NULL, FALSE),
  ('VANGUARD_PLUS', 'GAUGE_MARKETPLACE_BUY', NULL, FALSE),
  ('VANGUARD_ELITE', 'FULL_REPAIR_GUIDES', NULL, FALSE),
  ('VANGUARD_ELITE', 'PROFESSIONAL_PDF_REPORTS', NULL, FALSE),
  ('VANGUARD_ELITE', 'MODE_06', NULL, FALSE),
  ('VANGUARD_ELITE', 'OEM_PIDS', NULL, FALSE),
  ('MECHANIC_SOLO', 'PLACE_MECHANIC_BID', 30, TRUE),
  ('MECHANIC_SOLO', 'TAKE_MECHANIC_REQUEST', 30, TRUE),
  ('MECHANIC_SOLO', 'COMPLETE_REPAIR_WITH_EVIDENCE', NULL, TRUE),
  ('MECHANIC_PRO', 'PLACE_MECHANIC_BID', NULL, TRUE),
  ('MECHANIC_PRO', 'UNLIMITED_MECHANIC_BIDS', NULL, TRUE),
  ('MECHANIC_PRO', 'COMPLETE_REPAIR_WITH_EVIDENCE', NULL, TRUE),
  ('WORKSHOP_ELITE', 'PLACE_MECHANIC_BID', NULL, TRUE),
  ('WORKSHOP_ELITE', 'UNLIMITED_MECHANIC_BIDS', NULL, TRUE),
  ('WORKSHOP_ELITE', 'PROFESSIONAL_COMMANDS', NULL, TRUE),
  ('PARTS_STORE_PRO', 'PLACE_PART_OFFER', NULL, TRUE),
  ('PARTS_STORE_PRO', 'ACCEPT_PART_OFFER', NULL, TRUE),
  ('TOW_PRO', 'TOW_PROVIDER_OFFERS', NULL, TRUE),
  ('RIDE_PRO', 'RIDE_PROVIDER_OFFERS', NULL, TRUE),
  ('FLEET', 'ENTERPRISE_ANALYTICS', NULL, FALSE),
  ('ENTERPRISE_API', 'OEM_PIDS', NULL, FALSE),
  ('ENTERPRISE_API', 'ENTERPRISE_ANALYTICS', NULL, FALSE),
  ('VERIFIED_COMPANY', 'VERIFIED_CAMPAIGNS', NULL, FALSE)
ON CONFLICT (plan_key, feature_key) DO UPDATE
SET usage_limit = EXCLUDED.usage_limit,
    requires_provider_verification = EXCLUDED.requires_provider_verification;

INSERT INTO public.vanguard_payment_policy_rules (
  transaction_kind,
  payment_rail,
  requires_google_play,
  external_settlement_allowed,
  requires_escrow,
  commission_eligible,
  policy_code
)
VALUES
  ('DIGITAL_SUBSCRIPTION', 'GOOGLE_PLAY_BILLING', TRUE, FALSE, FALSE, FALSE, 'digital_google_play'),
  ('DIGITAL_REPORT_PACK', 'GOOGLE_PLAY_BILLING', TRUE, FALSE, FALSE, FALSE, 'digital_google_play'),
  ('DIGITAL_GAUGE_PURCHASE', 'GOOGLE_PLAY_BILLING', TRUE, FALSE, FALSE, TRUE, 'digital_google_play'),
  ('REPAIR_SERVICE', 'EXTERNAL_PHYSICAL_SERVICE', FALSE, TRUE, TRUE, TRUE, 'physical_service_external_escrow'),
  ('PARTS_ORDER', 'EXTERNAL_PARTS_GOODS', FALSE, TRUE, TRUE, TRUE, 'physical_goods_external_escrow'),
  ('TOW_SERVICE', 'EXTERNAL_TOW_RIDE_SERVICE', FALSE, TRUE, TRUE, TRUE, 'mobility_service_external_escrow'),
  ('RIDE_SERVICE', 'EXTERNAL_TOW_RIDE_SERVICE', FALSE, TRUE, TRUE, TRUE, 'mobility_service_external_escrow'),
  ('VERIFIED_COMPANY_PLAN', 'INVOICE_OR_WIRE', FALSE, TRUE, FALSE, FALSE, 'business_contract_invoice'),
  ('ENTERPRISE_CONTRACT', 'INVOICE_OR_WIRE', FALSE, TRUE, FALSE, FALSE, 'business_contract_invoice'),
  ('GAUGE_CREATOR_PAYOUT', 'MANUAL_OFFLINE', FALSE, TRUE, FALSE, FALSE, 'manual_operations'),
  ('MANUAL_ADJUSTMENT', 'MANUAL_OFFLINE', FALSE, TRUE, FALSE, FALSE, 'manual_operations')
ON CONFLICT (transaction_kind) DO UPDATE
SET payment_rail = EXCLUDED.payment_rail,
    requires_google_play = EXCLUDED.requires_google_play,
    external_settlement_allowed = EXCLUDED.external_settlement_allowed,
    requires_escrow = EXCLUDED.requires_escrow,
    commission_eligible = EXCLUDED.commission_eligible,
    policy_code = EXCLUDED.policy_code,
    updated_at = now();

INSERT INTO public.vanguard_commission_rules (
  rule_key,
  transaction_kind,
  plan_key,
  provider_type,
  rate_bps,
  priority
)
VALUES
  ('repair_default', 'REPAIR_SERVICE', NULL, 'MECHANIC', 1000, 100),
  ('repair_mechanic_pro', 'REPAIR_SERVICE', 'MECHANIC_PRO', 'MECHANIC', 800, 50),
  ('repair_workshop_elite', 'REPAIR_SERVICE', 'WORKSHOP_ELITE', 'WORKSHOP', 600, 40),
  ('parts_default', 'PARTS_ORDER', NULL, 'PARTS_STORE', 800, 100),
  ('parts_store_pro', 'PARTS_ORDER', 'PARTS_STORE_PRO', 'PARTS_STORE', 600, 50),
  ('tow_default', 'TOW_SERVICE', NULL, 'TOW_TRUCK', 1000, 100),
  ('tow_pro', 'TOW_SERVICE', 'TOW_PRO', 'TOW_TRUCK', 800, 50),
  ('ride_default', 'RIDE_SERVICE', NULL, 'RIDE_DRIVER', 1000, 100),
  ('ride_pro', 'RIDE_SERVICE', 'RIDE_PRO', 'RIDE_DRIVER', 800, 50),
  ('digital_gauge_creator', 'DIGITAL_GAUGE_PURCHASE', NULL, 'CREATOR', 2500, 100)
ON CONFLICT (rule_key) DO UPDATE
SET transaction_kind = EXCLUDED.transaction_kind,
    plan_key = EXCLUDED.plan_key,
    provider_type = EXCLUDED.provider_type,
    rate_bps = EXCLUDED.rate_bps,
    priority = EXCLUDED.priority,
    updated_at = now();

ALTER TABLE public.vanguard_plan_catalog ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.vanguard_feature_catalog ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.vanguard_plan_entitlements ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.provider_plan_assignments ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.vanguard_payment_policy_rules ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.vanguard_commission_rules ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.vanguard_access_policy_audit_logs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS vanguard_plan_catalog_read ON public.vanguard_plan_catalog;

CREATE POLICY vanguard_plan_catalog_read
ON public.vanguard_plan_catalog
FOR SELECT
TO anon, authenticated
USING (is_active = TRUE);

DROP POLICY IF EXISTS vanguard_feature_catalog_read ON public.vanguard_feature_catalog;

CREATE POLICY vanguard_feature_catalog_read
ON public.vanguard_feature_catalog
FOR SELECT
TO anon, authenticated
USING (is_active = TRUE);

DROP POLICY IF EXISTS vanguard_plan_entitlements_read ON public.vanguard_plan_entitlements;

CREATE POLICY vanguard_plan_entitlements_read
ON public.vanguard_plan_entitlements
FOR SELECT
TO anon, authenticated
USING (TRUE);

DROP POLICY IF EXISTS vanguard_payment_rules_read ON public.vanguard_payment_policy_rules;

CREATE POLICY vanguard_payment_rules_read
ON public.vanguard_payment_policy_rules
FOR SELECT
TO anon, authenticated
USING (is_active = TRUE);

DROP POLICY IF EXISTS vanguard_commission_rules_read ON public.vanguard_commission_rules;

CREATE POLICY vanguard_commission_rules_read
ON public.vanguard_commission_rules
FOR SELECT
TO anon, authenticated
USING (is_active = TRUE);

DROP POLICY IF EXISTS provider_plan_assignments_owner_read ON public.provider_plan_assignments;

CREATE POLICY provider_plan_assignments_owner_read
ON public.provider_plan_assignments
FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS provider_plan_assignments_no_client_write ON public.provider_plan_assignments;

CREATE POLICY provider_plan_assignments_no_client_write
ON public.provider_plan_assignments
FOR ALL
TO anon, authenticated
USING (FALSE)
WITH CHECK (FALSE);

DROP POLICY IF EXISTS access_policy_audit_insert_own ON public.vanguard_access_policy_audit_logs;

CREATE POLICY access_policy_audit_insert_own
ON public.vanguard_access_policy_audit_logs
FOR INSERT
TO authenticated
WITH CHECK (actor_user_id IS NULL OR auth.uid() = actor_user_id);

DROP POLICY IF EXISTS access_policy_audit_no_client_select ON public.vanguard_access_policy_audit_logs;

CREATE POLICY access_policy_audit_no_client_select
ON public.vanguard_access_policy_audit_logs
FOR SELECT
TO anon, authenticated
USING (FALSE);
