set role authenticated;
select set_config('request.jwt.claim.sub', '44444444-4444-4444-8444-444444444444', false);
select set_config('request.jwt.claims', '{"sub":"44444444-4444-4444-8444-444444444444","aal":"aal2"}', false);
select public.safety_finalize_claim_publication_v1(
  :'candidate_id'::uuid, 'PUBLISH', 'INDEPENDENT_REVIEW', :'idempotency_key'::uuid
);
