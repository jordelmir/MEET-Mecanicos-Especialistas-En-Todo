-- Preserve the exact OR semantics of legitimate multi-role access paths while
-- ensuring each role/action evaluates one permissive policy per table.

begin;

drop policy if exists company_profiles_owner_rw on public.company_profiles;
drop policy if exists company_profiles_public_read on public.company_profiles;
create policy company_profiles_public_read_anon on public.company_profiles
for select to anon using (status = 'verified');
create policy company_profiles_authenticated_read on public.company_profiles
for select to authenticated using (
    status = 'verified' or owner_user_id = (select auth.uid())
);
create policy company_profiles_owner_insert on public.company_profiles
for insert to authenticated with check (owner_user_id = (select auth.uid()));
create policy company_profiles_owner_update on public.company_profiles
for update to authenticated using (owner_user_id = (select auth.uid()))
with check (owner_user_id = (select auth.uid()));
create policy company_profiles_owner_delete on public.company_profiles
for delete to authenticated using (owner_user_id = (select auth.uid()));

drop policy if exists part_offers_update_request_customer on public.part_offers;
drop policy if exists part_offers_update_store_owner_limited on public.part_offers;
create policy part_offers_update_participants on public.part_offers
for update to authenticated
using (
    store_owner_id = (select auth.uid()) or exists (
        select 1 from public.part_requests pr
        where pr."requestId" = "partRequestId"
          and pr.customer_id = (select auth.uid())
    )
)
with check (
    exists (
        select 1 from public.part_requests pr
        where pr."requestId" = "partRequestId"
          and pr.customer_id = (select auth.uid())
    ) or (
        store_owner_id = (select auth.uid())
        and status in ('PENDING', 'CANCELLED')
    )
);

drop policy if exists repair_offers_customer_read on public.repair_offers;
drop policy if exists repair_offers_mechanic_rw on public.repair_offers;
create policy repair_offers_participant_read on public.repair_offers
for select to authenticated using (
    exists (
        select 1 from public.repair_requests rr
        join public.user_profiles up on up.id = rr.customer_profile_id
        where rr.id = repair_request_id
          and up.auth_user_id = (select auth.uid())
    ) or exists (
        select 1 from public.provider_profiles pp
        join public.user_profiles up on up.id = pp.user_profile_id
        where pp.id = mechanic_profile_id
          and up.auth_user_id = (select auth.uid())
    )
);
create policy repair_offers_mechanic_insert on public.repair_offers
for insert to authenticated with check (exists (
    select 1 from public.provider_profiles pp
    join public.user_profiles up on up.id = pp.user_profile_id
    where pp.id = mechanic_profile_id and up.auth_user_id = (select auth.uid())
));
create policy repair_offers_mechanic_update on public.repair_offers
for update to authenticated using (exists (
    select 1 from public.provider_profiles pp
    join public.user_profiles up on up.id = pp.user_profile_id
    where pp.id = mechanic_profile_id and up.auth_user_id = (select auth.uid())
)) with check (exists (
    select 1 from public.provider_profiles pp
    join public.user_profiles up on up.id = pp.user_profile_id
    where pp.id = mechanic_profile_id and up.auth_user_id = (select auth.uid())
));
create policy repair_offers_mechanic_delete on public.repair_offers
for delete to authenticated using (exists (
    select 1 from public.provider_profiles pp
    join public.user_profiles up on up.id = pp.user_profile_id
    where pp.id = mechanic_profile_id and up.auth_user_id = (select auth.uid())
));

drop policy if exists repair_requests_customer_rw on public.repair_requests;
drop policy if exists repair_requests_provider_read on public.repair_requests;
create policy repair_requests_authorized_read on public.repair_requests
for select to authenticated using (
    status in ('published', 'waiting_offers', 'offer_received') or exists (
        select 1 from public.user_profiles up
        where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
    )
);
create policy repair_requests_customer_insert on public.repair_requests
for insert to authenticated with check (exists (
    select 1 from public.user_profiles up
    where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
));
create policy repair_requests_customer_update on public.repair_requests
for update to authenticated using (exists (
    select 1 from public.user_profiles up
    where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
)) with check (exists (
    select 1 from public.user_profiles up
    where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
));
create policy repair_requests_customer_delete on public.repair_requests
for delete to authenticated using (exists (
    select 1 from public.user_profiles up
    where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
));

drop policy if exists ride_consents_driver_write on public.ride_consents;
drop policy if exists ride_consents_participant_select on public.ride_consents;
create policy ride_consents_authorized_read on public.ride_consents
for select to authenticated using (
    public.ride_is_participant(trip_id) or (
        driver_id = (select auth.uid())
        and public.ride_is_assigned_driver(trip_id)
        and public.ride_trip_is_active(trip_id)
    )
);
create policy ride_consents_driver_insert on public.ride_consents
for insert to authenticated with check (
    driver_id = (select auth.uid()) and public.ride_is_assigned_driver(trip_id)
    and public.ride_trip_is_active(trip_id)
);
create policy ride_consents_driver_update on public.ride_consents
for update to authenticated using (
    driver_id = (select auth.uid()) and public.ride_is_assigned_driver(trip_id)
    and public.ride_trip_is_active(trip_id)
) with check (
    driver_id = (select auth.uid()) and public.ride_is_assigned_driver(trip_id)
    and public.ride_trip_is_active(trip_id)
);
create policy ride_consents_driver_delete on public.ride_consents
for delete to authenticated using (
    driver_id = (select auth.uid()) and public.ride_is_assigned_driver(trip_id)
    and public.ride_trip_is_active(trip_id)
);

drop policy if exists ride_driver_vehicles_owner_select on public.ride_driver_vehicles;
drop policy if exists ride_driver_vehicles_participant_select on public.ride_driver_vehicles;
create policy ride_driver_vehicles_authorized_read on public.ride_driver_vehicles
for select to authenticated using (
    driver_id = (select auth.uid()) or public.ride_can_view_vehicle(id)
);

drop policy if exists ride_positions_participant_select on public.ride_positions;
drop policy if exists ride_positions_subject_write on public.ride_positions;
create policy ride_positions_authorized_read on public.ride_positions
for select to authenticated using (
    (public.ride_is_participant(trip_id) and public.ride_trip_is_active(trip_id) and expires_at > now())
    or (subject_user_id = (select auth.uid()) and public.ride_is_participant(trip_id) and public.ride_trip_is_active(trip_id))
);
create policy ride_positions_subject_insert on public.ride_positions
for insert to authenticated with check (
    subject_user_id = (select auth.uid()) and public.ride_is_participant(trip_id)
    and public.ride_trip_is_active(trip_id) and expires_at <= now() + interval '5 minutes'
);
create policy ride_positions_subject_update on public.ride_positions
for update to authenticated using (
    subject_user_id = (select auth.uid()) and public.ride_is_participant(trip_id)
    and public.ride_trip_is_active(trip_id)
) with check (
    subject_user_id = (select auth.uid()) and public.ride_is_participant(trip_id)
    and public.ride_trip_is_active(trip_id) and expires_at <= now() + interval '5 minutes'
);
create policy ride_positions_subject_delete on public.ride_positions
for delete to authenticated using (
    subject_user_id = (select auth.uid()) and public.ride_is_participant(trip_id)
    and public.ride_trip_is_active(trip_id)
);

drop policy if exists supplier_inventory_owner_write on public.supplier_inventory_items;
drop policy if exists supplier_inventory_read_marketplace on public.supplier_inventory_items;
create policy supplier_inventory_marketplace_read on public.supplier_inventory_items
for select to anon, authenticated using (true);
create policy supplier_inventory_owner_insert on public.supplier_inventory_items
for insert to authenticated with check ("storeOwnerId" = (select auth.uid()));
create policy supplier_inventory_owner_update on public.supplier_inventory_items
for update to authenticated using ("storeOwnerId" = (select auth.uid()))
with check ("storeOwnerId" = (select auth.uid()));
create policy supplier_inventory_owner_delete on public.supplier_inventory_items
for delete to authenticated using ("storeOwnerId" = (select auth.uid()));

drop policy if exists tow_requests_customer_rw on public.tow_requests;
drop policy if exists tow_requests_provider_read on public.tow_requests;
create policy tow_requests_authorized_read on public.tow_requests
for select to authenticated using (
    status in ('published', 'driver_assigned') or exists (
        select 1 from public.user_profiles up
        where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
    )
);
create policy tow_requests_customer_insert on public.tow_requests
for insert to authenticated with check (exists (
    select 1 from public.user_profiles up
    where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
));
create policy tow_requests_customer_update on public.tow_requests
for update to authenticated using (exists (
    select 1 from public.user_profiles up
    where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
)) with check (exists (
    select 1 from public.user_profiles up
    where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
));
create policy tow_requests_customer_delete on public.tow_requests
for delete to authenticated using (exists (
    select 1 from public.user_profiles up
    where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
));

drop policy if exists universal_requests_client_all on public.universal_service_requests;
drop policy if exists universal_requests_provider_read on public.universal_service_requests;
create policy universal_requests_authorized_read on public.universal_service_requests
for select to authenticated using (
    client_id = (select auth.uid()) or state = 'OPEN'
    or assigned_provider_id = (select auth.uid())
);
create policy universal_requests_client_insert on public.universal_service_requests
for insert to authenticated with check (client_id = (select auth.uid()));
create policy universal_requests_client_update on public.universal_service_requests
for update to authenticated using (client_id = (select auth.uid()))
with check (client_id = (select auth.uid()));
create policy universal_requests_client_delete on public.universal_service_requests
for delete to authenticated using (client_id = (select auth.uid()));

drop policy if exists ride_requests_customer_rw on public.vanguard_legacy_ride_requests;
drop policy if exists ride_requests_driver_read on public.vanguard_legacy_ride_requests;
create policy legacy_ride_requests_authorized_read on public.vanguard_legacy_ride_requests
for select to authenticated using (
    status in ('requested', 'searching_driver') or exists (
        select 1 from public.user_profiles up
        where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
    )
);
create policy legacy_ride_requests_customer_insert on public.vanguard_legacy_ride_requests
for insert to authenticated with check (exists (
    select 1 from public.user_profiles up
    where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
));
create policy legacy_ride_requests_customer_update on public.vanguard_legacy_ride_requests
for update to authenticated using (exists (
    select 1 from public.user_profiles up
    where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
)) with check (exists (
    select 1 from public.user_profiles up
    where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
));
create policy legacy_ride_requests_customer_delete on public.vanguard_legacy_ride_requests
for delete to authenticated using (exists (
    select 1 from public.user_profiles up
    where up.id = customer_profile_id and up.auth_user_id = (select auth.uid())
));

commit;
