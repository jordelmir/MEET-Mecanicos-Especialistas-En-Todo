-- MEET v4.13 pre-production security boundary.
-- Removes permissive development policies left by the 2026-04 and 2026-06
-- bootstrap migrations. Public automotive reference reads remain public;
-- private user data is owner-scoped and payment rows are server-write-only.

begin;

drop policy if exists "Users manage own scans and Mechanics view all" on public.scan_sessions;
drop policy if exists "Users manage own vehicles and Mechanics view all" on public.cloud_vehicles;
drop policy if exists "Users manage own subs" on public.subscriptions;

create policy scan_sessions_owner_select
on public.scan_sessions for select to authenticated
using (user_id = (select auth.uid())::text);
create policy scan_sessions_owner_insert
on public.scan_sessions for insert to authenticated
with check (user_id = (select auth.uid())::text);
create policy scan_sessions_owner_update
on public.scan_sessions for update to authenticated
using (user_id = (select auth.uid())::text)
with check (user_id = (select auth.uid())::text);
create policy scan_sessions_owner_delete
on public.scan_sessions for delete to authenticated
using (user_id = (select auth.uid())::text);

create policy cloud_vehicles_owner_select
on public.cloud_vehicles for select to authenticated
using (user_id = (select auth.uid())::text);
create policy cloud_vehicles_owner_insert
on public.cloud_vehicles for insert to authenticated
with check (user_id = (select auth.uid())::text);
create policy cloud_vehicles_owner_update
on public.cloud_vehicles for update to authenticated
using (user_id = (select auth.uid())::text)
with check (user_id = (select auth.uid())::text);
create policy cloud_vehicles_owner_delete
on public.cloud_vehicles for delete to authenticated
using (user_id = (select auth.uid())::text);

-- Subscription entitlement and receipts are billing authority data. Clients
-- may read their row but cannot manufacture or mutate entitlements directly.
create policy subscriptions_owner_select
on public.subscriptions for select to authenticated
using (user_id = (select auth.uid())::text);

drop policy if exists "Public Write Access Listings" on public.gauge_listings;
drop policy if exists "Public Update Access Listings" on public.gauge_listings;
drop policy if exists "Public Read Access Purchases" on public.gauge_purchases;
drop policy if exists "Public Write Access Purchases" on public.gauge_purchases;
drop policy if exists "Public Write Access Reviews" on public.gauge_reviews;
drop policy if exists "Public Update Access Reviews" on public.gauge_reviews;
drop policy if exists "Public Delete Access Reviews" on public.gauge_reviews;

create policy gauge_listings_creator_insert
on public.gauge_listings for insert to authenticated
with check (creator_id = (select auth.uid())::text);
create policy gauge_listings_creator_update
on public.gauge_listings for update to authenticated
using (creator_id = (select auth.uid())::text)
with check (creator_id = (select auth.uid())::text);
create policy gauge_listings_creator_delete
on public.gauge_listings for delete to authenticated
using (creator_id = (select auth.uid())::text);

-- Purchase creation and all financial fields are restricted to service_role.
-- The role bypasses RLS; no direct client INSERT/UPDATE/DELETE policy exists.
create policy gauge_purchases_buyer_select
on public.gauge_purchases for select to authenticated
using (buyer_id = (select auth.uid())::text);

create policy gauge_reviews_owner_insert
on public.gauge_reviews for insert to authenticated
with check (reviewer_id = (select auth.uid())::text);
create policy gauge_reviews_owner_update
on public.gauge_reviews for update to authenticated
using (reviewer_id = (select auth.uid())::text)
with check (reviewer_id = (select auth.uid())::text);
create policy gauge_reviews_owner_delete
on public.gauge_reviews for delete to authenticated
using (reviewer_id = (select auth.uid())::text);

revoke all on public.scan_sessions, public.cloud_vehicles, public.subscriptions,
  public.gauge_purchases from anon;
revoke insert, update, delete on public.gauge_listings, public.gauge_reviews from anon;

commit;
