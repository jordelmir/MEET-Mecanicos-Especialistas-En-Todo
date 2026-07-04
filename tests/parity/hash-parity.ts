#!/usr/bin/env node
/**
 * Cross-runtime parity verifier.
 *
 * Reads a JSON fixture, runs the TypeScript `canonicalSnapshotString` on
 * the same input, and compares the SHA-256 against the fixture's
 * `expectedHash`. The Kotlin side runs the exact same input through
 * `HashEngine.kt` (or `DiagnosticSnapshot.computeHash` for the
 * snapshot-only path). If both sides match the fixture, the chain
 * is intact across web and Android.
 *
 * Usage:
 *   tsx tests/parity/hash-parity.ts tests/parity/fixtures/snapshot-p0230.json
 *   tsx tests/parity/hash-parity.ts --all
 *
 * Exit code 0 on success, 1 on mismatch.
 */

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { canonicalSnapshotString, sha256Hex } from '../../lib/reports/hash.ts';
import type { DiagnosticProvenance } from '../../lib/reports/types.ts';

interface FixtureInput {
  vehicleId: string;
  sessionId: string | null;
  createdAtMs: number;
  dtcsActive: string[];
  dtcsPending: string[];
  dtcsPermanent: string[];
  freezeFramePidValues: Record<string, number>;
  readiness: Record<string, boolean>;
  ecuVoltage: number | null;
  rpm: number | null;
  coolantTempC: number | null;
  speedKph: number | null;
  engineLoadPct: number | null;
  fuelTrimStft: number | null;
  fuelTrimLtft: number | null;
  provenance: DiagnosticProvenance;
}

interface Fixture {
  label: string;
  expectedHash: string;
  [k: string]: unknown;
}

async function verifyOne(path: string): Promise<{ ok: boolean; label: string; actual: string; expected: string }> {
  const raw = readFileSync(resolve(path), 'utf-8');
  const fx = JSON.parse(raw) as Fixture;
  const input = fx as unknown as FixtureInput;
  const snap = {
    id: 'parity',
    vehicleId: input.vehicleId,
    sessionId: input.sessionId,
    createdAtMs: input.createdAtMs,
    dtcsActive: input.dtcsActive,
    dtcsPending: input.dtcsPending,
    dtcsPermanent: input.dtcsPermanent,
    freezeFramePidValues: input.freezeFramePidValues,
    livePids: {},
    readiness: input.readiness,
    ecuVoltage: input.ecuVoltage,
    rpm: input.rpm,
    coolantTempC: input.coolantTempC,
    speedKph: input.speedKph,
    engineLoadPct: input.engineLoadPct,
    fuelTrimStft: input.fuelTrimStft,
    fuelTrimLtft: input.fuelTrimLtft,
    rawFrames: [],
    notes: '',
    provenance: input.provenance,
  };
  const canonical = canonicalSnapshotString(snap);
  const actual = await sha256Hex(canonical);
  const ok = actual === fx.expectedHash;
  return { ok, label: fx.label, actual, expected: fx.expectedHash };
}

async function main(): Promise<number> {
  const args = process.argv.slice(2);
  const isAll = args[0] === '--all';
  const files = isAll
    ? ['tests/parity/fixtures/snapshot-p0230.json']
    : args;
  if (files.length === 0) {
    console.error('Usage: tsx tests/parity/hash-parity.ts <fixture.json> | --all');
    return 1;
  }
  let allOk = true;
  for (const f of files) {
    const r = await verifyOne(f);
    const tag = r.ok ? 'OK' : 'MISMATCH';
    console.log(`[${tag}] ${r.label}`);
    console.log(`  expected: ${r.expected}`);
    console.log(`  actual:   ${r.actual}`);
    if (!r.ok) allOk = false;
  }
  return allOk ? 0 : 1;
}

main().then((code) => process.exit(code));
