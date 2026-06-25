type VerifyRequest = {
  productId: string;
  productType: 'inapp' | 'subs';
  purchaseToken: string;
  anonymousId?: string;
};

type GoogleAccessToken = {
  access_token: string;
  expires_in: number;
  token_type: string;
};

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

const encoder = new TextEncoder();

function env(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`Missing required env ${name}`);
  return value;
}

function base64Url(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach(byte => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
}

function base64UrlJson(value: unknown): string {
  return base64Url(encoder.encode(JSON.stringify(value)));
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const clean = pem.replace(/\\n/g, '\n').replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, '');
  const binary = atob(clean);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

async function sha256(value: string): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', encoder.encode(value));
  return [...new Uint8Array(hash)].map(byte => byte.toString(16).padStart(2, '0')).join('');
}

async function createJwt(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: 'RS256', typ: 'JWT' };
  const payload = {
    iss: env('GOOGLE_SERVICE_ACCOUNT_EMAIL'),
    scope: 'https://www.googleapis.com/auth/androidpublisher',
    aud: 'https://oauth2.googleapis.com/token',
    exp: now + 3600,
    iat: now,
  };
  const unsigned = `${base64UrlJson(header)}.${base64UrlJson(payload)}`;
  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToArrayBuffer(env('GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY')),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, encoder.encode(unsigned));
  return `${unsigned}.${base64Url(new Uint8Array(signature))}`;
}

async function getGoogleAccessToken(): Promise<string> {
  const assertion = await createJwt();
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }),
  });
  if (!response.ok) throw new Error(`Google OAuth failed: ${response.status} ${await response.text()}`);
  const token = await response.json() as GoogleAccessToken;
  return token.access_token;
}

async function verifyWithGoogle(input: VerifyRequest): Promise<Record<string, unknown>> {
  const packageName = env('GOOGLE_PLAY_PACKAGE_NAME');
  const accessToken = await getGoogleAccessToken();
  const encodedToken = encodeURIComponent(input.purchaseToken);
  const url = input.productType === 'subs'
    ? `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases/subscriptionsv2/tokens/${encodedToken}`
    : `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases/products/${encodeURIComponent(input.productId)}/tokens/${encodedToken}`;

  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) throw new Error(`Google Play verification failed: ${response.status} ${await response.text()}`);
  return await response.json() as Record<string, unknown>;
}

function activeStatus(productType: VerifyRequest['productType'], google: Record<string, unknown>): string {
  if (productType === 'inapp') {
    return google.purchaseState === 0 || google.purchaseState === '0' ? 'active' : 'revoked';
  }
  const lineItems = Array.isArray(google.lineItems) ? google.lineItems as Array<Record<string, unknown>> : [];
  const expiry = lineItems[0]?.expiryTime as string | undefined;
  if (expiry && new Date(expiry).getTime() < Date.now()) return 'expired';
  return 'active';
}

function expiryTime(productType: VerifyRequest['productType'], google: Record<string, unknown>): string | null {
  if (productType === 'inapp') return null;
  const lineItems = Array.isArray(google.lineItems) ? google.lineItems as Array<Record<string, unknown>> : [];
  return (lineItems[0]?.expiryTime as string | undefined) ?? null;
}

Deno.serve(async request => {
  if (request.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const input = await request.json() as VerifyRequest;
    if (!input.productId || !input.purchaseToken || !['inapp', 'subs'].includes(input.productType)) {
      return new Response(JSON.stringify({ error: 'Invalid request' }), { status: 400, headers: corsHeaders });
    }

    const authHeader = request.headers.get('Authorization') ?? '';
    const supabaseUrl = env('SUPABASE_URL');
    const serviceRole = env('SUPABASE_SERVICE_ROLE_KEY');
    const anonKey = Deno.env.get('SUPABASE_ANON_KEY') ?? request.headers.get('apikey') ?? '';

    let userId: string | null = null;
    if (authHeader.startsWith('Bearer ') && anonKey) {
      const userResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
        headers: {
          Authorization: authHeader,
          apikey: anonKey,
        },
      });
      if (userResponse.ok) {
        const user = await userResponse.json() as { id?: string };
        userId = user.id ?? null;
      }
    }

    const google = await verifyWithGoogle(input);
    const tokenHash = await sha256(input.purchaseToken);
    const status = activeStatus(input.productType, google);
    const expiresAt = expiryTime(input.productType, google);

    const productResponse = await fetch(`${supabaseUrl}/rest/v1/billing_products?product_id=eq.${encodeURIComponent(input.productId)}&select=*`, {
      headers: {
        Authorization: `Bearer ${serviceRole}`,
        apikey: serviceRole,
      },
    });
    if (!productResponse.ok) throw new Error(`Product lookup failed: ${await productResponse.text()}`);
    const products = await productResponse.json() as Array<{ entitlement_key: string }>;
    const entitlementKey = products[0]?.entitlement_key;
    if (!entitlementKey) return new Response(JSON.stringify({ error: 'Unknown product' }), { status: 404, headers: corsHeaders });

    const receiptBody = {
      user_id: userId,
      product_id: input.productId,
      product_type: input.productType,
      purchase_token_hash: tokenHash,
      order_id: (google.orderId as string | undefined) ?? null,
      purchase_state: String((google.purchaseState ?? google.subscriptionState ?? 'unknown') as string),
      acknowledgement_state: String((google.acknowledgementState ?? 'unknown') as string),
      consumption_state: String((google.consumptionState ?? 'unknown') as string),
      expiry_time: expiresAt,
      raw_response: google,
    };

    const receiptResponse = await fetch(`${supabaseUrl}/rest/v1/google_play_purchase_receipts?on_conflict=purchase_token_hash`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${serviceRole}`,
        apikey: serviceRole,
        'Content-Type': 'application/json',
        Prefer: 'resolution=merge-duplicates,return=representation',
      },
      body: JSON.stringify(receiptBody),
    });
    if (!receiptResponse.ok) throw new Error(`Receipt upsert failed: ${await receiptResponse.text()}`);
    const receipts = await receiptResponse.json() as Array<{ id: string }>;
    const receiptId = receipts[0]?.id;

    const entitlementBody = {
      user_id: userId,
      anonymous_id: userId ? null : input.anonymousId ?? crypto.randomUUID(),
      entitlement_key: entitlementKey,
      product_id: input.productId,
      source: 'google_play',
      status,
      starts_at: new Date().toISOString(),
      expires_at: expiresAt,
      latest_receipt_id: receiptId,
      metadata: { product_type: input.productType },
      updated_at: new Date().toISOString(),
    };

    const entitlementLookupFilter = userId
      ? `user_id=eq.${encodeURIComponent(userId)}&product_id=eq.${encodeURIComponent(input.productId)}`
      : `anonymous_id=eq.${encodeURIComponent(entitlementBody.anonymous_id ?? '')}&product_id=eq.${encodeURIComponent(input.productId)}`;
    const entitlementLookup = await fetch(`${supabaseUrl}/rest/v1/user_entitlements?${entitlementLookupFilter}&select=id`, {
      headers: {
        Authorization: `Bearer ${serviceRole}`,
        apikey: serviceRole,
      },
    });
    if (!entitlementLookup.ok) throw new Error(`Entitlement lookup failed: ${await entitlementLookup.text()}`);
    const existingEntitlements = await entitlementLookup.json() as Array<{ id: string }>;
    const existingEntitlementId = existingEntitlements[0]?.id;

    const entitlementUrl = existingEntitlementId
      ? `${supabaseUrl}/rest/v1/user_entitlements?id=eq.${existingEntitlementId}`
      : `${supabaseUrl}/rest/v1/user_entitlements`;
    const entitlementResponse = await fetch(entitlementUrl, {
      method: existingEntitlementId ? 'PATCH' : 'POST',
      headers: {
        Authorization: `Bearer ${serviceRole}`,
        apikey: serviceRole,
        'Content-Type': 'application/json',
        Prefer: 'return=representation',
      },
      body: JSON.stringify(entitlementBody),
    });
    if (!entitlementResponse.ok) throw new Error(`Entitlement upsert failed: ${await entitlementResponse.text()}`);

    return new Response(JSON.stringify({ ok: true, status, entitlement_key: entitlementKey, expires_at: expiresAt }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (error) {
    return new Response(JSON.stringify({ error: String(error) }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
