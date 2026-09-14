import { describe, expect, it, vi } from 'vitest';
import { createSinpeWebhookHandler } from '../../supabase/functions/sinpe-email-webhook/handler';

const config = { secret: 'test-only-adapter-secret', recipientEmail: 'receipts@example.test', configured: true };
const payload = { subject: 'BAC SINPE', text: 'Referencia: ABC123456 Monto: CRC 1,250.00' };
const request = (body: unknown = payload, secret = config.secret) => new Request('https://localhost/sinpe', {
  method: 'POST', headers: { Authorization: `Bearer ${secret}` }, body: JSON.stringify(body),
});

describe('SINPE receipt ingress authorization', () => {
  it.each([
    { ...config, secret: '' }, { ...config, secret: '   ' },
    { ...config, configured: false }, { ...config, recipientEmail: '' },
  ])('does not ingest when backend or adapter configuration is absent', async (settings) => {
    const ingest = vi.fn();
    expect((await createSinpeWebhookHandler(settings, ingest)(request())).status).toBe(503);
    expect(ingest).not.toHaveBeenCalled();
  });

  it.each(['', 'wrong-secret'])('rejects unauthenticated receipts', async (secret) => {
    const ingest = vi.fn();
    expect((await createSinpeWebhookHandler(config, ingest)(request(payload, secret))).status).toBe(401);
    expect(ingest).not.toHaveBeenCalled();
  });

  it.each([null, [], { text: {} }, { secret: 123 }])('rejects malformed fields before ingestion', async (body) => {
    const ingest = vi.fn();
    expect((await createSinpeWebhookHandler(config, ingest)(request(body))).status).toBe(400);
    expect(ingest).not.toHaveBeenCalled();
  });

  it('forwards authenticated parsed receipt to the configured recipient', async () => {
    const ingest = vi.fn().mockResolvedValue({ data: { success: true, auto_reconciled: false }, error: null });
    const response = await createSinpeWebhookHandler(config, ingest)(request());
    expect(response.status).toBe(200);
    expect(ingest).toHaveBeenCalledWith(expect.objectContaining({
      p_reference_number: 'ABC123456', p_amount_crc: 1250, p_recipient_email: config.recipientEmail,
    }));
    expect((await response.json()).result.auto_reconciled).toBe(false);
  });

  it('preserves the existing authenticated body-token adapter contract', async () => {
    const ingest = vi.fn().mockResolvedValue({ data: { success: true }, error: null });
    const req = new Request('https://localhost/sinpe', {
      method: 'POST', body: JSON.stringify({ ...payload, secret: config.secret }),
    });
    expect((await createSinpeWebhookHandler(config, ingest)(req)).status).toBe(200);
    expect(ingest).toHaveBeenCalledOnce();
  });

  it.each([
    { data: null, error: { message: 'private SQL detail' } },
    { data: { success: false, error: 'private SQL detail' }, error: null },
    { data: null, error: null },
  ])('never reports ingestion success for database failure', async (result) => {
    const ingest = vi.fn().mockResolvedValue(result);
    const response = await createSinpeWebhookHandler(config, ingest)(request());
    expect(response.status).toBe(502);
    expect(await response.text()).not.toContain('private SQL detail');
  });

  it('redacts backend exceptions', async () => {
    const ingest = vi.fn().mockRejectedValue(new Error('private credential'));
    const response = await createSinpeWebhookHandler(config, ingest)(request());
    expect(response.status).toBe(502);
    expect(await response.text()).not.toContain('private credential');
  });
});
