import { readBoundedBody } from '../_shared/safety-http.ts';
import { createClient } from 'npm:@supabase/supabase-js@2.104.1';
import { evaluateIntegrity } from './policy.ts';
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' } });
Deno.serve(async (request: Request) => {
 if (request.method !== 'POST') return json({ error: 'METHOD_NOT_ALLOWED' }, 405);
 try {
  if (Number(request.headers.get('content-length') || 0) > 65536) return json({ error: 'REQUEST_TOO_LARGE' }, 413);
  const authorization = request.headers.get('authorization');
  if (!authorization?.startsWith('Bearer ')) return json({ error: 'AUTHENTICATION_REQUIRED' }, 401);
  const url = Deno.env.get('SUPABASE_URL'), anon = Deno.env.get('SUPABASE_ANON_KEY'), service = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!url || !anon || !service) return json({ error: 'SERVICE_CONFIGURATION_UNAVAILABLE' }, 503);
  const citizen = createClient(url, anon, { global: { headers: { Authorization: authorization } }, auth: { persistSession: false } });
  const { data: user, error: userError } = await citizen.auth.getUser();
  if (userError || !user.user) return json({ error: 'AUTHENTICATION_REQUIRED' }, 401);
  const raw = await readBoundedBody(request, 65536);
  const body = JSON.parse(raw);
  if (body.action === 'challenge') {
   const result = await citizen.rpc('safety_issue_device_challenge_v1');
   return result.error ? json({ error: 'CHALLENGE_REJECTED' }, 409) : json(result.data);
  }
  if (body.action !== 'verify' || !/^[0-9a-f-]{36}$/i.test(body.challenge_id || '') || typeof body.integrity_token !== 'string'
  || body.integrity_token.length > 60000 || typeof body.request_hash !== 'string') return json({ error: 'INVALID_REQUEST' }, 400);
  // A short-lived Google OAuth credential is injected by operator secret rotation.
  // Absence fails closed; the endpoint never fabricates provider verification.
  const googleToken = Deno.env.get('PLAY_INTEGRITY_GOOGLE_ACCESS_TOKEN');
  const certificates = (Deno.env.get('SAFETY_PLAY_CERTIFICATE_SHA256') || '').split(',').filter(Boolean);
  const version = Number(Deno.env.get('SAFETY_PLAY_MIN_VERSION_CODE'));
  if (!googleToken || certificates.length === 0 || !Number.isInteger(version) || version < 1) return json({ error: 'EXTERNAL_ATTESTATION_CONFIGURATION_REQUIRED' }, 503);
  const packageName = 'com.elysium369.meet';
  const provider = await fetch(`https://playintegrity.googleapis.com/v1/${packageName}:decodeIntegrityToken`, {
   method: 'POST', headers: { authorization: `Bearer ${googleToken}`, 'content-type': 'application/json' },
   body: JSON.stringify({ integrity_token: body.integrity_token }), signal: AbortSignal.timeout(10000)
  });
  if (!provider.ok) return json({ error: 'PROVIDER_VERIFICATION_UNAVAILABLE' }, 503);
  const decodedText = await readBoundedBody(provider, 131072);
  if (decodedText.length > 131072) return json({ error: 'INVALID_PROVIDER_RESPONSE' }, 502);
  const verdict = evaluateIntegrity(JSON.parse(decodedText), body.request_hash, { packageName, certificateSha256: certificates, minVersionCode: version, nowMillis: Date.now() });
  const digest = [...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(decodedText)))].map(x => x.toString(16).padStart(2, '0')).join('');
  const server = createClient(url, service, { auth: { persistSession: false } });
  const { data, error } = await server.rpc('safety_record_device_verdict_v1', {
   p_challenge_id: body.challenge_id, p_actor_id: user.user.id, p_request_hash: body.request_hash, p_package_name: packageName,
   p_verified: verdict.verified, p_response_digest: digest, p_reason_code: verdict.reason
  });
  return error ? json({ error: 'CHALLENGE_REJECTED' }, 409) : json(data);
 } catch (error) { return error instanceof Error && error.message === 'REQUEST_TOO_LARGE' ? json({ error: 'REQUEST_TOO_LARGE' }, 413) : json({ error: 'VERIFICATION_UNAVAILABLE' }, 503); }
});
