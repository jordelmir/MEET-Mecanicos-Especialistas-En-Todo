create or replace function public.ride_driver_decide_request_v1(
    p_trip_id uuid,
    p_action text,
    p_idempotency_key uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor uuid := (select auth.uid());
    v_trip public.ride_requests%rowtype;
    v_existing public.ride_driver_request_decisions%rowtype;
    v_eligible boolean;
begin
    if v_actor is null then
        raise exception using errcode = '42501', message = 'UNAUTHENTICATED';
    end if;
    if p_idempotency_key is null then
        raise exception using errcode = '22023', message = 'IDEMPOTENCY_KEY_REQUIRED';
    end if;
    if p_action not in ('DISMISS', 'REJECT') then
        raise exception using errcode = '22023', message = 'INVALID_DRIVER_DECISION';
    end if;

    select * into v_existing
      from public.ride_driver_request_decisions
     where idempotency_key = p_idempotency_key;
    if found then
        if v_existing.driver_id is distinct from v_actor or
           v_existing.trip_id is distinct from p_trip_id or
           v_existing.action is distinct from p_action then
            raise exception using errcode = '22023', message = 'IDEMPOTENCY_CONFLICT';
        end if;
        return jsonb_build_object('ok', true, 'action', v_existing.action, 'idempotent', true);
    end if;

    select * into v_trip from public.ride_requests where id = p_trip_id for update;
    if not found then
        raise exception using errcode = 'P0002', message = 'RIDE_NOT_FOUND';
    end if;
    if v_trip.passenger_id = v_actor then
        raise exception using errcode = '42501', message = 'SELF_RIDE_FORBIDDEN';
    end if;
    if v_trip.state not in ('SEARCHING', 'OFFERED') or
       v_trip.dispatch_expires_at <= clock_timestamp() then
        raise exception using errcode = '22023', message = 'RIDE_NOT_OPEN';
    end if;
    select exists (
        select 1 from public.ride_driver_vehicles v
         where v.driver_id = v_actor
           and public.ride_vehicle_dispatch_eligible(v.id, v_actor)
    ) into v_eligible;
    if not v_eligible then
        raise exception using errcode = '42501', message = 'DRIVER_NOT_DISPATCH_ELIGIBLE';
    end if;

    insert into public.ride_driver_request_decisions(
        trip_id, driver_id, action, idempotency_key
    ) values (p_trip_id, v_actor, p_action, p_idempotency_key)
    on conflict (trip_id, driver_id) do update
       set action = excluded.action,
           idempotency_key = excluded.idempotency_key,
           updated_at = clock_timestamp();

    update public.ride_trusted_driver_invites
       set state = case when p_action = 'REJECT' then 'DECLINED' else 'SEEN' end,
           updated_at = clock_timestamp()
     where trip_id = p_trip_id
       and driver_id = v_actor
       and state in ('PENDING', 'SEEN');

    if p_action = 'REJECT' then
        update public.ride_requests
           set driver_rejection_count = driver_rejection_count + 1,
               updated_at = clock_timestamp()
         where id = p_trip_id;
        insert into public.ride_trip_events(
            trip_id, actor_id, event_type, from_state, to_state, payload, idempotency_key
        ) values (
            p_trip_id, v_actor, 'DRIVER_REJECTED_REQUEST', v_trip.state, v_trip.state,
            jsonb_build_object('driver_rejection_count', v_trip.driver_rejection_count + 1),
            'driver-reject:' || p_idempotency_key::text
        ) on conflict (idempotency_key) do nothing;
        insert into public.ride_push_outbox(
            recipient_id, trip_id, notification_type, title, body, dedupe_key
        ) values (
            v_trip.passenger_id, p_trip_id, 'DRIVER_REJECTED',
            'Un chofer rechazó tu oferta',
            'Tu solicitud sigue activa para otros choferes.',
            'driver-rejected:' || p_trip_id::text || ':' || v_actor::text
        ) on conflict (dedupe_key) do nothing;
    end if;

    return jsonb_build_object('ok', true, 'action', p_action, 'idempotent', false);
end;
$$;

revoke all on function public.ride_driver_decide_request_v1(uuid, text, uuid) from public, anon;
grant execute on function public.ride_driver_decide_request_v1(uuid, text, uuid) to authenticated, service_role;
