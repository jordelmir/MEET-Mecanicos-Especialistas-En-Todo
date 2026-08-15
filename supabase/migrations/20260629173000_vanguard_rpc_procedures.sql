-- ══════════════════════════════════════════════════════════════════════════════
-- ELYSIUM VANGUARD — P0 FOUNDATION RPC PROCEDURES
-- Exposes transactional PostgreSQL functions for critical client-server flows.
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. accept_repair_offer_v1
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
  v_event_id UUID;
  v_request_version INT;
BEGIN
  -- 1. Lock request row
  SELECT status, version INTO v_request_status, v_request_version
  FROM public.repair_requests
  WHERE id = p_repair_request_id AND customer_profile_id = p_customer_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Repair request not found or unauthorized';
  END IF;

  IF v_request_status IN ('offer_accepted', 'mechanic_assigned', 'closed', 'cancelled') THEN
    RAISE EXCEPTION 'Repair request is already accepted, closed, or cancelled';
  END IF;

  -- 2. Lock and fetch offer row
  SELECT mechanic_profile_id, estimated_price_cents, labor_fee_cents, travel_fee_cents, warranty_days, status
  INTO v_mechanic_profile_id, v_estimated_price_cents, v_labor_fee_cents, v_travel_fee_cents, v_warranty_days, v_offer_status
  FROM public.repair_offers
  WHERE id = p_offer_id AND repair_request_id = p_repair_request_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Offer not found';
  END IF;

  IF v_offer_status != 'pending' THEN
    RAISE EXCEPTION 'Offer is not pending';
  END IF;

  -- 3. Update offer status to accepted
  UPDATE public.repair_offers
  SET status = 'accepted', updated_at = now()
  WHERE id = p_offer_id;

  -- 4. Reject all other pending offers
  UPDATE public.repair_offers
  SET status = 'rejected', updated_at = now()
  WHERE repair_request_id = p_repair_request_id AND id != p_offer_id AND status = 'pending';

  -- 5. Update repair request status
  UPDATE public.repair_requests
  SET status = 'offer_accepted', version = version + 1, updated_at = now()
  WHERE id = p_repair_request_id;

  -- 6. Insert work order
  INSERT INTO public.repair_work_orders (
    repair_request_id,
    accepted_offer_id,
    mechanic_profile_id,
    customer_profile_id,
    status,
    final_price_cents,
    final_labor_cents,
    version
  )
  VALUES (
    p_repair_request_id,
    p_offer_id,
    v_mechanic_profile_id,
    p_customer_id,
    'mechanic_assigned',
    v_estimated_price_cents,
    v_labor_fee_cents,
    1
  )
  RETURNING id INTO v_work_order_id;

  -- 7. Insert status event
  INSERT INTO public.repair_status_events (
    work_order_id,
    from_status,
    to_status,
    actor_id,
    actor_role,
    reason,
    idempotency_key
  )
  VALUES (
    v_work_order_id,
    'offer_accepted',
    'mechanic_assigned',
    p_customer_id,
    'customer',
    'Offer accepted by customer',
    p_idempotency_key
  );

  -- 8. Emit domain event
  v_event_id := gen_random_uuid();
  INSERT INTO public.vanguard_events (
    event_id,
    aggregate_type,
    aggregate_id,
    event_type,
    actor_id,
    actor_role,
    source,
    idempotency_key,
    payload_json,
    occurred_at_ms
  )
  VALUES (
    v_event_id,
    'repair_work_order',
    v_work_order_id,
    'REPAIR_MECHANIC_ASSIGNED',
    p_customer_id::text,
    'customer',
    'rpc_procedure',
    'event_' || p_idempotency_key,
    json_build_object(
      'offer_id', p_offer_id,
      'request_id', p_repair_request_id,
      'price_cents', v_estimated_price_cents
    )::text,
    (extract(epoch from now()) * 1000)::BIGINT
  );

  RETURN v_work_order_id;
END;
$$;
-- ─────────────────────────────────────────────────────────────────────────────
-- 2. close_repair_v1
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
  v_version INT;
  v_customer_id UUID;
  v_mechanic_id UUID;
  v_vehicle_id UUID;
  v_price_cents BIGINT;
  v_report_hash TEXT;
  v_after_photos_hash TEXT;
  v_payout_entry_id UUID;
  v_commission_entry_id UUID;
  v_event_id UUID;
  v_commission_rate_bps INT := 1000; -- Default 10%
  v_commission_cents BIGINT;
  v_net_cents BIGINT;
  v_plan_key TEXT;
  v_rule_key TEXT;
BEGIN
  -- 1. Fetch and Lock Work Order
  SELECT status, version, customer_profile_id, mechanic_profile_id, final_price_cents, report_hash, after_photos_hash, repair_request_id
  INTO v_status, v_version, v_customer_id, v_mechanic_id, v_price_cents, v_report_hash, v_after_photos_hash, v_payout_entry_id
  FROM public.repair_work_orders
  WHERE id = p_work_order_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Work order not found';
  END IF;

  IF v_status != 'validation_pending' AND v_status != 'customer_confirmed' AND v_status != 'repair_completed' THEN
    RAISE EXCEPTION 'Work order is not in a completed status that can be closed';
  END IF;

  -- 2. Validate minimal evidence
  IF v_report_hash IS NULL OR v_after_photos_hash IS NULL THEN
    RAISE EXCEPTION 'Cannot close repair without evidence hashes (report and completion photos)';
  END IF;

  -- Get vehicle_id from the original request
  SELECT vehicle_id INTO v_vehicle_id FROM public.repair_requests WHERE id = v_payout_entry_id;

  -- 3. Resolve commission rules
  -- Get active provider subscription plan to check discounts
  SELECT plan_key INTO v_plan_key
  FROM public.provider_plan_assignments
  WHERE user_id = (SELECT auth_user_id FROM public.user_profiles WHERE id = (SELECT user_profile_id FROM public.provider_profiles WHERE id = v_mechanic_id))
    AND status = 'active'
  LIMIT 1;

  -- Get rule rate
  SELECT rule_key, rate_bps INTO v_rule_key, v_commission_rate_bps
  FROM public.vanguard_commission_rules
  WHERE transaction_kind = 'REPAIR_SERVICE'
    AND (plan_key = v_plan_key OR (plan_key IS NULL AND v_plan_key IS NULL))
  ORDER BY priority ASC
  LIMIT 1;

  IF NOT FOUND THEN
    v_commission_rate_bps := 1000; -- Default fallback 10%
    v_rule_key := 'repair_default';
  END IF;

  v_commission_cents := (v_price_cents * v_commission_rate_bps) / 10000;
  v_net_cents := v_price_cents - v_commission_cents;

  -- 4. Update status to closed
  UPDATE public.repair_work_orders
  SET status = 'closed', version = version + 1, closed_at = now()
  WHERE id = p_work_order_id;

  -- Update request status
  UPDATE public.repair_requests
  SET status = 'closed'
  WHERE id = (SELECT repair_request_id FROM public.repair_work_orders WHERE id = p_work_order_id);

  -- 5. Record Commission
  INSERT INTO public.repair_commissions (
    work_order_id,
    provider_profile_id,
    transaction_kind,
    gross_amount_cents,
    commission_rate_bps,
    commission_amount_cents,
    net_provider_cents,
    rule_key,
    status,
    idempotency_key
  )
  VALUES (
    p_work_order_id,
    v_mechanic_id,
    'REPAIR_SERVICE',
    v_price_cents,
    v_commission_rate_bps,
    v_commission_cents,
    v_net_cents,
    v_rule_key,
    'released',
    'commission_' || p_idempotency_key
  );

  -- 6. Insert Ledger Entries
  v_payout_entry_id := gen_random_uuid();
  v_commission_entry_id := gen_random_uuid();
  v_event_id := gen_random_uuid();

  -- Emit vanguard event
  INSERT INTO public.vanguard_events (
    event_id,
    aggregate_type,
    aggregate_id,
    event_type,
    actor_id,
    actor_role,
    source,
    idempotency_key,
    payload_json,
    occurred_at_ms
  )
  VALUES (
    v_event_id,
    'repair_work_order',
    p_work_order_id,
    'REPAIR_CLOSED',
    p_actor_id::text,
    'customer',
    'rpc_procedure',
    'event_' || p_idempotency_key,
    json_build_object(
      'work_order_id', p_work_order_id,
      'gross_cents', v_price_cents,
      'commission_cents', v_commission_cents,
      'net_cents', v_net_cents
    )::text,
    (extract(epoch from now()) * 1000)::BIGINT
  );

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
    status,
    metadata_json,
    created_at_ms,
    idempotency_key
  )
  VALUES
  (
    v_payout_entry_id,
    gen_random_uuid()::text,
    v_event_id,
    'REPAIR_SERVICE',
    p_work_order_id::text,
    v_mechanic_id::text,
    'mechanic',
    'provider_payout',
    'credit',
    v_net_cents,
    'pending',
    '{"rule_key": "repair_closed"}',
    (extract(epoch from now()) * 1000)::BIGINT,
    'ledger_payout_' || p_idempotency_key
  ),
  (
    v_commission_entry_id,
    gen_random_uuid()::text,
    v_event_id,
    'REPAIR_SERVICE',
    p_work_order_id::text,
    'platform',
    'platform',
    'platform_commission',
    'credit',
    v_commission_cents,
    'pending',
    '{"rule_key": "repair_closed"}',
    (extract(epoch from now()) * 1000)::BIGINT,
    'ledger_comm_' || p_idempotency_key
  );

  -- 7. Log to Vehicle Timeline
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
      data
    )
    VALUES (
      v_vehicle_id,
      'repair_completed',
      'Orden de reparación cerrada',
      'La reparación ha sido confirmada y cerrada satisfactoriamente.',
      p_actor_id,
      'customer',
      'repair_work_order',
      p_work_order_id,
      v_report_hash,
      json_build_object(
        'work_order_id', p_work_order_id,
        'final_price_cents', v_price_cents
      )
    );
  END IF;

  RETURN TRUE;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. verify_provider_v1
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.verify_provider_v1(
  p_provider_profile_id UUID,
  p_reviewer_id UUID,
  p_approve BOOLEAN,
  p_notes TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_profile_id UUID;
  v_provider_type TEXT;
BEGIN
  -- 1. Check if provider exists
  SELECT user_profile_id, provider_type INTO v_user_profile_id, v_provider_type
  FROM public.provider_profiles
  WHERE id = p_provider_profile_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Provider profile not found';
  END IF;

  -- 2. Update all pending verifications to reviewed status
  UPDATE public.provider_verifications
  SET
    status = CASE WHEN p_approve THEN 'approved' ELSE 'rejected' END,
    reviewer_id = p_reviewer_id,
    reviewer_notes = p_notes,
    reviewed_at = now()
  WHERE provider_profile_id = p_provider_profile_id AND status = 'pending';

  -- 3. Update provider profile verification flag
  UPDATE public.provider_profiles
  SET
    is_verified = p_approve,
    status = CASE WHEN p_approve THEN 'active' ELSE status END,
    updated_at = now()
  WHERE id = p_provider_profile_id;

  -- 4. Emit Audit Log
  INSERT INTO public.audit_logs (
    actor_id,
    actor_role,
    action,
    resource_type,
    resource_id,
    new_state
  )
  VALUES (
    p_reviewer_id,
    'admin',
    'provider.verified',
    'provider',
    p_provider_profile_id::text,
    json_build_object(
      'is_verified', p_approve,
      'reviewer_notes', p_notes
    )
  );

  RETURN TRUE;
END;
$$;
