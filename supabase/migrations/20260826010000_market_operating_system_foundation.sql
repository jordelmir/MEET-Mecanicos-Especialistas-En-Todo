-- Elysium Vanguard Market Operating System: shared authority + Legal + Property + Fuel.
-- PostgreSQL owns globally observable lifecycle truth. Clients read projections and
-- mutate only through versioned, idempotent RPCs deriving the actor from auth.uid().

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;
create schema if not exists market_private;
revoke all on schema market_private from public, anon, authenticated;

create table public.market_organizations (
    organization_id uuid primary key default gen_random_uuid(),
    legal_name text not null check (length(trim(legal_name)) between 2 and 200),
    commercial_name text not null check (length(trim(commercial_name)) between 2 and 160),
    kind text not null check (kind in ('SOLO_PROFESSIONAL','LAW_FIRM','BROKERAGE','LANDLORD','FUEL_NETWORK','FUEL_STATION')),
    jurisdiction text not null check (jurisdiction ~ '^[A-Z]{2}(-[A-Z0-9]{1,3})?$'),
    status text not null default 'ACTIVE' check (status in ('PENDING','ACTIVE','SUSPENDED','CLOSED')),
    version bigint not null default 0 check (version >= 0),
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.market_organization_members (
    membership_id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.market_organizations(organization_id) on delete cascade,
    principal_id uuid not null references auth.users(id),
    roles text[] not null check (cardinality(roles) > 0),
    valid_from timestamptz not null default now(),
    valid_until timestamptz,
    revoked_at timestamptz,
    version bigint not null default 0,
    unique (organization_id, principal_id)
);
create index market_members_principal_idx on public.market_organization_members(principal_id, organization_id);

create table public.market_professional_credentials (
    credential_id uuid primary key default gen_random_uuid(),
    principal_id uuid not null references auth.users(id),
    organization_id uuid references public.market_organizations(organization_id),
    authority text not null,
    credential_type text not null,
    identifier_ciphertext text not null,
    identifier_masked text not null,
    status text not null check (status in ('UNVERIFIED','PENDING','ACTIVE','SUSPENDED','EXPIRED','REVOKED','NOT_APPLICABLE')),
    checked_at timestamptz,
    expires_at timestamptz,
    evidence_ref text,
    source_version text not null,
    checked_by uuid references auth.users(id),
    version bigint not null default 0,
    unique (principal_id, authority, credential_type)
);

create table public.market_taxonomy_versions (
    taxonomy_version_id uuid primary key default gen_random_uuid(),
    vertical text not null check (vertical in ('LEGAL','REAL_ESTATE','FUEL_REWARDS')),
    version integer not null check (version > 0),
    jurisdiction text not null,
    source_url text not null,
    source_checked_at timestamptz not null,
    published_at timestamptz,
    content_hash text not null check (content_hash ~ '^[a-f0-9]{64}$'),
    unique (vertical, jurisdiction, version)
);

create table public.market_service_categories (
    category_id uuid primary key default gen_random_uuid(),
    taxonomy_version_id uuid not null references public.market_taxonomy_versions(taxonomy_version_id),
    code text not null,
    parent_code text,
    display_name_es text not null,
    display_name_en text,
    sort_order integer not null default 0,
    active boolean not null default true,
    unique (taxonomy_version_id, code)
);

create table public.market_service_templates (
    service_template_id uuid primary key default gen_random_uuid(),
    category_id uuid not null references public.market_service_categories(category_id),
    code text not null,
    display_name_es text not null,
    allowed_fee_models text[] not null default '{}',
    requires_notary boolean not null default false,
    active boolean not null default true,
    unique (category_id, code)
);

create table public.market_commands (
    command_id uuid primary key,
    idempotency_key uuid not null unique,
    actor_principal_id uuid not null references auth.users(id),
    aggregate_type text not null,
    aggregate_id uuid not null,
    command_type text not null,
    expected_version bigint not null check (expected_version >= 0),
    canonical_digest text not null check (canonical_digest ~ '^[a-f0-9]{64}$'),
    payload jsonb not null,
    payload_version integer not null check (payload_version > 0),
    status text not null check (status in ('APPLIED','REJECTED')),
    result jsonb not null default '{}',
    created_at timestamptz not null default now()
);
create index market_commands_aggregate_idx on public.market_commands(aggregate_type, aggregate_id, created_at);

create table public.market_audit_events (
    audit_event_id uuid primary key default gen_random_uuid(),
    actor_principal_id uuid references auth.users(id),
    organization_id uuid references public.market_organizations(organization_id),
    aggregate_type text not null,
    aggregate_id uuid not null,
    action text not null,
    decision text not null,
    reason_code text,
    metadata jsonb not null default '{}',
    occurred_at timestamptz not null default now()
);
create index market_audit_aggregate_idx on public.market_audit_events(aggregate_type, aggregate_id, occurred_at desc);

-- LEGAL -----------------------------------------------------------------------
create table public.legal_professional_profiles (
    principal_id uuid primary key references auth.users(id),
    organization_id uuid references public.market_organizations(organization_id),
    public_display_name text not null,
    bar_identifier_masked text not null,
    declared_capabilities text[] not null default '{}',
    demonstrated_matter_count integer not null default 0 check (demonstrated_matter_count >= 0),
    accepting_matters boolean not null default false,
    version bigint not null default 0
);

create table public.legal_matters (
    matter_id uuid primary key default gen_random_uuid(),
    client_principal_id uuid not null references auth.users(id),
    category_code text not null,
    subcategory_code text,
    human_summary text not null check (length(trim(human_summary)) between 8 and 2000),
    privileged_detail_ciphertext text,
    jurisdiction_code text not null,
    urgency text not null check (urgency in ('NORMAL','URGENT','IMMEDIATE_RISK')),
    state text not null default 'CONFLICT_SCREENING' check (state in ('DRAFT','CONFLICT_SCREENING','MATCHING','OFFERED','ENGAGED','ACTIVE','COMPLETED','VOIDED')),
    disclosure_level text not null default 'TRIAGE_ONLY' check (disclosure_level in ('TRIAGE_ONLY','PARTY_NAMES_ONLY','CONFLICT_CLEARED','ENGAGED')),
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.legal_matter_parties (
    party_id uuid primary key default gen_random_uuid(),
    matter_id uuid not null references public.legal_matters(matter_id) on delete cascade,
    role text not null,
    display_name_ciphertext text not null,
    conflict_fingerprint text not null check (conflict_fingerprint ~ '^[a-f0-9]{64}$'),
    unique (matter_id, conflict_fingerprint)
);

create table public.legal_conflict_checks (
    conflict_check_id uuid primary key default gen_random_uuid(),
    matter_id uuid not null references public.legal_matters(matter_id) on delete cascade,
    professional_principal_id uuid not null references auth.users(id),
    organization_id uuid references public.market_organizations(organization_id),
    decision text not null check (decision in ('CLEAR','POSSIBLE_CONFLICT','CONFLICT')),
    notes_ciphertext text,
    checked_at timestamptz not null default now(),
    expires_at timestamptz not null,
    unique (matter_id, professional_principal_id)
);

create table public.legal_offers (
    offer_id uuid primary key default gen_random_uuid(),
    matter_id uuid not null references public.legal_matters(matter_id),
    organization_id uuid not null references public.market_organizations(organization_id),
    professional_principal_id uuid not null references auth.users(id),
    fee_model text not null check (fee_model in ('FIXED','HOURLY','PER_STAGE','RETAINER','TARIFF_BASED','QUOTE_AFTER_CONSULTATION')),
    fee_amount_minor bigint not null check (fee_amount_minor >= 0),
    external_expenses_minor bigint not null default 0 check (external_expenses_minor >= 0),
    currency char(3) not null check (currency in ('CRC','USD')),
    scope text not null,
    exclusions text not null,
    valid_until timestamptz not null,
    state text not null default 'SUBMITTED' check (state in ('SUBMITTED','ACCEPTED','REJECTED','EXPIRED','WITHDRAWN')),
    version bigint not null default 0
);

create table public.legal_engagements (
    engagement_id uuid primary key default gen_random_uuid(),
    matter_id uuid not null unique references public.legal_matters(matter_id),
    accepted_offer_id uuid not null unique references public.legal_offers(offer_id),
    client_principal_id uuid not null references auth.users(id),
    organization_id uuid not null references public.market_organizations(organization_id),
    professional_principal_id uuid not null references auth.users(id),
    state text not null default 'ACTIVE' check (state in ('ACTIVE','PAUSED','COMPLETED','TERMINATED')),
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    version bigint not null default 0
);

create table public.legal_documents (
    document_id uuid primary key default gen_random_uuid(),
    matter_id uuid not null references public.legal_matters(matter_id) on delete cascade,
    uploaded_by uuid not null references auth.users(id),
    classification text not null default 'LEGAL_PRIVILEGED' check (classification in ('LEGAL_PRIVILEGED','CLIENT_CONFIDENTIAL','PUBLIC_FILING')),
    storage_ref text not null,
    sha256 text not null check (sha256 ~ '^[a-f0-9]{64}$'),
    created_at timestamptz not null default now()
);

create table public.legal_deadlines (
    deadline_id uuid primary key default gen_random_uuid(),
    matter_id uuid not null references public.legal_matters(matter_id) on delete cascade,
    title text not null,
    due_at timestamptz not null,
    source text not null,
    status text not null default 'OPEN' check (status in ('OPEN','COMPLETED','CANCELLED')),
    version bigint not null default 0
);

-- PROPERTY --------------------------------------------------------------------
create table public.property_assets (
    property_id uuid primary key default gen_random_uuid(),
    created_by uuid not null references auth.users(id),
    registry_number_ciphertext text,
    registry_number_masked text,
    cadastral_plan_ciphertext text,
    cadastral_plan_masked text,
    property_type_code text not null,
    approximate_zone text not null,
    exact_address_ciphertext text,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.property_proofs (
    proof_id uuid primary key default gen_random_uuid(),
    property_id uuid not null references public.property_assets(property_id) on delete cascade,
    claim_key text not null,
    truth_state text not null check (truth_state in ('SELLER_DECLARED','DOCUMENT_OBSERVED','REGISTRY_VERIFIED','CADASTRAL_VERIFIED','MUNICIPAL_VERIFIED','NOTARIAL_VERIFIED','PHYSICALLY_INSPECTED','UNKNOWN')),
    authority text,
    evidence_ref text,
    observed_at timestamptz not null default now(),
    expires_at timestamptz,
    checked_by uuid references auth.users(id),
    version bigint not null default 0,
    unique (property_id, claim_key)
);

create table public.property_listings (
    listing_id uuid primary key default gen_random_uuid(),
    property_id uuid not null references public.property_assets(property_id),
    seller_principal_id uuid not null references auth.users(id),
    brokerage_organization_id uuid references public.market_organizations(organization_id),
    operation text not null check (operation in ('SALE','RENT','RENT_TO_OWN','TEMPORARY','PRESALE','ASSIGNMENT')),
    asking_amount_minor bigint not null check (asking_amount_minor >= 0),
    currency char(3) not null check (currency in ('CRC','USD')),
    public_description text not null,
    state text not null default 'DRAFT' check (state in ('DRAFT','COMPLIANCE_REVIEW','PUBLISHED','RESERVED','UNDER_DUE_DILIGENCE','CLOSED','WITHDRAWN')),
    compliance_approved_at timestamptz,
    exact_address_grant_required boolean not null default true,
    version bigint not null default 0,
    published_at timestamptz,
    unique (property_id, operation) deferrable initially immediate
);

create table public.property_offers (
    offer_id uuid primary key default gen_random_uuid(),
    listing_id uuid not null references public.property_listings(listing_id),
    buyer_principal_id uuid not null references auth.users(id),
    amount_minor bigint not null check (amount_minor >= 0),
    currency char(3) not null check (currency in ('CRC','USD')),
    conditions text not null,
    state text not null default 'SUBMITTED' check (state in ('SUBMITTED','COUNTERED','ACCEPTED','REJECTED','WITHDRAWN','EXPIRED')),
    version bigint not null default 0,
    created_at timestamptz not null default now()
);
create unique index property_one_accepted_offer_per_listing
on public.property_offers(listing_id) where state = 'ACCEPTED';

create table public.property_due_diligence (
    item_id uuid primary key default gen_random_uuid(),
    listing_id uuid not null references public.property_listings(listing_id),
    requested_by uuid not null references auth.users(id),
    item_type text not null,
    truth_state text not null default 'UNKNOWN',
    evidence_ref text,
    legal_matter_id uuid references public.legal_matters(matter_id),
    status text not null default 'REQUESTED' check (status in ('REQUESTED','IN_REVIEW','SATISFIED','FAILED','WAIVED')),
    version bigint not null default 0
);

create table public.property_transactions (
    transaction_id uuid primary key default gen_random_uuid(),
    listing_id uuid not null unique references public.property_listings(listing_id),
    accepted_offer_id uuid not null unique references public.property_offers(offer_id),
    legal_matter_id uuid references public.legal_matters(matter_id),
    state text not null default 'DUE_DILIGENCE' check (state in ('DUE_DILIGENCE','RESERVED','CLOSING','CLOSED','VOIDED')),
    version bigint not null default 0,
    closed_at timestamptz
);

create table public.property_leases (
    lease_id uuid primary key default gen_random_uuid(),
    listing_id uuid not null references public.property_listings(listing_id),
    landlord_principal_id uuid not null references auth.users(id),
    tenant_principal_id uuid not null references auth.users(id),
    contract_document_id uuid references public.legal_documents(document_id),
    rent_amount_minor bigint not null check (rent_amount_minor >= 0),
    deposit_amount_minor bigint not null check (deposit_amount_minor >= 0),
    currency char(3) not null check (currency in ('CRC','USD')),
    starts_on date not null,
    ends_on date not null check (ends_on > starts_on),
    state text not null default 'ACTIVE' check (state in ('PENDING','ACTIVE','RENEWED','ENDED','TERMINATED')),
    version bigint not null default 0
);

-- FUEL ------------------------------------------------------------------------
create table public.fuel_stations (
    station_id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.market_organizations(organization_id),
    name text not null,
    aresep_station_ref text,
    aresep_truth_state text not null default 'UNKNOWN' check (aresep_truth_state in ('AUTHORITY_VERIFIED','DOCUMENT_OBSERVED','UNKNOWN')),
    official_checked_at timestamptz,
    active boolean not null default true,
    version bigint not null default 0
);

create table public.fuel_campaigns (
    campaign_id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.market_organizations(organization_id),
    name text not null,
    status text not null default 'DRAFT' check (status in ('DRAFT','ACTIVE','PAUSED','ENDED','CANCELLED')),
    version bigint not null default 0,
    created_by uuid not null references auth.users(id)
);

create table public.fuel_campaign_versions (
    campaign_version_id uuid primary key default gen_random_uuid(),
    campaign_id uuid not null references public.fuel_campaigns(campaign_id),
    version integer not null check (version > 0),
    starts_at timestamptz not null,
    ends_at timestamptz not null check (ends_at > starts_at),
    qualifying_spend_minor bigint not null check (qualifying_spend_minor > 0),
    currency char(3) not null default 'CRC' check (currency = 'CRC'),
    issue_policy text not null,
    max_per_transaction integer check (max_per_transaction > 0),
    eligible_station_ids uuid[] not null default '{}',
    benefit_type text not null check (benefit_type in ('EXTERNAL_REWARD','STORE_ITEM_DISCOUNT','CAR_WASH_REWARD','POINTS','CASHBACK_ACCOUNTING','FREE_PRODUCT','PARTNER_REWARD','FUEL_PRICE_CREDIT')),
    benefit_payload jsonb not null,
    eligibility text not null,
    restrictions text not null,
    redemption_procedure text not null,
    terms_version integer not null check (terms_version > 0),
    terms_hash text not null check (terms_hash ~ '^[a-f0-9]{64}$'),
    regulatory_approval_ref text,
    published_at timestamptz,
    created_at timestamptz not null default now(),
    unique (campaign_id, version),
    check (benefit_type <> 'FUEL_PRICE_CREDIT' or regulatory_approval_ref is not null)
);

create table public.fuel_customer_profiles (
    customer_id uuid primary key references auth.users(id),
    consent_version text not null,
    consented_at timestamptz not null,
    preferred_station_id uuid references public.fuel_stations(station_id),
    communication_preferences jsonb not null default '{}',
    version bigint not null default 0
);

create table public.fuel_purchases (
    purchase_id uuid primary key default gen_random_uuid(),
    station_id uuid not null references public.fuel_stations(station_id),
    organization_id uuid not null references public.market_organizations(organization_id),
    terminal_ref text,
    shift_ref text,
    cashier_principal_id uuid references auth.users(id),
    customer_id uuid references auth.users(id),
    occurred_at timestamptz not null,
    total_minor bigint not null check (total_minor >= 0),
    currency char(3) not null default 'CRC' check (currency = 'CRC'),
    payment_method text not null,
    source text not null check (source in ('POS_AUTHORITATIVE','ERP_IMPORTED','RECEIPT_VERIFIED','STAFF_DECLARED','CUSTOMER_DECLARED')),
    truth_state text not null,
    receipt_hash text check (receipt_hash is null or receipt_hash ~ '^[a-f0-9]{64}$'),
    status text not null check (status in ('PENDING','SETTLED','VOIDED','REFUNDED')),
    version bigint not null default 0,
    unique (station_id, receipt_hash)
);

create table public.fuel_coupons (
    coupon_id uuid primary key default gen_random_uuid(),
    campaign_version_id uuid not null references public.fuel_campaign_versions(campaign_version_id),
    owner_customer_id uuid references auth.users(id),
    issued_from_purchase_id uuid not null references public.fuel_purchases(purchase_id),
    unit_number integer not null check (unit_number > 0),
    opaque_token_hash text not null unique check (opaque_token_hash ~ '^[a-f0-9]{64}$'),
    state text not null default 'ISSUED' check (state in ('ISSUED','CLAIMED','RESERVED','REDEEMED','EXPIRED','REVOKED')),
    issued_at timestamptz not null default now(),
    expires_at timestamptz not null,
    redeemed_at timestamptz,
    redeemed_station_id uuid references public.fuel_stations(station_id),
    version bigint not null default 0,
    unique (issued_from_purchase_id, campaign_version_id, unit_number)
);

create table public.fuel_redemptions (
    redemption_id uuid primary key default gen_random_uuid(),
    coupon_id uuid not null unique references public.fuel_coupons(coupon_id),
    station_id uuid not null references public.fuel_stations(station_id),
    attendant_principal_id uuid not null references auth.users(id),
    purchase_id uuid references public.fuel_purchases(purchase_id),
    idempotency_key uuid not null unique,
    redeemed_at timestamptz not null default now()
);

create table public.fuel_crm_events (
    crm_event_id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.market_organizations(organization_id),
    customer_id uuid not null references auth.users(id),
    event_type text not null,
    source_ref uuid,
    metadata jsonb not null default '{}',
    occurred_at timestamptz not null default now()
);

-- Helper functions live outside the exposed API schema and pin search_path.
create or replace function market_private.is_org_member(
    p_organization_id uuid,
    p_principal_id uuid,
    p_roles text[] default null
) returns boolean
language sql stable security definer set search_path = ''
as $$
    select exists (
        select 1 from public.market_organization_members m
        where m.organization_id = p_organization_id
          and m.principal_id = p_principal_id
          and m.revoked_at is null
          and m.valid_from <= now()
          and (m.valid_until is null or m.valid_until >= now())
          and (p_roles is null or m.roles && p_roles)
    )
$$;

create or replace function market_private.has_legal_access(p_matter_id uuid, p_principal_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.legal_matters m
        where m.matter_id = p_matter_id and (
            m.client_principal_id = p_principal_id or exists (
                select 1 from public.legal_engagements e
                join public.legal_conflict_checks c
                  on c.matter_id = e.matter_id
                 and c.professional_principal_id = e.professional_principal_id
                 and c.decision = 'CLEAR' and c.expires_at >= now()
                where e.matter_id = m.matter_id
                  and e.state in ('ACTIVE','PAUSED')
                  and (e.professional_principal_id = p_principal_id or
                       market_private.is_org_member(e.organization_id, p_principal_id, null))
            )
        )
    )
$$;

grant usage on schema market_private to authenticated;
grant execute on all functions in schema market_private to authenticated;

-- Published campaign versions are immutable, including their terms.
create or replace function market_private.prevent_published_campaign_mutation()
returns trigger language plpgsql set search_path = '' as $$
begin
    if old.published_at is not null and new is distinct from old then
        raise exception 'PUBLISHED_CAMPAIGN_VERSION_IMMUTABLE' using errcode = '55000';
    end if;
    return new;
end $$;
create trigger fuel_campaign_version_immutable
before update or delete on public.fuel_campaign_versions
for each row execute function market_private.prevent_published_campaign_mutation();

-- Atomic redemption. Exactly one caller can transition a coupon to REDEEMED.
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
    v_existing public.fuel_redemptions%rowtype;
    v_redemption_id uuid := gen_random_uuid();
begin
    if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode = '28000'; end if;
    if not market_private.is_org_member(
        (select organization_id from public.fuel_stations where station_id = p_station_id),
        v_actor, array['ATTENDANT','CASHIER','SHIFT_SUPERVISOR','STATION_MANAGER','OWNER','ADMIN']
    ) then raise exception 'STATION_ROLE_REQUIRED' using errcode = '42501'; end if;

    select * into v_existing from public.fuel_redemptions where idempotency_key = p_idempotency_key;
    if found then return jsonb_build_object('redemption_id', v_existing.redemption_id, 'replayed', true); end if;

    select * into v_coupon from public.fuel_coupons
     where opaque_token_hash = encode(extensions.digest(p_opaque_token, 'sha256'), 'hex') for update;
    if not found then raise exception 'COUPON_NOT_FOUND' using errcode = 'P0002'; end if;
    if v_coupon.state not in ('ISSUED','CLAIMED','RESERVED') then raise exception 'COUPON_NOT_REDEEMABLE'; end if;
    if v_coupon.expires_at < now() then raise exception 'COUPON_EXPIRED'; end if;
    if exists (
        select 1 from public.fuel_campaign_versions cv
        where cv.campaign_version_id = v_coupon.campaign_version_id
          and cardinality(cv.eligible_station_ids) > 0
          and not (p_station_id = any(cv.eligible_station_ids))
    ) then raise exception 'WRONG_STATION'; end if;

    insert into public.fuel_redemptions(redemption_id, coupon_id, station_id, attendant_principal_id, purchase_id, idempotency_key)
    values (v_redemption_id, v_coupon.coupon_id, p_station_id, v_actor, p_purchase_id, p_idempotency_key);
    update public.fuel_coupons set state = 'REDEEMED', redeemed_at = now(), redeemed_station_id = p_station_id,
        version = version + 1 where coupon_id = v_coupon.coupon_id;
    insert into public.market_audit_events(actor_principal_id, aggregate_type, aggregate_id, action, decision)
    values (v_actor, 'FUEL_COUPON', v_coupon.coupon_id, 'REDEEM', 'ALLOWED');
    return jsonb_build_object('redemption_id', v_redemption_id, 'coupon_id', v_coupon.coupon_id, 'replayed', false);
end $$;

revoke all on function public.redeem_fuel_coupon_v1(text, uuid, uuid, uuid) from public, anon;
grant execute on function public.redeem_fuel_coupon_v1(text, uuid, uuid, uuid) to authenticated;

-- Server-issued rewards only for settled, sufficiently authoritative purchases.
create or replace function public.issue_fuel_rewards_v1(
    p_purchase_id uuid,
    p_campaign_version_id uuid,
    p_expected_purchase_version bigint,
    p_idempotency_key uuid
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_actor uuid := (select auth.uid());
    v_purchase public.fuel_purchases%rowtype;
    v_campaign public.fuel_campaign_versions%rowtype;
    v_units integer;
    v_i integer;
    v_coupon uuid;
    v_raw_token text;
    v_tokens jsonb := '[]'::jsonb;
begin
    if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode = '28000'; end if;
    if exists (select 1 from public.market_commands where idempotency_key = p_idempotency_key) then
        return (select result from public.market_commands where idempotency_key = p_idempotency_key);
    end if;
    select * into v_purchase from public.fuel_purchases where purchase_id = p_purchase_id for update;
    if not found then raise exception 'PURCHASE_NOT_FOUND'; end if;
    if v_purchase.version <> p_expected_purchase_version then raise exception 'VERSION_CONFLICT' using errcode = '40001'; end if;
    if v_purchase.status <> 'SETTLED' or v_purchase.source not in ('POS_AUTHORITATIVE','ERP_IMPORTED','RECEIPT_VERIFIED') then
        raise exception 'PURCHASE_NOT_AUTHORITATIVE';
    end if;
    if not (v_actor = v_purchase.customer_id or market_private.is_org_member(v_purchase.organization_id, v_actor, null)) then
        raise exception 'PURCHASE_ACCESS_DENIED' using errcode = '42501';
    end if;
    select * into v_campaign from public.fuel_campaign_versions where campaign_version_id = p_campaign_version_id;
    if not found or v_campaign.published_at is null or now() not between v_campaign.starts_at and v_campaign.ends_at then
        raise exception 'CAMPAIGN_INACTIVE';
    end if;
    v_units := floor(v_purchase.total_minor::numeric / v_campaign.qualifying_spend_minor)::integer;
    if v_campaign.issue_policy <> 'ONE_PER_EVERY_N_SPEND' then v_units := least(v_units, 1); end if;
    if v_campaign.max_per_transaction is not null then v_units := least(v_units, v_campaign.max_per_transaction); end if;
    for v_i in 1..v_units loop
        v_coupon := gen_random_uuid(); v_raw_token := replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
        insert into public.fuel_coupons(coupon_id, campaign_version_id, owner_customer_id, issued_from_purchase_id, unit_number, opaque_token_hash, expires_at)
        values (v_coupon, p_campaign_version_id, v_purchase.customer_id, p_purchase_id, v_i,
            encode(extensions.digest(v_raw_token, 'sha256'), 'hex'), least(v_campaign.ends_at, now() + interval '30 days'))
        on conflict (issued_from_purchase_id, campaign_version_id, unit_number) do nothing;
        v_tokens := v_tokens || jsonb_build_array(jsonb_build_object('coupon_id', v_coupon, 'token', v_raw_token));
    end loop;
    insert into public.market_commands(command_id, idempotency_key, actor_principal_id, aggregate_type, aggregate_id,
        command_type, expected_version, canonical_digest, payload, payload_version, status, result)
    values (gen_random_uuid(), p_idempotency_key, v_actor, 'FUEL_PURCHASE', p_purchase_id, 'ISSUE_REWARDS',
        p_expected_purchase_version, encode(extensions.digest(p_purchase_id::text || p_campaign_version_id::text || p_expected_purchase_version::text, 'sha256'), 'hex'),
        jsonb_build_object('campaign_version_id', p_campaign_version_id), 1, 'APPLIED',
        jsonb_build_object('issued_units', v_units, 'tokens', v_tokens));
    -- The idempotent command result retains the tokens for the authenticated
    -- actor so a lost response can be replayed without minting replacements.
    return jsonb_build_object('issued_units', v_units, 'tokens', v_tokens);
end $$;
revoke all on function public.issue_fuel_rewards_v1(uuid, uuid, bigint, uuid) from public, anon;
grant execute on function public.issue_fuel_rewards_v1(uuid, uuid, bigint, uuid) to authenticated;

-- RLS: fail closed. Direct writes are withheld; RPCs perform authoritative writes.
do $$
declare t text;
begin
    foreach t in array array[
      'market_organizations','market_organization_members','market_professional_credentials','market_commands','market_audit_events',
      'legal_professional_profiles','legal_matters','legal_matter_parties','legal_conflict_checks','legal_offers','legal_engagements','legal_documents','legal_deadlines',
      'property_assets','property_proofs','property_listings','property_offers','property_due_diligence','property_transactions','property_leases',
      'fuel_stations','fuel_campaigns','fuel_campaign_versions','fuel_customer_profiles','fuel_purchases','fuel_coupons','fuel_redemptions','fuel_crm_events'
    ] loop
      execute format('alter table public.%I enable row level security', t);
      execute format('revoke all on table public.%I from anon, authenticated', t);
      execute format('grant select on table public.%I to authenticated', t);
    end loop;
end $$;

alter table public.market_taxonomy_versions enable row level security;
alter table public.market_service_categories enable row level security;
alter table public.market_service_templates enable row level security;
revoke all on public.market_taxonomy_versions, public.market_service_categories, public.market_service_templates from anon, authenticated;
grant select on public.market_taxonomy_versions, public.market_service_categories, public.market_service_templates to anon, authenticated;
create policy market_taxonomy_public_published on public.market_taxonomy_versions for select using (published_at is not null);
create policy market_categories_public_active on public.market_service_categories for select using (active);
create policy market_templates_public_active on public.market_service_templates for select using (active);

create policy market_org_member_read on public.market_organizations for select to authenticated using (
    created_by = (select auth.uid()) or market_private.is_org_member(organization_id, (select auth.uid()), null)
);
create policy market_members_same_org_read on public.market_organization_members for select to authenticated using (
    principal_id = (select auth.uid()) or market_private.is_org_member(organization_id, (select auth.uid()), array['OWNER','ADMIN','AUDITOR'])
);
create policy market_credential_self_or_admin_read on public.market_professional_credentials for select to authenticated using (
    principal_id = (select auth.uid()) or (organization_id is not null and market_private.is_org_member(organization_id, (select auth.uid()), array['OWNER','ADMIN','COMPLIANCE']))
);
create policy market_command_actor_read on public.market_commands for select to authenticated using (actor_principal_id = (select auth.uid()));
create policy market_audit_scoped_read on public.market_audit_events for select to authenticated using (
    actor_principal_id = (select auth.uid()) or (organization_id is not null and market_private.is_org_member(organization_id, (select auth.uid()), array['OWNER','ADMIN','AUDITOR']))
);

create policy legal_profile_authenticated_read on public.legal_professional_profiles for select to authenticated using (true);
create policy legal_matter_authorized_read on public.legal_matters for select to authenticated using (market_private.has_legal_access(matter_id, (select auth.uid())));
create policy legal_party_authorized_read on public.legal_matter_parties for select to authenticated using (market_private.has_legal_access(matter_id, (select auth.uid())));
create policy legal_conflict_actor_read on public.legal_conflict_checks for select to authenticated using (
    professional_principal_id = (select auth.uid()) or market_private.has_legal_access(matter_id, (select auth.uid()))
);
create policy legal_offer_party_read on public.legal_offers for select to authenticated using (
    professional_principal_id = (select auth.uid()) or market_private.has_legal_access(matter_id, (select auth.uid()))
);
create policy legal_engagement_party_read on public.legal_engagements for select to authenticated using (
    client_principal_id = (select auth.uid()) or professional_principal_id = (select auth.uid()) or market_private.is_org_member(organization_id, (select auth.uid()), null)
);
create policy legal_document_authorized_read on public.legal_documents for select to authenticated using (market_private.has_legal_access(matter_id, (select auth.uid())));
create policy legal_deadline_authorized_read on public.legal_deadlines for select to authenticated using (market_private.has_legal_access(matter_id, (select auth.uid())));

create policy property_asset_owner_read on public.property_assets for select to authenticated using (created_by = (select auth.uid()));
create policy property_proof_owner_read on public.property_proofs for select to authenticated using (exists (
    select 1 from public.property_assets a where a.property_id = property_proofs.property_id and a.created_by = (select auth.uid())
));
create policy property_listing_public_or_owner_read on public.property_listings for select to authenticated using (state = 'PUBLISHED' or seller_principal_id = (select auth.uid()));
create policy property_offer_party_read on public.property_offers for select to authenticated using (
    buyer_principal_id = (select auth.uid()) or exists (select 1 from public.property_listings l where l.listing_id = property_offers.listing_id and l.seller_principal_id = (select auth.uid()))
);
create policy property_dd_party_read on public.property_due_diligence for select to authenticated using (
    requested_by = (select auth.uid()) or exists (select 1 from public.property_listings l where l.listing_id = property_due_diligence.listing_id and l.seller_principal_id = (select auth.uid()))
);
create policy property_tx_party_read on public.property_transactions for select to authenticated using (exists (
    select 1 from public.property_listings l join public.property_offers o on o.offer_id = property_transactions.accepted_offer_id
    where l.listing_id = property_transactions.listing_id and (l.seller_principal_id = (select auth.uid()) or o.buyer_principal_id = (select auth.uid()))
));
create policy property_lease_party_read on public.property_leases for select to authenticated using (landlord_principal_id = (select auth.uid()) or tenant_principal_id = (select auth.uid()));

create policy fuel_station_member_read on public.fuel_stations for select to authenticated using (active or market_private.is_org_member(organization_id, (select auth.uid()), null));
create policy fuel_campaign_member_read on public.fuel_campaigns for select to authenticated using (market_private.is_org_member(organization_id, (select auth.uid()), null));
create policy fuel_campaign_version_member_read on public.fuel_campaign_versions for select to authenticated using (exists (
    select 1 from public.fuel_campaigns c where c.campaign_id = fuel_campaign_versions.campaign_id and market_private.is_org_member(c.organization_id, (select auth.uid()), null)
));
create policy fuel_customer_self_read on public.fuel_customer_profiles for select to authenticated using (customer_id = (select auth.uid()));
create policy fuel_purchase_party_read on public.fuel_purchases for select to authenticated using (customer_id = (select auth.uid()) or market_private.is_org_member(organization_id, (select auth.uid()), null));
create policy fuel_coupon_owner_or_station_read on public.fuel_coupons for select to authenticated using (
    owner_customer_id = (select auth.uid()) or exists (
      select 1 from public.fuel_campaign_versions cv join public.fuel_campaigns c on c.campaign_id = cv.campaign_id
      where cv.campaign_version_id = fuel_coupons.campaign_version_id and market_private.is_org_member(c.organization_id, (select auth.uid()), null)
    )
);
create policy fuel_redemption_station_read on public.fuel_redemptions for select to authenticated using (attendant_principal_id = (select auth.uid()) or exists (
    select 1 from public.fuel_stations s where s.station_id = fuel_redemptions.station_id and market_private.is_org_member(s.organization_id, (select auth.uid()), array['OWNER','ADMIN','STATION_MANAGER','AUDITOR'])
));
create policy fuel_crm_member_read on public.fuel_crm_events for select to authenticated using (customer_id = (select auth.uid()) or market_private.is_org_member(organization_id, (select auth.uid()), array['OWNER','ADMIN','CRM_MANAGER','AUDITOR']));

-- Realtime is a wake-up signal; RLS-protected refresh remains authoritative.
do $$ begin
    alter publication supabase_realtime add table public.legal_matters;
exception when duplicate_object then null; end $$;
do $$ begin
    alter publication supabase_realtime add table public.property_listings;
exception when duplicate_object then null; end $$;
do $$ begin
    alter publication supabase_realtime add table public.fuel_coupons;
exception when duplicate_object then null; end $$;
