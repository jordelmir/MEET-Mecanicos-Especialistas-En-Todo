export interface CustodyEventV2 {
  eventId: string; evidenceId: string; previousHash: string | null;
  eventType: string; actor: string; reasonCode: string; serverSha256: string | null;
  occurredAtUtc: string;
}

/** Exact PostgreSQL V2 wire contract: US delimiters, fixed UTC microseconds. */
export function canonicalCustodyV2(event: CustodyEventV2): string {
  const fields = ['MEET-SAFETY-CUSTODY-V2',event.eventId,event.evidenceId,event.previousHash ?? '',
    event.eventType,event.actor,event.reasonCode,event.serverSha256 ?? '',event.occurredAtUtc];
  if (fields.some(field => /[\u001f\r\n]/.test(field))) throw new Error('AMBIGUOUS_CUSTODY_FIELD');
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
  if (!uuid.test(event.eventId) || !uuid.test(event.evidenceId)) throw new Error('INVALID_CUSTODY_ID');
  for (const hash of [event.previousHash,event.serverSha256]) if (hash !== null && !/^[a-f0-9]{64}$/.test(hash)) throw new Error('INVALID_CUSTODY_HASH');
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z$/.test(event.occurredAtUtc) || !Number.isFinite(Date.parse(event.occurredAtUtc))) throw new Error('INVALID_CUSTODY_TIME');
  return fields.join('\u001f');
}

/** SHA-256 over bytes, available in browsers, Node WebCrypto and Deno. */
export async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', new Uint8Array(bytes)));
  return Array.from(digest,b=>b.toString(16).padStart(2,'0')).join('');
}

/** Verifies a supplied V2 chain without promoting it to server authority. */
export async function verifyCustodyV2(events: Array<{event:CustodyEventV2;hash:string}>, legacyAnchor: string | null = null): Promise<boolean> {
  if (legacyAnchor !== null && !/^[a-f0-9]{64}$/.test(legacyAnchor)) return false;
  let previous: string | null = legacyAnchor;
  for (const row of events) {
    if (row.event.previousHash !== previous) return false;
    try { if (await sha256Hex(new TextEncoder().encode(canonicalCustodyV2(row.event))) !== row.hash) return false; }
    catch { return false; }
    previous = row.hash;
  }
  return events.length > 0;
}
