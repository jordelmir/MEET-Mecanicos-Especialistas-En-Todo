-- Complete operational data model for the three Market OS verticals.
-- Direct business-state writes remain withheld; RPCs below derive auth.uid().

create table public.market_organization_locations (
    location_id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.market_organizations(organization_id) on delete cascade,
    label text not null check (length(trim(label)) between 1 and 120),
    country_code text not null check (country_code ~ '^[A-Z]{2}$'),
    province_code text,
    approximate_address text not null,
    exact_address_ciphertext text,
    active boolean not null default true,
    version bigint not null default 0
);

create table public.market_professional_capabilities (
    capability_id uuid primary key default gen_random_uuid(),
    principal_id uuid not null references auth.users(id),
    organization_id uuid references public.market_organizations(organization_id),
    vertical text not null check (vertical in ('LEGAL','REAL_ESTATE','FUEL_REWARDS')),
    category_code text not null,
    truth_state text not null check (truth_state in ('DECLARED','DOCUMENT_OBSERVED','AUTHORITY_VERIFIED','UNKNOWN')),
    evidence_ref text,
    demonstrated_count integer not null default 0 check (demonstrated_count >= 0),
    verified_at timestamptz,
    expires_at timestamptz,
    version bigint not null default 0,
    unique(principal_id, organization_id, vertical, category_code)
);

create table public.market_data_grants (
    grant_id uuid primary key default gen_random_uuid(),
    grantor_principal_id uuid not null references auth.users(id),
    grantee_principal_id uuid references auth.users(id),
    grantee_organization_id uuid references public.market_organizations(organization_id),
    aggregate_type text not null,
    aggregate_id uuid not null,
    scopes text[] not null check (cardinality(scopes) > 0),
    purpose text not null,
    granted_at timestamptz not null default now(),
    expires_at timestamptz,
    revoked_at timestamptz,
    check ((grantee_principal_id is null) <> (grantee_organization_id is null))
);

create table public.market_invoices (
    invoice_id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.market_organizations(organization_id),
    customer_principal_id uuid not null references auth.users(id),
    aggregate_type text not null,
    aggregate_id uuid not null,
    subtotal_minor bigint not null check (subtotal_minor >= 0),
    external_expenses_minor bigint not null default 0 check (external_expenses_minor >= 0),
    tax_minor bigint not null default 0 check (tax_minor >= 0),
    currency char(3) not null check (currency in ('CRC','USD')),
    state text not null default 'DRAFT' check (state in ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','VOIDED','DISPUTED')),
    due_at timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null default now()
);

create table public.market_payment_events (
    payment_event_id uuid primary key default gen_random_uuid(),
    invoice_id uuid not null references public.market_invoices(invoice_id),
    actor_principal_id uuid references auth.users(id),
    provider text not null,
    provider_reference_hash text,
    event_type text not null check (event_type in ('INTENT_CREATED','PROOF_SUBMITTED','PROVIDER_CONFIRMED','FAILED','REFUNDED','CHARGEBACK')),
    amount_minor bigint not null check (amount_minor >= 0),
    currency char(3) not null check (currency in ('CRC','USD')),
    truth_state text not null check (truth_state in ('DECLARED','DOCUMENT_OBSERVED','AUTHORITY_VERIFIED','UNKNOWN')),
    occurred_at timestamptz not null default now(),
    metadata jsonb not null default '{}'
);

create table public.market_reviews (
    review_id uuid primary key default gen_random_uuid(),
    aggregate_type text not null,
    aggregate_id uuid not null,
    reviewer_principal_id uuid not null references auth.users(id),
    subject_principal_id uuid references auth.users(id),
    subject_organization_id uuid references public.market_organizations(organization_id),
    rating smallint not null check (rating between 1 and 5),
    body text not null check (length(trim(body)) between 3 and 2000),
    completion_evidence_ref text not null,
    state text not null default 'PUBLISHED' check (state in ('PUBLISHED','UNDER_REVIEW','REDACTED','VOIDED')),
    created_at timestamptz not null default now(),
    unique(aggregate_type, aggregate_id, reviewer_principal_id),
    check ((subject_principal_id is null) <> (subject_organization_id is null))
);

create table public.legal_milestones (
    milestone_id uuid primary key default gen_random_uuid(),
    engagement_id uuid not null references public.legal_engagements(engagement_id) on delete cascade,
    title text not null,
    description text not null,
    amount_minor bigint check (amount_minor >= 0),
    currency char(3) check (currency in ('CRC','USD')),
    state text not null default 'PLANNED' check (state in ('PLANNED','ACTIVE','SUBMITTED','ACCEPTED','DISPUTED','VOIDED')),
    due_at timestamptz,
    version bigint not null default 0
);

create table public.legal_time_entries (
    time_entry_id uuid primary key default gen_random_uuid(),
    engagement_id uuid not null references public.legal_engagements(engagement_id) on delete cascade,
    professional_principal_id uuid not null references auth.users(id),
    minutes integer not null check (minutes between 1 and 1440),
    description text not null,
    occurred_on date not null,
    billing_state text not null default 'UNBILLED' check (billing_state in ('UNBILLED','INVOICED','WRITTEN_OFF')),
    created_at timestamptz not null default now()
);

create table public.legal_practice_groups (
    practice_group_id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.market_organizations(organization_id) on delete cascade,
    name text not null,
    category_codes text[] not null check (cardinality(category_codes) > 0),
    active boolean not null default true,
    unique(organization_id, name)
);

create table public.property_media (
    media_id uuid primary key default gen_random_uuid(),
    property_id uuid not null references public.property_assets(property_id) on delete cascade,
    uploaded_by uuid not null references auth.users(id),
    media_type text not null check (media_type in ('PHOTO','VIDEO','TOUR_360','FLOOR_PLAN','DOCUMENT')),
    storage_ref text not null,
    sha256 text not null check (sha256 ~ '^[a-f0-9]{64}$'),
    truth_state text not null default 'DOCUMENT_OBSERVED',
    sort_order integer not null default 0,
    created_at timestamptz not null default now()
);

create table public.property_inquiries (
    inquiry_id uuid primary key default gen_random_uuid(),
    listing_id uuid not null references public.property_listings(listing_id),
    buyer_principal_id uuid not null references auth.users(id),
    message_ciphertext text not null,
    state text not null default 'OPEN' check (state in ('OPEN','RESPONDED','CLOSED','BLOCKED')),
    created_at timestamptz not null default now(),
    unique(listing_id, buyer_principal_id)
);

create table public.property_showings (
    showing_id uuid primary key default gen_random_uuid(),
    listing_id uuid not null references public.property_listings(listing_id),
    inquiry_id uuid references public.property_inquiries(inquiry_id),
    buyer_principal_id uuid not null references auth.users(id),
    host_principal_id uuid references auth.users(id),
    scheduled_at timestamptz not null,
    state text not null default 'REQUESTED' check (state in ('REQUESTED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW')),
    exact_address_grant_id uuid,
    version bigint not null default 0
);

create table public.property_address_grants (
    address_grant_id uuid primary key default gen_random_uuid(),
    property_id uuid not null references public.property_assets(property_id) on delete cascade,
    grantee_principal_id uuid not null references auth.users(id),
    granted_by uuid not null references auth.users(id),
    purpose text not null,
    granted_at timestamptz not null default now(),
    expires_at timestamptz not null,
    revoked_at timestamptz,
    unique(property_id, grantee_principal_id, purpose)
);
alter table public.property_showings
    add constraint property_showing_address_grant_fk
    foreign key (exact_address_grant_id) references public.property_address_grants(address_grant_id);

create table public.property_inspections (
    inspection_id uuid primary key default gen_random_uuid(),
    property_id uuid not null references public.property_assets(property_id),
    inspector_principal_id uuid references auth.users(id),
    organization_id uuid references public.market_organizations(organization_id),
    scope text not null,
    evidence_ref text,
    truth_state text not null default 'UNKNOWN',
    state text not null default 'REQUESTED' check (state in ('REQUESTED','SCHEDULED','COMPLETED','CANCELLED')),
    completed_at timestamptz,
    version bigint not null default 0
);

create table public.property_valuations (
    valuation_id uuid primary key default gen_random_uuid(),
    property_id uuid not null references public.property_assets(property_id),
    valuer_principal_id uuid references auth.users(id),
    amount_minor bigint not null check (amount_minor >= 0),
    currency char(3) not null check (currency in ('CRC','USD')),
    effective_on date not null,
    methodology text not null,
    evidence_ref text,
    truth_state text not null default 'DOCUMENT_OBSERVED',
    created_at timestamptz not null default now()
);

create table public.property_maintenance_cases (
    maintenance_case_id uuid primary key default gen_random_uuid(),
    lease_id uuid not null references public.property_leases(lease_id),
    opened_by uuid not null references auth.users(id),
    title text not null,
    detail_ciphertext text not null,
    priority text not null check (priority in ('LOW','NORMAL','URGENT','EMERGENCY')),
    state text not null default 'OPEN' check (state in ('OPEN','ASSIGNED','IN_PROGRESS','RESOLVED','VOIDED')),
    version bigint not null default 0,
    created_at timestamptz not null default now()
);

create table public.market_jurisdiction_rules (
    rule_id uuid primary key default gen_random_uuid(),
    jurisdiction text not null,
    vertical text not null,
    rule_code text not null,
    value_json jsonb not null,
    source_url text not null,
    source_checked_at timestamptz not null,
    effective_from date not null,
    effective_until date,
    version integer not null check (version > 0),
    published_at timestamptz,
    unique(jurisdiction, vertical, rule_code, version)
);

create table public.fuel_brands (
    brand_id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.market_organizations(organization_id) on delete cascade,
    name text not null,
    active boolean not null default true,
    unique(organization_id, name)
);

create table public.fuel_regions (
    region_id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.market_organizations(organization_id) on delete cascade,
    name text not null,
    manager_principal_id uuid references auth.users(id),
    active boolean not null default true,
    unique(organization_id, name)
);

alter table public.fuel_stations add column brand_id uuid references public.fuel_brands(brand_id);
alter table public.fuel_stations add column region_id uuid references public.fuel_regions(region_id);

create table public.fuel_shifts (
    shift_id uuid primary key default gen_random_uuid(),
    station_id uuid not null references public.fuel_stations(station_id),
    supervisor_principal_id uuid references auth.users(id),
    opened_at timestamptz not null,
    closed_at timestamptz,
    state text not null default 'OPEN' check (state in ('OPEN','CLOSED','RECONCILIATION_REQUIRED')),
    version bigint not null default 0
);

create table public.fuel_terminals (
    terminal_id uuid primary key default gen_random_uuid(),
    station_id uuid not null references public.fuel_stations(station_id),
    external_ref text not null,
    trust_state text not null default 'UNKNOWN' check (trust_state in ('AUTHORITY_VERIFIED','DOCUMENT_OBSERVED','UNKNOWN')),
    active boolean not null default true,
    version bigint not null default 0,
    unique(station_id, external_ref)
);

create table public.fuel_purchase_lines (
    purchase_line_id uuid primary key default gen_random_uuid(),
    purchase_id uuid not null references public.fuel_purchases(purchase_id) on delete cascade,
    product_code text not null,
    description text not null,
    quantity_milliunits bigint not null check (quantity_milliunits > 0),
    unit_amount_minor bigint not null check (unit_amount_minor >= 0),
    line_total_minor bigint not null check (line_total_minor >= 0),
    regulated_fuel boolean not null default false
);

create table public.fuel_segments (
    segment_id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.market_organizations(organization_id) on delete cascade,
    code text not null,
    definition jsonb not null,
    active boolean not null default true,
    version bigint not null default 0,
    unique(organization_id, code)
);

create table public.fuel_customer_segments (
    segment_id uuid not null references public.fuel_segments(segment_id) on delete cascade,
    customer_id uuid not null references auth.users(id),
    assigned_at timestamptz not null default now(),
    expires_at timestamptz,
    source text not null,
    primary key(segment_id, customer_id)
);

create table public.fuel_support_cases (
    support_case_id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.market_organizations(organization_id),
    customer_id uuid not null references auth.users(id),
    category text not null,
    summary_ciphertext text not null,
    state text not null default 'OPEN' check (state in ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
    version bigint not null default 0,
    created_at timestamptz not null default now()
);

create table public.fuel_purchase_claim_tokens (
    claim_token_id uuid primary key default gen_random_uuid(),
    purchase_id uuid not null unique references public.fuel_purchases(purchase_id) on delete cascade,
    token_hash text not null unique check (token_hash ~ '^[a-f0-9]{64}$'),
    issued_by uuid not null references auth.users(id),
    issued_at timestamptz not null default now(),
    expires_at timestamptz not null,
    claimed_by uuid references auth.users(id),
    claimed_at timestamptz,
    revoked_at timestamptz
);

create table public.fuel_reward_adjustments (
    adjustment_id uuid primary key default gen_random_uuid(),
    purchase_id uuid not null references public.fuel_purchases(purchase_id),
    customer_id uuid references auth.users(id),
    adjustment_type text not null check (adjustment_type in ('NEGATIVE_REWARD_BALANCE','MANUAL_REVIEW','RESTORED')),
    units integer not null check (units > 0),
    reason_code text not null,
    state text not null default 'OPEN' check (state in ('OPEN','RESOLVED','VOIDED')),
    created_at timestamptz not null default now(),
    resolved_at timestamptz
);

-- Fail closed: readable only through RLS; no direct client mutations.
do $$ declare t text;
begin
    foreach t in array array[
      'market_organization_locations','market_professional_capabilities','market_data_grants',
      'market_invoices','market_payment_events','market_reviews','legal_milestones',
      'legal_time_entries','legal_practice_groups','property_media','property_inquiries',
      'property_showings','property_address_grants','property_inspections','property_valuations',
      'property_maintenance_cases','fuel_brands','fuel_regions','fuel_shifts','fuel_terminals',
      'fuel_purchase_lines','fuel_segments','fuel_customer_segments','fuel_support_cases',
      'fuel_purchase_claim_tokens','fuel_reward_adjustments'
    ] loop
      execute format('alter table public.%I enable row level security', t);
      execute format('revoke all on table public.%I from anon, authenticated', t);
      execute format('grant select on table public.%I to authenticated', t);
    end loop;
end $$;

alter table public.market_jurisdiction_rules enable row level security;
revoke all on table public.market_jurisdiction_rules from anon, authenticated;
grant select on table public.market_jurisdiction_rules to anon, authenticated;
create policy market_jurisdiction_rules_published_read on public.market_jurisdiction_rules
for select using (published_at is not null and effective_from <= current_date and (effective_until is null or effective_until >= current_date));

create policy market_location_member_read on public.market_organization_locations for select to authenticated using
  (market_private.is_org_member(organization_id, (select auth.uid()), null));
create policy market_capability_authenticated_read on public.market_professional_capabilities for select to authenticated using (true);
create policy market_data_grant_party_read on public.market_data_grants for select to authenticated using
  (grantor_principal_id=(select auth.uid()) or grantee_principal_id=(select auth.uid()) or market_private.is_org_member(grantee_organization_id,(select auth.uid()),null));
create policy market_invoice_party_read on public.market_invoices for select to authenticated using
  (customer_principal_id=(select auth.uid()) or market_private.is_org_member(organization_id,(select auth.uid()),array['OWNER','ADMIN','BILLING','FINANCE','AUDITOR']));
create policy market_payment_invoice_party_read on public.market_payment_events for select to authenticated using
  (exists(select 1 from public.market_invoices i where i.invoice_id=market_payment_events.invoice_id and (i.customer_principal_id=(select auth.uid()) or market_private.is_org_member(i.organization_id,(select auth.uid()),array['OWNER','ADMIN','BILLING','FINANCE','AUDITOR']))));
create policy market_review_authenticated_read on public.market_reviews for select to authenticated using (state='PUBLISHED' or reviewer_principal_id=(select auth.uid()));
create policy legal_milestone_authorized_read on public.legal_milestones for select to authenticated using
  (exists(select 1 from public.legal_engagements e where e.engagement_id=legal_milestones.engagement_id and market_private.has_legal_access(e.matter_id,(select auth.uid()))));
create policy legal_time_authorized_read on public.legal_time_entries for select to authenticated using
  (professional_principal_id=(select auth.uid()) or exists(select 1 from public.legal_engagements e where e.engagement_id=legal_time_entries.engagement_id and market_private.has_legal_access(e.matter_id,(select auth.uid()))));
create policy legal_practice_group_member_read on public.legal_practice_groups for select to authenticated using
  (market_private.is_org_member(organization_id,(select auth.uid()),null));
create policy property_media_visible_read on public.property_media for select to authenticated using
  (exists(select 1 from public.property_assets a left join public.property_listings l using(property_id) where a.property_id=property_media.property_id and (a.created_by=(select auth.uid()) or l.state='PUBLISHED')));
create policy property_inquiry_party_read on public.property_inquiries for select to authenticated using
  (buyer_principal_id=(select auth.uid()) or exists(select 1 from public.property_listings l where l.listing_id=property_inquiries.listing_id and l.seller_principal_id=(select auth.uid())));
create policy property_showing_party_read on public.property_showings for select to authenticated using
  (buyer_principal_id=(select auth.uid()) or host_principal_id=(select auth.uid()) or exists(select 1 from public.property_listings l where l.listing_id=property_showings.listing_id and l.seller_principal_id=(select auth.uid())));
create policy property_address_grant_party_read on public.property_address_grants for select to authenticated using
  (grantee_principal_id=(select auth.uid()) or granted_by=(select auth.uid()));
create policy property_inspection_party_read on public.property_inspections for select to authenticated using
  (inspector_principal_id=(select auth.uid()) or exists(select 1 from public.property_assets a where a.property_id=property_inspections.property_id and a.created_by=(select auth.uid())));
create policy property_valuation_owner_read on public.property_valuations for select to authenticated using
  (valuer_principal_id=(select auth.uid()) or exists(select 1 from public.property_assets a where a.property_id=property_valuations.property_id and a.created_by=(select auth.uid())));
create policy property_maintenance_lease_party_read on public.property_maintenance_cases for select to authenticated using
  (exists(select 1 from public.property_leases l where l.lease_id=property_maintenance_cases.lease_id and (l.landlord_principal_id=(select auth.uid()) or l.tenant_principal_id=(select auth.uid()))));
create policy fuel_brand_member_read on public.fuel_brands for select to authenticated using (market_private.is_org_member(organization_id,(select auth.uid()),null));
create policy fuel_region_member_read on public.fuel_regions for select to authenticated using (market_private.is_org_member(organization_id,(select auth.uid()),null));
create policy fuel_shift_member_read on public.fuel_shifts for select to authenticated using
  (exists(select 1 from public.fuel_stations s where s.station_id=fuel_shifts.station_id and market_private.is_org_member(s.organization_id,(select auth.uid()),null)));
create policy fuel_terminal_member_read on public.fuel_terminals for select to authenticated using
  (exists(select 1 from public.fuel_stations s where s.station_id=fuel_terminals.station_id and market_private.is_org_member(s.organization_id,(select auth.uid()),null)));
create policy fuel_purchase_line_party_read on public.fuel_purchase_lines for select to authenticated using
  (exists(select 1 from public.fuel_purchases p where p.purchase_id=fuel_purchase_lines.purchase_id and (p.customer_id=(select auth.uid()) or market_private.is_org_member(p.organization_id,(select auth.uid()),null))));
create policy fuel_segment_member_read on public.fuel_segments for select to authenticated using (market_private.is_org_member(organization_id,(select auth.uid()),array['OWNER','ADMIN','MARKETING_MANAGER','CRM_MANAGER','AUDITOR']));
create policy fuel_customer_segment_party_read on public.fuel_customer_segments for select to authenticated using
  (customer_id=(select auth.uid()) or exists(select 1 from public.fuel_segments s where s.segment_id=fuel_customer_segments.segment_id and market_private.is_org_member(s.organization_id,(select auth.uid()),array['OWNER','ADMIN','CRM_MANAGER','AUDITOR'])));
create policy fuel_support_party_read on public.fuel_support_cases for select to authenticated using
  (customer_id=(select auth.uid()) or market_private.is_org_member(organization_id,(select auth.uid()),array['OWNER','ADMIN','SUPPORT','AUDITOR']));
create policy fuel_adjustment_party_read on public.fuel_reward_adjustments for select to authenticated using
  (customer_id=(select auth.uid()) or exists(select 1 from public.fuel_purchases p where p.purchase_id=fuel_reward_adjustments.purchase_id and market_private.is_org_member(p.organization_id,(select auth.uid()),array['OWNER','ADMIN','FINANCE','AUDITOR'])));

create or replace function public.submit_legal_professional_profile_v1(
  p_organization_id uuid,p_display_name text,p_bar_identifier_ciphertext text,p_bar_identifier_masked text,
  p_declared_capabilities text[],p_accepting_matters boolean,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  if p_organization_id is not null and not market_private.is_org_member(p_organization_id,v_actor,array['OWNER','ADMIN','PARTNER','PROFESSIONAL']) then raise exception 'ORGANIZATION_ROLE_REQUIRED' using errcode='42501'; end if;
  if length(trim(p_display_name)) not between 2 and 160 or trim(p_bar_identifier_ciphertext)='' or trim(p_bar_identifier_masked)='' then raise exception 'INVALID_PROFESSIONAL_PROFILE'; end if;
  if cardinality(p_declared_capabilities)=0 or exists(select 1 from unnest(p_declared_capabilities) c where not exists(select 1 from public.market_service_categories sc join public.market_taxonomy_versions t using(taxonomy_version_id) where t.vertical='LEGAL' and t.published_at is not null and sc.code=c and sc.active)) then raise exception 'UNKNOWN_LEGAL_CAPABILITY'; end if;
  insert into public.legal_professional_profiles(principal_id,organization_id,public_display_name,bar_identifier_masked,declared_capabilities,accepting_matters)
  values(v_actor,p_organization_id,trim(p_display_name),trim(p_bar_identifier_masked),p_declared_capabilities,p_accepting_matters)
  on conflict(principal_id) do update set organization_id=excluded.organization_id,public_display_name=excluded.public_display_name,
    bar_identifier_masked=excluded.bar_identifier_masked,declared_capabilities=excluded.declared_capabilities,
    accepting_matters=excluded.accepting_matters,version=legal_professional_profiles.version+1;
  insert into public.market_professional_credentials(credential_id,principal_id,organization_id,authority,credential_type,identifier_ciphertext,identifier_masked,status,source_version)
  values(gen_random_uuid(),v_actor,p_organization_id,'CAAB','LAWYER_MEMBERSHIP',p_bar_identifier_ciphertext,p_bar_identifier_masked,'PENDING','OPERATOR_REVIEW_V1')
  on conflict(principal_id,authority,credential_type) do update set identifier_ciphertext=excluded.identifier_ciphertext,identifier_masked=excluded.identifier_masked,status='PENDING',checked_at=null,expires_at=null,evidence_ref=null,checked_by=null,version=market_professional_credentials.version+1;
  delete from public.market_professional_capabilities where principal_id=v_actor and vertical='LEGAL' and truth_state='DECLARED';
  insert into public.market_professional_capabilities(principal_id,organization_id,vertical,category_code,truth_state)
  select v_actor,p_organization_id,'LEGAL',c,'DECLARED' from unnest(p_declared_capabilities)c;
  v_result:=jsonb_build_object('principal_id',v_actor,'credential_status','PENDING','verified',false);
  perform market_private.record_command(p_idempotency_key,v_actor,'LEGAL_PROFESSIONAL',v_actor,'SUBMIT_PROFILE',0,jsonb_build_object('capability_count',cardinality(p_declared_capabilities)),v_result);
  insert into public.market_audit_events(actor_principal_id,organization_id,aggregate_type,aggregate_id,action,decision,reason_code)
  values(v_actor,p_organization_id,'LEGAL_PROFESSIONAL',v_actor,'SUBMIT_PROFILE','PENDING','SUPERVISED_VERIFICATION_REQUIRED');
  return v_result;
end $$;

create or replace function public.submit_legal_offer_v1(
  p_matter_id uuid,p_organization_id uuid,p_fee_model text,p_fee_amount_minor bigint,
  p_external_expenses_minor bigint,p_currency text,p_scope text,p_exclusions text,p_valid_until timestamptz,
  p_expected_matter_version bigint,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_matter public.legal_matters%rowtype; v_offer uuid:=gen_random_uuid(); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  if not market_private.is_org_member(p_organization_id,v_actor,array['OWNER','PARTNER','PROFESSIONAL']) then raise exception 'FIRM_ROLE_REQUIRED' using errcode='42501'; end if;
  if not exists(select 1 from public.market_professional_credentials c where c.principal_id=v_actor and c.authority='CAAB' and c.status='ACTIVE' and c.checked_at>=now()-interval '30 days' and (c.expires_at is null or c.expires_at>=now())) then raise exception 'ACTIVE_CAAB_REQUIRED' using errcode='42501'; end if;
  select * into v_matter from public.legal_matters where matter_id=p_matter_id for update;
  if not found then raise exception 'MATTER_NOT_FOUND'; end if;
  if v_matter.version<>p_expected_matter_version then raise exception 'VERSION_CONFLICT' using errcode='40001'; end if;
  if not exists(select 1 from public.legal_conflict_checks c where c.matter_id=p_matter_id and c.professional_principal_id=v_actor and c.decision='CLEAR' and c.expires_at>=now()) then raise exception 'CONFLICT_CLEARANCE_REQUIRED'; end if;
  if p_fee_model not in ('FIXED','HOURLY','PER_STAGE','RETAINER','TARIFF_BASED','QUOTE_AFTER_CONSULTATION') or p_fee_amount_minor<0 or p_external_expenses_minor<0 or p_currency not in ('CRC','USD') or p_valid_until<=now() or trim(p_scope)='' or trim(p_exclusions)='' then raise exception 'INVALID_LEGAL_OFFER'; end if;
  insert into public.legal_offers(offer_id,matter_id,organization_id,professional_principal_id,fee_model,fee_amount_minor,external_expenses_minor,currency,scope,exclusions,valid_until)
  values(v_offer,p_matter_id,p_organization_id,v_actor,p_fee_model,p_fee_amount_minor,p_external_expenses_minor,p_currency,trim(p_scope),trim(p_exclusions),p_valid_until);
  update public.legal_matters set state='OFFERED',version=version+1,updated_at=now() where matter_id=p_matter_id;
  v_result:=jsonb_build_object('offer_id',v_offer,'matter_id',p_matter_id,'state','SUBMITTED','matter_version',p_expected_matter_version+1,'offer_version',0);
  perform market_private.record_command(p_idempotency_key,v_actor,'LEGAL_MATTER',p_matter_id,'SUBMIT_OFFER',p_expected_matter_version,jsonb_build_object('offer_id',v_offer),v_result);
  return v_result;
end $$;

create or replace function public.ensure_legal_matter_communication_v1(p_matter_id uuid)
returns uuid language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_client uuid; v_professional uuid; v_conversation uuid;
begin
  select e.client_principal_id,e.professional_principal_id into v_client,v_professional from public.legal_engagements e where e.matter_id=p_matter_id and e.state in('ACTIVE','PAUSED','COMPLETED');
  if v_actor is null or v_client is null or v_actor not in(v_client,v_professional) then raise exception 'ENGAGED_PARTICIPANT_REQUIRED' using errcode='42501'; end if;
  insert into public.communication_conversations(kind,title,service_vertical,service_reference_id,created_by)
  values('SERVICE','Legal Vanguard','legal',p_matter_id,v_actor) on conflict do nothing;
  select id into v_conversation from public.communication_conversations where service_vertical='legal' and service_reference_id=p_matter_id;
  insert into public.communication_participants(conversation_id,principal_id,role) values
    (v_conversation,v_client,'CUSTOMER'),(v_conversation,v_professional,'SERVICE_PROVIDER')
  on conflict(conversation_id,principal_id) do nothing;
  insert into public.market_audit_events(actor_principal_id,aggregate_type,aggregate_id,action,decision)
  values(v_actor,'LEGAL_MATTER',p_matter_id,'ENSURE_ENCRYPTED_COMMUNICATION','ALLOWED');
  return v_conversation;
end $$;

create or replace function public.create_property_listing_v1(
  p_property_id uuid,p_brokerage_organization_id uuid,p_operation text,p_asking_amount_minor bigint,
  p_currency text,p_public_description text,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_asset public.property_assets%rowtype; v_id uuid:=gen_random_uuid(); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select * into v_asset from public.property_assets where property_id=p_property_id for update;
  if not found or v_asset.created_by<>v_actor then raise exception 'PROPERTY_OWNER_REQUIRED' using errcode='42501'; end if;
  if p_brokerage_organization_id is not null and not market_private.is_org_member(p_brokerage_organization_id,v_actor,array['OWNER','ADMIN','BROKER']) then raise exception 'BROKERAGE_ROLE_REQUIRED' using errcode='42501'; end if;
  if p_operation not in('SALE','RENT','RENT_TO_OWN','TEMPORARY','PRESALE','ASSIGNMENT') or p_asking_amount_minor<0 or p_currency not in('CRC','USD') or length(trim(p_public_description)) not between 20 and 5000 then raise exception 'INVALID_PROPERTY_LISTING'; end if;
  insert into public.property_listings(listing_id,property_id,seller_principal_id,brokerage_organization_id,operation,asking_amount_minor,currency,public_description,state)
  values(v_id,p_property_id,v_actor,p_brokerage_organization_id,p_operation,p_asking_amount_minor,p_currency,trim(p_public_description),case when p_operation='PRESALE' then 'COMPLIANCE_REVIEW' else 'DRAFT' end);
  v_result:=jsonb_build_object('listing_id',v_id,'state',case when p_operation='PRESALE' then 'COMPLIANCE_REVIEW' else 'DRAFT' end,'version',0);
  perform market_private.record_command(p_idempotency_key,v_actor,'PROPERTY_LISTING',v_id,'CREATE',0,jsonb_build_object('property_id',p_property_id,'operation',p_operation),v_result);
  return v_result;
end $$;

create or replace function public.create_property_inquiry_v1(
  p_listing_id uuid,p_message_ciphertext text,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_listing public.property_listings%rowtype; v_id uuid:=gen_random_uuid(); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select * into v_listing from public.property_listings where listing_id=p_listing_id and state='PUBLISHED';
  if not found then raise exception 'LISTING_NOT_OPEN'; end if;
  if v_listing.seller_principal_id=v_actor then raise exception 'SELF_INQUIRY_FORBIDDEN'; end if;
  if length(trim(p_message_ciphertext))<16 then raise exception 'ENCRYPTED_MESSAGE_REQUIRED'; end if;
  insert into public.property_inquiries(inquiry_id,listing_id,buyer_principal_id,message_ciphertext)
  values(v_id,p_listing_id,v_actor,p_message_ciphertext)
  on conflict(listing_id,buyer_principal_id) do update set message_ciphertext=excluded.message_ciphertext,state='OPEN'
  returning inquiry_id into v_id;
  v_result:=jsonb_build_object('inquiry_id',v_id,'state','OPEN');
  perform market_private.record_command(p_idempotency_key,v_actor,'PROPERTY_LISTING',p_listing_id,'CREATE_INQUIRY',v_listing.version,jsonb_build_object('inquiry_id',v_id),v_result);
  return v_result;
end $$;

create or replace function public.grant_property_exact_address_v1(
  p_property_id uuid,p_grantee_principal_id uuid,p_purpose text,p_expires_at timestamptz,p_expected_property_version bigint,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_asset public.property_assets%rowtype; v_id uuid:=gen_random_uuid(); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select * into v_asset from public.property_assets where property_id=p_property_id for update;
  if not found or v_asset.created_by<>v_actor then raise exception 'PROPERTY_OWNER_REQUIRED' using errcode='42501'; end if;
  if v_asset.version<>p_expected_property_version then raise exception 'VERSION_CONFLICT' using errcode='40001'; end if;
  if p_grantee_principal_id=v_actor or p_expires_at<=now() or p_expires_at>now()+interval '30 days' or trim(p_purpose)='' then raise exception 'INVALID_ADDRESS_GRANT'; end if;
  insert into public.property_address_grants(address_grant_id,property_id,grantee_principal_id,granted_by,purpose,expires_at)
  values(v_id,p_property_id,p_grantee_principal_id,v_actor,trim(p_purpose),p_expires_at)
  on conflict(property_id,grantee_principal_id,purpose) do update set granted_at=now(),expires_at=excluded.expires_at,revoked_at=null
  returning address_grant_id into v_id;
  update public.property_assets set version=version+1,updated_at=now() where property_id=p_property_id;
  v_result:=jsonb_build_object('address_grant_id',v_id,'property_id',p_property_id,'version',p_expected_property_version+1);
  perform market_private.record_command(p_idempotency_key,v_actor,'PROPERTY',p_property_id,'GRANT_EXACT_ADDRESS',p_expected_property_version,jsonb_build_object('grantee_principal_id',p_grantee_principal_id,'purpose',p_purpose),v_result);
  return v_result;
end $$;

create or replace function public.create_fuel_station_v1(
  p_organization_id uuid,p_name text,p_aresep_station_ref text,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_id uuid:=gen_random_uuid(); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  if not market_private.is_org_member(p_organization_id,v_actor,array['OWNER','ADMIN','REGIONAL_MANAGER']) then raise exception 'FUEL_ORGANIZATION_ROLE_REQUIRED' using errcode='42501'; end if;
  if not exists(select 1 from public.market_organizations o where o.organization_id=p_organization_id and o.kind in('FUEL_NETWORK','FUEL_STATION') and o.status='ACTIVE') then raise exception 'ACTIVE_FUEL_ORGANIZATION_REQUIRED'; end if;
  if length(trim(p_name)) not between 2 and 160 then raise exception 'INVALID_STATION'; end if;
  insert into public.fuel_stations(station_id,organization_id,name,aresep_station_ref,aresep_truth_state)
  values(v_id,p_organization_id,trim(p_name),nullif(trim(p_aresep_station_ref),''),'UNKNOWN');
  v_result:=jsonb_build_object('station_id',v_id,'aresep_truth_state','UNKNOWN','version',0);
  perform market_private.record_command(p_idempotency_key,v_actor,'FUEL_STATION',v_id,'CREATE',0,jsonb_build_object('organization_id',p_organization_id),v_result);
  return v_result;
end $$;

create or replace function public.create_fuel_campaign_draft_v1(
  p_organization_id uuid,p_name text,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_id uuid:=gen_random_uuid(); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  if not market_private.is_org_member(p_organization_id,v_actor,array['OWNER','ADMIN','MARKETING_MANAGER']) then raise exception 'CAMPAIGN_ROLE_REQUIRED' using errcode='42501'; end if;
  if length(trim(p_name)) not between 2 and 160 then raise exception 'INVALID_CAMPAIGN_NAME'; end if;
  insert into public.fuel_campaigns(campaign_id,organization_id,name,created_by) values(v_id,p_organization_id,trim(p_name),v_actor);
  v_result:=jsonb_build_object('campaign_id',v_id,'status','DRAFT','version',0);
  perform market_private.record_command(p_idempotency_key,v_actor,'FUEL_CAMPAIGN',v_id,'CREATE',0,'{}',v_result);
  return v_result;
end $$;

create or replace function public.create_fuel_campaign_version_v1(
  p_campaign_id uuid,p_starts_at timestamptz,p_ends_at timestamptz,p_qualifying_spend_minor bigint,
  p_issue_policy text,p_max_per_transaction integer,p_eligible_station_ids uuid[],p_benefit_type text,
  p_benefit_payload jsonb,p_eligibility text,p_restrictions text,p_redemption_procedure text,
  p_terms_version integer,p_terms_hash text,p_regulatory_approval_ref text,p_expected_campaign_version bigint,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_campaign public.fuel_campaigns%rowtype; v_id uuid:=gen_random_uuid(); v_version integer; v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select * into v_campaign from public.fuel_campaigns where campaign_id=p_campaign_id for update;
  if not found then raise exception 'CAMPAIGN_NOT_FOUND'; end if;
  if not market_private.is_org_member(v_campaign.organization_id,v_actor,array['OWNER','ADMIN','MARKETING_MANAGER']) then raise exception 'CAMPAIGN_ROLE_REQUIRED' using errcode='42501'; end if;
  if v_campaign.version<>p_expected_campaign_version then raise exception 'VERSION_CONFLICT' using errcode='40001'; end if;
  if p_ends_at<=p_starts_at or p_qualifying_spend_minor<=0 or trim(p_eligibility)='' or trim(p_restrictions)='' or trim(p_redemption_procedure)='' or p_terms_version<=0 or p_terms_hash!~'^[a-f0-9]{64}$' then raise exception 'INCOMPLETE_CAMPAIGN_TERMS'; end if;
  if p_benefit_type='FUEL_PRICE_CREDIT' and nullif(trim(p_regulatory_approval_ref),'') is null then raise exception 'REGULATORY_APPROVAL_REQUIRED'; end if;
  if exists(select 1 from unnest(coalesce(p_eligible_station_ids,'{}')) s where not exists(select 1 from public.fuel_stations fs where fs.station_id=s and fs.organization_id=v_campaign.organization_id and fs.active)) then raise exception 'INVALID_ELIGIBLE_STATION'; end if;
  select coalesce(max(version),0)+1 into v_version from public.fuel_campaign_versions where campaign_id=p_campaign_id;
  insert into public.fuel_campaign_versions(campaign_version_id,campaign_id,version,starts_at,ends_at,qualifying_spend_minor,issue_policy,max_per_transaction,eligible_station_ids,benefit_type,benefit_payload,eligibility,restrictions,redemption_procedure,terms_version,terms_hash,regulatory_approval_ref)
  values(v_id,p_campaign_id,v_version,p_starts_at,p_ends_at,p_qualifying_spend_minor,p_issue_policy,p_max_per_transaction,coalesce(p_eligible_station_ids,'{}'),p_benefit_type,p_benefit_payload,p_eligibility,p_restrictions,p_redemption_procedure,p_terms_version,p_terms_hash,p_regulatory_approval_ref);
  update public.fuel_campaigns set version=version+1 where campaign_id=p_campaign_id;
  v_result:=jsonb_build_object('campaign_version_id',v_id,'campaign_id',p_campaign_id,'terms_version',p_terms_version,'version',p_expected_campaign_version+1,'state','DRAFT');
  perform market_private.record_command(p_idempotency_key,v_actor,'FUEL_CAMPAIGN',p_campaign_id,'CREATE_VERSION',p_expected_campaign_version,jsonb_build_object('campaign_version_id',v_id,'terms_hash',p_terms_hash),v_result);
  return v_result;
end $$;

create or replace function public.record_fuel_customer_consent_v1(
  p_consent_version text,p_preferred_station_id uuid,p_communication_preferences jsonb,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  if trim(p_consent_version)='' or jsonb_typeof(p_communication_preferences)<>'object' then raise exception 'INVALID_CONSENT'; end if;
  insert into public.fuel_customer_profiles(customer_id,consent_version,consented_at,preferred_station_id,communication_preferences)
  values(v_actor,p_consent_version,now(),p_preferred_station_id,p_communication_preferences)
  on conflict(customer_id) do update set consent_version=excluded.consent_version,consented_at=excluded.consented_at,preferred_station_id=excluded.preferred_station_id,communication_preferences=excluded.communication_preferences,version=fuel_customer_profiles.version+1;
  v_result:=jsonb_build_object('customer_id',v_actor,'consent_version',p_consent_version);
  perform market_private.record_command(p_idempotency_key,v_actor,'FUEL_CUSTOMER',v_actor,'RECORD_CONSENT',0,jsonb_build_object('consent_version',p_consent_version),v_result);
  return v_result;
end $$;

create or replace function public.issue_fuel_purchase_claim_qr_v1(
  p_purchase_id uuid,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_purchase public.fuel_purchases%rowtype; v_raw text; v_id uuid:=gen_random_uuid(); v_exp timestamptz:=now()+interval '10 minutes'; v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select * into v_purchase from public.fuel_purchases where purchase_id=p_purchase_id for update;
  if not found or v_purchase.status<>'SETTLED' then raise exception 'SETTLED_PURCHASE_REQUIRED'; end if;
  if not market_private.is_org_member(v_purchase.organization_id,v_actor,array['OWNER','ADMIN','STATION_MANAGER','SHIFT_SUPERVISOR','CASHIER','ATTENDANT']) then raise exception 'STATION_ROLE_REQUIRED' using errcode='42501'; end if;
  if v_purchase.customer_id is not null then raise exception 'PURCHASE_ALREADY_ASSIGNED'; end if;
  v_raw:=replace(gen_random_uuid()::text||gen_random_uuid()::text,'-','');
  insert into public.fuel_purchase_claim_tokens(claim_token_id,purchase_id,token_hash,issued_by,expires_at)
  values(v_id,p_purchase_id,encode(extensions.digest(v_raw,'sha256'),'hex'),v_actor,v_exp)
  on conflict(purchase_id) do update set token_hash=excluded.token_hash,issued_by=excluded.issued_by,issued_at=now(),expires_at=excluded.expires_at,claimed_by=null,claimed_at=null,revoked_at=null
  returning claim_token_id into v_id;
  v_result:=jsonb_build_object('claim_token_id',v_id,'url','https://elysium-vanguard.app/q/'||v_raw,'expires_at',v_exp);
  perform market_private.record_command(p_idempotency_key,v_actor,'FUEL_PURCHASE',p_purchase_id,'ISSUE_CLAIM_QR',v_purchase.version,'{}',v_result);
  return v_result;
end $$;

create or replace function public.claim_fuel_purchase_v1(
  p_opaque_token text,p_campaign_version_id uuid,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_claim public.fuel_purchase_claim_tokens%rowtype; v_purchase public.fuel_purchases%rowtype; v_rewards jsonb; v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select * into v_claim from public.fuel_purchase_claim_tokens where token_hash=encode(extensions.digest(p_opaque_token,'sha256'),'hex') for update;
  if not found then raise exception 'PURCHASE_QR_NOT_FOUND' using errcode='P0002'; end if;
  if v_claim.revoked_at is not null or v_claim.claimed_at is not null then raise exception 'PURCHASE_QR_REPLAY_REJECTED'; end if;
  if v_claim.expires_at<now() then raise exception 'PURCHASE_QR_EXPIRED'; end if;
  select * into v_purchase from public.fuel_purchases where purchase_id=v_claim.purchase_id for update;
  if v_purchase.status<>'SETTLED' or v_purchase.customer_id is not null then raise exception 'PURCHASE_NOT_CLAIMABLE'; end if;
  if p_campaign_version_id is null then
    select cv.campaign_version_id into p_campaign_version_id
    from public.fuel_campaign_versions cv join public.fuel_campaigns c using(campaign_id)
    where c.organization_id=v_purchase.organization_id and c.status='ACTIVE' and cv.published_at is not null
      and now() between cv.starts_at and cv.ends_at
      and (cardinality(cv.eligible_station_ids)=0 or v_purchase.station_id=any(cv.eligible_station_ids))
    order by cv.version desc limit 1;
  end if;
  if p_campaign_version_id is null then raise exception 'NO_ELIGIBLE_CAMPAIGN'; end if;
  update public.fuel_purchases set customer_id=v_actor,version=version+1 where purchase_id=v_purchase.purchase_id;
  update public.fuel_purchase_claim_tokens set claimed_by=v_actor,claimed_at=now() where claim_token_id=v_claim.claim_token_id;
  v_rewards:=public.issue_fuel_rewards_v1(v_purchase.purchase_id,p_campaign_version_id,v_purchase.version+1,gen_random_uuid());
  v_result:=jsonb_build_object('purchase_id',v_purchase.purchase_id,'status','CLAIMED','rewards',v_rewards);
  perform market_private.record_command(p_idempotency_key,v_actor,'FUEL_PURCHASE',v_purchase.purchase_id,'CLAIM_PURCHASE',v_purchase.version,jsonb_build_object('campaign_version_id',p_campaign_version_id),v_result);
  return v_result;
end $$;

-- A void/refund never erases used rewards; it opens an explicit adjustment.
create or replace function market_private.record_redeemed_reward_adjustment(p_purchase_id uuid,p_reason text)
returns integer language plpgsql security definer set search_path='' as $$
declare v_count integer;
begin
  insert into public.fuel_reward_adjustments(purchase_id,customer_id,adjustment_type,units,reason_code)
  select p_purchase_id,p.customer_id,'MANUAL_REVIEW',count(*),p_reason
  from public.fuel_purchases p join public.fuel_coupons c on c.issued_from_purchase_id=p.purchase_id
  where p.purchase_id=p_purchase_id and c.state='REDEEMED' group by p.customer_id
  returning units into v_count;
  return coalesce(v_count,0);
end $$;

create or replace function public.refund_fuel_purchase_v1(
  p_purchase_id uuid,p_reason_code text,p_expected_version bigint,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_purchase public.fuel_purchases%rowtype; v_review integer; v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select * into v_purchase from public.fuel_purchases where purchase_id=p_purchase_id for update;
  if not found then raise exception 'PURCHASE_NOT_FOUND'; end if;
  if not market_private.is_org_member(v_purchase.organization_id,v_actor,array['OWNER','ADMIN','STATION_MANAGER','FINANCE']) then raise exception 'REFUND_ROLE_REQUIRED' using errcode='42501'; end if;
  if v_purchase.version<>p_expected_version then raise exception 'VERSION_CONFLICT' using errcode='40001'; end if;
  if v_purchase.status<>'SETTLED' then raise exception 'PURCHASE_NOT_REFUNDABLE'; end if;
  update public.fuel_purchases set status='REFUNDED',version=version+1 where purchase_id=p_purchase_id;
  update public.fuel_coupons set state='REVOKED',version=version+1 where issued_from_purchase_id=p_purchase_id and state in('ISSUED','CLAIMED','RESERVED');
  v_review:=market_private.record_redeemed_reward_adjustment(p_purchase_id,p_reason_code);
  v_result:=jsonb_build_object('purchase_id',p_purchase_id,'status','REFUNDED','manual_review_units',v_review,'version',p_expected_version+1);
  perform market_private.record_command(p_idempotency_key,v_actor,'FUEL_PURCHASE',p_purchase_id,'REFUND',p_expected_version,jsonb_build_object('reason_code',p_reason_code),v_result);
  insert into public.market_audit_events(actor_principal_id,organization_id,aggregate_type,aggregate_id,action,decision,reason_code,metadata)
  values(v_actor,v_purchase.organization_id,'FUEL_PURCHASE',p_purchase_id,'REFUND','ALLOWED',p_reason_code,jsonb_build_object('manual_review_units',v_review));
  return v_result;
end $$;

revoke all on function public.submit_legal_professional_profile_v1(uuid,text,text,text,text[],boolean,uuid) from public,anon;
revoke all on function public.submit_legal_offer_v1(uuid,uuid,text,bigint,bigint,text,text,text,timestamptz,bigint,uuid) from public,anon;
revoke all on function public.ensure_legal_matter_communication_v1(uuid) from public,anon;
revoke all on function public.create_property_listing_v1(uuid,uuid,text,bigint,text,text,uuid) from public,anon;
revoke all on function public.create_property_inquiry_v1(uuid,text,uuid) from public,anon;
revoke all on function public.grant_property_exact_address_v1(uuid,uuid,text,timestamptz,bigint,uuid) from public,anon;
revoke all on function public.create_fuel_station_v1(uuid,text,text,uuid) from public,anon;
revoke all on function public.create_fuel_campaign_draft_v1(uuid,text,uuid) from public,anon;
revoke all on function public.create_fuel_campaign_version_v1(uuid,timestamptz,timestamptz,bigint,text,integer,uuid[],text,jsonb,text,text,text,integer,text,text,bigint,uuid) from public,anon;
revoke all on function public.record_fuel_customer_consent_v1(text,uuid,jsonb,uuid) from public,anon;
revoke all on function public.issue_fuel_purchase_claim_qr_v1(uuid,uuid) from public,anon;
revoke all on function public.claim_fuel_purchase_v1(text,uuid,uuid) from public,anon;
revoke all on function public.refund_fuel_purchase_v1(uuid,text,bigint,uuid) from public,anon;
grant execute on function public.submit_legal_professional_profile_v1(uuid,text,text,text,text[],boolean,uuid) to authenticated;
grant execute on function public.submit_legal_offer_v1(uuid,uuid,text,bigint,bigint,text,text,text,timestamptz,bigint,uuid) to authenticated;
grant execute on function public.ensure_legal_matter_communication_v1(uuid) to authenticated;
grant execute on function public.create_property_listing_v1(uuid,uuid,text,bigint,text,text,uuid) to authenticated;
grant execute on function public.create_property_inquiry_v1(uuid,text,uuid) to authenticated;
grant execute on function public.grant_property_exact_address_v1(uuid,uuid,text,timestamptz,bigint,uuid) to authenticated;
grant execute on function public.create_fuel_station_v1(uuid,text,text,uuid) to authenticated;
grant execute on function public.create_fuel_campaign_draft_v1(uuid,text,uuid) to authenticated;
grant execute on function public.create_fuel_campaign_version_v1(uuid,timestamptz,timestamptz,bigint,text,integer,uuid[],text,jsonb,text,text,text,integer,text,text,bigint,uuid) to authenticated;
grant execute on function public.record_fuel_customer_consent_v1(text,uuid,jsonb,uuid) to authenticated;
grant execute on function public.issue_fuel_purchase_claim_qr_v1(uuid,uuid) to authenticated;
grant execute on function public.claim_fuel_purchase_v1(text,uuid,uuid) to authenticated;
grant execute on function public.refund_fuel_purchase_v1(uuid,text,bigint,uuid) to authenticated;
