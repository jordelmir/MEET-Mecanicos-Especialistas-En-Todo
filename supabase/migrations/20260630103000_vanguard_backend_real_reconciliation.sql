-- ELYSIUM VANGUARD — real backend hardening for the repair/commerce spine.
-- This migration is intentionally additive/replacing: older migrations may already
-- be applied in production, so we do not edit history. We close unsafe RLS,
-- make critical RPCs idempotent, and move repair close-out side effects to DB.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ─────────────────────────────────────────────────────────────────────────────
-- Auth helpers
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.current_user_profile_id()
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT id
  FROM public.user_profiles
  WHERE auth_user_id = auth.uid()
  LIMIT 1
$$;

CREATE OR REPLACE FUNCTION public.is_admin_profile()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.user_profiles up
    JOIN public.user_roles ur ON ur.user_profile_id = up.id
    WHERE up.auth_user_id = auth.uid()
      AND ur.role_name IN ('admin', 'super_admin', 'support_agent', 'trust_safety_reviewer')
      AND ur.is_active = TRUE
  )
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- Report/evidence table for generated PDFs and immutable repair packets
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.repair_reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  work_order_id UUID NOT NULL REFERENCES public.repair_work_orders(id) ON DELETE CASCADE,
  report_hash TEXT NOT NULL,
  storage_path TEXT,
  pdf_url TEXT,
  generated_by UUID,
  generated_by_role TEXT,
  schema_version INTEGER NOT NULL DEFAULT 1,
  status TEXT NOT NULL DEFAULT 'generated'
    CHECK (status IN ('generated', 'signed', 'voided')),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (work_order_id),
  UNIQUE (report_hash)
);

CREATE INDEX IF NOT EXISTS idx_repair_reports_work_order ON public.repair_reports(work_order_id);

ALTER TABLE public.repair_reports ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS repair_reports_participants_read ON public.repair_reports;

DROP POLICY IF EXISTS repair_reports_no_client_write ON public.repair_reports;

CREATE POLICY repair_reports_participants_read
ON public.repair_reports
FOR SELECT
TO authenticated
USING (
  EXISTS (
    SELECT 1
    FROM public.repair_work_orders wo
    LEFT JOIN public.provider_profiles pp ON pp.id = wo.mechanic_profile_id
    WHERE wo.id = repair_reports.work_order_id
      AND (
        wo.customer_profile_id = public.current_user_profile_id()
        OR pp.user_profile_id = public.current_user_profile_id()
        OR public.is_admin_profile()
      )
  )
);

CREATE POLICY repair_reports_no_client_write
ON public.repair_reports
FOR ALL
TO anon, authenticated
USING (FALSE)
WITH CHECK (FALSE);

-- ─────────────────────────────────────────────────────────────────────────────
-- Harden event stream and ledger. Writes must go through service-side RPC/Edge.
-- ─────────────────────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS vanguard_events_sync_all ON public.vanguard_events;

DROP POLICY IF EXISTS marketplace_ledger_entries_sync_all ON public.marketplace_ledger_entries;

DROP POLICY IF EXISTS vanguard_events_participant_read ON public.vanguard_events;

DROP POLICY IF EXISTS vanguard_events_owner_insert ON public.vanguard_events;

DROP POLICY IF EXISTS marketplace_ledger_entries_participant_read ON public.marketplace_ledger_entries;

REVOKE ALL ON public.vanguard_events FROM anon;

REVOKE ALL ON public.marketplace_ledger_entries FROM anon;

REVOKE INSERT, UPDATE, DELETE ON public.vanguard_events FROM authenticated;

REVOKE INSERT, UPDATE, DELETE ON public.marketplace_ledger_entries FROM authenticated;

GRANT SELECT ON public.vanguard_events TO authenticated;

GRANT SELECT ON public.marketplace_ledger_entries TO authenticated;

GRANT ALL ON public.vanguard_events TO service_role;

GRANT ALL ON public.marketplace_ledger_entries TO service_role;

CREATE POLICY vanguard_events_participant_read
ON public.vanguard_events
FOR SELECT
TO authenticated
USING (
  actor_id = auth.uid()::text
  OR actor_id = public.current_user_profile_id()::text
  OR public.is_admin_profile()
  OR EXISTS (
    SELECT 1
    FROM public.repair_work_orders wo
    LEFT JOIN public.provider_profiles pp ON pp.id = wo.mechanic_profile_id
    WHERE vanguard_events.aggregate_type = 'repair_work_order'
      AND vanguard_events.aggregate_id = wo.id::text
      AND (
        wo.customer_profile_id = public.current_user_profile_id()
        OR pp.user_profile_id = public.current_user_profile_id()
      )
  )
);

CREATE POLICY vanguard_events_owner_insert
ON public.vanguard_events
FOR INSERT
TO authenticated
WITH CHECK (
  actor_id = auth.uid()::text
  OR actor_id = public.current_user_profile_id()::text
);

CREATE POLICY marketplace_ledger_entries_participant_read
ON public.marketplace_ledger_entries
FOR SELECT
TO authenticated
USING (
  participant_id = auth.uid()::text
  OR participant_id = public.current_user_profile_id()::text
  OR public.is_admin_profile()
  OR EXISTS (
    SELECT 1
    FROM public.repair_work_orders wo
    LEFT JOIN public.provider_profiles pp ON pp.id = wo.mechanic_profile_id
    WHERE marketplace_ledger_entries.order_type = 'REPAIR_SERVICE'
      AND marketplace_ledger_entries.order_id = wo.id::text
      AND (
        wo.customer_profile_id = public.current_user_profile_id()
        OR pp.user_profile_id = public.current_user_profile_id()
      )
  )
);

-- Keep anonymous community learning server-authored or explicitly moderated.
DROP POLICY IF EXISTS community_cases_insert ON public.community_cases;

DROP POLICY IF EXISTS community_cases_authenticated_submit ON public.community_cases;

CREATE POLICY community_cases_authenticated_submit
ON public.community_cases
FOR INSERT
TO authenticated
WITH CHECK (
  submitted_by = public.current_user_profile_id()
  AND status IN ('draft', 'pending_review')
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_community_cases_source_work_order_unique
ON public.community_cases(source_work_order_id)
WHERE source_work_order_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- Provider reputation recalculation. Uses only observed backend data.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.recalculate_provider_reputation_v1(
  p_provider_profile_id UUID
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_total_jobs INTEGER := 0;
  v_closed_jobs INTEGER := 0;
  v_disputed_jobs INTEGER := 0;
  v_warranty_claims INTEGER := 0;
  v_documented_jobs INTEGER := 0;
  v_repeat_jobs INTEGER := 0;
  v_unique_customers INTEGER := 0;
  v_avg_completion DOUBLE PRECISION := 0;
  v_success_rate DOUBLE PRECISION := 0;
  v_dispute_rate DOUBLE PRECISION := 0;
  v_warranty_rate DOUBLE PRECISION := 0;
  v_repeat_rate DOUBLE PRECISION := 0;
  v_documentation_score DOUBLE PRECISION := 0;
  v_technical_score DOUBLE PRECISION := 0;
  v_badge TEXT := 'technician';
BEGIN
  SELECT
    count(*)::INTEGER,
    count(*) FILTER (WHERE status = 'closed')::INTEGER,
    count(*) FILTER (WHERE status IN ('disputed', 'refunded'))::INTEGER,
    count(*) FILTER (
      WHERE status = 'closed'
        AND before_photos_hash IS NOT NULL
        AND after_photos_hash IS NOT NULL
        AND report_hash IS NOT NULL
    )::INTEGER,
    COALESCE(avg(EXTRACT(EPOCH FROM (completed_at - started_at)) / 60.0)
      FILTER (WHERE started_at IS NOT NULL AND completed_at IS NOT NULL AND completed_at > started_at), 0)
  INTO v_total_jobs, v_closed_jobs, v_disputed_jobs, v_documented_jobs, v_avg_completion
  FROM public.repair_work_orders
  WHERE mechanic_profile_id = p_provider_profile_id;

  IF v_total_jobs > 0 THEN
    v_success_rate := v_closed_jobs::DOUBLE PRECISION / v_total_jobs;
    v_dispute_rate := v_disputed_jobs::DOUBLE PRECISION / v_total_jobs;
  END IF;

  IF v_closed_jobs > 0 THEN
    SELECT count(*)::INTEGER
    INTO v_warranty_claims
    FROM public.repair_warranties rw
    JOIN public.repair_work_orders wo ON wo.id = rw.work_order_id
    WHERE wo.mechanic_profile_id = p_provider_profile_id
      AND wo.status = 'closed'
      AND rw.claim_status = 'claimed';

    v_warranty_rate := v_warranty_claims::DOUBLE PRECISION / v_closed_jobs;
    v_documentation_score := v_documented_jobs::DOUBLE PRECISION / v_closed_jobs;

    SELECT count(DISTINCT customer_profile_id)::INTEGER
    INTO v_unique_customers
    FROM public.repair_work_orders
    WHERE mechanic_profile_id = p_provider_profile_id
      AND status = 'closed';

    v_repeat_jobs := GREATEST(v_closed_jobs - COALESCE(v_unique_customers, 0), 0);
    v_repeat_rate := v_repeat_jobs::DOUBLE PRECISION / v_closed_jobs;
  END IF;

  v_technical_score := LEAST(1.0, GREATEST(0.0,
      (v_success_rate * 0.35)
    + ((1.0 - v_dispute_rate) * 0.15)
    + ((1.0 - v_warranty_rate) * 0.15)
    + (v_documentation_score * 0.20)
    + (LEAST(1.0, v_repeat_rate + 0.25) * 0.15)
  ));

  v_badge := CASE
    WHEN v_technical_score >= 0.90 AND v_closed_jobs >= 50 THEN 'elite_workshop'
    WHEN v_technical_score >= 0.75 AND v_closed_jobs >= 25 THEN 'master'
    WHEN v_technical_score >= 0.60 AND v_closed_jobs >= 10 THEN 'pro'
    WHEN v_technical_score >= 0.40 AND v_closed_jobs >= 5 THEN 'certified'
    ELSE 'technician'
  END;

  INSERT INTO public.provider_reputation_scores (
    provider_profile_id,
    repair_success_rate,
    comeback_rate,
    diagnostic_accuracy,
    avg_response_time_minutes,
    avg_completion_time_minutes,
    dispute_rate,
    warranty_claim_rate,
    repeat_customer_rate,
    verified_repairs_count,
    documentation_quality_score,
    quote_accuracy,
    on_time_arrival_rate,
    parts_return_rate,
    technical_score,
    badge_level,
    total_completed_jobs,
    last_calculated_at,
    metadata
  )
  VALUES (
    p_provider_profile_id,
    v_success_rate,
    v_warranty_rate,
    v_success_rate,
    0,
    v_avg_completion,
    v_dispute_rate,
    v_warranty_rate,
    v_repeat_rate,
    v_closed_jobs,
    v_documentation_score,
    0,
    0,
    0,
    v_technical_score,
    v_badge,
    v_closed_jobs,
    now(),
    jsonb_build_object('source', 'recalculate_provider_reputation_v1', 'observed_jobs', v_total_jobs)
  )
  ON CONFLICT (provider_profile_id) DO UPDATE
  SET repair_success_rate = EXCLUDED.repair_success_rate,
      comeback_rate = EXCLUDED.comeback_rate,
      diagnostic_accuracy = EXCLUDED.diagnostic_accuracy,
      avg_response_time_minutes = EXCLUDED.avg_response_time_minutes,
      avg_completion_time_minutes = EXCLUDED.avg_completion_time_minutes,
      dispute_rate = EXCLUDED.dispute_rate,
      warranty_claim_rate = EXCLUDED.warranty_claim_rate,
      repeat_customer_rate = EXCLUDED.repeat_customer_rate,
      verified_repairs_count = EXCLUDED.verified_repairs_count,
      documentation_quality_score = EXCLUDED.documentation_quality_score,
      technical_score = EXCLUDED.technical_score,
      badge_level = EXCLUDED.badge_level,
      total_completed_jobs = EXCLUDED.total_completed_jobs,
      last_calculated_at = now(),
      metadata = EXCLUDED.metadata,
      updated_at = now();

  RETURN TRUE;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- Offer acceptance: atomic, participant-safe, idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.accept_repair_offer_v1(
  p_repair_request_id UUID,
  p_offer_id UUID,
  p_customer_id UUID,
  p_idempotency_key TEXT
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_mechanic_profile_id UUID;
  v_estimated_price_cents BIGINT;
  v_labor_fee_cents BIGINT;
  v_travel_fee_cents BIGINT;
  v_warranty_days INT;
  v_request_status TEXT;
  v_offer_status TEXT;
  v_work_order_id UUID;
  v_existing_offer_id UUID;
  v_event_id TEXT;
BEGIN
  SELECT work_order_id
  INTO v_work_order_id
  FROM public.repair_status_events
  WHERE idempotency_key = p_idempotency_key
  LIMIT 1;

  IF v_work_order_id IS NOT NULL THEN
    RETURN v_work_order_id;
  END IF;

  SELECT id, accepted_offer_id
  INTO v_work_order_id, v_existing_offer_id
  FROM public.repair_work_orders
  WHERE repair_request_id = p_repair_request_id
  LIMIT 1;

  IF v_work_order_id IS NOT NULL THEN
    IF v_existing_offer_id = p_offer_id THEN
      RETURN v_work_order_id;
    END IF;
    RAISE EXCEPTION 'Repair request already has a different accepted offer';
  END IF;

  SELECT status
  INTO v_request_status
  FROM public.repair_requests
  WHERE id = p_repair_request_id
    AND customer_profile_id = p_customer_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Repair request not found or unauthorized';
  END IF;

  IF v_request_status IN ('closed', 'cancelled', 'disputed', 'refunded') THEN
    RAISE EXCEPTION 'Repair request is not acceptably active';
  END IF;

  SELECT mechanic_profile_id, estimated_price_cents, labor_fee_cents, travel_fee_cents, warranty_days, status
  INTO v_mechanic_profile_id, v_estimated_price_cents, v_labor_fee_cents, v_travel_fee_cents, v_warranty_days, v_offer_status
  FROM public.repair_offers
  WHERE id = p_offer_id
    AND repair_request_id = p_repair_request_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Offer not found';
  END IF;

  IF v_offer_status <> 'pending' THEN
    RAISE EXCEPTION 'Offer is not pending';
  END IF;

  UPDATE public.repair_offers
  SET status = 'accepted',
      version = version + 1,
      updated_at = now()
  WHERE id = p_offer_id;

  UPDATE public.repair_offers
  SET status = 'rejected',
      version = version + 1,
      updated_at = now()
  WHERE repair_request_id = p_repair_request_id
    AND id <> p_offer_id
    AND status = 'pending';

  UPDATE public.repair_requests
  SET status = 'offer_accepted',
      version = version + 1,
      updated_at = now()
  WHERE id = p_repair_request_id;

  INSERT INTO public.repair_work_orders (
    repair_request_id,
    accepted_offer_id,
    mechanic_profile_id,
    customer_profile_id,
    status,
    final_price_cents,
    final_labor_cents,
    final_parts_cents,
    warranty_terms,
    warranty_expires_at,
    version,
    metadata
  )
  VALUES (
    p_repair_request_id,
    p_offer_id,
    v_mechanic_profile_id,
    p_customer_id,
    'mechanic_assigned',
    v_estimated_price_cents,
    v_labor_fee_cents,
    GREATEST(v_estimated_price_cents - v_labor_fee_cents - v_travel_fee_cents, 0),
    CASE WHEN v_warranty_days > 0 THEN v_warranty_days || ' days warranty from accepted offer' ELSE NULL END,
    CASE WHEN v_warranty_days > 0 THEN now() + make_interval(days => v_warranty_days) ELSE NULL END,
    1,
    jsonb_build_object('accepted_offer_id', p_offer_id, 'accepted_idempotency_key', p_idempotency_key)
  )
  ON CONFLICT (repair_request_id) DO UPDATE
  SET updated_at = public.repair_work_orders.updated_at
  RETURNING id INTO v_work_order_id;

  INSERT INTO public.repair_status_events (
    work_order_id,
    from_status,
    to_status,
    actor_id,
    actor_role,
    reason,
    evidence,
    idempotency_key
  )
  VALUES (
    v_work_order_id,
    v_request_status,
    'mechanic_assigned',
    p_customer_id,
    'customer',
    'Offer accepted by customer',
    jsonb_build_object('offer_id', p_offer_id),
    p_idempotency_key
  )
  ON CONFLICT (idempotency_key) DO NOTHING;

  v_event_id := gen_random_uuid()::text;

  INSERT INTO public.vanguard_events (
    event_id,
    aggregate_type,
    aggregate_id,
    event_type,
    actor_id,
    actor_role,
    source,
    correlation_id,
    idempotency_key,
    payload_json,
    schema_version,
    occurred_at_ms
  )
  VALUES (
    v_event_id,
    'repair_work_order',
    v_work_order_id::text,
    'REPAIR_MECHANIC_ASSIGNED',
    p_customer_id::text,
    'customer',
    'rpc.accept_repair_offer_v1',
    p_repair_request_id::text,
    'event_' || p_idempotency_key,
    jsonb_build_object(
      'offer_id', p_offer_id,
      'request_id', p_repair_request_id,
      'price_cents', v_estimated_price_cents
    )::text,
    1,
    (extract(epoch from now()) * 1000)::BIGINT
  )
  ON CONFLICT (idempotency_key) DO NOTHING;

  RETURN v_work_order_id;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- Work order transitions: validates participant, records evidence, mirrors state.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.transition_repair_work_order_v1(
  p_work_order_id UUID,
  p_actor_id UUID,
  p_actor_role TEXT,
  p_to_status TEXT,
  p_reason TEXT,
  p_evidence JSONB,
  p_idempotency_key TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_from_status TEXT;
  v_customer_id UUID;
  v_mechanic_id UUID;
  v_mechanic_user_profile_id UUID;
  v_request_id UUID;
  v_event_id TEXT;
  v_item JSONB;
  v_evidence_type TEXT;
  v_file_hash TEXT;
  v_file_url TEXT;
  v_caption TEXT;
BEGIN
  IF p_to_status NOT IN (
    'mechanic_assigned', 'in_route', 'inspection_started', 'diagnosis_confirmed',
    'parts_required', 'waiting_parts', 'repair_in_progress', 'repair_completed',
    'validation_pending', 'customer_confirmed', 'closed', 'cancelled', 'disputed', 'refunded'
  ) THEN
    RAISE EXCEPTION 'Invalid target work order status';
  END IF;

  SELECT wo.status, wo.customer_profile_id, wo.mechanic_profile_id, pp.user_profile_id, wo.repair_request_id
  INTO v_from_status, v_customer_id, v_mechanic_id, v_mechanic_user_profile_id, v_request_id
  FROM public.repair_work_orders wo
  LEFT JOIN public.provider_profiles pp ON pp.id = wo.mechanic_profile_id
  WHERE wo.id = p_work_order_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Work order not found';
  END IF;

  IF p_actor_role = 'customer' AND p_actor_id <> v_customer_id THEN
    RAISE EXCEPTION 'Customer is not a participant in this work order';
  END IF;

  IF p_actor_role = 'mechanic' AND p_actor_id NOT IN (v_mechanic_id, v_mechanic_user_profile_id) THEN
    RAISE EXCEPTION 'Mechanic is not a participant in this work order';
  END IF;

  IF p_actor_role NOT IN ('customer', 'mechanic', 'system', 'admin') THEN
    RAISE EXCEPTION 'Invalid actor role';
  END IF;

  IF v_from_status = 'closed' AND p_to_status <> 'disputed' THEN
    RAISE EXCEPTION 'Closed work orders cannot transition except to dispute';
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.repair_status_events
    WHERE idempotency_key = p_idempotency_key
  ) THEN
    RETURN TRUE;
  END IF;

  IF COALESCE(jsonb_typeof(p_evidence), 'array') = 'array' THEN
    FOR v_item IN SELECT * FROM jsonb_array_elements(COALESCE(p_evidence, '[]'::jsonb))
    LOOP
      v_evidence_type := COALESCE(v_item->>'evidence_type', v_item->>'type');
      v_file_hash := COALESCE(v_item->>'file_hash', v_item->>'hash');
      v_file_url := COALESCE(v_item->>'file_url', v_item->>'url');
      v_caption := v_item->>'caption';

      IF v_evidence_type IS NOT NULL AND v_file_hash IS NOT NULL THEN
        INSERT INTO public.repair_evidence (
          work_order_id,
          evidence_type,
          file_url,
          file_hash,
          caption,
          actor_id,
          actor_role,
          metadata
        )
        VALUES (
          p_work_order_id,
          v_evidence_type,
          v_file_url,
          v_file_hash,
          v_caption,
          p_actor_id,
          p_actor_role,
          COALESCE(v_item->'metadata', '{}'::jsonb)
        )
        ON CONFLICT DO NOTHING;

        IF v_evidence_type = 'photo_before' THEN
          UPDATE public.repair_work_orders
          SET before_photos_hash = COALESCE(before_photos_hash, v_file_hash)
          WHERE id = p_work_order_id;
        ELSIF v_evidence_type = 'photo_after' THEN
          UPDATE public.repair_work_orders
          SET after_photos_hash = COALESCE(after_photos_hash, v_file_hash)
          WHERE id = p_work_order_id;
        ELSIF v_evidence_type = 'dtc_scan' THEN
          UPDATE public.repair_work_orders
          SET final_dtc_scan_hash = COALESCE(final_dtc_scan_hash, v_file_hash)
          WHERE id = p_work_order_id;
        ELSIF v_evidence_type = 'report' THEN
          UPDATE public.repair_work_orders
          SET report_hash = COALESCE(report_hash, v_file_hash)
          WHERE id = p_work_order_id;

          INSERT INTO public.repair_reports (
            work_order_id,
            report_hash,
            storage_path,
            pdf_url,
            generated_by,
            generated_by_role,
            metadata
          )
          VALUES (
            p_work_order_id,
            v_file_hash,
            v_item->>'storage_path',
            v_file_url,
            p_actor_id,
            p_actor_role,
            COALESCE(v_item->'metadata', '{}'::jsonb)
          )
          ON CONFLICT (work_order_id) DO UPDATE
          SET report_hash = EXCLUDED.report_hash,
              storage_path = COALESCE(EXCLUDED.storage_path, public.repair_reports.storage_path),
              pdf_url = COALESCE(EXCLUDED.pdf_url, public.repair_reports.pdf_url);
        ELSIF v_evidence_type = 'invoice' THEN
          UPDATE public.repair_work_orders
          SET invoice_hash = COALESCE(invoice_hash, v_file_hash)
          WHERE id = p_work_order_id;
        ELSIF v_evidence_type = 'signature' AND p_actor_role = 'customer' THEN
          UPDATE public.repair_work_orders
          SET customer_signature_hash = COALESCE(customer_signature_hash, v_file_hash)
          WHERE id = p_work_order_id;
        ELSIF v_evidence_type = 'signature' AND p_actor_role = 'mechanic' THEN
          UPDATE public.repair_work_orders
          SET mechanic_signature_hash = COALESCE(mechanic_signature_hash, v_file_hash)
          WHERE id = p_work_order_id;
        END IF;
      END IF;
    END LOOP;
  END IF;

  UPDATE public.repair_work_orders
  SET status = p_to_status,
      version = version + 1,
      started_at = CASE WHEN p_to_status = 'repair_in_progress' AND started_at IS NULL THEN now() ELSE started_at END,
      completed_at = CASE WHEN p_to_status IN ('repair_completed', 'validation_pending', 'customer_confirmed') AND completed_at IS NULL THEN now() ELSE completed_at END,
      updated_at = now()
  WHERE id = p_work_order_id;

  UPDATE public.repair_requests
  SET status = p_to_status,
      version = version + 1,
      updated_at = now()
  WHERE id = v_request_id;

  INSERT INTO public.repair_status_events (
    work_order_id,
    from_status,
    to_status,
    actor_id,
    actor_role,
    reason,
    evidence,
    idempotency_key
  )
  VALUES (
    p_work_order_id,
    v_from_status,
    p_to_status,
    p_actor_id,
    p_actor_role,
    p_reason,
    COALESCE(p_evidence, '{}'::jsonb),
    p_idempotency_key
  )
  ON CONFLICT (idempotency_key) DO NOTHING;

  v_event_id := gen_random_uuid()::text;

  INSERT INTO public.vanguard_events (
    event_id,
    aggregate_type,
    aggregate_id,
    event_type,
    actor_id,
    actor_role,
    source,
    correlation_id,
    idempotency_key,
    payload_json,
    schema_version,
    occurred_at_ms
  )
  VALUES (
    v_event_id,
    'repair_work_order',
    p_work_order_id::text,
    'REPAIR_' || upper(p_to_status),
    p_actor_id::text,
    p_actor_role,
    'rpc.transition_repair_work_order_v1',
    v_request_id::text,
    'event_' || p_idempotency_key,
    jsonb_build_object('from_status', v_from_status, 'to_status', p_to_status, 'reason', p_reason)::text,
    1,
    (extract(epoch from now()) * 1000)::BIGINT
  )
  ON CONFLICT (idempotency_key) DO NOTHING;

  RETURN TRUE;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- Close repair: evidence gate, commission, ledger, timeline, reputation, case.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.close_repair_v1(
  p_work_order_id UUID,
  p_actor_id UUID,
  p_idempotency_key TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_status TEXT;
  v_customer_id UUID;
  v_mechanic_id UUID;
  v_mechanic_user_profile_id UUID;
  v_provider_type TEXT;
  v_request_id UUID;
  v_vehicle_id UUID;
  v_vehicle_make TEXT;
  v_vehicle_model TEXT;
  v_vehicle_year INTEGER;
  v_vehicle_engine TEXT;
  v_dtc_codes TEXT[];
  v_symptoms TEXT;
  v_freeze_frame JSONB;
  v_live_data JSONB;
  v_price_cents BIGINT;
  v_report_hash TEXT;
  v_after_photos_hash TEXT;
  v_final_scan_hash TEXT;
  v_event_id TEXT;
  v_transaction_id TEXT;
  v_payout_entry_id TEXT;
  v_commission_entry_id TEXT;
  v_commission_rate_bps INT := 1000;
  v_commission_cents BIGINT := 0;
  v_net_cents BIGINT := 0;
  v_plan_key TEXT;
  v_rule_key TEXT := 'repair_default';
  v_actor_role TEXT := 'customer';
  v_tests_performed TEXT;
  v_root_cause TEXT;
  v_repair_applied TEXT;
  v_parts_used JSONB;
  v_repair_minutes INTEGER;
  v_case_confidence DOUBLE PRECISION;
BEGIN
  IF EXISTS (
    SELECT 1
    FROM public.repair_commissions
    WHERE work_order_id = p_work_order_id
      AND idempotency_key = 'commission_' || p_idempotency_key
  ) THEN
    RETURN TRUE;
  END IF;

  SELECT
    wo.status,
    wo.customer_profile_id,
    wo.mechanic_profile_id,
    pp.user_profile_id,
    COALESCE(pp.provider_type, 'MECHANIC'),
    wo.repair_request_id,
    wo.final_price_cents,
    wo.report_hash,
    wo.after_photos_hash,
    wo.final_dtc_scan_hash,
    rr.vehicle_id,
    rr.vehicle_make,
    rr.vehicle_model,
    rr.vehicle_year,
    rr.vehicle_engine,
    rr.dtc_codes,
    rr.symptoms,
    rr.freeze_frame,
    rr.live_data_snapshot,
    COALESCE(wo.metadata->>'tests_performed', rr.metadata->>'tests_performed'),
    COALESCE(wo.metadata->>'root_cause', rr.metadata->>'root_cause'),
    COALESCE(wo.metadata->>'repair_applied', rr.metadata->>'repair_applied'),
    COALESCE(wo.metadata->'parts_used', '[]'::jsonb),
    CASE
      WHEN wo.started_at IS NOT NULL AND wo.completed_at IS NOT NULL AND wo.completed_at > wo.started_at
      THEN (EXTRACT(EPOCH FROM (wo.completed_at - wo.started_at)) / 60)::INTEGER
      ELSE NULL
    END
  INTO
    v_status,
    v_customer_id,
    v_mechanic_id,
    v_mechanic_user_profile_id,
    v_provider_type,
    v_request_id,
    v_price_cents,
    v_report_hash,
    v_after_photos_hash,
    v_final_scan_hash,
    v_vehicle_id,
    v_vehicle_make,
    v_vehicle_model,
    v_vehicle_year,
    v_vehicle_engine,
    v_dtc_codes,
    v_symptoms,
    v_freeze_frame,
    v_live_data,
    v_tests_performed,
    v_root_cause,
    v_repair_applied,
    v_parts_used,
    v_repair_minutes
  FROM public.repair_work_orders wo
  JOIN public.repair_requests rr ON rr.id = wo.repair_request_id
  LEFT JOIN public.provider_profiles pp ON pp.id = wo.mechanic_profile_id
  WHERE wo.id = p_work_order_id
  FOR UPDATE OF wo;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Work order not found';
  END IF;

  IF p_actor_id = v_mechanic_id OR p_actor_id = v_mechanic_user_profile_id THEN
    v_actor_role := 'mechanic';
  ELSIF p_actor_id = v_customer_id THEN
    v_actor_role := 'customer';
  ELSE
    RAISE EXCEPTION 'Actor is not a participant in this work order';
  END IF;

  IF v_status = 'closed' THEN
    RETURN TRUE;
  END IF;

  IF v_status NOT IN ('repair_completed', 'validation_pending', 'customer_confirmed') THEN
    RAISE EXCEPTION 'Work order is not in a completed status that can be closed';
  END IF;

  IF COALESCE(v_price_cents, 0) <= 0 THEN
    RAISE EXCEPTION 'Cannot close repair without final price';
  END IF;

  IF v_report_hash IS NULL OR v_after_photos_hash IS NULL THEN
    RAISE EXCEPTION 'Cannot close repair without report and completion photo evidence';
  END IF;

  SELECT plan_key
  INTO v_plan_key
  FROM public.provider_plan_assignments
  WHERE status = 'active'
    AND (
      provider_profile_id = v_mechanic_id::text
      OR user_id = (
        SELECT auth_user_id
        FROM public.user_profiles
        WHERE id = v_mechanic_user_profile_id
      )
    )
  ORDER BY starts_at DESC
  LIMIT 1;

  SELECT rule_key, rate_bps
  INTO v_rule_key, v_commission_rate_bps
  FROM public.vanguard_commission_rules
  WHERE transaction_kind = 'REPAIR_SERVICE'
    AND is_active = TRUE
    AND (plan_key IS NULL OR plan_key = v_plan_key)
    AND (provider_type IS NULL OR upper(provider_type) = upper(v_provider_type))
  ORDER BY
    CASE
      WHEN plan_key = v_plan_key AND upper(provider_type) = upper(v_provider_type) THEN 0
      WHEN plan_key = v_plan_key AND provider_type IS NULL THEN 1
      WHEN plan_key IS NULL AND upper(provider_type) = upper(v_provider_type) THEN 2
      WHEN plan_key IS NULL AND provider_type IS NULL THEN 3
      ELSE 4
    END,
    priority ASC
  LIMIT 1;

  IF NOT FOUND THEN
    v_rule_key := 'repair_default';
    v_commission_rate_bps := 1000;
  END IF;

  v_commission_cents := (v_price_cents * COALESCE(v_commission_rate_bps, 1000)) / 10000;
  v_net_cents := v_price_cents - v_commission_cents;
  v_event_id := gen_random_uuid()::text;
  v_transaction_id := gen_random_uuid()::text;
  v_payout_entry_id := gen_random_uuid()::text;
  v_commission_entry_id := gen_random_uuid()::text;

  UPDATE public.repair_work_orders
  SET status = 'closed',
      version = version + 1,
      closed_at = COALESCE(closed_at, now()),
      updated_at = now(),
      metadata = metadata || jsonb_build_object(
        'closed_idempotency_key', p_idempotency_key,
        'closed_by_role', v_actor_role,
        'commission_rule_key', v_rule_key
      )
  WHERE id = p_work_order_id;

  UPDATE public.repair_requests
  SET status = 'closed',
      version = version + 1,
      updated_at = now()
  WHERE id = v_request_id;

  INSERT INTO public.repair_status_events (
    work_order_id,
    from_status,
    to_status,
    actor_id,
    actor_role,
    reason,
    evidence,
    idempotency_key
  )
  VALUES (
    p_work_order_id,
    v_status,
    'closed',
    p_actor_id,
    v_actor_role,
    'Repair closed with required evidence',
    jsonb_build_object('report_hash', v_report_hash, 'after_photos_hash', v_after_photos_hash, 'final_dtc_scan_hash', v_final_scan_hash),
    p_idempotency_key
  )
  ON CONFLICT (idempotency_key) DO NOTHING;

  INSERT INTO public.vanguard_events (
    event_id,
    aggregate_type,
    aggregate_id,
    event_type,
    actor_id,
    actor_role,
    source,
    correlation_id,
    idempotency_key,
    payload_json,
    schema_version,
    occurred_at_ms
  )
  VALUES (
    v_event_id,
    'repair_work_order',
    p_work_order_id::text,
    'REPAIR_CLOSED',
    p_actor_id::text,
    v_actor_role,
    'rpc.close_repair_v1',
    v_request_id::text,
    'event_' || p_idempotency_key,
    jsonb_build_object(
      'gross_cents', v_price_cents,
      'commission_cents', v_commission_cents,
      'net_cents', v_net_cents,
      'rule_key', v_rule_key
    )::text,
    1,
    (extract(epoch from now()) * 1000)::BIGINT
  )
  ON CONFLICT (idempotency_key) DO NOTHING;

  SELECT event_id
  INTO v_event_id
  FROM public.vanguard_events
  WHERE idempotency_key = 'event_' || p_idempotency_key
  LIMIT 1;

  INSERT INTO public.repair_commissions (
    work_order_id,
    provider_profile_id,
    transaction_kind,
    gross_amount_cents,
    commission_rate_bps,
    commission_amount_cents,
    net_provider_cents,
    currency,
    rule_key,
    status,
    ledger_entry_id,
    idempotency_key,
    metadata
  )
  VALUES (
    p_work_order_id,
    v_mechanic_id,
    'REPAIR_SERVICE',
    v_price_cents,
    COALESCE(v_commission_rate_bps, 1000),
    v_commission_cents,
    v_net_cents,
    'USD',
    v_rule_key,
    'released',
    v_payout_entry_id,
    'commission_' || p_idempotency_key,
    jsonb_build_object('transaction_id', v_transaction_id)
  )
  ON CONFLICT (work_order_id) DO UPDATE
  SET status = EXCLUDED.status,
      ledger_entry_id = COALESCE(public.repair_commissions.ledger_entry_id, EXCLUDED.ledger_entry_id),
      updated_at = now();

  INSERT INTO public.marketplace_ledger_entries (
    ledger_entry_id,
    transaction_id,
    related_event_id,
    order_type,
    order_id,
    participant_id,
    participant_role,
    entry_type,
    direction,
    amount_cents,
    currency,
    status,
    metadata_json,
    created_at_ms,
    idempotency_key
  )
  VALUES
  (
    v_payout_entry_id,
    v_transaction_id,
    v_event_id,
    'REPAIR_SERVICE',
    p_work_order_id::text,
    v_mechanic_id::text,
    lower(v_provider_type),
    'provider_payout',
    'credit',
    v_net_cents,
    'USD',
    'pending',
    jsonb_build_object('rule_key', v_rule_key, 'kind', 'provider_net')::text,
    (extract(epoch from now()) * 1000)::BIGINT,
    'ledger_payout_' || p_idempotency_key
  ),
  (
    v_commission_entry_id,
    v_transaction_id,
    v_event_id,
    'REPAIR_SERVICE',
    p_work_order_id::text,
    'platform',
    'platform',
    'platform_commission',
    'credit',
    v_commission_cents,
    'USD',
    'pending',
    jsonb_build_object('rule_key', v_rule_key, 'kind', 'platform_commission')::text,
    (extract(epoch from now()) * 1000)::BIGINT,
    'ledger_commission_' || p_idempotency_key
  )
  ON CONFLICT (idempotency_key) DO NOTHING;

  IF v_vehicle_id IS NOT NULL THEN
    INSERT INTO public.vehicle_timeline_events (
      vehicle_id,
      event_type,
      title,
      description,
      actor_id,
      actor_role,
      related_entity_type,
      related_entity_id,
      evidence_hash,
      data,
      is_private
    )
    VALUES (
      v_vehicle_id,
      'repair_completed',
      'Orden de reparación cerrada',
      'La reparación fue cerrada con evidencia y reporte verificable.',
      p_actor_id,
      v_actor_role,
      'repair_work_order',
      p_work_order_id,
      v_report_hash,
      jsonb_build_object('final_price_cents', v_price_cents, 'dtc_codes', v_dtc_codes),
      TRUE
    )
    ON CONFLICT DO NOTHING;
  END IF;

  v_case_confidence := CASE
    WHEN v_report_hash IS NOT NULL AND v_after_photos_hash IS NOT NULL AND COALESCE(array_length(v_dtc_codes, 1), 0) > 0 THEN 0.82
    WHEN v_report_hash IS NOT NULL AND v_after_photos_hash IS NOT NULL THEN 0.70
    ELSE 0.45
  END;

  INSERT INTO public.community_cases (
    source_work_order_id,
    vehicle_make,
    vehicle_model,
    vehicle_year,
    vehicle_engine,
    dtc_codes,
    symptoms,
    freeze_frame_summary,
    live_data_summary,
    tests_performed,
    root_cause,
    repair_applied,
    parts_used,
    approximate_cost_cents,
    repair_time_minutes,
    outcome,
    recurrence,
    confidence,
    quality_score,
    status,
    submitted_by,
    metadata
  )
  VALUES (
    p_work_order_id,
    v_vehicle_make,
    v_vehicle_model,
    v_vehicle_year,
    v_vehicle_engine,
    COALESCE(v_dtc_codes, '{}'::text[]),
    v_symptoms,
    COALESCE(v_freeze_frame, '{}'::jsonb),
    COALESCE(v_live_data, '{}'::jsonb),
    v_tests_performed,
    v_root_cause,
    v_repair_applied,
    COALESCE(v_parts_used, '[]'::jsonb),
    v_price_cents,
    v_repair_minutes,
    'success',
    FALSE,
    v_case_confidence,
    v_case_confidence,
    'pending_review',
    v_customer_id,
    jsonb_build_object(
      'anonymized_by', 'close_repair_v1',
      'report_hash_present', v_report_hash IS NOT NULL,
      'photo_hash_present', v_after_photos_hash IS NOT NULL,
      'final_scan_hash_present', v_final_scan_hash IS NOT NULL
    )
  )
  ON CONFLICT (source_work_order_id) WHERE source_work_order_id IS NOT NULL DO UPDATE
  SET confidence = GREATEST(public.community_cases.confidence, EXCLUDED.confidence),
      quality_score = GREATEST(public.community_cases.quality_score, EXCLUDED.quality_score),
      metadata = public.community_cases.metadata || EXCLUDED.metadata,
      updated_at = now();

  PERFORM public.recalculate_provider_reputation_v1(v_mechanic_id);

  RETURN TRUE;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- Edge-backed mobile/web outbox sync. No client direct ledger writes required.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.sync_vanguard_outbox_v1(
  p_actor_profile_id UUID,
  p_events JSONB,
  p_ledger_entries JSONB,
  p_idempotency_key TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_event JSONB;
  v_ledger JSONB;
  v_event_count INTEGER := 0;
  v_ledger_count INTEGER := 0;
  v_actor_text TEXT := p_actor_profile_id::text;
BEGIN
  IF p_actor_profile_id IS NULL THEN
    RAISE EXCEPTION 'Actor profile is required';
  END IF;

  IF COALESCE(jsonb_typeof(p_events), 'array') <> 'array' THEN
    RAISE EXCEPTION 'Events payload must be an array';
  END IF;

  IF COALESCE(jsonb_typeof(p_ledger_entries), 'array') <> 'array' THEN
    RAISE EXCEPTION 'Ledger payload must be an array';
  END IF;

  FOR v_event IN SELECT * FROM jsonb_array_elements(COALESCE(p_events, '[]'::jsonb))
  LOOP
    INSERT INTO public.vanguard_events (
      event_id,
      aggregate_type,
      aggregate_id,
      event_type,
      actor_id,
      actor_role,
      source,
      correlation_id,
      causation_id,
      idempotency_key,
      payload_json,
      schema_version,
      occurred_at_ms
    )
    VALUES (
      COALESCE(v_event->>'event_id', gen_random_uuid()::text),
      v_event->>'aggregate_type',
      v_event->>'aggregate_id',
      v_event->>'event_type',
      v_actor_text,
      v_event->>'actor_role',
      COALESCE(v_event->>'source', 'client_outbox'),
      v_event->>'correlation_id',
      v_event->>'causation_id',
      v_event->>'idempotency_key',
      COALESCE(v_event->>'payload_json', '{}'::text),
      COALESCE((v_event->>'schema_version')::INT, 1),
      COALESCE((v_event->>'occurred_at_ms')::BIGINT, (extract(epoch from now()) * 1000)::BIGINT)
    )
    ON CONFLICT (idempotency_key) DO NOTHING;

    v_event_count := v_event_count + 1;
  END LOOP;

  FOR v_ledger IN SELECT * FROM jsonb_array_elements(COALESCE(p_ledger_entries, '[]'::jsonb))
  LOOP
    INSERT INTO public.marketplace_ledger_entries (
      ledger_entry_id,
      transaction_id,
      related_event_id,
      order_type,
      order_id,
      participant_id,
      participant_role,
      entry_type,
      direction,
      amount_cents,
      currency,
      status,
      metadata_json,
      created_at_ms,
      settled_at_ms,
      idempotency_key
    )
    VALUES (
      COALESCE(v_ledger->>'ledger_entry_id', gen_random_uuid()::text),
      v_ledger->>'transaction_id',
      v_ledger->>'related_event_id',
      v_ledger->>'order_type',
      v_ledger->>'order_id',
      v_ledger->>'participant_id',
      v_ledger->>'participant_role',
      v_ledger->>'entry_type',
      v_ledger->>'direction',
      COALESCE((v_ledger->>'amount_cents')::BIGINT, 0),
      COALESCE(v_ledger->>'currency', 'USD'),
      CASE
        WHEN lower(COALESCE(v_ledger->>'entry_type', '')) IN (
          'gross_capture',
          'platform_commission',
          'provider_payout',
          'payment_refund'
        ) THEN 'client_pending_review'
        ELSE COALESCE(v_ledger->>'status', 'pending')
      END,
      COALESCE(v_ledger->>'metadata_json', '{}'::text),
      COALESCE((v_ledger->>'created_at_ms')::BIGINT, (extract(epoch from now()) * 1000)::BIGINT),
      NULLIF(v_ledger->>'settled_at_ms', '')::BIGINT,
      v_ledger->>'idempotency_key'
    )
    ON CONFLICT (idempotency_key) DO NOTHING;

    v_ledger_count := v_ledger_count + 1;
  END LOOP;

  INSERT INTO public.audit_logs (
    actor_id,
    actor_role,
    action,
    resource_type,
    resource_id,
    new_state,
    idempotency_key
  )
  VALUES (
    p_actor_profile_id,
    'client',
    'vanguard.outbox_synced',
    'vanguard_outbox',
    p_idempotency_key,
    jsonb_build_object('events_received', v_event_count, 'ledger_entries_received', v_ledger_count),
    'audit_' || p_idempotency_key
  )
  ON CONFLICT (idempotency_key) DO NOTHING;

  RETURN jsonb_build_object(
    'ok', TRUE,
    'eventsReceived', v_event_count,
    'ledgerEntriesReceived', v_ledger_count
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.accept_repair_offer_v1(UUID, UUID, UUID, TEXT) TO service_role;

GRANT EXECUTE ON FUNCTION public.close_repair_v1(UUID, UUID, TEXT) TO service_role;

GRANT EXECUTE ON FUNCTION public.transition_repair_work_order_v1(UUID, UUID, TEXT, TEXT, TEXT, JSONB, TEXT) TO service_role;

GRANT EXECUTE ON FUNCTION public.recalculate_provider_reputation_v1(UUID) TO service_role;

GRANT EXECUTE ON FUNCTION public.sync_vanguard_outbox_v1(UUID, JSONB, JSONB, TEXT) TO service_role;
