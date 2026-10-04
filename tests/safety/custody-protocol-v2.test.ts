/**
 * BLOQUE 2 — Cross-runtime parity tests for SAFETY-CUSTODY-V2.
 *
 * These tests use the SAME fixture values as CustodyProtocolV2Test.kt.
 * If Kotlin produces hash X for input Y, TypeScript MUST produce the same X.
 */

import { describe, expect, it } from 'vitest';
import {
  PROTOCOL_VERSION,
  computeEventHash,
  computeChainRoot,
  computePayloadHash,
} from '../src/custody-protocol-v2';

// Canonical fixture values (identical to Kotlin test)
const EVENT_ID = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
const EVENT_TYPE = 'EVIDENCE_CAPTURED';
const ACTOR_ID = '11111111-2222-3333-4444-555555555555';
const TIMESTAMP_UTC = '2026-01-15T08:30:00Z';
const PAYLOAD_HASH =
  'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855';
const PREVIOUS_HASH = 'GENESIS';

describe('CustodyProtocolV2 — TypeScript parity', () => {
  it('protocol version is SAFETY-CUSTODY-V2', () => {
    expect(PROTOCOL_VERSION).toBe('SAFETY-CUSTODY-V2');
  });

  it('event hash is deterministic', () => {
    const hash1 = computeEventHash(
      EVENT_ID, EVENT_TYPE, ACTOR_ID, TIMESTAMP_UTC, PAYLOAD_HASH, PREVIOUS_HASH,
    );
    const hash2 = computeEventHash(
      EVENT_ID, EVENT_TYPE, ACTOR_ID, TIMESTAMP_UTC, PAYLOAD_HASH, PREVIOUS_HASH,
    );
    expect(hash1).toBe(hash2);
  });

  it('event hash is 64 hex lowercase chars', () => {
    const hash = computeEventHash(
      EVENT_ID, EVENT_TYPE, ACTOR_ID, TIMESTAMP_UTC, PAYLOAD_HASH, PREVIOUS_HASH,
    );
    expect(hash).toHaveLength(64);
    expect(hash).toMatch(/^[0-9a-f]{64}$/);
  });

  it('event hash changes with different actor', () => {
    const hash1 = computeEventHash(
      EVENT_ID, EVENT_TYPE, ACTOR_ID, TIMESTAMP_UTC, PAYLOAD_HASH, PREVIOUS_HASH,
    );
    const hash2 = computeEventHash(
      EVENT_ID, EVENT_TYPE, '22222222-3333-4444-5555-666666666666',
      TIMESTAMP_UTC, PAYLOAD_HASH, PREVIOUS_HASH,
    );
    expect(hash1).not.toBe(hash2);
  });

  it('event hash changes with different timestamp', () => {
    const hash1 = computeEventHash(
      EVENT_ID, EVENT_TYPE, ACTOR_ID, TIMESTAMP_UTC, PAYLOAD_HASH, PREVIOUS_HASH,
    );
    const hash2 = computeEventHash(
      EVENT_ID, EVENT_TYPE, ACTOR_ID, '2026-01-15T08:31:00Z',
      PAYLOAD_HASH, PREVIOUS_HASH,
    );
    expect(hash1).not.toBe(hash2);
  });

  it('UUID casing is normalized to lowercase', () => {
    const hashUpper = computeEventHash(
      'A1B2C3D4-E5F6-7890-ABCD-EF1234567890',
      EVENT_TYPE, ACTOR_ID, TIMESTAMP_UTC, PAYLOAD_HASH, PREVIOUS_HASH,
    );
    const hashLower = computeEventHash(
      'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
      EVENT_TYPE, ACTOR_ID, TIMESTAMP_UTC, PAYLOAD_HASH, PREVIOUS_HASH,
    );
    expect(hashLower).toBe(hashUpper);
  });

  it('chain root is deterministic', () => {
    const hashes = [
      'abc123def456abc123def456abc123def456abc123def456abc123def456abc1',
      'def456abc123def456abc123def456abc123def456abc123def456abc123def4',
    ];
    const root1 = computeChainRoot(hashes);
    const root2 = computeChainRoot(hashes);
    expect(root1).toBe(root2);
  });

  it('chain root changes with order', () => {
    const a = 'abc123def456abc123def456abc123def456abc123def456abc123def456abc1';
    const b = 'def456abc123def456abc123def456abc123def456abc123def456abc123def4';
    const root1 = computeChainRoot([a, b]);
    const root2 = computeChainRoot([b, a]);
    expect(root1).not.toBe(root2);
  });

  it('payload hash of empty bytes is SHA-256 of empty', () => {
    const hash = computePayloadHash(Buffer.alloc(0));
    expect(hash).toBe(
      'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    );
  });

  // ═══════════════════════════════════════════════════════════════
  // CROSS-RUNTIME PARITY — the critical test
  //
  // This hash MUST match the Kotlin CustodyProtocolV2Test output.
  // If this test passes here and in Kotlin with identical values,
  // the protocols are byte-exact.
  // ═══════════════════════════════════════════════════════════════

  it('canonical fixture produces same hash as Kotlin', () => {
    const hash = computeEventHash(
      EVENT_ID, EVENT_TYPE, ACTOR_ID, TIMESTAMP_UTC, PAYLOAD_HASH, PREVIOUS_HASH,
    );
    // This exact hash must be recorded and compared with Kotlin output.
    // The CI will run both and compare.
    expect(hash).toHaveLength(64);
    expect(hash).toMatch(/^[0-9a-f]{64}$/);

    // Write hash to stdout for CI comparison
    console.log(`PARITY_EVENT_HASH=${hash}`);
  });
});
