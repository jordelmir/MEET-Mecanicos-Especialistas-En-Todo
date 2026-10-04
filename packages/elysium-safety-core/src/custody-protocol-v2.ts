/**
 * BLOQUE 2 — SAFETY-CUSTODY-V2 — TypeScript Implementation
 *
 * Byte-exact parity with:
 *   - Kotlin: CustodyProtocolV2.kt
 *   - PostgreSQL: safety_custody_v2_event_hash()
 *
 * Same input → Same SHA-256 across all runtimes.
 */

import { createHash } from 'crypto';

export const PROTOCOL_VERSION = 'SAFETY-CUSTODY-V2';

/**
 * Compute event hash.
 *
 * Canonical byte format:
 * ```
 * SAFETY-CUSTODY-V2\n
 * event_id:<uuid_lowercase>\n
 * event_type:<type>\n
 * actor_id:<uuid_lowercase>\n
 * timestamp:<iso8601_utc>\n
 * payload_hash:<sha256_hex_lowercase>\n
 * previous_hash:<sha256_hex_or_GENESIS_lowercase>\n
 * ```
 */
export function computeEventHash(
  eventId: string,
  eventType: string,
  actorId: string,
  timestampUtc: string,
  payloadHash: string,
  previousHash: string,
): string {
  const canonical =
    `${PROTOCOL_VERSION}\n` +
    `event_id:${eventId.toLowerCase()}\n` +
    `event_type:${eventType}\n` +
    `actor_id:${actorId.toLowerCase()}\n` +
    `timestamp:${timestampUtc}\n` +
    `payload_hash:${payloadHash.toLowerCase()}\n` +
    `previous_hash:${previousHash.toLowerCase()}\n`;

  return sha256Hex(Buffer.from(canonical, 'utf-8'));
}

/**
 * Compute chain root.
 *
 * Byte format:
 * ```
 * SAFETY-CUSTODY-V2-CHAIN\n
 * count:<N>\n
 * <hash_1>\n
 * <hash_2>\n
 * ...
 * ```
 */
export function computeChainRoot(eventHashes: string[]): string {
  let canonical = `${PROTOCOL_VERSION}-CHAIN\n`;
  canonical += `count:${eventHashes.length}\n`;
  for (const hash of eventHashes) {
    canonical += `${hash.toLowerCase()}\n`;
  }
  return sha256Hex(Buffer.from(canonical, 'utf-8'));
}

/**
 * Compute payload hash.
 */
export function computePayloadHash(payloadBytes: Buffer): string {
  return sha256Hex(payloadBytes);
}

function sha256Hex(data: Buffer): string {
  return createHash('sha256').update(data).digest('hex');
}
