-- A passenger cannot label a message as coming from the driver (or vice versa).
-- This applies equally to active and completed trip conversations.
drop policy if exists ride_messages_participant_insert on public.ride_messages;
create policy ride_messages_participant_insert on public.ride_messages
for insert to authenticated with check (
    sender_id = (select auth.uid())
    and exists (
        select 1 from public.ride_requests r
        where r.id = ride_messages.ride_request_id
          and r.state not in ('DRAFT', 'SEARCHING', 'OFFERED', 'EXPIRED')
          and (
              (r.passenger_id = (select auth.uid()) and ride_messages.sender_role = 'PASSENGER')
              or (r.assigned_driver_id = (select auth.uid()) and ride_messages.sender_role = 'DRIVER')
          )
    )
);

drop policy if exists ride_messages_sender_update on public.ride_messages;
create policy ride_messages_sender_update on public.ride_messages
for update to authenticated
using (sender_id = (select auth.uid()))
with check (
    sender_id = (select auth.uid())
    and exists (
        select 1 from public.ride_requests r
        where r.id = ride_messages.ride_request_id
          and (
              (r.passenger_id = (select auth.uid()) and ride_messages.sender_role = 'PASSENGER')
              or (r.assigned_driver_id = (select auth.uid()) and ride_messages.sender_role = 'DRIVER')
          )
    )
);
