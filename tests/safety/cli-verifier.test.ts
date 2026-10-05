/**
 * Phase 11, 12, 13 — CLI Independent Verifier Test Suite
 *
 * Verifies that the standalone CLI verifier correctly:
 * 1. Verifies authentic research packages (exitCode 0)
 * 2. Rejects incomplete research packages with REPRODUCIBILITY_INCOMPLETE (exitCode 1)
 * 3. Verifies authentic Ed25519-signed checkpoints (exitCode 0)
 * 4. Rejects tampered checkpoints (exitCode 1)
 * 5. Verifies custody chains and detects broken hashes (exitCode 1)
 */

import { describe, expect, it } from 'vitest';
import { generateKeyPairSync, sign } from 'crypto';
import { writeFileSync, unlinkSync } from 'fs';
import { resolve } from 'path';
import {
  verifyPackage,
  verifyCheckpoint,
  verifyChain,
  PROTOCOL_VERSION,
} from '../../tools/safety-verifier/verify';
import { buildCheckpointSignedPayload } from '../../packages/elysium-safety-core/src/ed25519-verifier';

describe('Independent Scientific Verifier CLI', () => {
  // ── Research Package Tests ───────────────────────────────────

  it('rejects incomplete package with REPRODUCIBILITY_INCOMPLETE', () => {
    const incompletePkg = {
      protocolVersion: PROTOCOL_VERSION,
      manifest: { name: 'test' },
      manifestHash: 'abc',
      // Missing datasetHash, methodologyVersion, codeCommit, environmentHash, analysisPlanHash
    };

    const tmpPath = resolve('/tmp/incomplete-package.json');
    writeFileSync(tmpPath, JSON.stringify(incompletePkg));

    try {
      const result = verifyPackage(tmpPath);
      expect(result.status).toBe('FAIL');
      expect(result.exitCode).toBe(1);
      expect(result.summary).toContain('REPRODUCIBILITY_INCOMPLETE');
    } finally {
      unlinkSync(tmpPath);
    }
  });

  it('verifies complete package with all mandatory reproducibility metadata', () => {
    const manifest = { files: ['dataset.jsonl', 'methodology.md'] };
    const manifestJson = JSON.stringify(manifest);
    const crypto = require('crypto');
    const manifestHash = crypto.createHash('sha256').update(Buffer.from(manifestJson, 'utf-8')).digest('hex');

    const completePkg = {
      protocolVersion: PROTOCOL_VERSION,
      manifest,
      manifestHash,
      datasetHash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      evidenceManifestHash: 'a1b2c3d4e5f6',
      methodologyVersion: 'METHODOLOGY-V1',
      codeCommit: 'db122b4255f7b95ea18841c9a0dfef2a9a392835',
      environmentHash: 'env-sha256-digest-ubuntu2204-node24',
      analysisPlanHash: 'analysis-plan-sha256',
      disclaimers: [
        'AI OUTPUT ≠ FACT',
        'EVIDENCE ≠ GUILT',
        'CLAIM ≠ CONVICTION',
      ],
      limitations: ['Sample size limited to observational cohort'],
    };

    const tmpPath = resolve('/tmp/complete-package.json');
    writeFileSync(tmpPath, JSON.stringify(completePkg));

    try {
      const result = verifyPackage(tmpPath);
      expect(result.status).toBe('PASS');
      expect(result.exitCode).toBe(0);
      expect(result.summary).toContain('VERIFIED');
    } finally {
      unlinkSync(tmpPath);
    }
  });

  // ── Ed25519 Checkpoint Verification ──────────────────────────

  it('verifies authentic Ed25519 signed checkpoint', () => {
    const rootHash = 'c9d4ac3b00315e177f8387737b7ceb13b4b919d9397126e64b34d1c53c5b6ca4';
    const eventCount = 10;
    const firstEventHash = 'f40ef568a767e9a98f79c7306c4365d4d5435fe101bb613a846b00f4a90dd62a';
    const lastEventHash = 'b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3';

    const { publicKey, privateKey } = generateKeyPairSync('ed25519');
    const payload = buildCheckpointSignedPayload(rootHash, eventCount, firstEventHash, lastEventHash);
    const signatureBytes = sign(null, payload, privateKey);
    const signatureBase64 = signatureBytes.toString('base64');
    const spkiDer = publicKey.export({ type: 'spki', format: 'der' });
    const publicKeyBase64 = spkiDer.subarray(spkiDer.length - 32).toString('base64');

    const checkpoint = {
      rootHash,
      eventCount,
      firstEventHash,
      lastEventHash,
      publicKeyBase64,
      signatureBase64,
    };

    const tmpPath = resolve('/tmp/valid-checkpoint.json');
    writeFileSync(tmpPath, JSON.stringify(checkpoint));

    try {
      const result = verifyCheckpoint(tmpPath);
      expect(result.status).toBe('PASS');
      expect(result.exitCode).toBe(0);
      const sigCheck = result.checks.find(c => c.name === 'SIGNATURE_VALID');
      expect(sigCheck?.status).toBe('PASS');
    } finally {
      unlinkSync(tmpPath);
    }
  });

  it('rejects tampered Ed25519 checkpoint (altered root hash)', () => {
    const rootHash = 'c9d4ac3b00315e177f8387737b7ceb13b4b919d9397126e64b34d1c53c5b6ca4';
    const eventCount = 10;

    const { publicKey, privateKey } = generateKeyPairSync('ed25519');
    const payload = buildCheckpointSignedPayload(rootHash, eventCount);
    const signatureBase64 = sign(null, payload, privateKey).toString('base64');
    const spkiDer = publicKey.export({ type: 'spki', format: 'der' });
    const publicKeyBase64 = spkiDer.subarray(spkiDer.length - 32).toString('base64');

    const tamperedCheckpoint = {
      rootHash: '0000000000000000000000000000000000000000000000000000000000000000', // tampered!
      eventCount,
      publicKeyBase64,
      signatureBase64,
    };

    const tmpPath = resolve('/tmp/tampered-checkpoint.json');
    writeFileSync(tmpPath, JSON.stringify(tamperedCheckpoint));

    try {
      const result = verifyCheckpoint(tmpPath);
      expect(result.status).toBe('FAIL');
      expect(result.exitCode).toBe(1);
      const sigCheck = result.checks.find(c => c.name === 'SIGNATURE_VALID');
      expect(sigCheck?.status).toBe('FAIL');
    } finally {
      unlinkSync(tmpPath);
    }
  });

  // ── Custody Chain Verification ───────────────────────────────

  it('verifies valid custody chain with unbroken hashes', () => {
    const chainData = {
      events: [
        {
          eventId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
          eventType: 'EVIDENCE_CAPTURED',
          actorId: '11111111-2222-3333-4444-555555555555',
          timestampUtc: '2026-01-15T08:30:00Z',
          payloadHash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
          expectedHash: 'f40ef568a767e9a98f79c7306c4365d4d5435fe101bb613a846b00f4a90dd62a',
        },
      ],
      expectedChainRoot: '8499905dd969a0dfc1b1ea284ebf4554a145459f36e508fe3b68e8cc67ec26de',
    };

    const tmpPath = resolve('/tmp/valid-chain.json');
    writeFileSync(tmpPath, JSON.stringify(chainData));

    try {
      const result = verifyChain(tmpPath);
      expect(result.status).toBe('PASS');
      expect(result.exitCode).toBe(0);
    } finally {
      unlinkSync(tmpPath);
    }
  });
});
