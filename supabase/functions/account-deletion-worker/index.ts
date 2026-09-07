// Supabase Edge Function: account-deletion-worker
// Purpose: Authoritative asynchronous worker for user account deletion and GDPR/Right-to-Erasure compliance.
// Executes database anonymization RPC followed by Supabase Auth Admin API user deletion.

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

function getEnv(name: string, fallback?: string): string {
  const value = Deno.env.get(name) ?? fallback;
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  if (req.method !== 'POST') {
    return new Response(JSON.stringify({ error: 'Method not allowed' }), {
      status: 405,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }

  try {
    const supabaseUrl = getEnv('SUPABASE_URL');
    const serviceRoleKey = getEnv('SUPABASE_SERVICE_ROLE_KEY');

    // Authorize caller: must provide service role key or internal worker secret
    const authHeader = req.headers.get('Authorization') ?? '';
    const token = authHeader.replace(/^Bearer\s+/i, '').trim();
    const workerSecret = Deno.env.get('ACCOUNT_DELETION_WORKER_SECRET');

    const isAuthorized =
      token === serviceRoleKey ||
      (workerSecret && token === workerSecret) ||
      req.headers.get('x-worker-secret') === workerSecret;

    if (!isAuthorized) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const adminClient = createClient(supabaseUrl, serviceRoleKey, {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
      },
    });

    let targetRequestId: string | null = null;
    try {
      const body = await req.json();
      targetRequestId = body.request_id ?? null;
    } catch {
      // Empty body allowed for batch processing
    }

    // Fetch pending requests
    let query = adminClient
      .from('account_deletion_requests')
      .select('id, user_id, requested_at, status')
      .eq('status', 'PENDING');

    if (targetRequestId) {
      query = query.eq('id', targetRequestId);
    } else {
      query = query.order('requested_at', { ascending: true }).limit(10);
    }

    const { data: requests, error: fetchError } = await query;
    if (fetchError) {
      return new Response(JSON.stringify({ error: fetchError.message }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const results: Array<{
      request_id: string;
      user_id: string;
      success: boolean;
      error?: string;
    }> = [];

    for (const r of requests ?? []) {
      try {
        // Step 1: Execute database anonymization and record purging RPC
        const { data: rpcResult, error: rpcError } = await adminClient.rpc(
          'process_account_deletion_request',
          { p_request_id: r.id }
        );

        if (rpcError) {
          results.push({
            request_id: r.id,
            user_id: r.user_id,
            success: false,
            error: `RPC error: ${rpcError.message}`,
          });
          continue;
        }

        if (!rpcResult?.success) {
          results.push({
            request_id: r.id,
            user_id: r.user_id,
            success: false,
            error: rpcResult?.error ?? 'RPC reported failure',
          });
          continue;
        }

        // Step 2: Delete user from Supabase Auth via Admin API
        const { error: deleteUserError } = await adminClient.auth.admin.deleteUser(
          r.user_id
        );

        if (deleteUserError) {
          // If already deleted by RPC or doesn't exist, treat as success
          if (deleteUserError.message?.toLowerCase().includes('not found')) {
            results.push({
              request_id: r.id,
              user_id: r.user_id,
              success: true,
            });
          } else {
            results.push({
              request_id: r.id,
              user_id: r.user_id,
              success: false,
              error: `Auth admin delete error: ${deleteUserError.message}`,
            });
          }
        } else {
          results.push({
            request_id: r.id,
            user_id: r.user_id,
            success: true,
          });
        }
      } catch (err: unknown) {
        const errorMsg = err instanceof Error ? err.message : String(err);
        results.push({
          request_id: r.id,
          user_id: r.user_id,
          success: false,
          error: errorMsg,
        });
      }
    }

    return new Response(
      JSON.stringify({
        success: true,
        processed_count: results.length,
        results,
        timestamp: new Date().toISOString(),
      }),
      {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      }
    );
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    return new Response(JSON.stringify({ error: errorMsg }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
