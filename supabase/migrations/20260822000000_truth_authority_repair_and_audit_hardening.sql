-- 20260822000000_truth_authority_repair_and_audit_hardening.sql
-- MEET TRUTH & AUTHORITY CONVERGENCE:
-- 1. Revoke public/authenticated access to internal audit event emitter.
-- 2. Revoke legacy/untrusted transition_repair_work_order_v1 from authenticated.
-- 3. Provide transition_repair_work_order_client_v1 with actor derived strictly from auth.uid() & server DB.
-- 4. Enforce mandatory expected_version (no NULL bypass).
-- 5. Canonical command hash for idempotency and anti-equivocation.
-- 6. Isolate admin override to admin_override_repair_state_v1.

-- ============================================================================
-- 1. HARDEN VANGUARD AUDIT EVENT EMISSION (INTERNAL ONLY)
-- ============================================================================
REVOKE ALL ON FUNCTION public.meet_emit_vanguard_event_v2(
  TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, INT
) FROM authenticated, anon, public;

GRANT EXECUTE ON FUNCTION public.meet_emit_vanguard_event_v2(
  TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, INT
) TO service_role;

-- ============================================================================
-- 2. REVOKE UNTRUSTED RPC FROM AUTHENTICATED
-- ============================================================================
REVOKE ALL ON FUNCTION public.transition_repair_work_order_v1(
  UUID, UUID, TEXT, TEXT, TEXT, JSONB, TEXT, INT
) FROM authenticated, anon, public;

GRANT EXECUTE ON FUNCTION public.transition_repair_work_order_v1(
  UUID, UUID, TEXT, TEXT, TEXT, JSONB, TEXT, INT
) TO service_role;

-- ============================================================================
-- 3. SECURE CLIENT REPAIR TRANSITION RPC (DERIVED ACTOR AUTHORITY)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.transition_repair_work_order_client_v1(
  p_work_order_id UUID,
  p_to_status TEXT,
  p_reason TEXT,
  p_expected_version INT,
  p_evidence JSONB DEFAULT NULL,
  p_idempotency_key TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_uid UUID;
  v_from_status TEXT;
  v_customer_id UUID;
  v_mechanic_id UUID;
  v_mechanic_user_profile_id UUID;
  v_request_id UUID;
  v_current_version INT;
  v_actor_role TEXT;
  v_actor_id UUID;
  v_canonical_command_hash TEXT;
  v_idem_key TEXT;
  v_is_valid_transition BOOLEAN := FALSE;
  v_item JSONB;
  v_evidence_type TEXT;
  v_file_hash TEXT;
  v_file_url TEXT;
  v_caption TEXT;
  v_post_scan_hash TEXT := NULL;
  v_invoice_hash TEXT := NULL;
  v_has_valid_post_scan BOOLEAN := FALSE;
BEGIN
  -- 1. Identify Caller Authority from auth.uid()
  v_caller_uid := auth.uid();
  IF v_caller_uid IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED: Authentication required to execute repair transitions';
  END IF;

  -- 2. Validate Mandatory Optimistic Concurrency Parameter
  IF p_expected_version IS NULL OR p_expected_version < 1 THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT: p_expected_version is mandatory for optimistic state transitions';
  END IF;

  -- 3. Validate Target Status
  IF p_to_status NOT IN (
    'draft', 'published', 'triaged', 'waiting_offers', 'offer_received', 'offer_accepted',
    'mechanic_assigned', 'in_route', 'inspection_started', 'diagnosis_confirmed',
    'parts_required', 'waiting_parts', 'repair_in_progress', 'repair_completed',
    'validation_pending', 'validation_passed', 'validation_failed',
    'customer_confirmed', 'closed', 'cancelled', 'disputed', 'refunded'
  ) THEN
    RAISE EXCEPTION 'INVALID_TARGET_STATUS: % is not a recognized work order status', p_to_status;
  END IF;

  -- 4. Lock Work Order Row and Read Authority State
  SELECT wo.status, wo.customer_profile_id, wo.mechanic_profile_id, pp.user_profile_id, wo.repair_request_id, COALESCE(wo.version, 1)
  INTO v_from_status, v_customer_id, v_mechanic_id, v_mechanic_user_profile_id, v_request_id, v_current_version
  FROM public.repair_work_orders wo
  LEFT JOIN public.provider_profiles pp ON pp.id = wo.mechanic_profile_id
  WHERE wo.id = p_work_order_id
  FOR UPDATE OF wo;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'NOT_FOUND: Work order % does not exist', p_work_order_id;
  END IF;

  -- 5. Strict Optimistic Concurrency Check
  IF v_current_version != p_expected_version THEN
    RAISE EXCEPTION 'STALE_COMMAND: Expected version %, but current database version is %', p_expected_version, v_current_version;
  END IF;

  -- 6. Derive Actor Role from Database Ownership (NEVER TRUST CLIENT CLAIMS)
  IF v_caller_uid = v_customer_id THEN
    v_actor_role := 'customer';
    v_actor_id := v_customer_id;
  ELSIF (v_mechanic_user_profile_id IS NOT NULL AND v_caller_uid = v_mechanic_user_profile_id) OR (v_caller_uid = v_mechanic_id) THEN
    v_actor_role := 'mechanic';
    v_actor_id := COALESCE(v_mechanic_id, v_caller_uid);
  ELSE
    RAISE EXCEPTION 'FORBIDDEN: User % is not a participant in work order %', v_caller_uid, p_work_order_id;
  END IF;

  -- 7. Compute Canonical Command Hash for Strict Anti-Equivocation Idempotency
  v_canonical_command_hash := encode(
    digest(
      p_work_order_id::text || ':' ||
      v_from_status || ':' ||
      p_to_status || ':' ||
      p_expected_version::text || ':' ||
      v_actor_id::text || ':' ||
      v_actor_role || ':' ||
      COALESCE(p_idempotency_key, '') || ':' ||
      COALESCE(p_evidence::text, ''),
      'sha256'
    ),
    'hex'
  );

  v_idem_key := COALESCE(p_idempotency_key, 'idem_' || gen_random_uuid()::text);

  IF p_idempotency_key IS NOT NULL THEN
    IF EXISTS (
      SELECT 1 FROM public.repair_status_events
      WHERE idempotency_key = p_idempotency_key
    ) THEN
      IF EXISTS (
        SELECT 1 FROM public.repair_status_events
        WHERE idempotency_key = p_idempotency_key
          AND work_order_id = p_work_order_id
          AND to_status = p_to_status
      ) THEN
        RETURN jsonb_build_object(
          'success', TRUE,
          'idempotent_replay', TRUE,
          'work_order_id', p_work_order_id,
          'status', p_to_status,
          'version', v_current_version
        );
      ELSE
        RAISE EXCEPTION 'IDEMPOTENCY_EQUIVOCATION: Idempotency key % was used for a different operation', p_idempotency_key;
      END IF;
    END IF;
  END IF;

  -- 8. Enforce State Machine Transitions by Derived Role
  IF p_to_status = 'disputed' THEN
    v_is_valid_transition := (v_from_status NOT IN ('cancelled', 'refunded', 'closed'));
  ELSIF p_to_status = 'cancelled' THEN
    v_is_valid_transition := (v_actor_role = 'customer' AND v_from_status IN ('draft', 'published', 'waiting_offers', 'offer_received', 'mechanic_assigned'));
  ELSE
    CASE v_from_status
      WHEN 'mechanic_assigned', 'offer_accepted' THEN
        IF p_to_status = 'in_route' AND v_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'in_route' THEN
        IF p_to_status = 'inspection_started' AND v_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'inspection_started' THEN
        IF p_to_status = 'diagnosis_confirmed' AND v_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'diagnosis_confirmed' THEN
        IF p_to_status IN ('parts_required', 'waiting_parts', 'repair_in_progress') AND v_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'parts_required', 'waiting_parts' THEN
        IF p_to_status = 'repair_in_progress' AND v_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'repair_in_progress' THEN
        IF p_to_status IN ('validation_pending', 'repair_completed') AND v_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'validation_pending' THEN
        IF p_to_status = 'validation_passed' AND v_actor_role = 'mechanic' THEN
          -- Validation passed strictly requires non-empty post-scan evidence with hash
          IF p_evidence IS NULL OR (jsonb_typeof(p_evidence) = 'array' AND jsonb_array_length(p_evidence) = 0) THEN
            RAISE EXCEPTION 'EVIDENCE_REQUIRED: Transition to validation_passed requires verifiable post-scan evidence';
          END IF;
          v_is_valid_transition := TRUE;
        ELSIF p_to_status = 'validation_failed' AND v_actor_role = 'mechanic' THEN
          v_is_valid_transition := TRUE;
        END IF;
      WHEN 'validation_failed' THEN
        IF p_to_status = 'repair_in_progress' AND v_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'validation_passed' THEN
        IF p_to_status = 'customer_confirmed' AND v_actor_role = 'customer' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'repair_completed' THEN
        IF p_to_status = 'customer_confirmed' AND v_actor_role = 'customer' THEN v_is_valid_transition := TRUE; END IF;
      WHEN 'customer_confirmed' THEN
        IF p_to_status = 'closed' AND v_actor_role = 'customer' THEN v_is_valid_transition := TRUE; END IF;
      ELSE
        v_is_valid_transition := FALSE;
    END CASE;
  END IF;

  IF NOT v_is_valid_transition THEN
    RAISE EXCEPTION 'ILLEGAL_TRANSITION: Cannot transition work order from % to % under role %', v_from_status, p_to_status, v_actor_role;
  END IF;

  -- 9. Ingest Evidence & Extract Hashes
  IF COALESCE(jsonb_typeof(p_evidence), 'array') = 'array' THEN
    FOR v_item IN SELECT * FROM jsonb_array_elements(COALESCE(p_evidence, '[]'::jsonb))
    LOOP
      v_evidence_type := UPPER(COALESCE(v_item->>'evidence_type', v_item->>'type', ''));
      v_file_hash := COALESCE(v_item->>'file_hash', v_item->>'hash');
      v_file_url := COALESCE(v_item->>'file_url', v_item->>'url');
      v_caption := v_item->>'caption';

      IF v_file_hash IS NOT NULL THEN
        IF v_evidence_type IN ('POST_SCAN_REPORT', 'POST_SCAN', 'DTC_SCAN') THEN
          v_post_scan_hash := v_file_hash;
          v_has_valid_post_scan := TRUE;
        ELSIF v_evidence_type IN ('FINAL_INVOICE', 'INVOICE', 'RECEIPT') THEN
          v_invoice_hash := v_file_hash;
        END IF;

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
          v_actor_id,
          v_actor_role,
          v_item
        );
      END IF;
    END LOOP;
  END IF;

  IF p_to_status = 'validation_passed' AND NOT v_has_valid_post_scan THEN
    RAISE EXCEPTION 'EVIDENCE_REQUIRED: validation_passed requires a valid POST_SCAN evidence hash';
  END IF;

  -- 10. Update Work Order State with Version Increment
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

  -- 11. Insert Status Event
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
    v_actor_id,
    v_actor_role,
    p_reason,
    p_evidence,
    v_idem_key
  );

  -- 12. Emit Vanguard Event Internally
  PERFORM public.meet_emit_vanguard_event_v2(
    p_aggregate_type := 'repair_work_order',
    p_aggregate_id := p_work_order_id::text,
    p_event_type := 'WORK_ORDER_STATE_TRANSITIONED',
    p_actor_id := v_actor_id::text,
    p_actor_role := v_actor_role,
    p_source := 'transition_repair_work_order_client_v1',
    p_idempotency_key := v_idem_key,
    p_payload_json := jsonb_build_object(
      'work_order_id', p_work_order_id,
      'from_status', v_from_status,
      'to_status', p_to_status,
      'actor_id', v_actor_id,
      'actor_role', v_actor_role,
      'reason', p_reason,
      'evidence', p_evidence,
      'version', v_current_version + 1,
      'canonical_command_hash', v_canonical_command_hash
    )::text
  );

  RETURN jsonb_build_object(
    'success', TRUE,
    'work_order_id', p_work_order_id,
    'from_status', v_from_status,
    'to_status', p_to_status,
    'version', v_current_version + 1,
    'actor_role', v_actor_role
  );
END;
$$;

-- Grant execution of client RPC to authenticated users
REVOKE ALL ON FUNCTION public.transition_repair_work_order_client_v1 FROM public, anon;
GRANT EXECUTE ON FUNCTION public.transition_repair_work_order_client_v1 TO authenticated, service_role;

-- ============================================================================
-- 4. DEDICATED ADMIN OVERRIDE RPC (SEPARATE SURFACE)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.admin_override_repair_state_v1(
  p_work_order_id UUID,
  p_to_status TEXT,
  p_ticket_reference TEXT,
  p_override_reason TEXT,
  p_expected_version INT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_uid UUID;
  v_current_version INT;
  v_from_status TEXT;
  v_is_platform_admin BOOLEAN := FALSE;
BEGIN
  v_caller_uid := auth.uid();
  IF v_caller_uid IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED: Authentication required';
  END IF;

  -- Check admin claim from auth.jwt()
  IF (auth.jwt()->>'role' = 'service_role') OR (auth.jwt()->'app_metadata'->>'is_platform_admin' = 'true') THEN
    v_is_platform_admin := TRUE;
  END IF;

  IF NOT v_is_platform_admin THEN
    RAISE EXCEPTION 'FORBIDDEN: Admin override requires verified platform admin authorization';
  END IF;

  IF p_ticket_reference IS NULL OR LENGTH(TRIM(p_ticket_reference)) = 0 THEN
    RAISE EXCEPTION 'AUDIT_REQUIRED: Admin override requires a valid p_ticket_reference';
  END IF;

  SELECT status, COALESCE(version, 1) INTO v_from_status, v_current_version
  FROM public.repair_work_orders
  WHERE id = p_work_order_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'NOT_FOUND: Work order % not found', p_work_order_id;
  END IF;

  IF p_expected_version IS NOT NULL AND v_current_version != p_expected_version THEN
    RAISE EXCEPTION 'STALE_COMMAND: Expected version %, but current version is %', p_expected_version, v_current_version;
  END IF;

  UPDATE public.repair_work_orders
  SET
    status = p_to_status,
    version = v_current_version + 1,
    updated_at = NOW()
  WHERE id = p_work_order_id;

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
    p_work_order_id,
    v_from_status,
    p_to_status,
    v_caller_uid,
    'admin',
    'ADMIN_OVERRIDE [Ticket: ' || p_ticket_reference || '] ' || p_override_reason,
    'admin_override_' || gen_random_uuid()::text
  );

  PERFORM public.meet_emit_vanguard_event_v2(
    p_aggregate_type := 'repair_work_order',
    p_aggregate_id := p_work_order_id::text,
    p_event_type := 'ADMIN_STATE_OVERRIDE',
    p_actor_id := v_caller_uid::text,
    p_actor_role := 'admin',
    p_source := 'admin_override_repair_state_v1',
    p_idempotency_key := 'admin_evt_' || gen_random_uuid()::text,
    p_payload_json := jsonb_build_object(
      'work_order_id', p_work_order_id,
      'from_status', v_from_status,
      'to_status', p_to_status,
      'ticket_reference', p_ticket_reference,
      'reason', p_override_reason,
      'version', v_current_version + 1
    )::text
  );

  RETURN jsonb_build_object(
    'success', TRUE,
    'work_order_id', p_work_order_id,
    'from_status', v_from_status,
    'to_status', p_to_status,
    'version', v_current_version + 1,
    'ticket', p_ticket_reference
  );
END;
$$;

REVOKE ALL ON FUNCTION public.admin_override_repair_state_v1 FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.admin_override_repair_state_v1 TO service_role;
