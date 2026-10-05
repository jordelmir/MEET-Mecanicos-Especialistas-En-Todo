/**
 * Phase 11 — Ed25519 Real Verification and Tamper Attack Tests
 *
 * Verifies that the Ed25519 verification cryptographically checks the signature
 * against canonical payload bytes, rejecting any modification to rootHash, eventCount,
 * signature, public key, or first/last event hashes.
 */

import { describe, expect, it } from 'vitest';
import { generateKeyPairSync, sign } from 'crypto';
import {
  buildCheckpointSignedPayload,
  verifyCheckpointSignature,
  runTamperTests,
  PROTOCOL_VERSION,
} from '../../packages/elysium-safety-core/src/ed25519-verifier';

describe('Ed25519 Checkpoint Signature Verification', () => {
  const rootHash = 'c9d4ac3b00315e177f8387737b7ceb13b4b919d9397126e64b34d1c53c5b6ca4';
  const eventCount = 42;
  const firstEventHash = 'f40ef568a767e9a98f79c7306c4365d4d5435fe101bb613a846b00f4a90dd62a';
  const lastEventHash = 'b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3';

  // Generate real Ed25519 keypair for test
  const { publicKey, privateKey } = generateKeyPairSync('ed25519');
  const payload = buildCheckpointSignedPayload(rootHash, eventCount, firstEventHash, lastEventHash);
  const signatureBytes = sign(null, payload, privateKey);
  const signatureBase64 = signatureBytes.toString('base64');

  // Export raw 32-byte public key
  const spkiDer = publicKey.export({ type: 'spki', format: 'der' });
  // The raw 32 bytes are at the end of the SPKI DER structure (last 32 bytes)
  const rawPublicKey = spkiDer.subarray(spkiDer.length - 32);
  const publicKeyBase64 = rawPublicKey.toString('base64');

  it('validates authentic checkpoint signature (SIGNATURE_VALID)', () => {
    const valid = verifyCheckpointSignature(
      publicKeyBase64,
      signatureBase64,
      rootHash,
      eventCount,
      firstEventHash,
      lastEventHash,
    );
    expect(valid).toBe(true);
  });

  it('fails if rootHash is modified', () => {
    const tamperedRoot = rootHash.replace(/^./, '0');
    const valid = verifyCheckpointSignature(
      publicKeyBase64,
      signatureBase64,
      tamperedRoot,
      eventCount,
      firstEventHash,
      lastEventHash,
    );
    expect(valid).toBe(false);
  });

  it('fails if eventCount is modified', () => {
    const valid = verifyCheckpointSignature(
      publicKeyBase64,
      signatureBase64,
      rootHash,
      eventCount + 1,
      firstEventHash,
      lastEventHash,
    );
    expect(valid).toBe(false);
  });

  it('fails if signature is corrupted', () => {
    const corruptSig = Buffer.from(signatureBase64, 'base64');
    corruptSig[0] ^= 0xff;
    const valid = verifyCheckpointSignature(
      publicKeyBase64,
      corruptSig.toString('base64'),
      rootHash,
      eventCount,
      firstEventHash,
      lastEventHash,
    );
    expect(valid).toBe(false);
  });

  it('fails if wrong public key is used', () => {
    const otherPair = generateKeyPairSync('ed25519');
    const otherSpki = otherPair.publicKey.export({ type: 'spki', format: 'der' });
    const otherRawKey = otherSpki.subarray(otherSpki.length - 32).toString('base64');

    const valid = verifyCheckpointSignature(
      otherRawKey,
      signatureBase64,
      rootHash,
      eventCount,
      firstEventHash,
      lastEventHash,
    );
    expect(valid).toBe(false);
  });

  it('runs all tamper attack tests and rejects every modified field', () => {
    const results = runTamperTests(
      publicKeyBase64,
      signatureBase64,
      rootHash,
      eventCount,
      firstEventHash,
      lastEventHash,
    );

    // Original must NOT be rejected
    const orig = results.find(r => r.field === 'ORIGINAL');
    expect(orig).toBeDefined();
    expect(orig?.rejected).toBe(false);

    // Every tampered field MUST be rejected
    const tampered = results.filter(r => r.tampered);
    expect(tampered.length).toBeGreaterThanOrEqual(4);
    for (const test of tampered) {
      expect(test.rejected).toBe(true);
    }
  });
});
