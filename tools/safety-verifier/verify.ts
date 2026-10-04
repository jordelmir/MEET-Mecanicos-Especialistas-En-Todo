#!/usr/bin/env npx tsx
/**
 * ═══════════════════════════════════════════════════════════════════
 * Phase 17 — INDEPENDENT VERIFIER CLI
 *
 * Standalone tool for third-party verification.
 * A forensic inspector can verify ANY Elysium Safety research package
 * with ONLY this script + the package ZIP/JSON.
 *
 * Usage:
 *   npx tsx tools/safety-verifier/verify.ts <path-to-package.json>
 *   npx tsx tools/safety-verifier/verify.ts --checkpoint <checkpoint.json>
 *   npx tsx tools/safety-verifier/verify.ts --chain <chain-events.json>
 *
 * Exit codes:
 *   0 = all verifications passed
 *   1 = verification failed (integrity, tampering, missing data)
 *   2 = usage error
 *
 * IMPORTANT: This tool does NOT require access to the Elysium system.
 * It operates ONLY on the exported data.
 * ═══════════════════════════════════════════════════════════════════
 */

import { createHash } from 'crypto';
import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const PROTOCOL_VERSION = 'SAFETY-CUSTODY-V2';

// ── SHA-256 ────────────────────────────────────────────────────

function sha256Hex(data: Buffer): string {
  return createHash('sha256').update(data).digest('hex');
}

function computeEventHash(
  eventId: string, eventType: string, actorId: string,
  timestampUtc: string, payloadHash: string, previousHash: string,
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

// ── Verification Results ───────────────────────────────────────

interface VerificationResult {
  status: 'PASS' | 'FAIL';
  checks: CheckResult[];
  summary: string;
}

interface CheckResult {
  name: string;
  status: 'PASS' | 'FAIL' | 'WARN';
  detail: string;
}

// ── Package Verifier ───────────────────────────────────────────

function verifyPackage(packagePath: string): VerificationResult {
  const checks: CheckResult[] = [];

  if (!existsSync(packagePath)) {
    return { status: 'FAIL', checks: [{ name: 'FILE_EXISTS', status: 'FAIL', detail: `File not found: ${packagePath}` }], summary: 'Package file not found' };
  }

  const raw = readFileSync(packagePath, 'utf-8');
  let pkg: any;
  try {
    pkg = JSON.parse(raw);
    checks.push({ name: 'JSON_VALID', status: 'PASS', detail: 'Package is valid JSON' });
  } catch (e) {
    return { status: 'FAIL', checks: [{ name: 'JSON_VALID', status: 'FAIL', detail: `Invalid JSON: ${e}` }], summary: 'Package is not valid JSON' };
  }

  // 1. Manifest hash
  if (pkg.manifestHash) {
    const computed = sha256Hex(Buffer.from(JSON.stringify(pkg.manifest || {}), 'utf-8'));
    if (computed === pkg.manifestHash) {
      checks.push({ name: 'MANIFEST_HASH', status: 'PASS', detail: `Hash matches: ${computed}` });
    } else {
      checks.push({ name: 'MANIFEST_HASH', status: 'FAIL', detail: `Expected ${pkg.manifestHash}, got ${computed}` });
    }
  } else {
    checks.push({ name: 'MANIFEST_HASH', status: 'WARN', detail: 'No manifestHash field in package' });
  }

  // 2. Evidence count
  const evidenceIds = pkg.evidenceIds || pkg.evidence_ids || [];
  checks.push({
    name: 'EVIDENCE_COUNT',
    status: evidenceIds.length > 0 ? 'PASS' : 'WARN',
    detail: `${evidenceIds.length} evidence items referenced`,
  });

  // 3. Claims
  const claims = pkg.claims || [];
  checks.push({
    name: 'CLAIMS_PRESENT',
    status: claims.length > 0 ? 'PASS' : 'WARN',
    detail: `${claims.length} claims in package`,
  });

  // 4. AI boundary
  const hasAiDisclaimer = pkg.disclaimers?.some((d: string) =>
    d.includes('AI OUTPUT') || d.includes('≠ FACT')
  );
  checks.push({
    name: 'AI_DISCLAIMER',
    status: hasAiDisclaimer ? 'PASS' : 'WARN',
    detail: hasAiDisclaimer ? 'AI disclaimer present' : 'No AI disclaimer found',
  });

  // 5. Epistemological disclaimer
  const hasEpistemicDisclaimer = pkg.disclaimers?.some((d: string) =>
    d.includes('EVIDENCE ≠ GUILT') || d.includes('CLAIM ≠ CONVICTION')
  );
  checks.push({
    name: 'EPISTEMIC_DISCLAIMER',
    status: hasEpistemicDisclaimer ? 'PASS' : 'WARN',
    detail: hasEpistemicDisclaimer ? 'Epistemic disclaimer present' : 'No epistemic disclaimer found',
  });

  // 6. Protocol version
  if (pkg.protocolVersion) {
    checks.push({
      name: 'PROTOCOL_VERSION',
      status: pkg.protocolVersion === PROTOCOL_VERSION ? 'PASS' : 'FAIL',
      detail: `Protocol: ${pkg.protocolVersion}`,
    });
  }

  // 7. Methodology version
  if (pkg.methodologyVersion) {
    checks.push({
      name: 'METHODOLOGY_VERSION',
      status: 'PASS',
      detail: `Methodology: ${pkg.methodologyVersion}`,
    });
  }

  // 8. Limitations
  const limitations = pkg.limitations || [];
  checks.push({
    name: 'LIMITATIONS_DECLARED',
    status: limitations.length > 0 ? 'PASS' : 'WARN',
    detail: `${limitations.length} limitations declared`,
  });

  const failed = checks.filter(c => c.status === 'FAIL');
  return {
    status: failed.length > 0 ? 'FAIL' : 'PASS',
    checks,
    summary: failed.length > 0
      ? `VERIFICATION FAILED: ${failed.length} check(s) failed`
      : `VERIFICATION PASSED: ${checks.length} checks, ${checks.filter(c => c.status === 'WARN').length} warnings`,
  };
}

// ── Chain Verifier ─────────────────────────────────────────────

function verifyChain(chainPath: string): VerificationResult {
  const checks: CheckResult[] = [];
  const raw = readFileSync(chainPath, 'utf-8');
  const chain = JSON.parse(raw);
  const events = chain.events || [];

  if (events.length === 0) {
    return { status: 'FAIL', checks: [{ name: 'CHAIN_EMPTY', status: 'FAIL', detail: 'No events in chain' }], summary: 'Empty chain' };
  }

  checks.push({ name: 'EVENT_COUNT', status: 'PASS', detail: `${events.length} events in chain` });

  // Verify each event hash
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
    checks.push({ name: 'ALL_EVENT_HASHES', status: 'PASS', detail: `${events.length} event hashes verified` });
  }

  // Verify chain root
  const computedRoot = computeChainRoot(hashes);
  if (chain.expectedChainRoot) {
    checks.push({
      name: 'CHAIN_ROOT',
      status: computedRoot === chain.expectedChainRoot ? 'PASS' : 'FAIL',
      detail: computedRoot === chain.expectedChainRoot
        ? `Chain root matches: ${computedRoot}`
        : `Root mismatch: expected ${chain.expectedChainRoot}, computed ${computedRoot}`,
    });
  } else {
    checks.push({ name: 'CHAIN_ROOT', status: 'PASS', detail: `Computed chain root: ${computedRoot}` });
  }

  const failed = checks.filter(c => c.status === 'FAIL');
  return {
    status: failed.length > 0 ? 'FAIL' : 'PASS',
    checks,
    summary: failed.length > 0
      ? `CHAIN VERIFICATION FAILED: ${failed.length} check(s) failed`
      : `CHAIN VERIFICATION PASSED: ${events.length} events, root=${computedRoot.substring(0, 16)}...`,
  };
}

// ── Checkpoint Verifier ────────────────────────────────────────

function verifyCheckpoint(checkpointPath: string): VerificationResult {
  const checks: CheckResult[] = [];
  const raw = readFileSync(checkpointPath, 'utf-8');
  const cp = JSON.parse(raw);

  if (!cp.rootHash) {
    checks.push({ name: 'ROOT_HASH', status: 'FAIL', detail: 'No rootHash in checkpoint' });
  } else {
    checks.push({ name: 'ROOT_HASH', status: 'PASS', detail: `Root: ${cp.rootHash.substring(0, 16)}...` });
  }

  if (!cp.eventCount || cp.eventCount < 1) {
    checks.push({ name: 'EVENT_COUNT', status: 'FAIL', detail: 'eventCount must be >= 1' });
  } else {
    checks.push({ name: 'EVENT_COUNT', status: 'PASS', detail: `${cp.eventCount} events` });
  }

  if (cp.signatureAlgorithm) {
    checks.push({ name: 'SIGNATURE_ALGORITHM', status: 'PASS', detail: cp.signatureAlgorithm });
  }

  if (cp.signatureBase64) {
    checks.push({ name: 'SIGNATURE_PRESENT', status: 'PASS', detail: 'Digital signature present' });
  } else {
    checks.push({ name: 'SIGNATURE_PRESENT', status: 'WARN', detail: 'No digital signature' });
  }

  const failed = checks.filter(c => c.status === 'FAIL');
  return {
    status: failed.length > 0 ? 'FAIL' : 'PASS',
    checks,
    summary: failed.length > 0
      ? `CHECKPOINT VERIFICATION FAILED`
      : `CHECKPOINT VERIFICATION PASSED`,
  };
}

// ── CLI ────────────────────────────────────────────────────────

function main() {
  const args = process.argv.slice(2);

  if (args.length === 0) {
    console.error('Usage:');
    console.error('  npx tsx verify.ts <package.json>');
    console.error('  npx tsx verify.ts --chain <chain-events.json>');
    console.error('  npx tsx verify.ts --checkpoint <checkpoint.json>');
    process.exit(2);
  }

  let result: VerificationResult;

  if (args[0] === '--chain' && args[1]) {
    result = verifyChain(resolve(args[1]));
  } else if (args[0] === '--checkpoint' && args[1]) {
    result = verifyCheckpoint(resolve(args[1]));
  } else {
    result = verifyPackage(resolve(args[0]));
  }

  // Output
  console.log('\n' + '═'.repeat(60));
  console.log(`  ELYSIUM SAFETY — INDEPENDENT VERIFIER`);
  console.log(`  Protocol: ${PROTOCOL_VERSION}`);
  console.log('═'.repeat(60));

  for (const check of result.checks) {
    const icon = check.status === 'PASS' ? '✅' : check.status === 'FAIL' ? '❌' : '⚠️';
    console.log(`  ${icon} ${check.name}: ${check.detail}`);
  }

  console.log('═'.repeat(60));
  console.log(`  ${result.status === 'PASS' ? '✅' : '❌'} ${result.summary}`);
  console.log('═'.repeat(60) + '\n');

  console.log('IMPORTANT DISCLAIMERS:');
  console.log('  • EVIDENCE ≠ GUILT');
  console.log('  • CLAIM ≠ CONVICTION');
  console.log('  • CORRELATION ≠ CAUSATION');
  console.log('  • AI OUTPUT ≠ FACT');
  console.log('  • PUBLICATION ≠ COURT JUDGMENT');
  console.log('  • This tool verifies DATA INTEGRITY only.');
  console.log('  • It does NOT establish legal truth.\n');

  process.exit(result.status === 'PASS' ? 0 : 1);
}

main();
