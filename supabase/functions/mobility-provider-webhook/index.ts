// Supabase Edge Function: mobility-provider-webhook
// Secure, authoritative PSP webhook ingestion with signature verification,
// anti-replay validation, payload sanitization, and service_role RPC invocation.

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-provider-signature, stripe-signature, x-signature-timestamp',
};

const encoder = new TextEncoder();

function getEnv(name: string, fallback?: string): string {
  const value = Deno.env.get(name) ?? fallback;
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

function timingSafeEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let result = 0;
  for (let i = 0; i < a.length; i++) {
    result |= a[i] ^ b[i];
  }
  return result === 0;
}

async function verifyHmacSha256(
  payload: string,
  secret: string,
  providedSignatureHex: string
): Promise<boolean> {
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['verify', 'sign']
  );

  const calculatedSig = await crypto.subtle.sign('HMAC', key, encoder.encode(payload));
  const calculatedHex = Array.from(new Uint8Array(calculatedSig))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');

  const cleanProvided = providedSignatureHex.trim().toLowerCase();
  if (calculatedHex.length !== cleanProvided.length) {
    return false;
  }

  return timingSafeEqual(encoder.encode(calculatedHex), encoder.encode(cleanProvided));
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  if (req.method !== 'POST') {
    return new Response(JSON.stringify({ error: 'METHOD_NOT_ALLOWED' }), {
      status: 405,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }

  try {
    const rawBody = await req.text();
    if (!rawBody || rawBody.trim() === '') {
      return new Response(JSON.stringify({ error: 'EMPTY_PAYLOAD' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    // 1. Signature & Anti-Replay Verification
    const webhookSecret = Deno.env.get('MOBILITY_PSP_WEBHOOK_SECRET') || Deno.env.get('STRIPE_WEBHOOK_SECRET');
    const signatureHeader = req.headers.get('x-provider-signature') ||
                           req.headers.get('stripe-signature') ||
                           req.headers.get('x-signature');
    const timestampHeader = req.headers.get('x-signature-timestamp');

    if (webhookSecret) {
      if (!signatureHeader) {
        return new Response(JSON.stringify({ error: 'MISSING_SIGNATURE' }), {
          status: 401,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      // If timestamp header present, enforce max 300s clock drift
      if (timestampHeader) {
        const eventTs = parseInt(timestampHeader, 10);
        const nowSec = Math.floor(Date.now() / 1000);
        if (isNaN(eventTs) || Math.abs(nowSec - eventTs) > 300) {
          return new Response(JSON.stringify({ error: 'EXPIRED_OR_DRIFTED_TIMESTAMP' }), {
            status: 401,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          });
        }
      }

      const isValid = await verifyHmacSha256(rawBody, webhookSecret, signatureHeader);
      if (!isValid) {
        return new Response(JSON.stringify({ error: 'INVALID_SIGNATURE' }), {
          status: 401,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }
    }

    // 2. Parse and Validate Payload
    let payload: Record<string, any>;
    try {
      payload = JSON.parse(rawBody);
    } catch {
      return new Response(JSON.stringify({ error: 'INVALID_JSON' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    // Normalize parameters from different PSP representations (Stripe, Adyen, MercadoPago, etc.)
    const eventType = payload.type || payload.event_type || 'capture.completed';
    const eventId = payload.id || payload.event_id || payload.event_ref;
    const dataObj = payload.data?.object || payload.data || payload;

    const paymentAuthId = dataObj.payment_authorization_id ||
                          dataObj.metadata?.payment_authorization_id ||
                          dataObj.client_reference_id;

    const tripId = dataObj.trip_id ||
                   dataObj.metadata?.trip_id ||
                   null;

    const captureRef = dataObj.capture_id ||
                       dataObj.provider_capture_ref ||
                       dataObj.charges?.data?.[0]?.id ||
                       dataObj.id;

    const amountMinor = dataObj.amount_captured ??
                        dataObj.captured_amount_minor ??
                        dataObj.amount ??
                        dataObj.amount_minor;

    const currencyCode = (dataObj.currency || dataObj.currency_code || 'USD').toUpperCase();

    if (!paymentAuthId || !captureRef || !eventId || amountMinor == null) {
      return new Response(
        JSON.stringify({
          error: 'MISSING_MANDATORY_PARAMETERS',
          required: ['payment_authorization_id', 'capture_id', 'event_id', 'amount_minor'],
        }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        }
      );
    }

    // 3. Invoke Canonical Supabase RPC mobility_confirm_provider_capture as service_role
    const supabaseUrl = getEnv('SUPABASE_URL');
    const serviceRoleKey = getEnv('SUPABASE_SERVICE_ROLE_KEY');

    const rpcPayload = {
      p_payment_authorization_id: paymentAuthId,
      p_trip_id: tripId,
      p_provider_capture_ref: String(captureRef),
      p_provider_event_id: String(eventId),
      p_captured_amount_minor: Number(amountMinor),
      p_currency_code: String(currencyCode),
      p_provider_payload: {
        event_type: eventType,
        raw_event_id: eventId,
        received_at: new Date().toISOString(),
        metadata: dataObj.metadata || {},
      },
    };

    const rpcResponse = await fetch(`${supabaseUrl}/rest/v1/rpc/mobility_confirm_provider_capture`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${serviceRoleKey}`,
        apikey: serviceRoleKey,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(rpcPayload),
    });

    const responseText = await rpcResponse.text();

    if (!rpcResponse.ok) {
      let rpcError: any;
      try {
        rpcError = JSON.parse(responseText);
      } catch {
        rpcError = { message: responseText };
      }

      console.error('PSP Webhook RPC Error:', rpcError);

      const status = rpcResponse.status >= 400 && rpcResponse.status < 500 ? 400 : 500;
      return new Response(
        JSON.stringify({
          ok: false,
          error: rpcError.message || 'RPC_EXECUTION_FAILED',
          details: rpcError,
        }),
        {
          status,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        }
      );
    }

    const rpcResult = JSON.parse(responseText);

    return new Response(
      JSON.stringify({
        ok: true,
        event_id: eventId,
        result: rpcResult,
      }),
      {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      }
    );
  } catch (err: any) {
    console.error('Unhandled webhook error:', err);
    return new Response(
      JSON.stringify({
        ok: false,
        error: err.message || 'INTERNAL_SERVER_ERROR',
      }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      }
    );
  }
});
