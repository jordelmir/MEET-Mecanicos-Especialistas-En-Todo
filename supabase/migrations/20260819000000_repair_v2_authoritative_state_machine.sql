-- 20260819000000_repair_v2_authoritative_state_machine.sql
-- Enforce strict server-authoritative state transitions, role gating, NULL-safe participant checks,
-- optimistic locking via expected_version, idempotency equivocation protection,
-- canonical vanguard_events emission, and strictly typed evidence materialization.

-- 1. Canonical Vanguard Domain Event Helper Function
CREATE OR REPLACE FUNCTION public.meet_emit_vanguard_event_v2(
  p_aggregate_type TEXT,
  p_aggregate_id TEXT,
  p_event_type TEXT,
  p_actor_id TEXT,
  p_actor_role TEXT,
  p_source TEXT,
  p_idempotency_key TEXT,
  p_payload_json TEXT,
  p_correlation_id TEXT DEFAULT NULL,
  p_causation_id TEXT DEFAULT NULL,
  p_schema_version INT DEFAULT 1
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_event_id TEXT := 'evt_' || gen_random_uuid()::text;
  v_occurred_at_ms BIGINT := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
BEGIN
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
    occurred_at_ms,
    created_at
  ) VALUES (
    v_event_id,
    p_aggregate_type,
    p_aggregate_id,
    p_event_type,
    p_actor_id,
    p_actor_role,
    COALESCE(p_source, 'server_rpc'),
    p_correlation_id,
    p_causation_id,
    p_idempotency_key,
    p_payload_json,
    p_schema_version,
    v_occurred_at_ms,
    NOW()
  )
  ON CONFLICT (idempotency_key) DO NOTHING;

  RETURN v_event_id;
EXCEPTION WHEN undefined_table THEN
  RETURN NULL;
END;
$$;

REVOKE ALL ON FUNCTION public.meet_emit_vanguard_event_v2(
  TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, INT
) FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.meet_emit_vanguard_event_v2(
  TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, INT
) TO service_role;

-- 2. Authoritative State Machine RPC for Repair Work Orders
CREATE OR REPLACE FUNCTION public.transition_repair_work_order_v1(
  p_work_order_id UUID,
  p_actor_id UUID,
  p_actor_role TEXT,
  p_to_status TEXT,
  p_reason TEXT,
  p_evidence JSONB DEFAULT NULL,
  p_idempotency_key TEXT DEFAULT NULL,
  p_expected_version INT DEFAULT NULL
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
  v_current_version INT;
  v_item JSONB;
  v_evidence_type TEXT;
  v_file_hash TEXT;
  v_file_url TEXT;
  v_caption TEXT;
  v_post_scan_hash TEXT := NULL;
  v_invoice_hash TEXT := NULL;
  v_is_valid_transition BOOLEAN := FALSE;
  v_idem_key TEXT;
BEGIN
  -- 1. Validate Target Status Parity (Including validation_passed and validation_failed)
  IF p_to_status NOT IN (
    'draft', 'published', 'triaged', 'waiting_offers', 'offer_received', 'offer_accepted',
    'mechanic_assigned', 'in_route', 'inspection_started', 'diagnosis_confirmed',
    'parts_required', 'waiting_parts', 'repair_in_progress', 'repair_completed',
    'validation_pending', 'validation_passed', 'validation_failed',
    'customer_confirmed', 'closed', 'cancelled', 'disputed', 'refunded'
  ) THEN
    RAISE EXCEPTION 'Invalid target work order status: %', p_to_status;
  END IF;

  -- 2. Lock Work Order Row
  SELECT wo.status, wo.customer_profile_id, wo.mechanic_profile_id, pp.user_profile_id, wo.repair_request_id, COALESCE(wo.version, 1)
  INTO v_from_status, v_customer_id, v_mechanic_id, v_mechanic_user_profile_id, v_request_id, v_current_version
  FROM public.repair_work_orders wo
  LEFT JOIN public.provider_profiles pp ON pp.id = wo.mechanic_profile_id
  WHERE wo.id = p_work_order_id
  FOR UPDATE OF wo;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Work order not found';
  END IF;

  -- 3. Optimistic Concurrency Check (Fail-closed against stale writes)
  IF p_expected_version IS NOT NULL AND v_current_version != p_expected_version THEN
    RAISE EXCEPTION 'STALE_COMMAND: Expected version %, but current work order version is %', p_expected_version, v_current_version;
  END IF;

  -- 4. Validate Participant Identity with NULL-safe checks
  IF p_actor_role = 'customer' THEN
    IF p_actor_id IS DISTINCT FROM v_customer_id THEN
      RAISE EXCEPTION 'Customer % is not the owner of work order %', p_actor_id, p_work_order_id;
    END IF;
  ELSIF p_actor_role = 'mechanic' THEN
    IF NOT (
      p_actor_id IS NOT DISTINCT FROM v_mechanic_id OR
      (v_mechanic_user_profile_id IS NOT NULL AND p_actor_id IS NOT DISTINCT FROM v_mechanic_user_profile_id)
    ) THEN
      RAISE EXCEPTION 'Mechanic % is not the assigned technician on work order %', p_actor_id, p_work_order_id;
    END IF;
  ELSIF p_actor_role NOT IN ('system', 'admin') THEN
    RAISE EXCEPTION 'Invalid actor role: %', p_actor_role;
  END IF;

  -- 5. Idempotency Check with Equivocation Protection
  v_idem_key := COALESCE(p_idempotency_key, 'idem_' || gen_random_uuid()::text);
  IF p_idempotency_key IS NOT NULL AND EXISTS (
    SELECT 1 FROM public.repair_status_events
    WHERE idempotency_key = p_idempotency_key
  ) THEN
    IF EXISTS (
      SELECT 1 FROM public.repair_status_events
      WHERE idempotency_key = p_idempotency_key
        AND work_order_id = p_work_order_id
        AND to_status = p_to_status
    ) THEN
      RETURN TRUE;
    ELSE
      RAISE EXCEPTION 'IDEMPOTENCY_EQUIVOCATION: Idempotency key % was already used for a different transition', p_idempotency_key;
    END IF;
  END IF;

  -- 6. Strict Server-Side State Machine Matrix Check
  IF p_actor_role IN ('admin', 'system') THEN
    IF v_from_status IN ('cancelled', 'refunded', 'closed') AND p_to_status NOT IN ('disputed', 'refunded', 'closed') THEN
      RAISE EXCEPTION 'Cannot revive terminal work order from % to %', v_from_status, p_to_status;
    END IF;
    v_is_valid_transition := TRUE;
  ELSIF p_to_status = 'disputed' THEN
    v_is_valid_transition := (v_from_status NOT IN ('cancelled', 'refunded'));
  ELSIF p_to_status = 'cancelled' THEN
    v_is_valid_transition := (p_actor_role = 'customer' AND v_from_status IN ('draft', 'published', 'waiting_offers', 'offer_received', 'mechanic_assigned'));
  ELSE
    CASE v_from_status
      WHEN 'mechanic_assigned', 'offer_accepted' THEN
        IF p_to_status = 'in_route' AND p_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'in_route' THEN
        IF p_to_status = 'inspection_started' AND p_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'inspection_started' THEN
        IF p_to_status = 'diagnosis_confirmed' AND p_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'diagnosis_confirmed' THEN
        IF p_to_status IN ('parts_required', 'waiting_parts', 'repair_in_progress') AND p_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'parts_required', 'waiting_parts' THEN
        IF p_to_status = 'repair_in_progress' AND p_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'repair_in_progress' THEN
        IF p_to_status IN ('validation_pending', 'repair_completed') AND p_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'validation_pending' THEN
        IF p_to_status = 'validation_passed' AND p_actor_role = 'mechanic' THEN
          -- Validation passed strictly requires non-empty post-scan evidence
          IF p_evidence IS NULL OR (jsonb_typeof(p_evidence) = 'array' AND jsonb_array_length(p_evidence) = 0) THEN
            RAISE EXCEPTION 'Transition to validation_passed requires post-scan verification evidence';
          END IF;
          v_is_valid_transition := TRUE;
        ELSIF p_to_status = 'validation_failed' AND p_actor_role = 'mechanic' THEN
          v_is_valid_transition := TRUE;
        END IF;
      WHEN 'validation_failed' THEN
        IF p_to_status = 'repair_in_progress' AND p_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'validation_passed' THEN
        IF p_to_status = 'customer_confirmed' AND p_actor_role = 'customer' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'repair_completed' THEN
        IF p_to_status = 'customer_confirmed' AND p_actor_role = 'customer' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'customer_confirmed' THEN
        IF p_to_status = 'closed' AND p_actor_role IN ('customer', 'admin', 'system') THEN v_is_valid_transition := TRUE; END IF;
      ELSE
        v_is_valid_transition := FALSE;
    END CASE;
  END IF;

  IF NOT v_is_valid_transition THEN
    RAISE EXCEPTION 'Illegal state transition from % to % by actor role %', v_from_status, p_to_status, p_actor_role;
  END IF;

  -- 7. Insert Evidence Blobs & Extract Strictly Typed Evidence Hashes
  IF COALESCE(jsonb_typeof(p_evidence), 'array') = 'array' THEN
    FOR v_item IN SELECT * FROM jsonb_array_elements(COALESCE(p_evidence, '[]'::jsonb))
    LOOP
      v_evidence_type := UPPER(COALESCE(v_item->>'evidence_type', v_item->>'type', ''));
      v_file_hash := COALESCE(v_item->>'file_hash', v_item->>'hash');
      v_file_url := COALESCE(v_item->>'file_url', v_item->>'url');
      v_caption := v_item->>'caption';

      -- Avoid evidence type confusion
      IF v_file_hash IS NOT NULL THEN
        IF v_evidence_type IN ('POST_SCAN_REPORT', 'POST_SCAN', 'DTC_SCAN') AND v_post_scan_hash IS NULL THEN
          v_post_scan_hash := v_file_hash;
        ELSIF v_evidence_type IN ('FINAL_INVOICE', 'INVOICE', 'RECEIPT') AND v_invoice_hash IS NULL THEN
          v_invoice_hash := v_file_hash;
        END IF;
      END IF;

      IF v_evidence_type <> '' AND v_file_hash IS NOT NULL THEN
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
          v_item
        );
      END IF;
    END LOOP;
  END IF;

  -- 8. Update Work Order with Version Increment, Timestamps and Evidence Materialization
  UPDATE public.repair_work_orders
  SET
    status = p_to_status,
    version = v_current_version + 1,
    started_at = CASE
      WHEN p_to_status IN ('in_route', 'inspection_started', 'repair_in_progress') AND started_at IS NULL THEN NOW()
      ELSE started_at
    END,
    completed_at = CASE
      WHEN p_to_status IN ('closed', 'customer_confirmed', 'repair_completed') AND completed_at IS NULL THEN NOW()
      ELSE completed_at
    END,
    final_dtc_scan_hash = CASE
      WHEN p_to_status = 'validation_passed' AND v_post_scan_hash IS NOT NULL THEN v_post_scan_hash
      ELSE final_dtc_scan_hash
    END,
    invoice_hash = CASE
      WHEN p_to_status = 'closed' AND v_invoice_hash IS NOT NULL THEN v_invoice_hash
      ELSE invoice_hash
    END,
    updated_at = NOW()
  WHERE id = p_work_order_id;

  IF v_request_id IS NOT NULL THEN
    UPDATE public.repair_requests
    SET
      status = p_to_status,
      version = COALESCE(version, 1) + 1,
      updated_at = NOW()
    WHERE id = v_request_id;
  END IF;

  -- 9. Record Status Transition Event
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
    p_evidence,
    v_idem_key
  );

  -- 10. Emit Vanguard Domain Event via canonical helper
  PERFORM public.meet_emit_vanguard_event_v2(
    p_aggregate_type := 'repair_work_order',
    p_aggregate_id := p_work_order_id::text,
    p_event_type := 'WORK_ORDER_STATE_TRANSITIONED',
    p_actor_id := p_actor_id::text,
    p_actor_role := p_actor_role,
    p_source := 'repair_state_engine_v1',
    p_idempotency_key := v_idem_key,
    p_payload_json := jsonb_build_object(
      'work_order_id', p_work_order_id,
      'from_status', v_from_status,
      'to_status', p_to_status,
      'actor_id', p_actor_id,
      'actor_role', p_actor_role,
      'reason', p_reason,
      'evidence', p_evidence,
      'version', v_current_version + 1
    )::text
  );

  RETURN TRUE;
END;
$$;

REVOKE ALL ON FUNCTION public.transition_repair_work_order_v1(
  UUID, UUID, TEXT, TEXT, TEXT, JSONB, TEXT, INT
) FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.transition_repair_work_order_v1(
  UUID, UUID, TEXT, TEXT, TEXT, JSONB, TEXT, INT
) TO service_role;
