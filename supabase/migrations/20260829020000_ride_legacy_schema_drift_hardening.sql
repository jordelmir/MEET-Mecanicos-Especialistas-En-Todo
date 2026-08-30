-- Repair three legacy Viajes RPCs that drifted from the authoritative schema.
-- The replacements preserve the v1 API surface while delegating assignment to
-- the versioned command kernel and refusing synthetic financial truth.

create table if not exists public.ride_trip_feedback (
    trip_id uuid primary key references public.ride_requests(id) on delete restrict,
    driver_id uuid not null references public.ride_profiles(user_id) on delete restrict,
    passenger_id uuid not null references auth.users(id) on delete restrict,
    rating smallint not null check (rating between 1 and 5),
    created_at timestamptz not null default now()
);

alter table public.ride_trip_feedback enable row level security;
revoke all on public.ride_trip_feedback from public, anon, authenticated;

create unique index if not exists ride_payment_intents_one_per_trip_idx
    on public.ride_payment_intents(trip_id);

create or replace function public.ride_record_trip_feedback_v1(
    p_trip_id uuid,
    p_rating smallint,
    p_compliments text[] default '{}'
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_caller_id uuid := (select auth.uid());
    v_request public.ride_requests%rowtype;
    v_profile public.ride_driver_public_profiles%rowtype;
    v_comp text;
    v_inserted integer := 0;
    v_new_rating_count integer;
    v_raw_avg numeric(3,2);
    v_bayesian numeric(3,2);
    v_total_completed integer;
    v_trust_tier text;
    v_confidence numeric(4,3);
    v_prior_c numeric := 10.0;
    v_prior_m numeric := 4.80;
begin
    if v_caller_id is null then
        raise exception using errcode = '42501', message = 'AUTHENTICATION_REQUIRED';
    end if;
    if p_rating is null or p_rating < 1 or p_rating > 5 then
        raise exception using errcode = '22023', message = 'RATING_OUT_OF_RANGE';
    end if;
    if exists (
        select 1
        from unnest(coalesce(p_compliments, '{}'::text[])) as c(value)
        where c.value not in (
            'COURTEOUS', 'SAFE_DRIVING', 'FAST_PICKUP', 'CLEAN_VEHICLE',
            'GOOD_COMMUNICATION', 'GOOD_NAVIGATION', 'HELPFUL', 'PROFESSIONAL'
        )
    ) then
        raise exception using errcode = '22023', message = 'INVALID_COMPLIMENT';
    end if;

    select r.*
      into v_request
      from public.ride_requests r
     where r.id = p_trip_id
       and r.passenger_id = v_caller_id
       and r.state = 'COMPLETED'
       and r.assigned_driver_id is not null
     for update;
    if not found then
        raise exception using errcode = '42501', message = 'TRIP_NOT_ELIGIBLE_FOR_FEEDBACK';
    end if;

    insert into public.ride_trip_feedback(trip_id, driver_id, passenger_id, rating)
    values (p_trip_id, v_request.assigned_driver_id, v_caller_id, p_rating)
    on conflict (trip_id) do nothing;
    get diagnostics v_inserted = row_count;

    if v_inserted = 0 then
        if not exists (
            select 1 from public.ride_trip_feedback f
            where f.trip_id = p_trip_id
              and f.passenger_id = v_caller_id
              and f.rating = p_rating
        ) then
            raise exception using errcode = '23505', message = 'FEEDBACK_ALREADY_RECORDED';
        end if;
        select p.* into v_profile
          from public.ride_driver_public_profiles p
         where p.driver_id = v_request.assigned_driver_id;
        return jsonb_build_object(
            'success', true,
            'already_recorded', true,
            'driver_id', v_request.assigned_driver_id,
            'bayesian_rating', v_profile.bayesian_rating,
            'rating_count', v_profile.rating_count,
            'trust_tier', v_profile.trust_tier
        );
    end if;

    insert into public.ride_driver_public_profiles(driver_id, display_name)
    select rp.user_id, rp.display_name
      from public.ride_profiles rp
     where rp.user_id = v_request.assigned_driver_id
    on conflict (driver_id) do nothing;

    select p.* into strict v_profile
      from public.ride_driver_public_profiles p
     where p.driver_id = v_request.assigned_driver_id
     for update;

    v_new_rating_count := v_profile.rating_count + 1;
    v_total_completed := v_profile.total_trips + 1;
    v_bayesian := round((
        v_prior_c * v_prior_m
        + (v_profile.rating_count * coalesce(v_profile.bayesian_rating, v_prior_m))
        + p_rating
    ) / (v_prior_c + v_new_rating_count), 2);
    v_trust_tier := case
        when v_total_completed >= 500 and v_bayesian >= 4.90 then 'VANGUARD'
        when v_total_completed >= 100 and v_bayesian >= 4.80 then 'ELITE'
        when v_total_completed >= 20 and v_bayesian >= 4.50 then 'TRUSTED'
        else 'VERIFIED'
    end;
    v_confidence := round((1.0 - exp(-v_new_rating_count::numeric / 50.0))::numeric, 3);

    update public.ride_driver_public_profiles
       set rating_count = v_new_rating_count,
           total_trips = v_total_completed,
           bayesian_rating = v_bayesian,
           trust_tier = v_trust_tier,
           updated_at = now()
     where driver_id = v_request.assigned_driver_id;

    foreach v_comp in array coalesce(p_compliments, '{}'::text[])
    loop
        insert into public.ride_driver_compliments(
            driver_id, trip_id, passenger_id, compliment
        ) values (
            v_request.assigned_driver_id, p_trip_id, v_caller_id, v_comp
        ) on conflict (trip_id, compliment) do nothing;
    end loop;

    select round(avg(f.rating)::numeric, 2)
      into v_raw_avg
      from public.ride_trip_feedback f
     where f.driver_id = v_request.assigned_driver_id;

    insert into public.ride_driver_reputation_snapshots(
        driver_id, bayesian_rating, raw_average_rating, rating_count,
        completed_trips, trust_tier, confidence_score
    ) values (
        v_request.assigned_driver_id, v_bayesian, v_raw_avg,
        v_new_rating_count, v_total_completed, v_trust_tier, v_confidence
    );

    return jsonb_build_object(
        'success', true,
        'already_recorded', false,
        'driver_id', v_request.assigned_driver_id,
        'bayesian_rating', v_bayesian,
        'rating_count', v_new_rating_count,
        'trust_tier', v_trust_tier
    );
end;
$$;

create or replace function public.ride_try_auto_match_v1(
    p_request_id uuid,
    p_expected_version bigint
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_caller_id uuid := (select auth.uid());
    v_request public.ride_requests%rowtype;
    v_policy public.ride_auto_match_policies%rowtype;
    v_best_offer record;
    v_tier_rank integer;
    v_command jsonb;
    v_idempotency_key text;
begin
    if v_caller_id is null then
        raise exception using errcode = '42501', message = 'AUTHENTICATION_REQUIRED';
    end if;
    if coalesce(p_expected_version, 0) <= 0 then
        raise exception using errcode = '22023', message = 'EXPECTED_VERSION_REQUIRED';
    end if;

    select r.* into v_request
      from public.ride_requests r
     where r.id = p_request_id
       and r.passenger_id = v_caller_id
     for update;
    if not found then
        return jsonb_build_object('matched', false, 'reason', 'REQUEST_NOT_FOUND');
    end if;
    if v_request.state not in ('SEARCHING', 'OFFERED') then
        return jsonb_build_object(
            'matched', false, 'reason', 'INVALID_STATE', 'state', v_request.state
        );
    end if;
    if v_request.version <> p_expected_version then
        return jsonb_build_object(
            'matched', false, 'reason', 'VERSION_MISMATCH',
            'current_version', v_request.version
        );
    end if;

    select p.* into v_policy
      from public.ride_auto_match_policies p
     where p.request_id = p_request_id and p.enabled;
    if not found then
        return jsonb_build_object('matched', false, 'reason', 'POLICY_DISABLED_OR_NOT_FOUND');
    end if;

    v_tier_rank := case v_policy.minimum_trust_tier
        when 'VANGUARD' then 4 when 'ELITE' then 3 when 'TRUSTED' then 2 else 1
    end;

    select
        o.id as offer_id,
        o.driver_id,
        o.vehicle_id,
        o.fare_minor,
        o.eta_seconds,
        p.trust_tier,
        p.bayesian_rating,
        p.total_trips
      into v_best_offer
      from public.ride_offers o
      join public.ride_driver_public_profiles p on p.driver_id = o.driver_id
      join public.ride_driver_presence pr on pr.driver_id = o.driver_id
     where o.request_id = p_request_id
       and o.state = 'PENDING'
       and o.fare_minor <= v_policy.max_fare_minor
       and o.eta_seconds is not null
       and o.eta_seconds <= v_policy.maximum_eta_seconds
       and case p.trust_tier
            when 'VANGUARD' then 4 when 'ELITE' then 3
            when 'TRUSTED' then 2 else 1
           end >= v_tier_rank
       and (
           pr.availability = 'AVAILABLE'
           or (
               v_policy.allow_finishing_previous_trip
               and pr.availability = 'FINISHING_CURRENT_TRIP'
           )
       )
     order by
        case when v_policy.strategy = 'FASTEST_PICKUP' then o.eta_seconds end asc nulls last,
        case when v_policy.strategy = 'LOWEST_FARE' then o.fare_minor end asc nulls last,
        case when v_policy.strategy = 'HIGHEST_TRUST' then
            case p.trust_tier
                when 'VANGUARD' then 4 when 'ELITE' then 3
                when 'TRUSTED' then 2 else 1
            end
        end desc nulls last,
        case when v_policy.strategy in ('HIGHEST_TRUST', 'BALANCED')
             then p.bayesian_rating end desc nulls last,
        case when v_policy.strategy = 'BALANCED' then o.eta_seconds end asc nulls last,
        case when v_policy.strategy = 'BALANCED' then o.fare_minor end asc nulls last,
        o.created_at asc
     limit 1
     for update of o;

    if not found then
        return jsonb_build_object('matched', false, 'reason', 'NO_ELIGIBLE_OFFER');
    end if;

    v_idempotency_key := 'auto-match:' || p_request_id::text || ':' || p_expected_version::text;
    v_command := public.ride_accept_offer_v2(
        p_request_id,
        v_best_offer.offer_id,
        p_expected_version,
        v_idempotency_key
    );
    if not coalesce((v_command ->> 'ok')::boolean, false) then
        return jsonb_build_object(
            'matched', false,
            'reason', coalesce(v_command #>> '{error,code}', 'COMMAND_REJECTED'),
            'command', v_command
        );
    end if;

    update public.ride_driver_presence
       set availability = 'EN_ROUTE_TO_PICKUP',
           current_trip_id = p_request_id,
           updated_at = now()
     where driver_id = v_best_offer.driver_id;

    return jsonb_build_object(
        'matched', true,
        'request_id', p_request_id,
        'assigned_driver_id', v_best_offer.driver_id,
        'assigned_vehicle_id', v_best_offer.vehicle_id,
        'fare_minor', v_best_offer.fare_minor,
        'strategy_applied', v_policy.strategy,
        'new_version', (v_command #>> '{data,version}')::bigint,
        'command', v_command
    );
end;
$$;

create or replace function public.ride_attest_payment_event_v1(
    p_trip_id uuid,
    p_new_status text,
    p_reference_number text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_caller_id uuid := (select auth.uid());
    v_request public.ride_requests%rowtype;
    v_intent public.ride_payment_intents%rowtype;
    v_role text;
    v_amount_minor bigint;
    v_created boolean := false;
begin
    if v_caller_id is null then
        raise exception using errcode = '42501', message = 'AUTHENTICATION_REQUIRED';
    end if;
    if p_new_status not in (
        'PAYMENT_METHOD_SELECTED', 'PAYMENT_REQUESTED', 'USER_MARKED_SENT',
        'DRIVER_MARKED_RECEIVED', 'EXTERNAL_SETTLEMENT_ATTESTED',
        'BANK_CONFIRMED', 'DISPUTED'
    ) then
        raise exception using errcode = '22023', message = 'INVALID_PAYMENT_STATUS';
    end if;
    if p_new_status = 'BANK_CONFIRMED' then
        raise exception using
            errcode = '42501', message = 'BANK_CONFIRMATION_REQUIRES_TRUSTED_INGESTION';
    end if;
    if char_length(coalesce(p_reference_number, '')) > 160 then
        raise exception using errcode = '22023', message = 'REFERENCE_TOO_LONG';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('ride-payment:' || p_trip_id::text, 0)
    );
    select r.* into v_request
      from public.ride_requests r
     where r.id = p_trip_id
     for update;
    if not found or v_request.assigned_driver_id is null or (
        v_caller_id <> v_request.passenger_id
        and v_caller_id <> v_request.assigned_driver_id
    ) then
        raise exception using errcode = '42501', message = 'TRIP_PARTICIPANT_REQUIRED';
    end if;
    v_role := case
        when v_caller_id = v_request.passenger_id then 'PASSENGER'
        else 'DRIVER'
    end;
    if (p_new_status in ('PAYMENT_METHOD_SELECTED', 'USER_MARKED_SENT') and v_role <> 'PASSENGER')
       or (p_new_status in ('PAYMENT_REQUESTED', 'DRIVER_MARKED_RECEIVED') and v_role <> 'DRIVER')
    then
        raise exception using errcode = '42501', message = 'PAYMENT_ROLE_TRANSITION_DENIED';
    end if;

    select i.* into v_intent
      from public.ride_payment_intents i
     where i.trip_id = p_trip_id
     for update;
    if not found then
        if p_new_status not in ('PAYMENT_METHOD_SELECTED', 'PAYMENT_REQUESTED') then
            raise exception using errcode = '22023', message = 'PAYMENT_INTENT_REQUIRED';
        end if;
        v_amount_minor := coalesce(v_request.final_fare_minor, v_request.offered_fare_minor);
        if v_amount_minor is null or v_amount_minor <= 0 then
            raise exception using errcode = '22023', message = 'AUTHORITATIVE_FARE_REQUIRED';
        end if;
        insert into public.ride_payment_intents(
            trip_id, passenger_id, driver_id, amount_minor, currency,
            payment_method, status, sinpe_reference_number
        ) values (
            p_trip_id, v_request.passenger_id, v_request.assigned_driver_id,
            v_amount_minor, v_request.currency, 'SINPE_MOVIL', p_new_status,
            nullif(trim(p_reference_number), '')
        ) returning * into v_intent;
        v_created := true;
    else
        if v_intent.passenger_id <> v_request.passenger_id
           or v_intent.driver_id <> v_request.assigned_driver_id
        then
            raise exception using errcode = '23514', message = 'PAYMENT_PARTICIPANT_MISMATCH';
        end if;
        if v_intent.status = p_new_status then
            return jsonb_build_object(
                'success', true, 'already_recorded', true,
                'payment_intent_id', v_intent.id, 'status', v_intent.status
            );
        end if;
        if not (
            (v_intent.status = 'PAYMENT_METHOD_SELECTED' and p_new_status in ('PAYMENT_REQUESTED', 'USER_MARKED_SENT', 'DISPUTED'))
            or (v_intent.status = 'PAYMENT_REQUESTED' and p_new_status in ('USER_MARKED_SENT', 'DISPUTED'))
            or (v_intent.status = 'USER_MARKED_SENT' and p_new_status in ('DRIVER_MARKED_RECEIVED', 'DISPUTED'))
            or (v_intent.status = 'DRIVER_MARKED_RECEIVED' and p_new_status in ('EXTERNAL_SETTLEMENT_ATTESTED', 'DISPUTED'))
            or (v_intent.status = 'EXTERNAL_SETTLEMENT_ATTESTED' and p_new_status = 'DISPUTED')
        ) then
            raise exception using errcode = '22023', message = 'INVALID_PAYMENT_TRANSITION';
        end if;
        update public.ride_payment_intents
           set status = p_new_status,
               sinpe_reference_number = coalesce(
                   nullif(trim(p_reference_number), ''), sinpe_reference_number
               ),
               updated_at = now()
         where id = v_intent.id
         returning * into v_intent;
    end if;

    insert into public.ride_payment_events(
        payment_intent_id, actor_id, actor_role, event_type, evidence_payload
    ) values (
        v_intent.id, v_caller_id, v_role, p_new_status,
        jsonb_build_object(
            'reference_supplied', nullif(trim(p_reference_number), '') is not null,
            'attested_at', now()
        )
    );

    return jsonb_build_object(
        'success', true,
        'already_recorded', false,
        'created', v_created,
        'payment_intent_id', v_intent.id,
        'status', v_intent.status,
        'amount_minor', v_intent.amount_minor,
        'currency', v_intent.currency,
        'proof_level', case
            when v_intent.status = 'EXTERNAL_SETTLEMENT_ATTESTED' then 'PARTICIPANT_ATTESTED'
            else 'USER_DECLARED'
        end
    );
end;
$$;

revoke all on function public.ride_record_trip_feedback_v1(uuid, smallint, text[]) from public, anon;
revoke all on function public.ride_try_auto_match_v1(uuid, bigint) from public, anon;
revoke all on function public.ride_attest_payment_event_v1(uuid, text, text) from public, anon;
grant execute on function public.ride_record_trip_feedback_v1(uuid, smallint, text[]) to authenticated;
grant execute on function public.ride_try_auto_match_v1(uuid, bigint) to authenticated;
grant execute on function public.ride_attest_payment_event_v1(uuid, text, text) to authenticated;

comment on function public.ride_attest_payment_event_v1(uuid, text, text) is
'Records participant attestations only. BANK_CONFIRMED requires a separate trusted bank ingestion path.';
