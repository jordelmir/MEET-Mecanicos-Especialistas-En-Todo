-- Elysium Vanguard: participant-only ride multimedia chat and the canonical
-- ontology/lifecycle foundation for physical, digital and hybrid services.

-- Backward-compatible contact metadata for the mature local marketplace
-- adapter. No fabricated fallback number is allowed.
alter table if exists public.service_bids
    add column if not exists "providerPhone" text not null default '';

create table if not exists public.ride_messages (
    id uuid primary key,
    ride_request_id uuid not null references public.ride_requests(id) on delete cascade,
    sender_id uuid not null references auth.users(id) on delete restrict,
    sender_name text not null check (char_length(sender_name) between 1 and 120),
    sender_role text not null check (sender_role in ('PASSENGER', 'DRIVER')),
    message_type text not null check (message_type in ('TEXT', 'PRESET', 'AUDIO', 'IMAGE')),
    text_content text check (text_content is null or char_length(text_content) <= 4000),
    media_path text,
    media_mime_type text,
    audio_duration_ms bigint check (audio_duration_ms is null or audio_duration_ms between 500 and 600000),
    created_at_epoch_ms bigint not null check (created_at_epoch_ms > 0),
    created_at timestamptz not null default now(),
    check (
        (message_type in ('TEXT', 'PRESET') and text_content is not null and media_path is null) or
        (message_type in ('AUDIO', 'IMAGE') and media_path is not null)
    )
);

create index if not exists ride_messages_timeline_idx
    on public.ride_messages(ride_request_id, created_at_epoch_ms, id);

alter table public.ride_messages enable row level security;

drop policy if exists ride_messages_participant_select on public.ride_messages;
create policy ride_messages_participant_select on public.ride_messages
for select to authenticated using (
    exists (
        select 1 from public.ride_requests r
        where r.id = ride_messages.ride_request_id
          and auth.uid() in (r.passenger_id, r.assigned_driver_id)
    )
);

drop policy if exists ride_messages_participant_insert on public.ride_messages;
create policy ride_messages_participant_insert on public.ride_messages
for insert to authenticated with check (
    sender_id = auth.uid()
    and exists (
        select 1 from public.ride_requests r
        where r.id = ride_messages.ride_request_id
          and auth.uid() in (r.passenger_id, r.assigned_driver_id)
          and r.state not in ('DRAFT', 'SEARCHING', 'OFFERED', 'EXPIRED')
    )
);

drop policy if exists ride_messages_sender_update on public.ride_messages;
create policy ride_messages_sender_update on public.ride_messages
for update to authenticated using (sender_id = auth.uid())
with check (sender_id = auth.uid());

insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
values (
    'ride-media',
    'ride-media',
    false,
    15728640,
    array['image/jpeg', 'image/png', 'image/webp', 'audio/mp4', 'audio/m4a']
)
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists ride_media_participant_select on storage.objects;
create policy ride_media_participant_select on storage.objects
for select to authenticated using (
    bucket_id = 'ride-media'
    and exists (
        select 1 from public.ride_requests r
        where r.id = ((storage.foldername(name))[1])::uuid
          and auth.uid() in (r.passenger_id, r.assigned_driver_id)
    )
);

drop policy if exists ride_media_sender_insert on storage.objects;
create policy ride_media_sender_insert on storage.objects
for insert to authenticated with check (
    bucket_id = 'ride-media'
    and (storage.foldername(name))[2] = auth.uid()::text
    and exists (
        select 1 from public.ride_requests r
        where r.id = ((storage.foldername(name))[1])::uuid
          and auth.uid() in (r.passenger_id, r.assigned_driver_id)
    )
);

create table if not exists public.service_definitions (
    id text primary key,
    domain text not null,
    display_name text not null,
    supported_modalities text[] not null,
    risk_tier text not null default 'STANDARD'
        check (risk_tier in ('STANDARD', 'ELEVATED', 'RESTRICTED')),
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.universal_service_requests (
    id uuid primary key default gen_random_uuid(),
    client_id uuid not null references auth.users(id) on delete restrict,
    service_definition_id text not null references public.service_definitions(id),
    modality text not null check (modality in ('PHYSICAL', 'DIGITAL', 'HYBRID')),
    title text not null check (char_length(title) between 3 and 160),
    description text not null check (char_length(description) between 10 and 5000),
    intake jsonb not null default '{}'::jsonb,
    location geography(point, 4326),
    location_label text,
    offered_price_minor bigint not null check (offered_price_minor > 0),
    final_price_minor bigint check (final_price_minor is null or final_price_minor > 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    state text not null default 'OPEN'
        check (state in ('DRAFT', 'OPEN', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'DISPUTED')),
    assigned_provider_id uuid references auth.users(id) on delete restrict,
    accepted_offer_id uuid,
    payment_state text not null default 'NOT_STARTED'
        check (payment_state in ('NOT_STARTED', 'PENDING', 'AUTHORIZED', 'CAPTURED', 'REFUNDED', 'FAILED')),
    version bigint not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.universal_service_offers (
    id uuid primary key default gen_random_uuid(),
    request_id uuid not null references public.universal_service_requests(id) on delete cascade,
    provider_id uuid not null references auth.users(id) on delete restrict,
    price_minor bigint not null check (price_minor > 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    eta_minutes integer check (eta_minutes is null or eta_minutes between 0 and 43200),
    warranty_days integer not null default 0 check (warranty_days between 0 and 3650),
    scope jsonb not null default '{}'::jsonb,
    state text not null default 'PENDING'
        check (state in ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(request_id, provider_id)
);

alter table public.universal_service_requests
    add constraint universal_service_requests_accepted_offer_fk
    foreign key (accepted_offer_id) references public.universal_service_offers(id)
    deferrable initially deferred;

create unique index if not exists universal_service_one_accepted_offer
    on public.universal_service_offers(request_id) where state = 'ACCEPTED';

alter table public.service_definitions enable row level security;
alter table public.universal_service_requests enable row level security;
alter table public.universal_service_offers enable row level security;

create policy service_definitions_read on public.service_definitions
for select using (active);
create policy universal_requests_client_all on public.universal_service_requests
for all to authenticated using (client_id = auth.uid()) with check (client_id = auth.uid());
create policy universal_requests_provider_read on public.universal_service_requests
for select to authenticated using (state = 'OPEN' or assigned_provider_id = auth.uid());
create policy universal_offers_participant_read on public.universal_service_offers
for select to authenticated using (
    provider_id = auth.uid() or exists (
        select 1 from public.universal_service_requests r
        where r.id = request_id and r.client_id = auth.uid()
    )
);
create policy universal_offers_provider_write on public.universal_service_offers
for insert to authenticated with check (provider_id = auth.uid());

insert into public.service_definitions(id, domain, display_name, supported_modalities, risk_tier)
values
    ('home_cleaning', 'Hogar', 'Limpieza residencial', array['PHYSICAL'], 'STANDARD'),
    ('plumbing', 'Hogar', 'Plomería', array['PHYSICAL'], 'STANDARD'),
    ('electrical_home', 'Hogar', 'Electricidad residencial', array['PHYSICAL'], 'ELEVATED'),
    ('moving', 'Logística', 'Mudanzas y carga', array['PHYSICAL'], 'STANDARD'),
    ('courier', 'Logística', 'Mensajería y entregas', array['PHYSICAL'], 'STANDARD'),
    ('personal_transport', 'Movilidad', 'Transporte de personas', array['PHYSICAL'], 'ELEVATED'),
    ('roadside', 'Movilidad', 'Asistencia vial', array['PHYSICAL'], 'ELEVATED'),
    ('mechanical', 'Automotriz', 'Mecánica y diagnóstico', array['PHYSICAL'], 'ELEVATED'),
    ('tutoring', 'Educación', 'Tutorías y clases', array['PHYSICAL','DIGITAL','HYBRID'], 'STANDARD'),
    ('translation', 'Profesional', 'Traducción', array['DIGITAL'], 'STANDARD'),
    ('accounting', 'Profesional', 'Contabilidad', array['DIGITAL'], 'ELEVATED'),
    ('legal', 'Profesional', 'Orientación legal', array['PHYSICAL','DIGITAL','HYBRID'], 'RESTRICTED'),
    ('graphic_design', 'Digital', 'Diseño gráfico', array['DIGITAL'], 'STANDARD'),
    ('software', 'Digital', 'Software y automatización', array['DIGITAL'], 'STANDARD'),
    ('it_support', 'Digital', 'Soporte técnico', array['PHYSICAL','DIGITAL','HYBRID'], 'STANDARD'),
    ('custom', 'Otros', 'Otro servicio', array['PHYSICAL','DIGITAL','HYBRID'], 'STANDARD')
on conflict (id) do update set
    domain = excluded.domain,
    display_name = excluded.display_name,
    supported_modalities = excluded.supported_modalities,
    risk_tier = excluded.risk_tier,
    active = true,
    updated_at = now();
