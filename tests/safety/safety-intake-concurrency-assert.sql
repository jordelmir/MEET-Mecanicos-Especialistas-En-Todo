do $$ begin
 if (select count(*) from public.safety_reports where reporter_user_id='d1000000-0000-4000-8000-000000000001')<>1 then raise exception 'Concurrent rate budget permitted multiple reports'; end if;
 if (select sum(submission_count) from safety_private.intake_rate_windows where actor_id='d1000000-0000-4000-8000-000000000001')<>1 then raise exception 'Concurrent denied command consumed quota'; end if;
 if (select count(*) from public.safety_command_dedup where actor_id='d1000000-0000-4000-8000-000000000001')<>1 then raise exception 'Denied command leaked dedup reservation'; end if;
end $$;
