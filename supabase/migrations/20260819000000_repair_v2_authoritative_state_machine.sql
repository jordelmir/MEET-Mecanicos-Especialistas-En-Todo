-- 20260819000000_repair_v2_authoritative_state_machine.sql
-- Enforce strict server-authoritative state transitions, role gating, NULL-safe participant checks,
-- version increments, timestamp updates, evidence materialization and status parity with Android RepairStateEngine.

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
  v_current_version INT;
  v_event_id TEXT;
  v_item JSONB;
  v_evidence_type TEXT;
  v_file_hash TEXT;
  v_file_url TEXT;
  v_caption TEXT;
  v_primary_evidence_hash TEXT := NULL;
  v_is_valid_transition BOOLEAN := FALSE;
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

  -- 3. Validate Participant Identity with NULL-safe checks
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

  -- 4. Idempotency Check
  IF EXISTS (
    SELECT 1 FROM public.repair_status_events
    WHERE idempotency_key = p_idempotency_key
  ) THEN
    RETURN TRUE;
  END IF;

  -- 5. Strict Server-Side State Machine Matrix Check
  IF p_actor_role IN ('admin', 'system') THEN
    -- Admin override must still follow non-terminal transition sanity
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
          -- Validation passed strictly requires non-empty evidence
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

  -- 6. Insert Evidence Blobs & Extract Primary Evidence Hash
  IF COALESCE(jsonb_typeof(p_evidence), 'array') = 'array' THEN
    FOR v_item IN SELECT * FROM jsonb_array_elements(COALESCE(p_evidence, '[]'::jsonb))
    LOOP
      v_evidence_type := COALESCE(v_item->>'evidence_type', v_item->>'type');
      v_file_hash := COALESCE(v_item->>'file_hash', v_item->>'hash');
      v_file_url := COALESCE(v_item->>'file_url', v_item->>'url');
      v_caption := v_item->>'caption';

      IF v_file_hash IS NOT NULL AND v_primary_evidence_hash IS NULL THEN
        v_primary_evidence_hash := v_file_hash;
      END IF;

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
          v_item
        );
      END IF;
    END LOOP;
  END IF;

  -- 7. Update Work Order with Version Increment, Timestamps and Evidence Materialization
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
      WHEN p_to_status = 'validation_passed' AND v_primary_evidence_hash IS NOT NULL THEN v_primary_evidence_hash
      ELSE final_dtc_scan_hash
    END,
    invoice_hash = CASE
      WHEN p_to_status = 'closed' AND v_primary_evidence_hash IS NOT NULL THEN v_primary_evidence_hash
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

  -- 8. Record Status Transition Event
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
    p_idempotency_key
  );

  -- 9. Emit Vanguard Domain Event (if vanguard_events table exists)
  BEGIN
    INSERT INTO public.vanguard_events (
      aggregate_id,
      aggregate_type,
      event_type,
      version,
      payload,
      occurred_at
    )
    VALUES (
      p_work_order_id,
      'REPAIR_WORK_ORDER',
      'WORK_ORDER_STATE_TRANSITIONED',
      v_current_version + 1,
      jsonb_build_object(
        'work_order_id', p_work_order_id,
        'from_status', v_from_status,
        'to_status', p_to_status,
        'actor_id', p_actor_id,
        'actor_role', p_actor_role,
        'idempotency_key', p_idempotency_key,
        'reason', p_reason
      ),
      NOW()
    );
  EXCEPTION WHEN undefined_table THEN
    -- vanguard_events table is optional depending on projection architecture
    NULL;
  END;

  RETURN TRUE;
END;
$$;
