/**
 * ═══════════════════════════════════════════════════════════════════
 * Phase 11 — Ed25519 REAL SIGNATURE VERIFICATION
 *
 * Replaces SIGNATURE_PRESENT with SIGNATURE_VALID.
 * Uses Node.js crypto for Ed25519 verification.
 * ═══════════════════════════════════════════════════════════════════
 */

import { createHash, verify as cryptoVerify, createPublicKey } from 'crypto';

export const PROTOCOL_VERSION = 'SAFETY-CUSTODY-V2';

/**
 * Build the canonical signed payload for a checkpoint.
 * This is the exact bytes that were signed.
 */
export function buildCheckpointSignedPayload(
  rootHash: string,
  eventCount: number,
  firstEventHash?: string,
  lastEventHash?: string,
): Buffer {
  const canonical =
    `${PROTOCOL_VERSION}-CHECKPOINT\n` +
    `root_hash:${rootHash.toLowerCase()}\n` +
    `event_count:${eventCount}\n` +
    `first_event_hash:${(firstEventHash || 'NONE').toLowerCase()}\n` +
    `last_event_hash:${(lastEventHash || 'NONE').toLowerCase()}\n`;
  return Buffer.from(canonical, 'utf-8');
}

/**
 * Verify an Ed25519 signature on a checkpoint.
 *
 * @returns true if the signature is cryptographically valid
 */
export function verifyCheckpointSignature(
  publicKeyBase64: string,
  signatureBase64: string,
  rootHash: string,
  eventCount: number,
  firstEventHash?: string,
  lastEventHash?: string,
): boolean {
  try {
    const payload = buildCheckpointSignedPayload(
      rootHash, eventCount, firstEventHash, lastEventHash,
    );
    const signatureBytes = Buffer.from(signatureBase64, 'base64');
    const publicKeyBytes = Buffer.from(publicKeyBase64, 'base64');

    // Build Ed25519 public key object
    const publicKey = createPublicKey({
      key: Buffer.concat([
        // Ed25519 DER prefix for 32-byte raw key
        Buffer.from('302a300506032b6570032100', 'hex'),
        publicKeyBytes,
      ]),
      format: 'der',
      type: 'spki',
    });

    return cryptoVerify(
      null, // Ed25519 doesn't use a digest algorithm
      payload,
      publicKey,
      signatureBytes,
    );
  } catch {
    return false;
  }
}

/**
 * Tamper detection result
 */
export interface TamperTestResult {
  field: string;
  tampered: boolean;
  rejected: boolean;
}

/**
 * Run tamper tests against a signed checkpoint.
 * Each test modifies one field and expects verification to FAIL.
 */
export function runTamperTests(
  publicKeyBase64: string,
  signatureBase64: string,
  rootHash: string,
  eventCount: number,
  firstEventHash?: string,
  lastEventHash?: string,
): TamperTestResult[] {
  const results: TamperTestResult[] = [];

  // Original must PASS
  const originalValid = verifyCheckpointSignature(
    publicKeyBase64, signatureBase64, rootHash, eventCount,
    firstEventHash, lastEventHash,
  );

  results.push({
    field: 'ORIGINAL',
    tampered: false,
    rejected: !originalValid, // should NOT be rejected
  });

  // Modified rootHash
  const tamperedRoot = rootHash.replace(/^./, 'f');
  results.push({
    field: 'ROOT_HASH',
    tampered: true,
    rejected: !verifyCheckpointSignature(
      publicKeyBase64, signatureBase64, tamperedRoot, eventCount,
      firstEventHash, lastEventHash,
    ),
  });

  // Modified eventCount
  results.push({
    field: 'EVENT_COUNT',
    tampered: true,
    rejected: !verifyCheckpointSignature(
      publicKeyBase64, signatureBase64, rootHash, eventCount + 1,
      firstEventHash, lastEventHash,
    ),
  });

  // Modified signature
  const tamperedSig = Buffer.from(signatureBase64, 'base64');
  if (tamperedSig.length > 0) tamperedSig[0] ^= 0xff;
  results.push({
    field: 'SIGNATURE',
    tampered: true,
    rejected: !verifyCheckpointSignature(
      publicKeyBase64, tamperedSig.toString('base64'), rootHash, eventCount,
      firstEventHash, lastEventHash,
    ),
  });

  // Wrong public key (flip first byte)
  const wrongKey = Buffer.from(publicKeyBase64, 'base64');
  if (wrongKey.length > 0) wrongKey[0] ^= 0xff;
  results.push({
    field: 'PUBLIC_KEY',
    tampered: true,
    rejected: !verifyCheckpointSignature(
      wrongKey.toString('base64'), signatureBase64, rootHash, eventCount,
      firstEventHash, lastEventHash,
    ),
  });

  // Modified firstEventHash
  if (firstEventHash) {
    results.push({
      field: 'FIRST_EVENT_HASH',
      tampered: true,
      rejected: !verifyCheckpointSignature(
        publicKeyBase64, signatureBase64, rootHash, eventCount,
        firstEventHash.replace(/^./, 'a'), lastEventHash,
      ),
    });
  }

  return results;
}
