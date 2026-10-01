do $$
begin
  if (select count(*) from public.safety_publication_decisions
      where claim_id = '14141414-1414-4414-8414-141414141414'
        and decision_phase = 'FINAL' and decision = 'PUBLISH') <> 1 then
    raise exception 'Concurrent finalization created zero or multiple final decisions';
  end if;
  if (select count(*) from public.safety_public_points
      where claim_id = '14141414-1414-4414-8414-141414141414') <> 1 then
    raise exception 'Concurrent finalization created zero or multiple public points';
  end if;
  if (select count(*) from public.safety_command_dedup
      where aggregate_id = '14141414-1414-4414-8414-141414141414'
        and command_type = 'FINALIZE_CLAIM_PUBLICATION') <> 1 then
    raise exception 'Rejected racing finalizations committed idempotency receipts';
  end if;
end $$;
