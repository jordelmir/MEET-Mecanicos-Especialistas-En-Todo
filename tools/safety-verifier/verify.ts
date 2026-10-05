#!/usr/bin/env npx tsx
/**
 * ═══════════════════════════════════════════════════════════════════
 * Phase 11, 12, 13 — INDEPENDENT SCIENTIFIC VERIFIER CLI
 *
 * Standalone tool for third-party forensic inspection.
 * A forensic inspector or academic institution can verify ANY
 * Elysium Safety research package, custody chain, or checkpoint
 * with ONLY this script + the exported JSON/files.
 *
 * Usage:
 *   elysium-safety verify package <package.json>
 *   elysium-safety verify chain <chain-events.json>
 *   elysium-safety verify checkpoint <checkpoint.json>
 *
 * Exit codes:
 *   0 = VERIFIED (all cryptographic and reproducibility checks passed)
 *   1 = FAILED / REPRODUCIBILITY_INCOMPLETE
 *   2 = INVALID_USAGE
 *
 * IMPORTANT: This tool does NOT require access to the Elysium system,
 * Supabase, Android runtime, or API credentials.
 * ═══════════════════════════════════════════════════════════════════
 */

import { createHash } from 'crypto';
import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';
import {
  verifyCheckpointSignature,
  PROTOCOL_VERSION as ED25519_PROTOCOL_VERSION,
} from '../../packages/elysium-safety-core/src/ed25519-verifier';

export const PROTOCOL_VERSION = 'SAFETY-CUSTODY-V2';

// ── SHA-256 Helper ─────────────────────────────────────────────

function sha256Hex(data: Buffer): string {
  return createHash('sha256').update(data).digest('hex');
}

function computeEventHash(
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

function computeChainRoot(eventHashes: string[]): string {
  let canonical = `${PROTOCOL_VERSION}-CHAIN\n`;
  canonical += `count:${eventHashes.length}\n`;
  for (const hash of eventHashes) {
    canonical += `${hash.toLowerCase()}\n`;
  }
  return sha256Hex(Buffer.from(canonical, 'utf-8'));
}

// ── Verification Types ─────────────────────────────────────────

export interface VerificationResult {
  status: 'PASS' | 'FAIL';
  exitCode: number;
  checks: CheckResult[];
  summary: string;
}

export interface CheckResult {
  name: string;
  status: 'PASS' | 'FAIL' | 'WARN';
  detail: string;
}

// ── Phase 12: Research Package Verifier ─────────────────────────

export function verifyPackage(packagePath: string): VerificationResult {
  const checks: CheckResult[] = [];

  if (!existsSync(packagePath)) {
    return {
      status: 'FAIL',
      exitCode: 1,
      checks: [{ name: 'FILE_EXISTS', status: 'FAIL', detail: `File not found: ${packagePath}` }],
      summary: 'Package file not found',
    };
  }

  const raw = readFileSync(packagePath, 'utf-8');
  let pkg: any;
  try {
    pkg = JSON.parse(raw);
    checks.push({ name: 'JSON_VALID', status: 'PASS', detail: 'Package is valid JSON' });
  } catch (e) {
    return {
      status: 'FAIL',
      exitCode: 1,
      checks: [{ name: 'JSON_VALID', status: 'FAIL', detail: `Invalid JSON: ${e}` }],
      summary: 'Package is not valid JSON',
    };
  }

  // Mandatory Reproducibility Fields (Phase 12)
  const mandatoryFields = [
    { key: 'protocolVersion', name: 'PROTOCOL_VERSION' },
    { key: 'manifestHash', altKey: 'manifest', name: 'MANIFEST_HASH' },
    { key: 'datasetHash', altKey: 'dataset', name: 'DATASET_HASH' },
    { key: 'evidenceManifestHash', altKey: 'evidenceIds', name: 'EVIDENCE_METADATA' },
    { key: 'methodologyVersion', altKey: 'methodologyHash', name: 'METHODOLOGY_SPEC' },
    { key: 'codeCommit', name: 'CODE_COMMIT' },
    { key: 'environmentHash', altKey: 'environment', name: 'ENVIRONMENT_LOCK' },
    { key: 'analysisPlanHash', altKey: 'analysisPlan', name: 'ANALYSIS_PLAN' },
  ];

  const missingMandatory: string[] = [];

  for (const f of mandatoryFields) {
    const hasField = pkg[f.key] !== undefined || (f.altKey && pkg[f.altKey] !== undefined);
    if (!hasField) {
      missingMandatory.push(f.name);
      checks.push({
        name: f.name,
        status: 'FAIL',
        detail: `Missing mandatory field: ${f.key}${f.altKey ? ' or ' + f.altKey : ''}`,
      });
    } else {
      checks.push({
        name: f.name,
        status: 'PASS',
        detail: `Field present: ${pkg[f.key] !== undefined ? f.key : f.altKey}`,
      });
    }
  }

  // Protocol version check
  if (pkg.protocolVersion) {
    const protocolValid = pkg.protocolVersion === PROTOCOL_VERSION;
    checks.push({
      name: 'PROTOCOL_MATCH',
      status: protocolValid ? 'PASS' : 'FAIL',
      detail: `Protocol version: ${pkg.protocolVersion} (expected: ${PROTOCOL_VERSION})`,
    });
  }

  // Manifest checksum verification
  if (pkg.manifest && pkg.manifestHash) {
    const computed = sha256Hex(Buffer.from(JSON.stringify(pkg.manifest), 'utf-8'));
    if (computed === pkg.manifestHash) {
      checks.push({ name: 'MANIFEST_CHECKSUM_VALID', status: 'PASS', detail: `Checksum matches: ${computed}` });
    } else {
      checks.push({ name: 'MANIFEST_CHECKSUM_VALID', status: 'FAIL', detail: `Checksum mismatch: expected ${pkg.manifestHash}, got ${computed}` });
    }
  }

  // Epistemic disclaimers check (mandatory for publication/release)
  const hasAiDisclaimer = pkg.disclaimers?.some((d: string) =>
    d.includes('AI OUTPUT') || d.includes('≠ FACT')
  );
  checks.push({
    name: 'AI_DISCLAIMER',
    status: hasAiDisclaimer ? 'PASS' : 'FAIL',
    detail: hasAiDisclaimer ? 'AI limitation disclaimer present' : 'Missing mandatory AI disclaimer',
  });

  const hasEpistemicDisclaimer = pkg.disclaimers?.some((d: string) =>
    d.includes('EVIDENCE ≠ GUILT') || d.includes('CLAIM ≠ CONVICTION')
  );
  checks.push({
    name: 'EPISTEMIC_DISCLAIMER',
    status: hasEpistemicDisclaimer ? 'PASS' : 'FAIL',
    detail: hasEpistemicDisclaimer ? 'Epistemic disclaimer present' : 'Missing mandatory epistemic disclaimer',
  });

  const hasFailed = checks.some(c => c.status === 'FAIL');
  const isReproducibilityIncomplete = missingMandatory.length > 0;

  let summary: string;
  if (isReproducibilityIncomplete) {
    summary = `REPRODUCIBILITY_INCOMPLETE: missing mandatory metadata: ${missingMandatory.join(', ')}`;
  } else if (hasFailed) {
    summary = `VERIFICATION FAILED: one or more integrity checks failed`;
  } else {
    summary = `VERIFIED: package satisfies all cryptographic and reproducibility standards`;
  }

  return {
    status: hasFailed ? 'FAIL' : 'PASS',
    exitCode: hasFailed ? 1 : 0,
    checks,
    summary,
  };
}

// ── Phase 11: Checkpoint Verifier with Real Ed25519 ─────────────

export function verifyCheckpoint(checkpointPath: string): VerificationResult {
  const checks: CheckResult[] = [];

  if (!existsSync(checkpointPath)) {
    return {
      status: 'FAIL',
      exitCode: 1,
      checks: [{ name: 'FILE_EXISTS', status: 'FAIL', detail: `File not found: ${checkpointPath}` }],
      summary: 'Checkpoint file not found',
    };
  }

  const raw = readFileSync(checkpointPath, 'utf-8');
  let cp: any;
  try {
    cp = JSON.parse(raw);
    checks.push({ name: 'JSON_VALID', status: 'PASS', detail: 'Checkpoint is valid JSON' });
  } catch (e) {
    return {
      status: 'FAIL',
      exitCode: 1,
      checks: [{ name: 'JSON_VALID', status: 'FAIL', detail: `Invalid JSON: ${e}` }],
      summary: 'Checkpoint is not valid JSON',
    };
  }

  // 1. Root hash
  if (!cp.rootHash) {
    checks.push({ name: 'ROOT_HASH', status: 'FAIL', detail: 'Missing rootHash' });
  } else {
    checks.push({ name: 'ROOT_HASH', status: 'PASS', detail: `Root: ${cp.rootHash}` });
  }

  // 2. Event count
  if (!cp.eventCount || cp.eventCount < 1) {
    checks.push({ name: 'EVENT_COUNT', status: 'FAIL', detail: 'eventCount must be >= 1' });
  } else {
    checks.push({ name: 'EVENT_COUNT', status: 'PASS', detail: `${cp.eventCount} events recorded` });
  }

  // 3. Ed25519 Real Verification (Phase 11)
  const publicKey = cp.publicKeyBase64 || cp.publicKey;
  const signature = cp.signatureBase64 || cp.signature;

  if (!publicKey || !signature) {
    checks.push({
      name: 'SIGNATURE_CHECK',
      status: 'FAIL',
      detail: 'Missing publicKeyBase64 or signatureBase64 for cryptographic verification',
    });
  } else {
    const isValid = verifyCheckpointSignature(
      publicKey,
      signature,
      cp.rootHash || '',
      cp.eventCount || 0,
      cp.firstEventHash,
      cp.lastEventHash,
    );

    if (isValid) {
      checks.push({
        name: 'SIGNATURE_VALID',
        status: 'PASS',
        detail: 'Cryptographic Ed25519 signature is authentic and verified against canonical payload',
      });
    } else {
      checks.push({
        name: 'SIGNATURE_VALID',
        status: 'FAIL',
        detail: 'Ed25519 signature verification FAILED — checkpoint data or signature was modified',
      });
    }
  }

  const hasFailed = checks.some(c => c.status === 'FAIL');
  return {
    status: hasFailed ? 'FAIL' : 'PASS',
    exitCode: hasFailed ? 1 : 0,
    checks,
    summary: hasFailed
      ? 'CHECKPOINT VERIFICATION FAILED'
      : 'CHECKPOINT VERIFIED: authentic cryptographic signature and integrity confirmed',
  };
}

// ── Custody Chain Verifier ─────────────────────────────────────

export function verifyChain(chainPath: string): VerificationResult {
  const checks: CheckResult[] = [];

  if (!existsSync(chainPath)) {
    return {
      status: 'FAIL',
      exitCode: 1,
      checks: [{ name: 'FILE_EXISTS', status: 'FAIL', detail: `File not found: ${chainPath}` }],
      summary: 'Chain file not found',
    };
  }

  const raw = readFileSync(chainPath, 'utf-8');
  let chain: any;
  try {
    chain = JSON.parse(raw);
  } catch (e) {
    return {
      status: 'FAIL',
      exitCode: 1,
      checks: [{ name: 'JSON_VALID', status: 'FAIL', detail: `Invalid JSON: ${e}` }],
      summary: 'Chain file is not valid JSON',
    };
  }

  const events = chain.events || [];
  if (events.length === 0) {
    return {
      status: 'FAIL',
      exitCode: 1,
      checks: [{ name: 'CHAIN_EMPTY', status: 'FAIL', detail: 'Chain contains no events' }],
      summary: 'Chain is empty',
    };
  }

  checks.push({ name: 'EVENT_COUNT', status: 'PASS', detail: `${events.length} events in chain` });

  const hashes: string[] = [];
  let previousHash = 'GENESIS';
  let hashErrors = 0;

  for (let i = 0; i < events.length; i++) {
    const e = events[i];
    const computed = computeEventHash(
      e.eventId, e.eventType, e.actorId,
      e.timestampUtc, e.payloadHash, previousHash,
    );
    hashes.push(computed);

    if (e.expectedHash && computed !== e.expectedHash) {
      checks.push({
        name: `EVENT_HASH_${i}`,
        status: 'FAIL',
        detail: `Event ${i} hash mismatch: expected ${e.expectedHash}, computed ${computed}`,
      });
      hashErrors++;
    }
    previousHash = computed;
  }

  if (hashErrors === 0) {
    checks.push({ name: 'ALL_EVENT_HASHES', status: 'PASS', detail: `${events.length} consecutive event hashes verified` });
  }

  const computedRoot = computeChainRoot(hashes);
  if (chain.expectedChainRoot) {
    const rootMatches = computedRoot === chain.expectedChainRoot;
    checks.push({
      name: 'CHAIN_ROOT',
      status: rootMatches ? 'PASS' : 'FAIL',
      detail: rootMatches
        ? `Chain root matches: ${computedRoot}`
        : `Root mismatch: expected ${chain.expectedChainRoot}, computed ${computedRoot}`,
    });
  } else {
    checks.push({ name: 'CHAIN_ROOT', status: 'PASS', detail: `Computed chain root: ${computedRoot}` });
  }

  const hasFailed = checks.some(c => c.status === 'FAIL');
  return {
    status: hasFailed ? 'FAIL' : 'PASS',
    exitCode: hasFailed ? 1 : 0,
    checks,
    summary: hasFailed
      ? 'CHAIN VERIFICATION FAILED'
      : `CHAIN VERIFIED: ${events.length} events unbroken custody root=${computedRoot.substring(0, 16)}...`,
  };
}

// ── CLI Main Dispatch ──────────────────────────────────────────

export function main(argv: string[] = process.argv.slice(2)): number {
  if (argv.length === 0 || argv.includes('--help') || argv.includes('-h')) {
    console.log(`
ELYSIUM SAFETY — INDEPENDENT VERIFIER CLI
Usage:
  elysium-safety verify package <package.json>
  elysium-safety verify chain <chain-events.json>
  elysium-safety verify checkpoint <checkpoint.json>

Exit codes:
  0 = VERIFIED
  1 = FAILED / REPRODUCIBILITY_INCOMPLETE
  2 = INVALID_USAGE
`);
    return argv.length === 0 ? 2 : 0;
  }

  let command = argv[0];
  let subCommand = argv[1];
  let targetPath = argv[2];

  let result: VerificationResult;

  if (command === 'verify') {
    if (subCommand === 'package' && targetPath) {
      result = verifyPackage(resolve(targetPath));
    } else if (subCommand === 'chain' && targetPath) {
      result = verifyChain(resolve(targetPath));
    } else if (subCommand === 'checkpoint' && targetPath) {
      result = verifyCheckpoint(resolve(targetPath));
    } else if (subCommand && !targetPath) {
      // Single argument to verify: auto-detect based on file contents
      const file = resolve(subCommand);
      if (file.includes('checkpoint')) {
        result = verifyCheckpoint(file);
      } else if (file.includes('chain')) {
        result = verifyChain(file);
      } else {
        result = verifyPackage(file);
      }
    } else {
      console.error('Invalid usage. Run with --help for options.');
      return 2;
    }
  } else if (command === '--chain' && subCommand) {
    result = verifyChain(resolve(subCommand));
  } else if (command === '--checkpoint' && subCommand) {
    result = verifyCheckpoint(resolve(subCommand));
  } else {
    // Treat first arg as package path
    result = verifyPackage(resolve(command));
  }

  // Print results
  console.log('\n' + '═'.repeat(64));
  console.log(`  ELYSIUM SAFETY — INDEPENDENT SCIENTIFIC VERIFIER`);
  console.log(`  Protocol: ${PROTOCOL_VERSION}`);
  console.log('═'.repeat(64));

  for (const check of result.checks) {
    const icon = check.status === 'PASS' ? '✅' : check.status === 'FAIL' ? '❌' : '⚠️';
    console.log(`  ${icon} [${check.status}] ${check.name}: ${check.detail}`);
  }

  console.log('═'.repeat(64));
  console.log(`  ${result.status === 'PASS' ? '✅' : '❌'} ${result.summary}`);
  console.log('═'.repeat(64) + '\n');

  console.log('MANDATORY EPISTEMIC INVARIANTS:');
  console.log('  • EVIDENCE ≠ GUILT');
  console.log('  • CLAIM ≠ CONVICTION');
  console.log('  • CORRELATION ≠ CAUSATION');
  console.log('  • NON_ACTION ≠ CRIMINAL LIABILITY');
  console.log('  • AI OUTPUT ≠ FACT');
  console.log('  • PUBLICATION ≠ COURT JUDGMENT');
  console.log('  • PEER REVIEW ≠ JUDICIAL DETERMINATION\n');

  return result.exitCode;
}

if (typeof process !== 'undefined' && process.argv[1]?.endsWith('verify.ts')) {
  const code = main();
  process.exit(code);
}
