-- =============================================================================
-- Reports sync + verifier support
-- =============================================================================
-- Extends the `evidence_type` enum to mirror the Kotlin
-- `core/marketplace/ServiceCatalog.kt::EvidenceType` so a web client and
-- an Android client can write the same evidence rows.
--
-- The 9 original values (PR-4) are preserved. We add 10 more:
--   BEFORE_PHOTO, AFTER_PHOTO, MULTIMETER_READING, FUEL_PRESSURE_READING,
--   PART_REPLACED, RECEIPT, CUSTOMER_SIGNATURE, PROVIDER_NOTE,
--   TEST_DRIVE_RESULT, PDF_REPORT.
--
-- Total: 19 values. TypeScript and Kotlin enums in the parity PRs are
-- updated to the same 19.
-- =============================================================================

set check_function_bodies = off;
set search_path = public;

-- -----------------------------------------------------------------------------
-- 1. Extend evidence_type
-- -----------------------------------------------------------------------------
do $$ begin
  if not exists (select 1 from pg_enum
                 where enumlabel = 'BEFORE_PHOTO'
                 and enumtypid = 'public.evidence_type'::regtype) then
    alter type public.evidence_type add value 'BEFORE_PHOTO';
  end if;
  if not exists (select 1 from pg_enum
                 where enumlabel = 'AFTER_PHOTO'
                 and enumtypid = 'public.evidence_type'::regtype) then
    alter type public.evidence_type add value 'AFTER_PHOTO';
  end if;
  if not exists (select 1 from pg_enum
                 where enumlabel = 'MULTIMETER_READING'
                 and enumtypid = 'public.evidence_type'::regtype) then
    alter type public.evidence_type add value 'MULTIMETER_READING';
  end if;
  if not exists (select 1 from pg_enum
                 where enumlabel = 'FUEL_PRESSURE_READING'
                 and enumtypid = 'public.evidence_type'::regtype) then
    alter type public.evidence_type add value 'FUEL_PRESSURE_READING';
  end if;
  if not exists (select 1 from pg_enum
                 where enumlabel = 'PART_REPLACED'
                 and enumtypid = 'public.evidence_type'::regtype) then
    alter type public.evidence_type add value 'PART_REPLACED';
  end if;
  if not exists (select 1 from pg_enum
                 where enumlabel = 'RECEIPT'
                 and enumtypid = 'public.evidence_type'::regtype) then
    alter type public.evidence_type add value 'RECEIPT';
  end if;
  if not exists (select 1 from pg_enum
                 where enumlabel = 'CUSTOMER_SIGNATURE'
                 and enumtypid = 'public.evidence_type'::regtype) then
    alter type public.evidence_type add value 'CUSTOMER_SIGNATURE';
  end if;
  if not exists (select 1 from pg_enum
                 where enumlabel = 'PROVIDER_NOTE'
                 and enumtypid = 'public.evidence_type'::regtype) then
    alter type public.evidence_type add value 'PROVIDER_NOTE';
  end if;
  if not exists (select 1 from pg_enum
                 where enumlabel = 'TEST_DRIVE_RESULT'
                 and enumtypid = 'public.evidence_type'::regtype) then
    alter type public.evidence_type add value 'TEST_DRIVE_RESULT';
  end if;
  if not exists (select 1 from pg_enum
                 where enumlabel = 'PDF_REPORT'
                 and enumtypid = 'public.evidence_type'::regtype) then
    alter type public.evidence_type add value 'PDF_REPORT';
  end if;
end $$;

-- -----------------------------------------------------------------------------
-- 2. Comments
-- -----------------------------------------------------------------------------
comment on type public.evidence_type
  is 'Evidence attached to a certified report. 19 values mirror the Kotlin EvidenceType in core/marketplace/ServiceCatalog.kt. Web and Android produce the same labels.';
