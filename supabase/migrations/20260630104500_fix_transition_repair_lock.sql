-- Fix transition_repair_work_order_v1 row locking.
-- PostgreSQL cannot apply FOR UPDATE to the nullable side of a LEFT JOIN, so
-- lock only the work order row and still read provider metadata.

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
  FOR UPDATE OF wo;

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

GRANT EXECUTE ON FUNCTION public.transition_repair_work_order_v1(UUID, UUID, TEXT, TEXT, TEXT, JSONB, TEXT) TO service_role;
