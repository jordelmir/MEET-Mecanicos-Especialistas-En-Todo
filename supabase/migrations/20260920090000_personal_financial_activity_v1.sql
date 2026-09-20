-- One authenticated, read-only personal activity projection.
-- The client never supplies an account or participant id: every row is scoped
-- from auth.uid(), so another account cannot inspect its activity by changing a
-- request parameter.

CREATE OR REPLACE FUNCTION public.personal_financial_activity_v1(
  p_from timestamptz DEFAULT NULL,
  p_limit integer DEFAULT 250
)
RETURNS TABLE (
  entry_id text,
  occurred_at timestamptz,
  activity_type text,
  title text,
  reference_id text,
  flow text,
  amount_minor bigint,
  currency text,
  status text,
  source text,
  is_confirmed boolean
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_actor uuid := auth.uid();
  v_profile uuid := public.current_user_profile_id();
  v_limit integer := greatest(1, least(coalesce(p_limit, 250), 500));
BEGIN
  IF v_actor IS NULL THEN
    RAISE EXCEPTION 'AUTHENTICATION_REQUIRED';
  END IF;

  RETURN QUERY
  WITH entries AS (
    -- The immutable personal ledger is the canonical source for activities
    -- posted by the unified payment authority.
    SELECT
      fle.entry_id,
      fle.occurred_at,
      CASE
        WHEN fle.trip_or_job_id ILIKE 'tow%' THEN 'GRUAS'
        WHEN fle.trip_or_job_id ILIKE 'ride%' THEN 'VIAJES'
        ELSE 'SERVICIOS_ELYSIUM'
      END AS activity_type,
      replace(fle.entry_type, '_', ' ') AS title,
      fle.trip_or_job_id AS reference_id,
      CASE
        WHEN fle.account_type = 'DRIVER_PAYABLE' THEN 'INGRESO'
        WHEN fle.account_type IN ('PASSENGER_RECEIVABLE', 'REFUND_EXPENSE') THEN 'GASTO'
        WHEN fle.entry_type IN ('PAYOUT_CREATED', 'PAYOUT_SETTLED') THEN 'EGRESO'
        ELSE 'MOVIMIENTO'
      END AS flow,
      abs(fle.minor_units) AS amount_minor,
      fle.currency,
      'POSTED' AS status,
      'FINANCIAL_LEDGER' AS source,
      true AS is_confirmed
    FROM public.financial_ledger_entries fle
    WHERE fle.principal_id = v_actor::text
      AND (p_from IS NULL OR fle.occurred_at >= p_from)

    UNION ALL

    -- Driver wallet movements are append-only. Pending top-ups remain visible
    -- but are explicitly excluded from confirmed totals and wallet balance.
    SELECT
      'wallet:' || w.id::text,
      w.created_at,
      'VIAJES' AS activity_type,
      replace(w.entry_type, '_', ' ') AS title,
      coalesce(w.trip_id::text, w.id::text) AS reference_id,
      CASE
        WHEN w.entry_type LIKE 'TOP_UP%' THEN 'RECARGA'
        WHEN w.direction = 'CREDIT' THEN 'INGRESO'
        ELSE 'GASTO'
      END AS flow,
      w.amount_minor,
      w.currency,
      CASE WHEN w.entry_type = 'TOP_UP_PENDING' THEN 'PENDING' ELSE 'POSTED' END,
      'RIDE_WALLET' AS source,
      w.entry_type <> 'TOP_UP_PENDING' AS is_confirmed
    FROM public.ride_wallet_ledger w
    WHERE w.driver_id = v_actor
      AND (p_from IS NULL OR w.created_at >= p_from)

    UNION ALL

    -- Double-entry postings expose driver earnings and commissions without
    -- inventing passenger charges from a local trip projection.
    SELECT
      'ride-posting:' || p.id::text,
      p.created_at,
      'VIAJES' AS activity_type,
      replace(t.event_type, '_', ' ') AS title,
      coalesce(t.trip_id::text, t.id::text) AS reference_id,
      CASE WHEN p.direction = 'CREDIT' THEN 'INGRESO' ELSE 'GASTO' END AS flow,
      p.amount_minor,
      p.currency,
      'POSTED' AS status,
      'RIDE_DOUBLE_ENTRY' AS source,
      true AS is_confirmed
    FROM public.ride_ledger_postings p
    JOIN public.ride_ledger_transactions t ON t.id = p.transaction_id
    WHERE p.account_owner_id = v_actor
      AND (p_from IS NULL OR p.created_at >= p_from)

    UNION ALL

    -- Marketplace ledger covers repair, workshops, parts and Elysium services.
    SELECT
      'marketplace:' || m.ledger_entry_id,
      to_timestamp(m.created_at_ms / 1000.0),
      CASE
        WHEN m.order_type ILIKE '%PART%' THEN 'REPUESTOS'
        WHEN m.order_type ILIKE '%WORKSHOP%' OR m.order_type ILIKE '%REPAIR%' THEN 'TALLER_MECANICA'
        WHEN m.order_type ILIKE '%TOW%' THEN 'GRUAS'
        ELSE 'SERVICIOS_ELYSIUM'
      END AS activity_type,
      replace(m.entry_type, '_', ' ') AS title,
      m.order_id AS reference_id,
      CASE WHEN upper(m.direction) = 'CREDIT' THEN 'INGRESO' ELSE 'GASTO' END AS flow,
      m.amount_cents AS amount_minor,
      m.currency,
      upper(m.status) AS status,
      'MARKETPLACE_LEDGER' AS source,
      lower(m.status) IN ('settled', 'paid', 'completed', 'captured', 'posted', 'released') AS is_confirmed
    FROM public.marketplace_ledger_entries m
    WHERE m.participant_id IN (v_actor::text, coalesce(v_profile::text, ''))
      AND (p_from IS NULL OR to_timestamp(m.created_at_ms / 1000.0) >= p_from)
  )
  SELECT *
  FROM entries
  ORDER BY occurred_at DESC, entry_id DESC
  LIMIT v_limit;
END;
$$;

REVOKE ALL ON FUNCTION public.personal_financial_activity_v1(timestamptz, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.personal_financial_activity_v1(timestamptz, integer) TO authenticated;

COMMENT ON FUNCTION public.personal_financial_activity_v1(timestamptz, integer) IS
  'Authenticated personal-only financial history. Rows preserve source and confirmation state; totals must exclude is_confirmed=false.';
