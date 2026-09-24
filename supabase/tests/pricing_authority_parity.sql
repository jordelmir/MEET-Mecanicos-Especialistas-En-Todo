-- Fail closed when the effective database state diverges from the canonical
-- CRC pricing fixture enforced by TS and Kotlin.
DO $$
DECLARE
    v_policy public.mobility_pricing_policies%ROWTYPE;
    v_active_count BIGINT;
    v_create_request_definition TEXT;
BEGIN
    SELECT count(*)
      INTO v_active_count
      FROM public.mobility_pricing_policies
     WHERE market_id = 'CR_GAM'
       AND service_category_id = 'STD_RIDE'
       AND active = TRUE
       AND (valid_until IS NULL OR valid_until > clock_timestamp());

    IF v_active_count <> 1 THEN
        RAISE EXCEPTION 'PRICING_PARITY_ACTIVE_POLICY_COUNT expected=1 actual=%', v_active_count;
    END IF;

    SELECT *
      INTO STRICT v_policy
      FROM public.mobility_pricing_policies
     WHERE market_id = 'CR_GAM'
       AND service_category_id = 'STD_RIDE'
       AND active = TRUE
       AND (valid_until IS NULL OR valid_until > clock_timestamp());

    IF v_policy.version <> 2
       OR v_policy.currency_code <> 'CRC'
       OR v_policy.base_fare_minor <> 0
       OR v_policy.per_meter_numerator <> 300
       OR v_policy.per_meter_denominator <> 1000
       OR v_policy.per_second_numerator <> 60
       OR v_policy.per_second_denominator <> 60
       OR v_policy.minimum_fare_minor <> 0
       OR v_policy.booking_fee_minor <> 0
       OR v_policy.tax_basis_points <> 0
    THEN
        RAISE EXCEPTION 'PRICING_PARITY_ACTIVE_POLICY_MISMATCH policy=%', row_to_json(v_policy);
    END IF;

    SELECT pg_get_functiondef(
        'public.ride_create_request_v3(uuid,text,text,double precision,double precision,text,double precision,double precision,text,bigint,text,text,jsonb,text,bigint,bigint,bigint,bigint,bigint,boolean,text,jsonb)'::regprocedure
    ) INTO v_create_request_definition;

    IF v_create_request_definition !~ 'p_distance_rate_minor_per_km[^;]+<> 300'
       OR v_create_request_definition !~ 'p_time_rate_minor_per_minute[^;]+<> 60'
    THEN
        RAISE EXCEPTION 'PRICING_PARITY_RIDE_CREATE_REQUEST_V3_MISMATCH';
    END IF;
END;
$$;
