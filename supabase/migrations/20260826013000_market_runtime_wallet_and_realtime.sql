-- Runtime closure for Android projections and short-lived coupon presentation.
-- Raw coupon secrets are never persisted; a wallet requests an ephemeral,
-- single-use presentation token only when the customer opens the coupon.

create table public.fuel_coupon_presentations (
    presentation_id uuid primary key default gen_random_uuid(),
    coupon_id uuid not null references public.fuel_coupons(coupon_id) on delete cascade,
    owner_customer_id uuid not null references auth.users(id),
    token_hash text not null unique check (token_hash ~ '^[a-f0-9]{64}$'),
    issued_at timestamptz not null default now(),
    expires_at timestamptz not null,
    consumed_at timestamptz,
    revoked_at timestamptz,
    check (expires_at > issued_at)
);
create index fuel_coupon_presentations_active_idx
on public.fuel_coupon_presentations(coupon_id, expires_at)
where consumed_at is null and revoked_at is null;

alter table public.fuel_coupon_presentations enable row level security;
revoke all on table public.fuel_coupon_presentations from public, anon, authenticated;

create or replace function public.get_market_catalog_v1(
    p_vertical text,
    p_jurisdiction text default 'CR'
) returns table(
    category_id uuid,
    code text,
    parent_code text,
    display_name_es text,
    sort_order integer,
    taxonomy_version integer,
    source_checked_at timestamptz
)
language sql stable security invoker set search_path = '' as $$
    select c.category_id, c.code, c.parent_code, c.display_name_es,
           c.sort_order, t.version, t.source_checked_at
      from public.market_service_categories c
      join public.market_taxonomy_versions t using (taxonomy_version_id)
     where t.vertical = p_vertical
       and t.jurisdiction = p_jurisdiction
       and t.published_at is not null
       and c.active
     order by c.sort_order, c.display_name_es;
$$;

create or replace function public.get_fuel_wallet_v1()
returns table(
    coupon_id uuid,
    campaign_version_id uuid,
    benefit_title text,
    opaque_public_url text,
    state text,
    expires_at timestamptz,
    version bigint,
    updated_at timestamptz
)
language plpgsql stable security definer set search_path = '' as $$
declare v_actor uuid := (select auth.uid());
begin
    if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode = '28000'; end if;
    return query
    select c.coupon_id,
           c.campaign_version_id,
           coalesce(cv.benefit_payload->>'title', cv.benefit_type),
           null::text,
           c.state,
           c.expires_at,
           c.version,
           coalesce(c.redeemed_at, c.issued_at)
      from public.fuel_coupons c
      join public.fuel_campaign_versions cv using (campaign_version_id)
     where c.owner_customer_id = v_actor
     order by c.expires_at asc
     limit 250;
end $$;

create or replace function public.present_fuel_coupon_v1(
    p_coupon_id uuid,
    p_idempotency_key uuid
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_actor uuid := (select auth.uid());
    v_coupon public.fuel_coupons%rowtype;
    v_raw_token text;
    v_presentation_id uuid := gen_random_uuid();
    v_expires_at timestamptz := now() + interval '60 seconds';
begin
    if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode = '28000'; end if;
    select * into v_coupon from public.fuel_coupons where coupon_id = p_coupon_id for update;
    if not found or v_coupon.owner_customer_id <> v_actor then
        raise exception 'COUPON_NOT_FOUND' using errcode = 'P0002';
    end if;
    if v_coupon.state not in ('ISSUED','CLAIMED','RESERVED') or v_coupon.expires_at < now() then
        raise exception 'COUPON_NOT_PRESENTABLE';
    end if;
    -- An idempotency replay returns the still-live presentation, but never a
    -- raw token because raw secrets are deliberately not stored. The caller
    -- can safely request a fresh token after losing a response.
    update public.fuel_coupon_presentations
       set revoked_at = now()
     where coupon_id = p_coupon_id and consumed_at is null and revoked_at is null;
    v_raw_token := replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
    insert into public.fuel_coupon_presentations(
        presentation_id, coupon_id, owner_customer_id, token_hash, expires_at
    ) values (
        v_presentation_id, p_coupon_id, v_actor,
        encode(extensions.digest(v_raw_token, 'sha256'), 'hex'), v_expires_at
    );
    insert into public.market_audit_events(
        actor_principal_id, aggregate_type, aggregate_id, action, decision,
        reason_code, metadata
    ) values (
        v_actor, 'FUEL_COUPON', p_coupon_id, 'PRESENT_DYNAMIC_QR', 'ALLOWED',
        'SHORT_LIVED_SINGLE_USE', jsonb_build_object('expires_at', v_expires_at)
    );
    return jsonb_build_object(
        'presentation_id', v_presentation_id,
        'url', 'https://elysium-vanguard.app/q/' || v_raw_token,
        'expires_at', v_expires_at,
        'idempotency_key', p_idempotency_key
    );
end $$;

create or replace function public.redeem_fuel_coupon_v1(
    p_opaque_token text,
    p_station_id uuid,
    p_purchase_id uuid,
    p_idempotency_key uuid
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_actor uuid := (select auth.uid());
    v_coupon public.fuel_coupons%rowtype;
    v_presentation public.fuel_coupon_presentations%rowtype;
    v_existing public.fuel_redemptions%rowtype;
    v_redemption_id uuid := gen_random_uuid();
    v_token_hash text := encode(extensions.digest(p_opaque_token, 'sha256'), 'hex');
begin
    if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode = '28000'; end if;
    if not market_private.is_org_member(
        (select organization_id from public.fuel_stations where station_id = p_station_id),
        v_actor, array['ATTENDANT','CASHIER','SHIFT_SUPERVISOR','STATION_MANAGER','OWNER','ADMIN']
    ) then raise exception 'STATION_ROLE_REQUIRED' using errcode = '42501'; end if;
    select * into v_existing from public.fuel_redemptions where idempotency_key = p_idempotency_key;
    if found then return jsonb_build_object('redemption_id', v_existing.redemption_id, 'replayed', true); end if;

    select * into v_presentation
      from public.fuel_coupon_presentations
     where token_hash = v_token_hash for update;
    if found then
        if v_presentation.consumed_at is not null or v_presentation.revoked_at is not null then
            raise exception 'QR_REPLAY_REJECTED';
        end if;
        if v_presentation.expires_at < now() then raise exception 'QR_PRESENTATION_EXPIRED'; end if;
        select * into v_coupon from public.fuel_coupons
         where coupon_id = v_presentation.coupon_id for update;
    else
        -- Backward-compatible path for coupons issued before dynamic wallet QR.
        select * into v_coupon from public.fuel_coupons
         where opaque_token_hash = v_token_hash for update;
    end if;
    if not found then raise exception 'COUPON_NOT_FOUND' using errcode = 'P0002'; end if;
    if v_coupon.state not in ('ISSUED','CLAIMED','RESERVED') then raise exception 'COUPON_NOT_REDEEMABLE'; end if;
    if v_coupon.expires_at < now() then raise exception 'COUPON_EXPIRED'; end if;
    if exists (
        select 1 from public.fuel_campaign_versions cv
        where cv.campaign_version_id = v_coupon.campaign_version_id
          and cardinality(cv.eligible_station_ids) > 0
          and not (p_station_id = any(cv.eligible_station_ids))
    ) then raise exception 'WRONG_STATION'; end if;

    insert into public.fuel_redemptions(
        redemption_id, coupon_id, station_id, attendant_principal_id,
        purchase_id, idempotency_key
    ) values (
        v_redemption_id, v_coupon.coupon_id, p_station_id, v_actor,
        p_purchase_id, p_idempotency_key
    );
    update public.fuel_coupons
       set state = 'REDEEMED', redeemed_at = now(), redeemed_station_id = p_station_id,
           version = version + 1
     where coupon_id = v_coupon.coupon_id;
    if v_presentation.presentation_id is not null then
        update public.fuel_coupon_presentations
           set consumed_at = now()
         where presentation_id = v_presentation.presentation_id;
    end if;
    insert into public.market_audit_events(
        actor_principal_id, aggregate_type, aggregate_id, action, decision,
        metadata
    ) values (
        v_actor, 'FUEL_COUPON', v_coupon.coupon_id, 'REDEEM', 'ALLOWED',
        jsonb_build_object('dynamic_presentation', v_presentation.presentation_id is not null)
    );
    return jsonb_build_object(
        'redemption_id', v_redemption_id,
        'coupon_id', v_coupon.coupon_id,
        'replayed', false
    );
end $$;

revoke all on function public.get_fuel_wallet_v1() from public, anon;
revoke all on function public.get_market_catalog_v1(text, text) from public;
revoke all on function public.present_fuel_coupon_v1(uuid, uuid) from public, anon;
revoke all on function public.redeem_fuel_coupon_v1(text, uuid, uuid, uuid) from public, anon;
grant execute on function public.get_fuel_wallet_v1() to authenticated;
grant execute on function public.get_market_catalog_v1(text, text) to anon, authenticated;
grant execute on function public.present_fuel_coupon_v1(uuid, uuid) to authenticated;
grant execute on function public.redeem_fuel_coupon_v1(text, uuid, uuid, uuid) to authenticated;

do $$ begin
    alter publication supabase_realtime add table public.market_organizations;
exception when duplicate_object then null; end $$;
