import { createClient } from 'npm:@supabase/supabase-js@2.104.1';
import { validEvidencePath, verifyEvidenceResponse } from './handler.ts';
import { readBoundedJson } from '../_shared/bounded-json.ts';

const headers = { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' };
const json = (status: number, value: unknown) => new Response(JSON.stringify(value), { status, headers });

Deno.serve(async request => {
  if (request.method !== 'POST') return json(405, { error: 'METHOD_NOT_ALLOWED' });
  const authorization = request.headers.get('authorization');
  if (!authorization?.startsWith('Bearer ')) return json(401, { error: 'AUTHENTICATION_REQUIRED' });
  if (Number(request.headers.get('content-length')) > 4096) return json(413, { error: 'REQUEST_TOO_LARGE' });
  try {
    const parsed = await readBoundedJson(request, 4096);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return json(400, { error: 'INVALID_REQUEST' });
    const body = parsed as Record<string,unknown>;
    if (typeof body.evidence_id !== 'string' || !/^[0-9a-f-]{36}$/i.test(body.evidence_id)) return json(400, { error: 'INVALID_EVIDENCE_ID' });
    const baseUrl = Deno.env.get('SUPABASE_URL');
    const anonKey = Deno.env.get('SUPABASE_ANON_KEY');
    const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
    if (!baseUrl || !anonKey || !serviceKey) return json(503, { error: 'VERIFIER_UNAVAILABLE' });
    const caller = createClient(baseUrl, anonKey, {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: user, error: authError } = await caller.auth.getUser();
    if (authError || !user.user) return json(401, { error: 'AUTHENTICATION_REQUIRED' });
    // The descriptor query executes with caller RLS before any elevated storage access.
    const { data: evidence, error } = await caller.from('safety_evidence_objects')
      .select('id,storage_path,content_sha256,byte_count,uploader_user_id,report_id')
      .eq('id', body.evidence_id).maybeSingle();
    if (error || !evidence || !validEvidencePath(evidence, user.user.id)) return json(404, { error: 'EVIDENCE_NOT_AVAILABLE' });
    const previous = await caller.from('safety_evidence_verifications')
      .select('id,verification_state').eq('evidence_id', evidence.id)
      .in('verification_state', ['MATCH','MISMATCH','QUARANTINED']);
    if (previous.error) return json(503, { error: 'VERIFIER_UNAVAILABLE' });
    const terminal = previous.data?.find(row => row.verification_state !== 'MATCH') ?? previous.data?.[0];
    if (terminal) return json(200, { ok: true, verification_id: terminal.id, verification_state: terminal.verification_state });
    const storageUrl = new URL(`${baseUrl.replace(/\/$/,'')}/storage/v1/object/authenticated/safety-evidence-original/${evidence.storage_path.split('/').map(encodeURIComponent).join('/')}`);
    if (storageUrl.protocol !== 'https:' && !['localhost','127.0.0.1','kong'].includes(storageUrl.hostname)) return json(503, { error: 'VERIFIER_UNAVAILABLE' });
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 30_000);
    let result;
    try {
      const response = await fetch(storageUrl, { headers: { Authorization: `Bearer ${serviceKey}`, apikey: serviceKey }, redirect: 'error', signal: controller.signal });
      result = await verifyEvidenceResponse(evidence, response);
    } finally { clearTimeout(timeout); }
    const service = createClient(baseUrl, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const recorded = await service.rpc('safety_record_evidence_verification_v2', {
      p_evidence_id: evidence.id, p_state: result.state, p_server_sha256: result.sha256,
      p_server_byte_count: result.byteCount, p_verifier_version: 'SAFETY-EVIDENCE-VERIFY-V2',
    });
    if (recorded.error) return json(503, { error: 'VERIFICATION_RECORD_PENDING' });
    return json(result.state === 'ERROR' ? 503 : 200, recorded.data);
  } catch (error) {
    if (error instanceof Error && error.message === 'REQUEST_TOO_LARGE') return json(413, { error: 'REQUEST_TOO_LARGE' });
    return json(503, { error: 'VERIFIER_UNAVAILABLE' });
  }
});
