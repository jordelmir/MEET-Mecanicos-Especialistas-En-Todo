-- =============================================================================
-- Certified Reports Foundation
-- =============================================================================
-- Adds the database schema for the certified-report pipeline:
--   - public.certified_reports     : the report header (per Jor's spec)
--   - public.report_evidence       : photos, OBD snapshots, signatures, etc.
--   - public.diagnostic_snapshots  : mirror of the Kotlin DiagnosticSnapshot
--   - public.repair_actions        : parts used + work done
--   - public.report_signatures     : signer + SHA-256 chain
--
-- Backwards compatible: the only new objects are CREATE TABLE / CREATE TYPE /
-- CREATE INDEX / ALTER TABLE ENABLE RLS. We do not modify any existing table.
--
-- Field naming follows the project's Android-friendly convention: camelCase
-- quoted names. The TypeScript types in lib/reports/types.ts mirror this.
-- =============================================================================

set check_function_bodies = off;
set search_path = public, auth;

-- -----------------------------------------------------------------------------
-- 1. Enums
-- -----------------------------------------------------------------------------
do $$ begin
  if not exists (select 1 from pg_type where typname = 'report_type') then
    create type public.report_type as enum (
      'PRE_SCAN_REPORT',
      'POST_SCAN_REPORT',
      'REPAIR_EVIDENCE_REPORT',
      'PRE_PURCHASE_INSPECTION_REPORT',
      'DVIR_REPORT'
    );
  end if;
end $$;

do $$ begin
  if not exists (select 1 from pg_type where typname = 'report_status') then
    create type public.report_status as enum (
      'DRAFT',
      'READY',
      'SIGNED',
      'EXPORTED',
      'SHARED',
      'VOIDED'
    );
  end if;
end $$;

do $$ begin
  if not exists (select 1 from pg_type where typname = 'evidence_type') then
    create type public.evidence_type as enum (
      'PHOTO',
      'VIDEO',
      'OBD_SNAPSHOT',
      'FREEZE_FRAME',
      'SENSOR_GRAPH',
      'SIGNATURE',
      'MEASUREMENT',
      'PART_INVOICE',
      'REPAIR_NOTE'
    );
  end if;
end $$;

do $$ begin
  if not exists (select 1 from pg_type where typname = 'diagnostic_provenance') then
    create type public.diagnostic_provenance as enum (
      'LIVE_OBD',
      'CACHED_OBD',
      'MANUAL',
      'OFFLINE_FIXTURE'
    );
  end if;
end $$;

-- -----------------------------------------------------------------------------
-- 2. public.certified_reports
-- -----------------------------------------------------------------------------
create table if not exists public.certified_reports (
  "reportId" text primary key,
  "vehicleId" text not null,
  "userId" uuid not null references auth.users(id) on delete cascade,
  "reportType" public.report_type not null,
  "title" text not null,
  "status" public.report_status not null default 'DRAFT',
  "odometerKm" integer,
  "vin" text,
  "plate" text,
  "generatedAt" bigint not null default (extract(epoch from now()) * 1000)::bigint,
  "signedAt" bigint,
  "pdfUri" text,
  "qrVerificationUrl" text,
  "integrityHash" text not null,
  "previousHash" text,
  "createdAt" bigint not null default (extract(epoch from now()) * 1000)::bigint,
  "updatedAt" bigint not null default (extract(epoch from now()) * 1000)::bigint,
  constraint certified_reports_status_check
    check ("status" in ('DRAFT', 'READY', 'SIGNED', 'EXPORTED', 'SHARED', 'VOIDED'))
);

create index if not exists idx_certified_reports_vehicle
  on public.certified_reports ("vehicleId", "generatedAt" desc);
create index if not exists idx_certified_reports_user
  on public.certified_reports ("userId", "generatedAt" desc);
create index if not exists idx_certified_reports_chain
  on public.certified_reports ("vehicleId", "integrityHash");
create index if not exists idx_certified_reports_status
  on public.certified_reports ("status");
create index if not exists idx_certified_reports_type
  on public.certified_reports ("reportType");

alter table public.certified_reports enable row level security;

drop policy if exists certified_reports_owner_read on public.certified_reports;
create policy certified_reports_owner_read
  on public.certified_reports
  for select
  to authenticated
  using ("userId" = auth.uid());

drop policy if exists certified_reports_owner_write on public.certified_reports;
create policy certified_reports_owner_write
  on public.certified_reports
  for all
  to authenticated
  using ("userId" = auth.uid())
  with check ("userId" = auth.uid());

-- -----------------------------------------------------------------------------
-- 3. public.report_evidence
-- -----------------------------------------------------------------------------
create table if not exists public.report_evidence (
  "evidenceId" text primary key,
  "reportId" text not null references public.certified_reports("reportId") on delete cascade,
  "type" public.evidence_type not null,
  "label" text not null,
  "description" text not null default '',
  "uri" text not null default '',
  "hash" text,
  "capturedAt" bigint not null default (extract(epoch from now()) * 1000)::bigint,
  "lat" double precision,
  "lng" double precision
);

create index if not exists idx_report_evidence_report
  on public.report_evidence ("reportId", "capturedAt" desc);
create index if not exists idx_report_evidence_type
  on public.report_evidence ("type");

alter table public.report_evidence enable row level security;

drop policy if exists report_evidence_owner_read on public.report_evidence;
create policy report_evidence_owner_read
  on public.report_evidence
  for select
  to authenticated
  using (
    exists (
      select 1
      from public.certified_reports cr
      where cr."reportId" = report_evidence."reportId"
        and cr."userId" = auth.uid()
    )
  );

drop policy if exists report_evidence_owner_write on public.report_evidence;
create policy report_evidence_owner_write
  on public.report_evidence
  for all
  to authenticated
  using (
    exists (
      select 1
      from public.certified_reports cr
      where cr."reportId" = report_evidence."reportId"
        and cr."userId" = auth.uid()
    )
  )
  with check (
    exists (
      select 1
      from public.certified_reports cr
      where cr."reportId" = report_evidence."reportId"
        and cr."userId" = auth.uid()
    )
  );

-- -----------------------------------------------------------------------------
-- 4. public.diagnostic_snapshots
-- -----------------------------------------------------------------------------
create table if not exists public.diagnostic_snapshots (
  "snapshotId" text primary key,
  "vehicleId" text not null,
  "sessionId" text,
  "createdAtMs" bigint not null default (extract(epoch from now()) * 1000)::bigint,
  "dtcsActive" text[] not null default '{}'::text[],
  "dtcsPending" text[] not null default '{}'::text[],
  "dtcsPermanent" text[] not null default '{}'::text[],
  "freezeFramePidValues" jsonb not null default '{}'::jsonb,
  "livePids" jsonb not null default '{}'::jsonb,
  "readiness" jsonb not null default '{}'::jsonb,
  "ecuVoltage" double precision,
  "rpm" double precision,
  "coolantTempC" double precision,
  "speedKph" double precision,
  "engineLoadPct" double precision,
  "fuelTrimStft" double precision,
  "fuelTrimLtft" double precision,
  "rawFrames" text[] not null default '{}'::text[],
  "notes" text not null default '',
  "liveFromAdapter" boolean not null default false,
  "provenance" public.diagnostic_provenance not null default 'MANUAL',
  "hashSha256" text not null,
  "reportId" text references public.certified_reports("reportId") on delete set null
);

create index if not exists idx_diagnostic_snapshots_vehicle
  on public.diagnostic_snapshots ("vehicleId", "createdAtMs" desc);
create index if not exists idx_diagnostic_snapshots_report
  on public.diagnostic_snapshots ("reportId");
create index if not exists idx_diagnostic_snapshots_dtc_active_gin
  on public.diagnostic_snapshots using gin ("dtcsActive");
create index if not exists idx_diagnostic_snapshots_hash
  on public.diagnostic_snapshots ("hashSha256");

alter table public.diagnostic_snapshots enable row level security;

-- Snapshots are tied to a reportId when used in a certified report. The
-- RLS reads as: I can see a snapshot if I own the linked report, or if
-- the snapshot is orphan (no report yet) AND I am the report owner.
drop policy if exists diagnostic_snapshots_owner_read on public.diagnostic_snapshots;
create policy diagnostic_snapshots_owner_read
  on public.diagnostic_snapshots
  for select
  to authenticated
  using (
    exists (
      select 1
      from public.certified_reports cr
      where cr."reportId" = diagnostic_snapshots."reportId"
        and cr."userId" = auth.uid()
    )
  );

drop policy if exists diagnostic_snapshots_owner_write on public.diagnostic_snapshots;
create policy diagnostic_snapshots_owner_write
  on public.diagnostic_snapshots
  for all
  to authenticated
  using (
    exists (
      select 1
      from public.certified_reports cr
      where cr."reportId" = diagnostic_snapshots."reportId"
        and cr."userId" = auth.uid()
    )
  )
  with check (
    exists (
      select 1
      from public.certified_reports cr
      where cr."reportId" = diagnostic_snapshots."reportId"
        and cr."userId" = auth.uid()
    )
  );

-- -----------------------------------------------------------------------------
-- 5. public.repair_actions
-- -----------------------------------------------------------------------------
create table if not exists public.repair_actions (
  "actionId" text primary key,
  "reportId" text not null references public.certified_reports("reportId") on delete cascade,
  "actionType" text not null,
  "component" text not null,
  "dtcRelated" text,
  "description" text not null default '',
  "partUsed" text,
  "supplier" text,
  "mechanic" text,
  "cost" double precision,
  "currency" text not null default 'CRC',
  "warrantyDays" integer,
  "createdAt" bigint not null default (extract(epoch from now()) * 1000)::bigint
);

create index if not exists idx_repair_actions_report
  on public.repair_actions ("reportId");
create index if not exists idx_repair_actions_dtc
  on public.repair_actions ("dtcRelated");
create index if not exists idx_repair_actions_supplier
  on public.repair_actions ("supplier");

alter table public.repair_actions enable row level security;

drop policy if exists repair_actions_owner_read on public.repair_actions;
create policy repair_actions_owner_read
  on public.repair_actions
  for select
  to authenticated
  using (
    exists (
      select 1
      from public.certified_reports cr
      where cr."reportId" = repair_actions."reportId"
        and cr."userId" = auth.uid()
    )
  );

drop policy if exists repair_actions_owner_write on public.repair_actions;
create policy repair_actions_owner_write
  on public.repair_actions
  for all
  to authenticated
  using (
    exists (
      select 1
      from public.certified_reports cr
      where cr."reportId" = repair_actions."reportId"
        and cr."userId" = auth.uid()
    )
  )
  with check (
    exists (
      select 1
      from public.certified_reports cr
      where cr."reportId" = repair_actions."reportId"
        and cr."userId" = auth.uid()
    )
  );

-- -----------------------------------------------------------------------------
-- 6. public.report_signatures
-- -----------------------------------------------------------------------------
create table if not exists public.report_signatures (
  "signatureId" text primary key,
  "reportId" text not null references public.certified_reports("reportId") on delete cascade,
  "signerName" text not null,
  "signerRole" text not null,
  "signatureImageUri" text not null,
  "signedAt" bigint not null default (extract(epoch from now()) * 1000)::bigint,
  "deviceIdHash" text not null,
  "integrityHash" text not null
);

create unique index if not exists idx_report_signatures_report_unique
  on public.report_signatures ("reportId");
create index if not exists idx_report_signatures_signer
  on public.report_signatures ("signerName");

alter table public.report_signatures enable row level security;

drop policy if exists report_signatures_owner_read on public.report_signatures;
create policy report_signatures_owner_read
  on public.report_signatures
  for select
  to authenticated
  using (
    exists (
      select 1
      from public.certified_reports cr
      where cr."reportId" = report_signatures."reportId"
        and cr."userId" = auth.uid()
    )
  );

drop policy if exists report_signatures_owner_write on public.report_signatures;
create policy report_signatures_owner_write
  on public.report_signatures
  for insert
  to authenticated
  with check (
    exists (
      select 1
      from public.certified_reports cr
      where cr."reportId" = report_signatures."reportId"
        and cr."userId" = auth.uid()
    )
  );

-- -----------------------------------------------------------------------------
-- 7. Invariants (triggers)
-- -----------------------------------------------------------------------------

-- Once a report is SIGNED, it must not be silently mutated. The application
-- is expected to write a new draft and bump version. This trigger raises
-- an exception if a SIGNED row is updated and the integrityHash is
-- unchanged.
create or replace function public.certified_reports_no_silent_mutation()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if OLD."status" = 'SIGNED' and NEW."integrityHash" = OLD."integrityHash" then
    raise exception 'A signed report cannot be mutated without re-issuing a draft and signing again.';
  end if;
  if OLD."status" = 'SIGNED' and NEW."integrityHash" is distinct from OLD."integrityHash" then
    -- Allowed: VOIDED transitions on signed reports are explicit and re-hashed.
    if NEW."status" not in ('VOIDED', 'SHARED', 'EXPORTED') then
      raise exception 'Signed report cannot change hash without moving to VOIDED/SHARED/EXPORTED.';
    end if;
  end if;
  NEW."updatedAt" = (extract(epoch from now()) * 1000)::bigint;
  return NEW;
end;
$$;

drop trigger if exists trg_certified_reports_no_silent_mutation
  on public.certified_reports;
create trigger trg_certified_reports_no_silent_mutation
before update on public.certified_reports
for each row execute function public.certified_reports_no_silent_mutation();

-- -----------------------------------------------------------------------------
-- 8. Comments
-- -----------------------------------------------------------------------------
comment on table public.certified_reports
  is 'Header of a certified report (Pre/Post-Scan, Repair, Peritaje, DVIR).';
comment on column public.certified_reports."integrityHash"
  is 'SHA-256 of the canonical content (vehicle, snapshot, evidence, repair, peritaje).';
comment on column public.certified_reports."previousHash"
  is 'integrityHash of the prior signed report for the same vehicle. Forms the per-vehicle chain.';
comment on table public.report_evidence
  is 'PHOTO / OBD_SNAPSHOT / SIGNATURE / MEASUREMENT / etc. attached to a certified report.';
comment on table public.diagnostic_snapshots
  is 'OBD snapshot mirror of the Kotlin DiagnosticSnapshot. Linked to a report by reportId.';
comment on column public.diagnostic_snapshots."hashSha256"
  is 'SHA-256 of the canonical snapshot fields (see lib/reports/hash.ts canonicalSnapshotString).';
comment on table public.repair_actions
  is 'Repair actions taken in a REPAIR_EVIDENCE_REPORT or POST_SCAN_REPORT.';
comment on table public.report_signatures
  is 'Signer + signature image + SHA-256 of (integrityHash + signer + role + timestamp + deviceIdHash).';
