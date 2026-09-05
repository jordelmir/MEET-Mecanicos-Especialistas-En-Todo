-- Relax v3 evidence check: only block if evidence was uploaded but is incomplete.
-- If no evidence rows exist (v2 submissions), the owner can still decide.
create or replace function public.meet_owner_decide_verification_v3(
  p_application_id uuid, p_decision text, p_reason text
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare v_type text; v_required text[]; v_actual text[]; v_evidence_count integer;
begin
  if not public.meet_session_has_aal2() then
    raise exception using errcode='42501', message='AAL2_REQUIRED';
  end if;
  select service_type into strict v_type from public.service_verification_applications
   where id=p_application_id for update;
  if p_decision='APPROVED' then
    v_required := case v_type
      when 'PASSENGER' then array['profile','id_front','selfie_with_id']::text[]
      when 'RIDE_DRIVER' then array[
        'license_front','license_back','id_front','id_back','criminal_record',
        'marchamo','inspection','insurance','profile','selfie_with_id',
        'selfie_with_license','vehicle_front','vehicle_back','vehicle_interior'
      ]::text[] else array[]::text[] end;
    select count(*) into v_evidence_count
      from public.service_verification_evidence e
     where e.application_id=p_application_id;
    if v_evidence_count > 0 then
      select coalesce(array_agg(e.evidence_kind),'{}') into v_actual
        from public.service_verification_evidence e
        join storage.objects o on o.bucket_id='trust-verification-evidence' and o.name=e.storage_path
       where e.application_id=p_application_id;
      if cardinality(v_required)>0 and not v_required <@ v_actual then
        raise exception using errcode='42501', message='REVIEWABLE_EVIDENCE_REQUIRED';
      end if;
    end if;
  end if;
  return public.meet_owner_decide_verification_v2(p_application_id,p_decision,p_reason);
end;
$$;
