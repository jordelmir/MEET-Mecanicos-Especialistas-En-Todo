\set ON_ERROR_STOP on

insert into auth.users(id) values
  ('10000000-0000-0000-0000-000000000001'),
  ('10000000-0000-0000-0000-000000000002'),
  ('10000000-0000-0000-0000-000000000003');

set request.jwt.claim.sub = '10000000-0000-0000-0000-000000000001';

do $test$
declare
  v_profile jsonb;
  v_matter jsonb;
begin
  select public.submit_legal_professional_profile_v1(
    null, 'Lic. Prueba', 'encrypted-caab-123', 'CAAB-***123',
    array['civil'], true,
    '10000000-0000-0000-0000-000000000101'
  ) into v_profile;

  if v_profile->>'credential_status' <> 'PENDING' then
    raise exception 'self-submitted credential must remain PENDING: %', v_profile;
  end if;
  if exists (
    select 1 from public.search_legal_professionals_v1('civil', 25)
    where principal_id = '10000000-0000-0000-0000-000000000001'
  ) then
    raise exception 'unverified professional leaked into verified search';
  end if;

  select public.create_legal_matter_v1(
    'civil', null, 'Incumplimiento contractual documentado',
    'ciphertext', 'CR', 'NORMAL', '[]'::jsonb,
    '10000000-0000-0000-0000-000000000102'
  ) into v_matter;
  if v_matter->>'state' <> 'CONFLICT_SCREENING' then
    raise exception 'unexpected legal matter state: %', v_matter;
  end if;
end $test$;

do $test$
declare
  v_asset jsonb;
  v_listing jsonb;
  v_property_id uuid;
  v_listing_id uuid;
  v_public record;
begin
  select public.create_property_asset_v1(
    'independent_house', 'San Jose oeste', 'encrypted-registry', '***-123',
    null, null, 'encrypted-exact-address',
    '10000000-0000-0000-0000-000000000103'
  ) into v_asset;
  v_property_id := (v_asset->>'property_id')::uuid;

  select public.create_property_listing_v1(
    v_property_id, null, 'SALE', 125000000, 'CRC',
    'Casa verificada documentalmente; registro pendiente de autoridad.',
    '10000000-0000-0000-0000-000000000104'
  ) into v_listing;
  v_listing_id := (v_listing->>'listing_id')::uuid;
  perform public.publish_property_listing_v1(
    v_listing_id, 0, '10000000-0000-0000-0000-000000000105'
  );

  select * into v_public from public.search_property_listings_v1('SALE', 10)
  where listing_id = v_listing_id;
  if not found then raise exception 'published property missing from public projection'; end if;
  if row_to_json(v_public)::text ilike '%encrypted-exact-address%'
     or row_to_json(v_public)::text ilike '%encrypted-registry%' then
    raise exception 'private property identity leaked into public projection';
  end if;
end $test$;

do $test$
declare
  v_org uuid := '10000000-0000-0000-0000-000000000201';
  v_station jsonb;
  v_station_id uuid;
  v_campaign jsonb;
  v_campaign_id uuid;
  v_campaign_version jsonb;
begin
  insert into public.market_organizations(
    organization_id, legal_name, commercial_name, kind, jurisdiction, created_by
  ) values (v_org, 'Combustibles Prueba SA', 'Estacion Prueba', 'FUEL_NETWORK', 'CR', auth.uid());
  insert into public.market_organization_members(organization_id, principal_id, roles)
  values(v_org, auth.uid(), array['OWNER','ADMIN','MARKETING_MANAGER','STATION_MANAGER']);

  select public.create_fuel_station_v1(
    v_org, 'Estacion Central', 'ARESEP-PENDING',
    '10000000-0000-0000-0000-000000000106'
  ) into v_station;
  v_station_id := (v_station->>'station_id')::uuid;

  select public.create_fuel_campaign_draft_v1(
    v_org, 'Bienvenida verificable',
    '10000000-0000-0000-0000-000000000107'
  ) into v_campaign;
  v_campaign_id := (v_campaign->>'campaign_id')::uuid;
  select public.create_fuel_campaign_version_v1(
    v_campaign_id, now() - interval '1 day', now() + interval '365 days',
    100, 'PER_PURCHASE', 1, array[v_station_id], 'POINTS',
    '{"label":"100 puntos"}'::jsonb,
    'Compra de combustible confirmada', 'Una recompensa por compra',
    'Presente el QR dinamico en la estacion', 1, repeat('a',64), null, 0,
    '10000000-0000-0000-0000-000000000108'
  ) into v_campaign_version;

  if (v_campaign_version->>'campaign_version_id') is null then
    raise exception 'campaign version was not created';
  end if;
  if not exists(select 1 from public.fuel_stations where station_id=v_station_id and active) then
    raise exception 'fuel station was not created';
  end if;
end $test$;

-- A different JWT cannot read the first client's legal matter.
set role authenticated;
set request.jwt.claim.sub = '10000000-0000-0000-0000-000000000002';
do $test$
begin
  if (select count(*) from public.legal_matters) <> 0 then
    raise exception 'legal matter leaked across principals';
  end if;
end $test$;
reset role;

-- Exact address remains opaque until the owner grants a bounded purpose.
set request.jwt.claim.sub = '10000000-0000-0000-0000-000000000002';
do $test$
declare v_property uuid; v_denied jsonb;
begin
  select property_id into v_property from public.property_assets limit 1;
  select public.get_property_exact_address_v1(v_property,'SCHEDULED_SHOWING') into v_denied;
  if (v_denied->>'allowed')::boolean then raise exception 'exact address exposed without grant'; end if;
end $test$;

set request.jwt.claim.sub = '10000000-0000-0000-0000-000000000001';
do $test$
declare v_property uuid;
begin
  select property_id into v_property from public.property_assets limit 1;
  perform public.grant_property_exact_address_v1(
    v_property,'10000000-0000-0000-0000-000000000002','SCHEDULED_SHOWING',
    now()+interval '1 day',0,'10000000-0000-0000-0000-000000000109'
  );
  if exists(select 1 from public.property_proofs where property_id=v_property and truth_state in('REGISTRY_VERIFIED','CADASTRAL_VERIFIED')) then
    raise exception 'seller-declared registry evidence was promoted without authority';
  end if;
end $test$;

set request.jwt.claim.sub = '10000000-0000-0000-0000-000000000002';
do $test$
declare v_property uuid; v_allowed jsonb;
begin
  select property_id into v_property from public.property_assets limit 1;
  select public.get_property_exact_address_v1(v_property,'SCHEDULED_SHOWING') into v_allowed;
  if not (v_allowed->>'allowed')::boolean or v_allowed->>'exact_address_ciphertext'<>'encrypted-exact-address' then
    raise exception 'valid exact-address grant was not honored: %',v_allowed;
  end if;
end $test$;

-- Only the service-role authority adapter can activate observed credentials.
reset request.jwt.claim.sub;
do $test$
declare v_credential uuid; v_result jsonb;
begin
  if has_function_privilege('authenticated','public.record_authority_verification_v1(uuid,text,text,text,timestamptz,timestamptz,text)','EXECUTE')
     or not has_function_privilege('service_role','public.record_authority_verification_v1(uuid,text,text,text,timestamptz,timestamptz,text)','EXECUTE') then
    raise exception 'authority adapter privilege boundary is incorrect';
  end if;
  select credential_id into v_credential from public.market_professional_credentials where authority='CAAB' limit 1;
  select public.record_authority_verification_v1(v_credential,'CAAB:test-record',repeat('b',64),'ACTIVE',now(),now()+interval '30 days','caab-test-run-1') into v_result;
  if v_result->>'status'<>'ACTIVE' then raise exception 'authority observation not applied: %',v_result; end if;
end $test$;

set request.jwt.claim.sub = '10000000-0000-0000-0000-000000000001';
do $test$
begin
  if not exists(select 1 from public.match_legal_professionals_v1('civil','CR','es',500000,25)) then
    raise exception 'authority-verified professional missing from explainable match';
  end if;
end $test$;

-- Fuel proof: wrong-station redemption is rejected, a valid redemption is
-- single-use, and refund records explicit manual review for consumed value.
do $test$
declare
  v_org uuid; v_station uuid; v_wrong_station uuid; v_campaign uuid; v_cv uuid;
  v_purchase jsonb; v_purchase_id uuid; v_rewards jsonb; v_token text; v_refund jsonb;
begin
  select organization_id into v_org from public.market_organizations where kind='FUEL_NETWORK' limit 1;
  select station_id into v_station from public.fuel_stations where organization_id=v_org order by name limit 1;
  select campaign_id into v_campaign from public.fuel_campaigns where organization_id=v_org limit 1;
  select campaign_version_id into v_cv from public.fuel_campaign_versions where campaign_id=v_campaign limit 1;
  perform public.publish_fuel_campaign_version_v1(v_cv,1,'10000000-0000-0000-0000-000000000110');
  v_wrong_station := (public.create_fuel_station_v1(v_org,'Estacion Alterna','ARESEP-PENDING-2','10000000-0000-0000-0000-000000000111')->>'station_id')::uuid;
  select public.record_fuel_purchase_v1(v_station,'TERM-1','SHIFT-1','10000000-0000-0000-0000-000000000002',now(),500,'CARD','POS_AUTHORITATIVE',repeat('c',64),'10000000-0000-0000-0000-000000000112') into v_purchase;
  v_purchase_id := (v_purchase->>'purchase_id')::uuid;
  select public.issue_fuel_rewards_v1(v_purchase_id,v_cv,0,'10000000-0000-0000-0000-000000000113') into v_rewards;
  v_token := v_rewards#>>'{tokens,0,token}';
  begin
    perform public.redeem_fuel_coupon_v1(v_token,v_wrong_station,v_purchase_id,'10000000-0000-0000-0000-000000000114');
    raise exception 'wrong-station coupon was accepted';
  exception when others then
    if sqlerrm not like '%WRONG_STATION%' then raise; end if;
  end;
  perform public.redeem_fuel_coupon_v1(v_token,v_station,v_purchase_id,'10000000-0000-0000-0000-000000000115');
  begin
    perform public.redeem_fuel_coupon_v1(v_token,v_station,v_purchase_id,'10000000-0000-0000-0000-000000000116');
    raise exception 'coupon replay was accepted';
  exception when others then
    if sqlerrm not like '%COUPON_NOT_REDEEMABLE%' then raise; end if;
  end;
  select public.refund_fuel_purchase_v1(v_purchase_id,'CUSTOMER_REFUND',0,'10000000-0000-0000-0000-000000000117') into v_refund;
  if (v_refund->>'manual_review_units')::integer<>1 then
    raise exception 'redeemed reward was not routed to manual review: %',v_refund;
  end if;
end $test$;

reset request.jwt.claim.sub;

do $test$
begin
  if (select count(*) from public.market_service_categories) < 80 then
    raise exception 'Costa Rica catalog unexpectedly incomplete';
  end if;
  if (select count(*) from public.market_commands) < 7 then
    raise exception 'authoritative command journal unexpectedly incomplete';
  end if;
end $test$;
