import { readBoundedBody } from '../_shared/safety-http.ts';
import { createClient } from 'npm:@supabase/supabase-js@2.104.1';
import { sha256Base64url, verifyGatewayBinding, verifyMachineJwt } from './auth.ts';
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' } });
Deno.serve(async (request: Request) => {
 if (request.method !== 'POST') return json({ error: 'METHOD_NOT_ALLOWED' }, 405);
 try {
  const issuer = Deno.env.get('SAFETY_MACHINE_ISSUER'), audience = Deno.env.get('SAFETY_MACHINE_AUDIENCE');
  const gatewayIssuer = Deno.env.get('SAFETY_MTLS_GATEWAY_ISSUER'), gatewayAudience = Deno.env.get('SAFETY_MTLS_GATEWAY_AUDIENCE');
  // Operator pins public verification keys independently of all caller-controlled JWT headers.
  const machineJwks = Deno.env.get('SAFETY_MACHINE_JWKS'), gatewayJwks = Deno.env.get('SAFETY_MTLS_GATEWAY_JWKS');
  const url = Deno.env.get('SUPABASE_URL'), service = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!issuer || !audience || !gatewayIssuer || !gatewayAudience || !machineJwks || !gatewayJwks || !url || !service) return json({ error: 'EXTERNAL_IDENTITY_CONFIGURATION_REQUIRED' }, 503);
  const token = request.headers.get('authorization')?.match(/^Bearer (\S+)$/)?.[1], assertion = request.headers.get('x-safety-mtls-assertion');
  if (!token || !assertion) return json({ error: 'MACHINE_AND_MTLS_IDENTITY_REQUIRED' }, 401);
  const now = Date.now() / 1000;
  const machine = await verifyMachineJwt(token, JSON.parse(machineJwks), { issuer, audience, nowSeconds: now, maxLifetimeSeconds: 300 });
  const gateway = await verifyMachineJwt(assertion, JSON.parse(gatewayJwks), { issuer: gatewayIssuer, audience: gatewayAudience, nowSeconds: now, maxLifetimeSeconds: 30 });
  verifyGatewayBinding(machine, gateway, await sha256Base64url(token));
  const raw = await readBoundedBody(request, 1048576);
  const body = JSON.parse(raw);
  if (typeof body.action !== 'string' || !body.payload || typeof body.payload !== 'object' || Array.isArray(body.payload)) return json({ error: 'INVALID_REQUEST' }, 400);
  const server = createClient(url, service, { auth: { persistSession: false } });
  const result = await server.rpc('safety_institutional_gateway_v1', { p_issuer: machine.iss, p_subject: machine.sub,
   p_scopes: machine.scope.split(' ').filter(Boolean), p_action: body.action, p_payload: body.payload });
  return result.error ? json({ error: 'INSTITUTIONAL_OPERATION_DENIED' }, 403) : json(result.data);
 } catch (error) { return error instanceof Error && error.message === 'REQUEST_TOO_LARGE' ? json({ error: 'REQUEST_TOO_LARGE' }, 413) : json({ error: 'INSTITUTIONAL_AUTHENTICATION_REJECTED' }, 401); }
});
