import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';
import { createHmac } from 'node:crypto';
import { handleWebhook } from '../../supabase/functions/mobility-provider-webhook/handler';

const secret = 'test-webhook-only';
let env: Record<string, string>;
let fetchMock: ReturnType<typeof vi.fn>;
const payload = () => ({ type: 'capture.completed', id: 'evt_1', data: {
  payment_authorization_id: '11111111-1111-4111-8111-111111111111',
  trip_id: '22222222-2222-4222-8222-222222222222', capture_id: 'cap_1',
  amount_minor: 1200, currency: 'usd',
} });
function request(value: unknown, overrides: Record<string, string> = {}, stripe = false) {
  const body = JSON.stringify(value);
  const timestamp = String(Math.floor(Date.now() / 1000));
  const sig = createHmac('sha256', secret).update(`${timestamp}.${body}`).digest('hex');
  return new Request('https://localhost/webhook', { method: 'POST', body, headers: {
    ...(stripe ? { 'stripe-signature': `t=${timestamp},v1=${sig}` } : {
      'x-signature-timestamp': timestamp, 'x-provider-signature': sig,
    }), ...overrides,
  } });
}
beforeEach(() => {
  env = { MOBILITY_PSP_WEBHOOK_SECRET: secret, STRIPE_WEBHOOK_SECRET: secret,
    SUPABASE_URL: 'https://localhost', SUPABASE_SERVICE_ROLE_KEY: 'test-service-only' };
  vi.stubGlobal('Deno', { env: { get: (name: string) => env[name] } });
  fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ success: true })));
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => vi.unstubAllGlobals());
describe('provider capture ingress', () => {
  it('fails closed when signing secret is missing', async () => {
    delete env.MOBILITY_PSP_WEBHOOK_SECRET;
    expect((await handleWebhook(request(payload()))).status).toBe(503);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it.each(['', '0', '123junk'])('rejects missing, expired or malformed timestamp %s', async timestamp => {
    expect((await handleWebhook(request(payload(), { 'x-signature-timestamp': timestamp }))).status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it('rejects timestamp tampering even inside freshness window', async () => {
    expect((await handleWebhook(request(payload(), { 'x-signature-timestamp': String(Math.floor(Date.now()/1000)+30) }))).status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it.each([undefined, 'payment.failed', 'payment.authorized'])('rejects event %s without capture', async type => {
    const value = payload(); value.type = type as string;
    expect((await handleWebhook(request(value))).status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it.each([undefined, '', 'US'])('never invents currency %s', async currency => {
    const value = payload(); value.data.currency = currency as string;
    expect((await handleWebhook(request(value))).status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it.each([0, -1, 1.5, Number.MAX_SAFE_INTEGER+1, '1200'])('rejects invalid minor amount %s', async amount => {
    const value = payload(); value.data.amount_minor = amount as number;
    expect((await handleWebhook(request(value))).status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it('forwards signed explicit capture evidence', async () => {
    expect((await handleWebhook(request(payload()))).status).toBe(200);
    const sent = JSON.parse(fetchMock.mock.calls[0][1].body);
    expect(sent.p_currency_code).toBe('USD'); expect(sent.p_captured_amount_minor).toBe(1200);
  });
  it('accepts Stripe signed timestamp and actual amount received', async () => {
    const value = { type: 'payment_intent.succeeded', id: 'evt_2', data: { object: {
      ...payload().data, status: 'succeeded', amount_received: 1250,
    } } };
    expect((await handleWebhook(request(value, {}, true))).status).toBe(200);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).p_captured_amount_minor).toBe(1250);
  });
  it('does not expose database errors', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ message: 'private database detail' }), { status: 400 }));
    const response = await handleWebhook(request(payload()));
    expect(await response.text()).not.toContain('private database detail');
  });
});
