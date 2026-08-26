-- Authoritative command surface for Market OS. Direct writes remain revoked.

create or replace function market_private.command_replay(p_key uuid, p_actor uuid)
returns jsonb language sql stable security definer set search_path = '' as $$
  select result from public.market_commands where idempotency_key = p_key and actor_principal_id = p_actor
$$;

create or replace function market_private.record_command(
  p_key uuid, p_actor uuid, p_aggregate_type text, p_aggregate_id uuid,
  p_command_type text, p_expected_version bigint, p_payload jsonb, p_result jsonb
) returns void language plpgsql security definer set search_path = '' as $$
begin
  insert into public.market_commands(
    command_id,idempotency_key,actor_principal_id,aggregate_type,aggregate_id,command_type,
    expected_version,canonical_digest,payload,payload_version,status,result
  ) values (
    gen_random_uuid(),p_key,p_actor,p_aggregate_type,p_aggregate_id,p_command_type,p_expected_version,
    encode(extensions.digest(p_aggregate_type || chr(31) || p_aggregate_id::text || chr(31) || p_command_type || chr(31) ||
      p_expected_version::text || chr(31) || p_actor::text || chr(31) || p_payload::text,'sha256'),'hex'),
    p_payload,1,'APPLIED',p_result
  );
end $$;

create or replace function public.create_market_organization_v1(
  p_legal_name text, p_commercial_name text, p_kind text, p_jurisdiction text, p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid := (select auth.uid()); v_id uuid := gen_random_uuid(); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay := market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  if p_kind not in ('SOLO_PROFESSIONAL','LAW_FIRM','BROKERAGE','LANDLORD','FUEL_NETWORK','FUEL_STATION') then raise exception 'INVALID_ORGANIZATION_KIND'; end if;
  insert into public.market_organizations(organization_id,legal_name,commercial_name,kind,jurisdiction,created_by)
  values(v_id,trim(p_legal_name),trim(p_commercial_name),p_kind,p_jurisdiction,v_actor);
  insert into public.market_organization_members(organization_id,principal_id,roles) values(v_id,v_actor,array['OWNER','ADMIN']);
  v_result := jsonb_build_object('organization_id',v_id,'version',0);
  perform market_private.record_command(p_idempotency_key,v_actor,'ORGANIZATION',v_id,'CREATE',0,
    jsonb_build_object('kind',p_kind,'jurisdiction',p_jurisdiction),v_result);
  insert into public.market_audit_events(actor_principal_id,organization_id,aggregate_type,aggregate_id,action,decision)
  values(v_actor,v_id,'ORGANIZATION',v_id,'CREATE','ALLOWED');
  return v_result;
end $$;

create or replace function public.create_legal_matter_v1(
  p_category_code text, p_subcategory_code text, p_human_summary text,
  p_privileged_detail_ciphertext text, p_jurisdiction_code text, p_urgency text,
  p_parties jsonb, p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid := (select auth.uid()); v_id uuid := gen_random_uuid(); v_party jsonb; v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay := market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  if length(trim(p_human_summary)) not between 8 and 2000 then raise exception 'INVALID_SUMMARY'; end if;
  if p_urgency not in ('NORMAL','URGENT','IMMEDIATE_RISK') then raise exception 'INVALID_URGENCY'; end if;
  if not exists(
    select 1 from public.market_service_categories c join public.market_taxonomy_versions t using(taxonomy_version_id)
    where t.vertical='LEGAL' and t.published_at is not null and c.active and c.code=p_category_code
  ) then raise exception 'UNKNOWN_LEGAL_CATEGORY'; end if;
  insert into public.legal_matters(matter_id,client_principal_id,category_code,subcategory_code,human_summary,
    privileged_detail_ciphertext,jurisdiction_code,urgency,disclosure_level)
  values(v_id,v_actor,p_category_code,p_subcategory_code,trim(p_human_summary),p_privileged_detail_ciphertext,
    p_jurisdiction_code,p_urgency,case when jsonb_array_length(coalesce(p_parties,'[]'))>0 then 'PARTY_NAMES_ONLY' else 'TRIAGE_ONLY' end);
  for v_party in select * from jsonb_array_elements(coalesce(p_parties,'[]'::jsonb)) loop
    if coalesce(v_party->>'role','')='' or coalesce(v_party->>'display_name_ciphertext','')='' or
       coalesce(v_party->>'conflict_fingerprint','') !~ '^[a-f0-9]{64}$' then raise exception 'INVALID_CONFLICT_PARTY'; end if;
    insert into public.legal_matter_parties(matter_id,role,display_name_ciphertext,conflict_fingerprint)
    values(v_id,v_party->>'role',v_party->>'display_name_ciphertext',v_party->>'conflict_fingerprint');
  end loop;
  v_result := jsonb_build_object('matter_id',v_id,'state','CONFLICT_SCREENING','version',0);
  perform market_private.record_command(p_idempotency_key,v_actor,'LEGAL_MATTER',v_id,'CREATE',0,
    jsonb_build_object('category_code',p_category_code,'jurisdiction_code',p_jurisdiction_code),v_result);
  insert into public.market_audit_events(actor_principal_id,aggregate_type,aggregate_id,action,decision,metadata)
  values(v_actor,'LEGAL_MATTER',v_id,'CREATE','ALLOWED',jsonb_build_object('disclosure','MINIMUM'));
  return v_result;
end $$;

create or replace function public.get_legal_conflict_packet_v1(p_matter_id uuid)
returns table(party_role text, conflict_fingerprint text)
language plpgsql security definer set search_path = '' as $$
declare v_actor uuid := (select auth.uid());
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  if not exists(
    select 1 from public.market_professional_credentials c where c.principal_id=v_actor
      and c.authority='CAAB' and c.status='ACTIVE' and c.checked_at >= now()-interval '30 days'
      and (c.expires_at is null or c.expires_at>=now())
  ) then raise exception 'ACTIVE_CAAB_REQUIRED' using errcode='42501'; end if;
  insert into public.market_audit_events(actor_principal_id,aggregate_type,aggregate_id,action,decision,metadata)
  values(v_actor,'LEGAL_MATTER',p_matter_id,'READ_CONFLICT_PACKET','ALLOWED',jsonb_build_object('fields','fingerprints_only'));
  return query select p.role,p.conflict_fingerprint from public.legal_matter_parties p where p.matter_id=p_matter_id;
end $$;

create or replace function public.record_legal_conflict_check_v1(
  p_matter_id uuid, p_organization_id uuid, p_decision text, p_notes_ciphertext text,
  p_expected_matter_version bigint, p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid := (select auth.uid()); v_matter public.legal_matters%rowtype; v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  if p_decision not in ('CLEAR','POSSIBLE_CONFLICT','CONFLICT') then raise exception 'INVALID_CONFLICT_DECISION'; end if;
  if not market_private.is_org_member(p_organization_id,v_actor,array['OWNER','PARTNER','PROFESSIONAL','COMPLIANCE']) then raise exception 'FIRM_ROLE_REQUIRED' using errcode='42501'; end if;
  if not exists(select 1 from public.market_professional_credentials c where c.principal_id=v_actor and c.authority='CAAB' and c.status='ACTIVE' and c.checked_at>=now()-interval '30 days' and (c.expires_at is null or c.expires_at>=now())) then raise exception 'ACTIVE_CAAB_REQUIRED' using errcode='42501'; end if;
  select * into v_matter from public.legal_matters where matter_id=p_matter_id for update;
  if not found then raise exception 'MATTER_NOT_FOUND'; end if;
  if v_matter.version<>p_expected_matter_version then raise exception 'VERSION_CONFLICT' using errcode='40001'; end if;
  insert into public.legal_conflict_checks(matter_id,professional_principal_id,organization_id,decision,notes_ciphertext,expires_at)
  values(p_matter_id,v_actor,p_organization_id,p_decision,p_notes_ciphertext,now()+interval '30 days')
  on conflict(matter_id,professional_principal_id) do update set decision=excluded.decision,notes_ciphertext=excluded.notes_ciphertext,checked_at=now(),expires_at=excluded.expires_at;
  update public.legal_matters set state=case when p_decision='CLEAR' then 'MATCHING' else 'CONFLICT_SCREENING' end,
    version=version+1,updated_at=now() where matter_id=p_matter_id;
  v_result:=jsonb_build_object('matter_id',p_matter_id,'decision',p_decision,'version',p_expected_matter_version+1);
  perform market_private.record_command(p_idempotency_key,v_actor,'LEGAL_MATTER',p_matter_id,'RECORD_CONFLICT_CHECK',p_expected_matter_version,jsonb_build_object('decision',p_decision),v_result);
  insert into public.market_audit_events(actor_principal_id,organization_id,aggregate_type,aggregate_id,action,decision)
  values(v_actor,p_organization_id,'LEGAL_MATTER',p_matter_id,'CONFLICT_CHECK',p_decision);
  return v_result;
end $$;

create or replace function public.create_property_asset_v1(
  p_property_type_code text,p_approximate_zone text,p_registry_number_ciphertext text,
  p_registry_number_masked text,p_cadastral_plan_ciphertext text,p_cadastral_plan_masked text,
  p_exact_address_ciphertext text,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=(select auth.uid()); v_id uuid:=gen_random_uuid(); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  if trim(p_property_type_code)='' or trim(p_approximate_zone)='' then raise exception 'PROPERTY_IDENTITY_REQUIRED'; end if;
  insert into public.property_assets(property_id,created_by,registry_number_ciphertext,registry_number_masked,cadastral_plan_ciphertext,cadastral_plan_masked,property_type_code,approximate_zone,exact_address_ciphertext)
  values(v_id,v_actor,p_registry_number_ciphertext,p_registry_number_masked,p_cadastral_plan_ciphertext,p_cadastral_plan_masked,p_property_type_code,trim(p_approximate_zone),p_exact_address_ciphertext);
  insert into public.property_proofs(property_id,claim_key,truth_state,authority)
  values(v_id,'registered_owner','SELLER_DECLARED','SELLER'),(v_id,'registry_identity',case when p_registry_number_ciphertext is null then 'UNKNOWN' else 'DOCUMENT_OBSERVED' end,'SELLER');
  v_result:=jsonb_build_object('property_id',v_id,'version',0,'ownership_truth','SELLER_DECLARED');
  perform market_private.record_command(p_idempotency_key,v_actor,'PROPERTY',v_id,'CREATE',0,jsonb_build_object('property_type_code',p_property_type_code),v_result);
  return v_result;
end $$;

create or replace function public.publish_property_listing_v1(
  p_listing_id uuid,p_expected_version bigint,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=(select auth.uid()); v_listing public.property_listings%rowtype; v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select * into v_listing from public.property_listings where listing_id=p_listing_id for update;
  if not found then raise exception 'LISTING_NOT_FOUND'; end if;
  if v_listing.seller_principal_id<>v_actor then raise exception 'SELLER_REQUIRED' using errcode='42501'; end if;
  if v_listing.version<>p_expected_version then raise exception 'VERSION_CONFLICT' using errcode='40001'; end if;
  if v_listing.operation='PRESALE' and v_listing.compliance_approved_at is null then raise exception 'PRESALE_COMPLIANCE_REQUIRED'; end if;
  update public.property_listings set state='PUBLISHED',published_at=now(),version=version+1 where listing_id=p_listing_id;
  v_result:=jsonb_build_object('listing_id',p_listing_id,'state','PUBLISHED','version',p_expected_version+1);
  perform market_private.record_command(p_idempotency_key,v_actor,'PROPERTY_LISTING',p_listing_id,'PUBLISH',p_expected_version,'{}',v_result);
  return v_result;
end $$;

create or replace function public.publish_fuel_campaign_version_v1(
  p_campaign_version_id uuid,p_expected_campaign_version bigint,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=(select auth.uid()); v_cv public.fuel_campaign_versions%rowtype; v_campaign public.fuel_campaigns%rowtype; v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select * into v_cv from public.fuel_campaign_versions where campaign_version_id=p_campaign_version_id for update;
  if not found then raise exception 'CAMPAIGN_VERSION_NOT_FOUND'; end if;
  select * into v_campaign from public.fuel_campaigns where campaign_id=v_cv.campaign_id for update;
  if not market_private.is_org_member(v_campaign.organization_id,v_actor,array['OWNER','ADMIN','MARKETING_MANAGER','COMPLIANCE']) then raise exception 'CAMPAIGN_ROLE_REQUIRED' using errcode='42501'; end if;
  if v_campaign.version<>p_expected_campaign_version then raise exception 'VERSION_CONFLICT' using errcode='40001'; end if;
  if v_cv.published_at is not null then raise exception 'ALREADY_PUBLISHED'; end if;
  if trim(v_cv.eligibility)='' or trim(v_cv.restrictions)='' or trim(v_cv.redemption_procedure)='' or v_cv.terms_hash !~ '^[a-f0-9]{64}$' then raise exception 'INCOMPLETE_CAMPAIGN_TERMS'; end if;
  update public.fuel_campaign_versions set published_at=now() where campaign_version_id=p_campaign_version_id;
  update public.fuel_campaigns set status='ACTIVE',version=version+1 where campaign_id=v_campaign.campaign_id;
  v_result:=jsonb_build_object('campaign_version_id',p_campaign_version_id,'status','ACTIVE','version',p_expected_campaign_version+1);
  perform market_private.record_command(p_idempotency_key,v_actor,'FUEL_CAMPAIGN',v_campaign.campaign_id,'PUBLISH_VERSION',p_expected_campaign_version,jsonb_build_object('campaign_version_id',p_campaign_version_id),v_result);
  return v_result;
end $$;

create or replace function public.record_fuel_purchase_v1(
  p_station_id uuid,p_terminal_ref text,p_shift_ref text,p_customer_id uuid,p_occurred_at timestamptz,
  p_total_minor bigint,p_payment_method text,p_source text,p_receipt_hash text,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=(select auth.uid()); v_org uuid; v_id uuid:=gen_random_uuid(); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select organization_id into v_org from public.fuel_stations where station_id=p_station_id and active;
  if v_org is null then raise exception 'STATION_NOT_ACTIVE'; end if;
  if not market_private.is_org_member(v_org,v_actor,array['OWNER','ADMIN','STATION_MANAGER','SHIFT_SUPERVISOR','CASHIER','ATTENDANT']) then raise exception 'STATION_ROLE_REQUIRED' using errcode='42501'; end if;
  if p_source not in ('POS_AUTHORITATIVE','ERP_IMPORTED','RECEIPT_VERIFIED','STAFF_DECLARED','CUSTOMER_DECLARED') then raise exception 'INVALID_PURCHASE_SOURCE'; end if;
  if p_total_minor<0 then raise exception 'INVALID_TOTAL'; end if;
  insert into public.fuel_purchases(purchase_id,station_id,organization_id,terminal_ref,shift_ref,cashier_principal_id,customer_id,occurred_at,total_minor,payment_method,source,truth_state,receipt_hash,status)
  values(v_id,p_station_id,v_org,p_terminal_ref,p_shift_ref,v_actor,p_customer_id,p_occurred_at,p_total_minor,p_payment_method,p_source,p_source,p_receipt_hash,
    case when p_source in ('POS_AUTHORITATIVE','ERP_IMPORTED','RECEIPT_VERIFIED') then 'SETTLED' else 'PENDING' end);
  v_result:=jsonb_build_object('purchase_id',v_id,'status',case when p_source in ('POS_AUTHORITATIVE','ERP_IMPORTED','RECEIPT_VERIFIED') then 'SETTLED' else 'PENDING' end,'version',0);
  perform market_private.record_command(p_idempotency_key,v_actor,'FUEL_PURCHASE',v_id,'RECORD',0,jsonb_build_object('station_id',p_station_id,'source',p_source,'total_minor',p_total_minor),v_result);
  return v_result;
end $$;

create or replace function public.accept_legal_offer_v1(
  p_offer_id uuid,p_expected_matter_version bigint,p_expected_offer_version bigint,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=(select auth.uid()); v_offer public.legal_offers%rowtype; v_matter public.legal_matters%rowtype;
  v_id uuid:=gen_random_uuid(); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select * into v_offer from public.legal_offers where offer_id=p_offer_id for update;
  if not found then raise exception 'OFFER_NOT_FOUND'; end if;
  select * into v_matter from public.legal_matters where matter_id=v_offer.matter_id for update;
  if v_matter.client_principal_id<>v_actor then raise exception 'CLIENT_REQUIRED' using errcode='42501'; end if;
  if v_matter.version<>p_expected_matter_version or v_offer.version<>p_expected_offer_version then raise exception 'VERSION_CONFLICT' using errcode='40001'; end if;
  if v_offer.state<>'SUBMITTED' or v_offer.valid_until<now() then raise exception 'OFFER_NOT_ACCEPTABLE'; end if;
  if not exists(select 1 from public.legal_conflict_checks c where c.matter_id=v_matter.matter_id and c.professional_principal_id=v_offer.professional_principal_id and c.decision='CLEAR' and c.expires_at>=now()) then raise exception 'CONFLICT_CLEARANCE_REQUIRED'; end if;
  if not exists(select 1 from public.market_professional_credentials c where c.principal_id=v_offer.professional_principal_id and c.authority='CAAB' and c.status='ACTIVE' and c.checked_at>=now()-interval '30 days' and (c.expires_at is null or c.expires_at>=now())) then raise exception 'ACTIVE_CAAB_REQUIRED'; end if;
  insert into public.legal_engagements(engagement_id,matter_id,accepted_offer_id,client_principal_id,organization_id,professional_principal_id)
  values(v_id,v_matter.matter_id,p_offer_id,v_actor,v_offer.organization_id,v_offer.professional_principal_id);
  update public.legal_offers set state=case when offer_id=p_offer_id then 'ACCEPTED' else 'REJECTED' end,version=version+1 where matter_id=v_matter.matter_id and state='SUBMITTED';
  update public.legal_matters set state='ENGAGED',disclosure_level='ENGAGED',version=version+1,updated_at=now() where matter_id=v_matter.matter_id;
  v_result:=jsonb_build_object('engagement_id',v_id,'matter_id',v_matter.matter_id,'version',p_expected_matter_version+1);
  perform market_private.record_command(p_idempotency_key,v_actor,'LEGAL_MATTER',v_matter.matter_id,'ACCEPT_OFFER',p_expected_matter_version,jsonb_build_object('offer_id',p_offer_id),v_result);
  return v_result;
end $$;

create or replace function public.submit_property_offer_v1(
  p_listing_id uuid,p_amount_minor bigint,p_currency text,p_conditions text,p_expected_listing_version bigint,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=(select auth.uid()); v_listing public.property_listings%rowtype; v_id uuid:=gen_random_uuid(); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select * into v_listing from public.property_listings where listing_id=p_listing_id for share;
  if not found or v_listing.state<>'PUBLISHED' then raise exception 'LISTING_NOT_OPEN'; end if;
  if v_listing.version<>p_expected_listing_version then raise exception 'VERSION_CONFLICT' using errcode='40001'; end if;
  if v_listing.seller_principal_id=v_actor then raise exception 'SELF_OFFER_FORBIDDEN'; end if;
  if p_amount_minor<0 or p_currency not in ('CRC','USD') or trim(p_conditions)='' then raise exception 'INVALID_OFFER'; end if;
  insert into public.property_offers(offer_id,listing_id,buyer_principal_id,amount_minor,currency,conditions)
  values(v_id,p_listing_id,v_actor,p_amount_minor,p_currency,trim(p_conditions));
  v_result:=jsonb_build_object('offer_id',v_id,'listing_id',p_listing_id,'state','SUBMITTED','version',0);
  perform market_private.record_command(p_idempotency_key,v_actor,'PROPERTY_OFFER',v_id,'SUBMIT',0,jsonb_build_object('listing_id',p_listing_id,'amount_minor',p_amount_minor,'currency',p_currency),v_result);
  return v_result;
end $$;

create or replace function public.accept_property_offer_v1(
  p_offer_id uuid,p_expected_listing_version bigint,p_expected_offer_version bigint,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=(select auth.uid()); v_offer public.property_offers%rowtype; v_listing public.property_listings%rowtype;
  v_tx uuid:=gen_random_uuid(); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select * into v_offer from public.property_offers where offer_id=p_offer_id for update;
  if not found then raise exception 'OFFER_NOT_FOUND'; end if;
  select * into v_listing from public.property_listings where listing_id=v_offer.listing_id for update;
  if v_listing.seller_principal_id<>v_actor then raise exception 'SELLER_REQUIRED' using errcode='42501'; end if;
  if v_listing.version<>p_expected_listing_version or v_offer.version<>p_expected_offer_version then raise exception 'VERSION_CONFLICT' using errcode='40001'; end if;
  if v_listing.state<>'PUBLISHED' or v_offer.state<>'SUBMITTED' then raise exception 'OFFER_NOT_ACCEPTABLE'; end if;
  update public.property_offers set state=case when offer_id=p_offer_id then 'ACCEPTED' else 'REJECTED' end,version=version+1 where listing_id=v_listing.listing_id and state='SUBMITTED';
  update public.property_listings set state='UNDER_DUE_DILIGENCE',version=version+1 where listing_id=v_listing.listing_id;
  insert into public.property_transactions(transaction_id,listing_id,accepted_offer_id) values(v_tx,v_listing.listing_id,p_offer_id);
  v_result:=jsonb_build_object('transaction_id',v_tx,'listing_id',v_listing.listing_id,'state','DUE_DILIGENCE','version',p_expected_listing_version+1);
  perform market_private.record_command(p_idempotency_key,v_actor,'PROPERTY_LISTING',v_listing.listing_id,'ACCEPT_OFFER',p_expected_listing_version,jsonb_build_object('offer_id',p_offer_id),v_result);
  return v_result;
end $$;

create or replace function public.void_fuel_purchase_v1(
  p_purchase_id uuid,p_reason_code text,p_expected_version bigint,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=(select auth.uid()); v_purchase public.fuel_purchases%rowtype; v_redeemed integer; v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  select * into v_purchase from public.fuel_purchases where purchase_id=p_purchase_id for update;
  if not found then raise exception 'PURCHASE_NOT_FOUND'; end if;
  if not market_private.is_org_member(v_purchase.organization_id,v_actor,array['OWNER','ADMIN','STATION_MANAGER','FINANCE','AUDITOR']) then raise exception 'VOID_ROLE_REQUIRED' using errcode='42501'; end if;
  if v_purchase.version<>p_expected_version then raise exception 'VERSION_CONFLICT' using errcode='40001'; end if;
  if v_purchase.status not in ('PENDING','SETTLED') then raise exception 'PURCHASE_NOT_VOIDABLE'; end if;
  select count(*) into v_redeemed from public.fuel_coupons where issued_from_purchase_id=p_purchase_id and state='REDEEMED';
  update public.fuel_purchases set status='VOIDED',version=version+1 where purchase_id=p_purchase_id;
  update public.fuel_coupons set state='REVOKED',version=version+1 where issued_from_purchase_id=p_purchase_id and state in ('ISSUED','CLAIMED','RESERVED');
  v_result:=jsonb_build_object('purchase_id',p_purchase_id,'status','VOIDED','redeemed_rewards_requiring_review',v_redeemed,'version',p_expected_version+1);
  perform market_private.record_command(p_idempotency_key,v_actor,'FUEL_PURCHASE',p_purchase_id,'VOID',p_expected_version,jsonb_build_object('reason_code',p_reason_code),v_result);
  insert into public.market_audit_events(actor_principal_id,organization_id,aggregate_type,aggregate_id,action,decision,reason_code,metadata)
  values(v_actor,v_purchase.organization_id,'FUEL_PURCHASE',p_purchase_id,'VOID','ALLOWED',p_reason_code,jsonb_build_object('redeemed_rewards_requiring_review',v_redeemed));
  return v_result;
end $$;

create or replace function public.search_legal_professionals_v1(p_category_code text,p_limit integer default 25)
returns table(principal_id uuid,organization_id uuid,public_display_name text,bar_identifier_masked text,
  caab_status text,caab_checked_at timestamptz,notary_status text,notary_checked_at timestamptz,demonstrated_matter_count integer)
language sql stable security definer set search_path = '' as $$
  select p.principal_id,p.organization_id,p.public_display_name,p.bar_identifier_masked,
    caab.status,caab.checked_at,dnn.status,dnn.checked_at,p.demonstrated_matter_count
  from public.legal_professional_profiles p
  join public.market_professional_credentials caab on caab.principal_id=p.principal_id and caab.authority='CAAB'
  left join public.market_professional_credentials dnn on dnn.principal_id=p.principal_id and dnn.authority='DNN'
  where p.accepting_matters and p_category_code=any(p.declared_capabilities)
    and caab.status='ACTIVE' and caab.checked_at>=now()-interval '30 days' and (caab.expires_at is null or caab.expires_at>=now())
  order by p.demonstrated_matter_count desc,p.public_display_name limit least(greatest(p_limit,1),50)
$$;

create or replace function public.search_property_listings_v1(p_operation text,p_limit integer default 50)
returns table(listing_id uuid,property_id uuid,operation text,property_type_code text,approximate_zone text,
  asking_amount_minor bigint,currency char(3),ownership_truth text,registry_truth text,listing_version bigint)
language sql stable security definer set search_path = '' as $$
  select l.listing_id,a.property_id,l.operation,a.property_type_code,a.approximate_zone,l.asking_amount_minor,l.currency,
    coalesce(owner.truth_state,'UNKNOWN'),coalesce(registry.truth_state,'UNKNOWN'),l.version
  from public.property_listings l join public.property_assets a using(property_id)
  left join public.property_proofs owner on owner.property_id=a.property_id and owner.claim_key='registered_owner'
  left join public.property_proofs registry on registry.property_id=a.property_id and registry.claim_key='registry_identity'
  where l.state='PUBLISHED' and (p_operation is null or l.operation=p_operation)
  order by l.published_at desc limit least(greatest(p_limit,1),100)
$$;

do $$
declare r record;
begin
  for r in select n.nspname schema_name,p.proname,pg_get_function_identity_arguments(p.oid) args
    from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public' and p.proname in (
      'create_market_organization_v1','create_legal_matter_v1','get_legal_conflict_packet_v1',
      'record_legal_conflict_check_v1','create_property_asset_v1','publish_property_listing_v1',
      'publish_fuel_campaign_version_v1','record_fuel_purchase_v1','accept_legal_offer_v1',
      'submit_property_offer_v1','accept_property_offer_v1','void_fuel_purchase_v1',
      'search_legal_professionals_v1','search_property_listings_v1'
    )
  loop
    execute format('revoke all on function %I.%I(%s) from public, anon',r.schema_name,r.proname,r.args);
    execute format('grant execute on function %I.%I(%s) to authenticated',r.schema_name,r.proname,r.args);
  end loop;
end $$;
