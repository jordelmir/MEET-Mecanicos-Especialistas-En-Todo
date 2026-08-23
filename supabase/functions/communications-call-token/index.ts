import { createClient } from 'npm:@supabase/supabase-js@2';
import { AccessToken } from 'npm:livekit-server-sdk@2.17.0';

type TokenRequest = {
  roomName?: string;
  participantName?: string;
  participantIdentity?: string;
};

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
  });
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`MISSING_${name}`);
  return value;
}

function asWebSocketUrl(raw: string): string {
  const url = new URL(raw);
  if (url.protocol === 'https:') url.protocol = 'wss:';
  if (url.protocol !== 'wss:') throw new Error('LIVEKIT_URL_MUST_BE_SECURE');
  return url.toString().replace(/\/$/, '');
}

Deno.serve(async request => {
  if (request.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  if (request.method !== 'POST') return json(405, { error: 'METHOD_NOT_ALLOWED' });

  const authorization = request.headers.get('Authorization');
  if (!authorization?.startsWith('Bearer ')) return json(401, { error: 'AUTHENTICATION_REQUIRED' });

  let body: TokenRequest;
  try {
    body = await request.json();
  } catch {
    return json(400, { error: 'INVALID_JSON' });
  }

  const conversationId = body.roomName?.trim();
  if (!conversationId || !/^[0-9a-f-]{36}$/i.test(conversationId)) {
    return json(400, { error: 'INVALID_CONVERSATION' });
  }

  try {
    const supabaseUrl = requiredEnv('SUPABASE_URL');
    const anonKey = requiredEnv('SUPABASE_ANON_KEY');
    const serviceKey = requiredEnv('SUPABASE_SERVICE_ROLE_KEY');
    const userClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const adminClient = createClient(supabaseUrl, serviceKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    const { data: userData, error: userError } = await userClient.auth.getUser();
    const user = userData.user;
    if (userError || !user) return json(401, { error: 'AUTHENTICATION_REQUIRED' });
    if (body.participantIdentity && body.participantIdentity !== user.id) {
      return json(403, { error: 'IDENTITY_MISMATCH' });
    }

    // This query executes under the caller JWT. RLS makes non-participant
    // conversations indistinguishable from missing conversations.
    const { data: participant } = await userClient
      .from('communication_participants')
      .select('conversation_id,membership_state')
      .eq('conversation_id', conversationId)
      .eq('principal_id', user.id)
      .eq('membership_state', 'ACTIVE')
      .maybeSingle();
    if (!participant) return json(404, { error: 'CONVERSATION_NOT_AVAILABLE' });

    const { data: conversation } = await userClient
      .from('communication_conversations')
      .select('id,request_state')
      .eq('id', conversationId)
      .eq('request_state', 'ACCEPTED')
      .maybeSingle();
    if (!conversation) return json(403, { error: 'MESSAGE_REQUEST_NOT_ACCEPTED' });

    const { data: activeParticipants, error: participantError } = await adminClient
      .from('communication_participants')
      .select('principal_id')
      .eq('conversation_id', conversationId)
      .eq('membership_state', 'ACTIVE');
    if (participantError) return json(503, { error: 'CALL_AUTHORIZATION_FAILED' });

    const participantIds = (activeParticipants ?? []).map(row => row.principal_id as string);
    const otherParticipantIds = participantIds.filter(id => id !== user.id);
    if (!participantIds.includes(user.id) || otherParticipantIds.length < 1) {
      return json(409, { error: 'SECOND_PARTICIPANT_REQUIRED' });
    }

    // A block in either direction disables calls. Service-role access is used
    // only after authenticating the caller and proving active membership.
    const [outgoingBlock, incomingBlock] = await Promise.all([
      adminClient
        .from('communication_blocks')
        .select('blocked_id', { count: 'exact', head: true })
        .eq('blocker_id', user.id)
        .in('blocked_id', otherParticipantIds),
      adminClient
        .from('communication_blocks')
        .select('blocker_id', { count: 'exact', head: true })
        .eq('blocked_id', user.id)
        .in('blocker_id', otherParticipantIds),
    ]);
    if (outgoingBlock.error || incomingBlock.error) {
      return json(503, { error: 'CALL_AUTHORIZATION_FAILED' });
    }
    if ((outgoingBlock.count ?? 0) > 0 || (incomingBlock.count ?? 0) > 0) {
      return json(403, { error: 'COMMUNICATION_BLOCKED' });
    }

    const rateWindow = new Date(Date.now() - 10 * 60_000).toISOString();
    const { count: recentCallCount, error: rateError } = await adminClient
      .from('communication_call_sessions')
      .select('id', { count: 'exact', head: true })
      .eq('initiated_by', user.id)
      .gte('created_at', rateWindow);
    if (rateError) return json(503, { error: 'CALL_AUTHORIZATION_FAILED' });
    if ((recentCallCount ?? 0) >= 5) return json(429, { error: 'CALL_RATE_LIMITED' });

    const activeSince = new Date(Date.now() - 2 * 60_000).toISOString();
    let { data: call } = await adminClient
      .from('communication_call_sessions')
      .select('id,livekit_room_name')
      .eq('conversation_id', conversationId)
      .in('state', ['RINGING', 'ACTIVE'])
      .gte('created_at', activeSince)
      .order('created_at', { ascending: false })
      .limit(1)
      .maybeSingle();

    if (!call) {
      const callId = crypto.randomUUID();
      const inserted = await adminClient
        .from('communication_call_sessions')
        .insert({
          id: callId,
          conversation_id: conversationId,
          initiated_by: user.id,
          media_type: 'AUDIO',
          state: 'RINGING',
          livekit_room_name: `elysium-${callId}`,
        })
        .select('id,livekit_room_name')
        .single();
      if (inserted.error || !inserted.data) return json(503, { error: 'CALL_AUTHORIZATION_FAILED' });
      call = inserted.data;
    }

    const livekitUrl = asWebSocketUrl(requiredEnv('LIVEKIT_URL'));
    const token = new AccessToken(
      requiredEnv('LIVEKIT_API_KEY'),
      requiredEnv('LIVEKIT_API_SECRET'),
      {
        identity: user.id,
        name: body.participantName?.trim().slice(0, 120) || 'Elysium user',
        ttl: 300,
      },
    );
    token.addGrant({
      roomJoin: true,
      room: call.livekit_room_name,
      canPublish: true,
      canSubscribe: true,
      canPublishData: true,
    });

    return json(200, {
      serverUrl: livekitUrl,
      participantToken: await token.toJwt(),
      roomName: call.livekit_room_name,
      participantName: body.participantName?.trim().slice(0, 120) || 'Elysium user',
    });
  } catch {
    // Never return infrastructure, key, SQL or JWT details to the client.
    return json(503, { error: 'CALL_SERVICE_UNAVAILABLE' });
  }
});
