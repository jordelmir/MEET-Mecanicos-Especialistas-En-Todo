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

-- Align persisted constraints with the authoritative state machine before replacing the RPC.
ALTER TABLE public.repair_work_orders DROP CONSTRAINT IF EXISTS repair_work_orders_status_check;
ALTER TABLE public.repair_work_orders ADD CONSTRAINT repair_work_orders_status_check CHECK (status IN (
  'mechanic_assigned', 'in_route', 'inspection_started', 'diagnosis_confirmed',
  'parts_required', 'waiting_parts', 'repair_in_progress', 'repair_completed',
  'validation_pending', 'validation_passed', 'validation_failed', 'customer_confirmed',
  'closed', 'cancelled', 'disputed', 'refunded'
));
ALTER TABLE public.repair_requests DROP CONSTRAINT IF EXISTS repair_requests_status_check;
ALTER TABLE public.repair_requests ADD CONSTRAINT repair_requests_status_check CHECK (status IN (
  'draft', 'published', 'triaged', 'waiting_offers', 'offer_received', 'offer_accepted',
  'mechanic_assigned', 'in_route', 'inspection_started', 'diagnosis_confirmed',
  'parts_required', 'waiting_parts', 'repair_in_progress', 'repair_completed',
  'validation_pending', 'validation_passed', 'validation_failed', 'customer_confirmed',
  'closed', 'cancelled', 'disputed', 'refunded'
));
ALTER TABLE public.repair_status_events
  ADD COLUMN IF NOT EXISTS canonical_command_hash TEXT,
  ADD COLUMN IF NOT EXISTS expected_version INTEGER;

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
  v_customer_auth_uid UUID;
  v_mechanic_id UUID;
  v_mechanic_auth_uid UUID;
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
  v_existing_invoice_hash TEXT := NULL;
  v_has_valid_post_scan BOOLEAN := FALSE;
  v_has_valid_invoice BOOLEAN := FALSE;
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
  IF p_idempotency_key IS NULL OR LENGTH(TRIM(p_idempotency_key)) < 8 THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT: a stable idempotency key of at least 8 characters is mandatory';
  END IF;
  IF p_reason IS NULL OR LENGTH(TRIM(p_reason)) = 0 THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT: transition reason is mandatory';
  END IF;
  IF p_evidence IS NOT NULL AND jsonb_typeof(p_evidence) <> 'array' THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT: evidence must be a JSON array';
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
  SELECT wo.status, wo.customer_profile_id, customer_up.auth_user_id,
         wo.mechanic_profile_id, mechanic_up.auth_user_id,
         wo.repair_request_id, COALESCE(wo.version, 1), wo.invoice_hash
  INTO v_from_status, v_customer_id, v_customer_auth_uid,
       v_mechanic_id, v_mechanic_auth_uid, v_request_id, v_current_version, v_existing_invoice_hash
  FROM public.repair_work_orders wo
  JOIN public.user_profiles customer_up ON customer_up.id = wo.customer_profile_id
  LEFT JOIN public.provider_profiles pp ON pp.id = wo.mechanic_profile_id
  LEFT JOIN public.user_profiles mechanic_up ON mechanic_up.id = pp.user_profile_id
  WHERE wo.id = p_work_order_id
  FOR UPDATE OF wo;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'NOT_FOUND: Work order % does not exist', p_work_order_id;
  END IF;

  -- 6. Derive Actor Role from Database Ownership (NEVER TRUST CLIENT CLAIMS)
  IF v_caller_uid = v_customer_auth_uid THEN
    v_actor_role := 'customer';
    v_actor_id := v_customer_id;
  ELSIF v_mechanic_auth_uid IS NOT NULL AND v_caller_uid = v_mechanic_auth_uid THEN
    v_actor_role := 'mechanic';
    v_actor_id := v_mechanic_id;
  ELSE
    RAISE EXCEPTION 'FORBIDDEN: User % is not a participant in work order %', v_caller_uid, p_work_order_id;
  END IF;

  -- 7. Compute Canonical Command Hash for Strict Anti-Equivocation Idempotency
  v_canonical_command_hash := encode(
    extensions.digest(
      p_work_order_id::text || ':' ||
      v_from_status || ':' ||
      p_to_status || ':' ||
      p_expected_version::text || ':' ||
      v_actor_id::text || ':' ||
      v_actor_role || ':' ||
      COALESCE(p_reason, '') || ':' ||
      COALESCE(p_idempotency_key, '') || ':' ||
      COALESCE(p_evidence::text, ''),
      'sha256'
    ),
    'hex'
  );

  v_idem_key := p_idempotency_key;

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
          AND actor_id = v_actor_id
          AND actor_role = v_actor_role
          AND reason IS NOT DISTINCT FROM p_reason
          AND evidence IS NOT DISTINCT FROM p_evidence
          AND expected_version = p_expected_version
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

  -- A valid replay is returned above; new commands must match the locked version.
  IF v_current_version != p_expected_version THEN
    RAISE EXCEPTION 'STALE_COMMAND: Expected version %, but current database version is %', p_expected_version, v_current_version;
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
        IF p_to_status = 'validation_pending' AND v_actor_role = 'mechanic' THEN v_is_valid_transition := TRUE; END IF;
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
      v_evidence_type := LOWER(COALESCE(v_item->>'evidence_type', v_item->>'type', ''));
      v_file_hash := COALESCE(v_item->>'file_hash', v_item->>'hash');
      v_file_url := COALESCE(v_item->>'file_url', v_item->>'url');
      v_caption := v_item->>'caption';

      IF v_file_hash IS NOT NULL THEN
        IF v_file_hash !~ '^[0-9a-fA-F]{64}$' THEN
          RAISE EXCEPTION 'INVALID_EVIDENCE_HASH: evidence hashes must be 64 hexadecimal SHA-256 characters';
        END IF;

        IF v_evidence_type IN ('post_scan_report', 'post_scan', 'dtc_scan') THEN
          v_post_scan_hash := v_file_hash;
          v_has_valid_post_scan := TRUE;
          v_evidence_type := 'dtc_scan';
        ELSIF v_evidence_type IN ('final_invoice', 'invoice', 'receipt') THEN
          v_invoice_hash := v_file_hash;
          v_has_valid_invoice := TRUE;
          v_evidence_type := 'invoice';
        ELSIF v_evidence_type NOT IN ('photo_before', 'photo_after', 'video', 'document', 'signature', 'report', 'voice_note') THEN
          RAISE EXCEPTION 'INVALID_EVIDENCE_TYPE: % is not accepted by repair_evidence', v_evidence_type;
        END IF;

        IF v_file_url IS NULL OR LENGTH(TRIM(v_file_url)) = 0 THEN
          RAISE EXCEPTION 'INVALID_EVIDENCE_REFERENCE: evidence requires a retrievable file_url or storage URI';
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
  IF p_to_status = 'closed' AND NOT v_has_valid_invoice AND v_existing_invoice_hash IS NULL THEN
    RAISE EXCEPTION 'EVIDENCE_REQUIRED: closed requires a valid final invoice evidence hash';
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
      WHEN v_invoice_hash IS NOT NULL THEN v_invoice_hash
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
    idempotency_key,
    canonical_command_hash,
    expected_version
  )
  VALUES (
    p_work_order_id,
    v_from_status,
    p_to_status,
    v_actor_id,
    v_actor_role,
    p_reason,
    p_evidence,
    v_idem_key,
    v_canonical_command_hash,
    p_expected_version
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
REVOKE ALL ON FUNCTION public.transition_repair_work_order_client_v1(UUID, TEXT, TEXT, INT, JSONB, TEXT) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.transition_repair_work_order_client_v1(UUID, TEXT, TEXT, INT, JSONB, TEXT) TO authenticated, service_role;

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
  v_idempotency_key TEXT;
  v_canonical_command_hash TEXT;
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
  IF p_override_reason IS NULL OR LENGTH(TRIM(p_override_reason)) = 0 THEN
    RAISE EXCEPTION 'AUDIT_REQUIRED: Admin override requires a non-empty reason';
  END IF;

  IF p_to_status NOT IN (
    'mechanic_assigned', 'in_route', 'inspection_started', 'diagnosis_confirmed',
    'parts_required', 'waiting_parts', 'repair_in_progress', 'repair_completed',
    'validation_pending', 'validation_passed', 'validation_failed', 'customer_confirmed',
    'closed', 'cancelled', 'disputed', 'refunded'
  ) THEN
    RAISE EXCEPTION 'INVALID_TARGET_STATUS: % is not recognized', p_to_status;
  END IF;

  SELECT status, COALESCE(version, 1) INTO v_from_status, v_current_version
  FROM public.repair_work_orders
  WHERE id = p_work_order_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'NOT_FOUND: Work order % not found', p_work_order_id;
  END IF;

  IF p_expected_version IS NULL OR v_current_version != p_expected_version THEN
    RAISE EXCEPTION 'STALE_COMMAND: Expected version %, but current version is %', p_expected_version, v_current_version;
  END IF;

  v_idempotency_key := 'admin_override_' || gen_random_uuid()::text;
  v_canonical_command_hash := encode(
    extensions.digest(
      p_work_order_id::text || ':' || v_from_status || ':' || p_to_status || ':' ||
      p_expected_version::text || ':' || v_caller_uid::text || ':' ||
      p_ticket_reference || ':' || p_override_reason,
      'sha256'
    ),
    'hex'
  );

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
    idempotency_key,
    canonical_command_hash,
    expected_version
  )
  VALUES (
    p_work_order_id,
    v_from_status,
    p_to_status,
    v_caller_uid,
    'admin',
    'ADMIN_OVERRIDE [Ticket: ' || p_ticket_reference || '] ' || p_override_reason,
    v_idempotency_key,
    v_canonical_command_hash,
    p_expected_version
  );

  PERFORM public.meet_emit_vanguard_event_v2(
    p_aggregate_type := 'repair_work_order',
    p_aggregate_id := p_work_order_id::text,
    p_event_type := 'ADMIN_STATE_OVERRIDE',
    p_actor_id := v_caller_uid::text,
    p_actor_role := 'admin',
    p_source := 'admin_override_repair_state_v1',
    p_idempotency_key := v_idempotency_key,
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

REVOKE ALL ON FUNCTION public.admin_override_repair_state_v1(UUID, TEXT, TEXT, TEXT, INT) FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.admin_override_repair_state_v1(UUID, TEXT, TEXT, TEXT, INT) TO service_role;
