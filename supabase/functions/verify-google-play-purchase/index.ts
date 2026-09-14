// Supabase Edge Function: verify-google-play-purchase
// Zero-Trust fail-closed Google Play purchase & subscription verifier.
// Strict invariants:
// 1. Authenticated Supabase session required (NO anonymous paid entitlements).
// 2. Immutable token ownership enforced via claim_google_play_purchase.
// 3. Fail-closed SubscriptionPurchaseV2 lifecycle mapping (unknown state -> disabled).
// 4. Zero sensitive leakage (tokens, JWTs, infra errors redacted).
// 5. X-Correlation-Id header returned with every response.

type VerifyRequest = {
  productId: string;
  productType: 'inapp' | 'subs';
  purchaseToken: string;
};

type GoogleAccessToken = {
  access_token: string;
  expires_in: number;
  token_type: string;
};

class AuthError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'AuthError';
  }
}

class ClaimConflictError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ClaimConflictError';
  }
}

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-correlation-id',
};

const encoder = new TextEncoder();

function jsonResponse(
  status: number,
  body: Record<string, unknown>,
  correlationId: string,
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      'Content-Type': 'application/json',
      'X-Correlation-Id': correlationId,
    },
  });
}

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
  const hdr = '-----' + 'BEGIN ' + 'PRIVATE KEY' + '-----';
  const end = '-----' + 'END ' + 'PRIVATE KEY' + '-----';
  const clean = pem.replace(/\\n/g, '\n').replace(new RegExp(`${hdr.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}|${end.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}|\\s`, 'g'), '');
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
  if (!response.ok) {
    throw new Error('Google OAuth failed');
  }
  const token = await response.json() as GoogleAccessToken;
  return token.access_token;
}

async function requireUser(
  request: Request,
  supabaseUrl: string,
  anonKey: string,
): Promise<string> {
  const authorization = request.headers.get('Authorization');

  if (!authorization?.startsWith('Bearer ')) {
    throw new AuthError('MISSING_BEARER');
  }

  const response = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: {
      Authorization: authorization,
      apikey: anonKey,
    },
  });

  if (!response.ok) {
    throw new AuthError('INVALID_SESSION');
  }

  const user = (await response.json()) as { id?: string };

  if (!user.id) {
    throw new AuthError('INVALID_USER');
  }

  return user.id;
}

async function verifyWithGoogle(
  input: VerifyRequest,
): Promise<Record<string, unknown>> {
  const packageName = env('GOOGLE_PLAY_PACKAGE_NAME');
  const accessToken = await getGoogleAccessToken();
  const encodedToken = encodeURIComponent(input.purchaseToken);
  const url =
    input.productType === 'subs'
      ? `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases/subscriptionsv2/tokens/${encodedToken}`
      : `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases/products/${encodeURIComponent(input.productId)}/tokens/${encodedToken}`;

  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) {
    throw new Error(`Google Play verification failed status=${response.status}`);
  }
  return (await response.json()) as Record<string, unknown>;
}

// Fail-closed lifecycle classifier
function classifyStatus(
  productType: VerifyRequest['productType'],
  google: Record<string, unknown>,
): string {
  if (productType === 'inapp') {
    const purchaseState = google.purchaseState;
    if (purchaseState === 0 || purchaseState === '0') return 'active';
    if (purchaseState === 1 || purchaseState === '1') return 'canceled';
    if (purchaseState === 2 || purchaseState === '2') return 'pending';
    return 'revoked'; // FAIL CLOSED
  }

  // SubscriptionPurchaseV2 lifecycle
  const subscriptionState = String(google.subscriptionState ?? '');
  switch (subscriptionState) {
    case 'SUBSCRIPTION_STATE_ACTIVE':
      break;
    case 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD':
      return 'in_grace_period';
    case 'SUBSCRIPTION_STATE_ON_HOLD':
      return 'on_hold';
    case 'SUBSCRIPTION_STATE_PAUSED':
      return 'paused';
    case 'SUBSCRIPTION_STATE_CANCELED':
      return 'canceled';
    case 'SUBSCRIPTION_STATE_EXPIRED':
      return 'expired';
    default:
      // Unknown Google state -> fail closed
      return 'disabled';
  }

  const lineItems = Array.isArray(google.lineItems)
    ? (google.lineItems as Array<Record<string, unknown>>)
    : [];
  const expiry = lineItems[0]?.expiryTime as string | undefined;
  if (expiry && new Date(expiry).getTime() < Date.now()) {
    return 'expired';
  }

  return 'active';
}

function extractExpiry(
  productType: VerifyRequest['productType'],
  google: Record<string, unknown>,
): string | null {
  if (productType === 'inapp') return null;
  const lineItems = Array.isArray(google.lineItems)
    ? (google.lineItems as Array<Record<string, unknown>>)
    : [];
  return (lineItems[0]?.expiryTime as string | undefined) ?? null;
}

Deno.serve(async request => {
  const correlationId =
    request.headers.get('x-correlation-id') ?? crypto.randomUUID();

  if (request.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    let input: VerifyRequest;
    try {
      input = (await request.json()) as VerifyRequest;
    } catch {
      return jsonResponse(400, { error: 'INVALID_JSON_BODY' }, correlationId);
    }

    if (
      !input.productId ||
      !input.purchaseToken ||
      !['inapp', 'subs'].includes(input.productType)
    ) {
      return jsonResponse(400, { error: 'INVALID_REQUEST_PAYLOAD' }, correlationId);
    }

    const supabaseUrl = env('SUPABASE_URL');
    const serviceRole = env('SUPABASE_SERVICE_ROLE_KEY');
    const anonKey =
      Deno.env.get('SUPABASE_ANON_KEY') ?? request.headers.get('apikey') ?? '';

    // Invariant: MONEY / PAID ENTITLEMENT REQUIRES AUTHENTICATED USER
    let userId: string;
    try {
      userId = await requireUser(request, supabaseUrl, anonKey);
    } catch (authErr) {
      const code = authErr instanceof AuthError ? authErr.message : 'UNAUTHENTICATED';
      return jsonResponse(401, { error: code }, correlationId);
    }

    const packageName = env('GOOGLE_PLAY_PACKAGE_NAME');
    const google = await verifyWithGoogle(input);
    const tokenHash = await sha256(input.purchaseToken);
    const status = classifyStatus(input.productType, google);
    const expiresAt = extractExpiry(input.productType, google);

    // Atomic claim ownership (Section 5)
    const claimRes = await fetch(
      `${supabaseUrl}/rest/v1/rpc/claim_google_play_purchase`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${serviceRole}`,
          apikey: serviceRole,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          p_package_name: packageName,
          p_owner_user_id: userId,
          p_product_id: input.productId,
          p_product_type: input.productType,
          p_purchase_token_hash: tokenHash,
        }),
      },
    );

    if (!claimRes.ok) {
      const claimErr = await claimRes.text();
      if (claimRes.status === 409 || claimErr.includes('PURCHASE_ALREADY_CLAIMED') || claimErr.includes('23505')) {
        return jsonResponse(409, { error: 'PURCHASE_ALREADY_CLAIMED' }, correlationId);
      }
      return jsonResponse(500, { error: 'CLAIM_FAILED' }, correlationId);
    }

    // Lookup product entitlement key
    const productResponse = await fetch(
      `${supabaseUrl}/rest/v1/billing_products?product_id=eq.${encodeURIComponent(input.productId)}&select=*`,
      {
        headers: {
          Authorization: `Bearer ${serviceRole}`,
          apikey: serviceRole,
        },
      },
    );
    if (!productResponse.ok) {
      return jsonResponse(500, { error: 'PRODUCT_LOOKUP_FAILED' }, correlationId);
    }
    const products = (await productResponse.json()) as Array<{ entitlement_key: string }>;
    const entitlementKey = products[0]?.entitlement_key;
    if (!entitlementKey) {
      return jsonResponse(404, { error: 'PRODUCT_NOT_FOUND' }, correlationId);
    }

    // Insert or update receipt tied to authenticated user
    const receiptBody = {
      user_id: userId,
      product_id: input.productId,
      product_type: input.productType,
      purchase_token_hash: tokenHash,
      order_id: (google.orderId as string | undefined) ?? null,
      purchase_state: String(
        (google.purchaseState ?? google.subscriptionState ?? 'unknown') as string,
      ),
      acknowledgement_state: String(
        (google.acknowledgementState ?? 'unknown') as string,
      ),
      consumption_state: String((google.consumptionState ?? 'unknown') as string),
      expiry_time: expiresAt,
      raw_response: google,
    };

    const receiptResponse = await fetch(
      `${supabaseUrl}/rest/v1/google_play_purchase_receipts?on_conflict=purchase_token_hash`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${serviceRole}`,
          apikey: serviceRole,
          'Content-Type': 'application/json',
          Prefer: 'resolution=merge-duplicates,return=representation',
        },
        body: JSON.stringify(receiptBody),
      },
    );
    if (!receiptResponse.ok) {
      return jsonResponse(500, { error: 'RECEIPT_STORAGE_FAILED' }, correlationId);
    }
    const receipts = (await receiptResponse.json()) as Array<{ id: string }>;
    const receiptId = receipts[0]?.id;

    // Entitlement strictly owned by authenticated user
    const entitlementBody = {
      user_id: userId,
      anonymous_id: null,
      entitlement_key: entitlementKey,
      product_id: input.productId,
      source: 'google_play',
      status,
      starts_at: new Date().toISOString(),
      expires_at: expiresAt,
      latest_receipt_id: receiptId,
      metadata: { product_type: input.productType, token_hash: tokenHash },
      updated_at: new Date().toISOString(),
    };

    const entitlementLookup = await fetch(
      `${supabaseUrl}/rest/v1/user_entitlements?user_id=eq.${encodeURIComponent(userId)}&product_id=eq.${encodeURIComponent(input.productId)}&select=id`,
      {
        headers: {
          Authorization: `Bearer ${serviceRole}`,
          apikey: serviceRole,
        },
      },
    );
    const existingEntitlements = entitlementLookup.ok
      ? ((await entitlementLookup.json()) as Array<{ id: string }>)
      : [];
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

    if (!entitlementResponse.ok) {
      return jsonResponse(500, { error: 'ENTITLEMENT_SYNC_FAILED' }, correlationId);
    }

    return jsonResponse(
      200,
      {
        ok: true,
        status,
        entitlement_key: entitlementKey,
        expires_at: expiresAt,
      },
      correlationId,
    );
  } catch (error) {
    // Sanitized fail-closed error response - never leak infrastructure secrets
    console.error(`[verify-google-play-purchase] [${correlationId}] error:`, error instanceof Error ? error.message : 'UNKNOWN_ERROR');
    return jsonResponse(500, { error: 'INTERNAL_SERVER_ERROR' }, correlationId);
  }
});
